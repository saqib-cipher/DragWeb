package sketchweb.gl;

import android.view.View;
import android.view.ViewGroup;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PageCodeGenerator {

    private String projectName = "";
    private String projectLogoPath = "";
    private final Map<String, String> styleClassMap = new HashMap<>();
    private final List<String> styleRules = new ArrayList<>();
    private int styleClassCounter = 1;

    // Collected CSS classes from widget styles — key = class name, value = css rules
    private Map<String, String> extractedClasses = new LinkedHashMap<>();
    private int classCounter = 0;

    public PageCodeGenerator() {}

    public void setProjectInfo(String name, String logoPath) {
        this.projectName = name != null ? name : "";
        this.projectLogoPath = logoPath != null ? logoPath : "";
    }

    public String generateAllCode(View screen) {
        return generateFullCode(screen, null, null);
    }

    public String generateFullCode(View screen, ThemeManager themeManager, LogicBlockManager logicBlockManager) {
        extractedClasses.clear();
        classCounter = 0;

        StringBuilder bodyHtml = new StringBuilder();
        appendDefaultPageHeader(bodyHtml);

        if (screen instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) screen;
            for (int i = 0; i < vg.getChildCount(); i++) {
                bodyHtml.append(generateHtmlForView(vg.getChildAt(i), 1));
            }
        }

        StringBuilder htmlBuilder = new StringBuilder();
        appendHtmlHeader(htmlBuilder, themeManager);
        htmlBuilder.append(bodyHtml);
        appendHtmlFooter(htmlBuilder, logicBlockManager);
        return htmlBuilder.toString();
    }

    public String generateFullCodeFromTree(List<Map<String, Object>> widgetTree, ThemeManager themeManager, LogicBlockManager logicBlockManager) {
        extractedClasses.clear();
        classCounter = 0;

        StringBuilder bodyHtml = new StringBuilder();
        appendDefaultPageHeader(bodyHtml);

        if (widgetTree != null) {
            for (Map<String, Object> nodeMap : widgetTree) {
                bodyHtml.append(generateHtmlForNode(nodeMap, 1));
            }
        }

        StringBuilder htmlBuilder = new StringBuilder();
        appendHtmlHeader(htmlBuilder, themeManager);
        htmlBuilder.append(bodyHtml);
        appendHtmlFooter(htmlBuilder, logicBlockManager);
        return htmlBuilder.toString();
    }

    private void appendDefaultPageHeader(StringBuilder html) {
        if (projectName.isEmpty()) return;

        html.append("  <header class=\"site-header\">\n");
        if (!projectLogoPath.isEmpty()) {
            html.append("    <img class=\"site-logo\" src=\"").append(escapeAttr(projectLogoPath))
                .append("\" alt=\"").append(escapeAttr(projectName)).append(" logo\" />\n");
        }
        html.append("    <span class=\"site-title\">").append(escapeHtml(projectName)).append("</span>\n");
        html.append("  </header>\n");
    }

    private void appendHtmlHeader(StringBuilder html, ThemeManager themeManager) {
        String pageTitle = projectName.isEmpty() ? "DragWeb Page" : escapeHtml(projectName);
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>").append(pageTitle).append("</title>\n");
        html.append("  <style>\n");

        if (themeManager != null) {
            html.append(themeManager.generateGlobalCss());
        } else {
            html.append("    * { margin: 0; padding: 0; box-sizing: border-box; }\n");
            html.append("    body { font-family: sans-serif; line-height: 1.6; }\n");
        }

        html.append("    button { cursor: pointer; font-family: inherit; }\n");
        html.append("    input, textarea { font-family: inherit; }\n");
        html.append("    img { max-width: 100%; height: auto; }\n");

        // Default header styles
        if (!projectName.isEmpty()) {
            html.append("    .site-header { display: flex; align-items: center; padding: 12px 24px; background-color: #ffffff; border-bottom: 1px solid #e0e0e0; }\n");
            html.append("    .site-logo { height: 40px; width: auto; margin-right: 12px; }\n");
            html.append("    .site-title { font-size: 20px; font-weight: 700; color: #333333; }\n");
        }

        // Animation keyframes
        html.append("    @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }\n");
        html.append("    @keyframes slideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }\n");
        html.append("    @keyframes slideDown { from { transform: translateY(-20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }\n");
        html.append("    @keyframes pulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.05); } }\n");

        // Extracted widget CSS classes
        for (Map.Entry<String, String> entry : extractedClasses.entrySet()) {
            html.append("    .").append(entry.getKey()).append(" { ").append(entry.getValue()).append("}\n");
        }

        html.append("  </style>\n");
        html.append("</head>\n<body>\n");
    }

    private void appendHtmlFooter(StringBuilder html, LogicBlockManager logicBlockManager) {
        if (logicBlockManager != null) {
            String logicJs = logicBlockManager.generateJavaScript();
            if (logicJs != null && !logicJs.isEmpty()) {
                html.append("\n  <script>\n");
                html.append(logicJs);
                html.append("  </script>\n");
            }
        }
        html.append("</body>\n</html>");
    }

    @SuppressWarnings("unchecked")
    private String generateHtmlForNode(Map<String, Object> nodeMap, int indentLevel) {
        if (nodeMap == null) return "";

        String indent = repeat("  ", indentLevel);
        String tag = nodeMap.containsKey("tag") ? nodeMap.get("tag").toString() : "div";

        Map<String, Object> function = nodeMap.containsKey("function") ? (Map<String, Object>) nodeMap.get("function") : null;
        if (function == null) return "";

        StringBuilder html = new StringBuilder();
        html.append(indent).append("<").append(tag);

        // Collect user-defined class and id
        String userId = getStringProp(function, "id");
        String userClass = getStringProp(function, "class");

        // Extract styles into a CSS class
        Map<String, Object> style = function.containsKey("style") ? (Map<String, Object>) function.get("style") : null;
        String generatedClass = extractStyleClass(tag, style);

        // Build class attribute combining user class + generated class
        String combinedClass = buildClassAttr(userClass, generatedClass);
        if (!userId.isEmpty()) {
            html.append(" id=\"").append(escapeAttr(userId)).append("\"");
        }
        if (!combinedClass.isEmpty()) {
            html.append(" class=\"").append(escapeAttr(combinedClass)).append("\"");
        }

        appendTagAttributes(html, tag, function);

        // Self-closing tags
        if (isSelfClosing(tag)) {
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

    @SuppressWarnings("unchecked")
    private String generateHtmlForView(View view, int indentLevel) {
        if (view == null) return "";

        Object tagObj = view.getTag();
        if (!(tagObj instanceof Map)) return "";

        Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
        String tag = widgetMap.containsKey("tag") ? widgetMap.get("tag").toString() : "div";

        Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
        if (function == null) return "";

        String indent = repeat("  ", indentLevel);
        StringBuilder html = new StringBuilder();
        html.append(indent).append("<").append(tag);

        String userId = getStringProp(function, "id");
        String userClass = getStringProp(function, "class");

        Map<String, Object> style = (Map<String, Object>) function.get("style");
        String generatedClass = extractStyleClass(tag, style);

        String combinedClass = buildClassAttr(userClass, generatedClass);
        if (!userId.isEmpty()) {
            html.append(" id=\"").append(escapeAttr(userId)).append("\"");
        }
        if (!combinedClass.isEmpty()) {
            html.append(" class=\"").append(escapeAttr(combinedClass)).append("\"");
        }

        appendTagAttributes(html, tag, function);

        if (isSelfClosing(tag)) {
            html.append(" />\n");
            return html.toString();
        }

        html.append(">");

        if (function.containsKey("text")) {
            html.append(escapeHtml(function.get("text").toString()));
        }

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
        if (!styleClass.isEmpty()) {
            if (classes.length() > 0) classes.append(" ");
            classes.append(styleClass);
        }
        if (classes.length() > 0) {
            html.append(" class=\"").append(escapeAttr(classes.toString())).append("\"");
        }

        html.append("</").append(tag).append(">\n");
        return html.toString();
    }

    /**
     * Extract inline styles into a reusable CSS class.
     * Returns the generated class name, or empty string if no styles.
     */
    private String extractStyleClass(String tag, Map<String, Object> style) {
        if (style == null || style.isEmpty()) return "";

        StringBuilder cssRules = new StringBuilder();
        for (Map.Entry<String, Object> entry : style.entrySet()) {
            String cssKey = camelToKebab(entry.getKey());
            cssRules.append(cssKey).append(": ").append(entry.getValue()).append("; ");
        }

        String rules = cssRules.toString();

        // Check if we already have an identical class
        for (Map.Entry<String, String> existing : extractedClasses.entrySet()) {
            if (existing.getValue().equals(rules)) {
                return existing.getKey();
            }
        }

        classCounter++;
        String className = tag + "-" + classCounter;
        extractedClasses.put(className, rules);
        return className;
    }

    private void appendTagAttributes(StringBuilder html, String tag, Map<String, Object> function) {
        if ("img".equals(tag) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeAttr(function.get("src").toString())).append("\"");
            String alt = function.containsKey("alt") ? function.get("alt").toString() : "Image";
            html.append(" alt=\"").append(escapeAttr(alt)).append("\"");
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
    }

    private boolean isSelfClosing(String tag) {
        return "input".equals(tag) || "img".equals(tag) || "hr".equals(tag) || "br".equals(tag);
    }

    private String getStringProp(Map<String, Object> map, String key) {
        if (map.containsKey(key) && map.get(key) != null) {
            String val = map.get(key).toString().trim();
            return val.isEmpty() ? "" : val;
        }
        return "";
    }

    private String buildClassAttr(String userClass, String generatedClass) {
        if (userClass.isEmpty() && generatedClass.isEmpty()) return "";
        if (userClass.isEmpty()) return generatedClass;
        if (generatedClass.isEmpty()) return userClass;
        return userClass + " " + generatedClass;
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
