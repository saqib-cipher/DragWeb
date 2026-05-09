package sketchweb.gl;

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
    private final Map<String, String> styleClassMap = new HashMap<>();
    private final List<String> styleRules = new ArrayList<>();
    private int styleClassCounter = 1;
    private IconLibraryManager iconLibraryManager;
    private AnimationLibraryManager animationLibraryManager;

    public PageCodeGenerator() {}

    public void setProjectInfo(String name, String logoPath) {
        this.projectName = name != null ? name : "";
        this.projectLogoPath = logoPath != null ? logoPath : "";
    }

    /** Optional icon library configuration whose CDN tags will be injected. */
    public void setIconLibraryManager(IconLibraryManager m) {
        this.iconLibraryManager = m;
    }

    public void setAnimationLibraryManager(AnimationLibraryManager m) {
        this.animationLibraryManager = m;
    }

    public String generateAllCode(View screen) {
        return generateFullCode(screen, null, null, null);
    }

    public String generateFullCode(View screen, ThemeManager themeManager, LogicBlockManager logicBlockManager) {
        return generateFullCode(screen, themeManager, logicBlockManager, null);
    }

    public String generateFullCode(View screen, ThemeManager themeManager,
                                   LogicBlockManager logicBlockManager,
                                   CustomBlockManager customBlockManager) {
        resetStyleCache();
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

        StringBuilder htmlBuilder = new StringBuilder();
        appendHtmlHeader(htmlBuilder, themeManager, logicBlockManager);
        htmlBuilder.append(bodyBuilder);
        appendHtmlFooter(htmlBuilder, logicBlockManager, customBlockManager);
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
                                           CustomBlockManager customBlockManager) {
        resetStyleCache();
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

        StringBuilder htmlBuilder = new StringBuilder();
        appendHtmlHeader(htmlBuilder, themeManager, logicBlockManager);
        htmlBuilder.append(bodyBuilder);
        appendHtmlFooter(htmlBuilder, logicBlockManager, customBlockManager);
        return htmlBuilder.toString();
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

        htmlBuilder.append("  <style>\n");

        // Theme CSS variables
        if (themeManager != null) {
            htmlBuilder.append(themeManager.generateGlobalCss());
        } else {
            htmlBuilder.append("    * { margin: 0; padding: 0; box-sizing: border-box; }\n");
            htmlBuilder.append("    body { font-family: sans-serif; line-height: 1.6; }\n");
        }

        htmlBuilder.append("    button { cursor: pointer; font-family: inherit; }\n");
        htmlBuilder.append("    input, textarea { font-family: inherit; }\n");
        htmlBuilder.append("    img { max-width: 100%; height: auto; }\n");

        // Full keyframe library used by the animation blocks.
        if (animationLibraryManager != null) {
            htmlBuilder.append(animationLibraryManager.generateLocalKeyframesCss("    "));
        } else {
            htmlBuilder.append(AnimationLibrary.generateKeyframesCss("    "));
        }
        for (String styleRule : styleRules) {
            htmlBuilder.append(styleRule);
        }

        htmlBuilder.append("  </style>\n");

        // External icon-library CDN includes (Font Awesome, Material Icons, …).
        if (iconLibraryManager != null) {
            String includes = iconLibraryManager.generateHtmlIncludes();
            if (includes != null && !includes.isEmpty()) htmlBuilder.append(includes);
        }
        if (animationLibraryManager != null) {
            String includes = animationLibraryManager.generateHtmlIncludes();
            if (includes != null && !includes.isEmpty()) htmlBuilder.append(includes);
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

    private void appendHtmlFooter(StringBuilder htmlBuilder, LogicBlockManager logicBlockManager) {
        appendHtmlFooter(htmlBuilder, logicBlockManager, null);
    }

    private void appendHtmlFooter(StringBuilder htmlBuilder,
                                  LogicBlockManager logicBlockManager,
                                  CustomBlockManager customBlockManager) {
        // Emit static CSS rules, pseudo-class rules, custom block styles, and ASD CSS source
        // into a single consolidated <style> block.
        if (logicBlockManager != null || customBlockManager != null) {
            StringBuilder consolidatedCss = new StringBuilder();
            
            if (logicBlockManager != null) {
                String baseCss = logicBlockManager.generateBaseCssRules();
                if (baseCss != null && !baseCss.trim().isEmpty()) {
                    consolidatedCss.append("  /* Logic block styles */\n").append(baseCss).append("\n");
                }

                String pseudoCss = logicBlockManager.generateCssPseudoRules();
                if (pseudoCss != null && !pseudoCss.trim().isEmpty()) {
                    consolidatedCss.append("  /* CSS interaction rules */\n").append(pseudoCss).append("\n");
                }
                
                String asdCss = logicBlockManager.generateAsdSource("css");
                if (asdCss != null && !asdCss.trim().isEmpty()) {
                    consolidatedCss.append("  /* ASD CSS source */\n").append(asdCss).append("\n");
                }
            }

            if (customBlockManager != null) {
                String customCss = customBlockManager.renderAllCss();
                if (customCss != null && !customCss.trim().isEmpty()) {
                    consolidatedCss.append("  /* Custom block styles */\n").append(customCss).append("\n");
                }
            }

            if (consolidatedCss.length() > 0) {
                htmlBuilder.append("\n  <style>\n").append(consolidatedCss).append("  </style>\n");
            }
        }

        String logicJs = logicBlockManager != null ? logicBlockManager.generateJavaScript() : "";
        String asdJs = logicBlockManager != null ? logicBlockManager.generateAsdSource("js") : "";
        boolean hasJs = (logicJs != null && !logicJs.trim().isEmpty())
            || (asdJs != null && !asdJs.trim().isEmpty());
        if (hasJs) {
            htmlBuilder.append("\n  <script>\n");
            htmlBuilder.append("    /* state */\n");
            htmlBuilder.append("    var DW = window.DW = window.DW || { state: {}, components: {} };\n");
            if (logicJs != null && !logicJs.trim().isEmpty()) {
                htmlBuilder.append("    /* logic blocks */\n");
                htmlBuilder.append(logicJs);
            }
            if (asdJs != null && !asdJs.trim().isEmpty()) {
                if (logicJs != null && !logicJs.trim().isEmpty()) htmlBuilder.append("\n");
                htmlBuilder.append("    /* user JS */\n");
                htmlBuilder.append(asdJs).append("\n");
            }
            htmlBuilder.append("  </script>\n");
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

        StringBuilder html = new StringBuilder();
        html.append(indent).append("<").append(tag);

        // ID attribute
        if (function.containsKey("id") && function.get("id") != null) {
            String idVal = function.get("id").toString().trim();
            if (!idVal.isEmpty()) {
                html.append(" id=\"").append(escapeAttr(idVal)).append("\"");
            }
        }

        // Class attribute
        Map<String, Object> style = function.containsKey("style") ? (Map<String, Object>) function.get("style") : null;
        String styleClass = classForStyle(style);
        StringBuilder classes = new StringBuilder();
        if (function.containsKey("class") && function.get("class") != null) {
            String classVal = function.get("class").toString().trim();
            if (!classVal.isEmpty()) {
                classes.append(classVal);
            }
        }
        if (!styleClass.isEmpty()) {
            if (classes.length() > 0) classes.append(" ");
            classes.append(styleClass);
        }
        if (classes.length() > 0) {
            html.append(" class=\"").append(escapeAttr(classes.toString())).append("\"");
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
        if (nodeMap.containsKey("children")) {
            Object childrenObj = nodeMap.get("children");
            if (childrenObj instanceof List) {
                List<Map<String, Object>> children = (List<Map<String, Object>>) childrenObj;
                if (!children.isEmpty()) {
                    html.append("\n");
                    for (Map<String, Object> child : children) {
                        html.append(generateHtmlForNode(child, indentLevel + 1));
                    }
                    html.append(indent);
                }
            }
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

        StringBuilder html = new StringBuilder();
        html.append(indent).append("<").append(tag);

        // ID attribute for logic targeting
        if (function.containsKey("id") && function.get("id") != null) {
            String idVal = function.get("id").toString().trim();
            if (!idVal.isEmpty()) {
                html.append(" id=\"").append(escapeAttr(idVal)).append("\"");
            }
        }

        // Class attribute for logic targeting
        Map<String, Object> style = (Map<String, Object>) function.get("style");
        String styleClass = classForStyle(style);
        StringBuilder classes = new StringBuilder();
        if (function.containsKey("class") && function.get("class") != null) {
            String classVal = function.get("class").toString().trim();
            if (!classVal.isEmpty()) {
                classes.append(classVal);
            }
        }
        if (!styleClass.isEmpty()) {
            if (classes.length() > 0) classes.append(" ");
            classes.append(styleClass);
        }
        if (classes.length() > 0) {
            html.append(" class=\"").append(escapeAttr(classes.toString())).append("\"");
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
        boolean hasChildren = false;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            if (vg.getChildCount() > 0) {
                html.append("\n");
                hasChildren = true;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    html.append(generateHtmlForView(vg.getChildAt(i), indentLevel + 1));
                }
                html.append(indent);
            }
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

    private void resetStyleCache() {
        styleClassMap.clear();
        styleRules.clear();
        styleClassCounter = 1;
    }

    private String classForStyle(Map<String, Object> style) {
        if (style == null || style.isEmpty()) return "";
        TreeMap<String, Object> sorted = new TreeMap<>(style);
        StringBuilder key = new StringBuilder();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            key.append(entry.getKey()).append("=").append(String.valueOf(entry.getValue())).append(";");
        }
        String styleKey = key.toString();
        if (styleClassMap.containsKey(styleKey)) {
            return styleClassMap.get(styleKey);
        }

        String className = "dw-s" + styleClassCounter++;
        styleClassMap.put(styleKey, className);
        StringBuilder rule = new StringBuilder();
        rule.append("    .").append(className).append(" { ");
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            rule.append(camelToKebab(entry.getKey())).append(": ").append(String.valueOf(entry.getValue())).append("; ");
        }
        rule.append("}\n");
        styleRules.add(rule.toString());
        return className;
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
