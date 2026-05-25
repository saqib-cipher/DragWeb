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

        // Clean website-source layout: index.html, css/, js/, assets/.
        // No project.json / pages / components / fonts / data — those are
        // editor-internal and don't belong in a published source backup.
        File exportDir = new File(context.getFilesDir(), "exports/" + sanitizeFileName(projectName));
        if (!exportDir.exists()) exportDir.mkdirs();
        File cssDir = new File(exportDir, "css");
        File jsDir = new File(exportDir, "js");
        File assetsDir = new File(exportDir, "assets");
        for (File d : new File[]{cssDir, jsDir, assetsDir}) {
            if (!d.exists()) d.mkdirs();
        }
        result.exportDir = exportDir;

        try {
            writeFile(new File(exportDir, "index.html"), htmlContent);
            writeFile(new File(cssDir, "style.css"), cssContent);
            writeFile(new File(jsDir, "script.js"), jsContent);
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

        // Clean website-source zip: HTML, CSS, JS, and assets only. Editor
        // internals (project.json, data/, components/, pages/, fonts/) used
        // to ship here too, but that confused users importing the zip as a
        // plain website backup — now omitted.
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            addToZip(zos, "index.html", result.htmlContent);
            addToZip(zos, "css/style.css", result.cssContent);
            addToZip(zos, "js/script.js", result.jsContent);
            includeAssets(zos, projectId);
        }
        return zipFile;
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

        // Per-element styles are emitted into the shared CSS buffer instead of
        // an inline style="" attribute so the final output keeps every rule
        // together in css/style.css. The exception is empty style maps, which
        // we just skip.
        Map<String, Object> style = (Map<String, Object>) function.get("style");
        if (style != null && !style.isEmpty()) {
            elementCssBuffer.append('.').append(generatedClass).append(" {\n");
            for (Map.Entry<String, Object> entry : style.entrySet()) {
                String cssKey = camelToKebab(entry.getKey());
                elementCssBuffer.append("  ").append(cssKey).append(": ")
                    .append(entry.getValue()).append(";\n");
            }
            elementCssBuffer.append("}\n");
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
        css.append(themeManager.generateGlobalCss(screen));
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

    // Backwards-compat import: extracts a legacy backup zip (which used to
    // ship data/, assets/, and project.json) into the editor's project store.
    // New exports no longer write data/, but old zips still round-trip here.
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
