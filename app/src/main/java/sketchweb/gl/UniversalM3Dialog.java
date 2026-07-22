package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Modern Material 3 Dialog facade. 
 * Every dialog uses a unified layout: Horizontal scrolling chips on top, 
 * followed by a custom outlined text input. No toggles or radio buttons.
 */
public final class UniversalM3Dialog {

    public interface OnText { void onText(String value); }
    public interface OnUnit { void onUnit(String value); }
    public interface OnTextValidate { String validate(String value); }

    private final Context context;
    private String title = "";
    private String hint;
    private String initial = "";
    private String[] options;
    private String[] units;
    private List<String> suggestions;
    private boolean multiline = false;
    private boolean isNumeric = false;

    public UniversalM3Dialog(Context context) {
        this.context = context;
    }

    public UniversalM3Dialog setTitle(String title) { this.title = title; return this; }
    public UniversalM3Dialog setHint(String hint) { this.hint = hint; return this; }
    public UniversalM3Dialog setInitialValue(String value) { this.initial = value == null ? "" : value; return this; }
    public UniversalM3Dialog setOptions(String[] options) { this.options = options; return this; }
    public UniversalM3Dialog setUnits(String[] units) { this.units = units; return this; }
    public UniversalM3Dialog setSuggestions(List<String> suggestions) { this.suggestions = suggestions; return this; }
    public UniversalM3Dialog setMultiline(boolean multiline) { this.multiline = multiline; return this; }
    public UniversalM3Dialog setIsNumeric(boolean isNumeric) { this.isNumeric = isNumeric; return this; }

    public void showTextInput(OnText cb) {
        showCoreDialog(null, initial, hint != null ? hint : "Value", null, cb);
    }

    public void showTextInputWithValidation(OnTextValidate validator, OnText cb) {
        showCoreDialog(null, initial, hint != null ? hint : "Value", validator, cb);
    }

    public void showChoiceInput(OnText cb) {
        List<String> combined = new ArrayList<>();
        if (options != null) combined.addAll(Arrays.asList(options));
        if (units != null) {
            for (String u : units) if (!combined.contains(u)) combined.add(u);
        }
        if (suggestions != null) {
            for (String s : suggestions) if (!combined.contains(s)) combined.add(s);
        }
        showCoreDialog(combined, initial, hint != null ? hint : "Custom value", null, cb);
    }

    public void showColorInput(OnText cb) {
        UniversalDialog.colorPicker(context, title, initial, suggestions,
            hex -> { if (cb != null) cb.onText(hex); });
    }

    public void showUnitInput(String prop, OnUnit cb) {
        showUnitInputCore(val -> {
            if (cb != null) cb.onUnit(val);
        });
    }

    public void showUnitInput(OnText cb) {
        showUnitInputCore(cb);
    }

    private void showUnitInputCore(OnText cb) {
        int pad = dp(24);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(12), pad, dp(20));
        scroll.addView(root);

        List<String> unitList = new ArrayList<>();
        if (units != null && units.length > 0) {
            unitList.addAll(Arrays.asList(units));
        } else if (options != null && options.length > 0) {
            unitList.addAll(Arrays.asList(options));
        } else {
            BlockParamTypeManager pMgr = new BlockParamTypeManager(context);
            List<String> loadedUnits = pMgr.getOptions("unit");
            if (loadedUnits != null && !loadedUnits.isEmpty()) {
                unitList.addAll(loadedUnits);
            } else {
                unitList.addAll(Arrays.asList("px", "%", "em", "rem", "vh", "vw"));
            }
        }

        String initialVal = initial != null ? initial.trim() : "";
        String defaultUnit = "px";
        String numericPart = initialVal;

        for (String u : unitList) {
            if (initialVal.equalsIgnoreCase(u)) {
                defaultUnit = u;
                numericPart = "";
                break;
            } else if (initialVal.toLowerCase().endsWith(u.toLowerCase())) {
                defaultUnit = u;
                numericPart = initialVal.substring(0, initialVal.length() - u.length()).trim();
                break;
            }
        }

        android.widget.TextView labelUnits = new android.widget.TextView(context);
        labelUnits.setText("Select Unit");
        labelUnits.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelUnits.setPadding(0, 0, 0, dp(6));
        root.addView(labelUnits);

        android.widget.HorizontalScrollView unitScroll = createChipScroll();
        final ChipGroup unitGroup = createChipGroup();
        unitGroup.setSingleSelection(true);
        unitGroup.setSelectionRequired(true);

        final TextInputLayout til = createTextInputLayout(hint != null ? hint : "Value");
        til.setSuffixText(defaultUnit);
        boolean originalNumeric = this.isNumeric;
        this.isNumeric = true;
        final MaterialAutoCompleteTextView edit = createEditor(til, numericPart, null);
        this.isNumeric = originalNumeric;
        til.addView(edit);

        final String[] selectedUnit = {defaultUnit};
        for (final String u : unitList) {
            Chip c = createPresetChip(u);
            c.setCheckable(true);
            if (u.equalsIgnoreCase(defaultUnit)) {
                c.setChecked(true);
            }
            c.setOnClickListener(v -> {
                selectedUnit[0] = u;
                til.setSuffixText(u);
            });
            unitGroup.addView(c);
        }

        unitScroll.addView(unitGroup);
        root.addView(unitScroll);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        root.addView(til, lp);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(title.isEmpty() ? "Input Unit Value" : title)
            .setView(scroll)
            .setPositiveButton("Done", (d, w) -> {
                String val = edit.getText().toString().trim();
                String unitStr = selectedUnit[0];
                if (cb != null) {
                    if (val.isEmpty()) {
                        cb.onText(unitStr);
                    } else if (val.endsWith(unitStr)) {
                        cb.onText(val);
                    } else {
                        cb.onText(val + unitStr);
                    }
                }
            })
            .setNegativeButton("Cancel", null);
        showAndFocusKeyboard(builder, edit);
    }

    public void showAutocompleteChoice(List<String> suggestions, String initialValue, OnText cb) {
        showCoreDialog(suggestions, initialValue, hint != null ? hint : "Custom value", null, cb);
    }

    /**
     * Specialised dialog for editing a space-separated list of tokens such as
     * CSS class names. Renders:
     *   1. A horizontal chip row showing every token already on the widget
     *      (tap to remove).
     *   2. A horizontal chip row of suggested tokens harvested from the rest
     *      of the project (tap to add).
     *   3. A free-form text field for typing new tokens; chips and text stay
     *      in real-time sync (typing updates chips; tapping chips updates the
     *      text).
     *
     * <p>Importantly, every call builds a fresh local state — no static field
     * holds the previous input — so closing & re-opening the dialog on a
     * different widget never reuses the prior widget's class list. The dialog
     * is seeded only with the widget value passed in via setInitialValue.</p>
     *
     * @param suggestions live list of class names harvested from the project
     * @param cb          callback fired with the new space-separated class list
     */
    public void showClassChipsInput(List<String> suggestions, OnText cb) {
        int pad = dp(24);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(12), pad, dp(20));
        scroll.addView(root);

        // ------------------------------------------------------------------
        // Local state — kept inside the closure so reopening the dialog NEVER
        // leaks a prior widget's value or a prior session's chip selection.
        // ------------------------------------------------------------------
        final java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>();
        if (initial != null && !initial.trim().isEmpty()) {
            for (String tok : initial.trim().split("\\s+")) {
                String t = tok.replaceFirst("^\\.", "").trim();
                if (!t.isEmpty()) selected.add(t);
            }
        }

        // 1. Header for "Current classes" + chips row
        android.widget.TextView lblCurrent = new android.widget.TextView(context);
        lblCurrent.setText("Current classes (tap to remove)");
        lblCurrent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        lblCurrent.setPadding(0, 0, 0, dp(6));
        root.addView(lblCurrent);

        android.widget.HorizontalScrollView currentScroll = createChipScroll();
        final ChipGroup currentChips = createChipGroup();
        currentChips.setSingleSelection(false);
        currentScroll.addView(currentChips);
        root.addView(currentScroll);

        // 2. Suggestions section
        android.widget.TextView lblSugg = new android.widget.TextView(context);
        lblSugg.setText("Project classes (tap to add)");
        lblSugg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        lblSugg.setPadding(0, dp(4), 0, dp(6));
        root.addView(lblSugg);

        android.widget.HorizontalScrollView suggScroll = createChipScroll();
        final ChipGroup suggChips = createChipGroup();
        suggChips.setSingleSelection(false);
        suggScroll.addView(suggChips);
        root.addView(suggScroll);

        // 3. Free-form text input
        TextInputLayout til = createTextInputLayout(hint != null ? hint : "class1 class2");
        final MaterialAutoCompleteTextView edit = createEditor(til, joinSpace(selected), suggestions);
        til.addView(edit);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        editLp.topMargin = dp(8);
        root.addView(til, editLp);

        // Internal re-entrancy guard so chip->text and text->chip updates
        // don't bounce off each other.
        final boolean[] internal = {false};

        // Render helpers
        final Runnable renderCurrent = new Runnable() {
            @Override public void run() {
                currentChips.removeAllViews();
                for (final String cls : new ArrayList<>(selected)) {
                    Chip chip = new Chip(context);
                    chip.setId(View.generateViewId());
                    chip.setText(cls);
                    chip.setCloseIconVisible(true);
                    chip.setCheckable(false);
                    chip.setOnCloseIconClickListener(v -> {
                        selected.remove(cls);
                        // Realtime sync: update text + suggestion chip state.
                        internal[0] = true;
                        edit.setText(joinSpace(selected));
                        edit.setSelection(edit.getText().length());
                        internal[0] = false;
                        run();
                        syncSuggestionState(suggChips, selected);
                    });
                    currentChips.addView(chip);
                }
            }
        };

        // Build suggestion chips (tap to add)
        if (suggestions != null) {
            for (final String s : suggestions) {
                if (s == null || s.trim().isEmpty()) continue;
                final String cls = s.trim().replaceFirst("^\\.", "");
                Chip c = createPresetChip(cls);
                c.setCheckable(true);
                c.setChecked(selected.contains(cls));
                c.setOnClickListener(v -> {
                    if (selected.contains(cls)) {
                        selected.remove(cls);
                        c.setChecked(false);
                    } else {
                        selected.add(cls);
                        c.setChecked(true);
                    }
                    internal[0] = true;
                    edit.setText(joinSpace(selected));
                    edit.setSelection(edit.getText().length());
                    internal[0] = false;
                    renderCurrent.run();
                });
                suggChips.addView(c);
            }
        }

        // Realtime sync: typing in the text field rebuilds chips.
        edit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (internal[0]) return;
                selected.clear();
                for (String tok : s.toString().trim().split("\\s+")) {
                    String t = tok.replaceFirst("^\\.", "").trim();
                    if (!t.isEmpty()) selected.add(t);
                }
                renderCurrent.run();
                syncSuggestionState(suggChips, selected);
            }
        });

        renderCurrent.run();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Done", (d, w) -> {
                if (cb != null) cb.onText(joinSpace(selected));
            })
            .setNegativeButton("Cancel", null);
        showAndFocusKeyboard(builder, edit);
    }

    private static String joinSpace(java.util.LinkedHashSet<String> set) {
        StringBuilder b = new StringBuilder();
        for (String s : set) {
            if (b.length() > 0) b.append(' ');
            b.append(s);
        }
        return b.toString();
    }

    private static void syncSuggestionState(ChipGroup suggChips, java.util.Set<String> selected) {
        for (int i = 0; i < suggChips.getChildCount(); i++) {
            Chip c = (Chip) suggChips.getChildAt(i);
            boolean checked = selected.contains(c.getText().toString());
            if (c.isChecked() != checked) c.setChecked(checked);
        }
    }

    /**
     * Internal unified builder. 
     * Renders a HorizontalScrollView with ChipGroup, then a TextInputLayout.
     */
    private void showCoreDialog(List<String> presets, String currentValue, String inputHint, OnTextValidate validator, OnText cb) {
        int pad = dp(24);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(12), pad, dp(20));
        scroll.addView(root);

        // 1. Custom Input
        TextInputLayout til = createTextInputLayout(inputHint);
        final MaterialAutoCompleteTextView edit = createEditor(til, currentValue, presets);
        til.addView(edit);

        // 2. Chips
        if (presets != null && !presets.isEmpty()) {
            android.widget.HorizontalScrollView chipScroll = createChipScroll();
            final ChipGroup chips = createChipGroup();
            
            final boolean[] isSelfChange = {false};

            for (String val : presets) {
                Chip chip = createPresetChip(val);
                if (isMatch(currentValue, val)) {
                    isSelfChange[0] = true;
                    chip.setChecked(true);
                    isSelfChange[0] = false;
                }
                chips.addView(chip);
            }
            chipScroll.addView(chips);
            root.addView(chipScroll);

            // Chip -> Input sync
            chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (isSelfChange[0]) return;
                if (checkedIds.isEmpty()) return;
                Chip selected = group.findViewById(checkedIds.get(0));
                if (selected != null) {
                    String t = selected.getText().toString();
                    if (!t.equalsIgnoreCase(edit.getText().toString())) {
                        isSelfChange[0] = true;
                        edit.setText(t);
                        edit.setSelection(t.length());
                        isSelfChange[0] = false;
                    }
                }
            });

            // Input -> Chip sync
            edit.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    if (isSelfChange[0]) return;
                    String text = s.toString();
                    isSelfChange[0] = true;
                    boolean matchFound = false;
                    for (int i = 0; i < chips.getChildCount(); i++) {
                        Chip c = (Chip) chips.getChildAt(i);
                        if (isMatch(text, c.getText().toString())) {
                            if (!c.isChecked()) chips.check(c.getId());
                            matchFound = true;
                            break;
                        }
                    }
                    if (!matchFound) chips.clearCheck();
                    isSelfChange[0] = false;
                }
            });
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        root.addView(til, lp);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Done", (d, w) -> {
                if (cb != null) cb.onText(edit.getText().toString().trim());
            })
            .setNegativeButton("Cancel", null);
        showAndFocusKeyboard(builder, edit);
    }



    private static String lastSelectedIconLib = "Tabler";

    public void showIconPicker(OnText cb) {
        final String[] selectedLibPrefix = {""};
        final String[] selectedLibSuffix = {""};

        int pad = dp(24);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(12), pad, dp(20));
        scroll.addView(root);

        // 1. Search/Type Input
        TextInputLayout til = createTextInputLayout("Type or select icon name");
        final MaterialAutoCompleteTextView edit = createEditor(til, "", null);
        til.addView(edit);
        root.addView(til);

        // 2. Results Container
        final LinearLayout results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);

        // 3. Library Selection (Required)
        android.widget.TextView label = new android.widget.TextView(context);
        label.setText("Step 2: Select Library (Required)");
        label.setPadding(0, dp(16), 0, dp(8));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(label);

        android.widget.HorizontalScrollView libScroll = createChipScroll();
        final ChipGroup libGroup = createChipGroup();
        libGroup.setSingleSelection(true);
        libGroup.setSelectionRequired(true);

        String[][] libs = {
            {"Tabler", "ti ti-", ""},
            {"Phosphor", "ph ph-", ""},
            {"FontAwesome", "fa-solid fa-", ""},
            {"Material", "material-icons mi-", ""},
            {"Bootstrap", "bi bi-", ""},
            {"Remix", "ri-", "-line"}
        };

        for (final String[] lib : libs) {
            Chip c = createPresetChip(lib[0]);
            c.setCheckable(true);
            if (lib[0].equals(lastSelectedIconLib)) {
                c.setChecked(true);
                selectedLibPrefix[0] = lib[1];
                selectedLibSuffix[0] = lib[2];
            }
            c.setOnClickListener(v -> {
                lastSelectedIconLib = lib[0];
                selectedLibPrefix[0] = lib[1];
                selectedLibSuffix[0] = lib[2];
            });
            libGroup.addView(c);
        }
        libScroll.addView(libGroup);
        root.addView(libScroll);

        final com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(title.isEmpty() ? "Pick an Icon" : title)
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Select", (d, w) -> {
                String name = edit.getText().toString().trim();
                if (cb != null && !name.isEmpty() && !selectedLibPrefix[0].isEmpty()) {
                    cb.onText(selectedLibPrefix[0] + name + selectedLibSuffix[0]);
                }
            });

        final android.app.Dialog dialog = builder.create();

        edit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String q = s.toString().trim();
                results.removeAllViews();
                if (q.length() >= 1) {
                    List<String> matches = IconSearchProvider.search(q);
                    for (int i = 0; i < Math.min(matches.size(), 10); i++) {
                        final String name = matches.get(i);
                        android.widget.TextView tv = new android.widget.TextView(context);
                        tv.setText(name);
                        tv.setPadding(dp(16), dp(8), dp(16), dp(8));
                        tv.setClickable(true);
                        tv.setFocusable(true);
                        
                        android.util.TypedValue outValue = new android.util.TypedValue();
                        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                        tv.setBackgroundResource(outValue.resourceId);

                        tv.setOnClickListener(v -> {
                            edit.setText(name);
                            edit.setSelection(name.length());
                            results.removeAllViews();
                        });
                        results.addView(tv);
                    }
                }
            }
        });

        dialog.show();
    }

    public static class ArrayItemRow {
        public String type = "string";
        public View rowView;
        public EditText etValue;
        public Spinner spBool;
        public View spBoolView;
    }

    public interface ArrayRowAdder {
        void add(ArrayItemRow row);
    }

    public void showArrayListBuilder(String initialValue, final OnText cb) {
        View root = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_setup_list, null);
        final LinearLayout itemsContainer = root.findViewById(R.id.items_container);
        Button btnAddStr = root.findViewById(R.id.btn_add_string);
        Button btnAddNum = root.findViewById(R.id.btn_add_number);
        Button btnAddBool = root.findViewById(R.id.btn_add_boolean);
        Button btnAddObj = root.findViewById(R.id.btn_add_object);

        final ArrayList<ArrayItemRow> rows = new ArrayList<>();

        final Runnable refreshIndices = () -> {
            for (int i = 0; i < rows.size(); i++) {
                View itemRoot = rows.get(i).rowView;
                if (itemRoot != null) {
                    TextView tvIdx = itemRoot.findViewById(1001);
                    if (tvIdx != null) tvIdx.setText("[" + i + "]");
                }
            }
        };

        final ArrayRowAdder adder = new ArrayRowAdder() {
            @Override
            public void add(final ArrayItemRow row) {
                final LinearLayout rowLayout = new LinearLayout(context);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setGravity(Gravity.CENTER_VERTICAL);
                rowLayout.setPadding(dp(4), dp(4), dp(4), dp(4));

                TextView tvIdx = new TextView(context);
                tvIdx.setId(1001);
                tvIdx.setText("[" + rows.size() + "]");
                tvIdx.setTextSize(12);
                tvIdx.setTypeface(null, android.graphics.Typeface.BOLD);
                tvIdx.setPadding(0, 0, dp(6), 0);
                rowLayout.addView(tvIdx);

                if ("string".equals(row.type)) {
                    com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
                    til.setHint("String (e.g. Saqib)");
                    til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                    til.setLayoutParams(lp);

                    com.google.android.material.textfield.TextInputEditText etInput = new com.google.android.material.textfield.TextInputEditText(til.getContext());
                    etInput.setTextSize(13);
                    if (row.etValue != null && row.etValue.getText() != null) etInput.setText(row.etValue.getText());
                    til.addView(etInput);
                    rowLayout.addView(til);
                    row.etValue = etInput;
                } else if ("number".equals(row.type)) {
                    com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
                    til.setHint("Number (e.g. 20)");
                    til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                    til.setLayoutParams(lp);

                    com.google.android.material.textfield.TextInputEditText etInput = new com.google.android.material.textfield.TextInputEditText(til.getContext());
                    etInput.setTextSize(13);
                    etInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
                    if (row.etValue != null && row.etValue.getText() != null) etInput.setText(row.etValue.getText());
                    til.addView(etInput);
                    rowLayout.addView(til);
                    row.etValue = etInput;
                } else if ("boolean".equals(row.type)) {
                    com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
                    til.setHint("Boolean");
                    til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                    til.setLayoutParams(lp);

                    com.google.android.material.textfield.MaterialAutoCompleteTextView actv = new com.google.android.material.textfield.MaterialAutoCompleteTextView(til.getContext());
                    actv.setTextSize(13);
                    actv.setSimpleItems(new String[]{"true", "false"});
                    actv.setText("true", false);
                    til.addView(actv);
                    rowLayout.addView(til);
                    row.spBoolView = actv;
                } else if ("object".equals(row.type)) {
                    final TextView tvObj = new TextView(context);
                    tvObj.setTextSize(13);
                    tvObj.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                    tvObj.setPadding(dp(10), dp(10), dp(10), dp(10));
                    int primaryColor = com.google.android.material.color.MaterialColors.getColor(context, android.R.attr.colorPrimary, 0xFF00897B);
                    tvObj.setBackgroundColor(primaryColor & 0x1FFFFFFF);
                    tvObj.setTextColor(primaryColor);
                    tvObj.setTypeface(null, android.graphics.Typeface.BOLD);

                    String curVal = (row.etValue != null && row.etValue.getText() != null && !row.etValue.getText().toString().isEmpty()) 
                                    ? row.etValue.getText().toString() : "{ name: \"Ali\", age: 20 }";
                    tvObj.setText(curVal);
                    if (row.etValue == null) {
                        row.etValue = new EditText(context);
                    }
                    row.etValue.setText(curVal);

                    ImageView btnPencil = new ImageView(context);
                    btnPencil.setImageResource(R.drawable.pencil);
                    btnPencil.setImageTintList(com.google.android.material.color.MaterialColors.getColorStateList(context, android.R.attr.colorPrimary, android.content.res.ColorStateList.valueOf(0xFF00897B)));
                    btnPencil.setPadding(dp(6), dp(6), dp(6), dp(6));
                    btnPencil.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String initObj = row.etValue.getText() != null ? row.etValue.getText().toString() : "{}";
                            showObjectPropertiesBuilder(initObj, new OnText() {
                                @Override
                                public void onText(String value) {
                                    row.etValue.setText(value);
                                    tvObj.setText(value);
                                }
                            });
                        }
                    });

                    rowLayout.addView(tvObj);
                    rowLayout.addView(btnPencil);
                }

                ImageView btnDel = new ImageView(context);
                btnDel.setImageResource(R.drawable.trash);
                btnDel.setImageTintList(com.google.android.material.color.MaterialColors.getColorStateList(context, android.R.attr.colorError, android.content.res.ColorStateList.valueOf(0xFFB00020)));
                btnDel.setPadding(dp(6), dp(6), dp(6), dp(6));
                btnDel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        itemsContainer.removeView(rowLayout);
                        rows.remove(row);
                        refreshIndices.run();
                    }
                });
                rowLayout.addView(btnDel);

                row.rowView = rowLayout;
                rows.add(row);
                itemsContainer.addView(rowLayout);
            }
        };

        // Parse initial value if present
        if (initialValue != null && !initialValue.trim().isEmpty()) {
            String cleanInit = initialValue.trim();
            if (cleanInit.startsWith("[") && cleanInit.endsWith("]")) {
                cleanInit = cleanInit.substring(1, cleanInit.length() - 1).trim();
            }
            if (!cleanInit.isEmpty()) {
                String[] parts = cleanInit.split(",");
                for (String part : parts) {
                    String p = part.trim();
                    ArrayItemRow row = new ArrayItemRow();
                    if (p.startsWith("{") || p.endsWith("}")) {
                        row.type = "object";
                        row.etValue = new EditText(context);
                        row.etValue.setText(p);
                    } else if (p.startsWith("\"") || p.startsWith("'")) {
                        row.type = "string";
                        row.etValue = new EditText(context);
                        row.etValue.setText(p.replaceAll("^[\"']|[\"']$", ""));
                    } else if ("true".equals(p) || "false".equals(p)) {
                        row.type = "boolean";
                    } else {
                        row.type = "number";
                        row.etValue = new EditText(context);
                        row.etValue.setText(p);
                    }
                    adder.add(row);
                }
            }
        }

        if (rows.isEmpty()) {
            ArrayItemRow defaultRow = new ArrayItemRow();
            defaultRow.type = "string";
            adder.add(defaultRow);
        }

        btnAddStr.setOnClickListener(v -> { ArrayItemRow r = new ArrayItemRow(); r.type = "string"; adder.add(r); });
        btnAddNum.setOnClickListener(v -> { ArrayItemRow r = new ArrayItemRow(); r.type = "number"; adder.add(r); });
        btnAddBool.setOnClickListener(v -> { ArrayItemRow r = new ArrayItemRow(); r.type = "boolean"; adder.add(r); });
        btnAddObj.setOnClickListener(v -> { ArrayItemRow r = new ArrayItemRow(); r.type = "object"; adder.add(r); });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle("Setup List / Array")
            .setView(root)
            .setPositiveButton("Save", (dialogInterface, which) -> {
                StringBuilder sb = new StringBuilder("[ ");
                for (int i = 0; i < rows.size(); i++) {
                    ArrayItemRow r = rows.get(i);
                    if (i > 0) sb.append(", ");
                    if ("string".equals(r.type)) {
                        String txt = r.etValue != null ? r.etValue.getText().toString() : "";
                        sb.append("\"").append(txt.replace("\"", "\\\"")).append("\"");
                    } else if ("number".equals(r.type)) {
                        String txt = r.etValue != null ? r.etValue.getText().toString() : "0";
                        sb.append(txt.isEmpty() ? "0" : txt);
                    } else if ("boolean".equals(r.type)) {
                        String txt = "true";
                        if (r.spBoolView instanceof android.widget.AutoCompleteTextView) {
                            txt = ((android.widget.AutoCompleteTextView) r.spBoolView).getText().toString();
                        } else if (r.spBool != null) {
                            txt = r.spBool.getSelectedItem().toString();
                        }
                        sb.append(txt.isEmpty() ? "true" : txt);
                    } else if ("object".equals(r.type)) {
                        String txt = r.etValue != null ? r.etValue.getText().toString() : "{}";
                        sb.append(txt.isEmpty() ? "{}" : txt);
                    }
                }
                sb.append(" ]");
                if (cb != null) cb.onText(sb.toString());
            })
            .setNegativeButton("Cancel", null);

        builder.show();
    }

    public static class ObjPropRow {
        public String key = "";
        public EditText etKey;
        public EditText etVal;
        public View rowView;
    }

    public interface ObjPropRowAdder {
        void add(ObjPropRow row);
    }

    public void showObjectPropertiesBuilder(String initialObjJson, final OnText cb) {
        View root = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_setup_object, null);
        final LinearLayout propsContainer = root.findViewById(R.id.props_container);
        Button btnAddProp = root.findViewById(R.id.btn_add_property);

        final ArrayList<ObjPropRow> rows = new ArrayList<>();

        final ObjPropRowAdder adder = new ObjPropRowAdder() {
            @Override
            public void add(final ObjPropRow row) {
                final LinearLayout rowLayout = new LinearLayout(context);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setGravity(Gravity.CENTER_VERTICAL);
                rowLayout.setPadding(dp(4), dp(4), dp(4), dp(4));

                com.google.android.material.textfield.TextInputLayout tilKey = new com.google.android.material.textfield.TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
                tilKey.setHint("Key");
                tilKey.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
                LinearLayout.LayoutParams lpKey = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                lpKey.setMargins(dp(2), dp(2), dp(2), dp(2));
                tilKey.setLayoutParams(lpKey);

                com.google.android.material.textfield.TextInputEditText etKey = new com.google.android.material.textfield.TextInputEditText(tilKey.getContext());
                etKey.setTextSize(13);
                if (row.key != null && !row.key.isEmpty()) etKey.setText(row.key);
                tilKey.addView(etKey);
                rowLayout.addView(tilKey);
                row.etKey = etKey;

                TextView tvColon = new TextView(context);
                tvColon.setText(" : ");
                tvColon.setTypeface(null, android.graphics.Typeface.BOLD);
                tvColon.setPadding(dp(2), 0, dp(2), 0);
                rowLayout.addView(tvColon);

                com.google.android.material.textfield.TextInputLayout tilVal = new com.google.android.material.textfield.TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
                tilVal.setHint("Value");
                tilVal.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
                LinearLayout.LayoutParams lpVal = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                lpVal.setMargins(dp(2), dp(2), dp(2), dp(2));
                tilVal.setLayoutParams(lpVal);

                com.google.android.material.textfield.TextInputEditText etVal = new com.google.android.material.textfield.TextInputEditText(tilVal.getContext());
                etVal.setTextSize(13);
                if (row.etVal != null && row.etVal.getText() != null) etVal.setText(row.etVal.getText());
                tilVal.addView(etVal);
                rowLayout.addView(tilVal);
                row.etVal = etVal;

                ImageView btnDel = new ImageView(context);
                btnDel.setImageResource(R.drawable.trash);
                btnDel.setImageTintList(com.google.android.material.color.MaterialColors.getColorStateList(context, android.R.attr.colorError, android.content.res.ColorStateList.valueOf(0xFFB00020)));
                btnDel.setPadding(dp(6), dp(6), dp(6), dp(6));
                btnDel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        propsContainer.removeView(rowLayout);
                        rows.remove(row);
                    }
                });
                rowLayout.addView(btnDel);

                row.rowView = rowLayout;
                rows.add(row);
                propsContainer.addView(rowLayout);
            }
        };

        if (initialObjJson != null && !initialObjJson.trim().isEmpty()) {
            String clean = initialObjJson.trim();
            if (clean.startsWith("{") && clean.endsWith("}")) {
                clean = clean.substring(1, clean.length() - 1).trim();
            }
            if (!clean.isEmpty()) {
                String[] parts = clean.split(",");
                for (String part : parts) {
                    String[] kv = part.split(":", 2);
                    if (kv.length == 2) {
                        ObjPropRow r = new ObjPropRow();
                        r.key = kv[0].trim().replaceAll("^[\"']|[\"']$", "");
                        r.etVal = new EditText(context);
                        r.etVal.setText(kv[1].trim().replaceAll("^[\"']|[\"']$", ""));
                        adder.add(r);
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            ObjPropRow defaultRow = new ObjPropRow();
            defaultRow.key = "name";
            defaultRow.etVal = new EditText(context);
            defaultRow.etVal.setText("Ali");
            adder.add(defaultRow);
        }

        btnAddProp.setOnClickListener(v -> {
            ObjPropRow r = new ObjPropRow();
            r.etVal = new EditText(context);
            adder.add(r);
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle("Edit Object Element")
            .setView(root)
            .setPositiveButton("Save Object", (dialogInterface, which) -> {
                StringBuilder sb = new StringBuilder("{ ");
                for (int i = 0; i < rows.size(); i++) {
                    ObjPropRow r = rows.get(i);
                    String k = r.etKey != null ? r.etKey.getText().toString().trim() : "";
                    String v = r.etVal != null ? r.etVal.getText().toString().trim() : "";
                    if (k.isEmpty()) continue;
                    if (i > 0) sb.append(", ");
                    sb.append(k).append(": ");
                    if (v.matches("^-?\\d+(\\.\\d+)?$") || "true".equals(v) || "false".equals(v) || v.startsWith("{") || v.startsWith("[")) {
                        sb.append(v.isEmpty() ? "\"\"" : v);
                    } else {
                        sb.append("\"").append(v.replace("\"", "\\\"")).append("\"");
                    }
                }
                sb.append(" }");
                if (cb != null) cb.onText(sb.toString());
            })
            .setNegativeButton("Cancel", null);

        builder.show();
    }

    public void showSelectorInput(List<String> idSuggestions, 
                                 List<String> classSuggestions, 
                                 List<String> tagSuggestions, 
                                 OnText cb) {
        int pad = dp(24);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(12), pad, dp(20));
        scroll.addView(root);

        // 1. Mode selection chips
        ChipGroup modeGroup = createChipGroup();
        modeGroup.setPadding(0, 0, 0, dp(4));
        final int idId = View.generateViewId(), classId = View.generateViewId(), tagId = View.generateViewId();
        modeGroup.addView(createModeChip("ID (#)", idId));
        modeGroup.addView(createModeChip("Class (.)", classId));
        modeGroup.addView(createModeChip("Tag", tagId));
        root.addView(modeGroup);

        // 2. Suggestion chips (dynamic)
        android.widget.HorizontalScrollView suggestionScroll = createChipScroll();
        final ChipGroup suggestionChips = createChipGroup();
        suggestionChips.setPadding(0, 0, 0, dp(4));
        suggestionScroll.addView(suggestionChips);
        root.addView(suggestionScroll);

        // 3. Input
        TextInputLayout til = createTextInputLayout("Name");
        final MaterialAutoCompleteTextView edit = createEditor(til, "", null);
        til.addView(edit);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        root.addView(til, lp);

        final boolean[] isInternal = {false};

        // Logic to update suggestions and match state
        Runnable updateSuggestions = () -> {
            suggestionChips.removeAllViews();
            int selId = modeGroup.getCheckedChipId();
            List<String> active = (selId == idId) ? idSuggestions : (selId == classId ? classSuggestions : tagSuggestions);
            String current = edit.getText().toString();
            if (active != null) {
                isInternal[0] = true;
                for (String s : active) {
                    Chip chip = createPresetChip(s);
                    if (s.equalsIgnoreCase(current)) chip.setChecked(true);
                    suggestionChips.addView(chip);
                }
                edit.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, active));
                isInternal[0] = false;
            }
        };

        modeGroup.setOnCheckedStateChangeListener((g, checkedIds) -> {
            if (!checkedIds.isEmpty()) updateSuggestions.run();
        });

        suggestionChips.setOnCheckedStateChangeListener((g, checkedIds) -> {
            if (isInternal[0] || checkedIds.isEmpty()) return;
            Chip selected = g.findViewById(checkedIds.get(0));
            if (selected != null) {
                String t = selected.getText().toString();
                isInternal[0] = true;
                edit.setText(t);
                edit.setSelection(t.length());
                isInternal[0] = false;
            }
        });

        edit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (isInternal[0]) return;
                String text = s.toString();
                isInternal[0] = true;
                boolean found = false;
                for (int i = 0; i < suggestionChips.getChildCount(); i++) {
                    Chip c = (Chip) suggestionChips.getChildAt(i);
                    if (c.getText().toString().equalsIgnoreCase(text)) {
                        suggestionChips.check(c.getId());
                        found = true;
                        break;
                    }
                }
                if (!found) suggestionChips.clearCheck();
                isInternal[0] = false;
            }
        });

        // Initial state
        String val = initial == null ? "" : initial.trim();
        if (val.startsWith("#")) { modeGroup.check(idId); edit.setText(val.substring(1)); }
        else if (val.startsWith(".")) { modeGroup.check(classId); edit.setText(val.substring(1)); }
        else { modeGroup.check(tagId); edit.setText(val); }
        updateSuggestions.run();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Done", (d, w) -> {
                String raw = edit.getText().toString().trim();
                int checked = modeGroup.getCheckedChipId();
                String prefix = (checked == idId) ? "#" : (checked == classId ? "." : "");
                if (cb != null) cb.onText(prefix + raw.replaceFirst("^[#.]", ""));
            })
            .setNegativeButton("Cancel", null);
        showAndFocusKeyboard(builder, edit);
    }

    public void showFourValueInput(String prop, OnText cb) {
        int pad = dp(24);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(12), pad, dp(20));
        scroll.addView(root);

        final MaterialAutoCompleteTextView[] edits = new MaterialAutoCompleteTextView[4];

        // 1. Unit Chips
        if (units != null && units.length > 0) {
            android.widget.HorizontalScrollView cs = createChipScroll();
            ChipGroup cg = createChipGroup();
            for (String u : units) {
                Chip chip = createPresetChip(u);
                chip.setOnClickListener(v -> {
                    for (MaterialAutoCompleteTextView e : edits) {
                        String t = e.getText().toString().replaceAll("[^0-9.-]", "");
                        if (t.isEmpty()) t = "0";
                        e.setText(t + u);
                    }
                });
                cg.addView(chip);
            }
            cs.addView(cg);
            root.addView(cs);
        }

        String[] parts = (initial == null ? "" : initial).split("\\s+");
        String[] labels = {"Top", "Right", "Bottom", "Left"};
        for (int k = 0; k < 4; k++) {
            TextInputLayout til = createTextInputLayout(labels[k]);
            String val = parts.length > k ? parts[k] : (parts.length > 0 ? parts[0] : "0px");
            edits[k] = createEditor(til, val, units != null ? Arrays.asList(units) : null);
            til.addView(edits[k]);
            root.addView(til, new LinearLayout.LayoutParams(-1, -2) {{ bottomMargin = dp(12); }});
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(title).setView(scroll)
            .setPositiveButton("Done", (d, w) -> {
                if (cb != null) cb.onText(edits[0].getText().toString().trim() + " " + edits[1].getText().toString().trim() + " " + edits[2].getText().toString().trim() + " " + edits[3].getText().toString().trim());
            }).setNegativeButton("Cancel", null);
        showAndFocusKeyboard(builder, edits[0]);
    }

    private void showAndFocusKeyboard(MaterialAlertDialogBuilder builder, final View inputView) {
        final androidx.appcompat.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
        if (inputView != null) {
            inputView.requestFocus();
            inputView.postDelayed(() -> {
                try {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(inputView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                } catch (Exception ignored) {}
            }, 150);
        }
    }

    // --- Helpers ---

    private android.widget.HorizontalScrollView createChipScroll() {
        android.widget.HorizontalScrollView s = new android.widget.HorizontalScrollView(context);
        s.setHorizontalScrollBarEnabled(false);
        s.setClipToPadding(false);
        return s;
    }

    private ChipGroup createChipGroup() {
        ChipGroup g = new ChipGroup(context);
        g.setSingleLine(true);
        g.setSingleSelection(true);
        g.setChipSpacingHorizontal(dp(8));
        g.setPadding(0, 0, 0, dp(4));
        return g;
    }

    private Chip createPresetChip(String text) {
        Chip c = new Chip(context);
        c.setId(View.generateViewId());
        c.setText(text);
        c.setCheckable(true);
        c.setClickable(true);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        return c;
    }

    private Chip createModeChip(String text, int id) {
        Chip c = createPresetChip(text);
        c.setId(id);
        return c;
    }

    private TextInputLayout createTextInputLayout(String hint) {
        TextInputLayout til = new TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(hint);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(dp(14), dp(14), dp(14), dp(14));
        til.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        return til;
    }

    private MaterialAutoCompleteTextView createEditor(TextInputLayout til, String value, List<String> presets) {
        MaterialAutoCompleteTextView edit = new MaterialAutoCompleteTextView(til.getContext());
        if (multiline) {
            edit.setSingleLine(false);
            edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            edit.setMinLines(8);
            edit.setMaxLines(15);
            edit.setGravity(Gravity.TOP | Gravity.START);
        } else {
            edit.setSingleLine(true);
            if (isNumeric) {
                edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
        }
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        edit.setText(value);
        edit.setThreshold(1);
        edit.setMinimumHeight(dp(multiline ? 160 : 56));
        int hp = dp(16), vp = dp(12);
        edit.setPadding(hp, vp, hp, vp);
        if (presets != null) edit.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, presets));
        return edit;
    }

    private boolean isMatch(String currentValue, String preset) {
        if (currentValue == null || preset == null) return false;
        String cv = currentValue.trim().toLowerCase();
        String pv = preset.trim().toLowerCase();
        if (pv.equals(cv)) return true;

        // CSS specific synonyms
        if (pv.equals("bold") && cv.equals("700")) return true;
        if (pv.equals("700") && cv.equals("bold")) return true;
        if (pv.equals("normal") && cv.equals("400")) return true;
        if (pv.equals("400") && cv.equals("normal")) return true;

        if (!cv.isEmpty() && cv.endsWith(pv)) {
            String prefix = cv.substring(0, cv.length() - pv.length());
            return prefix.isEmpty() || prefix.matches("-?\\d*\\.?\\d*");
        }
        return false;
    }

    private int dp(int v) { return (int) (v * context.getResources().getDisplayMetrics().density); }

    public void showVariableSelector(String filename, String menuName, final OnText cb) {
        showVariableSelector(filename, menuName, false, null, cb);
    }

    public void showVariableSelector(String filename, String menuName, final boolean disableConst, final OnText cb) {
        showVariableSelector(filename, menuName, disableConst, null, cb);
    }

    public void showVariableSelector(String filename, String menuName, final boolean disableConst, final java.util.Set<String> usedConstVars, final OnText cb) {
        int pad = dp(24);
        LinearLayout dialogContainer = new LinearLayout(context);
        dialogContainer.setOrientation(LinearLayout.VERTICAL);
        dialogContainer.setPadding(pad, dp(12), pad, dp(20));

        // Chip group for filtering types: boolean, number, string
        final com.google.android.material.chip.ChipGroup cgFilter = new com.google.android.material.chip.ChipGroup(context);
        cgFilter.setSingleSelection(true);
        cgFilter.setSelectionRequired(true);
        cgFilter.setChipSpacingHorizontal(dp(8));
        cgFilter.setPadding(0, 0, 0, dp(12));

        final com.google.android.material.chip.Chip chipBool = new com.google.android.material.chip.Chip(context);
        chipBool.setText("boolean");
        chipBool.setCheckable(true);
        chipBool.setId(View.generateViewId());
        cgFilter.addView(chipBool);

        final com.google.android.material.chip.Chip chipNum = new com.google.android.material.chip.Chip(context);
        chipNum.setText("number");
        chipNum.setCheckable(true);
        chipNum.setId(View.generateViewId());
        cgFilter.addView(chipNum);

        final com.google.android.material.chip.Chip chipStr = new com.google.android.material.chip.Chip(context);
        chipStr.setText("string");
        chipStr.setCheckable(true);
        chipStr.setId(View.generateViewId());
        cgFilter.addView(chipStr);

        dialogContainer.addView(cgFilter);

        ScrollView scroll = new ScrollView(context);
        dialogContainer.addView(scroll);

        // Radio group for variables
        final android.widget.RadioGroup rg = new android.widget.RadioGroup(context);
        rg.setOrientation(android.widget.RadioGroup.VERTICAL);
        scroll.addView(rg);

        // Fetch variables
        final ArrayList<Pair<Integer, String>> allVars = DesignDataManager.getVariables(filename);

        // Filter helper runnable
        final Runnable filterRunnable = new Runnable() {
            @Override
            public void run() {
                rg.removeAllViews();
                int checkedChipId = cgFilter.getCheckedChipId();
                
                for (Pair<Integer, String> p : allVars) {
                    int t = p.first;
                    boolean isConst = (t >= 4);
                    int baseType = isConst ? (t - 4) : t;

                    // Apply chip filtering
                    if (checkedChipId == chipBool.getId() && baseType != 0) continue;
                    if (checkedChipId == chipNum.getId() && baseType != 1) continue;
                    if (checkedChipId == chipStr.getId() && baseType != 2) continue;

                    String typeStr = "number";
                    if (baseType == 0) typeStr = "boolean";
                    else if (baseType == 2) typeStr = "string";

                    String label = p.second + " (" + (isConst ? "const " : "let ") + typeStr + ")";

                    com.google.android.material.radiobutton.MaterialRadioButton rb = new com.google.android.material.radiobutton.MaterialRadioButton(context);
                    rb.setText(label);
                    rb.setTag(p.second);
                    rb.setTextSize(16);
                    rb.setPadding(dp(8), dp(10), dp(8), dp(10));
                    if (disableConst && isConst && usedConstVars != null && usedConstVars.contains(p.second)) {
                        rb.setEnabled(false);
                        rb.setAlpha(0.45f);
                    }
                    rg.addView(rb);
                }

                if (rg.getChildCount() == 0) {
                    TextView tvEmpty = new TextView(context);
                    tvEmpty.setText("No matching variables found.");
                    tvEmpty.setPadding(dp(8), dp(16), dp(8), dp(16));
                    tvEmpty.setGravity(Gravity.CENTER);
                    rg.addView(tvEmpty);
                }
            }
        };

        // Pre-select filter chip based on menuName
        if ("varBool".equals(menuName)) {
            chipBool.setChecked(true);
        } else if ("varInt".equals(menuName)) {
            chipNum.setChecked(true);
        } else if ("varStr".equals(menuName)) {
            chipStr.setChecked(true);
        }

        // Disable unselected chips if variable selector is pre-filtered by block arg type
        if ("varBool".equals(menuName) || "varInt".equals(menuName) || "varStr".equals(menuName)) {
            chipBool.setEnabled("varBool".equals(menuName));
            chipNum.setEnabled("varInt".equals(menuName));
            chipStr.setEnabled("varStr".equals(menuName));
        }

        cgFilter.setOnCheckedStateChangeListener(new com.google.android.material.chip.ChipGroup.OnCheckedStateChangeListener() {
            @Override
            public void onCheckedChanged(com.google.android.material.chip.ChipGroup group, List<Integer> checkedIds) {
                filterRunnable.run();
            }
        });

        // Run initial filter
        filterRunnable.run();

        // Pre-select current value if matches
        String currentVal = initial != null ? initial.trim() : "";
        for (int i = 0; i < rg.getChildCount(); i++) {
            View child = rg.getChildAt(i);
            if (child instanceof com.google.android.material.radiobutton.MaterialRadioButton) {
                com.google.android.material.radiobutton.MaterialRadioButton rb = (com.google.android.material.radiobutton.MaterialRadioButton) child;
                if (rb.getTag() != null && rb.getTag().toString().equals(currentVal)) {
                    rb.setChecked(true);
                    break;
                }
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle("Select Variable")
            .setView(dialogContainer)
            .setPositiveButton("Select", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialogInterface, int which) {
                    int checkedId = rg.getCheckedRadioButtonId();
                    View selectedRb = rg.findViewById(checkedId);
                    if (selectedRb instanceof com.google.android.material.radiobutton.MaterialRadioButton && selectedRb.isEnabled()) {
                        String selectedVar = ((com.google.android.material.radiobutton.MaterialRadioButton) selectedRb).getTag().toString();
                        if (cb != null) cb.onText(selectedVar);
                    }
                }
            })
            .setNegativeButton("Cancel", null);

        builder.create().show();
    }

    public void showListSelector(String filename, String initial, final OnText cb) {
        int pad = dp(24);
        LinearLayout dialogContainer = new LinearLayout(context);
        dialogContainer.setOrientation(LinearLayout.VERTICAL);
        dialogContainer.setPadding(pad, dp(12), pad, dp(20));

        ScrollView scroll = new ScrollView(context);
        dialogContainer.addView(scroll);

        final android.widget.RadioGroup rg = new android.widget.RadioGroup(context);
        rg.setOrientation(android.widget.RadioGroup.VERTICAL);
        scroll.addView(rg);

        final ArrayList<Pair<Integer, String>> allLists = DesignDataManager.getLists(filename);
        String currentVal = initial != null ? initial.trim() : "";

        if (allLists != null) {
            for (Pair<Integer, String> p : allLists) {
                com.google.android.material.radiobutton.MaterialRadioButton rb = new com.google.android.material.radiobutton.MaterialRadioButton(context);
                rb.setText(p.second);
                rb.setTag(p.second);
                rb.setTextSize(16);
                rb.setPadding(dp(8), dp(10), dp(8), dp(10));
                if (p.second.equals(currentVal)) {
                    rb.setChecked(true);
                }
                rg.addView(rb);
            }
        }

        if (rg.getChildCount() == 0) {
            TextView tvEmpty = new TextView(context);
            tvEmpty.setText("No lists created yet.");
            tvEmpty.setPadding(dp(8), dp(16), dp(8), dp(16));
            tvEmpty.setGravity(Gravity.CENTER);
            rg.addView(tvEmpty);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle("Select List")
            .setView(dialogContainer)
            .setPositiveButton("Select", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialogInterface, int which) {
                    int checkedId = rg.getCheckedRadioButtonId();
                    View selectedRb = rg.findViewById(checkedId);
                    if (selectedRb instanceof com.google.android.material.radiobutton.MaterialRadioButton) {
                        String selectedVar = ((com.google.android.material.radiobutton.MaterialRadioButton) selectedRb).getTag().toString();
                        if (cb != null) cb.onText(selectedVar);
                    }
                }
            })
            .setNegativeButton("Cancel", null);

        builder.show();
    }
}
