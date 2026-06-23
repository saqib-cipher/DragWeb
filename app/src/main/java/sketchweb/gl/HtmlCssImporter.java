package sketchweb.gl;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses HTML and CSS files and converts them into the DragWeb widget tree format.
 * Widget tree nodes follow the structure:
 * { "tag": "div", "function": { "text": "...", "id": "...", "class": "...", "style": { ... } }, "children": [...] }
 */
public class HtmlCssImporter {

    private static final String TAG = "HtmlCssImporter";

    // Supported tags that map to DragWeb widgets
    private static final String[] SUPPORTED_TAGS = {
        "div", "section", "nav", "header", "footer", "main", "article", "aside", "form",
        "p", "h1", "h2", "h3", "h4", "h5", "h6", "a", "span", "label", "pre", "blockquote",
        "button", "img", "input", "textarea",
        "ul", "ol", "li", "hr", "br",
        "video", "audio", "canvas", "svg", "iframe", "table",
        "tr", "td", "th", "thead", "tbody", "tfoot"
    };

    // CSS rules parsed from external CSS: selector -> { property -> value }
    private final Map<String, Map<String, String>> cssRules = new LinkedHashMap<>();

    // Logic blocks parsed during CSS import
    private final List<Map<String, Object>> importedLogicBlocks = new ArrayList<>();

    /**
     * Import result containing the widget tree and logic blocks for the project.
     */
    public static class ImportResult {
        public boolean success;
        public String message;
        public List<Map<String, Object>> widgetTree;
        public List<Map<String, Object>> logicBlocks;

        public ImportResult(boolean success, String message, List<Map<String, Object>> widgetTree) {
            this.success = success;
            this.message = message;
            this.widgetTree = widgetTree;
            this.logicBlocks = new ArrayList<>();
        }

        public ImportResult(boolean success, String message, List<Map<String, Object>> widgetTree, List<Map<String, Object>> logicBlocks) {
            this.success = success;
            this.message = message;
            this.widgetTree = widgetTree;
            this.logicBlocks = logicBlocks;
        }
    }

    /**
     * Parse HTML content (and optionally CSS) into a DragWeb widget tree.
     */
    public ImportResult importHtmlCss(String htmlContent, String cssContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return new ImportResult(false, "HTML content is empty", null);
        }

        try {
            // Clear prior states
            cssRules.clear();
            importedLogicBlocks.clear();

            // Parse external CSS first so styles can be applied to elements
            if (cssContent != null && !cssContent.trim().isEmpty()) {
                parseCss(cssContent);
            }

            // Also extract <style> blocks from the HTML
            extractInlineStyleBlocks(htmlContent);

            // Extract the <body> content, or use the whole HTML if no body tag
            String bodyContent = extractBodyContent(htmlContent);

            // Parse the HTML into widget tree nodes
            List<Map<String, Object>> widgetTree = parseHtmlNodes(bodyContent);

            if (widgetTree.isEmpty()) {
                return new ImportResult(false, "No supported HTML elements found", null);
            }

            // Link Blockly chains for nextBlockId and subStackId references
            linkBlockChains(importedLogicBlocks);

            return new ImportResult(true, "Successfully imported " + countNodes(widgetTree) + " elements", widgetTree, new ArrayList<>(importedLogicBlocks));
        } catch (Exception e) {
            Log.e(TAG, "Import failed: " + e.getMessage(), e);
            return new ImportResult(false, "Parse error: " + e.getMessage(), null);
        }
    }

    /**
     * Extract content between <body> and </body> tags.
     */
    private String extractBodyContent(String html) {
        // Remove comments
        html = html.replaceAll("<!--[\\s\\S]*?-->", "");

        Pattern bodyPattern = Pattern.compile("<body[^>]*>(.*)</body>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher bodyMatcher = bodyPattern.matcher(html);
        if (bodyMatcher.find()) {
            return bodyMatcher.group(1).trim();
        }
        // No body tag - remove head/html tags and use remaining content
        html = html.replaceAll("(?i)<html[^>]*>", "");
        html = html.replaceAll("(?i)</html>", "");
        html = html.replaceAll("(?i)<head[\\s\\S]*?</head>", "");
        html = html.replaceAll("(?i)<!DOCTYPE[^>]*>", "");
        return html.trim();
    }

    /**
     * Extract and parse <style> blocks from HTML.
     */
    private void extractInlineStyleBlocks(String html) {
        Pattern stylePattern = Pattern.compile("<style[^>]*>([\\s\\S]*?)</style>", Pattern.CASE_INSENSITIVE);
        Matcher styleMatcher = stylePattern.matcher(html);
        while (styleMatcher.find()) {
            parseCss(styleMatcher.group(1));
        }
    }

    private static class SelectorInfo {
        String action;
        String spec;
        List<String> paramValues = new ArrayList<>();
    }

    private SelectorInfo parseSelector(String selector) {
        SelectorInfo info = new SelectorInfo();
        selector = selector.trim();

        if (selector.endsWith(":hover")) {
            info.action = "cssHover";
            info.spec = "%s:hover { %m.space }";
            info.paramValues.add(selector.substring(0, selector.length() - 6).trim());
        } else if (selector.endsWith(":focus")) {
            info.action = "cssFocus";
            info.spec = "%s:focus { %m.space }";
            info.paramValues.add(selector.substring(0, selector.length() - 6).trim());
        } else if (selector.endsWith(":active")) {
            info.action = "cssActive";
            info.spec = "%s:active { %m.space }";
            info.paramValues.add(selector.substring(0, selector.length() - 7).trim());
        } else if (selector.endsWith(":visited")) {
            info.action = "cssVisited";
            info.spec = "%s:visited { %m.space }";
            info.paramValues.add(selector.substring(0, selector.length() - 8).trim());
        } else if (selector.endsWith("::before")) {
            info.action = "cssBefore";
            info.spec = "%s::before { %m.space }";
            info.paramValues.add(selector.substring(0, selector.length() - 8).trim());
        } else if (selector.endsWith(":before")) {
            info.action = "cssBefore";
            info.spec = "%s::before { %m.space }";
            info.paramValues.add(selector.substring(0, selector.length() - 7).trim());
        } else if (selector.endsWith("::after")) {
            info.action = "cssAfter";
            info.spec = "%s::after { %m.space }";
            info.paramValues.add(selector.substring(0, selector.length() - 7).trim());
        } else if (selector.endsWith(":after")) {
            info.action = "cssAfter";
            info.spec = "%s::after { %m.space }";
            info.paramValues.add(selector.substring(0, selector.length() - 6).trim());
        } else if (selector.endsWith(":first-child")) {
            info.action = "cssFirstChild";
            info.spec = "%s:first-child { %m.space }";
            info.paramValues.add(selector.substring(0, selector.length() - 12).trim());
        } else if (selector.endsWith(":last-child")) {
            info.action = "cssLastChild";
            info.spec = "%s:last-child { %m.space }";
            info.paramValues.add(selector.substring(0, selector.length() - 11).trim());
        } else if (selector.contains(":nth-child(")) {
            int start = selector.indexOf(":nth-child(");
            int end = selector.indexOf(")", start);
            if (end > start) {
                String base = selector.substring(0, start).trim();
                String arg = selector.substring(start + 11, end).trim();
                info.action = "cssNthChild";
                info.spec = "%s:nth-child(%n) { %m.space }";
                info.paramValues.add(base);
                info.paramValues.add(arg);
            } else {
                info.action = "cssSelector";
                info.spec = "%s { %m.space }";
                info.paramValues.add(selector);
            }
        } else {
            info.action = "cssSelector";
            info.spec = "%s { %m.space }";
            info.paramValues.add(selector);
        }
        return info;
    }

    private static class PropertyBlockInfo {
        String action;
        String category = "css";
        String spec;
        List<String> paramValues = new ArrayList<>();
    }

    private PropertyBlockInfo parsePropertyToBlock(String property, String value) {
        PropertyBlockInfo info = new PropertyBlockInfo();
        property = property.trim().toLowerCase();
        value = value.trim();

        String[] numUnit = splitNumberAndUnit(value);

        switch (property) {
            case "display":
                info.action = "setDisplay";
                info.spec = "display: %m.display;";
                info.paramValues.add(value);
                break;
            case "position":
                info.action = "setPosition";
                info.spec = "position: %m.position;";
                info.paramValues.add(value);
                break;
            case "overflow":
                info.action = "setOverflow";
                info.spec = "overflow: %m.overflow;";
                info.paramValues.add(value);
                break;
            case "color":
                info.action = "setColor";
                info.spec = "color: %m.color;";
                info.paramValues.add(value);
                break;
            case "background-color":
            case "backgroundcolor":
            case "background":
                if (value.contains("url(")) {
                    info.action = "setBackgroundImage";
                    info.spec = "background-image: url(%s);";
                    info.paramValues.add(extractUrl(value));
                } else {
                    info.action = "setBackground";
                    info.spec = "background: %m.color;";
                    info.paramValues.add(value);
                }
                break;
            case "background-image":
            case "backgroundimage":
                info.action = "setBackgroundImage";
                info.spec = "background-image: url(%s);";
                info.paramValues.add(extractUrl(value));
                break;
            case "width":
                info.action = "setWidth";
                info.spec = "width: %n%m.unit;";
                info.paramValues.add(numUnit[0]);
                info.paramValues.add(numUnit[1]);
                break;
            case "height":
                info.action = "setHeight";
                info.spec = "height: %n%m.unit;";
                info.paramValues.add(numUnit[0]);
                info.paramValues.add(numUnit[1]);
                break;
            case "max-width":
            case "maxwidth":
                info.action = "setMaxWidth";
                info.spec = "max-width: %n%m.unit;";
                info.paramValues.add(numUnit[0]);
                info.paramValues.add(numUnit[1]);
                break;
            case "max-height":
            case "maxheight":
                info.action = "setMaxHeight";
                info.spec = "max-height: %n%m.unit;";
                info.paramValues.add(numUnit[0]);
                info.paramValues.add(numUnit[1]);
                break;
            case "min-width":
            case "minwidth":
                info.action = "setMinWidth";
                info.spec = "min-width: %n%m.unit;";
                info.paramValues.add(numUnit[0]);
                info.paramValues.add(numUnit[1]);
                break;
            case "min-height":
            case "minheight":
                info.action = "setMinHeight";
                info.spec = "min-height: %n%m.unit;";
                info.paramValues.add(numUnit[0]);
                info.paramValues.add(numUnit[1]);
                break;
            case "margin":
                info.action = "setMargin";
                info.spec = "margin: %n%m.unit;";
                info.paramValues.add(numUnit[0]);
                info.paramValues.add(numUnit[1]);
                break;
            case "padding":
                info.action = "setPadding";
                info.spec = "padding: %n%m.unit;";
                info.paramValues.add(numUnit[0]);
                info.paramValues.add(numUnit[1]);
                break;
            case "border-radius":
            case "borderradius":
                info.action = "setRadius";
                info.spec = "border-radius: %n%m.unit;";
                info.paramValues.add(numUnit[0]);
                info.paramValues.add(numUnit[1]);
                break;
            case "font-size":
            case "fontsize":
                info.action = "setFontSize";
                info.spec = "font-size: %n%m.unit;";
                info.paramValues.add(numUnit[0]);
                info.paramValues.add(numUnit[1]);
                break;
            case "font-family":
            case "fontfamily":
                info.action = "setFontFamily";
                info.spec = "font-family: %s;";
                info.paramValues.add(value);
                break;
            case "font-weight":
            case "fontweight":
                info.action = "setFontWeight";
                info.spec = "font-weight: %m.fontWeight;";
                info.paramValues.add(value);
                break;
            case "font-style":
            case "fontstyle":
                info.action = "setFontStyle";
                info.spec = "font-style: %m.fontStyle;";
                info.paramValues.add(value);
                break;
            case "text-align":
            case "textalign":
                info.action = "setTextAlign";
                info.spec = "text-align: %m.textAlign;";
                info.paramValues.add(value);
                break;
            case "text-decoration":
            case "textdecoration":
                info.action = "setTextDecoration";
                info.spec = "text-decoration: %m.textDecoration;";
                info.paramValues.add(value);
                break;
            case "line-height":
            case "lineheight":
                info.action = "setLineHeight";
                info.spec = "line-height: %n;";
                info.paramValues.add(value);
                break;
            case "letter-spacing":
            case "letterspacing":
                info.action = "setLetterSpacing";
                info.spec = "letter-spacing: %n%m.unit;";
                info.paramValues.add(numUnit[0]);
                info.paramValues.add(numUnit[1]);
                break;
            case "opacity":
                info.action = "setOpacity";
                info.spec = "opacity: %n;";
                info.paramValues.add(value);
                break;
            case "z-index":
            case "zindex":
                info.action = "setZIndex";
                info.spec = "z-index: %n;";
                info.paramValues.add(value);
                break;
            case "cursor":
                info.action = "setCursor";
                info.spec = "cursor: %m.cursor;";
                info.paramValues.add(value);
                break;
            case "flex-direction":
            case "flexdirection":
                info.action = "setFlexDirection";
                info.spec = "flex-direction: %m.flexDirection;";
                info.paramValues.add(value);
                break;
            case "justify-content":
            case "justifycontent":
                info.action = "setJustifyContent";
                info.spec = "justify-content: %m.justifyContent;";
                info.paramValues.add(value);
                break;
            case "align-items":
            case "alignitems":
                info.action = "setAlignItems";
                info.spec = "align-items: %m.alignItems;";
                info.paramValues.add(value);
                break;
            case "gap":
                info.action = "setGap";
                info.spec = "gap: %n%m.unit;";
                info.paramValues.add(numUnit[0]);
                info.paramValues.add(numUnit[1]);
                break;
            case "grid-template-columns":
            case "gridtemplatecolumns":
                info.action = "setGridTemplateColumns";
                info.spec = "grid-template-columns: %s;";
                info.paramValues.add(value);
                break;
            case "transform":
                info.action = "setTransform";
                info.spec = "transform: %s;";
                info.paramValues.add(value);
                break;
            case "filter":
                info.action = "setFilter";
                info.spec = "filter: %s;";
                info.paramValues.add(value);
                break;
            case "border":
            case "borderwidth":
            case "border-width":
            case "bordercolor":
            case "border-color":
                if ("borderwidth".equals(property) || "border-width".equals(property)) {
                    info.action = "setBorder";
                    info.spec = "border: %n%m.unit solid %m.color;";
                    info.paramValues.add(numUnit[0]);
                    info.paramValues.add(numUnit[1]);
                    info.paramValues.add("#000000");
                } else if ("bordercolor".equals(property) || "border-color".equals(property)) {
                    info.action = "setBorder";
                    info.spec = "border: %n%m.unit solid %m.color;";
                    info.paramValues.add("1");
                    info.paramValues.add("px");
                    info.paramValues.add(value);
                } else {
                    String[] borderParams = parseBorderParams(value);
                    info.action = "setBorder";
                    info.spec = "border: %n%m.unit solid %m.color;";
                    info.paramValues.add(borderParams[0]);
                    info.paramValues.add(borderParams[1]);
                    info.paramValues.add(borderParams[2]);
                }
                break;
            case "box-shadow":
            case "boxshadow":
                String[] shadowParams = parseBoxShadowParams(value);
                info.action = "setBoxShadow";
                info.spec = "box-shadow: %npx %npx %npx %m.color;";
                info.paramValues.add(shadowParams[0]);
                info.paramValues.add(shadowParams[1]);
                info.paramValues.add(shadowParams[2]);
                info.paramValues.add(shadowParams[3]);
                break;
            default:
                info.action = "asdCss";
                info.category = "asd";
                info.spec = "%s";
                info.paramValues.add(kebabToCamel(property) + ": " + value + ";");
                break;
        }

        return info;
    }

    private String[] splitNumberAndUnit(String value) {
        String num = "";
        String unit = "";
        Pattern p = Pattern.compile("^([+-]?\\d*(?:\\.\\d+)?)(.*)$");
        Matcher m = p.matcher(value);
        if (m.matches()) {
            num = m.group(1);
            unit = m.group(2).trim();
        }
        if (num.isEmpty()) {
            unit = value;
        }
        if (unit.isEmpty()) {
            unit = "px";
        }
        return new String[]{num, unit};
    }

    private String extractUrl(String val) {
        Pattern p = Pattern.compile("url\\(['\"]?([^'\"]*)['\"]?\\)");
        Matcher m = p.matcher(val);
        if (m.find()) {
            return m.group(1);
        }
        return val;
    }

    private String[] parseBorderParams(String val) {
        String size = "1";
        String unit = "px";
        String color = "#000000";
        String[] parts = val.split("\\s+");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty() || part.equals("solid") || part.equals("dashed") || part.equals("dotted")) continue;
            String[] nu = splitNumberAndUnit(part);
            if (!nu[0].isEmpty()) {
                size = nu[0];
                unit = nu[1];
            } else {
                color = part;
            }
        }
        return new String[]{size, unit, color};
    }

    private String[] parseBoxShadowParams(String val) {
        String x = "0";
        String y = "0";
        String blur = "0";
        String color = "#00000033";
        
        String[] parts = val.split("\\s+");
        int numIndex = 0;
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            String[] nu = splitNumberAndUnit(part);
            if (!nu[0].isEmpty()) {
                if (numIndex == 0) x = nu[0];
                else if (numIndex == 1) y = nu[0];
                else if (numIndex == 2) blur = nu[0];
                numIndex++;
            } else {
                color = part;
            }
        }
        return new String[]{x, y, blur, color};
    }

    private void linkBlockChains(List<Map<String, Object>> blocks) {
        Map<String, List<Map<String, Object>>> childrenByParent = new HashMap<>();
        for (Map<String, Object> block : blocks) {
            String parentId = (String) block.get("parentBlockId");
            if (parentId == null) parentId = "";
            if (!childrenByParent.containsKey(parentId)) {
                childrenByParent.put(parentId, new ArrayList<>());
            }
            childrenByParent.get(parentId).add(block);
        }

        for (Map.Entry<String, List<Map<String, Object>>> entry : childrenByParent.entrySet()) {
            String parentId = entry.getKey();
            List<Map<String, Object>> siblings = entry.getValue();
            if (siblings.isEmpty()) continue;

            for (int i = 0; i < siblings.size() - 1; i++) {
                siblings.get(i).put("nextBlockId", siblings.get(i + 1).get("id"));
            }
            siblings.get(siblings.size() - 1).put("nextBlockId", null);

            if (!parentId.isEmpty()) {
                for (Map<String, Object> parentBlock : blocks) {
                    if (parentId.equals(parentBlock.get("id"))) {
                        parentBlock.put("subStackId", siblings.get(0).get("id"));
                        break;
                    }
                }
            }
        }
    }

    private String joinPipe(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append('|');
            String v = values.get(i);
            sb.append(v != null ? v : "");
        }
        return sb.toString();
    }

    /**
     * Parse CSS content and store rules.
     */
    private void parseCss(String css) {
        if (css == null || css.trim().isEmpty()) return;

        Pattern pattern = Pattern.compile("(?:/\\*([\\s\\S]*?)\\*/)|(?:(?<!https?:|ftp:|url\\()//([^\n]*))|([^{}]+)\\{([^}]*)\\}");
        Matcher matcher = pattern.matcher(css);

        String currentGroupId = null;
        long timestamp = System.currentTimeMillis();
        int counter = importedLogicBlocks.size();

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // Block comment
                String commentText = matcher.group(1).trim();
                if (commentText.isEmpty()) continue;

                String blockId = "blk_group_" + timestamp + "_" + (counter++);
                Map<String, Object> groupBlock = new HashMap<>();
                groupBlock.put("id", blockId);
                groupBlock.put("action", "groupBlock");
                groupBlock.put("category", "meta");
                groupBlock.put("shape", "cblock");
                groupBlock.put("spec", "/* <%s> */ %m.space /* </%s> */");
                
                String groupName = commentText.replaceAll("^<|>$", "").trim();
                List<String> paramValues = new ArrayList<>();
                paramValues.add(groupName);
                groupBlock.put("paramValues", paramValues);
                groupBlock.put("params", groupName);
                groupBlock.put("event", "immediate");
                groupBlock.put("parentBlockId", "");

                importedLogicBlocks.add(groupBlock);
                currentGroupId = blockId;

            } else if (matcher.group(2) != null) {
                // Single-line comment
                String commentText = matcher.group(2).trim();
                if (commentText.isEmpty()) continue;

                String blockId = "blk_comment_" + timestamp + "_" + (counter++);
                Map<String, Object> commentBlock = new HashMap<>();
                commentBlock.put("id", blockId);
                commentBlock.put("action", "commentBlock");
                commentBlock.put("category", "meta");
                commentBlock.put("shape", "stack");
                commentBlock.put("spec", "/* %s */");
                List<String> paramValues = new ArrayList<>();
                paramValues.add(commentText);
                commentBlock.put("paramValues", paramValues);
                commentBlock.put("params", commentText);
                commentBlock.put("event", "immediate");
                commentBlock.put("parentBlockId", currentGroupId != null ? currentGroupId : "");

                importedLogicBlocks.add(commentBlock);

            } else if (matcher.group(3) != null && matcher.group(4) != null) {
                // Rule block
                String selectorGroup = matcher.group(3).trim();
                String propertiesBlock = matcher.group(4).trim();

                if (selectorGroup.startsWith("@")) {
                    continue;
                }

                Map<String, String> properties = parseCssProperties(propertiesBlock);
                if (properties.isEmpty()) continue;

                String[] selectors = selectorGroup.split(",");
                for (String selector : selectors) {
                    selector = selector.trim();
                    if (selector.isEmpty()) continue;

                    String[] parts = selector.split("\\s+");
                    String simpleSelector = parts[parts.length - 1];

                    String ruleSelector = simpleSelector.replaceAll(":[a-zA-Z-]+(?:\\([^)]*\\))?", "");
                    if (!ruleSelector.isEmpty()) {
                        if (cssRules.containsKey(ruleSelector)) {
                            cssRules.get(ruleSelector).putAll(properties);
                        } else {
                            cssRules.put(ruleSelector, new HashMap<>(properties));
                        }
                    }

                    SelectorInfo selInfo = parseSelector(simpleSelector);

                    String selectorBlockId = "blk_sel_" + timestamp + "_" + (counter++);
                    Map<String, Object> selBlock = new HashMap<>();
                    selBlock.put("id", selectorBlockId);
                    selBlock.put("action", selInfo.action);
                    selBlock.put("category", "css");
                    selBlock.put("shape", "cblock");
                    selBlock.put("spec", selInfo.spec);
                    selBlock.put("paramValues", selInfo.paramValues);
                    selBlock.put("params", joinPipe(selInfo.paramValues));
                    selBlock.put("event", "immediate");
                    selBlock.put("parentBlockId", currentGroupId != null ? currentGroupId : "");

                    importedLogicBlocks.add(selBlock);

                    for (Map.Entry<String, String> propEntry : properties.entrySet()) {
                        String prop = propEntry.getKey();
                        String val = propEntry.getValue();

                        PropertyBlockInfo propInfo = parsePropertyToBlock(prop, val);

                        String propBlockId = "blk_prop_" + timestamp + "_" + (counter++);
                        Map<String, Object> propBlock = new HashMap<>();
                        propBlock.put("id", propBlockId);
                        propBlock.put("action", propInfo.action);
                        propBlock.put("category", propInfo.category);
                        propBlock.put("shape", "stack");
                        propBlock.put("spec", propInfo.spec);
                        propBlock.put("paramValues", propInfo.paramValues);
                        propBlock.put("params", joinPipe(propInfo.paramValues));
                        propBlock.put("event", "immediate");
                        propBlock.put("parentBlockId", selectorBlockId);

                        importedLogicBlocks.add(propBlock);
                    }
                }
            }
        }
    }

    private void decomposeBorder(String borderVal, Map<String, String> targetMap) {
        if (borderVal == null || borderVal.trim().isEmpty()) return;
        String[] parts = borderVal.split("\\s+");
        for (String part : parts) {
            part = part.trim().toLowerCase();
            if (part.isEmpty()) continue;
            // Check if it's a border style
            if (part.equals("solid") || part.equals("dashed") || part.equals("dotted") 
                || part.equals("double") || part.equals("groove") || part.equals("ridge") 
                || part.equals("inset") || part.equals("outset") || part.equals("none") 
                || part.equals("hidden")) {
                continue;
            }
            // Check if it's a width / dimension
            if (part.endsWith("px") || part.endsWith("em") || part.endsWith("rem") 
                || part.endsWith("pt") || part.endsWith("%") || part.matches("\\d+(\\.\\d+)?")
                || part.equals("thin") || part.equals("medium") || part.equals("thick")) {
                targetMap.put("borderWidth", part);
            } else {
                // Treat as color
                targetMap.put("borderColor", part);
            }
        }
    }

    /**
     * Parse a CSS property block into a map of camelCase property -> value.
     */
    private Map<String, String> parseCssProperties(String block) {
        Map<String, String> props = new LinkedHashMap<>();
        if (block == null || block.trim().isEmpty()) return props;

        // Strip comments (block and single-line) from the block before parsing
        block = block.replaceAll("/\\*[\\s\\S]*?\\*/", "");
        block = block.replaceAll("(?m)^[ \\t]*//.*$", "");
        block = block.replaceAll("(?i)(?<!https?:|ftp:|url\\()//.*", "");

        String[] declarations = block.split(";");
        for (String decl : declarations) {
            decl = decl.trim();
            if (decl.isEmpty()) continue;
            int colonIdx = decl.indexOf(':');
            if (colonIdx <= 0) continue;
            String property = decl.substring(0, colonIdx).trim().toLowerCase();
            String value = decl.substring(colonIdx + 1).trim();
            // Remove !important
            value = value.replaceAll("\\s*!important\\s*$", "");
            if (property.isEmpty() || value.isEmpty()) continue;

            String camelProp = kebabToCamel(property);
            if ("border".equals(camelProp)) {
                decomposeBorder(value, props);
            } else if ("background".equals(camelProp)) {
                if (!value.contains("url")) {
                    props.put("backgroundColor", value);
                }
            } else {
                props.put(camelProp, value);
            }
        }
        return props;
    }

    /**
     * Convert kebab-case to camelCase: "font-size" -> "fontSize"
     */
    private String kebabToCamel(String kebab) {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = false;
        for (int i = 0; i < kebab.length(); i++) {
            char c = kebab.charAt(i);
            if (c == '-') {
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    sb.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Parse HTML string into a list of widget tree nodes.
     */
    private List<Map<String, Object>> parseHtmlNodes(String html) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (html == null || html.trim().isEmpty()) return nodes;

        int pos = 0;
        int len = html.length();

        while (pos < len) {
            // Skip whitespace
            int textStart = pos;

            // Find next tag
            int tagStart = html.indexOf('<', pos);

            if (tagStart < 0) {
                // Rest is text
                String text = html.substring(pos).trim();
                if (!text.isEmpty()) {
                    addTextNode(nodes, decodeHtmlEntities(text));
                }
                break;
            }

            // Text before this tag
            if (tagStart > pos) {
                String text = html.substring(pos, tagStart).trim();
                if (!text.isEmpty()) {
                    addTextNode(nodes, decodeHtmlEntities(text));
                }
            }

            // Skip <script> blocks entirely
            if (html.substring(tagStart).toLowerCase().startsWith("<script")) {
                int scriptEnd = html.toLowerCase().indexOf("</script>", tagStart);
                if (scriptEnd >= 0) {
                    pos = scriptEnd + "</script>".length();
                } else {
                    pos = len;
                }
                continue;
            }

            // Skip <style> blocks (already parsed)
            if (html.substring(tagStart).toLowerCase().startsWith("<style")) {
                int styleEnd = html.toLowerCase().indexOf("</style>", tagStart);
                if (styleEnd >= 0) {
                    pos = styleEnd + "</style>".length();
                } else {
                    pos = len;
                }
                continue;
            }

            // Skip closing tags
            if (tagStart + 1 < len && html.charAt(tagStart + 1) == '/') {
                int closingEnd = html.indexOf('>', tagStart);
                pos = (closingEnd >= 0) ? closingEnd + 1 : len;
                continue;
            }

            // Skip comments
            if (html.substring(tagStart).startsWith("<!--")) {
                int commentEnd = html.indexOf("-->", tagStart);
                pos = (commentEnd >= 0) ? commentEnd + 3 : len;
                continue;
            }

            // Skip doctype
            if (html.substring(tagStart).toLowerCase().startsWith("<!doctype")) {
                int dtEnd = html.indexOf('>', tagStart);
                pos = (dtEnd >= 0) ? dtEnd + 1 : len;
                continue;
            }

            // Parse opening tag
            ParsedTag parsedTag = parseOpeningTag(html, tagStart);
            if (parsedTag == null) {
                pos = tagStart + 1;
                continue;
            }

            pos = parsedTag.endPos;

            if (!isSupportedTag(parsedTag.tagName)) {
                // Unsupported tag - if it's a container, we still need to find its children
                // and closing tag. Treat as a transparent wrapper.
                if (!parsedTag.selfClosing && !isVoidElement(parsedTag.tagName)) {
                    // Find closing tag and parse inner content as if it were at this level
                    int closingPos = findClosingTag(html, pos, parsedTag.tagName);
                    if (closingPos >= 0) {
                        String innerHtml = html.substring(pos, closingPos);
                        List<Map<String, Object>> innerNodes = parseHtmlNodes(innerHtml);
                        nodes.addAll(innerNodes);
                        pos = closingPos + ("</" + parsedTag.tagName + ">").length();
                    }
                }
                continue;
            }

            // Create widget node
            Map<String, Object> widgetNode = createWidgetNode(parsedTag);

            // Handle self-closing and void elements
            if (parsedTag.selfClosing || isVoidElement(parsedTag.tagName)) {
                nodes.add(widgetNode);
                continue;
            }

            // Find children content up to closing tag
            int closingPos = findClosingTag(html, pos, parsedTag.tagName);
            if (closingPos >= 0) {
                String innerHtml = html.substring(pos, closingPos);

                if (isContainerTag(parsedTag.tagName)) {
                    // Parse children for container elements
                    List<Map<String, Object>> children = parseHtmlNodes(innerHtml);
                    if (!children.isEmpty()) {
                        widgetNode.put("children", children);
                    }
                } else {
                    // For leaf elements, just extract text content
                    String textContent = innerHtml.replaceAll("<[^>]*>", "").trim();
                    if (!textContent.isEmpty()) {
                        Map<String, Object> function = (Map<String, Object>) widgetNode.get("function");
                        function.put("text", decodeHtmlEntities(textContent));
                    }
                }

                pos = closingPos + ("</" + parsedTag.tagName + ">").length();
            } else {
                // No closing tag found, treat remaining content as this element's content
                String innerHtml = html.substring(pos);
                String textContent = innerHtml.replaceAll("<[^>]*>", "").trim();
                if (!textContent.isEmpty()) {
                    Map<String, Object> function = (Map<String, Object>) widgetNode.get("function");
                    function.put("text", decodeHtmlEntities(textContent));
                }
                pos = len;
            }

            nodes.add(widgetNode);
        }

        return nodes;
    }

    /**
     * Add a text node as a <p> widget if no parent element exists for it.
     */
    private void addTextNode(List<Map<String, Object>> nodes, String text) {
        if (text == null || text.trim().isEmpty()) return;
        Map<String, Object> node = new HashMap<>();
        node.put("tag", "p");
        Map<String, Object> function = new HashMap<>();
        function.put("text", text.trim());
        function.put("style", new HashMap<String, Object>());
        node.put("function", function);
        nodes.add(node);
    }

    /**
     * Parsed tag data holder.
     */
    private static class ParsedTag {
        String tagName;
        Map<String, String> attributes;
        boolean selfClosing;
        int endPos;
    }

    /**
     * Parse an opening HTML tag starting at the given position.
     */
    private ParsedTag parseOpeningTag(String html, int startPos) {
        int len = html.length();
        if (startPos >= len || html.charAt(startPos) != '<') return null;

        int pos = startPos + 1;

        // Read tag name
        StringBuilder tagNameBuilder = new StringBuilder();
        while (pos < len && !Character.isWhitespace(html.charAt(pos)) && html.charAt(pos) != '>' && html.charAt(pos) != '/') {
            tagNameBuilder.append(html.charAt(pos));
            pos++;
        }
        String tagName = tagNameBuilder.toString().toLowerCase().trim();
        if (tagName.isEmpty()) return null;

        // Parse attributes
        Map<String, String> attributes = new LinkedHashMap<>();
        boolean selfClosing = false;

        while (pos < len) {
            // Skip whitespace
            while (pos < len && Character.isWhitespace(html.charAt(pos))) pos++;

            if (pos >= len) break;

            if (html.charAt(pos) == '>') {
                pos++;
                break;
            }

            if (html.charAt(pos) == '/') {
                selfClosing = true;
                pos++;
                if (pos < len && html.charAt(pos) == '>') {
                    pos++;
                    break;
                }
                continue;
            }

            // Read attribute name
            StringBuilder attrName = new StringBuilder();
            while (pos < len && html.charAt(pos) != '=' && html.charAt(pos) != '>' && !Character.isWhitespace(html.charAt(pos)) && html.charAt(pos) != '/') {
                attrName.append(html.charAt(pos));
                pos++;
            }

            String name = attrName.toString().toLowerCase().trim();

            // Skip whitespace
            while (pos < len && Character.isWhitespace(html.charAt(pos))) pos++;

            if (pos < len && html.charAt(pos) == '=') {
                pos++; // skip '='
                // Skip whitespace
                while (pos < len && Character.isWhitespace(html.charAt(pos))) pos++;

                if (pos < len) {
                    String value;
                    if (html.charAt(pos) == '"' || html.charAt(pos) == '\'') {
                        char quote = html.charAt(pos);
                        pos++;
                        StringBuilder valBuilder = new StringBuilder();
                        while (pos < len && html.charAt(pos) != quote) {
                            valBuilder.append(html.charAt(pos));
                            pos++;
                        }
                        if (pos < len) pos++; // skip closing quote
                        value = valBuilder.toString();
                    } else {
                        // Unquoted attribute value
                        StringBuilder valBuilder = new StringBuilder();
                        while (pos < len && !Character.isWhitespace(html.charAt(pos)) && html.charAt(pos) != '>' && html.charAt(pos) != '/') {
                            valBuilder.append(html.charAt(pos));
                            pos++;
                        }
                        value = valBuilder.toString();
                    }
                    if (!name.isEmpty()) {
                        attributes.put(name, value);
                    }
                }
            } else {
                // Boolean attribute (no value)
                if (!name.isEmpty()) {
                    attributes.put(name, name);
                }
            }
        }

        ParsedTag tag = new ParsedTag();
        tag.tagName = tagName;
        tag.attributes = attributes;
        tag.selfClosing = selfClosing;
        tag.endPos = pos;
        return tag;
    }

    /**
     * Find the position of the matching closing tag, handling nesting.
     */
    private int findClosingTag(String html, int startPos, String tagName) {
        int depth = 1;
        int pos = startPos;
        int len = html.length();
        String openTag = "<" + tagName;
        String closeTag = "</" + tagName;

        while (pos < len && depth > 0) {
            int nextOpen = indexOfIgnoreCase(html, openTag, pos);
            int nextClose = indexOfIgnoreCase(html, closeTag, pos);

            if (nextClose < 0) {
                // No closing tag found
                return -1;
            }

            if (nextOpen >= 0 && nextOpen < nextClose) {
                // Check if this is actually a tag (not part of text)
                int afterTag = nextOpen + openTag.length();
                if (afterTag < len) {
                    char c = html.charAt(afterTag);
                    if (c == '>' || c == ' ' || c == '/' || c == '\t' || c == '\n') {
                        // Check if it's self-closing
                        int tagEnd = html.indexOf('>', nextOpen);
                        if (tagEnd >= 0 && html.charAt(tagEnd - 1) != '/') {
                            depth++;
                        }
                    }
                }
                pos = nextOpen + openTag.length();
            } else {
                depth--;
                if (depth == 0) {
                    return nextClose;
                }
                pos = nextClose + closeTag.length();
            }
        }
        return -1;
    }

    private int indexOfIgnoreCase(String source, String target, int fromIndex) {
        String lowerSource = source.toLowerCase();
        String lowerTarget = target.toLowerCase();
        return lowerSource.indexOf(lowerTarget, fromIndex);
    }

    /**
     * Create a widget node map from a parsed tag.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> createWidgetNode(ParsedTag parsedTag) {
        Map<String, Object> node = new HashMap<>();
        String tag = mapTag(parsedTag.tagName);
        node.put("tag", tag);

        Map<String, Object> function = new HashMap<>();

        // Extract id
        if (parsedTag.attributes.containsKey("id")) {
            function.put("id", parsedTag.attributes.get("id"));
        }

        // Extract class
        if (parsedTag.attributes.containsKey("class")) {
            function.put("class", parsedTag.attributes.get("class"));
        }

        // Extract tag-specific attributes
        if (parsedTag.attributes.containsKey("href")) {
            function.put("href", parsedTag.attributes.get("href"));
        }
        if (parsedTag.attributes.containsKey("src")) {
            function.put("src", parsedTag.attributes.get("src"));
        }
        if (parsedTag.attributes.containsKey("placeholder")) {
            function.put("placeholder", parsedTag.attributes.get("placeholder"));
        }
        if (parsedTag.attributes.containsKey("type")) {
            function.put("type", parsedTag.attributes.get("type"));
        }
        if (parsedTag.attributes.containsKey("alt")) {
            function.put("alt", parsedTag.attributes.get("alt"));
        }
        if (parsedTag.attributes.containsKey("value")) {
            function.put("text", parsedTag.attributes.get("value"));
        }
        if (parsedTag.attributes.containsKey("controls")) {
            function.put("controls", "true");
        }

        // Extract JS Event attributes (any attribute name starting with "on")
        for (Map.Entry<String, String> entry : parsedTag.attributes.entrySet()) {
            if (entry.getKey().startsWith("on")) {
                function.put(entry.getKey(), entry.getValue());
            }
        }

        // Build style map
        Map<String, Object> styleMap = new HashMap<>();

        // Apply CSS rules based on tag name
        // applyCssRules(styleMap, parsedTag.tagName, parsedTag.attributes);

        // Apply inline styles (highest priority)
        if (parsedTag.attributes.containsKey("style")) {
            Map<String, String> inlineStyles = parseCssProperties(parsedTag.attributes.get("style"));
            for (Map.Entry<String, String> entry : inlineStyles.entrySet()) {
                styleMap.put(entry.getKey(), entry.getValue());
            }
        }

        // Apply default heading styles
        applyDefaultTagStyles(styleMap, parsedTag.tagName);

        function.put("style", styleMap);
        node.put("function", function);

        return node;
    }

    /**
     * Apply CSS rules to the style map based on element tag, classes, and id.
     */
    private void applyCssRules(Map<String, Object> styleMap, String tagName, Map<String, String> attributes) {
        // Apply element selector rules
        if (cssRules.containsKey(tagName)) {
            for (Map.Entry<String, String> entry : cssRules.get(tagName).entrySet()) {
                styleMap.put(entry.getKey(), entry.getValue());
            }
        }

        // Apply class selector rules
        String classAttr = attributes.get("class");
        if (classAttr != null && !classAttr.isEmpty()) {
            String[] classes = classAttr.trim().split("\\s+");
            for (String cls : classes) {
                String classSelector = "." + cls;
                if (cssRules.containsKey(classSelector)) {
                    for (Map.Entry<String, String> entry : cssRules.get(classSelector).entrySet()) {
                        styleMap.put(entry.getKey(), entry.getValue());
                    }
                }
                // Also try tag.class combination
                String tagClassSelector = tagName + "." + cls;
                if (cssRules.containsKey(tagClassSelector)) {
                    for (Map.Entry<String, String> entry : cssRules.get(tagClassSelector).entrySet()) {
                        styleMap.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        // Apply id selector rules
        String idAttr = attributes.get("id");
        if (idAttr != null && !idAttr.isEmpty()) {
            String idSelector = "#" + idAttr;
            if (cssRules.containsKey(idSelector)) {
                for (Map.Entry<String, String> entry : cssRules.get(idSelector).entrySet()) {
                    styleMap.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // Apply wildcard rules
        if (cssRules.containsKey("*")) {
            // Only apply box-sizing and similar universal rules
            Map<String, String> universalRules = cssRules.get("*");
            for (Map.Entry<String, String> entry : universalRules.entrySet()) {
                if (!styleMap.containsKey(entry.getKey())) {
                    styleMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * Apply default styles for heading and semantic tags when no explicit style exists.
     */
    private void applyDefaultTagStyles(Map<String, Object> styleMap, String tagName) {
        switch (tagName) {
            case "h1":
                if (!styleMap.containsKey("fontSize")) styleMap.put("fontSize", "32px");
                if (!styleMap.containsKey("fontWeight")) styleMap.put("fontWeight", "bold");
                break;
            case "h2":
                if (!styleMap.containsKey("fontSize")) styleMap.put("fontSize", "28px");
                if (!styleMap.containsKey("fontWeight")) styleMap.put("fontWeight", "bold");
                break;
            case "h3":
                if (!styleMap.containsKey("fontSize")) styleMap.put("fontSize", "24px");
                if (!styleMap.containsKey("fontWeight")) styleMap.put("fontWeight", "bold");
                break;
            case "h4":
                if (!styleMap.containsKey("fontSize")) styleMap.put("fontSize", "20px");
                if (!styleMap.containsKey("fontWeight")) styleMap.put("fontWeight", "bold");
                break;
            case "h5":
                if (!styleMap.containsKey("fontSize")) styleMap.put("fontSize", "18px");
                if (!styleMap.containsKey("fontWeight")) styleMap.put("fontWeight", "bold");
                break;
            case "h6":
                if (!styleMap.containsKey("fontSize")) styleMap.put("fontSize", "16px");
                if (!styleMap.containsKey("fontWeight")) styleMap.put("fontWeight", "bold");
                break;
            case "a":
                if (!styleMap.containsKey("color")) styleMap.put("color", "#2196F3");
                if (!styleMap.containsKey("textDecoration")) styleMap.put("textDecoration", "underline");
                break;
            case "pre":
            case "code":
                if (!styleMap.containsKey("fontFamily")) styleMap.put("fontFamily", "monospace");
                if (!styleMap.containsKey("backgroundColor")) styleMap.put("backgroundColor", "#F5F5F5");
                if (!styleMap.containsKey("padding")) styleMap.put("padding", "8px");
                break;
            case "blockquote":
                if (!styleMap.containsKey("borderLeftWidth")) styleMap.put("borderLeftWidth", "4px");
                if (!styleMap.containsKey("borderLeftColor")) styleMap.put("borderLeftColor", "#CCCCCC");
                if (!styleMap.containsKey("padding")) styleMap.put("padding", "12px");
                break;
            case "button":
                if (!styleMap.containsKey("padding")) styleMap.put("padding", "10px");
                if (!styleMap.containsKey("borderRadius")) styleMap.put("borderRadius", "4px");
                break;
            case "hr":
                if (!styleMap.containsKey("borderColor")) styleMap.put("borderColor", "#CCCCCC");
                break;
        }
    }

    /**
     * Map HTML5 tags to the closest supported DragWeb tag.
     */
    private String mapTag(String htmlTag) {
        switch (htmlTag) {
            case "thead":
            case "tbody":
            case "tfoot":
            case "tr":
                return "div";
            case "td":
            case "th":
                return "div";
            case "code":
                return "pre";
            case "strong":
            case "b":
            case "em":
            case "i":
            case "u":
            case "small":
            case "mark":
            case "del":
            case "ins":
            case "sub":
            case "sup":
            case "abbr":
            case "cite":
            case "q":
            case "time":
                return "span";
            case "figure":
            case "figcaption":
            case "details":
            case "summary":
            case "dialog":
                return "div";
            case "select":
                return "input";
            default:
                return htmlTag;
        }
    }

    private boolean isSupportedTag(String tag) {
        for (String supported : SUPPORTED_TAGS) {
            if (supported.equals(tag)) return true;
        }
        // Also accept tags that we map to supported ones
        switch (tag) {
            case "thead": case "tbody": case "tfoot": case "tr": case "td": case "th":
            case "code": case "strong": case "b": case "em": case "i": case "u":
            case "small": case "mark": case "del": case "ins": case "sub": case "sup":
            case "abbr": case "cite": case "q": case "time":
            case "figure": case "figcaption": case "details": case "summary": case "dialog":
            case "select":
                return true;
            default:
                return false;
        }
    }

    private boolean isVoidElement(String tag) {
        switch (tag) {
            case "img": case "input": case "hr": case "br":
            case "meta": case "link": case "area": case "base":
            case "col": case "embed": case "source": case "track": case "wbr":
                return true;
            default:
                return false;
        }
    }

    private boolean isContainerTag(String tag) {
        switch (tag) {
            case "div": case "section": case "nav": case "header": case "footer":
            case "main": case "article": case "aside": case "form":
            case "ul": case "ol": case "table":
            case "thead": case "tbody": case "tfoot": case "tr": case "td": case "th":
            case "figure": case "figcaption": case "details": case "summary": case "dialog":
            case "a":
                return true;
            default:
                return false;
        }
    }

    private String decodeHtmlEntities(String text) {
        if (text == null) return "";
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ");
    }

    private int countNodes(List<Map<String, Object>> nodes) {
        int count = 0;
        for (Map<String, Object> node : nodes) {
            count++;
            if (node.containsKey("children")) {
                Object children = node.get("children");
                if (children instanceof List) {
                    count += countNodes((List<Map<String, Object>>) children);
                }
            }
        }
        return count;
    }
}
