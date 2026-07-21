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
    public String catColor;   // Optional category color hex
    public String description;
    public List<ChipInput> inputs;

    // Sketchware blocks.json compatibility fields
    public String name;
    public String spec;
    public String palette;
    public String type;
    public String blockType;

    public String getOpCode() {
        return name != null && !name.isEmpty() ? name : id;
    }

    public String getSpec() {
        return spec != null && !spec.isEmpty() ? spec : label;
    }

    public String getType() {
        if (type != null && !type.isEmpty()) return type;
        if (blockType != null && !blockType.isEmpty()) {
            if (blockType.equals("normal") || blockType.equals("v") || blockType.equals(" ")) {
                return " ";
            }
            return blockType;
        }
        return " ";
    }

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
        if ("cblock".equals(s) || "loop".equals(s) || "condition".equals(s)) return true;
        // Also treat as container if template contains %m.space token
        String t = resolvedTemplate();
        return t != null && t.contains("%m.space");
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
        if (derivedInputs != null) return derivedInputs;
        derivedInputs = new ArrayList<>();
        String src = resolvedTemplate();
        if (src.isEmpty()) return derivedInputs;

        Pattern p = Pattern.compile("%(?:(selector)|(?:(?:(\\d+)\\$)?(?:(m\\.[a-zA-Z_\\.]+|var\\.[sbd]|var)|([nsbd]))))");
        Matcher m = p.matcher(src);
        int idx = 0;
        while (m.find()) {
            ChipInput chip = new ChipInput();
            
            String selectorLiteral = m.group(1);
            String pos = m.group(2);
            String mType = m.group(3);
            String letterType = m.group(4);
            
            chip.id = (pos != null) ? "p" + pos : "p" + idx;
            
            if (selectorLiteral != null) {
                chip.type = "selector";
                chip.selector = "any";
            } else if (mType != null) {
                String cleanSelector = mType;
                if (mType.startsWith("m.")) {
                    cleanSelector = mType.substring(2);
                }
                chip.type = mapSelectorToType(cleanSelector);
                chip.selector = cleanSelector;
            } else if (letterType != null) {
                if ("n".equals(letterType) || "d".equals(letterType)) {
                    chip.type = "number";
                } else if ("b".equals(letterType)) {
                    chip.type = "boolean";
                } else {
                    chip.type = "text";
                }
            } else {
                chip.type = "text";
            }
            
            if (inputs != null && idx < inputs.size()) {
                ChipInput original = inputs.get(idx);
                if (original.defaultValue != null) chip.defaultValue = original.defaultValue;
                if (original.options != null) chip.options = original.options;
                if (original.placeholder != null) chip.placeholder = original.placeholder;
                if (original.paramType != null) chip.paramType = original.paramType;
            }
            
            if (chip.defaultValue == null) {
                chip.defaultValue = defaultForType(chip.type);
            }
            derivedInputs.add(chip);
            idx++;
        }

        if (derivedInputs.isEmpty() && inputs != null) {
            derivedInputs.addAll(inputs);
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

    private static List<BlockDef> cacheDefs;

    public static void clearCache() {
        cacheDefs = null;
    }

    public static List<BlockDef> getDefinitions(android.content.Context context) {
        if (cacheDefs != null && !cacheDefs.isEmpty()) return cacheDefs;
        cacheDefs = new ArrayList<>();
        if (context == null) return cacheDefs;

        try {
            java.io.File file = CustomStorageUtil.getCustomFile(context, "blocks.json");
            String json = FileUtil.readFile(file.getAbsolutePath());
            if (json != null && !json.trim().isEmpty()) {
                List<BlockDef> loaded = new com.google.gson.Gson().fromJson(json,
                    new com.google.gson.reflect.TypeToken<List<BlockDef>>(){}.getType());
                if (loaded != null) cacheDefs = loaded;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cacheDefs;
    }
}

