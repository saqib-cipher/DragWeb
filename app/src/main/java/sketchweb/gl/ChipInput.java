package sketchweb.gl;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Inline editable chip definition.
 *
 * <p>Drives the inline-editable widgets rendered next to a block label –
 * tap-to-edit number/text fields, dropdowns, color pickers, boolean toggles,
 * variable references and selector pickers.
 */
public class ChipInput {
    /** Stable id used to look up a value in a block instance. */
    public String id;
    /** text | number | dropdown | boolean | color | variable | selector | container */
    public String type;
    /** Fallback value used the first time a chip is rendered. */
    @SerializedName("default")
    public String defaultValue;
    /** Static option list for {@code dropdown} chips. */
    public List<String> options;
    /** Selector kind for {@code selector} chips: id|class|tag|file|section. */
    public String selector;
    /** When non-empty, dropdown options come from {@code BlockParamTypeManager}. */
    public String paramType;
    /** Optional placeholder shown when the value is empty. */
    public String placeholder;
}
