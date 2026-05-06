package sketchweb.gl.dragweb;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Bundles a generated project directory into a downloadable ZIP. The caller
 * picks the destination {@link OutputStream} (typically obtained from a
 * Storage Access Framework Uri picker) so the export lands wherever the user
 * chose to save it.
 */
public class DragWebExportManager {

    private final Context context;

    public DragWebExportManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Zip the entire <code>/.dragweb/projects/&lt;projectName&gt;/</code>
     * directory (including generated index.html, style.css, assets/) into
     * <code>out</code>. Does not close <code>out</code>.
     */
    public void exportZip(String projectName, OutputStream out) throws IOException {
        File root = DragWebPaths.projectDir(context, projectName);
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            if (root.exists()) writeEntry(zos, root, root.getName());
        }
    }

    public File exportZipToFile(String projectName) throws IOException {
        File outFile = new File(context.getCacheDir(),
                DragWebPaths.sanitize(projectName) + ".zip");
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            exportZip(projectName, fos);
        }
        return outFile;
    }

    private static void writeEntry(ZipOutputStream zos, File source, String entryPath)
            throws IOException {
        if (source.isDirectory()) {
            File[] children = source.listFiles();
            if (children == null) return;
            for (File c : children) writeEntry(zos, c, entryPath + "/" + c.getName());
        } else {
            ZipEntry entry = new ZipEntry(entryPath);
            zos.putNextEntry(entry);
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source))) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
            }
            zos.closeEntry();
        }
    }
}
