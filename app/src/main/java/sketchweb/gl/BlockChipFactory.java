package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the inline editable chip {@link android.view.View Views} that live
 * inside a block. Each chip renders the value, ripples on touch, and opens
 * the appropriate inline editor (number pad, dropdown menu, color hex input,
 * boolean toggle).
 *
 * <p>The factory is deliberately stateless – the host view holds the value,
 * passes it in, and receives the new value via {@link OnChipValueChanged}.
 */
final class BlockChipFactory {

    interface OnChipValueChanged {
        void onChanged(String chipId, String newValue);
    }

    private final Context context;
    private final BlockParamTypeManager paramTypes;
    private final CustomBlockManager customBlocks;

    BlockChipFactory(Context context,
                     BlockParamTypeManager paramTypes,
                     CustomBlockManager customBlocks) {
        this.context = context;
        this.paramTypes = paramTypes;
        this.customBlocks = customBlocks;
    }

    View buildChip(ChipInput input,
                   String currentValue,
                   int blockBaseColor,
                   OnChipValueChanged listener) {
        if (input == null) return new View(context);
        String value = currentValue != null && !currentValue.isEmpty()
            ? currentValue
            : (input.defaultValue != null ? input.defaultValue : "");
        String type = input.type != null ? input.type : "text";
        switch (type) {
            case "boolean":  return booleanChip(input, value, blockBaseColor, listener);
            case "color":    return colorChip(input, value, blockBaseColor, listener);
            case "dropdown": return dropdownChip(input, value, blockBaseColor, listener, resolveDropdownOptions(input));
            case "selector": return selectorChip(input, value, blockBaseColor, listener);
            case "variable": return dropdownChip(input, value, blockBaseColor, listener, resolveVariableOptions());
            case "number":   return textChip(input, value, blockBaseColor, listener, true);
            default:         return textChip(input, value, blockBaseColor, listener, false);
        }
    }

    /** Hex strings used by the color preset row. */
    private static final String[] COLOR_PRESETS = {
        "#000000", "#FFFFFF", "#F44336", "#E91E63",
        "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
        "#03A9F4", "#00BCD4", "#009688", "#4CAF50",
        "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107",
        "#FF9800", "#FF5722", "#795548", "#9E9E9E"
    };

    // -------------------------------------------------------------------
    // Chip implementations
    // -------------------------------------------------------------------

    private TextView baseChip(int baseColor, String text) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextColor(BlockCategoryPalette.darken(baseColor));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setMinWidth(dp(40));
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setMaxWidth(dp(180));
        int padX = dp(10);
        int padY = dp(4);
        chip.setPadding(padX, padY, padX, padY);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x33000000);

        ShapeDrawable mask = new ShapeDrawable(new RoundRectShape(roundedCorners(dp(14)), null, null));
        mask.getPaint().setColor(Color.WHITE);
        RippleDrawable ripple = new RippleDrawable(android.content.res.ColorStateList.valueOf(0x40000000), bg, mask);
        chip.setBackground(ripple);

        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(3), dp(2), dp(3), dp(2));
        chip.setLayoutParams(lp);
        return chip;
    }
    private TextView textChip(ChipInput input, String value, int baseColor,
                              OnChipValueChanged listener, boolean numeric) {
        String display = value.isEmpty() ? (input.placeholder != null ? input.placeholder : "...") : value;
        TextView chip = baseChip(baseColor, display);
        chip.setOnClickListener(v -> {
            new UniversalM3Dialog(context)
                .setTitle(input.id != null ? input.id : "Edit")
                .setHint(input.placeholder != null ? input.placeholder : "Value")
                .setInitialValue(value)
                .showTextInput(nv -> {
                    chip.setText(nv.isEmpty() ? (input.placeholder != null ? input.placeholder : "...") : nv);
                    if (listener != null) listener.onChanged(input.id, nv);
                });
        });
        return chip;
    }

    private TextView dropdownChip(ChipInput input, String value, int baseColor,
                                  OnChipValueChanged listener, List<String> options) {
        TextView chip = baseChip(baseColor, value.isEmpty() ? "▼" : value + " ▼");
        chip.setOnClickListener(v -> {
            // The previous ListPopupWindow anchored to the chip squeezed unit
            // pickers (px / % / em / …) into a 40dp-wide column, rendering
            // each character on its own line. The outlined-radio-with-custom
            // dialog gives the row room to breathe and still keeps a custom
            // input for free-form values.
            new UniversalM3Dialog(context)
                .setTitle(input.id != null ? input.id : "Choose")
                .setHint(input.placeholder != null ? input.placeholder : "Custom value")
                .setInitialValue(value)
                .showRadioWithCustom(options, value, chosen -> {
                    chip.setText(chosen.isEmpty() ? "▼" : chosen + " ▼");
                    if (listener != null) listener.onChanged(input.id, chosen);
                });
        });
        return chip;
    }

    private TextView booleanChip(ChipInput input, String value, int baseColor,
                                 OnChipValueChanged listener) {
        boolean[] state = { "true".equalsIgnoreCase(value) };
        TextView chip = baseChip(baseColor, state[0] ? "true" : "false");
        chip.setOnClickListener(v -> {
            state[0] = !state[0];
            chip.setText(state[0] ? "true" : "false");
            if (listener != null) listener.onChanged(input.id, state[0] ? "true" : "false");
        });
        return chip;
    }

    private TextView colorChip(ChipInput input, String value, int baseColor,
                               OnChipValueChanged listener) {
        TextView chip = baseChip(baseColor, value.isEmpty() ? "#FFFFFF" : value);
        applyColorSwatch(chip, value);
        chip.setOnClickListener(v -> {
            new UniversalM3Dialog(context)
                .setTitle(input.id != null ? "Color · " + input.id : "Color")
                .setInitialValue(value)
                .showColorInput(picked -> {
                    chip.setText(picked);
                    applyColorSwatch(chip, picked);
                    if (listener != null) listener.onChanged(input.id, picked);
                });
        });
        return chip;
    }

    private interface OnPicked { void onPicked(String value); }

    /**
     * Selector chip dialog: 3 horizontal mode pills (#id / .class / tag) plus
     * an autocomplete input for fast searching of saved selectors. The chosen
     * mode prefix is auto-applied so the chip value is always a complete CSS
     * selector ready to splice into a generated rule.
     */
    private TextView selectorChip(ChipInput input, String value, int baseColor,
                                  OnChipValueChanged listener) {
        TextView chip = baseChip(baseColor, value.isEmpty() ? "▼" : value);
        chip.setOnClickListener(v -> showSelectorPickerDialog(input, value, picked -> {
            chip.setText(picked.isEmpty() ? "▼" : picked);
            if (listener != null) listener.onChanged(input.id, picked);
        }));
        return chip;
    }

    private void showSelectorPickerDialog(ChipInput input, String current, OnPicked picked) {
        // %m.selector now goes through the universal outlined-radio dialog so
        // the picker shares the same Done/Cancel layout as every other chip.
        // Existing selectors gathered from BlockParamTypeManager show up as
        // radio rows; the custom input row lets the user type a fresh one.
        java.util.List<String> presets = collectSelectorSuggestions();
        new UniversalM3Dialog(context)
            .setTitle(input.id != null ? "Pick selector · " + input.id : "Pick selector")
            .setHint("e.g. #header, .btn, h1")
            .setInitialValue(current)
            .showRadioWithCustom(presets, current, chosen -> {
                String composed = composeSelector(inferMode(chosen), chosen);
                if (picked != null) picked.onPicked(composed);
            });
    }

    private java.util.List<String> collectSelectorSuggestions() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (paramTypes != null) {
            for (String key : new String[]{"selectors_id", "selectors_class", "selectors_tag"}) {
                java.util.List<String> opts = paramTypes.getOptions(key);
                if (opts != null) out.addAll(opts);
            }
        }
        if (out.isEmpty()) {
            String[] defaults = {
                "body", "html", "h1", "h2", "h3", "p", "a", "button", "input",
                "ul", "li", "img", "section", "article", "header", "footer",
                "nav", "main", "div", "span"
            };
            java.util.Collections.addAll(out, defaults);
        }
        return out;
    }

    private static String composeSelector(String mode, String name) {
        if (name == null) name = "";
        name = name.replaceFirst("^[#.]", "");
        if ("id".equals(mode)) return "#" + name;
        if ("class".equals(mode)) return "." + name;
        return name;
    }

    private static String inferMode(String value) {
        if (value == null) return "tag";
        String v = value.trim();
        if (v.startsWith("#")) return "id";
        if (v.startsWith(".")) return "class";
        return "tag";
    }

    private void applyColorSwatch(TextView chip, String hex) {
        int parsed;
        try { parsed = Color.parseColor(hex); } catch (Exception e) { parsed = 0xFFFFFFFF; }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(parsed);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x55000000);
        chip.setBackground(bg);
        // Auto contrasting text color
        double luma = 0.299 * Color.red(parsed) + 0.587 * Color.green(parsed) + 0.114 * Color.blue(parsed);
        chip.setTextColor(luma < 140 ? Color.WHITE : Color.BLACK);
    }

    // -------------------------------------------------------------------
    // Option resolution
    // -------------------------------------------------------------------

    private List<String> resolveDropdownOptions(ChipInput input) {
        if (input.options != null && !input.options.isEmpty()) return input.options;
        if (input.paramType != null && paramTypes != null) {
            List<String> opts = paramTypes.getOptions(input.paramType);
            if (opts != null && !opts.isEmpty()) return opts;
        }
        if (input.id != null && paramTypes != null) {
            List<String> opts = paramTypes.getOptions(input.id);
            if (opts != null && !opts.isEmpty()) return opts;
        }
        return new ArrayList<>();
    }

    private List<String> resolveVariableOptions() {
        // Variable list comes from the workspace; for now allow free-form.
        return new ArrayList<>();
    }

    private float[] roundedCorners(int r) {
        return new float[]{ r,r, r,r, r,r, r,r };
    }

    private int dp(int px) {
        return (int) (px * context.getResources().getDisplayMetrics().density);
    }
}
