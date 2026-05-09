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
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;

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
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(8), pad, 0);
        scroll.addView(root);

        // Radio group for presets
        RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.VERTICAL);
        group.setPadding(0, 0, 0, dp(8));

        int customId = View.generateViewId();
        boolean matched = false;
        if (presets != null) {
            for (String p : presets) {
                RadioButton rb = new RadioButton(context);
                rb.setText(p);
                rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                group.addView(rb);
                if (p.equals(currentValue)) {
                    rb.setChecked(true);
                    matched = true;
                }
            }
        }
        root.addView(group);

        // Custom row: Radio + Input
        LinearLayout customRow = new LinearLayout(context);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        customRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        RadioButton customRb = new RadioButton(context);
        customRb.setId(customId);
        customRb.setText("Custom");
        customRb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        customRow.addView(customRb);

        TextInputLayout til = new TextInputLayout(context);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint(hint == null ? "Type..." : hint);
        til.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));

        MaterialAutoCompleteTextView edit = new MaterialAutoCompleteTextView(til.getContext());
        edit.setSingleLine(true);
        if (!matched && currentValue != null && !currentValue.isEmpty()) {
            edit.setText(currentValue);
            customRb.setChecked(true);
        }
        if (presets != null) {
            edit.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, presets));
        }
        til.addView(edit);
        
        LinearLayout.LayoutParams tilLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tilLp.leftMargin = dp(8);
        customRow.addView(til, tilLp);
        root.addView(customRow);

        edit.setOnFocusChangeListener((v, has) -> {
            if (has) {
                group.clearCheck();
                customRb.setChecked(true);
            }
        });
        customRb.setOnClickListener(v -> {
            group.clearCheck();
            customRb.setChecked(true);
            edit.requestFocus();
        });

        new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Done", (d, w) -> {
                int checkedId = group.getCheckedRadioButtonId();
                String result;
                if (customRb.isChecked() || checkedId == -1) {
                    result = edit.getText().toString().trim();
                } else {
                    RadioButton rb = group.findViewById(checkedId);
                    result = rb.getText().toString();
                }
                if (cb != null) cb.onText(result);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    public void showFourValueInput(String prop, OnText cb) {
        int pad = dp(16);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(16), pad, 0);
        scroll.addView(root);

        String[] parts = (initial == null ? "" : initial).split("\\s+");
        String vT = parts.length > 0 ? parts[0] : "0px";
        String vR = parts.length > 1 ? parts[1] : (parts.length > 0 ? parts[0] : "0px");
        String vB = parts.length > 2 ? parts[2] : (parts.length > 0 ? parts[0] : "0px");
        String vL = parts.length > 3 ? parts[3] : (parts.length > 1 ? parts[1] : (parts.length > 0 ? parts[0] : "0px"));

        String[] labels = {"Top", "Right", "Bottom", "Left"};
        String[] vals = {vT, vR, vB, vL};
        MaterialAutoCompleteTextView[] edits = new MaterialAutoCompleteTextView[4];

        for (int i = 0; i < 4; i++) {
            TextInputLayout til = new TextInputLayout(context);
            til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
            til.setHint(labels[i]);
            til.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));
            
            edits[i] = new MaterialAutoCompleteTextView(til.getContext());
            edits[i].setText(vals[i]);
            edits[i].setSingleLine(true);
            if (units != null) {
                edits[i].setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, units));
            }
            til.addView(edits[i]);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            root.addView(til, lp);
        }

        new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Done", (d, w) -> {
                String res = edits[0].getText().toString().trim() + " " +
                             edits[1].getText().toString().trim() + " " +
                             edits[2].getText().toString().trim() + " " +
                             edits[3].getText().toString().trim();
                if (cb != null) cb.onText(res);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    public void showAutocompleteChoice(List<String> suggestions, String initialValue, OnText cb) {
        showRadioWithCustom(suggestions, initialValue, cb);
    }

    /**
     * A smart selector dialog that handles ID (#), Class (.), and Tag (none).
     * Automatically swaps autocomplete suggestions based on the selected prefix.
     */
    public void showSelectorInput(List<String> idSuggestions, 
                                 List<String> classSuggestions, 
                                 List<String> tagSuggestions, 
                                 OnText cb) {
        int pad = dp(16);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(16), pad, 0);
        scroll.addView(root);

        // Prefix selector (horizontal radio pills)
        RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.HORIZONTAL);
        group.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        group.setPadding(0, 0, 0, dp(16));

        String[] prefixes = {"ID (#)", "Class (.)", "Tag"};
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(context);
            rb.setId(100 + i);
            rb.setText(prefixes[i]);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            group.addView(rb);
        }
        root.addView(group);

        TextInputLayout til = new TextInputLayout(context);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint("Name");
        til.setBoxCornerRadii(dp(14), dp(14), dp(14), dp(14));
        til.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        // We'll use a standard search icon if possible, but for now just clear icon
        til.setEndIconDrawable(android.R.drawable.ic_menu_search);

        MaterialAutoCompleteTextView edit = new MaterialAutoCompleteTextView(til.getContext());
        edit.setSingleLine(true);
        til.addView(edit);
        root.addView(til);

        // Initial parsing
        String val = initial == null ? "" : initial.trim();
        if (val.startsWith("#")) {
            ((RadioButton)group.getChildAt(0)).setChecked(true);
            edit.setText(val.substring(1));
        } else if (val.startsWith(".")) {
            ((RadioButton)group.getChildAt(1)).setChecked(true);
            edit.setText(val.substring(1));
        } else {
            ((RadioButton)group.getChildAt(2)).setChecked(true);
            edit.setText(val);
        }

        group.setOnCheckedChangeListener((g, id) -> {
            List<String> active = (id == 100) ? idSuggestions : (id == 101 ? classSuggestions : tagSuggestions);
            if (active != null) {
                edit.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, active));
                if (!edit.getText().toString().isEmpty()) edit.showDropDown();
            } else {
                edit.setAdapter(null);
            }
        });
        
        // Trigger initial adapter
        int initialId = group.getCheckedRadioButtonId();
        List<String> initActive = (initialId == 100) ? idSuggestions : (initialId == 101 ? classSuggestions : tagSuggestions);
        if (initActive != null) edit.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, initActive));

        new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Done", (d, w) -> {
                String name = edit.getText().toString().trim();
                int checked = group.getCheckedRadioButtonId();
                String prefix = (checked == 100) ? "#" : (checked == 101 ? "." : "");
                
                // Clean up name
                name = name.replaceFirst("^[#.]", "");
                if (cb != null) cb.onText(prefix + name);
            })
            .setNegativeButton("Cancel", null)
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
