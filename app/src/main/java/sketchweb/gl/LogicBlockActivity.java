package sketchweb.gl;

import android.content.ClipData;
import android.content.ClipDescription;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Separate activity for Logic Block editing with puzzle-like block design,
 * Material 3 expressive styling, and Sketchware-Pro-like spinner selection.
 */
public class LogicBlockActivity extends AppCompatActivity {

    // Block categories
    private static final String CAT_EVENT = "event";
    private static final String CAT_CSS = "css";
    private static final String CAT_HTML = "html";
    private static final String CAT_LOGIC = "logic";
    private static final String CAT_VARIABLE = "variable";

    // Category colors
    private static final int COLOR_EVENT = Color.parseColor("#FF9800");
    private static final int COLOR_CSS = Color.parseColor("#2196F3");
    private static final int COLOR_HTML = Color.parseColor("#4CAF50");
    private static final int COLOR_LOGIC = Color.parseColor("#E91E63");
    private static final int COLOR_VARIABLE = Color.parseColor("#00BCD4");

    private LogicBlockManager logicBlockManager;
    private String projectId;
    private String pageName = "index";

    // Views
    private MaterialToolbar toolbar;
    private Spinner spnTargetMode;
    private AutoCompleteTextView etTargetSelector;
    private TabLayout tabCategories;
    private LinearLayout blockPaletteContainer;
    private LinearLayout blockWorkspace;
    private Button btnBlockUndo, btnBlockRedo, btnBlockViewJs, btnBlockAdd;
    private Button btnBlockImport, btnBlockExport;
    private TextView tvBlockCount;

    private String currentCategory = CAT_EVENT;

    // Undo/redo
    private List<String> undoStack = new ArrayList<>();
    private List<String> redoStack = new ArrayList<>();
    private static final int MAX_UNDO = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_logic_block);

        projectId = getIntent().getStringExtra("project_id");
        if (projectId == null) projectId = "";

        pageName = getIntent().getStringExtra("page_name");
        if (pageName == null || pageName.isEmpty()) pageName = "index";

        logicBlockManager = new LogicBlockManager(this);

        // Load existing blocks – swallow any error so a corrupt file can't
        // crash the activity at startup. fromJson itself rejects mismatched
        // payloads (e.g. custom-block library JSON) and supplies non-null
        // defaults for the fields the renderer relies on.
        try {
            File dir = new File(getFilesDir(), "projects");
            File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
            if (logicFile.exists()) {
                String json = FileUtil.readFile(logicFile.getAbsolutePath());
                if (json != null && !json.isEmpty()) {
                    logicBlockManager.fromJson(json);
                }
            }
        } catch (Exception e) {
            Log.w("LogicBlockActivity", "Could not load logic blocks: " + e.getMessage());
        }

        initViews();
        setupToolbar();
        setupTargetSelector();
        setupSelectorAutocomplete();
        setupCategoryTabs();
        setupToolbarButtons();
        setupWorkspaceDragDrop();

        showCategory(CAT_EVENT);
        refreshWorkspace();
        saveUndoState();

        final int toolbarInitialTop = toolbar != null ? toolbar.getPaddingTop() : 0;
        final int workspaceInitialBottom = blockWorkspace != null ? blockWorkspace.getPaddingBottom() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            if (toolbar != null) {
                toolbar.setPadding(toolbar.getPaddingLeft(), toolbarInitialTop + systemBars.top, toolbar.getPaddingRight(), toolbar.getPaddingBottom());
            }
            
            // Also handle bottom insets for the workspace
            if (blockWorkspace != null) {
                 blockWorkspace.setPadding(blockWorkspace.getPaddingLeft(), blockWorkspace.getPaddingTop(), blockWorkspace.getPaddingRight(), workspaceInitialBottom + systemBars.bottom);
            }

            // Apply side paddings to the main root view
            v.setPadding(systemBars.left, 0, systemBars.right, 0);

            return insets;
        });
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbarLogic);
        spnTargetMode = findViewById(R.id.spnTargetMode);
        // etTargetSelector is an AutoCompleteTextView in layout XML, not TextInputEditText
        etTargetSelector = findViewById(R.id.etTargetSelector);
        tabCategories = findViewById(R.id.tabBlockCategories);
        blockPaletteContainer = findViewById(R.id.blockPaletteContainer);
        blockWorkspace = findViewById(R.id.blockWorkspace);
        btnBlockUndo = findViewById(R.id.btnBlockUndo);
        btnBlockRedo = findViewById(R.id.btnBlockRedo);
        btnBlockViewJs = findViewById(R.id.btnBlockViewJs);
        btnBlockAdd = findViewById(R.id.btnBlockAdd);
        btnBlockImport = findViewById(R.id.btnBlockImport);
        btnBlockExport = findViewById(R.id.btnBlockExport);
        tvBlockCount = findViewById(R.id.tvBlockCount);
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> {
            saveAndFinish();
        });
    }

    private void setupTargetSelector() {
        String[] modes = {"By ID", "By Class", "By Tag"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, modes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnTargetMode.setAdapter(adapter);
    }

    private void setupSelectorAutocomplete() {
        List<String> suggestions = new ArrayList<>();
        try {
            File pageFile = new File(getFilesDir(), "projects/" + projectId + "_" + pageName + ".json");
            if (!pageFile.exists()) {
                pageFile = new File(getFilesDir(), "projects/" + projectId + ".json");
            }
            if (pageFile.exists()) {
                String json = FileUtil.readFile(pageFile.getAbsolutePath());
                List<java.util.Map<String, Object>> tree = new com.google.gson.Gson().fromJson(
                    json,
                    new com.google.gson.reflect.TypeToken<List<java.util.Map<String, Object>>>(){}.getType()
                );
                collectSelectorSuggestions(tree, suggestions);
            }
        } catch (Exception e) {
            Log.w("LogicBlockActivity", "Could not build selector autocomplete: " + e.getMessage());
        }
        ArrayAdapter<String> acAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            suggestions
        );
        etTargetSelector.setAdapter(acAdapter);
    }

    @SuppressWarnings("unchecked")
    private void collectSelectorSuggestions(List<java.util.Map<String, Object>> tree, List<String> out) {
        if (tree == null) return;
        for (java.util.Map<String, Object> node : tree) {
            Object fnObj = node.get("function");
            if (fnObj instanceof java.util.Map) {
                java.util.Map<String, Object> fn = (java.util.Map<String, Object>) fnObj;
                Object idObj = fn.get("id");
                if (idObj != null) {
                    String id = idObj.toString().trim();
                    if (!id.isEmpty()) {
                        out.add(id);
                    }
                }
                Object classObj = fn.get("class");
                if (classObj != null) {
                    String[] parts = classObj.toString().trim().split("\\s+");
                    for (String part : parts) {
                        if (!part.isEmpty()) out.add(part);
                    }
                }
            }
            Object childrenObj = node.get("children");
            if (childrenObj instanceof List) {
                collectSelectorSuggestions((List<java.util.Map<String, Object>>) childrenObj, out);
            }
        }
    }

    private String getTargetMode() {
        int pos = spnTargetMode.getSelectedItemPosition();
        switch (pos) {
            case 0: return "id";
            case 1: return "class";
            case 2: return "tag";
            default: return "id";
        }
    }

    private String getTargetValue() {
        return etTargetSelector.getText() != null ? etTargetSelector.getText().toString().trim() : "";
    }

    private void setupCategoryTabs() {
        tabCategories.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0: showCategory(CAT_EVENT); break;
                    case 1: showCategory(CAT_CSS); break;
                    case 2: showCategory(CAT_LOGIC); break;
                    case 3: showCategory(CAT_VARIABLE); break;
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupToolbarButtons() {
        btnBlockUndo.setOnClickListener(v -> undo());
        btnBlockRedo.setOnClickListener(v -> redo());
        btnBlockViewJs.setOnClickListener(v -> showJsPreview());
        btnBlockAdd.setOnClickListener(v -> showAddBlockDialog());
        if (btnBlockImport != null) btnBlockImport.setOnClickListener(v -> showImportDialog());
        if (btnBlockExport != null) btnBlockExport.setOnClickListener(v -> showExportDialog());
    }

    private void setupWorkspaceDragDrop() {
        blockWorkspace.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
                case DragEvent.ACTION_DRAG_ENTERED:
                    setWorkspaceHighlight(true);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    setWorkspaceHighlight(false);
                    return true;
                case DragEvent.ACTION_DROP:
                    setWorkspaceHighlight(false);
                    Object localState = event.getLocalState();
                    if (localState instanceof BlockDef) {
                        BlockDef def = (BlockDef) localState;
                        addBlockFromDef(def);
                    }
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    setWorkspaceHighlight(false);
                    return true;
            }
            return false;
        });
    }

    private void setWorkspaceHighlight(boolean highlight) {
        if (highlight) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(16);
            bg.setColor(Color.parseColor("#0D2196F3"));
            bg.setStroke(2, Color.parseColor("#2196F3"));
            blockWorkspace.setBackground(bg);
        } else {
            blockWorkspace.setBackground(null);
        }
    }

    // ---- Block Palette ----

    private void showCategory(String category) {
        currentCategory = category;
        blockPaletteContainer.removeAllViews();

        BlockDef[] blocks = getBlocksForCategory(category);
        int color = getCategoryColor(category);

        for (BlockDef def : blocks) {
            blockPaletteContainer.addView(createPuzzleBlock(def, color));
        }
    }

    private View createPuzzleBlock(BlockDef def, int baseColor) {
        // Sketchware-style compact palette pill: small colored chip with the
        // block label, drag/tap to add to workspace.
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(12), dp(8), dp(12), dp(10));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), dp(2), dp(4), dp(4));
        block.setLayoutParams(params);

        // Puzzle pill background with subtle notch on the left
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadii(new float[]{
            dp(4), dp(4),
            dp(8), dp(8),
            dp(8), dp(8),
            dp(4), dp(4)
        });
        bg.setColor(baseColor);
        bg.setStroke(dp(1), darken(baseColor));
        block.setBackground(bg);
        block.setElevation(2);

        TextView nameText = new TextView(this);
        nameText.setText(def.label);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(12);
        nameText.setTypeface(null, Typeface.BOLD);
        block.addView(nameText);

        TextView descText = new TextView(this);
        descText.setText(def.description);
        descText.setTextColor(Color.parseColor("#E1F5FE"));
        descText.setTextSize(10);
        descText.setPadding(0, dp(2), 0, 0);
        block.addView(descText);

        block.setOnLongClickListener(v -> {
            ClipData.Item item = new ClipData.Item(def.id + "|" + def.category);
            ClipData dragData = new ClipData("block", new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            v.startDragAndDrop(dragData, shadow, def, 0);
            return true;
        });
        block.setOnClickListener(v -> addBlockFromDef(def));

        return block;
    }

    // ---- Add Block Dialogs (Spinner-based) ----

    private void showAddBlockDialog() {
        // Use spinners instead of dialogs for selection (Sketchware-Pro style)
        String target = getTargetValue();
        String targetMode = getTargetMode();

        if (CAT_LOGIC.equals(currentCategory) || CAT_VARIABLE.equals(currentCategory)) {
            // Logic and Variable blocks don't need target
            showBlockPickerSpinner(currentCategory);
        } else {
            if (target.isEmpty()) {
                Toast.makeText(this, "Enter a target selector first", Toast.LENGTH_SHORT).show();
                etTargetSelector.requestFocus();
                return;
            }
            showBlockPickerSpinner(currentCategory);
        }
    }

    private void showBlockPickerSpinner(String category) {
        BlockDef[] blocks = getBlocksForCategory(category);
        String[] labels = new String[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            labels[i] = blocks[i].label + " - " + blocks[i].description;
        }

        // Event spinner selection
        if (CAT_EVENT.equals(category)) {
            // Select event, then action
            new MaterialAlertDialogBuilder(this)
                .setTitle("Select Event")
                .setItems(labels, (dialog, which) -> {
                    BlockDef eventDef = blocks[which];
                    showActionPickerForEvent(eventDef);
                })
                .setNegativeButton("Cancel", null)
                .show();
        } else if (CAT_LOGIC.equals(category) || CAT_VARIABLE.equals(category)) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Select Block")
                .setItems(labels, (dialog, which) -> {
                    addBlockFromDef(blocks[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            // CSS/HTML action - select action, then event
            new MaterialAlertDialogBuilder(this)
                .setTitle("Select Action")
                .setItems(labels, (dialog, which) -> {
                    BlockDef actionDef = blocks[which];
                    showEventPickerForAction(actionDef);
                })
                .setNegativeButton("Cancel", null)
                .show();
        }
    }

    /**
     * Show only CSS actions for a chosen event - HTML category was removed.
     * The dialog is kept as a fallback for accessibility; primary UX is
     * drag-and-drop CSS rectangles into the C-shaped event slot.
     */
    private void showActionPickerForEvent(BlockDef eventDef) {
        BlockDef[] cssBlocks = getBlocksForCategory(CAT_CSS);
        String[] labels = new String[cssBlocks.length];
        for (int i = 0; i < cssBlocks.length; i++) {
            labels[i] = cssBlocks[i].label + " - " + cssBlocks[i].description;
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("Action for " + eventDef.label)
            .setItems(labels, (dialog, which) -> {
                showValueInputForBlock(eventDef, cssBlocks[which]);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * For a CSS rule dropped without an explicit event, default it to
     * page-load so it emits as a static CSS rule in <style>.
     */
    private void showEventPickerForAction(BlockDef actionDef) {
        BlockDef eventDef = new BlockDef("load", "On Load", "Apply as CSS rule on page load", CAT_EVENT);
        showValueInputForBlock(eventDef, actionDef);
    }

    private void showValueInputForBlock(BlockDef eventDef, BlockDef actionDef) {
        String hint = getValueHint(actionDef);
        // Use the universal value+unit dialog when the action is a CSS sizing
        // value (px/rem/% friendly). For colors / strings, no unit chips are shown.
        boolean wantsUnit = isSizingAction(actionDef.id);
        boolean wantsColor = isColorAction(actionDef.id);

        showUniversalValueDialog(actionDef.label, hint, "", wantsUnit, wantsColor, value -> {
            createBlock(eventDef, actionDef, value);
        });
    }

    /**
     * Universal Sketchware-style value editor:
     *   [ EditText ] [ px ] [ rem ] [ % ]   for sizes
     *   [ ColorPickerHexEditText ]          for colors
     *   [ EditText ]                        for plain strings
     *
     * @param onCommit called with the final stringified value (e.g. "16px")
     */
    private void showUniversalValueDialog(String title, String hint, String initialValue,
                                          boolean wantsUnit, boolean wantsColor,
                                          ValueCommitListener onCommit) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), 0);

        // Numeric / value field
        TextInputLayout til = createTil(hint != null ? hint : "Value");
        TextInputEditText input = (TextInputEditText) til.getEditText();
        if (input != null && initialValue != null) {
            // Strip a trailing unit from initialValue if it matches one of the chips
            String stripped = stripUnit(initialValue);
            input.setText(stripped);
            input.setSelection(input.getText().length());
        }
        layout.addView(til);

        // Unit chip row (only shown when sizes are expected)
        final String[] unitChips = {"px", "rem", "%", "em", "vw", "vh", "auto", "0"};
        final String[] selectedUnit = { detectUnit(initialValue, "px") };
        if (wantsUnit) {
            android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout chipRow = new LinearLayout(this);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            chipRow.setPadding(0, dp(8), 0, dp(4));

            for (String unit : unitChips) {
                TextView chip = makeUnitChip(unit, unit.equals(selectedUnit[0]));
                chip.setOnClickListener(v -> {
                    selectedUnit[0] = unit;
                    for (int i = 0; i < chipRow.getChildCount(); i++) {
                        View child = chipRow.getChildAt(i);
                        if (child instanceof TextView) {
                            String t = ((TextView) child).getText().toString();
                            styleUnitChip((TextView) child, t.equals(unit));
                        }
                    }
                });
                chipRow.addView(chip);
            }
            hsv.addView(chipRow);
            layout.addView(hsv);
        }

        // Color preview (only for color actions)
        if (wantsColor && input != null) {
            input.setHint("#FF6B35 or red");
            TextView preview = new TextView(this);
            preview.setHeight(dp(28));
            preview.setBackgroundColor(parseColorSafe(initialValue, Color.LTGRAY));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
            lp.setMargins(0, dp(8), 0, 0);
            preview.setLayoutParams(lp);
            layout.addView(preview);

            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    preview.setBackgroundColor(parseColorSafe(s.toString(), Color.LTGRAY));
                }
            });
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("OK", (d, w) -> {
                String v = input != null && input.getText() != null
                    ? input.getText().toString().trim() : "";
                if (wantsUnit && !v.isEmpty()) {
                    if (!hasAnyUnit(v) && !"auto".equals(selectedUnit[0]) && !"0".equals(selectedUnit[0])) {
                        v = v + selectedUnit[0];
                    } else if ("auto".equals(selectedUnit[0])) {
                        v = "auto";
                    }
                }
                onCommit.onCommit(v);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    interface ValueCommitListener { void onCommit(String value); }

    private TextView makeUnitChip(String text, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(12);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        styleUnitChip(chip, selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    private void styleUnitChip(TextView chip, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (selected) {
            bg.setColor(COLOR_CSS);
            chip.setTextColor(Color.WHITE);
            chip.setTypeface(null, Typeface.BOLD);
        } else {
            bg.setColor(Color.parseColor("#11000000"));
            bg.setStroke(dp(1), Color.parseColor("#33000000"));
            chip.setTextColor(Color.parseColor("#0D47A1"));
            chip.setTypeface(null, Typeface.NORMAL);
        }
        chip.setBackground(bg);
    }

    private String detectUnit(String value, String fallback) {
        if (value == null) return fallback;
        String[] units = {"px", "rem", "%", "em", "vw", "vh"};
        for (String u : units) if (value.endsWith(u)) return u;
        if ("auto".equals(value)) return "auto";
        return fallback;
    }

    private String stripUnit(String value) {
        if (value == null) return "";
        String[] units = {"px", "rem", "%", "em", "vw", "vh"};
        for (String u : units) {
            if (value.endsWith(u)) return value.substring(0, value.length() - u.length());
        }
        return value;
    }

    private boolean hasAnyUnit(String value) {
        if (value == null) return false;
        String[] units = {"px", "rem", "%", "em", "vw", "vh", "auto"};
        for (String u : units) if (value.endsWith(u)) return true;
        return false;
    }

    private boolean isSizingAction(String id) {
        if (id == null) return false;
        switch (id) {
            case "setWidth": case "setHeight": case "setMargin": case "setPadding":
            case "setRadius": case "setFontSize":
                return true;
            default: return false;
        }
    }

    private boolean isColorAction(String id) {
        if (id == null) return false;
        switch (id) {
            case "setColor": case "setBackground": case "setBorder":
                return true;
            default: return false;
        }
    }

    private int parseColorSafe(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            String s = hex.trim();
            if (!s.startsWith("#")) {
                // try named colors via Color.parseColor
                return Color.parseColor(s);
            }
            return Color.parseColor(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    // ---- Block Creation ----

    private void addBlockFromDef(BlockDef def) {
        if (CAT_LOGIC.equals(def.category)) {
            showLogicBlockInput(def);
        } else if (CAT_VARIABLE.equals(def.category)) {
            showVariableBlockInput(def);
        } else if (CAT_EVENT.equals(def.category)) {
            String target = getTargetValue();
            if (target.isEmpty()) {
                Toast.makeText(this, "Enter a target selector first", Toast.LENGTH_SHORT).show();
                return;
            }
            showActionPickerForEvent(def);
        } else {
            // CSS/HTML action
            String target = getTargetValue();
            if (target.isEmpty()) {
                Toast.makeText(this, "Enter a target selector first", Toast.LENGTH_SHORT).show();
                return;
            }
            showEventPickerForAction(def);
        }
    }

    private void createBlock(BlockDef eventDef, BlockDef actionDef, String value) {
        saveUndoState();

        String target = getTargetValue();
        String targetMode = getTargetMode();
        String eventKey = mapEventKey(eventDef.id);
        String actionKey = mapActionKey(actionDef.id);
        String params = mapParams(actionDef.id, value);

        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
        block.targetWidget = target;
        block.targetMode = targetMode;
        block.event = eventKey;
        block.action = actionKey;
        block.params = params;

        logicBlockManager.addBlock(block);
        refreshWorkspace();
        Toast.makeText(this, "Block added", Toast.LENGTH_SHORT).show();
    }

    private void showLogicBlockInput(BlockDef def) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        switch (def.id) {
            case "ifBlock":
            case "ifElseBlock": {
                TextInputLayout tilLeft = createTil("Left value");
                TextInputEditText inputLeft = (TextInputEditText) tilLeft.getEditText();
                layout.addView(tilLeft);

                TextInputLayout tilOp = createTil("Operator (==, !=, >, <)");
                TextInputEditText inputOp = (TextInputEditText) tilOp.getEditText();
                if (inputOp != null) inputOp.setText("==");
                layout.addView(tilOp);

                TextInputLayout tilRight = createTil("Right value");
                layout.addView(tilRight);

                TextInputLayout tilAction = createTil("Then action (JS)");
                layout.addView(tilAction);

                TextInputLayout tilElse = null;
                if ("ifElseBlock".equals(def.id)) {
                    tilElse = createTil("Else action (JS)");
                    layout.addView(tilElse);
                }

                final TextInputLayout finalTilElse = tilElse;
                new MaterialAlertDialogBuilder(this)
                    .setTitle(def.label)
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "logic";
                        block.event = "immediate";
                        block.action = def.id;
                        String left = getText(tilLeft);
                        String op = getText(tilOp);
                        String right = getText(tilRight);
                        String action = getText(tilAction);
                        String elseAction = finalTilElse != null ? getText(finalTilElse) : "";
                        block.params = left + "|" + op + "|" + right + "|" + action
                            + (elseAction.isEmpty() ? "" : "|" + elseAction);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "delay": {
                TextInputLayout tilMs = createTil("Delay (ms)");
                TextInputEditText inputMs = (TextInputEditText) tilMs.getEditText();
                if (inputMs != null) inputMs.setText("1000");
                layout.addView(tilMs);

                TextInputLayout tilAction = createTil("Action after delay (JS)");
                layout.addView(tilAction);

                new MaterialAlertDialogBuilder(this)
                    .setTitle("Delay")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "logic";
                        block.event = "immediate";
                        block.action = "delay";
                        block.params = getText(tilMs) + "|" + getText(tilAction);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "loop": {
                TextInputLayout tilCount = createTil("Iterations");
                TextInputEditText inputCount = (TextInputEditText) tilCount.getEditText();
                if (inputCount != null) inputCount.setText("5");
                layout.addView(tilCount);

                TextInputLayout tilAction = createTil("Action per iteration (JS, use 'i' for index)");
                layout.addView(tilAction);

                new MaterialAlertDialogBuilder(this)
                    .setTitle("Loop")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "logic";
                        block.event = "immediate";
                        block.action = "loop";
                        block.params = getText(tilCount) + "|" + getText(tilAction);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            default: {
                // Comparison blocks
                String op = "==";
                if ("compareNotEqual".equals(def.id)) op = "!=";
                else if ("compareGreater".equals(def.id)) op = ">";
                else if ("compareLess".equals(def.id)) op = "<";

                TextInputLayout tilLeft = createTil("Left value");
                layout.addView(tilLeft);
                TextInputLayout tilRight = createTil("Right value");
                layout.addView(tilRight);
                TextInputLayout tilAction = createTil("Action if true (JS)");
                layout.addView(tilAction);

                final String finalOp = op;
                new MaterialAlertDialogBuilder(this)
                    .setTitle(def.label)
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "logic";
                        block.event = "immediate";
                        block.action = "ifBlock";
                        block.params = getText(tilLeft) + "|" + finalOp + "|" + getText(tilRight) + "|" + getText(tilAction);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
        }
    }

    private void showVariableBlockInput(BlockDef def) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        switch (def.id) {
            case "createVar":
            case "createVarString":
            case "createVarNumber":
            case "createVarBoolean": {
                TextInputLayout tilName = createTil("Variable name");
                layout.addView(tilName);
                String type = "createVarString".equals(def.id) ? "string" :
                    "createVarNumber".equals(def.id) ? "number" :
                    "createVarBoolean".equals(def.id) ? "boolean" : "any";
                TextInputLayout tilValue = createTil("Initial value (" + type + ")");
                layout.addView(tilValue);

                new MaterialAlertDialogBuilder(this)
                    .setTitle(def.label)
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "variable";
                        block.event = "immediate";
                        block.action = "createVar";
                        block.params = getText(tilName) + "|" + type + "|" + getText(tilValue);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "setVar": {
                TextInputLayout tilName = createTil("Variable name");
                layout.addView(tilName);
                TextInputLayout tilValue = createTil("New value");
                layout.addView(tilValue);

                new MaterialAlertDialogBuilder(this)
                    .setTitle("Set Variable")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "variable";
                        block.event = "immediate";
                        block.action = "setVar";
                        block.params = getText(tilName) + "|" + getText(tilValue);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "getVar": {
                TextInputLayout tilName = createTil("Variable name");
                layout.addView(tilName);
                TextInputLayout tilTarget = createTil("Assign to element (selector, or empty for console.log)");
                layout.addView(tilTarget);

                new MaterialAlertDialogBuilder(this)
                    .setTitle("Get Variable")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "variable";
                        block.event = "immediate";
                        block.action = "getVar";
                        block.params = getText(tilName) + "|" + getText(tilTarget);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
        }
    }

    // ---- Workspace Rendering (Puzzle Blocks) ----

    private void refreshWorkspace() {
        blockWorkspace.removeAllViews();

        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (tvBlockCount != null) {
            tvBlockCount.setText(blocks.size() + " block" + (blocks.size() == 1 ? "" : "s"));
        }
        if (blocks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Drag a CSS or Logic block here.\nEvents are C-shapes that wrap actions.");
            empty.setTextColor(Color.parseColor("#7A8B9C"));
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(32, 80, 32, 80);
            blockWorkspace.addView(empty);
            return;
        }

        // Group consecutive blocks by event into a C-shaped wrapper.
        // Event = orange C-shape (header cap + left rail + bottom cap) wrapping
        //         the rectangular CSS / Logic blocks that belong to it.
        String currentEvent = null;
        LinearLayout currentSlot = null;
        for (int i = 0; i < blocks.size(); i++) {
            LogicBlockManager.LogicBlock block = blocks.get(i);
            String ev = block.event != null ? block.event : "immediate";
            boolean isLogicGroup = "logic".equals(block.targetMode) || "variable".equals(block.targetMode);

            if (!ev.equals(currentEvent)) {
                // Close previous C-shape with a bottom cap
                if (currentSlot != null) {
                    blockWorkspace.addView(createEventBottomCap(currentEvent));
                }
                // Open a new C-shape: orange header, then a vertical slot
                // that holds rectangular action blocks.
                blockWorkspace.addView(createEventHeaderBlock(ev));
                // createEventSlot adds its wrapper to blockWorkspace itself
                // and returns the inner stack we should populate.
                currentSlot = createEventSlot(ev);
                currentEvent = ev;
            }

            if (isLogicGroup) {
                currentSlot.addView(createWorkspacePuzzleBlock(block, i));
            } else {
                currentSlot.addView(createWorkspacePuzzleBlock(block, i));
            }
        }
        if (currentSlot != null) {
            blockWorkspace.addView(createEventBottomCap(currentEvent));
        }
    }

    /**
     * Vertical container with an orange left rail — visually completes the
     * "C" shape between the event header and bottom cap. Children are the
     * rectangular CSS / Logic action blocks.
     */
    private LinearLayout createEventSlot(String eventKey) {
        LinearLayout slot = new LinearLayout(this);
        slot.setOrientation(LinearLayout.VERTICAL);
        slot.setPadding(dp(14), dp(4), dp(8), dp(4));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(adjustAlpha(COLOR_EVENT, 28));
        bg.setStroke(0, Color.TRANSPARENT);
        slot.setBackground(bg);

        // Left rail (the vertical bar of the C)
        // Implemented via a layered drawable using setBackground on the slot
        // wouldn't give us a thick left rail, so instead we add a vertical
        // colored View as the first child of a horizontal wrapper.
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);

        View rail = new View(this);
        GradientDrawable railBg = new GradientDrawable();
        railBg.setColor(COLOR_EVENT);
        rail.setBackground(railBg);
        LinearLayout.LayoutParams railLp = new LinearLayout.LayoutParams(dp(6),
            ViewGroup.LayoutParams.MATCH_PARENT);
        railLp.setMargins(dp(4), 0, 0, 0);
        rail.setLayoutParams(railLp);
        wrapper.addView(rail);

        // Inner stack
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(8), dp(2), dp(4), dp(2));
        LinearLayout.LayoutParams innerLp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inner.setLayoutParams(innerLp);
        wrapper.addView(inner);

        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapLp.setMargins(dp(4), 0, dp(4), 0);
        wrapper.setLayoutParams(wrapLp);

        // The slot variable returned to caller is the inner stack so further
        // addView calls populate the children of the C.
        blockWorkspace.addView(wrapper);
        return inner;
    }

    /**
     * Bottom cap of the C-shape. Mirrors the orange header so the wrapping
     * around the slot looks closed.
     */
    private View createEventBottomCap(String eventKey) {
        View cap = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadii(new float[]{0, 0, dp(10), dp(10), dp(10), dp(10), 0, 0});
        bg.setColor(COLOR_EVENT);
        cap.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(60), dp(8));
        lp.setMargins(dp(4), 0, 0, dp(8));
        cap.setLayoutParams(lp);
        return cap;
    }

    /**
     * Renders a Sketchware-style orange header block, eg.
     * "On Click" / "On Page Load" / "Immediate".
     */
    private View createEventHeaderBlock(String eventKey) {
        String label = LogicBlockManager.getEventDisplayName(eventKey);

        TextView header = new TextView(this);
        header.setText(label);
        header.setTextColor(Color.WHITE);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextSize(13);
        header.setPadding(dp(14), dp(8), dp(14), dp(10));

        GradientDrawable bg = new GradientDrawable();
        // Tab on top-left, slight curve elsewhere — matches Sketchware "event" cap.
        bg.setCornerRadii(new float[]{dp(10), dp(10), dp(10), dp(10), dp(10), dp(10), 0, 0});
        bg.setColor(COLOR_EVENT);
        header.setBackground(bg);
        header.setElevation(2);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), dp(8), dp(4), 0);
        header.setLayoutParams(params);
        return header;
    }

    /**
     * Render a single block as a horizontal Sketchware-style "puzzle" row:
     *
     *   [target chip]  verb  [param chip]  verb  [param chip] ...
     *
     * Each block stacks immediately below the previous one with no margin
     * so the chain looks connected.
     */
    private View createWorkspacePuzzleBlock(LogicBlockManager.LogicBlock block, int index) {
        // Defensive: render even when a block was loaded from a malformed file.
        if (block.targetMode == null) block.targetMode = LogicBlockManager.TARGET_MODE_ID;
        if (block.targetWidget == null) block.targetWidget = "";
        if (block.event == null) block.event = "immediate";
        if (block.action == null) block.action = "";
        if (block.params == null) block.params = "";

        boolean isLogic = "logic".equals(block.targetMode);
        boolean isVar = "variable".equals(block.targetMode);

        int baseColor;
        if (isLogic) baseColor = COLOR_LOGIC;
        else if (isVar) baseColor = COLOR_VARIABLE;
        else baseColor = COLOR_CSS; // The blue Sketchware-style action body
        int strokeColor = darken(baseColor);

        // Outer puzzle row (horizontal flow with wrap-around fallback if needed)
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(10), dp(9));
        row.setBaselineAligned(false);

        GradientDrawable bg = new GradientDrawable();
        if (isLogic) {
            // Logic = E-shape: rounded but flatter on the left so it visually
            // pairs with the C-shape event around it.
            bg.setCornerRadii(new float[]{
                dp(2), dp(2),     // top-left
                dp(8), dp(8),     // top-right
                dp(8), dp(8),     // bottom-right
                dp(2), dp(2)      // bottom-left
            });
        } else {
            // CSS / Var = plain rectangle that snaps into a slot.
            bg.setCornerRadius(dp(4));
        }
        bg.setColor(baseColor);
        bg.setStroke(dp(1), strokeColor);
        row.setBackground(bg);
        row.setElevation(2);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(2), 0, dp(2));
        row.setLayoutParams(rowParams);

        if (isLogic || isVar) {
            // Logic / Variable rows - leading category chip (NOT editable verb)
            // followed by editable param chips.
            String catLabel = isLogic ? getLogicLabel(block.action) : getVarLabel(block.action);
            row.addView(createVerbChip(catLabel, darken(baseColor)));
            String[] parts = (block.params != null ? block.params : "").split("\\|");
            for (int p = 0; p < parts.length; p++) {
                if (parts[p] == null) continue;
                String trimmed = parts[p].trim();
                if (trimmed.isEmpty()) continue;
                final int paramIdx = p;
                TextView chip = createValueChip(trimmed);
                chip.setOnClickListener(v -> editParamPart(index, paramIdx));
                row.addView(chip);
            }

            // If this is an if-else logic block, render a second slot ("else")
            // below it to give the block its E-shape silhouette. This is
            // visual only - the params already encode the else branch.
            if ("ifElseBlock".equals(block.action)) {
                LinearLayout column = new LinearLayout(this);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                column.addView(row);

                TextView elseTab = new TextView(this);
                elseTab.setText("else");
                elseTab.setTextColor(Color.WHITE);
                elseTab.setTextSize(11);
                elseTab.setTypeface(null, Typeface.BOLD);
                elseTab.setPadding(dp(12), dp(4), dp(12), dp(4));
                GradientDrawable elseBg = new GradientDrawable();
                elseBg.setCornerRadii(new float[]{0, 0, dp(6), dp(6), dp(6), dp(6), 0, 0});
                elseBg.setColor(darken(COLOR_LOGIC));
                elseTab.setBackground(elseBg);
                LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tabLp.setMargins(dp(20), dp(2), 0, dp(2));
                elseTab.setLayoutParams(tabLp);
                column.addView(elseTab);

                return column;
            }
        } else {
            // Element-targeted action row:  [target] verb [value] [extra value]
            String modePrefix = "id".equals(block.targetMode) ? "#"
                : "class".equals(block.targetMode) ? "." : "";
            String targetText = modePrefix + block.targetWidget;
            // Target chip is EDITABLE - tap opens id/class/tag picker.
            TextView targetChip = createTargetChip(
                targetText.isEmpty() ? "page" : targetText,
                Color.parseColor("#3D5AFE"));
            targetChip.setOnClickListener(v -> showTargetPickerDialog(
                block.targetMode, block.targetWidget,
                (mode, value) -> {
                    saveUndoState();
                    block.targetMode = mode;
                    block.targetWidget = value;
                    refreshWorkspace();
                }));
            row.addView(targetChip);

            // Verb is NOT editable - just a label between chips.
            row.addView(createVerb(getActionVerb(block.action)));

            // Value chips are EDITABLE — tap opens the universal value+unit dialog.
            List<String> chips = extractValueChips(block);
            for (int ci = 0; ci < chips.size(); ci++) {
                String chipText = chips.get(ci);
                if (chipText == null || chipText.isEmpty()) continue;
                final int chipIdx = ci;
                TextView vChip = createValueChip(chipText);
                vChip.setOnClickListener(v -> editValueChip(index, chipIdx));
                row.addView(vChip);
            }
        }

        // Long press = block actions (delete/move/duplicate)
        row.setOnLongClickListener(v -> {
            showBlockActions(index);
            return true;
        });
        // Short tap = edit value
        row.setOnClickListener(v -> showEditBlockDialog(index));

        return row;
    }

    /**
     * Edit a single value chip in a CSS / element-action block. Uses the
     * universal value+unit dialog when the action is sizing/color, otherwise
     * a plain text input.
     */
    private void editValueChip(int blockIndex, int chipIndex) {
        List<LogicBlockManager.LogicBlock> all = logicBlockManager.getBlocks();
        if (blockIndex < 0 || blockIndex >= all.size()) return;
        LogicBlockManager.LogicBlock block = all.get(blockIndex);

        boolean wantsUnit = isSizingActionForParams(block.action);
        boolean wantsColor = isColorActionForParams(block.action);

        // For "changeStyle" / "setAttribute" with two chips (property : value)
        // we only edit the chip the user tapped.
        List<String> chips = extractValueChips(block);
        if (chipIndex < 0 || chipIndex >= chips.size()) return;
        String currentValue = chips.get(chipIndex);

        showUniversalValueDialog(
            getActionVerb(block.action) + " - " + (chipIndex == 0 ? "property" : "value"),
            getValueHint(new BlockDef(block.action, "", "", CAT_CSS)),
            currentValue,
            wantsUnit && chipIndex > 0,
            wantsColor && chipIndex > 0,
            newValue -> {
                saveUndoState();
                chips.set(chipIndex, newValue);
                if (chips.size() == 2) {
                    block.params = chips.get(0) + ":" + chips.get(1);
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < chips.size(); i++) {
                        if (i > 0) sb.append("|");
                        sb.append(chips.get(i));
                    }
                    block.params = sb.toString();
                }
                refreshWorkspace();
            });
    }

    /**
     * Edit a single | -separated param of a logic / variable block.
     */
    private void editParamPart(int blockIndex, int partIndex) {
        List<LogicBlockManager.LogicBlock> all = logicBlockManager.getBlocks();
        if (blockIndex < 0 || blockIndex >= all.size()) return;
        LogicBlockManager.LogicBlock block = all.get(blockIndex);
        String[] parts = (block.params != null ? block.params : "").split("\\|", -1);
        if (partIndex < 0 || partIndex >= parts.length) return;
        String current = parts[partIndex];

        showUniversalValueDialog(
            "Edit value", "Value", current, false, false,
            newVal -> {
                saveUndoState();
                parts[partIndex] = newVal;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) sb.append("|");
                    sb.append(parts[i]);
                }
                block.params = sb.toString();
                refreshWorkspace();
            });
    }

    private boolean isSizingActionForParams(String action) {
        if (action == null) return false;
        // "changeStyle" carries property:value where value may need a unit
        return "changeStyle".equals(action);
    }

    private boolean isColorActionForParams(String action) {
        // changeStyle property "color", "background", "border" want color
        // hint - we surface that via the dialog the second chip when relevant.
        return "changeStyle".equals(action);
    }

    /** Read-only verb chip used as the leading badge for logic/var rows. */
    private TextView createVerbChip(String text, int color) {
        TextView chip = createTargetChip(text, color);
        chip.setClickable(false);
        chip.setFocusable(false);
        return chip;
    }

    /** Coloured rounded chip used for the leading "target" piece of a block. */
    private TextView createTargetChip(String text, int color) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(Color.WHITE);
        chip.setTypeface(null, Typeface.BOLD);
        chip.setTextSize(12);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(6));
        bg.setColor(color);
        chip.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(p);
        return chip;
    }

    /** White-on-blue inline verb (e.g. "set title", "show"). */
    private TextView createVerb(String text) {
        TextView verb = new TextView(this);
        verb.setText(text);
        verb.setTextColor(Color.WHITE);
        verb.setTextSize(13);
        verb.setPadding(0, 0, dp(6), 0);
        return verb;
    }

    /** White rectangular value pill (sketchware-style input look). */
    private TextView createValueChip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(Color.parseColor("#0D47A1"));
        chip.setTextSize(12);
        chip.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(1), Color.parseColor("#33000000"));
        chip.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(p);
        return chip;
    }

    /** Slightly darker version of the colour for puzzle outline / chips. */
    private int darken(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0f, hsv[2] * 0.78f);
        return Color.HSVToColor(hsv);
    }

    /**
     * Convert an action key into a Sketchware-style verb shown between chips.
     */
    private String getActionVerb(String action) {
        if (action == null) return "do";
        switch (action) {
            case "changeStyle": return "set style";
            case "addClass": return "add class";
            case "removeClass": return "remove class";
            case "toggleClass": return "toggle class";
            case "setText": return "set text";
            case "setHTML": return "set html";
            case "showHide": return "visibility";
            case "navigate": return "go to url";
            case "goToPage": return "go to page";
            case "openPage": return "open page";
            case "alert": return "alert";
            case "consoleLog": return "console.log";
            case "setAttribute": return "set attribute";
            case "removeAttribute": return "remove attribute";
            case "setValue": return "set value";
            case "appendChild": return "append";
            case "prependChild": return "prepend";
            case "createElement": return "create element";
            case "removeElement": return "remove";
            case "scrollTo": return "scroll to";
            case "copyClipboard": return "copy clipboard";
            case "delay": return "delay";
            case "focusInput": return "focus";
            case "blurInput": return "blur";
            case "setHref": return "set href";
            case "fetchApi": return "fetch";
            case "localStorage": return "local storage";
            case "customJs": return "run js";
            case "animate": return "animate";
            default: return action;
        }
    }

    /**
     * Split a block's params into one-or-two visible value chips.
     * For "property:value" style params we render them as ["property", "value"]
     * for nicer Sketchware-like puzzle pieces.
     */
    private List<String> extractValueChips(LogicBlockManager.LogicBlock block) {
        List<String> out = new ArrayList<>();
        String params = block.params != null ? block.params : "";
        if (params.isEmpty()) return out;
        if ("changeStyle".equals(block.action) || "setAttribute".equals(block.action)) {
            String[] split = params.split(":", 2);
            for (String s : split) out.add(s.trim());
        } else {
            out.add(params);
        }
        return out;
    }

    private void showBlockActions(int index) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Block Actions")
            .setItems(new String[]{"Delete", "Move Up", "Move Down", "Duplicate"}, (dialog, which) -> {
                List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
                switch (which) {
                    case 0:
                        saveUndoState();
                        logicBlockManager.removeBlock(index);
                        refreshWorkspace();
                        break;
                    case 1:
                        if (index > 0) {
                            saveUndoState();
                            LogicBlockManager.LogicBlock temp = blocks.get(index);
                            blocks.set(index, blocks.get(index - 1));
                            blocks.set(index - 1, temp);
                            refreshWorkspace();
                        }
                        break;
                    case 2:
                        if (index < blocks.size() - 1) {
                            saveUndoState();
                            LogicBlockManager.LogicBlock temp2 = blocks.get(index);
                            blocks.set(index, blocks.get(index + 1));
                            blocks.set(index + 1, temp2);
                            refreshWorkspace();
                        }
                        break;
                    case 3:
                        saveUndoState();
                        LogicBlockManager.LogicBlock orig = blocks.get(index);
                        LogicBlockManager.LogicBlock copy = new LogicBlockManager.LogicBlock();
                        copy.targetWidget = orig.targetWidget;
                        copy.targetMode = orig.targetMode;
                        copy.event = orig.event;
                        copy.action = orig.action;
                        copy.params = orig.params;
                        logicBlockManager.addBlock(copy);
                        refreshWorkspace();
                        break;
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ---- UI Helpers ----

    private LinearLayout createBlockRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 4, 0, 4);
        return row;
    }

    private TextView createBadge(String text, int color) {
        TextView badge = new TextView(this);
        badge.setText(text);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(10);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(12, 4, 12, 4);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10);
        bg.setColor(color);
        badge.setBackground(bg);
        return badge;
    }

    private TextInputLayout createTil(String hint) {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(hint);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(dp(10), dp(10), dp(10), dp(10));
        TextInputEditText input = new TextInputEditText(this);
        input.setMinHeight(dp(44));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        til.addView(input);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 0, 4);
        til.setLayoutParams(params);
        return til;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private String getText(TextInputLayout til) {
        if (til.getEditText() != null && til.getEditText().getText() != null) {
            return til.getEditText().getText().toString().trim();
        }
        return "";
    }

    private int adjustAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    // ---- Undo/Redo ----

    private void saveUndoState() {
        undoStack.add(logicBlockManager.toJson());
        if (undoStack.size() > MAX_UNDO) undoStack.remove(0);
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.size() <= 1) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            return;
        }
        redoStack.add(logicBlockManager.toJson());
        undoStack.remove(undoStack.size() - 1);
        logicBlockManager.fromJson(undoStack.get(undoStack.size() - 1));
        refreshWorkspace();
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to redo", Toast.LENGTH_SHORT).show();
            return;
        }
        undoStack.add(logicBlockManager.toJson());
        String next = redoStack.remove(redoStack.size() - 1);
        logicBlockManager.fromJson(next);
        refreshWorkspace();
    }

    // ---- Target Picker (id / class / tag) ----

    /**
     * Show a Sketchware-style horizontal-chip picker for the target element.
     * The user picks Tag/Class/Id with chips, then types/picks a value with
     * autocomplete sourced from the page tree.
     */
    private void showTargetPickerDialog(String currentMode, String currentValue,
                                        TargetCommitListener listener) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("Selector type");
        modeLabel.setTextSize(12);
        modeLabel.setTextColor(Color.parseColor("#7A8B9C"));
        layout.addView(modeLabel);

        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(0, dp(6), 0, dp(8));

        final String[] modes = {"id", "class", "tag"};
        final String[] selectedMode = { currentMode != null ? currentMode : "id" };

        for (String mode : modes) {
            String label = "id".equals(mode) ? "# ID"
                         : "class".equals(mode) ? ". Class" : "<> Tag";
            TextView chip = makeUnitChip(label, mode.equals(selectedMode[0]));
            chip.setOnClickListener(v -> {
                selectedMode[0] = mode;
                for (int i = 0; i < chipRow.getChildCount(); i++) {
                    View child = chipRow.getChildAt(i);
                    if (child instanceof TextView) {
                        String t = ((TextView) child).getText().toString();
                        boolean isSelected =
                            ("id".equals(mode) && t.contains("ID"))
                            || ("class".equals(mode) && t.contains("Class"))
                            || ("tag".equals(mode) && t.contains("Tag"));
                        styleUnitChip((TextView) child, isSelected);
                    }
                }
            });
            chipRow.addView(chip);
        }
        layout.addView(chipRow);

        TextInputLayout til = createTil("Selector value");
        TextInputEditText input = (TextInputEditText) til.getEditText();
        // Replace the inner view with an autocompleting one
        til.removeView(til.getEditText());
        AutoCompleteTextView ac = new AutoCompleteTextView(this);
        ac.setHint("Selector value");
        ac.setText(currentValue != null ? currentValue : "");
        ac.setCompletionThreshold(1);
        ac.setSingleLine(true);
        ac.setPadding(dp(12), dp(10), dp(12), dp(10));
        ac.setMinHeight(dp(44));
        ac.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_dropdown_item_1line, collectAutocompleteSuggestions()));
        til.addView(ac);
        layout.addView(til);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Pick target element")
            .setView(layout)
            .setPositiveButton("OK", (d, w) -> {
                String value = ac.getText() != null ? ac.getText().toString().trim() : "";
                listener.onCommit(selectedMode[0], value);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    interface TargetCommitListener { void onCommit(String mode, String value); }

    /**
     * Collect id/class/tag suggestions from the persisted page tree so the
     * autocomplete dropdown shows real elements from the user's page.
     */
    private List<String> collectAutocompleteSuggestions() {
        List<String> out = new ArrayList<>();
        try {
            File pageFile = new File(getFilesDir(), "projects/" + projectId + "_" + pageName + ".json");
            if (!pageFile.exists()) {
                pageFile = new File(getFilesDir(), "projects/" + projectId + ".json");
            }
            if (pageFile.exists()) {
                String json = FileUtil.readFile(pageFile.getAbsolutePath());
                List<java.util.Map<String, Object>> tree = new com.google.gson.Gson().fromJson(
                    json,
                    new com.google.gson.reflect.TypeToken<List<java.util.Map<String, Object>>>(){}.getType()
                );
                collectSelectorSuggestions(tree, out);
            }
        } catch (Exception e) {
            Log.w("LogicBlockActivity", "autocomplete: " + e.getMessage());
        }
        return out;
    }

    // ---- Edit existing block ----

    private void showEditBlockDialog(int index) {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (index < 0 || index >= blocks.size()) return;
        LogicBlockManager.LogicBlock block = blocks.get(index);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), 0);

        TextInputLayout tilParams = createTil("Params (use | to separate)");
        TextInputEditText etParams = (TextInputEditText) tilParams.getEditText();
        if (etParams != null) etParams.setText(block.params);
        layout.addView(tilParams);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Edit " + getActionVerb(block.action))
            .setView(layout)
            .setPositiveButton("Save", (d, w) -> {
                saveUndoState();
                block.params = getText(tilParams);
                refreshWorkspace();
            })
            .setNeutralButton("Delete", (d, w) -> {
                saveUndoState();
                logicBlockManager.removeBlock(index);
                refreshWorkspace();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ---- Import / Export generated code ----

    private void showExportDialog() {
        String baseCss = logicBlockManager.generateBaseCssRules();
        String pseudoCss = logicBlockManager.generateCssPseudoRules();
        String js = logicBlockManager.generateJavaScript();
        String json = logicBlockManager.toJson();

        StringBuilder code = new StringBuilder();
        if (baseCss != null && !baseCss.trim().isEmpty()) {
            code.append("<style>\n").append(baseCss).append("</style>\n\n");
        }
        if (pseudoCss != null && !pseudoCss.trim().isEmpty()) {
            code.append("<style>\n").append(pseudoCss).append("</style>\n\n");
        }
        if (js != null && !js.trim().isEmpty()) {
            code.append("<script>\n").append(js).append("</script>\n");
        }
        if (code.length() == 0) code.append("<!-- No logic blocks yet -->\n");

        new MaterialAlertDialogBuilder(this)
            .setTitle("Export")
            .setItems(new String[]{
                "Copy generated CSS + JS",
                "Copy blocks JSON",
                "Save to project export folder"
            }, (dialog, which) -> {
                switch (which) {
                    case 0: copyToClipboard("DragWeb code", code.toString()); break;
                    case 1: copyToClipboard("DragWeb blocks JSON", json); break;
                    case 2: saveToProjectExport(code.toString(), json); break;
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showImportDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), 0);

        TextView hint = new TextView(this);
        hint.setText("Paste blocks JSON exported from DragWeb. Existing blocks will be replaced.");
        hint.setTextSize(12);
        hint.setTextColor(Color.parseColor("#7A8B9C"));
        hint.setPadding(0, 0, 0, dp(8));
        layout.addView(hint);

        TextInputLayout til = createTil("Blocks JSON");
        TextInputEditText input = (TextInputEditText) til.getEditText();
        if (input != null) {
            input.setMinLines(6);
            input.setMaxLines(20);
            input.setGravity(Gravity.TOP | Gravity.START);
        }
        layout.addView(til);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Import")
            .setView(layout)
            .setPositiveButton("Import", (d, w) -> {
                String json = getText(til);
                if (json.isEmpty()) {
                    Toast.makeText(this, "Nothing to import", Toast.LENGTH_SHORT).show();
                    return;
                }
                int before = logicBlockManager.getBlocks().size();
                saveUndoState();
                logicBlockManager.fromJson(json);
                int after = logicBlockManager.getBlocks().size();
                refreshWorkspace();
                Toast.makeText(this, "Imported " + after + " block(s)" + (before > 0 ? " (replaced " + before + ")" : ""), Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("From clipboard", (d, w) -> {
                android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()
                    && cm.getPrimaryClip() != null
                    && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                    if (text != null) {
                        saveUndoState();
                        logicBlockManager.fromJson(text.toString());
                        refreshWorkspace();
                        Toast.makeText(this, "Imported from clipboard", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void copyToClipboard(String label, String text) {
        android.content.ClipboardManager cm =
            (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToProjectExport(String code, String json) {
        try {
            String basePath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId + "/exports";
            File extDir = new File(basePath);
            if (!extDir.exists()) extDir.mkdirs();
            File codeFile = new File(extDir, pageName + "_logic.html");
            File jsonFile = new File(extDir, pageName + "_logic.json");
            FileUtil.writeFile(codeFile.getAbsolutePath(), code);
            FileUtil.writeFile(jsonFile.getAbsolutePath(), json);
            Toast.makeText(this, "Saved to: " + extDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---- JS Preview ----

    private void showJsPreview() {
        String baseCss = logicBlockManager.generateBaseCssRules();
        String pseudoCss = logicBlockManager.generateCssPseudoRules();
        String js = logicBlockManager.generateJavaScript();

        StringBuilder combined = new StringBuilder();
        if (baseCss != null && !baseCss.trim().isEmpty()) {
            combined.append("/* CSS — applied at page load */\n<style>\n");
            combined.append(baseCss);
            combined.append("</style>\n\n");
        }
        if (pseudoCss != null && !pseudoCss.trim().isEmpty()) {
            combined.append("/* CSS pseudo-class rules */\n<style>\n");
            combined.append(pseudoCss);
            combined.append("</style>\n\n");
        }
        if (js != null && !js.trim().isEmpty()) {
            combined.append("/* JavaScript — for runtime events */\n<script>\n");
            combined.append(js);
            combined.append("</script>\n");
        }
        if (combined.length() == 0) combined.append("// No logic blocks yet");

        ScrollView sv = new ScrollView(this);
        sv.setPadding(24, 16, 24, 16);
        TextView tv = new TextView(this);
        tv.setText(combined.toString());
        tv.setTextSize(12);
        tv.setTextColor(Color.parseColor("#A5D6A7"));
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);

        GradientDrawable codeBg = new GradientDrawable();
        codeBg.setCornerRadius(12);
        codeBg.setColor(Color.parseColor("#0A0A14"));
        codeBg.setStroke(1, Color.parseColor("#333355"));
        tv.setBackground(codeBg);
        tv.setPadding(16, 12, 16, 12);
        sv.addView(tv);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Generated CSS / JS")
            .setView(sv)
            .setPositiveButton("Close", null)
            .show();
    }

    // ---- Save and Exit ----

    private void saveAndFinish() {
        File dir = new File(getFilesDir(), "projects");
        if (!dir.exists()) dir.mkdirs();
        String pageName = getIntent().getStringExtra("page_name");
        if (pageName == null || pageName.isEmpty()) pageName = "index";

        File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
        FileUtil.writeFile(logicFile.getAbsolutePath(), logicBlockManager.toJson());

        // Also save to external
        try {
            String basePath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId;
            File extDir = new File(basePath);
            if (!extDir.exists()) extDir.mkdirs();
            FileUtil.writeFile(new File(extDir, pageName + "_logic.json").getAbsolutePath(), logicBlockManager.toJson());
        } catch (Exception e) {
            Log.w("LogicBlockActivity", "Could not save to external: " + e.getMessage());
        }

        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onBackPressed() {
        saveAndFinish();
    }

    // ---- Block Definitions ----

 private BlockDef[] getBlocksForCategory(String category) {
    switch (category) {

        case CAT_EVENT: return new BlockDef[]{
            new BlockDef("onClick", "On Click", "When element is clicked", CAT_EVENT),
            new BlockDef("onHover", "On Hover", "When mouse hovers", CAT_EVENT),
            new BlockDef("onLoad", "On Load", "When page loads", CAT_EVENT),
            new BlockDef("onInput", "On Input", "When input changes", CAT_EVENT),
            new BlockDef("onSubmit", "On Submit", "When form submits", CAT_EVENT),
            new BlockDef("onScroll", "On Scroll", "When user scrolls", CAT_EVENT),
            new BlockDef("onKeyDown", "On Key Down", "When key pressed", CAT_EVENT),
            new BlockDef("onChange", "On Change", "When value changes", CAT_EVENT),
        };

        case CAT_CSS: return new BlockDef[]{
            new BlockDef("setDisplay", "Set Display", "block/none/flex", CAT_CSS),
            new BlockDef("setColor", "Set Color", "Text color", CAT_CSS),
            new BlockDef("setBackground", "Set Background", "Background color", CAT_CSS),
            new BlockDef("setWidth", "Set Width", "Width value", CAT_CSS),
            new BlockDef("setHeight", "Set Height", "Height value", CAT_CSS),
            new BlockDef("setOpacity", "Set Opacity", "0 to 1", CAT_CSS),
            new BlockDef("setFontSize", "Set Font Size", "Text size", CAT_CSS),
            new BlockDef("setMargin", "Set Margin", "Outer spacing", CAT_CSS),
            new BlockDef("setPadding", "Set Padding", "Inner spacing", CAT_CSS),
            new BlockDef("setBorder", "Set Border", "Border style", CAT_CSS),
            new BlockDef("setRadius", "Set Radius", "Border radius", CAT_CSS),
            new BlockDef("addClass", "Add Class", "Add CSS class", CAT_CSS),
            new BlockDef("removeClass", "Remove Class", "Remove CSS class", CAT_CSS),
            new BlockDef("toggleClass", "Toggle Class", "Toggle CSS class", CAT_CSS),
        };

        // HTML category removed - those are handled by the visual widget
        // designer in MainActivity now. Only CSS rules + Logic + Vars remain
        // here, plus a few runtime effects exposed via CSS-like blocks.

        case CAT_LOGIC: return new BlockDef[]{
            new BlockDef("ifBlock", "If", "Conditional execution", CAT_LOGIC),
            new BlockDef("ifElseBlock", "If / Else", "If-else conditional", CAT_LOGIC),
            new BlockDef("compareEqual", "Compare ==", "Check equality", CAT_LOGIC),
            new BlockDef("compareNotEqual", "Compare !=", "Check inequality", CAT_LOGIC),
            new BlockDef("compareGreater", "Compare >", "Greater than", CAT_LOGIC),
            new BlockDef("compareLess", "Compare <", "Less than", CAT_LOGIC),
            new BlockDef("delay", "Delay", "Wait then execute", CAT_LOGIC),
            new BlockDef("loop", "Loop", "Repeat N times", CAT_LOGIC),
        };

        case CAT_VARIABLE: return new BlockDef[]{
            new BlockDef("createVar", "Create Variable", "Declare a variable", CAT_VARIABLE),
            new BlockDef("setVar", "Set Variable", "Assign a value", CAT_VARIABLE),
            new BlockDef("getVar", "Get Variable", "Read variable value", CAT_VARIABLE),
            new BlockDef("createVarString", "String Var", "String variable", CAT_VARIABLE),
            new BlockDef("createVarNumber", "Number Var", "Number variable", CAT_VARIABLE),
            new BlockDef("createVarBoolean", "Boolean Var", "Boolean variable", CAT_VARIABLE),
        };

        default: return new BlockDef[]{};
    }
}
    private int getCategoryColor(String category) {
        switch (category) {
            case CAT_EVENT: return COLOR_EVENT;
            case CAT_CSS: return COLOR_CSS;
            case CAT_HTML: return COLOR_HTML;
            case CAT_LOGIC: return COLOR_LOGIC;
            case CAT_VARIABLE: return COLOR_VARIABLE;
            default: return COLOR_EVENT;
        }
    }

    // ---- Mapping helpers ----

    private String mapEventKey(String id) {
        switch (id) {
            case "onClick": return "click";
            case "onHover": return "hover";
            case "onLoad": return "load";
            case "onInput": return "input";
            case "onSubmit": return "submit";
            case "onScroll": return "scroll";
            case "onKeyDown": return "keydown";
            case "onChange": return "change";
            default: return id;
        }
    }

    private String mapActionKey(String id) {
        switch (id) {
            case "setDisplay": case "setColor": case "setBackground":
            case "setWidth": case "setHeight": case "setMargin":
            case "setPadding": case "setBorder": case "setRadius":
            case "setOpacity": case "setFontSize":
                return "changeStyle";
            case "addClass": return "addClass";
            case "removeClass": return "removeClass";
            case "toggleClass": return "toggleClass";
            case "setText": return "setText";
            case "setHTML": return "setHTML";
            case "showElement": case "hideElement": case "toggleElement": return "showHide";
            case "navigate": return "navigate";
            case "goToPage": return "goToPage";
            case "alert": return "alert";
            case "scrollTo": return "scrollTo";
            case "focusInput": return "focusInput";
            case "setAttribute": return "setAttribute";
            case "setHref": return "setHref";
            case "removeElement": return "removeElement";
            default: return id;
        }
    }

    private String mapParams(String actionId, String value) {
        switch (actionId) {
            case "setDisplay": return "display:" + value;
            case "setColor": return "color:" + value;
            case "setBackground": return "background:" + value;
            case "setWidth": return "width:" + value;
            case "setHeight": return "height:" + value;
            case "setMargin": return "margin:" + value;
            case "setPadding": return "padding:" + value;
            case "setBorder": return "border:" + value;
            case "setRadius": return "borderRadius:" + value;
            case "setOpacity": return "opacity:" + value;
            case "setFontSize": return "fontSize:" + value;
            case "showElement": return "show";
            case "hideElement": return "hide";
            case "toggleElement": return "toggle";
            case "focusInput": return "focus";
            default: return value;
        }
    }

    private String getValueHint(BlockDef def) {
        switch (def.id) {
            case "setDisplay": return "block, none, flex, grid";
            case "setColor": return "Color (e.g. #ff0000)";
            case "setBackground": return "Background color or gradient";
            case "setWidth": return "Width (e.g. 100px, 50%)";
            case "setHeight": return "Height (e.g. 200px)";
            case "setText": return "New text content";
            case "setHTML": return "HTML content";
            case "navigate": return "URL (e.g. https://example.com)";
            case "setHref": return "Href (e.g. #section, about.html, https://...)";
            case "goToPage": return "Page name (e.g. about)";
            case "alert": return "Alert message";
            case "addClass": case "removeClass": case "toggleClass": return "CSS class name";
            default: return "Value";
        }
    }

    private String getLogicLabel(String action) {
        if (action == null) return "LOGIC";
        switch (action) {
            case "ifBlock": return "IF";
            case "ifElseBlock": return "IF / ELSE";
            case "delay": return "DELAY";
            case "loop": return "LOOP";
            default: return "LOGIC";
        }
    }

    private String getVarLabel(String action) {
        if (action == null) return "VAR";
        switch (action) {
            case "createVar": return "VAR CREATE";
            case "setVar": return "VAR SET";
            case "getVar": return "VAR GET";
            default: return "VAR";
        }
    }

    private String formatLogicParams(LogicBlockManager.LogicBlock block) {
        if (block == null) return "";
        String params = block.params != null ? block.params : "";
        String action = block.action != null ? block.action : "";
        String[] parts = params.split("\\|");
        switch (action) {
            case "ifBlock":
            case "ifElseBlock":
                if (parts.length >= 4) {
                    String result = parts[0] + " " + parts[1] + " " + parts[2] + " -> " + parts[3];
                    if (parts.length >= 5) result += "\nELSE -> " + parts[4];
                    return result;
                }
                return params;
            case "delay":
                if (parts.length >= 2) return parts[0] + "ms -> " + parts[1];
                return params;
            case "loop":
                if (parts.length >= 2) return "x" + parts[0] + " -> " + parts[1];
                return params;
            default: return params;
        }
    }

    private boolean isHtmlAction(String action) {
        if (action == null) return false;
        switch (action) {
            case "setText": case "showHide": case "navigate":
            case "goToPage": case "scrollTo": case "alert":
            case "removeElement": case "setAttribute": case "setHref": case "focusInput":
                return true;
            default: return false;
        }
    }

    // ---- Block Definition ----

    static class BlockDef {
        String id;
        String label;
        String description;
        String category;

        BlockDef(String id, String label, String description, String category) {
            this.id = id;
            this.label = label;
            this.description = description;
this.category = category;
        }
    }
}
