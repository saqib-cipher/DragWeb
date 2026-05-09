package sketchweb.gl;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Arrays;
import java.util.List;

/**
 * Chained-builder facade over {@link UniversalDialog}. Lets callers compose a
 * dialog with a fluent API:
 *
 * <pre>{@code
 * new UniversalM3Dialog(ctx)
 *     .setTitle("Padding")
 *     .setUnits(new String[]{"px", "rem"})
 *     .showUnitInput("padding", v -> ...);
 * }</pre>
 *
 * <p>Adds an outlined radio + custom-input variant ({@link #showRadioWithCustom})
 * used by selector pickers and other choose-from-presets-or-type-your-own flows.
 * The custom outlined input sits on the left, "Done" / "Apply" on the right.
 */
public final class UniversalM3Dialog {

    public interface OnText { void onText(String value); }
    public interface OnUnit { void onUnit(String value); }

    private final Context context;
    private String title = "";
    private String hint;
    private String initial = "";
    private String[] options;
    private String[] units;

    public UniversalM3Dialog(Context context) {
        this.context = context;
    }

    public UniversalM3Dialog setTitle(String title) { this.title = title; return this; }
    public UniversalM3Dialog setHint(String hint) { this.hint = hint; return this; }
    public UniversalM3Dialog setInitialValue(String value) { this.initial = value == null ? "" : value; return this; }
    public UniversalM3Dialog setOptions(String[] options) { this.options = options; return this; }
    public UniversalM3Dialog setUnits(String[] units) { this.units = units; return this; }

    public void showTextInput(OnText cb) {
        UniversalDialog.textInput(context, title, hint == null ? "Value" : hint, initial,
            v -> { if (cb != null) cb.onText(v); });
    }

    public void showChoiceInput(OnText cb) {
        if (options == null || options.length == 0) {
            showTextInput(cb);
            return;
        }
        showRadioWithCustom(Arrays.asList(options), initial, v -> {
            if (cb != null) cb.onText(v);
        });
    }

    public void showColorInput(OnText cb) {
        UniversalDialog.colorPicker(context, title, initial,
            hex -> { if (cb != null) cb.onText(hex); });
    }

    /**
     * Show a numeric value + unit dialog. {@code prop} is unused by the underlying
     * dialog but kept in the signature so call sites keep their semantics.
     */
    public void showUnitInput(String prop, OnUnit cb) {
        float v = parseFloatSafe(initial);
        String u = parseUnit(initial, units);
        UniversalDialog.numberWithUnit(context, title, v, u, (value, unit) -> {
            String composed = trimTrailingZero(value) + (unit == null ? "" : unit);
            if (cb != null) cb.onUnit(composed);
        });
    }

    /**
     * Outlined radio list with a "Custom..." inline input. The whole row is
     * inside a TextInputLayout-style outlined card; the custom outlined input
     * is on the left of its row and the "Done"/"Apply" button sits on the
     * right of the dialog footer.
     */
    public void showRadioWithCustom(List<String> presets, String currentValue, OnText cb) {
        int pad = dp(16);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(8), pad, 0);

        // Outlined surface holding the radio group
        LinearLayout outlined = new LinearLayout(context);
        outlined.setOrientation(LinearLayout.VERTICAL);
        outlined.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(dp(14));
        outline.setStroke(dp(1), 0x55000000);
        outline.setColor(0x00000000);
        outlined.setBackground(outline);

        RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.VERTICAL);
        outlined.addView(group);

        int customId = View.generateViewId();
        boolean[] matched = { false };
        if (presets != null) {
            for (int i = 0; i < presets.size(); i++) {
                String p = presets.get(i);
                RadioButton rb = new RadioButton(context);
                rb.setId(View.generateViewId());
                rb.setText(p);
                rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                rb.setPadding(dp(4), dp(6), dp(4), dp(6));
                group.addView(rb);
                if (p.equals(currentValue)) {
                    rb.setChecked(true);
                    matched[0] = true;
                }
            }
        }

        // Custom row: radio toggle on the LEFT, outlined input fills remaining space.
        LinearLayout customRow = new LinearLayout(context);
        customRow.setOrientation(LinearLayout.HORIZONTAL);

        RadioButton customRb = new RadioButton(context);
        customRb.setId(customId);
        customRb.setText("Custom");
        customRb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        customRb.setPadding(dp(4), dp(6), dp(4), dp(6));
        // The radio sits in its own group anchor; we still link selection by
        // listening for focus on the custom field below.
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        customRow.addView(customRb, rbLp);

        TextInputLayout customTil = new TextInputLayout(context);
        customTil.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        customTil.setHint(hint == null ? "Custom value" : hint);
        customTil.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));

        TextInputEditText customEdit = new TextInputEditText(customTil.getContext());
        customEdit.setSingleLine(true);
        customEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        if (!matched[0] && currentValue != null && !currentValue.isEmpty()) {
            customEdit.setText(currentValue);
            customRb.setChecked(true);
        }
        customTil.addView(customEdit);
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        customLp.leftMargin = dp(8);
        customRow.addView(customTil, customLp);

        outlined.addView(customRow);

        customEdit.setOnFocusChangeListener((v, has) -> {
            if (has) {
                group.clearCheck();
                customRb.setChecked(true);
            }
        });
        customRb.setOnClickListener(v -> {
            group.clearCheck();
            customRb.setChecked(true);
            customEdit.requestFocus();
        });

        root.addView(outlined);

        new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(root)
            // "Done" lives on the right (positive button).
            .setPositiveButton("Done", (d, w) -> {
                int checkedId = group.getCheckedRadioButtonId();
                String result;
                if (checkedId == -1 || customRb.isChecked()) {
                    result = customEdit.getText() == null ? "" : customEdit.getText().toString().trim();
                } else {
                    View selectedView = group.findViewById(checkedId);
                    result = (selectedView instanceof RadioButton)
                        ? ((RadioButton) selectedView).getText().toString()
                        : "";
                }
                if (cb != null) cb.onText(result);
            })
            .setNegativeButton("Cancel", null)
            .setBackgroundInsetStart(dp(24))
            .setBackgroundInsetEnd(dp(24))
            .show();
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private static float parseFloatSafe(String s) {
        if (s == null) return 0f;
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.' || (i == 0 && c == '-')) num.append(c);
            else if (num.length() > 0) break;
        }
        try { return num.length() == 0 ? 0f : Float.parseFloat(num.toString()); }
        catch (Exception e) { return 0f; }
    }

    private static String parseUnit(String value, String[] units) {
        if (value == null) return units != null && units.length > 0 ? units[0] : "px";
        String trimmed = value.trim();
        if (units != null) {
            for (String u : units) {
                if (trimmed.endsWith(u)) return u;
            }
        }
        StringBuilder unit = new StringBuilder();
        for (int i = trimmed.length() - 1; i >= 0; i--) {
            char c = trimmed.charAt(i);
            if (Character.isLetter(c) || c == '%') unit.insert(0, c);
            else break;
        }
        if (unit.length() == 0) return units != null && units.length > 0 ? units[0] : "px";
        return unit.toString();
    }

    private static String trimTrailingZero(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.valueOf(v);
    }

    private int dp(int v) {
        return (int) (v * context.getResources().getDisplayMetrics().density);
    }
}
