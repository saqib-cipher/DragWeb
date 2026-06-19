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
    private NestedScrollView scrollBlocks, scrollWidgets;
    private LinearLayout containerBlocks, containerWidgets;
    private TextView tvEmptyBlocks, tvEmptyWidgets;
    private ExtendedFloatingActionButton fabAddCustom;

    private int activeTab = 0; // 0: Blocks, 1: Widgets
    private int themePrimaryColor;

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
        scrollBlocks = findViewById(R.id.scrollBlocks);
        scrollWidgets = findViewById(R.id.scrollWidgets);
        containerBlocks = findViewById(R.id.containerBlocks);
        containerWidgets = findViewById(R.id.containerWidgets);
        tvEmptyBlocks = findViewById(R.id.tvEmptyBlocks);
        tvEmptyWidgets = findViewById(R.id.tvEmptyWidgets);
        fabAddCustom = findViewById(R.id.fabAddCustom);

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
                    scrollBlocks.setVisibility(View.VISIBLE);
                    scrollWidgets.setVisibility(View.GONE);
                    fabAddCustom.setText("Add Block");
                } else {
                    scrollBlocks.setVisibility(View.GONE);
                    scrollWidgets.setVisibility(View.VISIBLE);
                    fabAddCustom.setText("Add Widget");
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
        containerBlocks.removeAllViews();
        containerBlocks.addView(tvEmptyBlocks);

        List<ManageBlocksWidgets.CustomBlockDef> defs = customBlockManager.getDefinitions();
        if (defs.isEmpty()) {
            tvEmptyBlocks.setVisibility(View.VISIBLE);
        } else {
            tvEmptyBlocks.setVisibility(View.GONE);
            for (ManageBlocksWidgets.CustomBlockDef def : defs) {
                View blockCard = createBlockCard(def);
                containerBlocks.addView(blockCard);
            }
        }
    }

    private void refreshWidgetsList() {
        containerWidgets.removeAllViews();
        containerWidgets.addView(tvEmptyWidgets);

        ArrayList<HashMap<String, Object>> widgets = widgetRegistry.getAllWidgets();
        if (widgets.isEmpty()) {
            tvEmptyWidgets.setVisibility(View.VISIBLE);
        } else {
            tvEmptyWidgets.setVisibility(View.GONE);
            for (HashMap<String, Object> widget : widgets) {
                View widgetCard = createWidgetCard(widget);
                containerWidgets.addView(widgetCard);
            }
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

    // -------------------------------------------------------------------------
    // Custom Block Card Programmatic Builder
    // -------------------------------------------------------------------------
    private View createBlockCard(final ManageBlocksWidgets.CustomBlockDef def) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cardLp);
        card.setCardElevation(2f);
        card.setRadius(12f);
        card.setCardBackgroundColor(getColor(android.R.color.transparent));
        card.setStrokeColor(Color.parseColor("#33CCCCCC"));
        card.setStrokeWidth(1);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        // Header Row (always visible)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        // Category Tag
        TextView categoryTag = new TextView(this);
        String categoryName = def.category != null ? def.category.toLowerCase() : "html";
        categoryTag.setText(categoryName.toUpperCase());
        categoryTag.setTextSize(10);
        categoryTag.setTypeface(null, Typeface.BOLD);
        categoryTag.setTextColor(Color.WHITE);
        categoryTag.setPadding(12, 4, 12, 4);
        
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(BlockCategoryPalette.colorIntForCategory(categoryName));
        gd.setCornerRadius(16f);
        categoryTag.setBackground(gd);

        TextView idText = new TextView(this);
        LinearLayout.LayoutParams idLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        idLp.setMarginStart(12);
        idText.setLayoutParams(idLp);
        idText.setText(def.id);
        idText.setTypeface(null, Typeface.BOLD);
        idText.setTextSize(14);
        idText.setTextColor(Color.BLACK);

        // Chevron arrow indicator
        TextView arrowIndicator = new TextView(this);
        arrowIndicator.setText("▼");
        arrowIndicator.setTextSize(14);
        arrowIndicator.setTextColor(Color.GRAY);
        arrowIndicator.setPadding(8, 0, 8, 0);

        header.addView(categoryTag);
        header.addView(idText);
        header.addView(arrowIndicator);

        // Display pattern (always visible under header)
        TextView displayText = new TextView(this);
        displayText.setText(def.display);
        displayText.setTextSize(13);
        displayText.setTypeface(null, Typeface.ITALIC);
        displayText.setPadding(0, 8, 0, 0);

        // Expandable Content Container (initially GONE)
        LinearLayout expandableLayout = new LinearLayout(this);
        expandableLayout.setOrientation(LinearLayout.VERTICAL);
        expandableLayout.setVisibility(View.GONE);

        // Divider
        View divider = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(this, 1));
        divLp.setMargins(0, 12, 0, 12);
        divider.setLayoutParams(divLp);
        divider.setBackgroundColor(Color.parseColor("#1F000000"));
        expandableLayout.addView(divider);

        // Template label & Monospace block template
        TextView templateLabel = new TextView(this);
        templateLabel.setText("HTML/CSS Template:");
        templateLabel.setTextSize(12);
        templateLabel.setTextColor(Color.GRAY);
        templateLabel.setPadding(0, 0, 0, 2);

        TextView templateText = new TextView(this);
        templateText.setText(def.template);
        templateText.setTextSize(12);
        templateText.setTypeface(Typeface.MONOSPACE);
        templateText.setBackgroundColor(Color.parseColor("#15000000"));
        templateText.setPadding(12, 8, 12, 8);

        expandableLayout.addView(templateLabel);
        expandableLayout.addView(templateText);

        // Action Buttons Row inside horizontal scroll container
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsvLp.setMargins(0, 12, 0, 0);
        hsv.setLayoutParams(hsvLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        hsv.addView(actions);

        // Edit
        MaterialButton btnEdit = createM3Button("Edit", R.drawable.icon_edit_round, themePrimaryColor, v -> showBlockEditDialog(def));
        actions.addView(btnEdit);

        // Duplicate
        MaterialButton btnDuplicate = createM3Button("Duplicate", R.drawable.icon_copy_all_round, themePrimaryColor, v -> duplicateBlock(def));
        actions.addView(btnDuplicate);

        // Copy
        MaterialButton btnCopy = createM3Button("Copy", R.drawable.icon_code_round, themePrimaryColor, v -> {
            copyToClipboard("Block Template", def.template);
        });
        actions.addView(btnCopy);

        // Export
        MaterialButton btnExport = createM3Button("Export", R.drawable.icon_export_round, themePrimaryColor, v -> {
            pendingExportBlock = def;
            exportSingleBlockLauncher.launch(def.id + ".json");
        });
        actions.addView(btnExport);

        // Delete
        MaterialButton btnDelete = createM3Button("Delete", R.drawable.icon_delete_round, themePrimaryColor, v -> showBlockDeleteConfirmation(def));
        actions.addView(btnDelete);

        expandableLayout.addView(hsv);

        layout.addView(header);
        layout.addView(displayText);
        layout.addView(expandableLayout);
        card.addView(layout);

        // Card tap listener for smooth collapse/expand transition
        card.setOnClickListener(v -> {
            boolean isExpanded = (expandableLayout.getVisibility() == View.VISIBLE);
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) card.getParent());
            expandableLayout.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
            arrowIndicator.setText(isExpanded ? "▼" : "▲");
        });

        return card;
    }

    // -------------------------------------------------------------------------
    // Custom Widget Card Programmatic Builder
    // -------------------------------------------------------------------------
    private View createWidgetCard(final HashMap<String, Object> widget) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cardLp);
        card.setCardElevation(2f);
        card.setRadius(12f);
        card.setCardBackgroundColor(getColor(android.R.color.transparent));
        card.setStrokeColor(Color.parseColor("#33CCCCCC"));
        card.setStrokeWidth(1);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        // Header Row (always visible)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        // Category Tag
        TextView categoryTag = new TextView(this);
        String category = widget.containsKey("category") ? widget.get("category").toString() : "basic";
        categoryTag.setText(category.toUpperCase());
        categoryTag.setTextSize(10);
        categoryTag.setTypeface(null, Typeface.BOLD);
        categoryTag.setTextColor(Color.WHITE);
        categoryTag.setPadding(12, 4, 12, 4);
        
        GradientDrawable gdCat = new GradientDrawable();
        gdCat.setColor(Color.parseColor("#3F51B5"));
        gdCat.setCornerRadius(16f);
        categoryTag.setBackground(gdCat);

        TextView nameText = new TextView(this);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        nameLp.setMarginStart(12);
        nameText.setLayoutParams(nameLp);
        nameText.setText(widget.containsKey("name") ? widget.get("name").toString() : "Unnamed");
        nameText.setTypeface(null, Typeface.BOLD);
        nameText.setTextSize(14);
        nameText.setTextColor(Color.BLACK);

        // Chevron arrow indicator
        TextView arrowIndicator = new TextView(this);
        arrowIndicator.setText("▼");
        arrowIndicator.setTextSize(14);
        arrowIndicator.setTextColor(Color.GRAY);
        arrowIndicator.setPadding(8, 0, 8, 0);

        header.addView(categoryTag);
        header.addView(nameText);
        header.addView(arrowIndicator);

        // Tag and Color Row (always visible)
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.HORIZONTAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        details.setPadding(0, 8, 0, 0);

        TextView tagText = new TextView(this);
        tagText.setText("Tag: <" + (widget.containsKey("tag") ? widget.get("tag").toString() : "div") + ">");
        tagText.setTextSize(13);
        tagText.setTypeface(null, Typeface.BOLD);

        TextView colorText = new TextView(this);
        String colorHex = widget.containsKey("color") ? widget.get("color").toString() : "#CCCCCC";
        colorText.setText(" Color: " + colorHex + " ");
        colorText.setTextSize(12);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.setMarginStart(16);
        colorText.setLayoutParams(colLp);
        colorText.setTextColor(Color.WHITE);
        colorText.setTypeface(null, Typeface.BOLD);
        GradientDrawable gdColor = new GradientDrawable();
        try {
            gdColor.setColor(Color.parseColor(colorHex));
        } catch (Exception e) {
            gdColor.setColor(Color.LTGRAY);
        }
        gdColor.setCornerRadius(8f);
        colorText.setBackground(gdColor);

        details.addView(tagText);
        details.addView(colorText);

        // Expandable Content Container (initially GONE)
        LinearLayout expandableLayout = new LinearLayout(this);
        expandableLayout.setOrientation(LinearLayout.VERTICAL);
        expandableLayout.setVisibility(View.GONE);

        // Divider
        View divider = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(this, 1));
        divLp.setMargins(0, 12, 0, 12);
        divider.setLayoutParams(divLp);
        divider.setBackgroundColor(Color.parseColor("#1F000000"));
        expandableLayout.addView(divider);

        // Function JSON Preview
        TextView functionLabel = new TextView(this);
        functionLabel.setText("Function Properties JSON:");
        functionLabel.setTextSize(12);
        functionLabel.setTextColor(Color.GRAY);
        functionLabel.setPadding(0, 0, 0, 2);

        TextView functionText = new TextView(this);
        Object funcObj = widget.get("function");
        String funcJson = funcObj != null ? new GsonBuilder().setPrettyPrinting().create().toJson(funcObj) : "{}";
        functionText.setText(funcJson);
        functionText.setTextSize(11);
        functionText.setTypeface(Typeface.MONOSPACE);
        functionText.setBackgroundColor(Color.parseColor("#15000000"));
        functionText.setPadding(12, 8, 12, 8);

        expandableLayout.addView(functionLabel);
        expandableLayout.addView(functionText);

        // Action Buttons Row inside horizontal scroll container
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsvLp.setMargins(0, 12, 0, 0);
        hsv.setLayoutParams(hsvLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        hsv.addView(actions);

        // Edit
        MaterialButton btnEdit = createM3Button("Edit", R.drawable.icon_edit_round, themePrimaryColor, v -> showWidgetEditDialog(widget));
        actions.addView(btnEdit);

        // Duplicate
        MaterialButton btnDuplicate = createM3Button("Duplicate", R.drawable.icon_copy_all_round, themePrimaryColor, v -> duplicateWidget(widget));
        actions.addView(btnDuplicate);

        // Copy
        MaterialButton btnCopy = createM3Button("Copy", R.drawable.icon_code_round, themePrimaryColor, v -> {
            copyToClipboard("Widget Function JSON", funcJson);
        });
        actions.addView(btnCopy);

        // Export
        MaterialButton btnExport = createM3Button("Export", R.drawable.icon_export_round, themePrimaryColor, v -> {
            pendingExportWidget = widget;
            exportSingleWidgetLauncher.launch(nameText.getText().toString() + ".json");
        });
        actions.addView(btnExport);

        // Delete
        MaterialButton btnDelete = createM3Button("Delete", R.drawable.icon_delete_round, themePrimaryColor, v -> showWidgetDeleteConfirmation(widget));
        actions.addView(btnDelete);

        expandableLayout.addView(hsv);

        layout.addView(header);
        layout.addView(details);
        layout.addView(expandableLayout);
        card.addView(layout);

        // Card tap listener for smooth collapse/expand transition
        card.setOnClickListener(v -> {
            boolean isExpanded = (expandableLayout.getVisibility() == View.VISIBLE);
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) card.getParent());
            expandableLayout.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
            arrowIndicator.setText(isExpanded ? "▼" : "▲");
        });

        return card;
    }

    // -------------------------------------------------------------------------
    // Custom Block Create / Edit Dialog
    // -------------------------------------------------------------------------
    private void showBlockEditDialog(final ManageBlocksWidgets.CustomBlockDef editDef) {
        final boolean isEdit = (editDef != null);
        
        ScrollView sv = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(32, 24, 32, 24);
        sv.addView(form);

        // Block ID
        final TextInputLayout tilId = createOutlinedInputLayout("Block ID (unique alphanumeric)");
        final TextInputEditText etId = new TextInputEditText(this);
        etId.setSingleLine(true);
        if (isEdit) {
            etId.setText(editDef.id);
            etId.setEnabled(false); // ID is final in edit
        }
        tilId.addView(etId);
        form.addView(tilId);

        // Category Spinner
        TextView spinnerLabel = new TextView(this);
        spinnerLabel.setText("Category");
        spinnerLabel.setTextSize(12);
        spinnerLabel.setTextColor(Color.GRAY);
        spinnerLabel.setPadding(4, 16, 0, 4);
        form.addView(spinnerLabel);

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
        form.addView(spinnerCategory);

        // Display text
        final TextInputLayout tilDisplay = createOutlinedInputLayout("Display Text (e.g. Set tag %s class to %s)");
        LinearLayout.LayoutParams displayLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        displayLp.setMargins(0, 16, 0, 0);
        tilDisplay.setLayoutParams(displayLp);
        final TextInputEditText etDisplay = new TextInputEditText(this);
        etDisplay.setSingleLine(true);
        if (isEdit) {
            etDisplay.setText(editDef.display);
        }
        tilDisplay.addView(etDisplay);
        form.addView(tilDisplay);

        // Template text
        final TextInputLayout tilTemplate = createOutlinedInputLayout("HTML/CSS Template (e.g. %1$s{class:%2$s;})");
        LinearLayout.LayoutParams templateLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        templateLp.setMargins(0, 16, 0, 0);
        tilTemplate.setLayoutParams(templateLp);
        final TextInputEditText etTemplate = new TextInputEditText(this);
        etTemplate.setSingleLine(false);
        etTemplate.setMinLines(3);
        if (isEdit) {
            etTemplate.setText(editDef.template);
        }
        tilTemplate.addView(etTemplate);
        form.addView(tilTemplate);

        new MaterialAlertDialogBuilder(this)
            .setTitle(isEdit ? "Edit Custom Block" : "Create Custom Block")
            .setView(sv)
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
        form.setPadding(32, 24, 32, 24);
        sv.addView(form);

        // Widget Name
        final TextInputLayout tilName = createOutlinedInputLayout("Widget Name (unique, e.g. CustomCard)");
        final TextInputEditText etName = new TextInputEditText(this);
        etName.setSingleLine(true);
        if (isEdit) {
            etName.setText(editWidget.get("name").toString());
            etName.setEnabled(false); // Name is final in edit
        }
        tilName.addView(etName);
        form.addView(tilName);

        // HTML Tag Spinner
        TextView spinnerTagLabel = new TextView(this);
        spinnerTagLabel.setText("HTML Tag");
        spinnerTagLabel.setTextSize(12);
        spinnerTagLabel.setTextColor(Color.GRAY);
        spinnerTagLabel.setPadding(4, 16, 0, 4);
        form.addView(spinnerTagLabel);

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
        form.addView(spinnerTag);

        // Category Spinner
        TextView spinnerCatLabel = new TextView(this);
        spinnerCatLabel.setText("Category");
        spinnerCatLabel.setTextSize(12);
        spinnerCatLabel.setTextColor(Color.GRAY);
        spinnerCatLabel.setPadding(4, 16, 0, 4);
        form.addView(spinnerCatLabel);

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
        form.addView(spinnerCat);

        // Hex Color Code
        final TextInputLayout tilColor = createOutlinedInputLayout("Palette Hex Color (e.g. #FFBB33)");
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.setMargins(0, 16, 0, 0);
        tilColor.setLayoutParams(colLp);
        final TextInputEditText etColor = new TextInputEditText(this);
        etColor.setSingleLine(true);
        etColor.setText(isEdit && editWidget.containsKey("color") ? editWidget.get("color").toString() : "#FFBB33");
        tilColor.addView(etColor);
        form.addView(tilColor);

        // Function JSON
        final TextInputLayout tilFunction = createOutlinedInputLayout("Function Properties JSON");
        LinearLayout.LayoutParams funcLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        funcLp.setMargins(0, 16, 0, 0);
        tilFunction.setLayoutParams(funcLp);
        final TextInputEditText etFunction = new TextInputEditText(this);
        etFunction.setSingleLine(false);
        etFunction.setMinLines(6);
        etFunction.setTypeface(Typeface.MONOSPACE);
        
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
                exportBlocksLauncher.launch("blocks.json");
            } else {
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
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(customBlockManager.getDefinitions());
            writeTextToUri(uri, json);
            Toast.makeText(this, "Blocks exported successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void performExportWidgets(Uri uri) {
        try {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(widgetRegistry.getAllWidgets());
            writeTextToUri(uri, json);
            Toast.makeText(this, "Widgets exported successfully", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Block exported successfully", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Widget exported successfully", Toast.LENGTH_SHORT).show();
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
        ManageBlocksWidgets.CustomBlockDef newDef = new ManageBlocksWidgets.CustomBlockDef();
        newDef.category = def.category;
        newDef.display = def.display;
        newDef.template = def.template;
        newDef.id = generateUniqueBlockId(def.id);
        
        customBlockManager.addDefinitionAfter(def.id, newDef);
        Toast.makeText(this, "Block duplicated as " + newDef.id, Toast.LENGTH_SHORT).show();
        refreshBlocksList();
    }

    private void duplicateWidget(HashMap<String, Object> widget) {
        HashMap<String, Object> newWidget = new HashMap<>();
        String originalName = widget.containsKey("name") ? widget.get("name").toString() : "Widget";
        String newName = generateUniqueWidgetName(originalName);
        
        newWidget.put("name", newName);
        newWidget.put("tag", widget.get("tag"));
        newWidget.put("category", widget.get("category"));
        newWidget.put("color", widget.get("color"));
        
        // Deep copy of function properties
        Object funcObj = widget.get("function");
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
        
        final String typeName = (activeTab == 0) ? "Blocks" : "Widgets";
        if (tilJson != null) {
            tilJson.setHint("Paste " + typeName + " JSON Content");
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("Import " + typeName)
            .setView(dialogView)
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
