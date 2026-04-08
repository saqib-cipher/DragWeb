package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportManager {

    private Context context;
    private ThemeManager themeManager;

    public ExportManager(Context context, ThemeManager themeManager) {
        this.context = context;
        this.themeManager = themeManager;
    }

    public static class ExportResult {
        public String htmlContent;
        public String cssContent;
        public String jsContent;
        public File exportDir;
        public boolean success;
        public String message;
    }

    public ExportResult generateExportFiles(View screen, String projectName, LogicBlockManager logicBlockManager) {
        ExportResult result = new ExportResult();

        String cssContent = generateCss(screen);
        String jsContent = generateJs(logicBlockManager);
        String htmlContent = generateHtml(screen, projectName);

        result.htmlContent = htmlContent;
        result.cssContent = cssContent;
        result.jsContent = jsContent;

        File exportDir = new File(context.getFilesDir(), "exports/" + sanitizeFileName(projectName));
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        result.exportDir = exportDir;

        try {
            writeFile(new File(exportDir, "index.html"), htmlContent);
            writeFile(new File(exportDir, "style.css"), cssContent);
            writeFile(new File(exportDir, "script.js"), jsContent);
            result.success = true;
            result.message = "Exported to: " + exportDir.getAbsolutePath();
        } catch (IOException e) {
            result.success = false;
            result.message = "Export failed: " + e.getMessage();
        }

        return result;
    }

    public File exportAsZip(View screen, String projectName, String projectId, LogicBlockManager logicBlockManager) throws IOException {
        ExportResult result = generateExportFiles(screen, projectName, logicBlockManager);
        if (!result.success) {
            throw new IOException(result.message);
        }

        String zipFileName = sanitizeFileName(projectName) + "_" + sanitizeFileName(projectId) + ".zip";

        // Try saving to external storage first: /.DragWeb/export/
        File zipFile = null;
        try {
            String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.DragWeb/export";
            File extDir = new File(extPath);
            if (!extDir.exists()) extDir.mkdirs();
            zipFile = new File(extDir, zipFileName);
        } catch (Exception e) {
            Log.w("ExportManager", "External storage unavailable, using internal: " + e.getMessage());
        }

        // Fallback to internal storage
        if (zipFile == null || (!zipFile.getParentFile().exists() && !zipFile.getParentFile().mkdirs())) {
            File internalDir = new File(context.getFilesDir(), "exports");
            if (!internalDir.exists()) internalDir.mkdirs();
            zipFile = new File(internalDir, zipFileName);
        }

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            addToZip(zos, "index.html", result.htmlContent);
            addToZip(zos, "style.css", result.cssContent);
            addToZip(zos, "script.js", result.jsContent);
        }
        return zipFile;
    }

    private String generateHtml(View screen, String projectName) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>").append(escapeHtml(projectName)).append("</title>\n");
        html.append("  <link rel=\"stylesheet\" href=\"style.css\">\n");
        html.append("</head>\n");
        html.append("<body>\n");

        if (screen instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) screen;
            for (int i = 0; i < vg.getChildCount(); i++) {
                html.append(generateElementHtml(vg.getChildAt(i), 1));
            }
        }

        html.append("\n  <script src=\"script.js\"></script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }

    private String generateElementHtml(View view, int indent) {
        if (view == null) return "";

        Object tagObj = view.getTag();
        if (!(tagObj instanceof Map)) return "";

        Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
        String tag = widgetMap.containsKey("tag") ? widgetMap.get("tag").toString() : "div";
        Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
        if (function == null) return "";

        String indentStr = repeat("  ", indent);
        StringBuilder html = new StringBuilder();
        html.append(indentStr).append("<").append(tag);

        // Data attribute for logic
        html.append(" data-widget=\"").append(tag).append("\"");

        // Generate class name
        String className = "el-" + tag + "-" + Math.abs(view.hashCode() % 10000);
        html.append(" class=\"").append(className).append("\"");

        // Inline styles
        Map<String, Object> style = (Map<String, Object>) function.get("style");
        if (style != null && !style.isEmpty()) {
            html.append(" style=\"");
            for (Map.Entry<String, Object> entry : style.entrySet()) {
                String cssKey = camelToKebab(entry.getKey());
                html.append(cssKey).append(": ").append(entry.getValue()).append("; ");
            }
            html.append("\"");
        }

        // Tag-specific attributes
        if ("img".equals(tag) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeHtml(function.get("src").toString())).append("\"");
            html.append(" alt=\"Image\"");
        }
        if (("input".equals(tag) || "textarea".equals(tag)) && function.containsKey("type")) {
            html.append(" type=\"").append(escapeHtml(function.get("type").toString())).append("\"");
        }
        if (("input".equals(tag) || "textarea".equals(tag)) && function.containsKey("placeholder")) {
            html.append(" placeholder=\"").append(escapeHtml(function.get("placeholder").toString())).append("\"");
        }
        if ("a".equals(tag) && function.containsKey("href")) {
            html.append(" href=\"").append(escapeHtml(function.get("href").toString())).append("\"");
        }
        if (("video".equals(tag) || "audio".equals(tag)) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeHtml(function.get("src").toString())).append("\"");
        }
        if (("video".equals(tag) || "audio".equals(tag)) && "true".equals(String.valueOf(function.get("controls")))) {
            html.append(" controls");
        }
        if ("iframe".equals(tag) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeHtml(function.get("src").toString())).append("\"");
        }

        // Self-closing tags
        if ("input".equals(tag) || "img".equals(tag) || "hr".equals(tag) || "br".equals(tag)) {
            html.append(" />\n");
            return html.toString();
        }

        html.append(">");

        // Text content
        if (function.containsKey("text")) {
            html.append(escapeHtml(function.get("text").toString()));
        }

        // Children
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            if (vg.getChildCount() > 0) {
                html.append("\n");
                for (int i = 0; i < vg.getChildCount(); i++) {
                    html.append(generateElementHtml(vg.getChildAt(i), indent + 1));
                }
                html.append(indentStr);
            }
        }

        html.append("</").append(tag).append(">\n");
        return html.toString();
    }

    private String generateCss(View screen) {
        StringBuilder css = new StringBuilder();
        css.append("/* Generated by DragWeb */\n\n");
        css.append(themeManager.generateGlobalCss());
        css.append("\n/* Animation Keyframes */\n");
        css.append("@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }\n");
        css.append("@keyframes slideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }\n");
        css.append("@keyframes slideDown { from { transform: translateY(-20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }\n");
        css.append("@keyframes pulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.05); } }\n\n");
        css.append("/* Element Styles */\n");

        return css.toString();
    }

    private String generateJs(LogicBlockManager logicBlockManager) {
        StringBuilder js = new StringBuilder();
        js.append("// Generated by DragWeb\n\n");
        js.append("document.addEventListener('DOMContentLoaded', function() {\n");
        js.append("  console.log('Page loaded successfully!');\n\n");
        js.append("  // Add click handlers for buttons\n");
        js.append("  var buttons = document.querySelectorAll('button');\n");
        js.append("  buttons.forEach(function(btn) {\n");
        js.append("    btn.addEventListener('click', function() {\n");
        js.append("      console.log('Button clicked: ' + this.textContent);\n");
        js.append("    });\n");
        js.append("  });\n");
        js.append("});\n\n");

        // Logic blocks JS
        if (logicBlockManager != null) {
            String logicJs = logicBlockManager.generateJavaScript();
            if (logicJs != null && !logicJs.isEmpty()) {
                js.append(logicJs);
            }
        }

        return js.toString();
    }

    private void writeFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private void addToZip(ZipOutputStream zos, String fileName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        zos.write(content.getBytes("UTF-8"));
        zos.closeEntry();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private String camelToKebab(String str) {
        return str.replaceAll("([A-Z])", "-$1").toLowerCase();
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}
