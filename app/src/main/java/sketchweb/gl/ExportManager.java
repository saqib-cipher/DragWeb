package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.util.List;
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
        return generateExportFiles(screen, projectName, logicBlockManager, null);
    }

    public ExportResult generateExportFiles(View screen, String projectName,
                                            LogicBlockManager logicBlockManager,
                                            CustomBlockManager customBlockManager) {
        ExportResult result = new ExportResult();

        String cssContent = generateCss(screen, logicBlockManager, customBlockManager);
        String jsContent = generateJs(logicBlockManager);
        String htmlContent = generateHtml(screen, projectName, customBlockManager);

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
        return exportAsZip(screen, projectName, projectId, logicBlockManager, null);
    }

    public File exportAsZip(View screen, String projectName, String projectId,
                            LogicBlockManager logicBlockManager,
                            CustomBlockManager customBlockManager) throws IOException {
        ExportResult result = generateExportFiles(screen, projectName, logicBlockManager, customBlockManager);
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

            // Include project data files for full backup
            includeProjectData(zos, projectId);

            // Include assets
            includeAssets(zos, projectId);
        }
        return zipFile;
    }

    /**
     * Include all project data files in the ZIP export:
     * layout JSON, theme, logic blocks, page layouts, metadata.
     */
    private void includeProjectData(ZipOutputStream zos, String projectId) {
        File dir = new File(context.getFilesDir(), "projects");
        if (!dir.exists()) return;

        // Include all project-related files
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName();
            // Include files belonging to this project: projectId.json, projectId.meta,
            // projectId.theme, projectId_pageName.logic, projectId_pageName.json
            if (name.startsWith(projectId + ".") || name.startsWith(projectId + "_")) {
                try {
                    addFileToZip(zos, "data/" + name, file);
                } catch (IOException e) {
                    Log.w("ExportManager", "Could not add " + name + " to zip: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Include project assets (images, etc.) in the ZIP export.
     */
    private void includeAssets(ZipOutputStream zos, String projectId) {
        try {
            String assetsPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId + "/assets";
            File assetsDir = new File(assetsPath);
            if (assetsDir.exists() && assetsDir.isDirectory()) {
                addDirectoryToZip(zos, assetsDir, "assets/");
            }
        } catch (Exception e) {
            Log.w("ExportManager", "Could not include assets: " + e.getMessage());
        }
    }

    private void addFileToZip(ZipOutputStream zos, String entryName, File file) throws IOException {
        byte[] buffer = new byte[4096];
        FileInputStream fis = new FileInputStream(file);
        zos.putNextEntry(new ZipEntry(entryName));
        int length;
        while ((length = fis.read(buffer)) > 0) {
            zos.write(buffer, 0, length);
        }
        zos.closeEntry();
        fis.close();
    }

    private void addDirectoryToZip(ZipOutputStream zos, File dir, String prefix) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, prefix + file.getName() + "/");
            } else {
                addFileToZip(zos, prefix + file.getName(), file);
            }
        }
    }

    private String generateHtml(View screen, String projectName, CustomBlockManager customBlockManager) {
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

        // Custom-block HTML appended into the body (after widget tree).
        if (customBlockManager != null) {
            String customHtml = customBlockManager.renderAllHtml();
            if (customHtml != null && !customHtml.trim().isEmpty()) {
                html.append("\n  <!-- Custom blocks -->\n  ");
                html.append(customHtml.replace("\n", "\n  "));
                html.append("\n");
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

    private String generateCss(View screen, LogicBlockManager logicBlockManager,
                               CustomBlockManager customBlockManager) {
        StringBuilder css = new StringBuilder();
        css.append("/* Generated by DragWeb */\n\n");
        css.append(themeManager.generateGlobalCss());
        css.append("\n/* Animation Keyframes */\n");
        css.append("@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }\n");
        css.append("@keyframes slideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }\n");
        css.append("@keyframes slideDown { from { transform: translateY(-20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }\n");
        css.append("@keyframes pulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.05); } }\n\n");

        // CSS pseudo-class interaction rules (hover, focus, active – no JS needed)
        if (logicBlockManager != null) {
            String pseudoRules = logicBlockManager.generateCssPseudoRules();
            if (pseudoRules != null && !pseudoRules.trim().isEmpty()) {
                css.append("/* CSS Interaction Rules */\n");
                css.append(pseudoRules);
                css.append("\n");
            }
            String baseRules = logicBlockManager.generateBaseCssRules();
            if (baseRules != null && !baseRules.trim().isEmpty()) {
                css.append("/* Logic Block Styles */\n");
                css.append(baseRules);
                css.append("\n");
            }
        }

        // Custom block CSS templates (e.g. ".menu{color:red;}" from class_color block)
        if (customBlockManager != null) {
            String customCss = customBlockManager.renderAllCss();
            if (customCss != null && !customCss.trim().isEmpty()) {
                css.append("/* Custom Block Styles */\n");
                css.append(customCss);
                css.append("\n");
            }
        }

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

    public boolean importZipBackup(File zipFile) {
        if (zipFile == null || !zipFile.exists()) return false;
        
        File projectDir = new File(context.getFilesDir(), "projects");
        if (!projectDir.exists()) projectDir.mkdirs();
        
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                
                if (name.startsWith("data/")) {
                    String fileName = name.substring(5);
                    File target = new File(projectDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                } else if (name.startsWith("assets/")) {
                    // Extract to external storage assets path
                    // We need the projectId from the filename if possible, but the ZIP itself 
                    // should ideally contain it in a meta file or we infer it.
                    // For now, let's assume we find a .json file in data/ first or just extract assets
                    // to a temporary location then move them once we know the projectId.
                    // Or better: the zip structure is assets/projectId/...
                    
                    // Actually, let's look for any .json file in data/ to find the projectId
                    // This is tricky during streaming.
                    
                    // Simple approach: Extract everything to a temp dir, then find the .json file, then move.
                }
                zis.closeEntry();
            }
            return true;
        } catch (IOException e) {
            Log.e("ExportManager", "Import failed: " + e.getMessage());
            return false;
        }
    }

    // Better import logic: extract all to temp, then move to right places
    public boolean restoreProjectFromZip(File zipFile) {
        File tempDir = new File(context.getCacheDir(), "import_temp_" + System.currentTimeMillis());
        tempDir.mkdirs();
        
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(tempDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
            
            // Now move from temp to actual locations
            File dataDir = new File(tempDir, "data");
            String foundProjectId = null;
            if (dataDir.exists()) {
                File[] files = dataDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().endsWith(".json") && !f.getName().contains("_")) {
                            foundProjectId = f.getName().replace(".json", "");
                            break;
                        }
                    }
                    
                    if (foundProjectId != null) {
                        // Move data files
                        File targetDataDir = new File(context.getFilesDir(), "projects");
                        targetDataDir.mkdirs();
                        for (File f : files) {
                            f.renameTo(new File(targetDataDir, f.getName()));
                        }
                        
                        // Move assets
                        File assetsDir = new File(tempDir, "assets");
                        if (assetsDir.exists()) {
                            String targetAssetsPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                                + "/.dragweb/projects/" + foundProjectId + "/assets";
                            File targetAssetsDir = new File(targetAssetsPath);
                            targetAssetsDir.mkdirs();
                            copyDirectory(assetsDir, targetAssetsDir);
                        }
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ExportManager", "Restore failed: " + e.getMessage());
        } finally {
            deleteDirectory(tempDir);
        }
        return false;
    }

    private void copyDirectory(File sourceLocation, File targetLocation) throws IOException {
        if (sourceLocation.isDirectory()) {
            if (!targetLocation.exists() && !targetLocation.mkdirs()) {
                throw new IOException("Cannot create dir " + targetLocation.getAbsolutePath());
            }
            String[] children = sourceLocation.list();
            for (int i = 0; i < children.length; i++) {
                copyDirectory(new File(sourceLocation, children[i]),
                        new File(targetLocation, children[i]));
            }
        } else {
            java.io.InputStream in = new FileInputStream(sourceLocation);
            java.io.OutputStream out = new FileOutputStream(targetLocation);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        }
    }

    private void deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) deleteDirectory(file);
                    else file.delete();
                }
            }
            path.delete();
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private String camelToKebab(String str) {
        if (str == null) return "";
        return str.replaceAll("([A-Z])", "-$1").toLowerCase();
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "project";
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
