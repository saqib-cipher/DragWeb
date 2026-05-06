package sketchweb.gl.dragweb;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Definition of a single block. Mirrors the on-disk JSON shape from
 * <code>/.dragweb/custom/blocks.json</code>:
 *
 * <pre>
 * {
 *   "id":"menu_link",
 *   "display":"Add menu link %s to %s",
 *   "template":"&lt;li&gt;&lt;a href='%1$s'&gt;%2$s&lt;/a&gt;&lt;/li&gt;",
 *   "category":"HTML"
 * }
 * </pre>
 */
public class DragWebBlock implements Serializable {

    @SerializedName("id")
    public String id = "";

    @SerializedName("display")
    public String display = "";

    @SerializedName("template")
    public String template = "";

    @SerializedName("category")
    public String category = "HTML";

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("%(?:\\d+\\$)?(?:m\\.[a-zA-Z]+|s|d)");

    public DragWebBlock() {}

    public DragWebBlock(String id, String display, String template, String category) {
        this.id = id;
        this.display = display;
        this.template = template;
        this.category = category;
    }

    /**
     * Tokens occur inside the <em>display</em> string. Each token corresponds
     * to one user-supplied input slot (in display-order). The display tokens
     * therefore declare the parameter list for the block.
     */
    public List<String> displayTokens() {
        return findTokens(display);
    }

    /**
     * Returns the maximum number of inputs the template references. Templates
     * use positional tokens like <code>%1$s</code>, <code>%2$s</code>; the
     * arity is {@code max index} rather than count, since positions may
     * repeat.
     */
    public int templateArity() {
        if (template == null) return 0;
        int max = 0;
        Matcher m = Pattern.compile("%(\\d+)\\$s").matcher(template);
        while (m.find()) {
            try {
                int idx = Integer.parseInt(m.group(1));
                if (idx > max) max = idx;
            } catch (NumberFormatException ignored) {}
        }
        return max;
    }

    /** Inputs needed: take the larger of display tokens vs template arity. */
    public int inputCount() {
        int byDisplay = displayTokens().size();
        int byTemplate = templateArity();
        return Math.max(byDisplay, byTemplate);
    }

    static List<String> findTokens(String s) {
        List<String> tokens = new ArrayList<>();
        if (s == null) return tokens;
        Matcher m = TOKEN_PATTERN.matcher(s);
        while (m.find()) tokens.add(m.group());
        return tokens;
    }
}
