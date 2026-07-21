package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import sketchweb.gl.colorpicker.ColorPickerDialogFragment;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ManageCategoriesActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView rvCategories;
    private TextView tvEmpty;
    private ExtendedFloatingActionButton fabAdd;
    private CategoriesAdapter adapter;
    private final List<CategoryDef> categories = new ArrayList<>();

    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String> exportLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_blocks_widgets);

        View tabLayout = findViewById(R.id.tabLayoutManager);
        if (tabLayout != null) tabLayout.setVisibility(View.GONE);
        View rvWidgets = findViewById(R.id.rvWidgets);
        if (rvWidgets != null) rvWidgets.setVisibility(View.GONE);
        View tvEmptyWidgets = findViewById(R.id.tvEmptyWidgets);
        if (tvEmptyWidgets != null) tvEmptyWidgets.setVisibility(View.GONE);

        toolbar = findViewById(R.id.toolbarManager);
        if (toolbar != null) {
            toolbar.setTitle("Manage Categories");
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        rvCategories = findViewById(R.id.rvBlocks);
        tvEmpty = findViewById(R.id.tvEmptyBlocks);
        fabAdd = findViewById(R.id.fabAddCustom);

        if (rvCategories != null) {
            rvCategories.setLayoutManager(new LinearLayoutManager(this));
            adapter = new CategoriesAdapter();
            rvCategories.setAdapter(adapter);
        }

        if (fabAdd != null) {
            fabAdd.setText("Add Category");
            fabAdd.setOnClickListener(v -> showEditCategoryDialog(null));
        }

        importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) importCategoriesFromUri(uri);
            }
        );

        exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) exportCategoriesToUri(uri);
            }
        );

        loadCategoriesFromStorage();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_manage_import_export, menu);
        int m3ColorOnSurface = com.google.android.material.color.MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK);

        if (menu != null) {
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item.getIcon() != null) {
                    item.getIcon().setTint(m3ColorOnSurface);
                }
            }
        }

        Toolbar toolbarView = findViewById(R.id.toolbar);
        if (toolbarView != null && toolbarView.getNavigationIcon() != null) {
            toolbarView.getNavigationIcon().setTint(m3ColorOnSurface);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_import) {
            showImportOptionsDialog();
            return true;
        } else if (item.getItemId() == R.id.menu_export) {
            showExportOptionsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showImportOptionsDialog() {
        String[] options = new String[]{"Import from JSON File", "Import from Clipboard"};
        new MaterialAlertDialogBuilder(this)
            .setTitle("Import Categories")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    importLauncher.launch(new String[]{"application/json", "text/*", "*/*"});
                } else if (which == 1) {
                    importFromClipboard();
                }
            })
            .show();
    }

    private androidx.appcompat.app.AlertDialog showProgressDialog(String message) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_progress_material, null);
        TextView tvMessage = dialogView.findViewById(R.id.progress_message);
        if (tvMessage != null) tvMessage.setText(message);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create();
        dialog.show();
        return dialog;
    }

    private String getUniqueCategoryId(String baseId, List<CategoryDef> list) {
        if (baseId == null || baseId.trim().isEmpty()) baseId = "custom_cat";
        String candidate = baseId.trim();
        int counter = 1;
        while (isCategoryIdExists(candidate, list)) {
            candidate = baseId.trim() + "_" + counter;
            counter++;
        }
        return candidate;
    }

    private boolean isCategoryIdExists(String id, List<CategoryDef> list) {
        if (id == null) return false;
        for (CategoryDef cat : list) {
            if (cat.id != null && cat.id.equalsIgnoreCase(id.trim())) return true;
        }
        return false;
    }

    private void importFromClipboard() {
        androidx.appcompat.app.AlertDialog progress = showProgressDialog("Importing categories...");
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null && !text.toString().trim().isEmpty()) {
                    String json = text.toString().trim();
                    List<CategoryDef> imported = new Gson().fromJson(json, new TypeToken<List<CategoryDef>>(){}.getType());
                    if (imported != null && !imported.isEmpty()) {
                        for (CategoryDef cat : imported) {
                            if (cat.id != null && !cat.id.isEmpty()) {
                                if (isCategoryIdExists(cat.id, categories)) {
                                    cat.id = getUniqueCategoryId(cat.id, categories);
                                }
                                categories.add(cat);
                            }
                        }
                        saveCategoriesToStorage();
                        loadCategoriesFromStorage();
                        Toast.makeText(this, "Imported " + imported.size() + " categories from clipboard", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Invalid category JSON in clipboard", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (progress != null && progress.isShowing()) progress.dismiss();
        }
    }

    private void shareCategory(CategoryDef cat) {
        if (cat == null) return;
        try {
            List<CategoryDef> singleList = new ArrayList<>();
            singleList.add(cat);
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(singleList);

            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Category: " + cat.name);
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, json);
            startActivity(android.content.Intent.createChooser(shareIntent, "Share Category"));
        } catch (Exception e) {
            Toast.makeText(this, "Failed to share: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showExportOptionsDialog() {
        String[] options = new String[]{"Export to JSON File", "Copy All to Clipboard"};
        new MaterialAlertDialogBuilder(this)
            .setTitle("Export All Categories")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    exportLauncher.launch("categories.json");
                } else if (which == 1) {
                    String json = new GsonBuilder().setPrettyPrinting().create().toJson(categories);
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Categories JSON", json));
                    Toast.makeText(this, "Copied all categories JSON to clipboard", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private File getCustomCategoriesFile() {
        return CustomStorageUtil.getCustomFile(this, "categories.json");
    }

    private void loadCategoriesFromStorage() {
        categories.clear();
        CategoryDef.clearCache();
        try {
            List<CategoryDef> loaded = CategoryDef.getCategories(this);
            if (loaded != null) categories.addAll(loaded);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (adapter != null) adapter.notifyDataSetChanged();
        if (tvEmpty != null) {
            tvEmpty.setVisibility(categories.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void saveCategoriesToStorage() {
        try {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(categories);
            File storageFile = CustomStorageUtil.getCustomFile(this, "categories.json");
            FileUtil.writeFile(storageFile.getAbsolutePath(), json);
            CategoryDef.clearCache();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showEditCategoryDialog(final CategoryDef existing) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_category, null);
        final com.google.android.material.textfield.TextInputLayout tilId = dialogView.findViewById(R.id.til_cat_id);
        final TextInputEditText etId = dialogView.findViewById(R.id.et_cat_id);
        final TextInputEditText etName = dialogView.findViewById(R.id.et_cat_name);
        final TextInputEditText etColor = dialogView.findViewById(R.id.et_cat_color);
        final MaterialAutoCompleteTextView etType = dialogView.findViewById(R.id.et_cat_type);

        String[] types = new String[]{"css", "js", "common"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, types);
        if (etType != null) {
            etType.setAdapter(typeAdapter);
            etType.setText(types[0], false);
        }

        Runnable updatePreview = () -> {
            String hex = etColor.getText() != null ? etColor.getText().toString().trim() : "";
            if (!hex.startsWith("#") && !hex.isEmpty()) hex = "#" + hex;
            try {
                int parsed = hex.isEmpty() ? Color.TRANSPARENT : Color.parseColor(hex);
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(parsed);
                dot.setStroke((int)(1 * getResources().getDisplayMetrics().density), Color.LTGRAY);
                int size = (int)(20 * getResources().getDisplayMetrics().density);
                dot.setBounds(0, 0, size, size);
                etColor.setCompoundDrawablesRelative(null, null, dot, null);
            } catch (Exception ignored) {}
        };

        if (etColor != null) {
            etColor.setFocusable(false);
            etColor.setCursorVisible(false);
            etColor.setOnClickListener(v -> {
                ColorPickerDialogFragment colorPicker = new ColorPickerDialogFragment();
                colorPicker.setHexOnlyMode(true);
                colorPicker.setOnColorSelectedListener(selectedHex -> {
                    etColor.setText(selectedHex);
                    updatePreview.run();
                });
                colorPicker.show(getSupportFragmentManager(), "category_color_picker");
            });

            etColor.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updatePreview.run(); }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // Real-time ID validation
        if (etId != null && tilId != null) {
            etId.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String inputId = s.toString().trim();
                    if (inputId.isEmpty()) {
                        tilId.setError(null);
                        return;
                    }
                    boolean exists = false;
                    for (CategoryDef cat : categories) {
                        if (cat != existing && cat.id != null && cat.id.equalsIgnoreCase(inputId)) {
                            exists = true;
                            break;
                        }
                    }
                    if (exists) {
                        tilId.setError("Category ID already exists");
                    } else {
                        tilId.setError(null);
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (existing != null) {
            etId.setText(existing.id);
            etName.setText(existing.name);
            etColor.setText(existing.catColor != null ? existing.catColor : "#1976D2");
            if (existing.type != null && etType != null) {
                etType.setText(existing.type, false);
            }
        } else {
            etColor.setText("#1976D2");
        }
        updatePreview.run();

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(existing == null ? "Create Category" : "Edit Category")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create();

        dialog.setOnShowListener(dialogInterface -> {
            android.widget.Button saveBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            saveBtn.setOnClickListener(v -> {
                String id = etId.getText().toString().trim();
                String name = etName.getText().toString().trim();
                String color = etColor.getText().toString().trim();
                if (!color.startsWith("#") && !color.isEmpty()) color = "#" + color;
                String type = etType != null ? etType.getText().toString().trim() : "";

                if (id.isEmpty()) {
                    if (tilId != null) tilId.setError("ID is required");
                    return;
                }
                if (name.isEmpty()) {
                    Toast.makeText(ManageCategoriesActivity.this, "Name is required", Toast.LENGTH_SHORT).show();
                    return;
                }

                for (CategoryDef cat : categories) {
                    if (cat != existing && cat.id != null && cat.id.equalsIgnoreCase(id)) {
                        if (tilId != null) tilId.setError("Category ID already exists");
                        return;
                    }
                }
                if (tilId != null) tilId.setError(null);

                if (existing == null) {
                    CategoryDef newCat = new CategoryDef();
                    newCat.id = id;
                    newCat.name = name;
                    newCat.catColor = color;
                    newCat.type = type;
                    categories.add(newCat);
                } else {
                    existing.id = id;
                    existing.name = name;
                    existing.catColor = color;
                    existing.type = type;
                }

                saveCategoriesToStorage();
                loadCategoriesFromStorage();
                Toast.makeText(ManageCategoriesActivity.this, "Category saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void importCategoriesFromUri(Uri uri) {
        androidx.appcompat.app.AlertDialog progress = showProgressDialog("Importing categories...");
        try (InputStream is = getContentResolver().openInputStream(uri);
             InputStreamReader reader = new InputStreamReader(is, "UTF-8")) {
            List<CategoryDef> imported = new Gson().fromJson(reader, new TypeToken<List<CategoryDef>>(){}.getType());
            if (imported != null && !imported.isEmpty()) {
                for (CategoryDef cat : imported) {
                    if (cat.id != null && !cat.id.isEmpty()) {
                        if (isCategoryIdExists(cat.id, categories)) {
                            cat.id = getUniqueCategoryId(cat.id, categories);
                        }
                        categories.add(cat);
                    }
                }
                saveCategoriesToStorage();
                loadCategoriesFromStorage();
                Toast.makeText(this, "Imported " + imported.size() + " categories", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (progress != null && progress.isShowing()) progress.dismiss();
        }
    }

    private void exportCategoriesToUri(Uri uri) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(categories);
            os.write(json.getBytes("UTF-8"));
            Toast.makeText(this, "Export successful", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private class CategoriesAdapter extends RecyclerView.Adapter<CategoriesAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_custom_category_def, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final CategoryDef cat = categories.get(position);
            holder.tvId.setText(cat.id);
            holder.tvName.setText(cat.name + " (" + (cat.type != null ? cat.type : "common") + ")");

            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            try {
                dot.setColor(Color.parseColor(cat.catColor != null ? cat.catColor : "#1976D2"));
            } catch (Exception e) {
                dot.setColor(Color.parseColor("#1976D2"));
            }
            if (holder.viewColorDot != null) holder.viewColorDot.setBackground(dot);

            holder.btnEdit.setOnClickListener(v -> showEditCategoryDialog(cat));
            if (holder.btnShare != null) holder.btnShare.setOnClickListener(v -> shareCategory(cat));
            holder.btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ManageCategoriesActivity.this)
                    .setTitle("Delete Category")
                    .setMessage("Delete '" + cat.id + "'?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        categories.remove(cat);
                        saveCategoriesToStorage();
                        loadCategoriesFromStorage();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvId, tvName;
            View viewColorDot;
            View btnEdit, btnShare, btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                tvId = itemView.findViewById(R.id.tv_cat_id);
                tvName = itemView.findViewById(R.id.tv_cat_name);
                viewColorDot = itemView.findViewById(R.id.view_cat_color);
                btnEdit = itemView.findViewById(R.id.btn_edit_cat);
                btnShare = itemView.findViewById(R.id.btn_share_cat);
                btnDelete = itemView.findViewById(R.id.btn_delete_cat);
            }
        }
    }
}
