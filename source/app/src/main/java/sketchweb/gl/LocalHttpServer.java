package sketchweb.gl;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal localhost-only HTTP server used to preview generated pages from a
 * proper {@code http://127.0.0.1:PORT/} URL instead of {@code file://}. This
 * unlocks browser features that require an http(s) origin (fetch, modules,
 * service workers, cross-origin assets) and matches a normal local-host
 * development workflow.
 *
 * <p>The server binds only to the loopback interface and serves files from a
 * single root directory. It is intentionally tiny (no third-party deps) and
 * single-purpose.
 */
public final class LocalHttpServer {

    private static final String TAG = "LocalHttpServer";

    private final File rootDir;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private ServerSocket socket;
    private volatile boolean running;
    private int port;

    public LocalHttpServer(File rootDir) {
        this.rootDir = rootDir;
    }

    /** Bind to a random free loopback port and start serving. Returns the port. */
    public int start() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        port = socket.getLocalPort();
        running = true;

        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket client = socket.accept();
                    pool.submit(() -> handle(client));
                } catch (IOException e) {
                    if (running) Log.w(TAG, "accept failed: " + e.getMessage());
                }
            }
        }, "LocalHttpServer-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        return port;
    }

    public int getPort() {
        return port;
    }

    /** Build a {@code http://127.0.0.1:PORT/path} URL for a relative file path. */
    public String urlFor(String relativePath) {
        if (relativePath == null) relativePath = "";
        if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
        return "http://127.0.0.1:" + port + "/" + relativePath;
    }

    public void stop() {
        running = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
    }

    // ---------------------------------------------------------------------
    // Request handling
    // ---------------------------------------------------------------------

    private void handle(Socket client) {
        try (Socket c = client;
             BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(c.getOutputStream())) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            // Drain remaining headers
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) { /* ignore */ }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                writeStatus(out, 400, "Bad Request", "text/plain", "Bad Request".getBytes());
                return;
            }

            String method = parts[0];
            String rawPath = parts[1];
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                writeStatus(out, 405, "Method Not Allowed", "text/plain", "Method Not Allowed".getBytes());
                return;
            }

            // Strip query string & decode
            int q = rawPath.indexOf('?');
            String path = q >= 0 ? rawPath.substring(0, q) : rawPath;
            try {
                path = URLDecoder.decode(path, "UTF-8");
            } catch (IllegalArgumentException ignored) { /* keep raw */ }

            File target = resolveSafe(path);
            if (target == null) {
                writeStatus(out, 403, "Forbidden", "text/plain", "Forbidden".getBytes());
                return;
            }
            if (target.isDirectory()) {
                File index = new File(target, "index.html");
                if (index.exists()) target = index;
            }
            if (!target.exists() || !target.isFile()) {
                writeStatus(out, 404, "Not Found", "text/plain", "Not Found".getBytes());
                return;
            }

            String contentType = mimeFor(target.getName());
            byte[] body = readFile(target);
            writeStatus(out, 200, "OK", contentType, body);
        } catch (IOException e) {
            Log.w(TAG, "handle failed: " + e.getMessage());
        }
    }

    /**
     * Resolve a request path within {@link #rootDir}, refusing any traversal
     * attempts that escape the root.
     */
    private File resolveSafe(String path) {
        if (path == null || path.isEmpty()) path = "/";
        if (path.startsWith("/")) path = path.substring(1);
        File f = new File(rootDir, path);
        try {
            String rootCanon = rootDir.getCanonicalPath();
            String fileCanon = f.getCanonicalPath();
            if (!fileCanon.equals(rootCanon) && !fileCanon.startsWith(rootCanon + File.separator)) {
                return null;
            }
            return f;
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] readFile(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(f.length(), Integer.MAX_VALUE)];
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
            if (read != buf.length) {
                byte[] trimmed = new byte[read];
                System.arraycopy(buf, 0, trimmed, 0, read);
                return trimmed;
            }
            return buf;
        }
    }

    private void writeStatus(OutputStream out, int code, String reason, String contentType, byte[] body) throws IOException {
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        header.append("Content-Type: ").append(contentType).append("\r\n");
        header.append("Content-Length: ").append(body.length).append("\r\n");
        header.append("Cache-Control: no-cache\r\n");
        header.append("Connection: close\r\n");
        header.append("\r\n");
        out.write(header.toString().getBytes());
        out.write(body);
        out.flush();
    }

    private static final Map<String, String> MIME = new HashMap<>();
    static {
        MIME.put("html", "text/html; charset=utf-8");
        MIME.put("htm",  "text/html; charset=utf-8");
        MIME.put("css",  "text/css; charset=utf-8");
        MIME.put("js",   "application/javascript; charset=utf-8");
        MIME.put("mjs",  "application/javascript; charset=utf-8");
        MIME.put("json", "application/json; charset=utf-8");
        MIME.put("svg",  "image/svg+xml");
        MIME.put("png",  "image/png");
        MIME.put("jpg",  "image/jpeg");
        MIME.put("jpeg", "image/jpeg");
        MIME.put("gif",  "image/gif");
        MIME.put("webp", "image/webp");
        MIME.put("ico",  "image/x-icon");
        MIME.put("ttf",  "font/ttf");
        MIME.put("otf",  "font/otf");
        MIME.put("woff", "font/woff");
        MIME.put("woff2","font/woff2");
        MIME.put("mp4",  "video/mp4");
        MIME.put("mp3",  "audio/mpeg");
        MIME.put("wav",  "audio/wav");
        MIME.put("txt",  "text/plain; charset=utf-8");
    }

    private String mimeFor(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String ext = name.substring(dot + 1).toLowerCase();
        String t = MIME.get(ext);
        return t != null ? t : "application/octet-stream";
    }
}
