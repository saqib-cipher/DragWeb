package sketchweb.gl;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FontPickerDialogFragment extends DialogFragment {

    public interface OnFontsSelectedListener {
        void onFontsSelected(String fonts);
    }

    private OnFontsSelectedListener listener;
    public void setOnFontsSelectedListener(OnFontsSelectedListener l) { this.listener = l; }

    private final List<FontEntry> allFonts = new ArrayList<>();
    private final List<FontEntry> filtered = new ArrayList<>();
    /** Tracks selected fonts in the order the user selected them */
    private final List<FontEntry> selectedOrdered = new ArrayList<>();
    private final List<String> customNames = new ArrayList<>();
    private String selectedCategory = "Default";
    private FontAdapter adapter;
    private ChipGroup selectedChips;
    private final Map<String, List<String>> defaultFonts = new LinkedHashMap<>();
    private GoogleFontsManager fontsMgr;
    private List<GoogleFontsManager.FontItem> allPopular;

    private static class FontEntry {
        String id;
        String name;
        String category;
        boolean isDefault;
        boolean isSelected;
    }

    private static String cssFallbackFor(String cat) {
        if (cat == null) return "sans-serif";
        switch (cat.toLowerCase()) {
            case "serif": return "serif";
            case "sans-serif": return "sans-serif";
            case "monospace": return "monospace";
            case "cursive": case "handwriting": return "cursive";
            case "fantasy": case "display": return "fantasy";
            default: return "sans-serif";
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String projectId = getArguments() != null ? getArguments().getString("projectId", "") : "";
        fontsMgr = !projectId.isEmpty() ? new GoogleFontsManager(requireContext(), projectId) : null;
        allPopular = GoogleFontsManager.loadFontsFromAssets(requireContext());

        buildDefaultFonts();
        buildAllFonts();

        // Restore initial selection — only fonts present in initial are selected, in order
        String initial = getArguments() != null ? getArguments().getString("initialFonts", "") : "";
        if (!initial.isEmpty()) {
            for (String part : initial.split(",")) {
                String t = part.trim().replaceAll("^['\"]|['\"]$", "");
                if (t.isEmpty()) continue;
                if (t.equals("serif") || t.equals("sans-serif") || t.equals("monospace")
                    || t.equals("cursive") || t.equals("fantasy")) continue;
                FontEntry match = null;
                for (FontEntry fe : allFonts) {
                    if (fe.name.equals(t) && !fe.isDefault) { match = fe; break; }
                }
                if (match == null) {
                    for (FontEntry fe : allFonts) {
                        if (fe.name.equals(t) && fe.isDefault) { match = fe; break; }
                    }
                }
                if (match != null) {
                    match.isSelected = true;
                    if (!selectedOrdered.contains(match)) selectedOrdered.add(match);
                }
            }
        }

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_font_picker, null);
        TextView emptyText = view.findViewById(R.id.empty_text);
        selectedChips = view.findViewById(R.id.selected_chips);
        refreshSelectedChips();

        ChipGroup catGroup = view.findViewById(R.id.category_chips);
        String[] cats = {"Default", "Custom", "Popular"};
        for (String cat : cats) {
            Chip chip = new Chip(requireContext());
            chip.setText(cat);
            chip.setCheckable(true);
            chip.setChecked(cat.equals(selectedCategory));
            chip.setOnCheckedChangeListener((button, isChecked) -> {
                if (isChecked) {
                    selectedCategory = cat;
                    for (int i = 0; i < catGroup.getChildCount(); i++) {
                        Chip c = (Chip) catGroup.getChildAt(i);
                        if (c != button) c.setChecked(false);
                    }
                    filterByCategory();
                }
            });
            catGroup.addView(chip);
        }

        RecyclerView recycler = view.findViewById(R.id.recycler_fonts);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FontAdapter();
        recycler.setAdapter(adapter);
        filterByCategory();

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_apply).setOnClickListener(v -> {
            saveCustomNames();
            StringBuilder sb = new StringBuilder();
            String fallback = "sans-serif";
            for (FontEntry fe : selectedOrdered) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("'").append(fe.name).append("'");
                if (!fe.isDefault && !"Custom".equals(fe.category)) {
                    String cat = categoryForName(fe.name);
                    if (cat != null) fallback = cssFallbackFor(cat);
                } else if (fe.isDefault) {
                    fallback = cssFallbackFor(fe.category);
                }
            }
            if (sb.length() > 0) {
                sb.append(", ").append(fallback);
            }
            if (listener != null) listener.onFontsSelected(sb.toString());
            dismiss();
        });

        return new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Font Family")
            .setView(view)
            .create();
    }

    private void refreshSelectedChips() {
        selectedChips.removeAllViews();
        for (FontEntry fe : selectedOrdered) {
            Chip chip = new Chip(requireContext());
            chip.setText(fe.name);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                fe.isSelected = false;
                selectedOrdered.remove(fe);
                refreshSelectedChips();
                adapter.notifyDataSetChanged();
            });
            selectedChips.addView(chip);
        }
    }

    /** Call when user toggles a font via checkbox or chip. */
    private void toggleFont(FontEntry fe) {
        fe.isSelected = !fe.isSelected;
        if (fe.isSelected) {
            if (!selectedOrdered.contains(fe)) selectedOrdered.add(fe);
        } else {
            selectedOrdered.remove(fe);
        }
        refreshSelectedChips();
        adapter.notifyDataSetChanged();
    }

    private String categoryForName(String name) {
        for (GoogleFontsManager.FontItem f : allPopular) {
            if (f.name.equals(name)) return f.category;
        }
        return null;
    }

    private void buildDefaultFonts() {
        defaultFonts.put("Sans-serif", Arrays.asList(
            "Arial", "Helvetica", "Verdana", "Tahoma", "Trebuchet MS",
            "Geneva", "Segoe UI", "Calibri"));
        defaultFonts.put("Serif", Arrays.asList(
            "Times New Roman", "Times", "Georgia", "Garamond",
            "Palatino", "Book Antiqua", "Baskerville"));
        defaultFonts.put("Monospace", Arrays.asList(
            "Courier New", "Courier", "Consolas", "Lucida Console",
            "Monaco", "Menlo", "Andale Mono"));
        defaultFonts.put("Cursive", Arrays.asList(
            "Comic Sans MS", "Brush Script MT"));
        defaultFonts.put("Fantasy", Arrays.asList(
            "Impact", "Papyrus", "Copperplate"));
    }

    private void buildAllFonts() {
        allFonts.clear();

        // Default fonts
        for (Map.Entry<String, List<String>> entry : defaultFonts.entrySet()) {
            for (String name : entry.getValue()) {
                FontEntry fe = new FontEntry();
                fe.id = "default:" + name;
                fe.name = name;
                fe.category = entry.getKey();
                fe.isDefault = true;
                allFonts.add(fe);
            }
        }

        // Popular fonts from assets — only show ones enabled in Font Settings
        for (GoogleFontsManager.FontItem f : allPopular) {
            if (f.href == null || f.href.isEmpty()) continue;
            if (fontsMgr != null && !fontsMgr.isEnabled(f.name)) continue;
            FontEntry fe = new FontEntry();
            fe.id = "google:" + f.name;
            fe.name = f.name;
            fe.category = "Google";
            fe.isDefault = false;
            allFonts.add(fe);
        }

        // Custom fonts from SharedPreferences (legacy)
        customNames.addAll(loadCustomNames());
        for (String cn : customNames) {
            FontEntry fe = new FontEntry();
            fe.id = "custom:" + cn;
            fe.name = cn;
            fe.category = "Custom";
            fe.isDefault = false;
            allFonts.add(fe);
        }

        // Custom imports from Font Settings
        if (fontsMgr != null) {
            for (GoogleFontsManager.CustomImport ci : fontsMgr.getCustomImports()) {
                if (ci.name == null || ci.name.isEmpty()) continue;
                FontEntry fe = new FontEntry();
                fe.id = "custom:" + ci.name;
                fe.name = ci.name;
                fe.category = "Custom";
                fe.isDefault = false;
                allFonts.add(fe);
            }
        }
    }

    private void filterByCategory() {
        filtered.clear();
        for (FontEntry fe : allFonts) {
            if (selectedCategory.equals("Default") && fe.isDefault) filtered.add(fe);
            else if (selectedCategory.equals("Custom") && !fe.isDefault && "Custom".equals(fe.category)) filtered.add(fe);
            else if (selectedCategory.equals("Popular") && !fe.isDefault && "Google".equals(fe.category)) filtered.add(fe);
        }
        adapter.notifyDataSetChanged();
        RecyclerView rv = getDialog() != null ? getDialog().findViewById(R.id.recycler_fonts) : null;
        TextView et = getDialog() != null ? getDialog().findViewById(R.id.empty_text) : null;
        if (rv != null && et != null) {
            if (filtered.isEmpty() && selectedCategory.equals("Custom")) {
                rv.setVisibility(View.GONE);
                et.setVisibility(View.VISIBLE);
            } else {
                rv.setVisibility(View.VISIBLE);
                et.setVisibility(View.GONE);
            }
        }
    }

    private List<String> loadCustomNames() {
        java.util.List<String> list = new ArrayList<>();
        String json = requireContext().getSharedPreferences("custom_fonts", 0).getString("names", "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
        } catch (Exception e) {}
        for (FontEntry fe : allFonts) {
            if ("Custom".equals(fe.category)) list.add(fe.name);
        }
        return list;
    }

    private void saveCustomNames() {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (FontEntry fe : allFonts) {
            if ("Custom".equals(fe.category)) arr.put(fe.name);
        }
        requireContext().getSharedPreferences("custom_fonts", 0).edit().putString("names", arr.toString()).apply();
    }

    private class FontAdapter extends RecyclerView.Adapter<FontAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_font_picker, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int i) {
            FontEntry fe = filtered.get(i);
            h.name.setText(fe.name);
            if (selectedCategory.equals("Default")) {
                h.cat.setVisibility(View.VISIBLE);
                h.cat.setText(fe.category);
            } else {
                h.cat.setVisibility(View.GONE);
            }
            h.check.setVisibility(View.VISIBLE);
            h.check.setOnCheckedChangeListener(null);
            h.check.setChecked(fe.isSelected);
            if ("Custom".equals(fe.category)) {
                h.deleteBtn.setVisibility(View.VISIBLE);
                h.deleteBtn.setOnClickListener(v -> {
                    allFonts.remove(fe);
                    customNames.remove(fe.name);
                    selectedOrdered.remove(fe);
                    saveCustomNames();
                    refreshSelectedChips();
                    filterByCategory();
                });
            } else {
                h.deleteBtn.setVisibility(View.GONE);
            }
            h.itemView.setOnClickListener(v -> {
                toggleFont(fe);
                // Update this item's checkbox state without re-binding everything
                h.check.setOnCheckedChangeListener(null);
                h.check.setChecked(fe.isSelected);
                h.check.setOnCheckedChangeListener((button, checked) -> {});
            });
            h.check.setOnCheckedChangeListener((button, checked) -> {
                if (checked != fe.isSelected) toggleFont(fe);
            });
        }

        @Override public int getItemCount() { return filtered.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, cat;
            MaterialCheckBox check;
            View deleteBtn;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.text_font_name);
                cat = v.findViewById(R.id.text_category);
                check = v.findViewById(R.id.checkbox_select);
                deleteBtn = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
