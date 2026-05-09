package sketchweb.gl;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
            EditText edit = new EditText(context);
            edit.setText(value);
            edit.setSelection(edit.getText().length());
            edit.setInputType(numeric ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED) : InputType.TYPE_CLASS_TEXT);
            new AlertDialog.Builder(context)
                .setTitle(input.id != null ? input.id : "Edit")
                .setView(wrap(edit))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String nv = edit.getText().toString();
                    chip.setText(nv.isEmpty() ? (input.placeholder != null ? input.placeholder : "...") : nv);
                    if (listener != null) listener.onChanged(input.id, nv);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });
        return chip;
    }

    private TextView dropdownChip(ChipInput input, String value, int baseColor,
                                  OnChipValueChanged listener, List<String> options) {
        TextView chip = baseChip(baseColor, value.isEmpty() ? "▼" : value + " ▼");
        chip.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(context, chip);
            int order = 0;
            for (String opt : options) pm.getMenu().add(0, order, order++, opt);
            // Always allow free-form entry as the last item.
            pm.getMenu().add(0, options.size(), order, "Custom...");
            pm.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == options.size()) {
                    EditText edit = new EditText(context);
                    edit.setText(value);
                    new AlertDialog.Builder(context)
                        .setTitle(input.id != null ? input.id : "Custom value")
                        .setView(wrap(edit))
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            String nv = edit.getText().toString();
                            chip.setText(nv.isEmpty() ? "▼" : nv + " ▼");
                            if (listener != null) listener.onChanged(input.id, nv);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                    return true;
                }
                String chosen = options.get(item.getItemId());
                chip.setText(chosen + " ▼");
                if (listener != null) listener.onChanged(input.id, chosen);
                return true;
            });
            pm.show();
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
        chip.setOnClickListener(v -> showColorPickerDialog(input.id, value, picked -> {
            chip.setText(picked);
            applyColorSwatch(chip, picked);
            if (listener != null) listener.onChanged(input.id, picked);
        }));
        return chip;
    }

    /**
     * Unified color picker dialog. Top row contains the color preset swatches
     * (tap to commit). Below: live preview swatch + advanced #RRGGBB input.
     */
    private void showColorPickerDialog(String chipId, String current, OnPicked picked) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(0));

        // Live preview / advanced hex field
        EditText hexInput = new EditText(context);
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hexInput.setText(current == null || current.isEmpty() ? "#" : current);

        TextView preview = new TextView(context);
        preview.setHeight(dp(36));
        preview.setMinWidth(dp(64));
        applyColorSwatch(preview, current);
        LinearLayout previewRow = new LinearLayout(context);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams pwLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        pwLp.setMargins(0, 0, dp(10), 0);
        previewRow.addView(preview, pwLp);
        previewRow.addView(hexInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f));

        // Preset palette grid
        LinearLayout palette = new LinearLayout(context);
        palette.setOrientation(LinearLayout.VERTICAL);
        LinearLayout currentRow = null;
        int columns = 5;
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (i % columns == 0) {
                currentRow = new LinearLayout(context);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                palette.addView(currentRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            String hex = COLOR_PRESETS[i];
            TextView swatch = new TextView(context);
            swatch.setHeight(dp(36));
            swatch.setText(" ");
            applyColorSwatch(swatch, hex);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            slp.setMargins(dp(2), dp(2), dp(2), dp(2));
            swatch.setLayoutParams(slp);
            swatch.setOnClickListener(v -> {
                hexInput.setText(hex);
                applyColorSwatch(preview, hex);
            });
            currentRow.addView(swatch);
        }

        hexInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable e) {
                String t = e.toString().trim();
                if (!t.isEmpty()) applyColorSwatch(preview, t);
            }
        });

        TextView paletteLabel = new TextView(context);
        paletteLabel.setText("Presets");
        paletteLabel.setTextSize(12);
        paletteLabel.setPadding(0, dp(12), 0, dp(4));

        root.addView(previewRow);
        root.addView(paletteLabel);
        root.addView(palette);

        new AlertDialog.Builder(context)
            .setTitle(chipId != null ? "Color · " + chipId : "Color")
            .setView(root)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                String nv = hexInput.getText().toString().trim();
                if (!nv.startsWith("#")) nv = "#" + nv;
                if (picked != null) picked.onPicked(nv);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
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
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), 0);

        final String[] modeRef = new String[]{ inferMode(current) };

        final android.widget.AutoCompleteTextView ac = new android.widget.AutoCompleteTextView(context);
        ac.setInputType(InputType.TYPE_CLASS_TEXT);
        ac.setText(stripModePrefix(current));
        ac.setHint("Selector name");
        ac.setThreshold(1);

        LinearLayout modeRow = new LinearLayout(context);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] pills = new TextView[3];
        String[] modeIds = { "id", "class", "tag" };
        String[] labels = { "# id", ". class", "tag" };
        Runnable repaint = () -> {
            for (int i = 0; i < pills.length; i++) {
                pills[i].setAlpha(modeIds[i].equals(modeRef[0]) ? 1f : 0.45f);
            }
        };
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView pill = baseChip(baseColorFor("css"), labels[i]);
            pill.setOnClickListener(v -> {
                modeRef[0] = modeIds[idx];
                repaint.run();
            });
            pills[i] = pill;
            modeRow.addView(pill, pillLp());
        }
        repaint.run();

        java.util.List<String> suggestions = collectSelectorSuggestions();
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
            context, android.R.layout.simple_dropdown_item_1line, suggestions);
        ac.setAdapter(adapter);

        TextView modeLabel = new TextView(context);
        modeLabel.setText("Match by");
        modeLabel.setTextSize(12);
        modeLabel.setPadding(0, 0, 0, dp(4));

        root.addView(modeLabel);
        root.addView(modeRow);
        TextView nameLabel = new TextView(context);
        nameLabel.setText("Selector");
        nameLabel.setTextSize(12);
        nameLabel.setPadding(0, dp(12), 0, dp(4));
        root.addView(nameLabel);
        root.addView(ac);

        new AlertDialog.Builder(context)
            .setTitle(input.id != null ? "Pick selector · " + input.id : "Pick selector")
            .setView(root)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                String name = ac.getText().toString().trim();
                String composed = composeSelector(modeRef[0], name);
                if (picked != null) picked.onPicked(composed);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
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

    private static String stripModePrefix(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.startsWith("#") || v.startsWith(".")) return v.substring(1);
        return v;
    }

    private LinearLayout.LayoutParams pillLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private int baseColorFor(String category) {
        return BlockCategoryPalette.colorIntForCategory(category);
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

    private LinearLayout wrap(View v) {
        LinearLayout ll = new LinearLayout(context);
        ll.setPadding(dp(20), dp(8), dp(20), 0);
        ll.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return ll;
    }

    private int dp(int px) {
        return (int) (px * context.getResources().getDisplayMetrics().density);
    }
}
