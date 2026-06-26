package sketchweb.gl;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.List;

public class BlockWidgetEditorActivity extends AppCompatActivity {

    private ManageBlocksWidgets customBlockManager;
    private WidgetRegistry widgetRegistry;

    private MaterialToolbar toolbar;
    private TextInputLayout tilId;
    private TextInputEditText etId;
    private TextInputLayout tilCategory;
    private TextInputEditText etCategory;
    private TextInputLayout tilDisplayText;
    private TextInputEditText etDisplayText;
    private TextInputLayout tilWidgetTag;
    private TextInputEditText etWidgetTag;
    private TextInputLayout tilWidgetColor;
    private TextInputEditText etWidgetColor;
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
        editId = getIntent().getStringExtra("extra_id");
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
        tilDisplayText = findViewById(R.id.tilDisplayText);
        etDisplayText = findViewById(R.id.mngDisplayText);
        tilWidgetTag = findViewById(R.id.tilWidgetTag);
        etWidgetTag = findViewById(R.id.mngWidgetTag);
        tilWidgetColor = findViewById(R.id.tilWidgetColor);
        etWidgetColor = findViewById(R.id.mngWidgetColor);
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
            tilWidgetColor.setVisibility(View.GONE);

            // Set input helper descriptions
            tilId.setHint("Block ID (unique alphanumeric)");
            tilCode.setHint("HTML/CSS Template (e.g. %1$s{class:%2$s;})");

            if (isEditMode) {
                ManageBlocksWidgets.CustomBlockDef def = customBlockManager.findDefinition(editId);
                if (def != null) {
                    etId.setText(def.id);
                    etId.setEnabled(false); // ID is final in edit
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
            tilWidgetColor.setVisibility(View.VISIBLE);

            tilId.setHint("Widget Name (unique, e.g. CustomCard)");
            tilCode.setHint("Function Properties JSON");

            // Setup click selection for HTML tags
            etWidgetTag.setOnClickListener(v -> showHtmlTagPickerDialog());
            tilWidgetTag.setEndIconOnClickListener(v -> showHtmlTagPickerDialog());

            if (isEditMode) {
                HashMap<String, Object> widget = widgetRegistry.getWidgetByName(editId);
                if (widget != null) {
                    etId.setText(String.valueOf(widget.get("name")));
                    etId.setEnabled(false); // Name is final in edit
                    etCategory.setText(String.valueOf(widget.get("category")));
                    etWidgetTag.setText(String.valueOf(widget.get("tag")));
                    etWidgetColor.setText(widget.containsKey("color") ? String.valueOf(widget.get("color")) : "#FFBB33");
                    
                    Object funcObj = widget.get("function");
                    String funcJson = funcObj != null ? new GsonBuilder().setPrettyPrinting().create().toJson(funcObj) : "{}";
                    etCode.setText(funcJson);
                } else {
                    Toast.makeText(this, "Widget not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                etCategory.setText("basic"); // Default widget category
                etWidgetTag.setText("button"); // Default tag
                etWidgetColor.setText("#FFBB33");
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
            List<String> list = customBlockManager.getBlockCategories();
            if (list.isEmpty()) {
                categories = new String[]{"html", "css", "logic", "animation", "asd", "value", "meta"};
            } else {
                categories = list.toArray(new String[0]);
            }
        } else {
            categories = new String[]{"basic", "layout", "form"};
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Category")
                .setItems(categories, (dialog, which) -> etCategory.setText(categories[which]))
                .show();
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

            if (!isEditMode) {
                if (customBlockManager.findDefinition(id) != null) {
                    Toast.makeText(this, "Block ID already exists", Toast.LENGTH_SHORT).show();
                    return;
                }
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
            String color = etWidgetColor.getText().toString().trim();

            if (tag.isEmpty() || color.isEmpty()) {
                Toast.makeText(this, "Please fill in all widget fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validate hex color format
            if (!color.startsWith("#") || (color.length() != 4 && color.length() != 7 && color.length() != 9)) {
                Toast.makeText(this, "Invalid hex color code (e.g. #FFBB33)", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validate color parsing
            try {
                Color.parseColor(color);
            } catch (Exception e) {
                Toast.makeText(this, "Invalid hex color code format", Toast.LENGTH_SHORT).show();
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

            // Verify uniqueness of name when creating new
            if (!isEditMode) {
                for (HashMap<String, Object> w : widgetRegistry.getAllWidgets()) {
                    if (id.equalsIgnoreCase(String.valueOf(w.get("name")))) {
                        Toast.makeText(this, "Widget name already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }

            HashMap<String, Object> newWidget = new HashMap<>();
            newWidget.put("name", id);
            newWidget.put("tag", tag);
            newWidget.put("category", category);
            newWidget.put("color", color);
            newWidget.put("function", functionMap);

            widgetRegistry.updateOrAddWidget(newWidget);
            Toast.makeText(this, "Widget saved", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
