package sketchweb.gl;

import android.view.View;
import android.view.ViewGroup;
import java.util.Map;

public class PageCodeGenerator {

    public PageCodeGenerator() {}

    public String generateAllCode(View screen) {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        htmlBuilder.append("  <meta charset=\"UTF-8\">\n");
        htmlBuilder.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        htmlBuilder.append("  <title>DragWeb Page</title>\n");
        htmlBuilder.append("  <style>\n");
        htmlBuilder.append("    * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        htmlBuilder.append("    body { font-family: sans-serif; line-height: 1.6; }\n");
        htmlBuilder.append("    button { cursor: pointer; font-family: inherit; }\n");
        htmlBuilder.append("    input { font-family: inherit; }\n");
        htmlBuilder.append("    img { max-width: 100%; height: auto; }\n");
        htmlBuilder.append("  </style>\n");
        htmlBuilder.append("</head>\n<body>\n");

        if (screen instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) screen;
            for (int i = 0; i < vg.getChildCount(); i++) {
                htmlBuilder.append(generateHtmlForView(vg.getChildAt(i), 1));
            }
        }

        htmlBuilder.append("\n  <script>\n");
        htmlBuilder.append("    document.addEventListener('DOMContentLoaded', function() {\n");
        htmlBuilder.append("      console.log('Page loaded successfully!');\n");
        htmlBuilder.append("    });\n");
        htmlBuilder.append("  </script>\n");
        htmlBuilder.append("</body>\n</html>");
        return htmlBuilder.toString();
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

        // Inline Styles
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
            html.append(" src=\"").append(escapeAttr(function.get("src").toString())).append("\"");
            html.append(" alt=\"Image\"");
        }
        if ("input".equals(tag) && function.containsKey("type")) {
            html.append(" type=\"").append(escapeAttr(function.get("type").toString())).append("\"");
        }
        if ("input".equals(tag) && function.containsKey("placeholder")) {
            html.append(" placeholder=\"").append(escapeAttr(function.get("placeholder").toString())).append("\"");
        }
        if ("a".equals(tag) && function.containsKey("href")) {
            html.append(" href=\"").append(escapeAttr(function.get("href").toString())).append("\"");
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
}
