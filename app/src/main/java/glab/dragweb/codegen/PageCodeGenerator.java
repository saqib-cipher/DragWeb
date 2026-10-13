package glab.dragweb.codegen;

import glab.dragweb.R;
import glab.dragweb.activities.*;
import glab.dragweb.fragments.*;
import glab.dragweb.engine.*;
import glab.dragweb.ui.*;
import glab.dragweb.logic.*;
import glab.dragweb.codegen.*;
import glab.dragweb.data.*;
import glab.dragweb.adapters.*;
import glab.dragweb.models.*;
import glab.dragweb.util.*;
import glab.dragweb.colorpicker.*;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PageCodeGenerator {

    // Project info for default header
    private String projectName = "";
    private String projectLogoPath = "";
    private String projectId = "";
    private Context context;
    private IconLibraryManager iconLibraryManager;
    private AnimationLibraryManager animationLibraryManager;
    private GoogleFontsManager googleFontsManager;
    private String currentPageName = "";
    private ThemeManager themeManager;
    private final java.util.Set<String> collectedScripts = new java.util.LinkedHashSet<>();

    public PageCodeGenerator() {}

    public void setProjectInfo(String name, String logoPath) {
        this.projectName = name != null ? name : "";
        this.projectLogoPath = logoPath != null ? logoPath : "";
    }

    public void setProjectId(String id) {
        this.projectId = id;
    }

    public void setContext(Context ctx) {
        this.context = ctx;
    }

    /** Optional icon library configuration whose CDN tags will be injected. */
    public void setIconLibraryManager(IconLibraryManager m) {
        this.iconLibraryManager = m;
    }

    public void setAnimationLibraryManager(AnimationLibraryManager m) {
        this.animationLibraryManager = m;
    }

    public void setGoogleFontsManager(GoogleFontsManager m) {
        this.googleFontsManager = m;
    }

    public void setCurrentPageName(String name) {
        this.currentPageName = name != null ? name : "";
    }

    public String generateAllCode(View screen) {
        return generateFullCode(screen, null, null, null);
    }

    public String generateFullCode(View screen, ThemeManager themeManager, LogicBlockManager logicBlockManager) {
        return generateFullCode(screen, themeManager, logicBlockManager, null);
    }

    public String generateFullCode(View screen, ThemeManager themeManager,
                                   LogicBlockManager logicBlockManager,
                                   ManageBlocksWidgets customBlockManager) {
        this.themeManager = themeManager;
        collectedScripts.clear();
        StringBuilder bodyBuilder = new StringBuilder();

        if (screen instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) screen;
            for (int i = 0; i < vg.getChildCount(); i++) {
                bodyBuilder.append(generateHtmlForView(vg.getChildAt(i), 1));
            }
        }

        // Custom-block HTML is appended after the main widget tree so it
        // becomes part of the page body (e.g. navbar items added via the
        // custom-block engine).
        if (customBlockManager != null) {
            String customHtml = customBlockManager.renderAllHtml();
            if (customHtml != null && !customHtml.trim().isEmpty()) {
                bodyBuilder.append("  <!-- Custom blocks -->\n  ");
                bodyBuilder.append(customHtml.replace("\n", "\n  "));
                bodyBuilder.append("\n");
            }
        }

        // Append any ASD raw-HTML source blocks the user authored.
        if (logicBlockManager != null) {
            String asdHtml = logicBlockManager.generateAsdSource("html");
            if (asdHtml != null && !asdHtml.trim().isEmpty()) {
                bodyBuilder.append("  <!-- ASD HTML source -->\n  ");
                bodyBuilder.append(asdHtml.replace("\n", "\n  "));
                bodyBuilder.append("\n");
            }
        }

        // Compile HTML blocks (category "html") into the body
        if (logicBlockManager != null && currentPageName != null && !currentPageName.isEmpty()) {
            String pageHtmlBlocks = compileHtmlBlocks(currentPageName);
            if (pageHtmlBlocks != null && !pageHtmlBlocks.trim().isEmpty()) {
                bodyBuilder.append("  <!-- HTML Blocks -->\n  ");
                bodyBuilder.append(pageHtmlBlocks.replace("\n", "\n  "));
                bodyBuilder.append("\n");
            }
        }

        StringBuilder htmlBuilder = new StringBuilder();
        appendHtmlHeader(htmlBuilder, themeManager, logicBlockManager);
        htmlBuilder.append(bodyBuilder);
        appendHtmlFooter(htmlBuilder, themeManager, logicBlockManager, customBlockManager);
        return htmlBuilder.toString();
    }

    /**
     * Generate full HTML code from a serialized widget tree (JSON data).
     * This allows generating code for pages that aren't currently loaded on screen.
     */
    public String generateFullCodeFromTree(List<Map<String, Object>> widgetTree, ThemeManager themeManager, LogicBlockManager logicBlockManager) {
        return generateFullCodeFromTree(widgetTree, themeManager, logicBlockManager, null);
    }

    public String generateFullCodeFromTree(List<Map<String, Object>> widgetTree,
                                           ThemeManager themeManager,
                                           LogicBlockManager logicBlockManager,
                                           ManageBlocksWidgets customBlockManager) {
        this.themeManager = themeManager;
        collectedScripts.clear();
        StringBuilder bodyBuilder = new StringBuilder();

        if (widgetTree != null) {
            for (Map<String, Object> nodeMap : widgetTree) {
                bodyBuilder.append(generateHtmlForNode(nodeMap, 1));
            }
        }

        if (customBlockManager != null) {
            String customHtml = customBlockManager.renderAllHtml();
            if (customHtml != null && !customHtml.trim().isEmpty()) {
                bodyBuilder.append("  <!-- Custom blocks -->\n  ");
                bodyBuilder.append(customHtml.replace("\n", "\n  "));
                bodyBuilder.append("\n");
            }
        }

        if (logicBlockManager != null) {
            String asdHtml = logicBlockManager.generateAsdSource("html");
            if (asdHtml != null && !asdHtml.trim().isEmpty()) {
                bodyBuilder.append("  <!-- ASD HTML source -->\n  ");
                bodyBuilder.append(asdHtml.replace("\n", "\n  "));
                bodyBuilder.append("\n");
            }
        }

        // Compile HTML blocks (category "html") into the body
        if (logicBlockManager != null && currentPageName != null && !currentPageName.isEmpty()) {
            String pageHtmlBlocks = compileHtmlBlocks(currentPageName);
            if (pageHtmlBlocks != null && !pageHtmlBlocks.trim().isEmpty()) {
                bodyBuilder.append("  <!-- HTML Blocks -->\n  ");
                bodyBuilder.append(pageHtmlBlocks.replace("\n", "\n  "));
                bodyBuilder.append("\n");
            }
        }

        StringBuilder htmlBuilder = new StringBuilder();
        appendHtmlHeader(htmlBuilder, themeManager, logicBlockManager);
        htmlBuilder.append(bodyBuilder);
        appendHtmlFooter(htmlBuilder, themeManager, logicBlockManager, customBlockManager);
        return htmlBuilder.toString();
    }

    private String compileHtmlBlocks(String pageName) {
        if (projectId == null || projectId.isEmpty() || context == null) return "";
        String cleanName = DesignDataManager.getCleanPageName(pageName);
        java.io.File logicFile = new java.io.File(FileUtil.getDragWebDir(), "projects/" + projectId + "/" + cleanName + "_logic.json");
        if (!logicFile.exists()) return "";
        try {
            String content = FileUtil.readFile(logicFile.getAbsolutePath());
            if (content == null || content.trim().isEmpty()) return "";
            DesignDataManager.PageLogicData data = DesignDataManager.deserializePageLogicData(content, pageName);
            if (data == null || data.blocks == null || data.blocks.isEmpty()) return "";
            BlockCodeCompiler compiler = new BlockCodeCompiler(context, projectId);
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, java.util.ArrayList<BlockBean>> entry : data.blocks.entrySet()) {
                String key = entry.getKey();
                if (key != null && (key.startsWith("func_") || key.contains("moreBlock"))) continue;
                java.util.ArrayList<BlockBean> htmlBlocks = new java.util.ArrayList<>();
                for (BlockBean b : entry.getValue()) {
                    if (b != null && "html".equals(b.category)) {
                        htmlBlocks.add(b);
                    }
                }
                if (htmlBlocks.isEmpty()) continue;
                String code = compiler.getSource(0, htmlBlocks);
                if (code != null && !code.trim().isEmpty()) {
                    sb.append(code).append("\n\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private void appendHtmlHeader(StringBuilder htmlBuilder, ThemeManager themeManager) {
        appendHtmlHeader(htmlBuilder, themeManager, null);
    }

    private void appendHtmlHeader(StringBuilder htmlBuilder, ThemeManager themeManager,
                                  LogicBlockManager logicBlockManager) {
        String pageTitle = projectName.isEmpty() ? "DragWeb Page" : escapeHtml(projectName);
        htmlBuilder.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        htmlBuilder.append("  <meta charset=\"UTF-8\">\n");
        htmlBuilder.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        htmlBuilder.append("  <title>").append(pageTitle).append("</title>\n");

        // ASD <meta> additions go before user styles.
        if (logicBlockManager != null) {
            String asdMeta = logicBlockManager.generateAsdSource("meta");
            if (asdMeta != null && !asdMeta.trim().isEmpty()) {
                htmlBuilder.append("  ").append(asdMeta.replace("\n", "\n  ")).append("\n");
            }
        }

        // External icon-library CDN includes (Font Awesome, Material Icons, …).
        if (iconLibraryManager != null) {
            String includes = iconLibraryManager.generateHtmlIncludes();
            if (includes != null && !includes.isEmpty()) htmlBuilder.append(includes);
        }
        if (animationLibraryManager != null) {
            String includes = animationLibraryManager.generateHtmlIncludes();
            if (includes != null && !includes.isEmpty()) htmlBuilder.append(includes);
        }

        // Google Fonts
        if (googleFontsManager != null) {
            String fonts = googleFontsManager.generateGoogleFontsLinkTag(currentPageName.isEmpty() ? null : currentPageName);
            if (fonts != null && !fonts.isEmpty()) htmlBuilder.append("  ").append(fonts);
        }

        // ASD <head> source goes last so it can override anything above.
        if (logicBlockManager != null) {
            String asdHead = logicBlockManager.generateAsdSource("head");
            if (asdHead != null && !asdHead.trim().isEmpty()) {
                htmlBuilder.append("  ").append(asdHead.replace("\n", "\n  ")).append("\n");
            }
        }

        htmlBuilder.append("</head>\n<body>\n");
    }

    private void appendHtmlFooter(StringBuilder htmlBuilder,
                                  LogicBlockManager logicBlockManager,
                                  ManageBlocksWidgets customBlockManager) {
        appendHtmlFooter(htmlBuilder, null, logicBlockManager, customBlockManager);
    }

    private void appendHtmlFooter(StringBuilder htmlBuilder,
                                  ThemeManager themeManager,
                                  LogicBlockManager logicBlockManager,
                                  ManageBlocksWidgets customBlockManager) {
        // Consolidated <style> block at the end of body as requested.
        htmlBuilder.append("\n  <style>\n");

        // Theme CSS variables
        if (themeManager != null) {
            htmlBuilder.append(themeManager.generateGlobalCss());
        }

        // Resets and Utilities
        if (themeManager == null || !themeManager.isDisableDefaultStyles()) {
            htmlBuilder.append("    * { margin: 0; padding: 0; box-sizing: border-box; }\n");
            htmlBuilder.append("    body { font-family: sans-serif; line-height: 1.6; }\n");
            htmlBuilder.append("    .hidden { display: none !important; }\n");
            htmlBuilder.append("    .flex { display: flex; }\n");
            htmlBuilder.append("    .flex-col { flex-direction: column; }\n");
            htmlBuilder.append("    .flex-row { flex-direction: row; }\n");
            htmlBuilder.append("    .items-center { align-items: center; }\n");
            htmlBuilder.append("    .justify-center { justify-content: center; }\n");
            htmlBuilder.append("    .justify-between { justify-content: space-between; }\n");
            htmlBuilder.append("    .text-center { text-align: center; }\n");
            htmlBuilder.append("    .w-full { width: 100%; }\n");
            htmlBuilder.append("    .h-full { height: 100%; }\n");
            htmlBuilder.append("    button { cursor: pointer; font-family: inherit; }\n");
            htmlBuilder.append("    input, textarea { font-family: inherit; }\n");
            htmlBuilder.append("    img { max-width: 100%; height: auto; }\n");
        } else {
            htmlBuilder.append("    .hidden { display: none !important; }\n");
        }

        // Keyframe library
        if (animationLibraryManager != null) {
            htmlBuilder.append(animationLibraryManager.generateLocalKeyframesCss("    "));
        } else {
            htmlBuilder.append(AnimationLibrary.generateKeyframesCss("    "));
        }

        // Logic blocks and user CSS
        if (logicBlockManager != null) {
            String baseCss = logicBlockManager.generateBaseCssRules();
            if (baseCss != null && !baseCss.trim().isEmpty()) {
                htmlBuilder.append("\n    /* Logic block styles */\n").append(baseCss).append("\n");
            }
            String pseudoCss = logicBlockManager.generateCssPseudoRules();
            if (pseudoCss != null && !pseudoCss.trim().isEmpty()) {
                htmlBuilder.append("\n    /* Pseudo class styles */\n").append(pseudoCss).append("\n");
            }
        }

        htmlBuilder.append("  </style>\n");

        // Logic blocks and widget scripts
        if (logicBlockManager != null) {
            String jsCode = logicBlockManager.generateJavaScript();
            if (jsCode == null || jsCode.trim().isEmpty()) {
                String projId = DesignDataManager.currentProjectId;
                if (projId != null && !projId.isEmpty()) {
                    jsCode = ProjectCodeGenerator.compileJsForFile(logicBlockManager.getContext(), projId, "js/script.js");
                }
            }
            if (jsCode != null && !jsCode.trim().isEmpty()) {
                htmlBuilder.append("\n  <script>\n").append(jsCode).append("\n  </script>\n");
            }
        }
        if (!collectedScripts.isEmpty()) {
            StringBuilder wsSb = new StringBuilder();
            String curDoc = htmlBuilder.toString();
            for (String wScript : collectedScripts) {
                if (wScript == null || wScript.trim().isEmpty()) continue;
                if (ExportManager.isScriptReferenced(wScript, curDoc, curDoc)) {
                    wsSb.append(wScript).append("\n\n");
                }
            }
            if (wsSb.length() > 0) {
                htmlBuilder.append("\n  <script>\n").append(wsSb).append("  </script>\n");
            }
        }

        htmlBuilder.append("</body>\n</html>");
    }

    /**
     * Generate HTML from a serialized widget node (Map data from JSON).
     */
    @SuppressWarnings("unchecked")
    private String generateHtmlForNode(Map<String, Object> nodeMap, int indentLevel) {
        if (nodeMap == null) return "";

        String indent = repeat("  ", indentLevel);
        String tag = nodeMap.containsKey("tag") ? nodeMap.get("tag").toString() : "div";

        Map<String, Object> function = nodeMap.containsKey("function") ? (Map<String, Object>) nodeMap.get("function") : null;
        if (function == null) return "";
        collectWidgetScript(function);

        StringBuilder html = new StringBuilder();
        html.append(indent).append("<").append(tag);

        // ID attribute
        if (function.containsKey("id") && function.get("id") != null) {
            String idVal = function.get("id").toString().trim();
            if (!idVal.isEmpty()) {
                html.append(" id=\"").append(escapeAttr(idVal)).append("\"");
            }
        }

        // Inline style attribute (replaces dw-s classes for better readability)
        Map<String, Object> style = function.containsKey("style") ? (Map<String, Object>) function.get("style") : null;
        if (style != null && !style.isEmpty() && (themeManager == null || themeManager.isUseInlineStyles())) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> entry : style.entrySet()) {
                String val = entry.getValue() != null ? entry.getValue().toString().trim() : "";
                if (val.isEmpty()) continue;
                sb.append(camelToKebab(entry.getKey())).append(": ").append(val).append("; ");
            }
            html.append(" style=\"").append(escapeAttr(sb.toString().trim())).append("\"");
        }

        // Class attribute
        if (function.containsKey("class") && function.get("class") != null) {
            String classVal = function.get("class").toString().trim();
            if (!classVal.isEmpty()) {
                html.append(" class=\"").append(escapeAttr(classVal)).append("\"");
            }
        }

        // JS Event attributes
        for (Map.Entry<String, Object> entry : function.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("on") && entry.getValue() != null) {
                String val = String.valueOf(entry.getValue()).trim();
                if (!val.isEmpty()) {
                    html.append(" ").append(key).append("=\"").append(escapeAttr(val)).append("\"");
                }
            }
        }



        // Tag-specific attributes
        if ("img".equals(tag) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeAttr(resolveAssetPath(function.get("src").toString()))).append("\"");
            html.append(" alt=\"Image\"");
        }
        if (("input".equals(tag) || "textarea".equals(tag)) && function.containsKey("type")) {
            html.append(" type=\"").append(escapeAttr(function.get("type").toString())).append("\"");
        }
        if (("input".equals(tag) || "textarea".equals(tag)) && function.containsKey("placeholder")) {
            html.append(" placeholder=\"").append(escapeAttr(function.get("placeholder").toString())).append("\"");
        }
        if ("a".equals(tag) && function.containsKey("href")) {
            html.append(" href=\"").append(escapeAttr(function.get("href").toString())).append("\"");
        }
        if (("video".equals(tag) || "audio".equals(tag)) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeAttr(function.get("src").toString())).append("\"");
        }
        if (("video".equals(tag) || "audio".equals(tag)) && "true".equals(String.valueOf(function.get("controls")))) {
            html.append(" controls");
        }
        if ("iframe".equals(tag) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeAttr(function.get("src").toString())).append("\"");
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

        // Recursive children
        StringBuilder childrenHtml = new StringBuilder();
        if (nodeMap.containsKey("children")) {
            Object childrenObj = nodeMap.get("children");
            if (childrenObj instanceof List) {
                List<Map<String, Object>> children = (List<Map<String, Object>>) childrenObj;
                for (Map<String, Object> child : children) {
                    String ch = generateHtmlForNode(child, indentLevel + 1);
                    if (ch != null && !ch.trim().isEmpty()) {
                        childrenHtml.append(ch);
                    }
                }
            }
        }
        if (childrenHtml.length() > 0) {
            html.append("\n").append(childrenHtml).append(indent);
        } else {
            appendGeneratedChildrenForItems(html, tag, function, indent);
        }

        html.append("</").append(tag).append(">\n");
        return html.toString();
    }

    private String generateHtmlForView(View view, int indentLevel) {
        if (view == null) return "";

        String indent = repeat("  ", indentLevel);

        Object tagObj = view.getTag();
        if (!(tagObj instanceof Map)) {
            return "";
        }

        Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
        String tag = widgetMap.containsKey("tag") ? widgetMap.get("tag").toString() : "div";

        Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
        if (function == null) return "";
        collectWidgetScript(function);

        StringBuilder html = new StringBuilder();
        html.append(indent).append("<").append(tag);

        // ID attribute for logic targeting
        if (function.containsKey("id") && function.get("id") != null) {
            String idVal = function.get("id").toString().trim();
            if (!idVal.isEmpty()) {
                html.append(" id=\"").append(escapeAttr(idVal)).append("\"");
            }
        }

        // Inline style attribute (replaces dw-s classes for better readability)
        Map<String, Object> style = (Map<String, Object>) function.get("style");
        if (style != null && !style.isEmpty() && (themeManager == null || themeManager.isUseInlineStyles())) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> entry : style.entrySet()) {
                String val = entry.getValue() != null ? entry.getValue().toString().trim() : "";
                if (val.isEmpty()) continue;
                sb.append(camelToKebab(entry.getKey())).append(": ").append(val).append("; ");
            }
            html.append(" style=\"").append(escapeAttr(sb.toString().trim())).append("\"");
        }

        // Class attribute for logic targeting
        if (function.containsKey("class") && function.get("class") != null) {
            String classVal = function.get("class").toString().trim();
            if (!classVal.isEmpty()) {
                html.append(" class=\"").append(escapeAttr(classVal)).append("\"");
            }
        }

        // JS Event attributes
        for (Map.Entry<String, Object> entry : function.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("on") && entry.getValue() != null) {
                String val = String.valueOf(entry.getValue()).trim();
                if (!val.isEmpty()) {
                    html.append(" ").append(key).append("=\"").append(escapeAttr(val)).append("\"");
                }
            }
        }



        // Tag-specific attributes
        if ("img".equals(tag) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeAttr(resolveAssetPath(function.get("src").toString()))).append("\"");
            html.append(" alt=\"Image\"");
        }
        if (("input".equals(tag) || "textarea".equals(tag)) && function.containsKey("type")) {
            html.append(" type=\"").append(escapeAttr(function.get("type").toString())).append("\"");
        }
        if (("input".equals(tag) || "textarea".equals(tag)) && function.containsKey("placeholder")) {
            html.append(" placeholder=\"").append(escapeAttr(function.get("placeholder").toString())).append("\"");
        }
        if ("a".equals(tag) && function.containsKey("href")) {
            html.append(" href=\"").append(escapeAttr(function.get("href").toString())).append("\"");
        }
        if (("video".equals(tag) || "audio".equals(tag)) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeAttr(function.get("src").toString())).append("\"");
        }
        if (("video".equals(tag) || "audio".equals(tag)) && "true".equals(String.valueOf(function.get("controls")))) {
            html.append(" controls");
        }
        if ("iframe".equals(tag) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeAttr(function.get("src").toString())).append("\"");
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

        // Recursive children
        StringBuilder childrenHtml = new StringBuilder();
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                String ch = generateHtmlForView(vg.getChildAt(i), indentLevel + 1);
                if (ch != null && !ch.trim().isEmpty()) {
                    childrenHtml.append(ch);
                }
            }
        }
        if (childrenHtml.length() > 0) {
            html.append("\n").append(childrenHtml).append(indent);
        } else {
            appendGeneratedChildrenForItems(html, tag, function, indent);
        }

        html.append("</").append(tag).append(">\n");
        return html.toString();
    }

    private String camelToKebab(String str) {
        return str.replaceAll("([A-Z])", "-$1").toLowerCase();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    private String escapeAttr(String text) {
        return text.replace("&", "&amp;")
                   .replace("\"", "&quot;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    private String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }



    private void collectWidgetScript(Map<String, Object> function) {
        if (function == null) return;
        String s = function.containsKey("script:js") && function.get("script:js") != null
                ? String.valueOf(function.get("script:js"))
                : (function.containsKey("script") && function.get("script") != null ? String.valueOf(function.get("script")) : null);
        if (s != null && !s.trim().isEmpty()) {
            collectedScripts.add(s.trim());
        }
    }

    private void appendGeneratedChildrenForItems(StringBuilder html, String tag, Map<String, Object> function, String indent) {
        if (function == null) return;
        if ("ul".equals(tag) || "ol".equals(tag)) {
            if (function.containsKey("items") && function.get("items") instanceof List) {
                List<?> items = (List<?>) function.get("items");
                if (!items.isEmpty()) {
                    html.append("\n");
                    for (Object item : items) {
                        if (item != null) {
                            html.append(indent).append("  <li>").append(escapeHtml(item.toString())).append("</li>\n");
                        }
                    }
                    html.append(indent);
                }
            }
        } else if ("select".equals(tag)) {
            if (function.containsKey("items") && function.get("items") instanceof List) {
                List<?> items = (List<?>) function.get("items");
                if (!items.isEmpty()) {
                    html.append("\n");
                    for (Object item : items) {
                        if (item != null) {
                            String v = item.toString().toLowerCase().replace(" ", "-");
                            html.append(indent).append("  <option value=\"").append(escapeAttr(v)).append("\">")
                                .append(escapeHtml(item.toString())).append("</option>\n");
                        }
                    }
                    html.append(indent);
                }
            }
        } else if ("nav".equals(tag)) {
            if (function.containsKey("items") && function.get("items") instanceof List) {
                html.append("\n");
                if (function.containsKey("brand") && function.get("brand") != null) {
                    html.append(indent).append("  <span class=\"nav-brand\" style=\"font-weight: bold;\">")
                        .append(escapeHtml(String.valueOf(function.get("brand")))).append("</span>\n");
                }
                List<?> items = (List<?>) function.get("items");
                List<?> icons = (function.containsKey("icons") && function.get("icons") instanceof List)
                    ? (List<?>) function.get("icons") : null;
                List<?> hrefs = (function.containsKey("hrefs") && function.get("hrefs") instanceof List)
                    ? (List<?>) function.get("hrefs") : ((function.containsKey("links") && function.get("links") instanceof List) ? (List<?>) function.get("links") : null);
                for (int i = 0; i < items.size(); i++) {
                    Object item = items.get(i);
                    if (item != null) {
                        String targetHref = "#";
                        if (hrefs != null && i < hrefs.size() && hrefs.get(i) != null) {
                            String h = hrefs.get(i).toString().trim();
                            if (!h.isEmpty()) targetHref = h;
                        }
                        String iconName = (icons != null && i < icons.size() && icons.get(i) != null)
                            ? icons.get(i).toString().trim() : "";
                        if (!iconName.isEmpty()) {
                            html.append(indent).append("  <a href=\"").append(escapeAttr(targetHref)).append("\" class=\"nav-item\" style=\"margin: 0 8px; text-decoration: none; color: inherit; display: inline-flex; align-items: center; gap: 6px;\">")
                                .append("<i data-lucide=\"").append(escapeHtml(iconName)).append("\" style=\"width: 16px; height: 16px;\"></i> ")
                                .append(escapeHtml(item.toString())).append("</a>\n");
                        } else {
                            html.append(indent).append("  <a href=\"").append(escapeAttr(targetHref)).append("\" class=\"nav-item\" style=\"margin: 0 8px; text-decoration: none; color: inherit;\">")
                                .append(escapeHtml(item.toString())).append("</a>\n");
                        }
                    }
                }
                html.append(indent);
            }
        } else if ("table".equals(tag)) {
            if (function.containsKey("headers") && function.get("headers") instanceof List) {
                List<?> headers = (List<?>) function.get("headers");
                html.append("\n").append(indent).append("  <thead>\n").append(indent).append("    <tr>\n");
                for (Object h : headers) {
                    if (h != null) {
                        html.append(indent).append("      <th style=\"padding: 8px 12px; border: 1px solid #E2E8F0;\">")
                            .append(escapeHtml(h.toString())).append("</th>\n");
                    }
                }
                html.append(indent).append("    </tr>\n").append(indent).append("  </thead>\n");
            }
            if (function.containsKey("rows") && function.get("rows") instanceof List) {
                List<?> rows = (List<?>) function.get("rows");
                html.append(indent).append("  <tbody>\n");
                for (Object rObj : rows) {
                    if (rObj instanceof List) {
                        html.append(indent).append("    <tr>\n");
                        for (Object cell : (List<?>) rObj) {
                            html.append(indent).append("      <td style=\"padding: 8px 12px; border: 1px solid #E2E8F0;\">")
                                .append(escapeHtml(cell != null ? cell.toString() : "")).append("</td>\n");
                        }
                        html.append(indent).append("    </tr>\n");
                    }
                }
                html.append(indent).append("  </tbody>\n").append(indent);
            }
        }
    }

    private String resolveAssetPath(String rawSrc) {
        if (rawSrc == null) return "";
        if (rawSrc.startsWith("data:")) return rawSrc;
        if (rawSrc.startsWith("assets/")) return rawSrc;
        int idx = rawSrc.indexOf("/assets/");
        if (idx >= 0) {
            return "assets/" + rawSrc.substring(idx + "/assets/".length());
        }
        return rawSrc;
    }
}
