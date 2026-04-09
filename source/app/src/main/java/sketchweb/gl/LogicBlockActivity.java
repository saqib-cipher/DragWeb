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

import androidx.appcompat.app.AppCompatActivity;

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

    // Views
    private MaterialToolbar toolbar;
    private Spinner spnTargetMode;
    private AutoCompleteTextView etTargetSelector;
    private TabLayout tabCategories;
    private LinearLayout blockPaletteContainer;
    private LinearLayout blockWorkspace;
    private Button btnBlockUndo, btnBlockRedo, btnBlockViewJs, btnBlockAdd;

    private String currentCategory = CAT_EVENT;

    // Undo/redo
    private List<String> undoStack = new ArrayList<>();
    private List<String> redoStack = new ArrayList<>();
    private static final int MAX_UNDO = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logic_block);

        projectId = getIntent().getStringExtra("project_id");
        if (projectId == null) projectId = "";

        String pageName = getIntent().getStringExtra("page_name");
        if (pageName == null || pageName.isEmpty()) pageName = "index";

        logicBlockManager = new LogicBlockManager(this);

        // Load existing blocks
        File dir = new File(getFilesDir(), "projects");
        File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
        if (logicFile.exists()) {
            String json = FileUtil.readFile(logicFile.getAbsolutePath());
            if (json != null && !json.isEmpty()) {
                logicBlockManager.fromJson(json);
            }
        }

        initViews();
        setupToolbar();
        setupTargetSelector();
        setupCategoryTabs();
        setupToolbarButtons();
        setupWorkspaceDragDrop();

        showCategory(CAT_EVENT);
        refreshWorkspace();
        saveUndoState();
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
                    case 2: showCategory(CAT_HTML); break;
                    case 3: showCategory(CAT_LOGIC); break;
                    case 4: showCategory(CAT_VARIABLE); break;
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
        // Puzzle-shaped block with notch and tab
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(16, 12, 16, 12);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(6, 4, 6, 4);
        block.setLayoutParams(params);

        // Puzzle-like shape with rounded corners and colored border
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadii(new float[]{16, 16, 16, 16, 16, 16, 4, 4}); // Puzzle notch effect
        bg.setColor(adjustAlpha(baseColor, 30));
        bg.setStroke(3, baseColor);
        block.setBackground(bg);
        block.setElevation(4);

        // Top notch indicator (puzzle connector)
        View topNotch = new View(this);
        GradientDrawable notchBg = new GradientDrawable();
        notchBg.setCornerRadius(4);
        notchBg.setColor(baseColor);
        topNotch.setBackground(notchBg);
        LinearLayout.LayoutParams notchParams = new LinearLayout.LayoutParams(24, 6);
        notchParams.gravity = Gravity.CENTER_HORIZONTAL;
        notchParams.setMargins(0, 0, 0, 6);
        topNotch.setLayoutParams(notchParams);
        block.addView(topNotch);

        // Block name
        TextView nameText = new TextView(this);
        nameText.setText(def.label);
        nameText.setTextColor(baseColor);
        nameText.setTextSize(13);
        nameText.setTypeface(null, Typeface.BOLD);
        block.addView(nameText);

        // Description
        TextView descText = new TextView(this);
        descText.setText(def.description);
        descText.setTextColor(Color.parseColor("#999999"));
        descText.setTextSize(10);
        block.addView(descText);

        // Bottom tab (puzzle connector out)
        View bottomTab = new View(this);
        GradientDrawable tabBg = new GradientDrawable();
        tabBg.setCornerRadius(4);
        tabBg.setColor(adjustAlpha(baseColor, 120));
        bottomTab.setBackground(tabBg);
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(24, 6);
        tabParams.gravity = Gravity.CENTER_HORIZONTAL;
        tabParams.setMargins(0, 6, 0, 0);
        bottomTab.setLayoutParams(tabParams);
        block.addView(bottomTab);

        // Drag support
        block.setOnLongClickListener(v -> {
            ClipData.Item item = new ClipData.Item(def.id + "|" + def.category);
            ClipData dragData = new ClipData("block", new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            v.startDragAndDrop(dragData, shadow, def, 0);
            return true;
        });

        // Tap to add
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

    private void showActionPickerForEvent(BlockDef eventDef) {
        // Combine CSS and HTML actions
        BlockDef[] cssBlocks = getBlocksForCategory(CAT_CSS);
        BlockDef[] htmlBlocks = getBlocksForCategory(CAT_HTML);

        List<String> labels = new ArrayList<>();
        List<BlockDef> allActions = new ArrayList<>();

        for (BlockDef b : cssBlocks) { labels.add("[CSS] " + b.label); allActions.add(b); }
        for (BlockDef b : htmlBlocks) { labels.add("[HTML] " + b.label); allActions.add(b); }

        new MaterialAlertDialogBuilder(this)
            .setTitle("Select Action for " + eventDef.label)
            .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                BlockDef actionDef = allActions.get(which);
                showValueInputForBlock(eventDef, actionDef);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showEventPickerForAction(BlockDef actionDef) {
        String[] events = {"On Click", "On Hover", "On Load", "On Input", "On Submit", "On Scroll"};
        String[] eventKeys = {"click", "hover", "load", "input", "submit", "scroll"};

        new MaterialAlertDialogBuilder(this)
            .setTitle("Select Event for " + actionDef.label)
            .setItems(events, (dialog, which) -> {
                BlockDef eventDef = new BlockDef(eventKeys[which], events[which], "", CAT_EVENT);
                showValueInputForBlock(eventDef, actionDef);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showValueInputForBlock(BlockDef eventDef, BlockDef actionDef) {
        String hint = getValueHint(actionDef);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        TextInputLayout til = new TextInputLayout(this);
        til.setHint(hint);
        TextInputEditText input = new TextInputEditText(this);
        til.addView(input);
        layout.addView(til);

        new MaterialAlertDialogBuilder(this)
            .setTitle(actionDef.label)
            .setView(layout)
            .setPositiveButton("Add Block", (d, w) -> {
                String value = input.getText() != null ? input.getText().toString().trim() : "";
                createBlock(eventDef, actionDef, value);
            })
            .setNegativeButton("Cancel", null)
            .show();
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
        if (blocks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Drag blocks here or tap + Add\nto build your logic");
            empty.setTextColor(Color.parseColor("#666666"));
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(32, 80, 32, 80);
            blockWorkspace.addView(empty);
            return;
        }

        for (int i = 0; i < blocks.size(); i++) {
            blockWorkspace.addView(createWorkspacePuzzleBlock(blocks.get(i), i));
        }
    }

    private View createWorkspacePuzzleBlock(LogicBlockManager.LogicBlock block, int index) {
        boolean isLogic = "logic".equals(block.targetMode);
        boolean isVar = "variable".equals(block.targetMode);

        int baseColor;
        if (isLogic) baseColor = COLOR_LOGIC;
        else if (isVar) baseColor = COLOR_VARIABLE;
        else baseColor = COLOR_EVENT;

        // Outer card with puzzle shape
        LinearLayout puzzleCard = new LinearLayout(this);
        puzzleCard.setOrientation(LinearLayout.VERTICAL);
        puzzleCard.setPadding(16, 12, 16, 12);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(4, 2, 4, 2);
        puzzleCard.setLayoutParams(cardParams);

        // Puzzle shape background
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadii(new float[]{16, 16, 16, 16, 16, 16, 4, 4});
        bg.setColor(adjustAlpha(baseColor, 20));
        bg.setStroke(2, adjustAlpha(baseColor, 180));
        puzzleCard.setBackground(bg);
        puzzleCard.setElevation(3);

        // Top puzzle connector
        View topNotch = new View(this);
        GradientDrawable notchBg = new GradientDrawable();
        notchBg.setCornerRadius(4);
        notchBg.setColor(baseColor);
        topNotch.setBackground(notchBg);
        LinearLayout.LayoutParams notchParams = new LinearLayout.LayoutParams(32, 6);
        notchParams.gravity = Gravity.START;
        notchParams.setMargins(16, 0, 0, 8);
        topNotch.setLayoutParams(notchParams);
        puzzleCard.addView(topNotch);

        if (!isLogic && !isVar) {
            // TARGET row
            LinearLayout targetRow = createBlockRow();
            TextView targetLabel = createBadge("TARGET", Color.parseColor("#9C27B0"));
            targetRow.addView(targetLabel);
            String modePrefix = "id".equals(block.targetMode) ? "#" :
                "class".equals(block.targetMode) ? "." : "";
            TextView targetValue = new TextView(this);
            targetValue.setText("  " + modePrefix + block.targetWidget + " (" + block.targetMode + ")");
            targetValue.setTextColor(Color.parseColor("#CE93D8"));
            targetValue.setTextSize(13);
            targetValue.setTypeface(null, Typeface.BOLD);
            targetRow.addView(targetValue);
            puzzleCard.addView(targetRow);

            // EVENT row
            LinearLayout eventRow = createBlockRow();
            TextView whenBadge = createBadge("WHEN", COLOR_EVENT);
            eventRow.addView(whenBadge);
            TextView eventValue = new TextView(this);
            eventValue.setText("  " + block.event.toUpperCase());
            eventValue.setTextColor(Color.parseColor("#FFB74D"));
            eventValue.setTextSize(13);
            eventValue.setTypeface(null, Typeface.BOLD);
            eventRow.addView(eventValue);
            puzzleCard.addView(eventRow);

            // ACTION row
            LinearLayout actionRow = createBlockRow();
            int actionColor = isHtmlAction(block.action) ? COLOR_HTML : COLOR_CSS;
            TextView doBadge = createBadge("DO", actionColor);
            actionRow.addView(doBadge);
            TextView actionValue = new TextView(this);
            actionValue.setText("  " + block.action + "(" + block.params + ")");
            actionValue.setTextColor(adjustAlpha(actionColor, 200));
            actionValue.setTextSize(12);
            actionRow.addView(actionValue);
            puzzleCard.addView(actionRow);
        } else if (isLogic) {
            LinearLayout row = createBlockRow();
            TextView badge = createBadge(getLogicLabel(block.action), COLOR_LOGIC);
            row.addView(badge);
            puzzleCard.addView(row);

            TextView paramsView = new TextView(this);
            paramsView.setText(formatLogicParams(block));
            paramsView.setTextColor(Color.parseColor("#F48FB1"));
            paramsView.setTextSize(12);
            paramsView.setPadding(4, 4, 4, 0);
            puzzleCard.addView(paramsView);
        } else {
            LinearLayout row = createBlockRow();
            TextView badge = createBadge(getVarLabel(block.action), COLOR_VARIABLE);
            row.addView(badge);
            puzzleCard.addView(row);

            TextView paramsView = new TextView(this);
            paramsView.setText(block.params);
            paramsView.setTextColor(Color.parseColor("#80DEEA"));
            paramsView.setTextSize(12);
            paramsView.setPadding(4, 4, 4, 0);
            puzzleCard.addView(paramsView);
        }

        // Bottom puzzle tab
        View bottomTab = new View(this);
        GradientDrawable tabBg = new GradientDrawable();
        tabBg.setCornerRadius(4);
        tabBg.setColor(adjustAlpha(baseColor, 100));
        bottomTab.setBackground(tabBg);
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(32, 6);
        tabParams.gravity = Gravity.START;
        tabParams.setMargins(16, 8, 0, 0);
        bottomTab.setLayoutParams(tabParams);
        puzzleCard.addView(bottomTab);

        // Long press for actions
        puzzleCard.setOnLongClickListener(v -> {
            showBlockActions(index);
            return true;
        });

        return puzzleCard;
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
        TextInputEditText input = new TextInputEditText(this);
        til.addView(input);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 0, 4);
        til.setLayoutParams(params);
        return til;
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

    // ---- JS Preview ----

    private void showJsPreview() {
        String js = logicBlockManager.generateJavaScript();
        if (js.isEmpty()) js = "// No logic blocks yet";

        ScrollView sv = new ScrollView(this);
        sv.setPadding(24, 16, 24, 16);
        TextView tv = new TextView(this);
        tv.setText(js);
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
            .setTitle("Generated JavaScript")
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

        case CAT_HTML: return new BlockDef[]{
            new BlockDef("setText", "Set Text", "Change text content", CAT_HTML),
            new BlockDef("setHTML", "Set HTML", "Set inner HTML", CAT_HTML),
            new BlockDef("showElement", "Show", "Show element", CAT_HTML),
            new BlockDef("hideElement", "Hide", "Hide element", CAT_HTML),
            new BlockDef("toggleElement", "Toggle", "Toggle visibility", CAT_HTML),
            new BlockDef("navigate", "Navigate", "Go to URL", CAT_HTML),
            new BlockDef("goToPage", "Go To Page", "Navigate to page", CAT_HTML),
            new BlockDef("alert", "Alert", "Show alert dialog", CAT_HTML),
            new BlockDef("scrollTo", "Scroll To", "Scroll to position", CAT_HTML),
            new BlockDef("focusInput", "Focus Input", "Focus input field", CAT_HTML),
            new BlockDef("setAttribute", "Set Attribute", "Set HTML attribute", CAT_HTML),
            new BlockDef("removeElement", "Remove", "Remove element", CAT_HTML),
        };

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
            case "goToPage": return "Page name (e.g. about)";
            case "alert": return "Alert message";
            case "addClass": case "removeClass": case "toggleClass": return "CSS class name";
            default: return "Value";
        }
    }

    private String getLogicLabel(String action) {
        switch (action) {
            case "ifBlock": return "IF";
            case "ifElseBlock": return "IF / ELSE";
            case "delay": return "DELAY";
            case "loop": return "LOOP";
            default: return "LOGIC";
        }
    }

    private String getVarLabel(String action) {
        switch (action) {
            case "createVar": return "VAR CREATE";
            case "setVar": return "VAR SET";
            case "getVar": return "VAR GET";
            default: return "VAR";
        }
    }

    private String formatLogicParams(LogicBlockManager.LogicBlock block) {
        String[] parts = block.params.split("\\|");
        switch (block.action) {
            case "ifBlock":
            case "ifElseBlock":
                if (parts.length >= 4) {
                    String result = parts[0] + " " + parts[1] + " " + parts[2] + " -> " + parts[3];
                    if (parts.length >= 5) result += "\nELSE -> " + parts[4];
                    return result;
                }
                return block.params;
            case "delay":
                if (parts.length >= 2) return parts[0] + "ms -> " + parts[1];
                return block.params;
            case "loop":
                if (parts.length >= 2) return "x" + parts[0] + " -> " + parts[1];
                return block.params;
            default: return block.params;
        }
    }

    private boolean isHtmlAction(String action) {
        switch (action) {
            case "setText": case "showHide": case "navigate":
            case "goToPage": case "scrollTo": case "alert":
            case "removeElement": case "setAttribute": case "focusInput":
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
