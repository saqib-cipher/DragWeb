package sketchweb.gl.dragweb;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The token-replacement engine. Inputs:
 * <ul>
 *   <li>a template string (e.g. {@code <li><a href='%1$s'>%2$s</a></li>})</li>
 *   <li>a list of user-supplied input values</li>
 * </ul>
 * Output: the substituted string.
 *
 * <p>Token grammar supported:
 * <pre>
 * %s          - String input  (positional - consumed in order)
 * %d          - Number input  (positional - consumed in order)
 * %m.id       - HTML id selector
 * %m.class    - CSS class selector
 * %m.tag      - HTML tag selector
 * %m.file     - HTML file selector
 * %m.section  - Section id selector
 * %1$s, %2$s  - Positional placeholders inside templates
 * </pre>
 *
 * <p>The engine purposely does not run any JavaScript. It generates pure
 * static text — the same approach Sketchware uses to emit Java.
 */
public final class DragWebTemplateEngine {

    private DragWebTemplateEngine() {}

    private static final Pattern POSITIONAL = Pattern.compile("%(\\d+)\\$s");
    private static final Pattern PLAIN_TOKEN = Pattern.compile("%(?:m\\.[a-zA-Z]+|s|d)");

    /**
     * Render a template using the inputs supplied. The same input list is
     * consumed by both <em>positional</em> tokens (<code>%1$s</code>) and
     * sequential tokens (<code>%s</code>, <code>%d</code>, <code>%m.*</code>).
     *
     * @param template the raw template string
     * @param inputs   user-supplied substitution values (1-indexed for
     *                 positional references). May be empty/null.
     * @return the substituted string. Missing inputs render as the empty
     *         string rather than throwing — generators commonly call this on
     *         partially-filled blocks and we must not crash the editor.
     */
    public static String render(String template, List<String> inputs) {
        if (template == null) return "";
        List<String> safe = inputs == null ? new ArrayList<>() : inputs;

        String stage1 = renderPositional(template, safe);
        return renderSequential(stage1, safe);
    }

    private static String renderPositional(String template, List<String> inputs) {
        Matcher m = POSITIONAL.matcher(template);
        StringBuffer sb = new StringBuffer(template.length());
        while (m.find()) {
            int idx = Integer.parseInt(m.group(1));
            String value = (idx >= 1 && idx <= inputs.size()) ? inputs.get(idx - 1) : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String renderSequential(String template, List<String> inputs) {
        Matcher m = PLAIN_TOKEN.matcher(template);
        StringBuffer sb = new StringBuffer(template.length());
        int cursor = 0;
        while (m.find()) {
            String value = (cursor < inputs.size()) ? inputs.get(cursor) : "";
            cursor++;
            m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Detect every token in a string in document order. Useful for the UI to
     * present the right number of input slots.
     */
    public static List<String> detectTokens(String template) {
        List<String> tokens = new ArrayList<>();
        if (template == null) return tokens;

        // Walk the string, matching either the positional or plain pattern at
        // each step, whichever comes first. This preserves authoring order.
        int i = 0;
        while (i < template.length()) {
            Matcher pos = POSITIONAL.matcher(template).region(i, template.length());
            Matcher plain = PLAIN_TOKEN.matcher(template).region(i, template.length());

            int posStart = pos.find() ? pos.start() : -1;
            int plainStart = plain.find() ? plain.start() : -1;

            if (posStart == -1 && plainStart == -1) break;

            if (posStart != -1 && (plainStart == -1 || posStart <= plainStart)) {
                tokens.add(pos.group());
                i = pos.end();
            } else {
                tokens.add(plain.group());
                i = plain.end();
            }
        }
        return tokens;
    }
}
