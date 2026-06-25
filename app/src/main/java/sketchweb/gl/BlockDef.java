package sketchweb.gl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schema definition for a single block, loaded from blocks.json.
 *
 * <p>Supports the modern Sketchware/Blockly-style schema:
 * <pre>
 * {
 *   "id": "setWidth",
 *   "category": "css",
 *   "shape": "stack",
 *   "color": "#2196F3",
 *   "label": "set width",
 *   "code": "width: %n%m.unit;",
 *   "inputs": [
 *     {"id":"value","type":"number","default":"100"},
 *     {"id":"unit","type":"dropdown","default":"px","options":["px","%","rem","vw","vh"]}
 *   ]
 * }
 * </pre>
 *
 * <p>Older entries that only carry {id, label, code, category, shape} stay
 * fully supported – {@link #resolvedInputs()} derives inputs from {@code code}
 * tokens and {@link #resolvedColor()} falls back to the category palette.
 */
public class BlockDef {

    public String id;
    public String label;
    public String code;       // Template e.g. "width: %n%m.unit;"
    public String template;   // Alias accepted by the schema
    public String category;
    public String shape;      // stack | cblock | event | boolean | value | reporter | loop | condition
    public String color;      // Optional hex
    public String description;
    public List<ChipInput> inputs;

    private transient List<ChipInput> derivedInputs;

    public String resolvedTemplate() {
        if (template != null && !template.isEmpty()) return template;
        return code != null ? code : "";
    }

    public String resolvedShape() {
        if (shape == null || shape.isEmpty()) {
            if ("event".equals(category)) return "event";
            if ("value".equals(category)) return "value";
            return "stack";
        }
        // Map legacy shape codes to modern ones.
        switch (shape) {
            case "C": return "cblock";
            case "E": return "cblock";
            case "rect": return "stack";
            case "cap": return "event";
            default: return shape;
        }
    }

    public boolean isContainer() {
        String s = resolvedShape();
        return "cblock".equals(s) || "loop".equals(s) || "condition".equals(s);
    }

    public boolean isReporter() {
        String s = resolvedShape();
        return "value".equals(s) || "reporter".equals(s) || "boolean".equals(s);
    }

    public String resolvedColor() {
        if (color != null && !color.isEmpty()) return color;
        return BlockCategoryPalette.colorForCategory(category);
    }

    /**
     * Effective input list for this block. If {@link #inputs} is provided in
     * the JSON, it is returned verbatim. Otherwise tokens inside the code
     * template ({@code %n}, {@code %s}, {@code %b}, {@code %m.<type>}) are
     * parsed and converted to implicit chip inputs in declaration order.
     */
    public List<ChipInput> resolvedInputs() {
        if (inputs != null && !inputs.isEmpty()) return inputs;
        if (derivedInputs != null) return derivedInputs;
        derivedInputs = new ArrayList<>();
        String src = resolvedTemplate();
        if (src.isEmpty()) return derivedInputs;

        Pattern p = Pattern.compile("%(?:m\\.([a-zA-Z]+)|([nsbd]))");
        Matcher m = p.matcher(src);
        int idx = 0;
        while (m.find()) {
            ChipInput chip = new ChipInput();
            chip.id = "p" + idx;
            if (m.group(1) != null) {
                String selector = m.group(1);
                chip.type = mapSelectorToType(selector);
                chip.selector = selector;
            } else {
                String t = m.group(2);
                if ("n".equals(t) || "d".equals(t)) chip.type = "number";
                else if ("b".equals(t)) chip.type = "boolean";
                else chip.type = "text";
            }
            chip.defaultValue = defaultForType(chip.type);
            derivedInputs.add(chip);
            idx++;
        }
        return derivedInputs;
    }

    private static String mapSelectorToType(String selector) {
        if (selector == null) return "text";
        switch (selector) {
            case "color": return "color";
            case "id":
            case "class":
            case "tag":
            case "file":
            case "section":
            case "selector":
                return "selector";
            case "space": return "container";
            case "boolean": return "boolean";
            default: return "dropdown";
        }
    }

    private static String defaultForType(String type) {
        switch (type) {
            case "number": return "0";
            case "boolean": return "false";
            case "color": return "#2196F3";
            default: return "";
        }
    }
}
