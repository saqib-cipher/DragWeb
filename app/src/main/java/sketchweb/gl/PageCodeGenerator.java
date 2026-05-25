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
    private IconLibraryManager iconLibraryManager;
    private AnimationLibraryManager animationLibraryManager;

    /**
     * Controls whether per-widget styles are emitted as inline
     * style="..." attributes on each element. Defaults to true to keep
     * existing behaviour; toggled via the theme settings dialog
     * ({@link ThemeManager#isUseInlineStyles()}).
     */
    private boolean useInlineStyles = true;

    /**
     * Accumulates per-element CSS rules emitted while
     * {@link #useInlineStyles} is false. Flushed into the final &lt;style&gt;
     * block by {@link #appendHtmlFooter}.
     */
    private final StringBuilder externalElementCss = new StringBuilder();
    private int externalElementCounter = 0;

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

    private View activeScreen;

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
        activeScreen = screen;
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
                                           CustomBlockManager customBlockManager) {
        resetStyleCache();
        this.useInlineStyles = themeManager == null || themeManager.isUseInlineStyles();
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
        appendHtmlFooter(htmlBuilder, themeManager, logicBlockManager, customBlockManager);
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

    private void appendHtmlFooter(StringBuilder htmlBuilder,
                                  LogicBlockManager logicBlockManager,
                                  CustomBlockManager customBlockManager) {
        appendHtmlFooter(htmlBuilder, null, logicBlockManager, customBlockManager);
    }

    private void appendHtmlFooter(StringBuilder htmlBuilder,
                                  ThemeManager themeManager,
                                  LogicBlockManager logicBlockManager,
                                  CustomBlockManager customBlockManager) {
        // Consolidated <style> block at the end of body as requested.
        htmlBuilder.append("\n  <style>\n");

        // Theme CSS variables + resets + only-the-used utility rules. The
        // theme manager scans the active screen so dead utilities like .flex
        // or a:hover don't leak in for projects that don't use them.
        if (themeManager != null) {
            String global = themeManager.generateGlobalCss(activeScreen);
            htmlBuilder.append(indentBlock(global, "    "));
            if (!global.endsWith("\n")) htmlBuilder.append("\n");
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
                htmlBuilder.append("\n    /* CSS interaction rules */\n").append(pseudoCss).append("\n");
            }
            
            String asdCss = logicBlockManager.generateAsdSource("css");
            if (asdCss != null && !asdCss.trim().isEmpty()) {
                htmlBuilder.append("\n    /* ASD CSS source */\n").append(asdCss).append("\n");
            }
        }

        if (customBlockManager != null) {
            String customCss = customBlockManager.renderAllCss();
            if (customCss != null && !customCss.trim().isEmpty()) {
                htmlBuilder.append("\n    /* Custom block styles */\n").append(customCss).append("\n");
            }
        }

        // Per-element CSS collected while inline-styles were disabled.
        if (externalElementCss.length() > 0) {
            htmlBuilder.append("\n    /* Per-element styles (inline disabled) */\n");
            htmlBuilder.append(externalElementCss);
        }

        htmlBuilder.append("  </style>\n");

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

        // Per-element style emission. When inline styles are enabled (the
        // default) we keep the historical style="..." attribute. When the
        // toggle is off we hoist the rule into the page-level <style> block
        // under a generated class so the markup stays clean.
        Map<String, Object> style = function.containsKey("style") ? (Map<String, Object>) function.get("style") : null;
        String userClass = function.containsKey("class") && function.get("class") != null
            ? function.get("class").toString().trim() : "";
        if (useInlineStyles) {
            if (style != null && !style.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Object> entry : style.entrySet()) {
                    sb.append(camelToKebab(entry.getKey())).append(": ").append(String.valueOf(entry.getValue())).append("; ");
                }
                html.append(" style=\"").append(escapeAttr(sb.toString().trim())).append("\"");
            }
            if (!userClass.isEmpty()) {
                html.append(" class=\"").append(escapeAttr(userClass)).append("\"");
            }
        } else {
            StringBuilder classAttr = new StringBuilder();
            if (style != null && !style.isEmpty()) classAttr.append(externalizeStyle(style));
            if (!userClass.isEmpty()) {
                if (classAttr.length() > 0) classAttr.append(' ');
                classAttr.append(userClass);
            }
            if (classAttr.length() > 0) {
                html.append(" class=\"").append(escapeAttr(classAttr.toString())).append("\"");
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

        // Style attribute emission honours the inline-styles toggle (see
        // ThemeManager.isUseInlineStyles). When disabled, the per-element
        // rule is pushed into the page <style> block under a generated class.
        Map<String, Object> style = (Map<String, Object>) function.get("style");
        String userClass = function.containsKey("class") && function.get("class") != null
            ? function.get("class").toString().trim() : "";
        if (useInlineStyles) {
            if (style != null && !style.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Object> entry : style.entrySet()) {
                    sb.append(camelToKebab(entry.getKey())).append(": ").append(String.valueOf(entry.getValue())).append("; ");
                }
                html.append(" style=\"").append(escapeAttr(sb.toString().trim())).append("\"");
            }
            if (!userClass.isEmpty()) {
                html.append(" class=\"").append(escapeAttr(userClass)).append("\"");
            }
        } else {
            StringBuilder classAttr = new StringBuilder();
            if (style != null && !style.isEmpty()) classAttr.append(externalizeStyle(style));
            if (!userClass.isEmpty()) {
                if (classAttr.length() > 0) classAttr.append(' ');
                classAttr.append(userClass);
            }
            if (classAttr.length() > 0) {
                html.append(" class=\"").append(escapeAttr(classAttr.toString())).append("\"");
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

    /** Prefix every non-empty line of {@code block} with {@code indent}. */
    private String indentBlock(String block, String indent) {
        if (block == null || block.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : block.split("\n", -1)) {
            if (line.isEmpty()) {
                sb.append('\n');
            } else {
                sb.append(indent).append(line).append('\n');
            }
        }
        // Trim trailing newline we always added.
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private void resetStyleCache() {
        externalElementCss.setLength(0);
        externalElementCounter = 0;
    }

    /**
     * Allocates a generated CSS class for a per-widget style map, appends the
     * rule to {@link #externalElementCss}, and returns the class name. Used
     * when {@link #useInlineStyles} is false.
     */
    private String externalizeStyle(Map<String, Object> style) {
        String generated = "dw-el-" + (++externalElementCounter);
        externalElementCss.append("    .").append(generated).append(" {\n");
        for (Map.Entry<String, Object> entry : style.entrySet()) {
            externalElementCss.append("      ")
                .append(camelToKebab(entry.getKey())).append(": ")
                .append(String.valueOf(entry.getValue())).append(";\n");
        }
        externalElementCss.append("    }\n");
        return generated;
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
