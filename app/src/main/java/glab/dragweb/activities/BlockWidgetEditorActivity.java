package glab.dragweb.activities;

import glab.dragweb.R;
import glab.dragweb.activities.*;
import glab.dragweb.fragments.*;
import glab.dragweb.engine.*;
import glab.dragweb.ui.*;
import glab.dragweb.logic.*;
import glab.dragweb.codegen.*;
import glab.dragweb.data.*;
import glab.dragweb.adapters.*;
import glab.dragweb.models.*;
import glab.dragweb.util.*;
import glab.dragweb.colorpicker.*;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import android.graphics.drawable.GradientDrawable;
import glab.dragweb.colorpicker.ColorPickerDialogFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.List;

public class BlockWidgetEditorActivity extends AppCompatActivity {

    private ManageBlocksWidgets customBlockManager;
    private WidgetRegistry widgetRegistry;

    private MaterialToolbar toolbar;
    private TextInputLayout tilId;
    private TextInputEditText etId;
    private TextInputLayout tilCategory;
    private TextInputEditText etCategory;
    private TextInputLayout tilCustomCategory;
    private TextInputEditText etCustomCategory;
    private TextInputLayout tilDisplayText;
    private TextInputEditText etDisplayText;
    private TextInputLayout tilWidgetTag;
    private TextInputEditText etWidgetTag;
    private TextInputLayout tilWidgetIcon;
    private TextInputEditText etWidgetIcon;
    private TextInputLayout tilCode;
    private TextInputEditText etCode;
    private Button btnCancel;
    private Button btnSave;

    private String type; // "block" or "widget"
    private String editId; // null if creating, ID/Name if editing
    private boolean isEditMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.block_widgeteditor);

        customBlockManager = new ManageBlocksWidgets(this);
        widgetRegistry = new WidgetRegistry(this);

        type = getIntent().getStringExtra("extra_type");
        if (type == null) type = getIntent().getStringExtra("type");

        editId = getIntent().getStringExtra("extra_id");
        if (editId == null) editId = getIntent().getStringExtra("id");
        if (editId == null) editId = getIntent().getStringExtra("name");
        isEditMode = (editId != null);

        if (type == null) {
            Toast.makeText(this, "Invalid activity entry", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupFields();

        // Handle edge-to-edge system bars insets and keyboard resizing (IME)
        View root = findViewById(R.id.root_layout);
        View content = findViewById(R.id.content);
        if (root != null && content != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                
                // Adjust root padding for Status Bar (top)
                root.setPadding(0, systemBars.top, 0, 0);
                
                // Adjust content padding for Navigation Bar or Keyboard (bottom)
                int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
                content.setPadding(dpToPx(16), 0, dpToPx(16), bottomPadding);
                
                return insets;
            });
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tilId = findViewById(R.id.tilId);
        etId = findViewById(R.id.mngId);
        tilCategory = findViewById(R.id.events_creator_icon_til);
        etCategory = findViewById(R.id.mngCategory);
        tilCustomCategory = findViewById(R.id.tilCustomCategory);
        etCustomCategory = findViewById(R.id.mngCustomCategory);
        tilDisplayText = findViewById(R.id.tilDisplayText);
        etDisplayText = findViewById(R.id.mngDisplayText);
        tilWidgetTag = findViewById(R.id.tilWidgetTag);
        etWidgetTag = findViewById(R.id.mngWidgetTag);
        tilWidgetIcon = findViewById(R.id.tilWidgetIcon);
        etWidgetIcon = findViewById(R.id.mngWidgetIcon);
        tilCode = findViewById(R.id.tilCode);
        etCode = findViewById(R.id.mngCode);
        btnCancel = findViewById(R.id.events_creator_cancel);
        btnSave = findViewById(R.id.events_creator_save);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Category selection behavior
        View.OnClickListener categoryChooser = v -> showCategoryPickerDialog();
        etCategory.setOnClickListener(categoryChooser);
        tilCategory.setEndIconOnClickListener(categoryChooser);

        // Icon picker behavior for widget icon
        View.OnClickListener iconChooser = v -> showIconPickerDialog();
        if (etWidgetIcon != null) {
            etWidgetIcon.setOnClickListener(iconChooser);
        }
        if (tilWidgetIcon != null) {
            tilWidgetIcon.setEndIconOnClickListener(iconChooser);
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
                    if ("block".equals(type)) {
                        exists = customBlockManager.findDefinition(inputId) != null && (!isEditMode || !inputId.equalsIgnoreCase(editId));
                    } else {
                        for (HashMap<String, Object> w : widgetRegistry.getAllWidgets()) {
                            if (inputId.equalsIgnoreCase(String.valueOf(w.get("name"))) && (!isEditMode || !inputId.equalsIgnoreCase(editId))) {
                                exists = true;
                                break;
                            }
                        }
                    }
                    if (exists) {
                        tilId.setError("block".equals(type) ? "Block ID already exists" : "Widget name already exists");
                    } else {
                        tilId.setError(null);
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // Cancel click
        btnCancel.setOnClickListener(v -> finish());

        // Save click
        btnSave.setOnClickListener(v -> save());
    }

    private void setupFields() {
        if ("block".equals(type)) {
            // Setup Toolbar Title
            toolbar.setTitle(isEditMode ? "Edit Custom Block" : "Create Custom Block");
            
            // Adjust input field visibility for Blocks
            tilDisplayText.setVisibility(View.VISIBLE);
            tilWidgetTag.setVisibility(View.GONE);
            tilWidgetIcon.setVisibility(View.GONE);

            // Set input helper descriptions
            tilId.setHint("Block ID (unique alphanumeric)");
            tilCode.setHint("HTML/CSS Template (e.g. %1$s{class:%2$s;})");

            if (isEditMode) {
                ManageBlocksWidgets.CustomBlockDef def = customBlockManager.findDefinition(editId);
                if (def != null) {
                    etId.setText(def.id);
                    etCategory.setText(def.category);
                    etDisplayText.setText(def.display);
                    etCode.setText(def.template);
                } else {
                    Toast.makeText(this, "Block def not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                etCategory.setText("html"); // Default block category
            }
        } else {
            // Widget setup
            toolbar.setTitle(isEditMode ? "Edit Custom Widget" : "Create Custom Widget");
            
            // Adjust input field visibility for Widgets
            tilDisplayText.setVisibility(View.GONE);
            tilWidgetTag.setVisibility(View.VISIBLE);
            tilWidgetIcon.setVisibility(View.VISIBLE);

            tilId.setHint("Widget Name (unique, e.g. CustomCard)");
            tilCode.setHint("Function Properties JSON");

            // Setup click selection for HTML tags
            etWidgetTag.setOnClickListener(v -> showHtmlTagPickerDialog());
            tilWidgetTag.setEndIconOnClickListener(v -> showHtmlTagPickerDialog());

            if (isEditMode) {
                HashMap<String, Object> widget = widgetRegistry.getWidgetByName(editId);
                if (widget != null) {
                    etId.setText(String.valueOf(widget.get("name")));
                    String cat = String.valueOf(widget.get("category"));
                    boolean isStandardCat = false;
                    for (WidgetRegistry.WidgetCategory wc : widgetRegistry.getWidgetCategories()) {
                        if (wc != null && wc.id != null && wc.id.equalsIgnoreCase(cat) && !"custom".equalsIgnoreCase(wc.id)) {
                            isStandardCat = true;
                            break;
                        }
                    }
                    if (isStandardCat) {
                        etCategory.setText(cat);
                        tilCustomCategory.setVisibility(View.GONE);
                    } else {
                        etCategory.setText("custom");
                        tilCustomCategory.setVisibility(View.VISIBLE);
                        if (etCustomCategory != null) {
                            etCustomCategory.setText(cat);
                        }
                    }
                    etWidgetTag.setText(String.valueOf(widget.get("tag")));
                    String iconName = widget.containsKey("icon") && widget.get("icon") != null ? String.valueOf(widget.get("icon")) : "box";
                    etWidgetIcon.setText(iconName);
                    updateIconPreview(iconName);
                    
                    Object funcObj = widget.get("function");
                    String funcJson = funcObj != null ? new GsonBuilder().setPrettyPrinting().create().toJson(funcObj) : "{}";
                    etCode.setText(funcJson);
                } else {
                    Toast.makeText(this, "Widget not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                etCategory.setText("basic"); // Default widget category
                tilCustomCategory.setVisibility(View.GONE);
                etWidgetTag.setText("button"); // Default tag
                etWidgetIcon.setText("puzzle");
                updateIconPreview("puzzle");
                etCode.setText("{\n  \"text\": \"Click Me\",\n  \"style\": {\n    \"padding\": \"10px 20px\",\n    \"backgroundColor\": \"#FFBB33\"\n  }\n}");
            }

            // Seed template dynamically depending on tag selection (only in creation mode)
            if (!isEditMode) {
                etWidgetTag.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String selectedTag = s.toString().trim();
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
                        etCode.setText(template);
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
            }
        }
    }

    private void showCategoryPickerDialog() {
        final String[] categories;
        if ("block".equals(type)) {
            List<CategoryDef> defs = CategoryDef.getCategories(this);
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
            for (CategoryDef c : defs) {
                if (c.id != null && !c.id.trim().isEmpty()) {
                    set.add(c.id.trim());
                }
            }
            if (set.isEmpty()) {
                categories = new String[]{"css", "css_layout", "css_flex", "css_grid", "js_dom", "js_logic", "js_events", "meta"};
            } else {
                categories = set.toArray(new String[0]);
            }
        } else {
            List<WidgetRegistry.WidgetCategory> widgetCats = widgetRegistry.getWidgetCategories();
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
            for (WidgetRegistry.WidgetCategory c : widgetCats) {
                if (c.id != null && !c.id.trim().isEmpty()) {
                    set.add(c.id.trim());
                }
            }
            if (set.isEmpty()) {
                categories = new String[]{"layout", "basic", "form", "media", "advanced", "custom"};
            } else {
                categories = set.toArray(new String[0]);
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Category")
                .setItems(categories, (dialog, which) -> {
                    String selected = categories[which];
                    etCategory.setText(selected);
                    checkCategorySelection(selected);
                })
                .show();
    }

    private void checkCategorySelection(String selectedCat) {
        if (tilCustomCategory == null) return;
        if ("custom".equalsIgnoreCase(selectedCat)) {
            tilCustomCategory.setVisibility(View.VISIBLE);
            if (etCustomCategory != null) {
                etCustomCategory.requestFocus();
            }
        } else {
            tilCustomCategory.setVisibility(View.GONE);
        }
    }

    private void showIconPickerDialog() {
        final String[] iconNames = WidgetRegistry.AVAILABLE_ICONS;
        android.widget.BaseAdapter iconAdapter = new android.widget.BaseAdapter() {
            @Override
            public int getCount() { return iconNames.length; }
            @Override
            public Object getItem(int position) { return iconNames[position]; }
            @Override
            public long getItemId(int position) { return position; }
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(android.R.layout.activity_list_item, parent, false);
                }
                android.widget.TextView text = convertView.findViewById(android.R.id.text1);
                android.widget.ImageView icon = convertView.findViewById(android.R.id.icon);
                String name = iconNames[position];
                text.setText(name);
                int res = WidgetRegistry.getIconDrawableRes(name);
                icon.setImageResource(res);
                icon.setImageTintList(com.google.android.material.color.MaterialColors.getColorStateList(
                    BlockWidgetEditorActivity.this, android.R.attr.colorPrimary, android.content.res.ColorStateList.valueOf(Color.BLACK)));
                return convertView;
            }
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Icon")
                .setAdapter(iconAdapter, (dialog, which) -> {
                    String selectedIcon = iconNames[which];
                    etWidgetIcon.setText(selectedIcon);
                    updateIconPreview(selectedIcon);
                })
                .show();
    }

    private void updateIconPreview(String iconName) {
        if (etWidgetIcon == null) return;
        int res = WidgetRegistry.getIconDrawableRes(iconName);
        android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(this, res);
        if (d != null) {
            d = androidx.core.graphics.drawable.DrawableCompat.wrap(d.mutate());
            int color = com.google.android.material.color.MaterialColors.getColor(
                this, android.R.attr.colorPrimary, Color.BLACK);
            androidx.core.graphics.drawable.DrawableCompat.setTint(d, color);
            int size = (int)(24 * getResources().getDisplayMetrics().density);
            d.setBounds(0, 0, size, size);
            etWidgetIcon.setCompoundDrawablesRelative(d, null, null, null);
            etWidgetIcon.setCompoundDrawablePadding((int)(8 * getResources().getDisplayMetrics().density));
        }
    }

    private void showHtmlTagPickerDialog() {
        final String[] tags = {"button", "div", "p", "h1", "h2", "h3", "img", "input", "textarea", "a", "span", "label", "select"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select HTML Tag")
                .setItems(tags, (dialog, which) -> {
                    etWidgetTag.setText(tags[which]);
                })
                .show();
    }

    private void save() {
        String id = etId.getText().toString().trim();
        String category = etCategory.getText().toString().trim();
        String code = etCode.getText().toString().trim();

        if (id.isEmpty() || category.isEmpty() || code.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("block".equals(type)) {
            String display = etDisplayText.getText().toString().trim();
            if (display.isEmpty()) {
                Toast.makeText(this, "Please fill in the Display Text", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isEditMode || !id.equalsIgnoreCase(editId)) {
                if (customBlockManager.findDefinition(id) != null) {
                    if (tilId != null) tilId.setError("Block ID already exists");
                    return;
                }
            }
            if (tilId != null) tilId.setError(null);

            if (isEditMode && editId != null && !editId.equalsIgnoreCase(id)) {
                customBlockManager.removeDefinition(editId);
            }

            ManageBlocksWidgets.CustomBlockDef def = new ManageBlocksWidgets.CustomBlockDef();
            def.id = id;
            def.display = display;
            def.template = code;
            def.category = category;

            customBlockManager.addDefinition(def);
            Toast.makeText(this, "Block saved", Toast.LENGTH_SHORT).show();
            finish();

        } else {
            // Widget validation and saving
            String tag = etWidgetTag.getText().toString().trim();
            String icon = etWidgetIcon != null && etWidgetIcon.getText() != null ? etWidgetIcon.getText().toString().trim() : "box";
            if (icon.isEmpty()) icon = "box";

            if (tag.isEmpty()) {
                Toast.makeText(this, "Please fill in all widget fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Parse function JSON
            HashMap<String, Object> functionMap;
            try {
                functionMap = new Gson().fromJson(code, new TypeToken<HashMap<String, Object>>(){}.getType());
            } catch (Exception e) {
                Toast.makeText(this, "Invalid JSON format: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            // Verify uniqueness of name
            if (!isEditMode || !id.equalsIgnoreCase(editId)) {
                for (HashMap<String, Object> w : widgetRegistry.getAllWidgets()) {
                    if (id.equalsIgnoreCase(String.valueOf(w.get("name")))) {
                        if (tilId != null) tilId.setError("Widget name already exists");
                        return;
                    }
                }
            }
            if (tilId != null) tilId.setError(null);

            if (isEditMode && editId != null && !editId.equalsIgnoreCase(id)) {
                widgetRegistry.deleteWidget(editId);
            }

            String cat = category;
            if ("custom".equalsIgnoreCase(cat)) {
                String customCat = etCustomCategory != null && etCustomCategory.getText() != null
                    ? etCustomCategory.getText().toString().trim() : "";
                if (!customCat.isEmpty()) {
                    cat = customCat;
                }
            }

            HashMap<String, Object> newWidget = new HashMap<>();
            newWidget.put("name", id);
            newWidget.put("tag", tag);
            newWidget.put("category", cat);
            newWidget.put("icon", icon);
            newWidget.put("function", functionMap);

            widgetRegistry.updateOrAddWidget(newWidget);
            Toast.makeText(this, "Widget saved", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
