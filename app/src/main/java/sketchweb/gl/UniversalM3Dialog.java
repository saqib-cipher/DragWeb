package sketchweb.gl;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;

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

    private final Context context;
    private String title = "";
    private String hint;
    private String initial = "";
    private String[] options;
    private String[] units;
    private List<String> suggestions;

    public UniversalM3Dialog(Context context) {
        this.context = context;
    }

    public UniversalM3Dialog setTitle(String title) { this.title = title; return this; }
    public UniversalM3Dialog setHint(String hint) { this.hint = hint; return this; }
    public UniversalM3Dialog setInitialValue(String value) { this.initial = value == null ? "" : value; return this; }
    public UniversalM3Dialog setOptions(String[] options) { this.options = options; return this; }
    public UniversalM3Dialog setUnits(String[] units) { this.units = units; return this; }
    public UniversalM3Dialog setSuggestions(List<String> suggestions) { this.suggestions = suggestions; return this; }

    public void showTextInput(OnText cb) {
        showCoreDialog(null, initial, hint != null ? hint : "Value", cb);
    }

    public void showChoiceInput(OnText cb) {
        List<String> combined = new ArrayList<>();
        if (options != null) combined.addAll(Arrays.asList(options));
        if (units != null) {
            for (String u : units) if (!combined.contains(u)) combined.add(u);
        }
        showCoreDialog(combined, initial, hint != null ? hint : "Custom value", cb);
    }

    public void showColorInput(OnText cb) {
        UniversalDialog.colorPicker(context, title, initial, suggestions,
            hex -> { if (cb != null) cb.onText(hex); });
    }

    public void showUnitInput(String prop, OnUnit cb) {
        List<String> chipValues = new ArrayList<>();
        if (options != null) chipValues.addAll(Arrays.asList(options));
        if (units != null) {
            for (String u : units) if (!chipValues.contains(u)) chipValues.add(u);
        }
        showCoreDialog(chipValues, initial, hint != null ? hint : "Custom value", val -> {
            if (cb != null) cb.onUnit(val);
        });
    }

    public void showAutocompleteChoice(List<String> suggestions, String initialValue, OnText cb) {
        showCoreDialog(suggestions, initialValue, hint != null ? hint : "Custom value", cb);
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

        new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Done", (d, w) -> {
                if (cb != null) cb.onText(joinSpace(selected));
            })
            .setNegativeButton("Cancel", null)
            .show();
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
    private void showCoreDialog(List<String> presets, String currentValue, String inputHint, OnText cb) {
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

        new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Done", (d, w) -> {
                if (cb != null) cb.onText(edit.getText().toString().trim());
            })
            .setNegativeButton("Cancel", null)
            .show();
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
        final int idId = View.generateViewId(), classId = View.generateViewId(), tagId = View.generateViewId();
        modeGroup.addView(createModeChip("ID (#)", idId));
        modeGroup.addView(createModeChip("Class (.)", classId));
        modeGroup.addView(createModeChip("Tag", tagId));
        root.addView(modeGroup);

        // 2. Suggestion chips (dynamic)
        android.widget.HorizontalScrollView suggestionScroll = createChipScroll();
        final ChipGroup suggestionChips = createChipGroup();
        suggestionScroll.addView(suggestionChips);
        root.addView(suggestionScroll);

        // 3. Input
        TextInputLayout til = createTextInputLayout("Name");
        final MaterialAutoCompleteTextView edit = createEditor(til, "", null);
        til.addView(edit);
        root.addView(til);

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

        new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Done", (d, w) -> {
                String raw = edit.getText().toString().trim();
                int checked = modeGroup.getCheckedChipId();
                String prefix = (checked == idId) ? "#" : (checked == classId ? "." : "");
                if (cb != null) cb.onText(prefix + raw.replaceFirst("^[#.]", ""));
            })
            .setNegativeButton("Cancel", null)
            .show();
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

        new MaterialAlertDialogBuilder(context).setTitle(title).setView(scroll)
            .setPositiveButton("Done", (d, w) -> {
                if (cb != null) cb.onText(edits[0].getText().toString().trim() + " " + edits[1].getText().toString().trim() + " " + edits[2].getText().toString().trim() + " " + edits[3].getText().toString().trim());
            }).setNegativeButton("Cancel", null).show();
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
        g.setPadding(0, 0, 0, dp(16));
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
        til.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));
        til.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        return til;
    }

    private MaterialAutoCompleteTextView createEditor(TextInputLayout til, String value, List<String> presets) {
        MaterialAutoCompleteTextView edit = new MaterialAutoCompleteTextView(til.getContext());
        edit.setSingleLine(true);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        edit.setText(value);
        edit.setThreshold(1);
        edit.setMinimumHeight(dp(56));
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
}
