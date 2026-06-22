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
import java.util.List;
import java.util.Map;

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

        populateCategoryChips(new String[]{"All", "html", "css", "logic", "animation", "asd", "value", "meta"}, selectedBlockCategory, true);

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
                    populateCategoryChips(new String[]{"All", "html", "css", "logic", "animation", "asd", "value", "meta"}, selectedBlockCategory, true);
                } else {
                    rvBlocks.setVisibility(View.GONE);
                    rvWidgets.setVisibility(View.VISIBLE);
                    tvEmptyBlocks.setVisibility(View.GONE);
                    tvEmptyWidgets.setVisibility(widgetsAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    fabAddCustom.setText("Add Widget");
                    populateCategoryChips(new String[]{"All", "basic", "layout", "form"}, selectedWidgetCategory, false);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        fabAddCustom.setOnClickListener(v -> {
            if (activeTab == 0) {
                showBlockEditDialog(null);
            } else {
                showWidgetEditDialog(null);
            }
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

    private void populateCategoryChips(String[] categories, String selectedCategory, boolean isBlockTab) {
        if (chipGroupCategories == null) return;
        chipGroupCategories.removeAllViews();
        for (String category : categories) {
            Chip chip = new Chip(this);
            chip.setText(category.toUpperCase());
            chip.setCheckable(true);
            chip.setClickable(true);
            if (category.equalsIgnoreCase(selectedCategory)) {
                chip.setChecked(true);
            }
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (isBlockTab) {
                        selectedBlockCategory = category;
                        refreshBlocksList();
                    } else {
                        selectedWidgetCategory = category;
                        refreshWidgetsList();
                    }
                }
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
            MaterialButton btnEdit = createM3Button(editLabel, R.drawable.icon_edit_round, themePrimaryColor, v -> showBlockEditDialog(def));
            holder.layoutActions.addView(btnEdit);

            // Duplicate
            MaterialButton btnDuplicate = createM3Button("Duplicate", R.drawable.icon_copy_all_round, themePrimaryColor, v -> duplicateBlock(def));
            holder.layoutActions.addView(btnDuplicate);

            // Copy
            MaterialButton btnCopy = createM3Button("Copy", R.drawable.icon_code_round, themePrimaryColor, v -> {
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(def);
                copyToClipboard("Block JSON", json);
            });
            holder.layoutActions.addView(btnCopy);

            // Export
            MaterialButton btnExport = createM3Button("Export", R.drawable.icon_export_round, themePrimaryColor, v -> {
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
                MaterialButton btnDelete = createM3Button("Delete", R.drawable.icon_delete_round, themePrimaryColor, v -> showBlockDeleteConfirmation(def));
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
            MaterialButton btnEdit = createM3Button("Edit", R.drawable.icon_edit_round, themePrimaryColor, v -> showWidgetEditDialog(widget));
            holder.layoutActions.addView(btnEdit);

            // Duplicate
            MaterialButton btnDuplicate = createM3Button("Duplicate", R.drawable.icon_copy_all_round, themePrimaryColor, v -> duplicateWidget(widget));
            holder.layoutActions.addView(btnDuplicate);

            // Copy
            MaterialButton btnCopy = createM3Button("Copy", R.drawable.icon_code_round, themePrimaryColor, v -> {
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(widget);
                copyToClipboard("Widget JSON", json);
            });
            holder.layoutActions.addView(btnCopy);

            // Export
            MaterialButton btnExport = createM3Button("Export", R.drawable.icon_export_round, themePrimaryColor, v -> {
                pendingExportWidget = widget;
                List<HashMap<String, Object>> singleList = new ArrayList<>();
                singleList.add(widget);
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(singleList);
                copyToClipboard("Widget JSON", json);
                exportSingleWidgetLauncher.launch(name + ".json");
            });
            holder.layoutActions.addView(btnExport);

            // Delete
            MaterialButton btnDelete = createM3Button("Delete", R.drawable.icon_delete_round, themePrimaryColor, v -> showWidgetDeleteConfirmation(widget));
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

    // -------------------------------------------------------------------------
    // Custom Block Create / Edit Dialog
    // -------------------------------------------------------------------------
    private void showBlockEditDialog(final ManageBlocksWidgets.CustomBlockDef editDef) {
        final boolean isEdit = (editDef != null);
        
        ScrollView sv = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dpToPx(this, 24), dpToPx(this, 24), dpToPx(this, 24), dpToPx(this, 12));
        sv.addView(form);

        // Block ID
        final TextInputLayout tilId = createOutlinedInputLayout("Block ID (unique alphanumeric)");
        final TextInputEditText etId = new TextInputEditText(this);
        etId.setSingleLine(true);
        applyOutlinedFieldStyling(etId);
        if (isEdit) {
            etId.setText(editDef.id);
            etId.setEnabled(false); // ID is final in edit
        }
        tilId.addView(etId);
        form.addView(tilId);

        // Category Spinner
        final Spinner spinnerCategory = new Spinner(this);
        final String[] categories = {"html", "css", "logic", "animation", "asd", "value"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);
        if (isEdit && editDef.category != null) {
            for (int i = 0; i < categories.length; i++) {
                if (categories[i].equalsIgnoreCase(editDef.category)) {
                    spinnerCategory.setSelection(i);
                    break;
                }
            }
        }
        form.addView(createSpinnerWrapper(spinnerCategory, "Category"));

        // Display text
        final TextInputLayout tilDisplay = createOutlinedInputLayout("Display Text (e.g. Set tag %s class to %s)");
        LinearLayout.LayoutParams displayLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        displayLp.setMargins(0, dpToPx(this, 16f), 0, 0);
        tilDisplay.setLayoutParams(displayLp);
        final TextInputEditText etDisplay = new TextInputEditText(this);
        etDisplay.setSingleLine(true);
        applyOutlinedFieldStyling(etDisplay);
        if (isEdit) {
            etDisplay.setText(editDef.display);
        }
        tilDisplay.addView(etDisplay);
        form.addView(tilDisplay);

        // Template text
        final TextInputLayout tilTemplate = createOutlinedInputLayout("HTML/CSS Template (e.g. %1$s{class:%2$s;})");
        LinearLayout.LayoutParams templateLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        templateLp.setMargins(0, dpToPx(this, 16f), 0, 0);
        tilTemplate.setLayoutParams(templateLp);
        final TextInputEditText etTemplate = new TextInputEditText(this);
        etTemplate.setSingleLine(false);
        etTemplate.setMinLines(3);
        applyOutlinedFieldStyling(etTemplate);
        if (isEdit) {
            etTemplate.setText(editDef.template);
        }
        tilTemplate.addView(etTemplate);
        form.addView(tilTemplate);

        new MaterialAlertDialogBuilder(this)
            .setTitle(isEdit ? "Edit Custom Block" : "Create Custom Block")
            .setView(sv)
            .setBackgroundInsetStart(dpToPx(this, 24))
            .setBackgroundInsetEnd(dpToPx(this, 24))
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String id = etId.getText().toString().trim();
                    String display = etDisplay.getText().toString().trim();
                    String template = etTemplate.getText().toString().trim();
                    String category = spinnerCategory.getSelectedItem().toString();

                    if (id.isEmpty() || display.isEmpty() || template.isEmpty()) {
                        Toast.makeText(ManageBlocksWidgetsActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // If creating new, verify unique ID
                    if (!isEdit) {
                        if (customBlockManager.findDefinition(id) != null) {
                            Toast.makeText(ManageBlocksWidgetsActivity.this, "Block ID already exists", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    ManageBlocksWidgets.CustomBlockDef def = new ManageBlocksWidgets.CustomBlockDef();
                    def.id = id;
                    def.display = display;
                    def.template = template;
                    def.category = category;

                    customBlockManager.addDefinition(def);
                    Toast.makeText(ManageBlocksWidgetsActivity.this, "Block saved", Toast.LENGTH_SHORT).show();
                    refreshBlocksList();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
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

    // -------------------------------------------------------------------------
    // Custom Widget Create / Edit Dialog
    // -------------------------------------------------------------------------
    private void showWidgetEditDialog(final HashMap<String, Object> editWidget) {
        final boolean isEdit = (editWidget != null);

        ScrollView sv = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dpToPx(this, 24), dpToPx(this, 24), dpToPx(this, 24), dpToPx(this, 12f));
        sv.addView(form);

        // Widget Name
        final TextInputLayout tilName = createOutlinedInputLayout("Widget Name (unique, e.g. CustomCard)");
        final TextInputEditText etName = new TextInputEditText(this);
        etName.setSingleLine(true);
        applyOutlinedFieldStyling(etName);
        if (isEdit) {
            etName.setText(editWidget.get("name").toString());
            etName.setEnabled(false); // Name is final in edit
        }
        tilName.addView(etName);
        form.addView(tilName);

        // HTML Tag Spinner
        final Spinner spinnerTag = new Spinner(this);
        final String[] tags = {"button", "div", "p", "h1", "h2", "h3", "img", "input", "textarea", "a", "span", "label", "select"};
        ArrayAdapter<String> tagAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tags);
        tagAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTag.setAdapter(tagAdapter);
        if (isEdit && editWidget.containsKey("tag")) {
            String currentTag = editWidget.get("tag").toString();
            for (int i = 0; i < tags.length; i++) {
                if (tags[i].equalsIgnoreCase(currentTag)) {
                    spinnerTag.setSelection(i);
                    break;
                }
            }
        }
        form.addView(createSpinnerWrapper(spinnerTag, "HTML Tag"));

        // Category Spinner
        final Spinner spinnerCat = new Spinner(this);
        final String[] widgetCats = {"basic", "layout", "form"};
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, widgetCats);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCat.setAdapter(catAdapter);
        if (isEdit && editWidget.containsKey("category")) {
            String currentCat = editWidget.get("category").toString();
            for (int i = 0; i < widgetCats.length; i++) {
                if (widgetCats[i].equalsIgnoreCase(currentCat)) {
                    spinnerCat.setSelection(i);
                    break;
                }
            }
        }
        form.addView(createSpinnerWrapper(spinnerCat, "Category"));

        // Hex Color Code
        final TextInputLayout tilColor = createOutlinedInputLayout("Palette Hex Color (e.g. #FFBB33)");
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.setMargins(0, dpToPx(this, 16f), 0, 0);
        tilColor.setLayoutParams(colLp);
        final TextInputEditText etColor = new TextInputEditText(this);
        etColor.setSingleLine(true);
        applyOutlinedFieldStyling(etColor);
        etColor.setText(isEdit && editWidget.containsKey("color") ? editWidget.get("color").toString() : "#FFBB33");
        tilColor.addView(etColor);
        form.addView(tilColor);

        // Function JSON
        final TextInputLayout tilFunction = createOutlinedInputLayout("Function Properties JSON");
        LinearLayout.LayoutParams funcLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        funcLp.setMargins(0, dpToPx(this, 16f), 0, 0);
        tilFunction.setLayoutParams(funcLp);
        final TextInputEditText etFunction = new TextInputEditText(this);
        etFunction.setSingleLine(false);
        etFunction.setMinLines(6);
        etFunction.setTypeface(Typeface.MONOSPACE);
        applyOutlinedFieldStyling(etFunction);
        
        if (isEdit) {
            Object funcObj = editWidget.get("function");
            String funcJson = funcObj != null ? new GsonBuilder().setPrettyPrinting().create().toJson(funcObj) : "{}";
            etFunction.setText(funcJson);
        } else {
            // Seed a default template
            etFunction.setText("{\n  \"text\": \"Click Me\",\n  \"style\": {\n    \"padding\": \"10px 20px\",\n    \"backgroundColor\": \"#FFBB33\"\n  }\n}");
        }
        tilFunction.addView(etFunction);
        form.addView(tilFunction);

        // Dynamic seeding template depending on spinner selection
        if (!isEdit) {
            spinnerTag.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String selectedTag = tags[position];
                    String template = "{\n  \"style\": {}\n}";
                    if ("button".equals(selectedTag)) {
                        template = "{\n  \"text\": \"Click Me\",\n  \"style\": {\n    \"padding\": \"10px 20px\",\n    \"backgroundColor\": \"#FFBB33\"\n  }\n}";
                    } else if ("p".equals(selectedTag) || "h1".equals(selectedTag) || "h2".equals(selectedTag) || "h3".equals(selectedTag) || "span".equals(selectedTag) || "label".equals(selectedTag)) {
                        template = "{\n  \"text\": \"Hello World\",\n  \"style\": {\n    \"fontSize\": \"16px\"\n  }\n}";
                    } else if ("img".equals(selectedTag)) {
                        template = "{\n  \"src\": \"android.R.drawable.ic_menu_gallery\",\n  \"style\": {\n    \"width\": \"100%\"\n  }\n}";
                    } else if ("input".equals(selectedTag) || "textarea".equals(selectedTag)) {
                        template = "{\n  \"type\": \"text\",\n  \"placeholder\": \"Enter text...\",\n  \"style\": {\n    \"width\": \"100%\",\n    \"padding\": \"8px\"\n  }\n}";
                    } else if ("div".equals(selectedTag)) {
                        template = "{\n  \"style\": {\n    \"padding\": \"16px\",\n    \"backgroundColor\": \"#F5F5F5\"\n  }\n}";
                    } else if ("a".equals(selectedTag)) {
                        template = "{\n  \"text\": \"Visit Link\",\n  \"href\": \"https://\",\n  \"target\": \"_blank\",\n  \"style\": {}\n}";
                    }
                    etFunction.setText(template);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle(isEdit ? "Edit Custom Widget" : "Create Custom Widget")
            .setView(sv)
            .setBackgroundInsetStart(dpToPx(this, 24))
            .setBackgroundInsetEnd(dpToPx(this, 24))
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String name = etName.getText().toString().trim();
                    String tag = spinnerTag.getSelectedItem().toString();
                    String category = spinnerCat.getSelectedItem().toString();
                    String color = etColor.getText().toString().trim();
                    String funcJson = etFunction.getText().toString().trim();

                    if (name.isEmpty() || color.isEmpty() || funcJson.isEmpty()) {
                        Toast.makeText(ManageBlocksWidgetsActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Validate hex color format
                    if (!color.startsWith("#") || (color.length() != 4 && color.length() != 7 && color.length() != 9)) {
                        Toast.makeText(ManageBlocksWidgetsActivity.this, "Invalid hex color code (e.g. #FFBB33)", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Parse function JSON
                    HashMap<String, Object> functionMap;
                    try {
                        functionMap = new Gson().fromJson(funcJson, new TypeToken<HashMap<String, Object>>(){}.getType());
                    } catch (Exception e) {
                        Toast.makeText(ManageBlocksWidgetsActivity.this, "Invalid JSON format: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Verify uniqueness of name when creating new
                    if (!isEdit) {
                        for (HashMap<String, Object> w : widgetRegistry.getAllWidgets()) {
                            if (name.equalsIgnoreCase(w.get("name").toString())) {
                                Toast.makeText(ManageBlocksWidgetsActivity.this, "Widget name already exists", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                    }

                    HashMap<String, Object> newWidget = new HashMap<>();
                    newWidget.put("name", name);
                    newWidget.put("tag", tag);
                    newWidget.put("category", category);
                    newWidget.put("color", color);
                    newWidget.put("function", functionMap);

                    widgetRegistry.updateOrAddWidget(newWidget);
                    Toast.makeText(ManageBlocksWidgetsActivity.this, "Widget saved", Toast.LENGTH_SHORT).show();
                    refreshWidgetsList();
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
        int dp12 = dpToPx(this, 12f);
        til.setBoxCornerRadii(dp12, dp12, dp12, dp12);
        til.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        return til;
    }

    private static int dpToPx(android.content.Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
