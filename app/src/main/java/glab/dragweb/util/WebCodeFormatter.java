package glab.dragweb.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import java.util.Locale;

/**
 * Lightweight offline pretty-printer for HTML, CSS, JavaScript, and JSON source code.
 */
public final class WebCodeFormatter {

    private WebCodeFormatter() {}

    public static String format(String code, String mode) {
        if (code == null || code.trim().isEmpty()) return "";
        String m = mode != null ? mode.trim().toLowerCase(Locale.ROOT) : "";
        if ("json".equals(m)) {
            return formatJson(code);
        }
        if ("css".equals(m)) {
            return formatBraceCode(code);
        }
        if ("javascript".equals(m) || "js".equals(m)) {
            return formatBraceCode(code);
        }
        if ("html".equals(m) || "htmlmixed".equals(m) || "xml".equals(m)) {
            return formatHtml(code);
        }
        String trimmed = code.trim();
        if (trimmed.startsWith("<")) return formatHtml(code);
        return formatBraceCode(code);
    }

    private static String formatJson(String raw) {
        try {
            return new GsonBuilder().setPrettyPrinting().create()
                    .toJson(JsonParser.parseString(raw));
        } catch (Exception e) {
            return raw;
        }
    }

    /** Formats C-like brace code (JS/CSS) with consistent 2-space indentation. */
    public static String formatBraceCode(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        int indent = 0;
        boolean inStr = false;
        char strQuote = 0;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean lineStart = true;

        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        int len = normalized.length();
        for (int i = 0; i < len; i++) {
            char c = normalized.charAt(i);
            char next = (i + 1 < len) ? normalized.charAt(i + 1) : '\0';

            if (inLineComment) {
                out.append(c);
                if (c == '\n') {
                    inLineComment = false;
                    lineStart = true;
                }
                continue;
            }
            if (inBlockComment) {
                out.append(c);
                if (c == '*' && next == '/') {
                    out.append('/');
                    i++;
                    inBlockComment = false;
                } else if (c == '\n') {
                    lineStart = true;
                }
                continue;
            }
            if (inStr) {
                out.append(c);
                if (c == '\\' && i + 1 < len) {
                    out.append(next);
                    i++;
                } else if (c == strQuote) {
                    inStr = false;
                } else if (c == '\n') {
                    lineStart = true;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                appendIndentIfNeeded(out, indent, lineStart);
                lineStart = false;
                out.append("//");
                i++;
                inLineComment = true;
                continue;
            }
            if (c == '/' && next == '*') {
                appendIndentIfNeeded(out, indent, lineStart);
                lineStart = false;
                out.append("/*");
                i++;
                inBlockComment = true;
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') {
                appendIndentIfNeeded(out, indent, lineStart);
                lineStart = false;
                inStr = true;
                strQuote = c;
                out.append(c);
                continue;
            }

            if (c == '\n') {
                trimTrailingHorizontalSpace(out);
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                lineStart = true;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (!lineStart && out.length() > 0) {
                    char prev = out.charAt(out.length() - 1);
                    if (prev != ' ' && prev != '\n') {
                        out.append(' ');
                    }
                }
                continue;
            }

            if (c == '{') {
                appendIndentIfNeeded(out, indent, lineStart);
                trimTrailingHorizontalSpace(out);
                if (!lineStart && out.length() > 0 && out.charAt(out.length() - 1) != '\n' && out.charAt(out.length() - 1) != '(') {
                    out.append(' ');
                }
                out.append("{\n");
                indent++;
                lineStart = true;
            } else if (c == '}') {
                indent = Math.max(0, indent - 1);
                trimTrailingHorizontalSpace(out);
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                appendSpaces(out, indent * 2);
                out.append('}');
                if (next != ';' && next != ',' && next != ')' && next != '\0' && next != '\n') {
                    out.append('\n');
                    lineStart = true;
                } else {
                    lineStart = false;
                }
            } else if (c == ':') {
                appendIndentIfNeeded(out, indent, lineStart);
                lineStart = false;
                out.append(':');
                if (indent > 0 && next != ' ' && next != ':' && next != '\n' && isDeclarationColonAhead(normalized, i + 1)) {
                    out.append(' ');
                }
            } else if (c == ';') {
                appendIndentIfNeeded(out, indent, lineStart);
                trimTrailingHorizontalSpace(out);
                out.append(';');
                if (indent > 0 || next == '}' || next == '\n' || next == '\0') {
                    out.append('\n');
                    lineStart = true;
                } else {
                    lineStart = false;
                }
            } else {
                appendIndentIfNeeded(out, indent, lineStart);
                lineStart = false;
                out.append(c);
            }
        }
        return out.toString().trim() + "\n";
    }

    /** Formats HTML markup with clean tag nesting indentation. */
    public static String formatHtml(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        String s = raw.replace("\r\n", "\n").trim();
        int len = s.length();
        int i = 0;
        int indent = 0;

        while (i < len) {
            while (i < len && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i >= len) break;

            if (s.charAt(i) == '<') {
                int end = findTagEnd(s, i);
                String tag = s.substring(i, end + 1).trim();
                String lower = tag.toLowerCase(Locale.ROOT);
                boolean isCommentOrDecl = lower.startsWith("<!--") || lower.startsWith("<!");
                boolean isClosing = lower.startsWith("</");
                boolean isSelfClosing = tag.endsWith("/>") || isVoidHtmlTag(lower);

                if (isClosing) {
                    indent = Math.max(0, indent - 1);
                }

                // Inline simple <tag>short text</tag> on one line when next is text + matching closing tag
                if (!isCommentOrDecl && !isClosing && !isSelfClosing) {
                    String tagName = extractTagName(lower);
                    int nextClose = findInlineTextWithCloseTag(s, end + 1, tagName);
                    if (nextClose >= 0) {
                        String innerAndClose = s.substring(end + 1, nextClose);
                        appendSpaces(out, indent * 2);
                        out.append(tag).append(innerAndClose.trim()).append('\n');
                        i = nextClose;
                        continue;
                    }
                }

                appendSpaces(out, indent * 2);
                out.append(tag).append('\n');

                if (!isCommentOrDecl && !isClosing && !isSelfClosing) {
                    indent++;
                }
                i = end + 1;
            } else {
                int nextLt = s.indexOf('<', i);
                if (nextLt < 0) nextLt = len;
                String text = s.substring(i, nextLt).trim();
                if (!text.isEmpty()) {
                    appendSpaces(out, indent * 2);
                    out.append(text).append('\n');
                }
                i = nextLt;
            }
        }
        return out.toString().trim() + "\n";
    }

    private static int findTagEnd(String s, int start) {
        if (s.startsWith("<!--", start)) {
            int cEnd = s.indexOf("-->", start + 4);
            return cEnd >= 0 ? cEnd + 2 : s.length() - 1;
        }
        boolean inStr = false;
        char q = 0;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == q) inStr = false;
            } else if (c == '"' || c == '\'') {
                inStr = true;
                q = c;
            } else if (c == '>') {
                return i;
            }
        }
        return s.length() - 1;
    }

    private static int findInlineTextWithCloseTag(String s, int from, String tagName) {
        if (tagName.isEmpty()) return -1;
        int nextLt = s.indexOf('<', from);
        if (nextLt < 0) return -1;
        String text = s.substring(from, nextLt);
        if (text.contains("\n") || text.trim().length() > 80) return -1;
        int closeEnd = findTagEnd(s, nextLt);
        String closing = s.substring(nextLt, closeEnd + 1).trim().toLowerCase(Locale.ROOT);
        if (closing.equals("</" + tagName + ">")) {
            return closeEnd + 1;
        }
        return -1;
    }

    private static String extractTagName(String lowerTag) {
        if (lowerTag.length() < 2) return "";
        int i = 1;
        while (i < lowerTag.length() && Character.isLetterOrDigit(lowerTag.charAt(i))) {
            i++;
        }
        return lowerTag.substring(1, i);
    }

    private static boolean isVoidHtmlTag(String lowerTag) {
        String name = extractTagName(lowerTag);
        return "area".equals(name) || "base".equals(name) || "br".equals(name)
                || "col".equals(name) || "embed".equals(name) || "hr".equals(name)
                || "img".equals(name) || "input".equals(name) || "link".equals(name)
                || "meta".equals(name) || "param".equals(name) || "source".equals(name)
                || "track".equals(name) || "wbr".equals(name);
    }

    private static boolean isDeclarationColonAhead(String s, int from) {
        for (int k = from; k < s.length(); k++) {
            char ch = s.charAt(k);
            if (ch == ';' || ch == '}') return true;
            if (ch == '{') return false;
        }
        return false;
    }

    private static void appendIndentIfNeeded(StringBuilder sb, int indent, boolean lineStart) {
        if (lineStart) {
            appendSpaces(sb, indent * 2);
        }
    }

    private static void appendSpaces(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) sb.append(' ');
    }

    private static void trimTrailingHorizontalSpace(StringBuilder sb) {
        while (sb.length() > 0) {
            char ch = sb.charAt(sb.length() - 1);
            if (ch == ' ' || ch == '\t') {
                sb.deleteCharAt(sb.length() - 1);
            } else {
                break;
            }
        }
    }
}
