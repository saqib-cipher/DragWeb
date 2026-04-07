package sketchweb.gl;

import android.view.View;
import android.view.ViewGroup;
import java.util.Map;

public class PageCodeGenerator {

    public PageCodeGenerator() {}

    public String generateAllCode(View screen) {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<!DOCTYPE html>\n<html>\n<head>\n");
        htmlBuilder.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        htmlBuilder.append("<style>\n");
        htmlBuilder.append("  body { margin: 0; padding: 0; font-family: sans-serif; }\n");
        htmlBuilder.append("</style>\n");
        htmlBuilder.append("</head>\n<body>\n");

        if (screen instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) screen;
            for (int i = 0; i < vg.getChildCount(); i++) {
                htmlBuilder.append(generateHtmlForView(vg.getChildAt(i), 1));
            }
        }

        htmlBuilder.append("</body>\n</html>");
        return htmlBuilder.toString();
    }

    private String generateHtmlForView(View view, int indentLevel) {
        if (view == null) return "";

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < indentLevel; i++) {
            indent.append("  ");
        }

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

        // Inline Styles
        Map<String, Object> style = (Map<String, Object>) function.get("style");
        if (style != null && !style.isEmpty()) {
            html.append(" style=\"");
            for (Map.Entry<String, Object> entry : style.entrySet()) {
                // simple camelCase to kebab-case (e.g. fontSize -> font-size)
                String cssKey = entry.getKey().replaceAll("([A-Z])", "-$1").toLowerCase();
                html.append(cssKey).append(": ").append(entry.getValue()).append("; ");
            }
            html.append("\"");
        }

        // Attributes specific
        if (tag.equals("img") && function.containsKey("src")) {
            html.append(" src=\"").append(function.get("src")).append("\"");
        }
        if (tag.equals("input") && function.containsKey("type")) {
             html.append(" type=\"").append(function.get("type")).append("\"");
        }
        if (tag.equals("input") && function.containsKey("placeholder")) {
             html.append(" placeholder=\"").append(function.get("placeholder")).append("\"");
        }
        if (tag.equals("a") && function.containsKey("href")) {
             html.append(" href=\"").append(function.get("href")).append("\"");
        }

        if (tag.equals("input") || tag.equals("img") || tag.equals("hr") || tag.equals("br")) {
            html.append(" />\n"); // Self closing
            return html.toString();
        }

        html.append(">");

        // Inner Content
        if (function.containsKey("text")) {
             html.append(function.get("text"));
        }

        // Recursive children if ViewGroup
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
}
