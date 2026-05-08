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
        String value = currentValue != null ? currentValue : (input.defaultValue != null ? input.defaultValue : "");
        String type = input.type != null ? input.type : "text";
        switch (type) {
            case "boolean":  return booleanChip(input, value, blockBaseColor, listener);
            case "color":    return colorChip(input, value, blockBaseColor, listener);
            case "dropdown": return dropdownChip(input, value, blockBaseColor, listener, resolveDropdownOptions(input));
            case "selector": return dropdownChip(input, value, blockBaseColor, listener, resolveSelectorOptions(input));
            case "variable": return dropdownChip(input, value, blockBaseColor, listener, resolveVariableOptions());
            case "number":   return textChip(input, value, blockBaseColor, listener, true);
            default:         return textChip(input, value, blockBaseColor, listener, false);
        }
    }

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
        chip.setOnClickListener(v -> {
            EditText edit = new EditText(context);
            edit.setText(value.isEmpty() ? "#" : value);
            edit.setInputType(InputType.TYPE_CLASS_TEXT);
            new AlertDialog.Builder(context)
                .setTitle("Color (#RRGGBB)")
                .setView(wrap(edit))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String nv = edit.getText().toString().trim();
                    if (!nv.startsWith("#")) nv = "#" + nv;
                    chip.setText(nv);
                    applyColorSwatch(chip, nv);
                    if (listener != null) listener.onChanged(input.id, nv);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });
        return chip;
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

    private List<String> resolveSelectorOptions(ChipInput input) {
        // Selector chips ideally pull from the page tree via CustomBlockManager
        // but at edit-time the active tree is owned by the activity; expose a
        // free-form fallback so the chip is always usable.
        List<String> opts = new ArrayList<>();
        if (input.options != null) opts.addAll(input.options);
        return opts;
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
