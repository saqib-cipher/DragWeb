package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.RelativeLayout;
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

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import sketchweb.gl.colorpicker.ColorPickerDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
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

public class ManageBlocksActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView rvBlocks;
    private TextView tvEmpty;
    private ExtendedFloatingActionButton fabAdd;
    private BlocksAdapter adapter;

    private final List<BlockDef> allBlockDefs = new ArrayList<>();
    private final List<BlockDef> filteredBlockDefs = new ArrayList<>();
    private String selectedCategory = "All";

    private ChipGroup chipGroupCategories;

    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String> exportLauncher;
    private BlockDef pendingExportBlock;

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
            toolbar.setTitle("Manage Blocks");
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        rvBlocks = findViewById(R.id.rvBlocks);
        tvEmpty = findViewById(R.id.tvEmptyBlocks);
        fabAdd = findViewById(R.id.fabAddCustom);

        setupCategoryChips();

        if (rvBlocks != null) {
            rvBlocks.setLayoutManager(new LinearLayoutManager(this));
            adapter = new BlocksAdapter();
            rvBlocks.setAdapter(adapter);
        }

        if (fabAdd != null) {
            fabAdd.setText("Add Block");
            fabAdd.setOnClickListener(v -> showEditBlockDialog(null));
        }

        importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) importBlocksFromUri(uri);
            }
        );

        exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) exportBlockToUri(uri, pendingExportBlock);
            }
        );

        loadBlocksFromStorage();
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
        if (toolbarView != null && toolbarView.getNavigationIcon() != null)
            toolbarView.getNavigationIcon().setTint(m3ColorOnSurface);
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
            .setTitle("Import Blocks")
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

    private String getUniqueBlockId(String baseId, List<BlockDef> list) {
        if (baseId == null || baseId.trim().isEmpty()) baseId = "custom_block";
        String candidate = baseId.trim();
        int counter = 1;
        while (isBlockIdExists(candidate, list)) {
            candidate = baseId.trim() + "_" + counter;
            counter++;
        }
        return candidate;
    }

    private boolean isBlockIdExists(String id, List<BlockDef> list) {
        if (id == null) return false;
        for (BlockDef def : list) {
            if (def.id != null && def.id.equalsIgnoreCase(id.trim())) return true;
        }
        return false;
    }

    private void importFromClipboard() {
        androidx.appcompat.app.AlertDialog progress = showProgressDialog("Importing blocks...");
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null && !text.toString().trim().isEmpty()) {
                    String json = text.toString().trim();
                    List<BlockDef> imported = new Gson().fromJson(json, new TypeToken<List<BlockDef>>(){}.getType());
                    if (imported != null && !imported.isEmpty()) {
                        for (BlockDef def : imported) {
                            if (def.id != null && !def.id.isEmpty()) {
                                if (isBlockIdExists(def.id, allBlockDefs)) {
                                    def.id = getUniqueBlockId(def.id, allBlockDefs);
                                }
                                allBlockDefs.add(def);
                            }
                        }
                        saveBlocksToStorage();
                        loadBlocksFromStorage();
                        Toast.makeText(this, "Imported " + imported.size() + " blocks from clipboard", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Invalid block JSON in clipboard", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Clipboard import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (progress != null && progress.isShowing()) progress.dismiss();
        }
    }

    private void showExportOptionsDialog() {
        String[] options = new String[]{"Export to JSON File", "Copy All to Clipboard"};
        new MaterialAlertDialogBuilder(this)
            .setTitle("Export All Blocks")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    pendingExportBlock = null;
                    exportLauncher.launch("blocks.json");
                } else if (which == 1) {
                    String json = new GsonBuilder().setPrettyPrinting().create().toJson(allBlockDefs);
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Blocks JSON", json));
                    Toast.makeText(this, "Copied all blocks JSON to clipboard", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void setupCategoryChips() {
        HorizontalScrollView scroll = findViewById(R.id.scrollViewChips);
        if (scroll != null) {
            scroll.setVisibility(View.VISIBLE);
        }
        chipGroupCategories = findViewById(R.id.chipGroupCategories);
        populateCategoryChips();
    }

    private void populateCategoryChips() {
        if (chipGroupCategories == null) return;
        chipGroupCategories.removeAllViews();

        List<String> categoriesList = new ArrayList<>();
        categoriesList.add("All");

        List<CategoryDef> loadedCats = CategoryDef.getCategories(this);
        for (CategoryDef cat : loadedCats) {
            if (cat.id != null && !categoriesList.contains(cat.id)) {
                categoriesList.add(cat.id);
            }
        }

        for (int i = 0; i < categoriesList.size(); i++) {
            final String catId = categoriesList.get(i);
            Chip chip = new Chip(this);
            chip.setText(catId);
            chip.setCheckable(true);
            chip.setClickable(true);
            if (catId.equalsIgnoreCase(selectedCategory)) {
                chip.setChecked(true);
            }
            chip.setOnClickListener(v -> {
                selectedCategory = catId;
                applyFilter();
            });
            chipGroupCategories.addView(chip);
        }
    }

    private File getCustomBlocksFile() {
        return CustomStorageUtil.getCustomFile(this, "blocks.json");
    }

    private void loadBlocksFromStorage() {
        allBlockDefs.clear();
        BlockDef.clearCache();
        try {
            List<BlockDef> loaded = BlockDef.getDefinitions(this);
            if (loaded != null) allBlockDefs.addAll(loaded);
        } catch (Exception e) {
            e.printStackTrace();
        }

        applyFilter();
        populateCategoryChips();
    }

    private void applyFilter() {
        filteredBlockDefs.clear();
        if ("All".equalsIgnoreCase(selectedCategory)) {
            filteredBlockDefs.addAll(allBlockDefs);
        } else {
            for (BlockDef def : allBlockDefs) {
                if (selectedCategory.equalsIgnoreCase(def.category)) {
                    filteredBlockDefs.add(def);
                }
            }
        }

        if (adapter != null) adapter.notifyDataSetChanged();
        if (tvEmpty != null) {
            tvEmpty.setVisibility(filteredBlockDefs.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void saveBlocksToStorage() {
        try {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(allBlockDefs);
            File storageFile = CustomStorageUtil.getCustomFile(this, "blocks.json");
            FileUtil.writeFile(storageFile.getAbsolutePath(), json);
            BlockDef.clearCache();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showEditBlockDialog(final BlockDef existing) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_custom_block, null);
        final com.google.android.material.textfield.TextInputLayout tilId = dialogView.findViewById(R.id.til_block_id);
        final TextInputEditText etId = dialogView.findViewById(R.id.et_block_id);
        final MaterialAutoCompleteTextView etCategory = dialogView.findViewById(R.id.et_block_category);
        final MaterialAutoCompleteTextView etType = dialogView.findViewById(R.id.et_block_type);
        final TextInputEditText etColor = dialogView.findViewById(R.id.et_block_color);
        final TextInputEditText etSpec = dialogView.findViewById(R.id.et_block_spec);
        final TextInputEditText etCode = dialogView.findViewById(R.id.et_block_code);

        List<CategoryDef> catDefs = CategoryDef.getCategories(this);
        List<String> catIds = new ArrayList<>();
        for (CategoryDef c : catDefs) catIds.add(c.id);

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, catIds);
        if (etCategory != null) {
            etCategory.setAdapter(catAdapter);
            if (!catIds.isEmpty()) etCategory.setText(catIds.get(0), false);
        }

        String[] types = new String[]{"normal", "c", "e", "b", "d", "s", "h"};
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
                colorPicker.show(getSupportFragmentManager(), "block_color_picker");
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
                    for (BlockDef def : allBlockDefs) {
                        if (def != existing && def.id != null && def.id.equalsIgnoreCase(inputId)) {
                            exists = true;
                            break;
                        }
                    }
                    if (exists) {
                        tilId.setError("Block ID already exists");
                    } else {
                        tilId.setError(null);
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (existing != null) {
            etId.setText(existing.id);
            if (existing.category != null && etCategory != null) {
                etCategory.setText(existing.category, false);
            }
            String currentType = existing.getType();
            if (currentType != null && etType != null) {
                etType.setText(currentType, false);
            }
            etColor.setText(existing.color != null ? existing.color : "#2196F3");
            etSpec.setText(existing.getSpec());
            etCode.setText(existing.code != null ? existing.code : "");
        } else {
            etColor.setText("#2196F3");
        }
        updatePreview.run();

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(existing == null ? "Create Custom Block" : "Edit Custom Block")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create();

        dialog.setOnShowListener(dialogInterface -> {
            android.widget.Button saveBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            saveBtn.setOnClickListener(v -> {
                String id = etId.getText().toString().trim();
                String cat = etCategory != null ? etCategory.getText().toString().trim() : "";
                String type = etType != null ? etType.getText().toString().trim() : "";
                String color = etColor.getText().toString().trim();
                if (!color.startsWith("#") && !color.isEmpty()) color = "#" + color;
                String spec = etSpec.getText().toString().trim();
                String code = etCode.getText().toString().trim();

                if (id.isEmpty()) {
                    if (tilId != null) tilId.setError("ID is required");
                    return;
                }
                if (spec.isEmpty()) {
                    Toast.makeText(ManageBlocksActivity.this, "Spec is required", Toast.LENGTH_SHORT).show();
                    return;
                }

                for (BlockDef def : allBlockDefs) {
                    if (def != existing && def.id != null && def.id.equalsIgnoreCase(id)) {
                        if (tilId != null) tilId.setError("Block ID already exists");
                        return;
                    }
                }
                if (tilId != null) tilId.setError(null);

                BlockDef target = existing != null ? existing : new BlockDef();
                target.id = id;
                target.name = id;
                target.category = cat;
                target.blockType = type;
                target.type = type;
                target.color = color.isEmpty() ? "#2196F3" : color;
                target.spec = spec;
                target.label = spec;
                target.code = code;

                if (existing == null) {
                    allBlockDefs.add(target);
                }

                saveBlocksToStorage();
                loadBlocksFromStorage();
                Toast.makeText(ManageBlocksActivity.this, "Block saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void importBlocksFromUri(Uri uri) {
        androidx.appcompat.app.AlertDialog progress = showProgressDialog("Importing blocks...");
        try (InputStream is = getContentResolver().openInputStream(uri);
             InputStreamReader reader = new InputStreamReader(is, "UTF-8")) {
            List<BlockDef> imported = new Gson().fromJson(reader, new TypeToken<List<BlockDef>>(){}.getType());
            if (imported != null && !imported.isEmpty()) {
                for (BlockDef def : imported) {
                    if (def.id != null && !def.id.isEmpty()) {
                        if (isBlockIdExists(def.id, allBlockDefs)) {
                            def.id = getUniqueBlockId(def.id, allBlockDefs);
                        }
                        allBlockDefs.add(def);
                    }
                }
                saveBlocksToStorage();
                loadBlocksFromStorage();
                Toast.makeText(this, "Imported " + imported.size() + " blocks", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (progress != null && progress.isShowing()) progress.dismiss();
        }
    }

    private void exportBlockToUri(Uri uri, BlockDef def) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            List<BlockDef> list;
            if (def != null) {
                list = new ArrayList<>();
                list.add(def);
            } else {
                list = allBlockDefs;
            }
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(list);
            os.write(json.getBytes("UTF-8"));
            Toast.makeText(this, "Export successful", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private class BlocksAdapter extends RecyclerView.Adapter<BlocksAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_custom_block_def, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final BlockDef def = filteredBlockDefs.get(position);
            holder.tvId.setText(def.id);
            holder.tvCategory.setText("Cat: " + def.category + " | Type: " + def.getType());
            holder.tvCode.setText("Code: " + (def.code != null ? def.code : "N/A"));

            holder.containerShape.removeAllViews();
            try {
                int blockColor;
                try {
                    blockColor = Color.parseColor(def.color != null ? def.color : "#2196F3");
                } catch (Exception ex) {
                    blockColor = Color.parseColor("#2196F3");
                }
                Block blockView = new Block(ManageBlocksActivity.this, 0, def.getSpec(), def.getType(), def.getOpCode(), new Object[]{Integer.valueOf(blockColor)});
                holder.containerShape.addView(blockView);
            } catch (Exception e) {
                TextView fallback = new TextView(ManageBlocksActivity.this);
                fallback.setText(def.getSpec());
                fallback.setTextColor(Color.WHITE);
                fallback.setPadding(16, 8, 16, 8);
                fallback.setBackgroundColor(Color.parseColor(def.color != null ? def.color : "#2196F3"));
                holder.containerShape.addView(fallback);
            }

            holder.btnEdit.setOnClickListener(v -> showEditBlockDialog(def));
            holder.btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ManageBlocksActivity.this)
                    .setTitle("Delete Block")
                    .setMessage("Delete '" + def.id + "'?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        allBlockDefs.remove(def);
                        saveBlocksToStorage();
                        loadBlocksFromStorage();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
            holder.btnExport.setOnClickListener(v -> {
                String[] options = new String[]{"Export to JSON File", "Copy to Clipboard"};
                new MaterialAlertDialogBuilder(ManageBlocksActivity.this)
                    .setTitle("Export Block (" + def.id + ")")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            pendingExportBlock = def;
                            exportLauncher.launch("block_" + def.id + ".json");
                        } else if (which == 1) {
                            List<BlockDef> list = new ArrayList<>();
                            list.add(def);
                            String json = new GsonBuilder().setPrettyPrinting().create().toJson(list);
                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Block JSON", json));
                            Toast.makeText(ManageBlocksActivity.this, "Copied block JSON to clipboard", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
            });
        }

        @Override
        public int getItemCount() {
            return filteredBlockDefs.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvId, tvCategory, tvCode;
            RelativeLayout containerShape;
            View btnEdit, btnDelete, btnExport;

            ViewHolder(View itemView) {
                super(itemView);
                tvId = itemView.findViewById(R.id.tv_block_id);
                tvCategory = itemView.findViewById(R.id.tv_block_category);
                tvCode = itemView.findViewById(R.id.tv_block_code);
                containerShape = itemView.findViewById(R.id.container_block_shape);
                btnEdit = itemView.findViewById(R.id.btn_edit_block);
                btnDelete = itemView.findViewById(R.id.btn_delete_block);
                btnExport = itemView.findViewById(R.id.btn_export_block);
            }
        }
    }
}
