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
    private IconLibraryManager iconLibraryManager;
    private AnimationLibraryManager animationLibraryManager;
    private String projectId;

    /**
     * Per-export accumulator for element-level CSS. Cleared at the start of
     * each call to {@link #generateExportFiles} so widget styles get appended
     * into {@code css/style.css} instead of being baked inline on the element.
     */
    private final StringBuilder elementCssBuffer = new StringBuilder();

    public ExportManager(Context context, ThemeManager themeManager) {
        this.context = context;
        this.themeManager = themeManager;
    }

    public void setIconLibraryManager(IconLibraryManager m) {
        this.iconLibraryManager = m;
    }

    public void setAnimationLibraryManager(AnimationLibraryManager m) {
        this.animationLibraryManager = m;
    }

    /**
     * Set the active project id so non-ZIP exports can locate the matching
     * assets folder ({@code .dragweb/projects/&lt;id&gt;/assets}). Without this
     * the HTML export still works, but ships with an empty assets directory.
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
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

        // Order matters: build HTML first so generateElementHtml() can populate
        // elementCssBuffer with the per-element rules, then ask generateCss()
        // to fold them into the final stylesheet alongside theme + logic CSS.
        elementCssBuffer.setLength(0);
        String htmlContent = generateHtml(screen, projectName, customBlockManager, logicBlockManager);
        String cssContent = generateCss(screen, logicBlockManager, customBlockManager);
        String jsContent = generateJs(logicBlockManager);

        result.htmlContent = htmlContent;
        result.cssContent = cssContent;
        result.jsContent = jsContent;

        File exportDir = new File(context.getFilesDir(), "exports/" + sanitizeFileName(projectName));
        if (!exportDir.exists()) exportDir.mkdirs();
        File cssDir = new File(exportDir, "css");
        File jsDir = new File(exportDir, "js");
        File pagesDir = new File(exportDir, "pages");
        File componentsDir = new File(exportDir, "components");
        File fontsDir = new File(exportDir, "fonts");
        File assetsDir = new File(exportDir, "assets");
        for (File d : new File[]{cssDir, jsDir, pagesDir, componentsDir, fontsDir, assetsDir}) {
            if (!d.exists()) d.mkdirs();
        }
        result.exportDir = exportDir;

        try {
            writeFile(new File(exportDir, "index.html"), htmlContent);
            writeFile(new File(cssDir, "style.css"), cssContent);
            writeFile(new File(jsDir, "script.js"), jsContent);
            writeFile(new File(exportDir, "project.json"),
                generateProjectManifest(projectName));
            // Mirror the assets panel into the HTML export so a plain-folder
            // export ships images/fonts/etc. alongside the generated HTML.
            copyAssetsPanel(assetsDir);
            result.success = true;
            result.message = "Exported to: " + exportDir.getAbsolutePath();
        } catch (IOException e) {
            result.success = false;
            result.message = "Export failed: " + e.getMessage();
        }

        return result;
    }

    /**
     * Copy the user's Assets-panel directory into the export's {@code assets/}
     * folder. Resolves the path via the same key the file explorer uses
     * ({@code .dragweb/projects/&lt;id&gt;/assets}) — call {@link #setProjectId}
     * before exporting or this is a silent no-op.
     */
    private void copyAssetsPanel(File targetAssetsDir) {
        if (projectId == null || projectId.isEmpty()) return;
        try {
            String panelPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId + "/assets";
            File src = new File(panelPath);
            if (!src.exists() || !src.isDirectory()) return;
            copyDirectory(src, targetAssetsDir);
        } catch (Exception e) {
            Log.w("ExportManager", "Could not mirror assets panel: " + e.getMessage());
        }
    }

    /**
     * Build a small project manifest describing the export. Keeps the export
     * tree self-describing so a future re-import can pick the project up
     * cleanly without inspecting the HTML.
     */
    private String generateProjectManifest(String projectName) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(escapeJsonString(projectName)).append("\",\n");
        sb.append("  \"generator\": \"DragWeb\",\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"entry\": \"index.html\",\n");
        sb.append("  \"styles\": [\"css/style.css\"],\n");
        sb.append("  \"scripts\": [\"js/script.js\"],\n");
        if (iconLibraryManager != null && !iconLibraryManager.enabledIds().isEmpty()) {
            sb.append("  \"iconLibraries\": [");
            boolean first = true;
            for (String id : iconLibraryManager.enabledIds()) {
                if (!first) sb.append(", ");
                sb.append("\"").append(escapeJsonString(id)).append("\"");
                first = false;
            }
            sb.append("],\n");
        }
        sb.append("  \"exportedAt\": ").append(System.currentTimeMillis()).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
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
            addToZip(zos, "css/style.css", result.cssContent);
            addToZip(zos, "js/script.js", result.jsContent);
            addToZip(zos, "project.json", generateProjectManifest(projectName));

            // Empty placeholder folders so a zero-asset export still has the
            // canonical project shape on disk after extraction.
            addToZip(zos, "pages/.keep", "");
            addToZip(zos, "components/.keep", "");
            addToZip(zos, "fonts/.keep", "");

            // Include project data files for full backup
            includeProjectData(zos, projectId);

            // Include assets
            includeAssets(zos, projectId);
        }
        return zipFile;
    }

    /**
     * Include all project data files in the ZIP export:
     * layout JSON, theme, logic blocks, page layouts, metadata, icon-library
     * config, custom components, and animation presets.
     */
    private void includeProjectData(ZipOutputStream zos, String projectId) {
        File dir = new File(context.getFilesDir(), "projects");
        if (!dir.exists()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName();
            if (name.startsWith(projectId + ".") || name.startsWith(projectId + "_")) {
                try {
                    addFileToZip(zos, "data/" + name, file);
                } catch (IOException e) {
                    Log.w("ExportManager", "Could not add " + name + " to zip: " + e.getMessage());
                }
            }
        }

        // Workspace-shared files that aren't keyed by projectId. They live in
        // the same folder so they round-trip with the rest of the project.
        for (String shared : new String[]{
                projectId + ".icons",
                projectId + ".components.json",
                projectId + ".animations.json",
                projectId + ".breakpoints.json"}) {
            File f = new File(dir, shared);
            if (f.exists()) {
                try { addFileToZip(zos, "data/" + shared, f); }
                catch (IOException e) { /* best-effort */ }
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

    private String generateHtml(View screen, String projectName, CustomBlockManager customBlockManager,
                                LogicBlockManager logicBlockManager) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <meta name=\"description\" content=\"")
            .append(escapeHtml(projectName)).append("\">\n");
        html.append("  <meta name=\"generator\" content=\"DragWeb\">\n");
        html.append("  <title>").append(escapeHtml(projectName)).append("</title>\n");

        // ASD <meta> additions go before icon CDN includes.
        if (logicBlockManager != null) {
            String asdMeta = logicBlockManager.generateAsdSource("meta");
            if (asdMeta != null && !asdMeta.trim().isEmpty()) {
                html.append("  ").append(asdMeta.replace("\n", "\n  ")).append("\n");
            }
        }

        if (iconLibraryManager != null) {
            String includes = iconLibraryManager.generateHtmlIncludes();
            if (includes != null && !includes.isEmpty()) html.append(includes);
        }
        if (animationLibraryManager != null) {
            String includes = animationLibraryManager.generateHtmlIncludes();
            if (includes != null && !includes.isEmpty()) html.append(includes);
        }
        html.append("  <link rel=\"stylesheet\" href=\"css/style.css\">\n");

        // ASD <head> source (e.g. extra <link> or <script src> tags) goes last
        // so it can override anything emitted above.
        if (logicBlockManager != null) {
            String asdHead = logicBlockManager.generateAsdSource("head");
            if (asdHead != null && !asdHead.trim().isEmpty()) {
                html.append("  ").append(asdHead.replace("\n", "\n  ")).append("\n");
            }
        }

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

        // ASD raw-HTML source blocks (authored in the Logic Blocks editor).
        if (logicBlockManager != null) {
            String asdHtml = logicBlockManager.generateAsdSource("html");
            if (asdHtml != null && !asdHtml.trim().isEmpty()) {
                html.append("\n  <!-- ASD HTML source -->\n  ");
                html.append(asdHtml.replace("\n", "\n  "));
                html.append("\n");
            }
        }

        html.append("\n  <script src=\"js/script.js\" defer></script>\n");
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

        // Generated class name. Any user-authored class lives alongside it so
        // pseudo/state rules from the logic block manager still match.
        String generatedClass = "el-" + tag + "-" + Math.abs(view.hashCode() % 10000);
        StringBuilder classAttr = new StringBuilder(generatedClass);
        if (function.containsKey("class")) {
            String userClass = String.valueOf(function.get("class")).trim();
            if (!userClass.isEmpty()) classAttr.append(' ').append(userClass);
        }
        html.append(" class=\"").append(escapeHtml(classAttr.toString())).append("\"");

        if (function.containsKey("id")) {
            String elId = String.valueOf(function.get("id")).trim();
            if (!elId.isEmpty()) html.append(" id=\"").append(escapeHtml(elId)).append("\"");
        }

        // Style emission. When the project's "inline HTML styles" toggle is
        // enabled (ThemeManager.isUseInlineStyles, default = true) we emit
        // a style="..." attribute on the element. When disabled, we keep the
        // historic behaviour of pushing the rule into css/style.css under the
        // element's generated class.
        Map<String, Object> style = (Map<String, Object>) function.get("style");
        boolean inlineStyles = themeManager != null && themeManager.isUseInlineStyles();
        if (style != null && !style.isEmpty()) {
            if (inlineStyles) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Object> entry : style.entrySet()) {
                    sb.append(camelToKebab(entry.getKey())).append(": ")
                      .append(entry.getValue()).append("; ");
                }
                html.append(" style=\"").append(escapeHtml(sb.toString().trim())).append("\"");
            } else {
                elementCssBuffer.append('.').append(generatedClass).append(" {\n");
                for (Map.Entry<String, Object> entry : style.entrySet()) {
                    String cssKey = camelToKebab(entry.getKey());
                    elementCssBuffer.append("  ").append(cssKey).append(": ")
                        .append(entry.getValue()).append(";\n");
                }
                elementCssBuffer.append("}\n");
            }
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
        if (animationLibraryManager != null) {
            css.append(animationLibraryManager.generateLocalKeyframesCss(""));
        } else {
            css.append(AnimationLibrary.generateKeyframesCss(""));
        }
        css.append("\n");

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

        // ASD raw CSS source authored in the Logic Blocks editor.
        if (logicBlockManager != null) {
            String asdCss = logicBlockManager.generateAsdSource("css");
            if (asdCss != null && !asdCss.trim().isEmpty()) {
                css.append("/* ASD CSS source */\n");
                css.append(asdCss);
                css.append("\n");
            }
        }

        css.append("/* Element Styles */\n");
        if (elementCssBuffer.length() > 0) {
            css.append(elementCssBuffer);
        }
        return css.toString();
    }

    private String generateJs(LogicBlockManager logicBlockManager) {
        StringBuilder js = new StringBuilder();
        js.append("/* ==========================================================\n");
        js.append(" * DragWeb generated runtime — DO NOT edit by hand.\n");
        js.append(" * Sections: state, init, events, api, animations.\n");
        js.append(" * ========================================================== */\n\n");
        js.append("'use strict';\n\n");

        // ----- state -----
        js.append("/* ----- state ----- */\n");
        js.append("var DW = window.DW = window.DW || { state: {}, components: {} };\n\n");

        // ----- logic blocks JS (already structured by LogicBlockManager) -----
        String logicJs = logicBlockManager != null ? logicBlockManager.generateJavaScript() : "";
        if (logicJs != null && !logicJs.isEmpty()) {
            js.append("/* ----- logic blocks ----- */\n");
            js.append(logicJs).append("\n");
        }

        // ----- ASD raw JS source authored in the Logic Blocks editor -----
        String asdJs = logicBlockManager != null ? logicBlockManager.generateAsdSource("js") : "";
        if (asdJs != null && !asdJs.trim().isEmpty()) {
            js.append("\n/* ----- user JS (ASD) ----- */\n");
            js.append(asdJs).append("\n");
        }

        // ----- init -----
        js.append("/* ----- init ----- */\n");
        js.append("document.addEventListener('DOMContentLoaded', function() {\n");
        js.append("  // Re-initialise icon libraries (no-op if not loaded).\n");
        js.append("  if (window.feather) try { feather.replace(); } catch (e) {}\n");
        js.append("  if (window.lucide)  try { lucide.createIcons(); } catch (e) {}\n");
        js.append("});\n");
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
