package sketchweb.gl;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ManageBlocksWidgetsActivity extends AppCompatActivity {

    private ManageBlocksWidgets customBlockManager;
    private WidgetRegistry widgetRegistry;

    private MaterialToolbar toolbar;
    private TabLayout tabLayout;
    private androidx.recyclerview.widget.RecyclerView rvBlocks, rvWidgets;
    private TextView tvEmptyBlocks, tvEmptyWidgets;
    private ExtendedFloatingActionButton fabAddCustom;

    private BlocksAdapter blocksAdapter;
    private WidgetsAdapter widgetsAdapter;

    private int activeTab = 0; // 0: Blocks, 1: Widgets
    private int themePrimaryColor;

    private ChipGroup chipGroupCategories;
    private String selectedBlockCategory = "All";
    private String selectedWidgetCategory = "All";

    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String> exportBlocksLauncher;
    private ActivityResultLauncher<String> exportWidgetsLauncher;

    private ActivityResultLauncher<String> exportSingleBlockLauncher;
    private ActivityResultLauncher<String> exportSingleWidgetLauncher;
    private ManageBlocksWidgets.CustomBlockDef pendingExportBlock = null;
    private HashMap<String, Object> pendingExportWidget = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_blocks_widgets);

        customBlockManager = new ManageBlocksWidgets(this);
        widgetRegistry = new WidgetRegistry(this);

        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
        themePrimaryColor = typedValue.data;

        importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    performImport(uri);
                }
            }
        );

        exportBlocksLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    performExportBlocks(uri);
                }
            }
        );

        exportWidgetsLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    performExportWidgets(uri);
                }
            }
        );

        exportSingleBlockLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null && pendingExportBlock != null) {
                    performExportSingleBlock(uri, pendingExportBlock);
                    pendingExportBlock = null;
                }
            }
        );

        exportSingleWidgetLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null && pendingExportWidget != null) {
                    performExportSingleWidget(uri, pendingExportWidget);
                    pendingExportWidget = null;
                }
            }
        );

        initViews();
        refreshLists();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLists();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbarManager);
        tabLayout = findViewById(R.id.tabLayoutManager);
        rvBlocks = findViewById(R.id.rvBlocks);
        rvWidgets = findViewById(R.id.rvWidgets);
        tvEmptyBlocks = findViewById(R.id.tvEmptyBlocks);
        tvEmptyWidgets = findViewById(R.id.tvEmptyWidgets);
        fabAddCustom = findViewById(R.id.fabAddCustom);
        chipGroupCategories = findViewById(R.id.chipGroupCategories);

        rvBlocks.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rvWidgets.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));

        blocksAdapter = new BlocksAdapter();
        widgetsAdapter = new WidgetsAdapter();

        rvBlocks.setAdapter(blocksAdapter);
        rvWidgets.setAdapter(widgetsAdapter);

        populateBlockCategoryChips();

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                activeTab = tab.getPosition();
                if (activeTab == 0) {
                    rvBlocks.setVisibility(View.VISIBLE);
                    rvWidgets.setVisibility(View.GONE);
                    tvEmptyBlocks.setVisibility(blocksAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    tvEmptyWidgets.setVisibility(View.GONE);
                    fabAddCustom.setText("Add Block");
                    populateBlockCategoryChips();
                } else {
                    rvBlocks.setVisibility(View.GONE);
                    rvWidgets.setVisibility(View.VISIBLE);
                    tvEmptyBlocks.setVisibility(View.GONE);
                    tvEmptyWidgets.setVisibility(widgetsAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    fabAddCustom.setText("Add Widget");
                    populateWidgetCategoryChips();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        fabAddCustom.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(ManageBlocksWidgetsActivity.this, BlockWidgetEditorActivity.class);
            if (activeTab == 0) {
                intent.putExtra("extra_type", "block");
            } else {
                intent.putExtra("extra_type", "widget");
            }
            startActivity(intent);
        });
    }

    private void refreshLists() {
        refreshBlocksList();
        refreshWidgetsList();
    }

    private void refreshBlocksList() {
        List<ManageBlocksWidgets.CustomBlockDef> defs = customBlockManager.getDefinitions();
        List<ManageBlocksWidgets.CustomBlockDef> filtered = new ArrayList<>();
        for (ManageBlocksWidgets.CustomBlockDef def : defs) {
            if (def != null) {
                String cat = def.category != null ? def.category : "html";
                if (selectedBlockCategory.equalsIgnoreCase("All") || selectedBlockCategory.equalsIgnoreCase(cat)) {
                    filtered.add(def);
                }
            }
        }
        blocksAdapter.setBlocks(filtered);
        
        if (activeTab == 0) {
            tvEmptyBlocks.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            populateBlockCategoryChips();
        } else {
            tvEmptyBlocks.setVisibility(View.GONE);
        }
    }

    private void refreshWidgetsList() {
        ArrayList<HashMap<String, Object>> widgets = widgetRegistry.getAllWidgets();
        ArrayList<HashMap<String, Object>> filtered = new ArrayList<>();
        for (HashMap<String, Object> w : widgets) {
            if (w != null) {
                String cat = w.containsKey("category") ? String.valueOf(w.get("category")) : "basic";
                if (selectedWidgetCategory.equalsIgnoreCase("All") || selectedWidgetCategory.equalsIgnoreCase(cat)) {
                    filtered.add(w);
                }
            }
        }
        widgetsAdapter.setWidgets(filtered);
        
        if (activeTab == 1) {
            tvEmptyWidgets.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            populateWidgetCategoryChips();
        } else {
            tvEmptyWidgets.setVisibility(View.GONE);
        }
    }

    private void copyToClipboard(String label, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void populateBlockCategoryChips() {
        if (chipGroupCategories == null) return;
        chipGroupCategories.removeAllViews();
        List<String> categories = customBlockManager.getBlockCategories();
        // Always show "All" first
        Chip allChip = new Chip(this);
        allChip.setText("ALL");
        allChip.setCheckable(true);
        allChip.setClickable(true);
        allChip.setTag("All");
        if ("All".equalsIgnoreCase(selectedBlockCategory)) allChip.setChecked(true);
        allChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) { selectedBlockCategory = "All"; refreshBlocksList(); }
        });
        chipGroupCategories.addView(allChip);

        for (String cat : categories) {
            Chip chip = new Chip(this);
            chip.setText(cat.toUpperCase());
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setTag(cat);
            if (cat.equalsIgnoreCase(selectedBlockCategory)) chip.setChecked(true);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) { selectedBlockCategory = cat; refreshBlocksList(); }
            });
            chipGroupCategories.addView(chip);
        }
    }

    private void populateWidgetCategoryChips() {
        if (chipGroupCategories == null) return;
        chipGroupCategories.removeAllViews();
        Set<String> categories = new LinkedHashSet<>();
        for (HashMap<String, Object> w : widgetRegistry.getAllWidgets()) {
            if (w != null && w.containsKey("category")) {
                String cat = String.valueOf(w.get("category"));
                if (cat != null && !"null".equals(cat) && !cat.isEmpty()) {
                    categories.add(cat.toLowerCase());
                }
            }
        }
        // Always show "All" first
        Chip allChip = new Chip(this);
        allChip.setText("ALL");
        allChip.setCheckable(true);
        allChip.setClickable(true);
        allChip.setTag("All");
        if ("All".equalsIgnoreCase(selectedWidgetCategory)) allChip.setChecked(true);
        allChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) { selectedWidgetCategory = "All"; refreshWidgetsList(); }
        });
        chipGroupCategories.addView(allChip);

        for (String cat : categories) {
            Chip chip = new Chip(this);
            chip.setText(cat.toUpperCase());
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setTag(cat);
            if (cat.equalsIgnoreCase(selectedWidgetCategory)) chip.setChecked(true);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) { selectedWidgetCategory = cat; refreshWidgetsList(); }
            });
            chipGroupCategories.addView(chip);
        }
    }

    // -------------------------------------------------------------------------
    // RecyclerView Adapters
    // -------------------------------------------------------------------------
    private class BlocksAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<BlocksAdapter.BlockViewHolder> {
        private final List<ManageBlocksWidgets.CustomBlockDef> blockList = new ArrayList<>();
        private final java.util.Set<String> expandedIds = new java.util.HashSet<>();

        public void setBlocks(List<ManageBlocksWidgets.CustomBlockDef> blocks) {
            blockList.clear();
            blockList.addAll(blocks);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return blockList.size();
        }

        @Override
        public BlockViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_custom_block, parent, false);
            return new BlockViewHolder(view);
        }

        @Override
        public void onBindViewHolder(BlockViewHolder holder, int position) {
            final ManageBlocksWidgets.CustomBlockDef def = blockList.get(position);
            holder.tvId.setText(def.id);
            holder.tvDisplay.setText(def.display);
            holder.tvTemplate.setText(def.template);

            String categoryName = def.category != null ? def.category.toLowerCase() : "html";
            holder.tvCategory.setText(categoryName.toUpperCase());
            
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(BlockCategoryPalette.colorIntForCategory(categoryName));
            gd.setCornerRadius(dpToPx(ManageBlocksWidgetsActivity.this, 16f));
            holder.tvCategory.setBackground(gd);

            final boolean isExpanded = expandedIds.contains(def.id);
            holder.layoutExpandable.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            holder.tvArrow.setText(isExpanded ? "▲" : "▼");

            holder.itemView.setOnClickListener(v -> {
                boolean expanded = expandedIds.contains(def.id);
                if (expanded) {
                    expandedIds.remove(def.id);
                } else {
                    expandedIds.add(def.id);
                }
                android.transition.TransitionManager.beginDelayedTransition((ViewGroup) holder.itemView.getParent());
                notifyItemChanged(position);
            });

            // Bind actions dynamically
            holder.layoutActions.removeAllViews();

            // Edit / Customize
            String editLabel = def.isCustom ? "Edit" : "Customize";
            MaterialButton btnEdit = createM3Button(editLabel, R.drawable.pencil, themePrimaryColor, v -> {
                android.content.Intent intent = new android.content.Intent(ManageBlocksWidgetsActivity.this, BlockWidgetEditorActivity.class);
                intent.putExtra("extra_type", "block");
                intent.putExtra("extra_id", def.id);
                startActivity(intent);
            });
            holder.layoutActions.addView(btnEdit);

            // Duplicate
            MaterialButton btnDuplicate = createM3Button("Duplicate", R.drawable.copy_plus, themePrimaryColor, v -> duplicateBlock(def));
            holder.layoutActions.addView(btnDuplicate);

            // Copy
            MaterialButton btnCopy = createM3Button("Copy", R.drawable.code_plus, themePrimaryColor, v -> {
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(def);
                copyToClipboard("Block JSON", json);
            });
            holder.layoutActions.addView(btnCopy);

            // Export
            MaterialButton btnExport = createM3Button("Export", R.drawable.file_export, themePrimaryColor, v -> {
                pendingExportBlock = def;
                List<ManageBlocksWidgets.CustomBlockDef> singleList = new ArrayList<>();
                singleList.add(def);
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(singleList);
                copyToClipboard("Block JSON", json);
                exportSingleBlockLauncher.launch(def.id + ".json");
            });
            holder.layoutActions.addView(btnExport);

            // Delete (only visible for custom blocks)
            if (def.isCustom) {
                MaterialButton btnDelete = createM3Button("Delete", R.drawable.trash, themePrimaryColor, v -> showBlockDeleteConfirmation(def));
                holder.layoutActions.addView(btnDelete);
            }
        }

        class BlockViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            TextView tvCategory, tvId, tvArrow, tvDisplay, tvTemplate;
            LinearLayout layoutExpandable, layoutActions;

            BlockViewHolder(View v) {
                super(v);
                tvCategory = v.findViewById(R.id.tvBlockCategory);
                tvId = v.findViewById(R.id.tvBlockId);
                tvArrow = v.findViewById(R.id.tvBlockArrow);
                tvDisplay = v.findViewById(R.id.tvBlockDisplay);
                tvTemplate = v.findViewById(R.id.tvBlockTemplate);
                layoutExpandable = v.findViewById(R.id.layoutBlockExpandable);
                layoutActions = v.findViewById(R.id.layoutBlockActions);
            }
        }
    }

    private class WidgetsAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<WidgetsAdapter.WidgetViewHolder> {
        private final List<HashMap<String, Object>> widgetList = new ArrayList<>();
        private final java.util.Set<String> expandedNames = new java.util.HashSet<>();

        public void setWidgets(List<HashMap<String, Object>> widgets) {
            widgetList.clear();
            widgetList.addAll(widgets);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return widgetList.size();
        }

        @Override
        public WidgetViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_custom_widget, parent, false);
            return new WidgetViewHolder(view);
        }

        @Override
        public void onBindViewHolder(WidgetViewHolder holder, int position) {
            final HashMap<String, Object> widget = widgetList.get(position);
            final String name = widget.containsKey("name") ? widget.get("name").toString() : "Unnamed";
            final String tag = widget.containsKey("tag") ? widget.get("tag").toString() : "div";
            final String category = widget.containsKey("category") ? widget.get("category").toString() : "basic";
            final String colorHex = widget.containsKey("color") ? widget.get("color").toString() : "#CCCCCC";

            holder.tvName.setText(name);
            holder.tvTag.setText("Tag: <" + tag + ">");
            
            holder.tvCategory.setText(category.toUpperCase());
            GradientDrawable gdCat = new GradientDrawable();
            gdCat.setColor(Color.parseColor("#3F51B5"));
            gdCat.setCornerRadius(dpToPx(ManageBlocksWidgetsActivity.this, 16f));
            holder.tvCategory.setBackground(gdCat);

            holder.tvColor.setText(" Color: " + colorHex + " ");
            GradientDrawable gdColor = new GradientDrawable();
            try {
                gdColor.setColor(Color.parseColor(colorHex));
            } catch (Exception e) {
                gdColor.setColor(Color.LTGRAY);
            }
            gdColor.setCornerRadius(dpToPx(ManageBlocksWidgetsActivity.this, 8f));
            holder.tvColor.setBackground(gdColor);

            Object funcObj = widget.get("function");
            final String funcJson = funcObj != null ? new GsonBuilder().setPrettyPrinting().create().toJson(funcObj) : "{}";
            holder.tvFunction.setText(funcJson);

            final boolean isExpanded = expandedNames.contains(name);
            holder.layoutExpandable.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            holder.tvArrow.setText(isExpanded ? "▲" : "▼");

            holder.itemView.setOnClickListener(v -> {
                boolean expanded = expandedNames.contains(name);
                if (expanded) {
                    expandedNames.remove(name);
                } else {
                    expandedNames.add(name);
                }
                android.transition.TransitionManager.beginDelayedTransition((ViewGroup) holder.itemView.getParent());
                notifyItemChanged(position);
            });

            // Bind actions dynamically
            holder.layoutActions.removeAllViews();

            // Edit
            MaterialButton btnEdit = createM3Button("Edit", R.drawable.pencil, themePrimaryColor, v -> {
                android.content.Intent intent = new android.content.Intent(ManageBlocksWidgetsActivity.this, BlockWidgetEditorActivity.class);
                intent.putExtra("extra_type", "widget");
                intent.putExtra("extra_id", name);
                startActivity(intent);
            });
            holder.layoutActions.addView(btnEdit);

            // Duplicate
            MaterialButton btnDuplicate = createM3Button("Duplicate", R.drawable.copy_plus, themePrimaryColor, v -> duplicateWidget(widget));
            holder.layoutActions.addView(btnDuplicate);

            // Copy
            MaterialButton btnCopy = createM3Button("Copy", R.drawable.code_plus, themePrimaryColor, v -> {
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(widget);
                copyToClipboard("Widget JSON", json);
            });
            holder.layoutActions.addView(btnCopy);

            // Export
            MaterialButton btnExport = createM3Button("Export", R.drawable.file_export, themePrimaryColor, v -> {
                pendingExportWidget = widget;
                List<HashMap<String, Object>> singleList = new ArrayList<>();
                singleList.add(widget);
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(singleList);
                copyToClipboard("Widget JSON", json);
                exportSingleWidgetLauncher.launch(name + ".json");
            });
            holder.layoutActions.addView(btnExport);

            // Delete
            MaterialButton btnDelete = createM3Button("Delete", R.drawable.trash, themePrimaryColor, v -> showWidgetDeleteConfirmation(widget));
            holder.layoutActions.addView(btnDelete);
        }

        class WidgetViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            TextView tvCategory, tvName, tvArrow, tvTag, tvColor, tvFunction;
            LinearLayout layoutExpandable, layoutActions;

            WidgetViewHolder(View v) {
                super(v);
                tvCategory = v.findViewById(R.id.tvWidgetCategory);
                tvName = v.findViewById(R.id.tvWidgetName);
                tvArrow = v.findViewById(R.id.tvWidgetArrow);
                tvTag = v.findViewById(R.id.tvWidgetTag);
                tvColor = v.findViewById(R.id.tvWidgetColor);
                tvFunction = v.findViewById(R.id.tvWidgetFunction);
                layoutExpandable = v.findViewById(R.id.layoutWidgetExpandable);
                layoutActions = v.findViewById(R.id.layoutWidgetActions);
            }
        }
    }

    private void showBlockDeleteConfirmation(final ManageBlocksWidgets.CustomBlockDef def) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Delete Custom Block")
            .setMessage("Are you sure you want to delete block '" + def.id + "'?")
            .setBackgroundInsetStart(dpToPx(this, 24))
            .setBackgroundInsetEnd(dpToPx(this, 24))
            .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    customBlockManager.removeDefinition(def.id);
                    Toast.makeText(ManageBlocksWidgetsActivity.this, "Block deleted", Toast.LENGTH_SHORT).show();
                    refreshBlocksList();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showWidgetDeleteConfirmation(final HashMap<String, Object> widget) {
        final String name = widget.get("name").toString();
        new MaterialAlertDialogBuilder(this)
            .setTitle("Delete Custom Widget")
            .setMessage("Are you sure you want to delete custom widget '" + name + "'?")
            .setBackgroundInsetStart(dpToPx(this, 24))
            .setBackgroundInsetEnd(dpToPx(this, 24))
            .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    widgetRegistry.deleteWidget(name);
                    Toast.makeText(ManageBlocksWidgetsActivity.this, "Widget deleted", Toast.LENGTH_SHORT).show();
                    refreshWidgetsList();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_custom_manager, menu);
        
        // Dynamic tinting to follow colorOnSurface
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        int tintColor = typedValue.data;
        
        for (int i = 0; i < menu.size(); i++) {
            android.view.MenuItem item = menu.getItem(i);
            android.graphics.drawable.Drawable icon = item.getIcon();
            if (icon != null) {
                icon.mutate();
                androidx.core.graphics.drawable.DrawableCompat.setTint(icon, tintColor);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_import) {
            showImportDialog();
            return true;
        } else if (id == R.id.action_export) {
            if (activeTab == 0) {
                List<ManageBlocksWidgets.CustomBlockDef> customOnly = new ArrayList<>();
                for (ManageBlocksWidgets.CustomBlockDef def : customBlockManager.getDefinitions()) {
                    if (def != null && def.isCustom) {
                        customOnly.add(def);
                    }
                }
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(customOnly);
                copyToClipboard("Blocks JSON", json);
                exportBlocksLauncher.launch("blocks.json");
            } else {
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(widgetRegistry.getAllWidgets());
                copyToClipboard("Widgets JSON", json);
                exportWidgetsLauncher.launch("widgets.json");
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void performImport(Uri uri) {
        try {
            String json = readTextFromUri(uri);
            if (activeTab == 0) {
                boolean success = customBlockManager.importCustomBlocks(json);
                Toast.makeText(this, success ? "Blocks imported successfully" : "Failed to import blocks: invalid JSON", Toast.LENGTH_SHORT).show();
                refreshBlocksList();
            } else {
                boolean success = widgetRegistry.importAndSaveCustomWidgets(json);
                Toast.makeText(this, success ? "Widgets imported successfully" : "Failed to import widgets: invalid JSON", Toast.LENGTH_SHORT).show();
                refreshWidgetsList();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void performExportBlocks(Uri uri) {
        try {
            List<ManageBlocksWidgets.CustomBlockDef> customOnly = new ArrayList<>();
            for (ManageBlocksWidgets.CustomBlockDef def : customBlockManager.getDefinitions()) {
                if (def != null && def.isCustom) {
                    customOnly.add(def);
                }
            }
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(customOnly);
            writeTextToUri(uri, json);
            copyToClipboard("Blocks JSON", json);
            Toast.makeText(this, "Blocks exported and copied to clipboard", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void performExportWidgets(Uri uri) {
        try {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(widgetRegistry.getAllWidgets());
            writeTextToUri(uri, json);
            copyToClipboard("Widgets JSON", json);
            Toast.makeText(this, "Widgets exported and copied to clipboard", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void performExportSingleBlock(Uri uri, ManageBlocksWidgets.CustomBlockDef def) {
        try {
            List<ManageBlocksWidgets.CustomBlockDef> singleList = new ArrayList<>();
            singleList.add(def);
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(singleList);
            writeTextToUri(uri, json);
            copyToClipboard("Block JSON", json);
            Toast.makeText(this, "Block exported and copied to clipboard", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void performExportSingleWidget(Uri uri, HashMap<String, Object> widget) {
        try {
            List<HashMap<String, Object>> singleList = new ArrayList<>();
            singleList.add(widget);
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(singleList);
            writeTextToUri(uri, json);
            copyToClipboard("Widget JSON", json);
            Toast.makeText(this, "Widget exported and copied to clipboard", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String readTextFromUri(Uri uri) throws java.io.IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private void writeTextToUri(Uri uri, String text) throws java.io.IOException {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri);
             java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(outputStream, "UTF-8"))) {
            writer.write(text);
        }
    }

    private MaterialButton createM3Button(String text, int iconResId, int tintColor, View.OnClickListener listener) {
        MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(text);
        btn.setTextSize(11);
        btn.setCornerRadius(dpToPx(this, 18f));
        btn.setPadding(dpToPx(this, 12), 0, dpToPx(this, 12), 0);
        
        if (iconResId != 0) {
            btn.setIcon(getDrawable(iconResId));
            btn.setIconSize(dpToPx(this, 16f));
            btn.setIconPadding(dpToPx(this, 4f));
        }
        
        if (text.equals("Delete")) {
            int deleteColor = Color.parseColor("#BA1A1A");
            btn.setTextColor(deleteColor);
            btn.setIconTint(android.content.res.ColorStateList.valueOf(deleteColor));
            btn.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFDAD6")));
            btn.setRippleColor(android.content.res.ColorStateList.valueOf(adjustAlpha(deleteColor, 0.15f)));
        } else {
            btn.setTextColor(tintColor);
            btn.setIconTint(android.content.res.ColorStateList.valueOf(tintColor));
            btn.setStrokeColor(android.content.res.ColorStateList.valueOf(adjustAlpha(tintColor, 0.4f)));
            btn.setRippleColor(android.content.res.ColorStateList.valueOf(adjustAlpha(tintColor, 0.15f)));
        }
        
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(this, 36f));
        lp.setMarginStart(dpToPx(this, 8f));
        btn.setLayoutParams(lp);
        return btn;
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    private String generateUniqueBlockId(String originalId) {
        String baseId = originalId;
        int lastUnderscore = originalId.lastIndexOf('_');
        if (lastUnderscore != -1) {
            String suffixStr = originalId.substring(lastUnderscore + 1);
            if (suffixStr.matches("\\d+")) {
                baseId = originalId.substring(0, lastUnderscore);
            }
        }
        
        String candidate = baseId + "_1";
        int suffix = 1;
        while (customBlockManager.findDefinition(candidate) != null) {
            suffix++;
            candidate = baseId + "_" + suffix;
        }
        return candidate;
    }

    private String generateUniqueWidgetName(String originalName) {
        String baseName = originalName;
        int lastUnderscore = originalName.lastIndexOf('_');
        if (lastUnderscore != -1) {
            String suffixStr = originalName.substring(lastUnderscore + 1);
            if (suffixStr.matches("\\d+")) {
                baseName = originalName.substring(0, lastUnderscore);
            }
        }
        
        String candidate = baseName + "_1";
        int suffix = 1;
        boolean exists = true;
        while (exists) {
            exists = false;
            for (HashMap<String, Object> w : widgetRegistry.getAllWidgets()) {
                if (candidate.equalsIgnoreCase(w.get("name").toString())) {
                    suffix++;
                    candidate = baseName + "_" + suffix;
                    exists = true;
                    break;
                }
            }
        }
        return candidate;
    }

    private void duplicateBlock(ManageBlocksWidgets.CustomBlockDef def) {
        ManageBlocksWidgets.CustomBlockDef freshDef = customBlockManager.findDefinition(def.id);
        if (freshDef == null) freshDef = def; // Fallback
        
        ManageBlocksWidgets.CustomBlockDef newDef = new ManageBlocksWidgets.CustomBlockDef();
        newDef.category = freshDef.category;
        newDef.display = freshDef.display;
        newDef.template = freshDef.template;
        newDef.id = generateUniqueBlockId(freshDef.id);
        
        customBlockManager.addDefinitionAfter(freshDef.id, newDef);
        Toast.makeText(this, "Block duplicated as " + newDef.id, Toast.LENGTH_SHORT).show();
        refreshBlocksList();
    }

    private void duplicateWidget(HashMap<String, Object> widget) {
        String originalName = widget.containsKey("name") ? widget.get("name").toString() : "Widget";
        HashMap<String, Object> freshWidget = widgetRegistry.getWidgetByName(originalName);
        if (freshWidget == null) freshWidget = widget; // Fallback
        
        HashMap<String, Object> newWidget = new HashMap<>();
        String newName = generateUniqueWidgetName(originalName);
        
        newWidget.put("name", newName);
        newWidget.put("tag", freshWidget.get("tag"));
        newWidget.put("category", freshWidget.get("category"));
        newWidget.put("color", freshWidget.get("color"));
        
        // Deep copy of function properties
        Object funcObj = freshWidget.get("function");
        if (funcObj != null) {
            String json = new Gson().toJson(funcObj);
            HashMap<String, Object> newFunc = new Gson().fromJson(json, new TypeToken<HashMap<String, Object>>(){}.getType());
            newWidget.put("function", newFunc);
        } else {
            newWidget.put("function", new HashMap<String, Object>());
        }
        
        widgetRegistry.addWidgetAfter(originalName, newWidget);
        Toast.makeText(this, "Widget duplicated as " + newName, Toast.LENGTH_SHORT).show();
        refreshWidgetsList();
    }

    private void showImportDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_import, null);
        final TextInputLayout tilJson = dialogView.findViewById(R.id.tilImportJson);
        final TextInputEditText etJson = dialogView.findViewById(R.id.etImportJson);
        if (etJson != null) {
            applyOutlinedFieldStyling(etJson);
            try {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                    CharSequence clipText = clipboard.getPrimaryClip().getItemAt(0).getText();
                    if (clipText != null) {
                        String textStr = clipText.toString().trim();
                        if ((textStr.startsWith("{") && textStr.endsWith("}")) || (textStr.startsWith("[") && textStr.endsWith("]"))) {
                            etJson.setText(textStr);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        
        final String typeName = (activeTab == 0) ? "Blocks" : "Widgets";
        if (tilJson != null) {
            tilJson.setHint("Paste " + typeName + " JSON Content");
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("Import " + typeName)
            .setView(dialogView)
            .setBackgroundInsetStart(dpToPx(this, 24))
            .setBackgroundInsetEnd(dpToPx(this, 24))
            .setPositiveButton("Import from Text", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (etJson == null) return;
                    String json = etJson.getText().toString().trim();
                    if (json.isEmpty()) {
                        Toast.makeText(ManageBlocksWidgetsActivity.this, "JSON content is empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (activeTab == 0) {
                        try {
                            boolean success = customBlockManager.importCustomBlocks(json);
                            Toast.makeText(ManageBlocksWidgetsActivity.this, success ? "Blocks imported successfully" : "Failed to import blocks: invalid JSON", Toast.LENGTH_SHORT).show();
                            refreshBlocksList();
                        } catch (Exception e) {
                            Toast.makeText(ManageBlocksWidgetsActivity.this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        try {
                            boolean success = widgetRegistry.importAndSaveCustomWidgets(json);
                            Toast.makeText(ManageBlocksWidgetsActivity.this, success ? "Widgets imported successfully" : "Failed to import widgets: invalid JSON", Toast.LENGTH_SHORT).show();
                            refreshWidgetsList();
                        } catch (Exception e) {
                            Toast.makeText(ManageBlocksWidgetsActivity.this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            })
            .setNeutralButton("Choose File...", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    importLauncher.launch(new String[]{"application/json"});
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private View createSpinnerWrapper(Spinner spinner, String labelText) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextSize(12);
        label.setTextColor(Color.GRAY);
        label.setPadding(dpToPx(this, 4f), dpToPx(this, 8f), 0, dpToPx(this, 4f));
        wrapper.addView(label);
        
        LinearLayout spinnerBox = new LinearLayout(this);
        spinnerBox.setOrientation(LinearLayout.HORIZONTAL);
        spinnerBox.setGravity(Gravity.CENTER_VERTICAL);
        
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dpToPx(this, 12f));
        gd.setStroke(dpToPx(this, 1f), Color.parseColor("#79747E")); // Outline border color
        gd.setColor(Color.TRANSPARENT);
        spinnerBox.setBackground(gd);
        
        spinner.setBackgroundColor(Color.TRANSPARENT);
        
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(this, 48f));
        spinner.setLayoutParams(spinnerLp);
        
        spinnerBox.addView(spinner);
        wrapper.addView(spinnerBox);
        
        LinearLayout.LayoutParams wrapperLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperLp.setMargins(0, dpToPx(this, 12f), 0, 0);
        wrapper.setLayoutParams(wrapperLp);
        
        return wrapper;
    }

    private void applyOutlinedFieldStyling(android.widget.EditText edit) {
        edit.setMinimumHeight(dpToPx(this, 56f));
        int hp = dpToPx(this, 16f);
        int vp = dpToPx(this, 12f);
        edit.setPadding(hp, vp, hp, vp);
        edit.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
    }

    private TextInputLayout createOutlinedInputLayout(String hint) {
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(hint);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        int dp14 = dpToPx(this, 14f);
        til.setBoxCornerRadii(dp14, dp14, dp14, dp14);
        til.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        return til;
    }

    private static int dpToPx(android.content.Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
