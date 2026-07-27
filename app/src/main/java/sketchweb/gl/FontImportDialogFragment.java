package sketchweb.gl;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FontImportDialogFragment extends DialogFragment {

    public interface OnFontImportAppliedListener {
        void onFontImportApplied();
    }

    private GoogleFontsManager mgr;
    private OnFontImportAppliedListener listener;
    public void setOnFontImportAppliedListener(OnFontImportAppliedListener l) { this.listener = l; }

    private final List<String> allPages = new ArrayList<>();
    private final Set<String> selectedPages = new HashSet<>();
    private final List<GoogleFontsManager.FontItem> googleFonts = new ArrayList<>();
    private final List<GoogleFontsManager.CustomImport> customImports = new ArrayList<>();
    private GoogleFontAdapter fontAdapter;
    private CustomImportAdapter customAdapter;
    private RecyclerView recyclerCustom;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String projectId = getArguments() != null ? getArguments().getString("projectId", "") : "";
        mgr = new GoogleFontsManager(requireContext(), projectId);
        googleFonts.addAll(GoogleFontsManager.loadFontsFromAssets(requireContext()));

        PageManager pm = new PageManager(requireContext(), projectId);
        allPages.addAll(pm.getPages());

        // Load saved page selections directly from file (source of truth)
        Set<String> saved = readSelectedPagesFromFile(projectId);
        for (String page : allPages) {
            if (saved.contains(page)) selectedPages.add(page);
        }
        if (selectedPages.isEmpty()) {
            for (String page : allPages) {
                if ("index".equals(page)) { selectedPages.add(page); break; }
            }
        }
        if (selectedPages.isEmpty() && !allPages.isEmpty()) {
            selectedPages.add(allPages.get(0));
        }

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_font_import, null);

        // Page chips
        ChipGroup pageChips = view.findViewById(R.id.page_chips);
        for (String page : allPages) {
            Chip chip = new Chip(requireContext());
            chip.setText(page);
            chip.setChecked(selectedPages.contains(page));
            chip.setCheckable(true);
            chip.setOnCheckedChangeListener((button, isChecked) -> {
                if (isChecked) selectedPages.add(page);
                else selectedPages.remove(page);
            });
            pageChips.addView(chip);
        }
        ((TextView) view.findViewById(R.id.info_text)).setText(allPages.size() + " page(s) \u2014 select pages to apply");

        // Google Fonts list
        RecyclerView recyclerFonts = view.findViewById(R.id.recycler_fonts);
        recyclerFonts.setLayoutManager(new LinearLayoutManager(requireContext()));
        fontAdapter = new GoogleFontAdapter();
        recyclerFonts.setAdapter(fontAdapter);

        // Custom imports list
        customImports.addAll(mgr.getCustomImports());
        recyclerCustom = view.findViewById(R.id.recycler_custom);
        recyclerCustom.setLayoutManager(new LinearLayoutManager(requireContext()));
        customAdapter = new CustomImportAdapter();
        recyclerCustom.setAdapter(customAdapter);

        // Add custom import button
        view.findViewById(R.id.btn_add_custom).setOnClickListener(v -> showCustomImportDialog(null));

        TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        View customPanel = view.findViewById(R.id.custom_panel);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                boolean isCustom = tab.getPosition() == 1;
                recyclerFonts.setVisibility(isCustom ? View.GONE : View.VISIBLE);
                customPanel.setVisibility(isCustom ? View.VISIBLE : View.GONE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_apply).setOnClickListener(v -> {
            List<String> enabled = new ArrayList<>();
            for (int i = 0; i < googleFonts.size(); i++) {
                if (fontAdapter.checked.contains(i)) {
                    enabled.add(googleFonts.get(i).name);
                }
            }
            mgr.setEnabled(enabled);
            mgr.setCustomImports(customImports);
            mgr.setSelectedPages(selectedPages);

            Toast.makeText(requireContext(), "Font imports updated for " + selectedPages.size() + " page(s)", Toast.LENGTH_SHORT).show();
            if (listener != null) listener.onFontImportApplied();
            dismiss();
        });

        return new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Font Imports")
            .setView(view)
            .create();
    }

    /** Read saved pages directly from the fontimports.json file (source of truth). */
    private Set<String> readSelectedPagesFromFile(String projectId) {
        Set<String> result = new HashSet<>();
        File f = new File(
            android.os.Environment.getExternalStorageDirectory(),
            ".dragweb/projects/" + projectId + "/fontimports.json");
        if (f.exists()) {
            try {
                String body = FileUtil.readFile(f.getAbsolutePath());
                if (body != null && !body.isEmpty()) {
                    Map<String, Object> map = new com.google.gson.Gson().fromJson(
                        body, new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
                    if (map != null && map.containsKey("selectedPages")) {
                        List<String> list = (List<String>) map.get("selectedPages");
                        if (list != null) result.addAll(list);
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        return result;
    }

    private void showCustomImportDialog(Integer editIndex) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_import_input, null);
        TextInputEditText inputName = v.findViewById(R.id.input_name);
        TextInputEditText inputLink = v.findViewById(R.id.input_link);
        MaterialAutoCompleteTextView inputCategory = v.findViewById(R.id.input_category);

        String[] categories = new String[]{"sans-serif", "serif", "monospace", "cursive", "fantasy", "display", "handwriting"};
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categories);
        inputCategory.setAdapter(catAdapter);
        inputCategory.setText("sans-serif", false);

        if (editIndex != null) {
            GoogleFontsManager.CustomImport ci = customImports.get(editIndex);
            inputName.setText(ci.name);
            inputLink.setText(ci.href);
            if (ci.category != null) inputCategory.setText(ci.category, false);
        }

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(editIndex != null ? "Edit Import" : "Add Custom Import")
            .setView(v)
            .setPositiveButton("Add", (d, w) -> {
                String name = inputName.getText().toString().trim();
                String link = inputLink.getText().toString().trim();
                String category = inputCategory.getText().toString().trim();
                if (name.isEmpty() || link.isEmpty()) {
                    Toast.makeText(requireContext(), "Enter both name and link", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!link.startsWith("<link")) {
                    link = "<link href=\"" + link + "\" rel=\"stylesheet\">";
                }
                if (editIndex != null) {
                    customImports.get(editIndex).name = name;
                    customImports.get(editIndex).href = link;
                    customImports.get(editIndex).category = category;
                } else {
                    customImports.add(new GoogleFontsManager.CustomImport(name, link, category));
                }
                customAdapter.notifyDataSetChanged();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ---- Google Fonts adapter ----

    private class GoogleFontAdapter extends RecyclerView.Adapter<GoogleFontAdapter.VH> {
        final Set<Integer> checked = new HashSet<>();

        GoogleFontAdapter() {
            for (int i = 0; i < googleFonts.size(); i++) {
                if (mgr.isEnabled(googleFonts.get(i).name)) checked.add(i);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_font_import, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int i) {
            GoogleFontsManager.FontItem item = googleFonts.get(i);
            h.name.setText(item.name);
            if (item.category != null && !item.category.isEmpty()) {
                h.cat.setVisibility(View.VISIBLE);
                h.cat.setText(item.category);
            } else h.cat.setVisibility(View.GONE);
            h.check.setChecked(checked.contains(i));
            h.itemView.setOnClickListener(v -> {
                if (checked.contains(i)) checked.remove(i);
                else checked.add(i);
                h.check.setChecked(checked.contains(i));
            });
        }

        @Override public int getItemCount() { return googleFonts.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, cat;
            MaterialCheckBox check;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.text_name);
                cat = v.findViewById(R.id.text_cat);
                check = v.findViewById(R.id.checkbox_select);
            }
        }
    }

    // ---- Custom imports adapter ----

    private class CustomImportAdapter extends RecyclerView.Adapter<CustomImportAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_custom_import, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int i) {
            GoogleFontsManager.CustomImport ci = customImports.get(i);
            h.name.setText(ci.name);
            h.link.setText(ci.href);
            h.btnEdit.setOnClickListener(v -> showCustomImportDialog(i));
            h.btnDelete.setOnClickListener(v -> {
                customImports.remove(i);
                notifyDataSetChanged();
            });
        }

        @Override public int getItemCount() { return customImports.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, link;
            View btnEdit, btnDelete;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.text_name);
                link = v.findViewById(R.id.text_link);
                btnEdit = v.findViewById(R.id.btn_edit);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
