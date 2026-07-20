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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
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

    private void importFromClipboard() {
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
                                categories.removeIf(c -> cat.id.equalsIgnoreCase(c.id));
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
            File storageFile = getCustomCategoriesFile();
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(categories);
            FileUtil.writeFile(storageFile.getAbsolutePath(), json);
            CategoryDef.clearCache();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showEditCategoryDialog(final CategoryDef existing) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_category, null);
        final TextInputEditText etId = dialogView.findViewById(R.id.et_cat_id);
        final TextInputEditText etName = dialogView.findViewById(R.id.et_cat_name);
        final TextInputEditText etColor = dialogView.findViewById(R.id.et_cat_color);
        final Spinner spType = dialogView.findViewById(R.id.sp_cat_type);

        String[] types = new String[]{"css", "js", "common"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(typeAdapter);

        if (existing != null) {
            etId.setText(existing.id);
            etId.setEnabled(false);
            etName.setText(existing.name);
            etColor.setText(existing.catColor != null ? existing.catColor : "#1976D2");
            if (existing.type != null) {
                for (int i = 0; i < types.length; i++) {
                    if (types[i].equalsIgnoreCase(existing.type)) {
                        spType.setSelection(i);
                        break;
                    }
                }
            }
        } else {
            etColor.setText("#1976D2");
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle(existing == null ? "Create Category" : "Edit Category")
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                String id = etId.getText().toString().trim();
                String name = etName.getText().toString().trim();
                String color = etColor.getText().toString().trim();
                if (!color.startsWith("#") && !color.isEmpty()) color = "#" + color;
                String type = (String) spType.getSelectedItem();

                if (id.isEmpty() || name.isEmpty()) {
                    Toast.makeText(ManageCategoriesActivity.this, "ID and Name are required", Toast.LENGTH_SHORT).show();
                    return;
                }

                CategoryDef target = existing != null ? existing : new CategoryDef();
                target.id = id;
                target.name = name;
                target.catColor = color.isEmpty() ? "#1976D2" : color;
                target.type = type;

                if (existing == null) {
                    categories.add(target);
                }

                saveCategoriesToStorage();
                loadCategoriesFromStorage();
                Toast.makeText(ManageCategoriesActivity.this, "Category saved", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void importCategoriesFromUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             InputStreamReader reader = new InputStreamReader(is, "UTF-8")) {
            List<CategoryDef> imported = new Gson().fromJson(reader, new TypeToken<List<CategoryDef>>(){}.getType());
            if (imported != null && !imported.isEmpty()) {
                for (CategoryDef cat : imported) {
                    if (cat.id != null && !cat.id.isEmpty()) {
                        categories.removeIf(c -> cat.id.equalsIgnoreCase(c.id));
                        categories.add(cat);
                    }
                }
                saveCategoriesToStorage();
                loadCategoriesFromStorage();
                Toast.makeText(this, "Imported " + imported.size() + " categories", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            View btnEdit, btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                tvId = itemView.findViewById(R.id.tv_cat_id);
                tvName = itemView.findViewById(R.id.tv_cat_name);
                viewColorDot = itemView.findViewById(R.id.view_cat_color);
                btnEdit = itemView.findViewById(R.id.btn_edit_cat);
                btnDelete = itemView.findViewById(R.id.btn_delete_cat);
            }
        }
    }
}
