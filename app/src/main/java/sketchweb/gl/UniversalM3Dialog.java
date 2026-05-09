package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Reusable Material 3 dialog used across the app for short input flows
 * (text / numeric+unit / dropdown choice / color hex). Builder-style API:
 *
 * <pre>
 * new UniversalM3Dialog(ctx)
 *     .setTitle("Padding")
 *     .setUnits(new String[]{"px","rem","em"})
 *     .showUnitInput("padding", value -> ...);
 * </pre>
 */
public final class UniversalM3Dialog {

    public interface OnValue { void onValue(String value); }

    private final Context context;
    private String title;
    private String hint;
    private String initialValue = "";
    private String[] options;
    private String[] units;

    public UniversalM3Dialog(Context context) {
        this.context = context;
    }

    public UniversalM3Dialog setTitle(String title) {
        this.title = title;
        return this;
    }

    public UniversalM3Dialog setHint(String hint) {
        this.hint = hint;
        return this;
    }

    public UniversalM3Dialog setInitialValue(String value) {
        this.initialValue = value == null ? "" : value;
        return this;
    }

    public UniversalM3Dialog setOptions(String[] options) {
        this.options = options;
        return this;
    }

    public UniversalM3Dialog setUnits(String[] units) {
        this.units = units;
        return this;
    }

    // ---------- Public flows ----------

    public void showTextInput(OnValue onValue) {
        TextInputLayout til = buildTil(hint != null ? hint : "Value");
        TextInputEditText edit = (TextInputEditText) til.getEditText();
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setText(initialValue);
        edit.setSelection(edit.getText().length());

        showFinal(til, () -> onValue.onValue(edit.getText().toString().trim()));
    }

    public void showChoiceInput(OnValue onValue) {
        TextInputLayout til = new TextInputLayout(
                new android.view.ContextThemeWrapper(context,
                        com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox_ExposedDropdownMenu));
        til.setHint(hint != null ? hint : "Select");
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(dp(14), dp(14), dp(14), dp(14));
        til.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialAutoCompleteTextView ac = new MaterialAutoCompleteTextView(til.getContext());
        ac.setInputType(InputType.TYPE_NULL);
        ac.setKeyListener(null);
        ac.setText(initialValue, false);
        if (options != null) {
            ac.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_list_item_1, options));
            ac.setOnClickListener(v -> ac.showDropDown());
            ac.setOnFocusChangeListener((v, has) -> { if (has) ac.showDropDown(); });
        }
        til.addView(ac);

        showFinal(til, () -> onValue.onValue(ac.getText().toString().trim()));
    }

    public void showColorInput(OnValue onValue) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        TextInputLayout til = buildTil("Hex (e.g. #2196F3)");
        TextInputEditText edit = (TextInputEditText) til.getEditText();
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setText(initialValue.isEmpty() ? "#" : initialValue);
        edit.setSelection(edit.getText().length());

        // Live swatch preview
        View swatch = new View(context);
        LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        swatchLp.topMargin = dp(8);
        swatch.setLayoutParams(swatchLp);
        applySwatch(swatch, edit.getText().toString());
        edit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                applySwatch(swatch, s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        // Preset palette
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        LinearLayout palette = new LinearLayout(context);
        palette.setOrientation(LinearLayout.HORIZONTAL);
        palette.setPadding(0, dp(12), 0, 0);
        String[] presets = {
                "#000000","#FFFFFF","#F44336","#E91E63","#9C27B0","#673AB7",
                "#3F51B5","#2196F3","#03A9F4","#00BCD4","#009688","#4CAF50",
                "#8BC34A","#CDDC39","#FFEB3B","#FFC107","#FF9800","#FF5722",
                "#795548","#9E9E9E"
        };
        for (String hex : presets) {
            View dot = new View(context);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(28), dp(28));
            dotLp.setMarginEnd(dp(8));
            dot.setLayoutParams(dotLp);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            try { bg.setColor(Color.parseColor(hex)); } catch (Exception e) { bg.setColor(Color.GRAY); }
            bg.setStroke(dp(1), 0x33000000);
            dot.setBackground(bg);
            dot.setOnClickListener(v -> edit.setText(hex));
            palette.addView(dot);
        }
        hsv.addView(palette);

        root.addView(til);
        root.addView(swatch);
        root.addView(hsv);

        showFinal(root, () -> onValue.onValue(edit.getText().toString().trim()));
    }

    public void showUnitInput(String key, OnValue onValue) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextInputLayout numTil = buildTil(hint != null ? hint : (key != null ? key : "Value"));
        TextInputEditText num = (TextInputEditText) numTil.getEditText();
        num.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);

        String startNum = stripUnit(initialValue);
        String startUnit = sniffUnit(initialValue, units);
        num.setText(startNum);

        TextInputLayout unitTil = new TextInputLayout(
                new android.view.ContextThemeWrapper(context,
                        com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox_ExposedDropdownMenu));
        unitTil.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        unitTil.setHint("unit");
        MaterialAutoCompleteTextView unitAc = new MaterialAutoCompleteTextView(context);
        unitAc.setInputType(InputType.TYPE_NULL);
        unitAc.setKeyListener(null);
        unitAc.setText(startUnit != null ? startUnit : (units != null && units.length > 0 ? units[0] : ""), false);
        if (units != null) {
            unitAc.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, units));
            unitAc.setOnClickListener(v -> unitAc.showDropDown());
            unitAc.setOnFocusChangeListener((v, has) -> { if (has) unitAc.showDropDown(); });
        }
        unitTil.addView(unitAc);

        LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f);
        LinearLayout.LayoutParams unitLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        unitLp.setMarginStart(dp(8));

        row.addView(numTil, numLp);
        row.addView(unitTil, unitLp);

        showFinal(row, () -> {
            String n = num.getText().toString().trim();
            String u = unitAc.getText().toString().trim();
            if (n.isEmpty()) { onValue.onValue(""); return; }
            if ("auto".equalsIgnoreCase(u)) onValue.onValue("auto");
            else onValue.onValue(n + (u == null ? "" : u));
        });
    }

    // ---------- Internals ----------

    private TextInputLayout buildTil(String hint) {
        TextInputLayout til = new TextInputLayout(
                new android.view.ContextThemeWrapper(context,
                        com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox));
        til.setHint(hint);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(dp(14), dp(14), dp(14), dp(14));
        TextInputEditText edit = new TextInputEditText(til.getContext());
        til.addView(edit);
        til.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return til;
    }

    private void showFinal(View content, Runnable onOk) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), dp(8));
        container.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title != null ? title : "Edit")
                .setView(container)
                .setBackgroundInsetStart(dp(20))
                .setBackgroundInsetEnd(dp(20))
                .setPositiveButton("OK", (d, w) -> { if (onOk != null) onOk.run(); })
                .setNegativeButton("Cancel", null)
                .create();

        // Soft input shows automatically on focus
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
    }

    private static String stripUnit(String value) {
        if (value == null) return "";
        if (TextUtils.isEmpty(value)) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c) || c == '.' || c == '-') sb.append(c);
            else break;
        }
        return sb.toString();
    }

    private static String sniffUnit(String value, String[] units) {
        if (value == null || units == null) return null;
        String lower = value.trim().toLowerCase();
        for (String u : units) {
            if (lower.endsWith(u.toLowerCase())) return u;
        }
        return null;
    }

    private void applySwatch(View v, String hex) {
        GradientDrawable bg = new GradientDrawable();
        try { bg.setColor(Color.parseColor(hex)); } catch (Exception e) { bg.setColor(0x22000000); }
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), 0x33000000);
        v.setBackground(bg);
    }

    private int dp(int px) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px,
                context.getResources().getDisplayMetrics());
    }
}
