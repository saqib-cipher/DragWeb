package sketchweb.gl;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Block-based drag and drop system for DragWeb.
 * Provides visual block palettes for Events, CSS Actions, HTML Actions,
 * Logic blocks, and Variable blocks that can be dragged into a workspace.
 */
public class BlockDragDropManager {

    // Block categories
    public static final String CAT_EVENT = "event";
    public static final String CAT_CSS = "css";
    public static final String CAT_HTML = "html";
    public static final String CAT_LOGIC = "logic";
    public static final String CAT_VARIABLE = "variable";

    // Colors for block categories
    private static final String COLOR_EVENT = "#FF9800";
    private static final String COLOR_EVENT_BG = "#2D1F00";
    private static final String COLOR_CSS = "#2196F3";
    private static final String COLOR_CSS_BG = "#001F3D";
    private static final String COLOR_HTML = "#4CAF50";
    private static final String COLOR_HTML_BG = "#002D00";
    private static final String COLOR_TARGET = "#9C27B0";
    private static final String COLOR_TARGET_BG = "#1A0025";
    private static final String COLOR_LOGIC = "#E91E63";
    private static final String COLOR_LOGIC_BG = "#2D000E";
    private static final String COLOR_VARIABLE = "#00BCD4";
    private static final String COLOR_VARIABLE_BG = "#002025";

    private Context context;
    private LogicBlockManager logicBlockManager;
    private LinearLayout blockWorkspace;
    private LinearLayout blockPalette;
    private OnBlocksChangedListener listener;

    // Search & collapse state
    private String searchQuery = "";
    private boolean workspaceCollapsed = false;

    // Undo/redo for blocks
    private List<String> blockUndoStack = new ArrayList<>();
    private List<String> blockRedoStack = new ArrayList<>();
    private static final int MAX_BLOCK_UNDO = 30;

    // Clipboard for copy/paste
    private LogicBlockManager.LogicBlock blockClipboard = null;

    // Block definitions
    private static final BlockDef[] EVENT_BLOCKS = {
        new BlockDef("onClick", "On Click", "When element is clicked", CAT_EVENT),
        new BlockDef("onHover", "On Hover", "When mouse hovers over element", CAT_EVENT),
        new BlockDef("onLoad", "On Load", "When page loads", CAT_EVENT),
        new BlockDef("onInput", "On Input", "When input value changes", CAT_EVENT),
        new BlockDef("onSubmit", "On Submit", "When form is submitted", CAT_EVENT),
        new BlockDef("onScroll", "On Scroll", "When user scrolls", CAT_EVENT),
        new BlockDef("onKeyDown", "On Key Down", "When key is pressed", CAT_EVENT),
        new BlockDef("onChange", "On Change", "When value changes", CAT_EVENT),
    };

    private static final BlockDef[] CSS_BLOCKS = {
        new BlockDef("setDisplay", "Set Display", "block, none, flex, grid", CAT_CSS),
        new BlockDef("setPosition", "Set Position", "static, relative, absolute, fixed", CAT_CSS),
        new BlockDef("setOverflow", "Set Overflow", "hidden, scroll, auto, visible", CAT_CSS),
        new BlockDef("setColor", "Set Color", "Set text color", CAT_CSS),
        new BlockDef("setBackground", "Set Background", "Color or gradient", CAT_CSS),
        new BlockDef("setWidth", "Set Width", "px, %, vw, auto", CAT_CSS),
        new BlockDef("setHeight", "Set Height", "px, %, vh, auto", CAT_CSS),
        new BlockDef("setMargin", "Set Margin", "Outer spacing", CAT_CSS),
        new BlockDef("setPadding", "Set Padding", "Inner spacing", CAT_CSS),
        new BlockDef("setBorder", "Set Border", "Border style", CAT_CSS),
        new BlockDef("setRadius", "Set Radius", "Border radius", CAT_CSS),
        new BlockDef("setOpacity", "Set Opacity", "0 to 1", CAT_CSS),
        new BlockDef("setFontSize", "Set Font Size", "Text size", CAT_CSS),
        new BlockDef("setTextAlign", "Set Text Align", "left, center, right", CAT_CSS),
        new BlockDef("setZIndex", "Set Z-Index", "Stack order", CAT_CSS),
        new BlockDef("setFlex", "Set Flex", "Flex layout properties", CAT_CSS),
        new BlockDef("addClass", "Add Class", "Add CSS class to element", CAT_CSS),
        new BlockDef("removeClass", "Remove Class", "Remove CSS class", CAT_CSS),
        new BlockDef("toggleClass", "Toggle Class", "Toggle CSS class", CAT_CSS),
    };

    private static final BlockDef[] HTML_BLOCKS = {
        new BlockDef("setHref", "Set Href", "URL or #section", CAT_HTML),
        new BlockDef("scrollTo", "Scroll To", "id, class, or position", CAT_HTML),
        new BlockDef("setText", "Set Text", "Change text content", CAT_HTML),
        new BlockDef("setHTML", "Set HTML", "Set inner HTML", CAT_HTML),
        new BlockDef("showElement", "Show Element", "Display element", CAT_HTML),
        new BlockDef("hideElement", "Hide Element", "Hide element", CAT_HTML),
        new BlockDef("toggleElement", "Toggle Visible", "Toggle show/hide", CAT_HTML),
        new BlockDef("setAttribute", "Set Attribute", "Set HTML attribute", CAT_HTML),
        new BlockDef("removeElement", "Remove Element", "Remove from DOM", CAT_HTML),
        new BlockDef("focusInput", "Focus Input", "Focus an input field", CAT_HTML),
        new BlockDef("blurInput", "Blur Input", "Remove focus from input", CAT_HTML),
        new BlockDef("alert", "Show Alert", "Browser alert dialog", CAT_HTML),
        new BlockDef("navigate", "Navigate", "Go to URL", CAT_HTML),
        new BlockDef("goToPage", "Go To Page", "Navigate to project page", CAT_HTML),
    };

    private static final BlockDef[] LOGIC_BLOCKS = {
        new BlockDef("ifBlock", "If", "Conditional execution", CAT_LOGIC),
        new BlockDef("ifElseBlock", "If / Else", "If-else conditional", CAT_LOGIC),
        new BlockDef("compareEqual", "Compare ==", "Check if values are equal", CAT_LOGIC),
        new BlockDef("compareNotEqual", "Compare !=", "Check if values differ", CAT_LOGIC),
        new BlockDef("compareGreater", "Compare >", "Check if A > B", CAT_LOGIC),
        new BlockDef("compareLess", "Compare <", "Check if A < B", CAT_LOGIC),
        new BlockDef("delay", "Delay", "Wait then execute", CAT_LOGIC),
        new BlockDef("loop", "Loop", "Repeat N times", CAT_LOGIC),
    };

    private static final BlockDef[] VARIABLE_BLOCKS = {
        new BlockDef("createVar", "Create Variable", "Declare a new variable", CAT_VARIABLE),
        new BlockDef("setVar", "Set Variable", "Assign a value", CAT_VARIABLE),
        new BlockDef("getVar", "Get Variable", "Read variable value", CAT_VARIABLE),
        new BlockDef("createVarString", "String Var", "Create string variable", CAT_VARIABLE),
        new BlockDef("createVarNumber", "Number Var", "Create number variable", CAT_VARIABLE),
        new BlockDef("createVarBoolean", "Boolean Var", "Create boolean variable", CAT_VARIABLE),
    };

    public BlockDragDropManager(Context context, LogicBlockManager logicBlockManager) {
        this.context = context;
        this.logicBlockManager = logicBlockManager;
    }

    public void setOnBlocksChangedListener(OnBlocksChangedListener listener) {
        this.listener = listener;
    }

    /**
     * Build the complete block editor UI: palette on top, workspace below.
     */
    public View buildBlockEditorView() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Category chip selector
        root.addView(buildCategoryChips());

        // Block search bar
        root.addView(buildSearchBar());

        // Block palette (horizontal scrollable)
        root.addView(buildBlockPalette());

        // Toolbar: undo/redo, copy/paste, collapse, JS preview
        root.addView(buildBlockToolbar());

        // Workspace area (drop target)
        root.addView(buildWorkspace());

        return root;
    }

    private View buildCategoryChips() {
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setPadding(8, 8, 8, 4);

        ChipGroup chipGroup = new ChipGroup(context);
        chipGroup.setSingleSelection(true);
        chipGroup.setSingleLine(true);

        Chip chipEvent = createFilterChip("Events", COLOR_EVENT);
        chipEvent.setChecked(true);
        chipEvent.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) showCategory(CAT_EVENT);
        });

        Chip chipCss = createFilterChip("CSS", COLOR_CSS);
        chipCss.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) showCategory(CAT_CSS);
        });

        Chip chipHtml = createFilterChip("HTML", COLOR_HTML);
        chipHtml.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) showCategory(CAT_HTML);
        });

        Chip chipLogic = createFilterChip("Logic", COLOR_LOGIC);
        chipLogic.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) showCategory(CAT_LOGIC);
        });

        Chip chipVar = createFilterChip("Variables", COLOR_VARIABLE);
        chipVar.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) showCategory(CAT_VARIABLE);
        });

        chipGroup.addView(chipEvent);
        chipGroup.addView(chipCss);
        chipGroup.addView(chipHtml);
        chipGroup.addView(chipLogic);
        chipGroup.addView(chipVar);
        hsv.addView(chipGroup);
        return hsv;
    }

    private View buildSearchBar() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(12, 2, 12, 2);
        row.setGravity(Gravity.CENTER_VERTICAL);

        EditText searchInput = new EditText(context);
        searchInput.setHint("Search blocks...");
        searchInput.setTextSize(12);
        searchInput.setSingleLine(true);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        searchInput.setLayoutParams(inputParams);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase();
                refreshPalette();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        row.addView(searchInput);
        return row;
    }

    private String currentCategory = CAT_EVENT;

    private void refreshPalette() {
        showCategory(currentCategory);
    }

    private Chip createFilterChip(String text, String color) {
        Chip chip = new Chip(context);
        chip.setText(text);
        chip.setTextSize(12);
        chip.setCheckable(true);
        chip.setChipBackgroundColorResource(android.R.color.transparent);
        chip.setTextColor(Color.parseColor(color));
        chip.setChipStrokeColorResource(android.R.color.darker_gray);
        chip.setChipStrokeWidth(1);
        return chip;
    }

    private View buildBlockPalette() {
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams hsvParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsvParams.setMargins(0, 0, 0, 4);
        hsv.setLayoutParams(hsvParams);

        blockPalette = new LinearLayout(context);
        blockPalette.setOrientation(LinearLayout.HORIZONTAL);
        blockPalette.setPadding(8, 4, 8, 4);
        hsv.addView(blockPalette);

        // Show events by default
        showCategory(CAT_EVENT);

        return hsv;
    }

    private View buildBlockToolbar() {
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(8, 2, 8, 2);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        toolbar.addView(createToolBtn("Undo", v -> undoBlocks()));
        toolbar.addView(createToolBtn("Redo", v -> redoBlocks()));
        toolbar.addView(createToolBtn("Copy", v -> copySelectedBlock()));
        toolbar.addView(createToolBtn("Paste", v -> pasteBlock()));
        toolbar.addView(createToolBtn(workspaceCollapsed ? "Expand" : "Collapse", v -> {
            workspaceCollapsed = !workspaceCollapsed;
            refreshWorkspace();
        }));
        toolbar.addView(createToolBtn("View JS", v -> showJsPreview()));

        hsv.addView(toolbar);
        return hsv;
    }

    private TextView createToolBtn(String label, View.OnClickListener onClick) {
        TextView btn = new TextView(context);
        btn.setText(label);
        btn.setTextSize(11);
        btn.setTextColor(Color.parseColor("#90CAF9"));
        btn.setPadding(14, 6, 14, 6);
        btn.setOnClickListener(onClick);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(6);
        bg.setStroke(1, Color.parseColor("#333355"));
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private void showCategory(String category) {
        currentCategory = category;
        if (blockPalette == null) return;
        blockPalette.removeAllViews();

        BlockDef[] blocks;
        switch (category) {
            case CAT_CSS: blocks = CSS_BLOCKS; break;
            case CAT_HTML: blocks = HTML_BLOCKS; break;
            case CAT_LOGIC: blocks = LOGIC_BLOCKS; break;
            case CAT_VARIABLE: blocks = VARIABLE_BLOCKS; break;
            default: blocks = EVENT_BLOCKS; break;
        }

        for (BlockDef def : blocks) {
            if (!searchQuery.isEmpty()) {
                String searchable = (def.label + " " + def.description + " " + def.id).toLowerCase();
                if (!searchable.contains(searchQuery)) continue;
            }
            blockPalette.addView(createPaletteBlock(def));
        }

        if (blockPalette.getChildCount() == 0) {
            TextView empty = new TextView(context);
            empty.setText("No blocks match search");
            empty.setTextColor(Color.parseColor("#888888"));
            empty.setTextSize(11);
            empty.setPadding(16, 8, 16, 8);
            blockPalette.addView(empty);
        }
    }

    private View createPaletteBlock(BlockDef def) {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(12, 8, 12, 8);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(4, 2, 4, 2);
        block.setLayoutParams(params);

        String bgColor, textColor;
        switch (def.category) {
            case CAT_CSS: bgColor = COLOR_CSS_BG; textColor = COLOR_CSS; break;
            case CAT_HTML: bgColor = COLOR_HTML_BG; textColor = COLOR_HTML; break;
            case CAT_LOGIC: bgColor = COLOR_LOGIC_BG; textColor = COLOR_LOGIC; break;
            case CAT_VARIABLE: bgColor = COLOR_VARIABLE_BG; textColor = COLOR_VARIABLE; break;
            default: bgColor = COLOR_EVENT_BG; textColor = COLOR_EVENT; break;
        }

        GradientDrawable bg = new GradientDrawable();
        // Create puzzle-like shape: top-left rounded, top-right square, bottom-right rounded, bottom-left square
        // Actually, let's use cornerRadii: TopLeft, TopRight, BottomRight, BottomLeft
        bg.setCornerRadii(new float[]{16, 16, 16, 16, 0, 0, 0, 0});
        bg.setColor(Color.parseColor(bgColor));
        bg.setStroke(2, Color.parseColor(textColor));
        block.setBackground(bg);

        // Block name
        TextView nameText = new TextView(context);
        nameText.setText(def.label);
        nameText.setTextColor(Color.parseColor(textColor));
        nameText.setTextSize(12);
        nameText.setTypeface(null, Typeface.BOLD);
        block.addView(nameText);

        // Description
        TextView descText = new TextView(context);
        descText.setText(def.description);
        descText.setTextColor(Color.parseColor("#999999"));
        descText.setTextSize(9);
        block.addView(descText);

        // Enable drag
        block.setOnLongClickListener(v -> {
            ClipData.Item item = new ClipData.Item(def.id + "|" + def.category);
            ClipData dragData = new ClipData("block", new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
            v.startDragAndDrop(dragData, shadowBuilder, def, 0);
            return true;
        });

        // Also allow tap to add
        block.setOnClickListener(v -> {
            showAddBlockFromPalette(def);
        });

        return block;
    }

    private View buildWorkspace() {
        ScrollView scrollView = new ScrollView(context);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollView.setLayoutParams(scrollParams);

        blockWorkspace = new LinearLayout(context);
        blockWorkspace.setOrientation(LinearLayout.VERTICAL);
        blockWorkspace.setPadding(12, 8, 12, 8);
        blockWorkspace.setMinimumHeight(300);

        GradientDrawable workspaceBg = new GradientDrawable();
        workspaceBg.setCornerRadius(12);
        workspaceBg.setColor(Color.parseColor("#0A0A14"));
        workspaceBg.setStroke(1, Color.parseColor("#222233"));
        blockWorkspace.setBackground(workspaceBg);

        // Set up drop listener
        blockWorkspace.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
                case DragEvent.ACTION_DRAG_ENTERED:
                    blockWorkspace.setAlpha(0.85f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    blockWorkspace.setAlpha(1f);
                    return true;
                case DragEvent.ACTION_DROP:
                    blockWorkspace.setAlpha(1f);
                    Object localState = event.getLocalState();
                    if (localState instanceof BlockDef) {
                        BlockDef def = (BlockDef) localState;
                        showAddBlockFromPalette(def);
                    }
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    blockWorkspace.setAlpha(1f);
                    return true;
            }
            return false;
        });

        scrollView.addView(blockWorkspace);
        return scrollView;
    }

    // ---- Block undo/redo ----

    private void saveBlockUndoState() {
        String json = logicBlockManager.toJson();
        blockUndoStack.add(json);
        if (blockUndoStack.size() > MAX_BLOCK_UNDO) {
            blockUndoStack.remove(0);
        }
        blockRedoStack.clear();
    }

    private void undoBlocks() {
        if (blockUndoStack.isEmpty()) {
            Toast.makeText(context, "Nothing to undo", Toast.LENGTH_SHORT).show();
            return;
        }
        blockRedoStack.add(logicBlockManager.toJson());
        String prev = blockUndoStack.remove(blockUndoStack.size() - 1);
        logicBlockManager.fromJson(prev);
        refreshWorkspace();
        if (listener != null) listener.onBlocksChanged();
    }

    private void redoBlocks() {
        if (blockRedoStack.isEmpty()) {
            Toast.makeText(context, "Nothing to redo", Toast.LENGTH_SHORT).show();
            return;
        }
        blockUndoStack.add(logicBlockManager.toJson());
        String next = blockRedoStack.remove(blockRedoStack.size() - 1);
        logicBlockManager.fromJson(next);
        refreshWorkspace();
        if (listener != null) listener.onBlocksChanged();
    }

    // ---- Block copy/paste ----

    private void copySelectedBlock() {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (blocks.isEmpty()) {
            Toast.makeText(context, "No blocks to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        // Copy the last block
        String[] labels = new String[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            LogicBlockManager.LogicBlock b = blocks.get(i);
            labels[i] = (i + 1) + ". " + b.event + " -> " + b.action;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle("Select Block to Copy")
            .setItems(labels, (dialog, which) -> {
                LogicBlockManager.LogicBlock original = blocks.get(which);
                blockClipboard = new LogicBlockManager.LogicBlock();
                blockClipboard.targetWidget = original.targetWidget;
                blockClipboard.targetMode = original.targetMode;
                blockClipboard.event = original.event;
                blockClipboard.action = original.action;
                blockClipboard.params = original.params;
                Toast.makeText(context, "Block copied", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void pasteBlock() {
        if (blockClipboard == null) {
            Toast.makeText(context, "Nothing to paste", Toast.LENGTH_SHORT).show();
            return;
        }
        saveBlockUndoState();
        LogicBlockManager.LogicBlock copy = new LogicBlockManager.LogicBlock();
        copy.targetWidget = blockClipboard.targetWidget;
        copy.targetMode = blockClipboard.targetMode;
        copy.event = blockClipboard.event;
        copy.action = blockClipboard.action;
        copy.params = blockClipboard.params;
        logicBlockManager.addBlock(copy);
        refreshWorkspace();
        if (listener != null) listener.onBlocksChanged();
        Toast.makeText(context, "Block pasted", Toast.LENGTH_SHORT).show();
    }

    // ---- JS Preview ----

    private void showJsPreview() {
        String js = logicBlockManager.generateJavaScript();
        if (js.isEmpty()) {
            js = "// No logic blocks added yet";
        }

        ScrollView sv = new ScrollView(context);
        sv.setPadding(24, 16, 24, 16);
        TextView tv = new TextView(context);
        tv.setText(js);
        tv.setTextSize(11);
        tv.setTextColor(Color.parseColor("#A5D6A7"));
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);

        GradientDrawable codeBg = new GradientDrawable();
        codeBg.setCornerRadius(8);
        codeBg.setColor(Color.parseColor("#0A0A14"));
        codeBg.setStroke(1, Color.parseColor("#333355"));
        tv.setBackground(codeBg);
        tv.setPadding(16, 12, 16, 12);

        sv.addView(tv);

        new MaterialAlertDialogBuilder(context)
            .setTitle("Generated JavaScript")
            .setView(sv)
            .setPositiveButton("Close", null)
            .show();
    }

    /**
     * Show dialog to configure a block dropped from the palette.
     */
    public void showAddBlockFromPalette(BlockDef def) {
        if (CAT_LOGIC.equals(def.category)) {
            showLogicBlockDialog(def);
        } else if (CAT_VARIABLE.equals(def.category)) {
            showVariableBlockDialog(def);
        } else if (CAT_EVENT.equals(def.category)) {
            showTargetDialog(def);
        } else {
            showActionTargetDialog(def);
        }
    }

    // ---- Logic block dialogs ----

    private void showLogicBlockDialog(BlockDef def) {
        switch (def.id) {
            case "ifBlock":
            case "ifElseBlock": {
                // Need: condition left, operator, condition right, then action
                LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(48, 16, 48, 0);

                TextInputLayout tilLeft = new TextInputLayout(context);
                tilLeft.setHint("Left value (e.g. variable name or selector.value)");
                TextInputEditText inputLeft = new TextInputEditText(context);
                tilLeft.addView(inputLeft);
                layout.addView(tilLeft);

                TextInputLayout tilOp = new TextInputLayout(context);
                tilOp.setHint("Operator (==, !=, >, <)");
                TextInputEditText inputOp = new TextInputEditText(context);
                inputOp.setText("==");
                tilOp.addView(inputOp);
                layout.addView(tilOp);

                TextInputLayout tilRight = new TextInputLayout(context);
                tilRight.setHint("Right value (e.g. 'hello' or 5)");
                TextInputEditText inputRight = new TextInputEditText(context);
                tilRight.addView(inputRight);
                layout.addView(tilRight);

                TextInputLayout tilAction = new TextInputLayout(context);
                tilAction.setHint("Then action (JS code to execute)");
                TextInputEditText inputAction = new TextInputEditText(context);
                tilAction.addView(inputAction);
                layout.addView(tilAction);

                TextInputLayout tilElse = null;
                TextInputEditText inputElse = null;
                if ("ifElseBlock".equals(def.id)) {
                    tilElse = new TextInputLayout(context);
                    tilElse.setHint("Else action (JS code)");
                    inputElse = new TextInputEditText(context);
                    tilElse.addView(inputElse);
                    layout.addView(tilElse);
                }

                final TextInputEditText finalInputElse = inputElse;
                new MaterialAlertDialogBuilder(context)
                    .setTitle(def.label)
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        String left = inputLeft.getText().toString().trim();
                        String op = inputOp.getText().toString().trim();
                        String right = inputRight.getText().toString().trim();
                        String action = inputAction.getText().toString().trim();
                        String elseAction = finalInputElse != null ? finalInputElse.getText().toString().trim() : "";

                        saveBlockUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "logic";
                        block.event = "immediate";
                        block.action = def.id;
                        block.params = left + "|" + op + "|" + right + "|" + action
                            + (elseAction.isEmpty() ? "" : "|" + elseAction);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                        if (listener != null) listener.onBlocksChanged();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "compareEqual":
            case "compareNotEqual":
            case "compareGreater":
            case "compareLess": {
                String op = "==";
                if ("compareNotEqual".equals(def.id)) op = "!=";
                else if ("compareGreater".equals(def.id)) op = ">";
                else if ("compareLess".equals(def.id)) op = "<";

                LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(48, 16, 48, 0);

                TextInputLayout tilLeft = new TextInputLayout(context);
                tilLeft.setHint("Left value");
                TextInputEditText inputLeft = new TextInputEditText(context);
                tilLeft.addView(inputLeft);
                layout.addView(tilLeft);

                TextInputLayout tilRight = new TextInputLayout(context);
                tilRight.setHint("Right value");
                TextInputEditText inputRight = new TextInputEditText(context);
                tilRight.addView(inputRight);
                layout.addView(tilRight);

                TextInputLayout tilAction = new TextInputLayout(context);
                tilAction.setHint("Action if true (JS code)");
                TextInputEditText inputAction = new TextInputEditText(context);
                tilAction.addView(inputAction);
                layout.addView(tilAction);

                final String finalOp = op;
                new MaterialAlertDialogBuilder(context)
                    .setTitle(def.label + " (" + op + ")")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveBlockUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "logic";
                        block.event = "immediate";
                        block.action = "ifBlock";
                        String left = inputLeft.getText().toString().trim();
                        String right = inputRight.getText().toString().trim();
                        String action = inputAction.getText().toString().trim();
                        block.params = left + "|" + finalOp + "|" + right + "|" + action;
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                        if (listener != null) listener.onBlocksChanged();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "delay": {
                LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(48, 16, 48, 0);

                TextInputLayout tilMs = new TextInputLayout(context);
                tilMs.setHint("Delay in milliseconds");
                TextInputEditText inputMs = new TextInputEditText(context);
                inputMs.setText("1000");
                tilMs.addView(inputMs);
                layout.addView(tilMs);

                TextInputLayout tilAction = new TextInputLayout(context);
                tilAction.setHint("Action after delay (JS code)");
                TextInputEditText inputAction = new TextInputEditText(context);
                tilAction.addView(inputAction);
                layout.addView(tilAction);

                new MaterialAlertDialogBuilder(context)
                    .setTitle("Delay")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveBlockUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "logic";
                        block.event = "immediate";
                        block.action = "delay";
                        block.params = inputMs.getText().toString().trim() + "|" + inputAction.getText().toString().trim();
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                        if (listener != null) listener.onBlocksChanged();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "loop": {
                LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(48, 16, 48, 0);

                TextInputLayout tilCount = new TextInputLayout(context);
                tilCount.setHint("Number of iterations");
                TextInputEditText inputCount = new TextInputEditText(context);
                inputCount.setText("5");
                tilCount.addView(inputCount);
                layout.addView(tilCount);

                TextInputLayout tilAction = new TextInputLayout(context);
                tilAction.setHint("Action per iteration (JS, use 'i' for index)");
                TextInputEditText inputAction = new TextInputEditText(context);
                tilAction.addView(inputAction);
                layout.addView(tilAction);

                new MaterialAlertDialogBuilder(context)
                    .setTitle("Loop")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveBlockUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "logic";
                        block.event = "immediate";
                        block.action = "loop";
                        block.params = inputCount.getText().toString().trim() + "|" + inputAction.getText().toString().trim();
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                        if (listener != null) listener.onBlocksChanged();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
        }
    }

    // ---- Variable block dialogs ----

    private void showVariableBlockDialog(BlockDef def) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        switch (def.id) {
            case "createVar":
            case "createVarString":
            case "createVarNumber":
            case "createVarBoolean": {
                TextInputLayout tilName = new TextInputLayout(context);
                tilName.setHint("Variable name");
                TextInputEditText inputName = new TextInputEditText(context);
                tilName.addView(inputName);
                layout.addView(tilName);

                TextInputLayout tilValue = new TextInputLayout(context);
                String type = "any";
                if ("createVarString".equals(def.id)) {
                    tilValue.setHint("Initial value (string)");
                    type = "string";
                } else if ("createVarNumber".equals(def.id)) {
                    tilValue.setHint("Initial value (number)");
                    type = "number";
                } else if ("createVarBoolean".equals(def.id)) {
                    tilValue.setHint("Initial value (true/false)");
                    type = "boolean";
                } else {
                    tilValue.setHint("Initial value");
                }
                TextInputEditText inputValue = new TextInputEditText(context);
                tilValue.addView(inputValue);
                layout.addView(tilValue);

                final String varType = type;
                new MaterialAlertDialogBuilder(context)
                    .setTitle(def.label)
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveBlockUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "variable";
                        block.event = "immediate";
                        block.action = "createVar";
                        block.params = inputName.getText().toString().trim() + "|" + varType + "|" + inputValue.getText().toString().trim();
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                        if (listener != null) listener.onBlocksChanged();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "setVar": {
                TextInputLayout tilName = new TextInputLayout(context);
                tilName.setHint("Variable name");
                TextInputEditText inputName = new TextInputEditText(context);
                tilName.addView(inputName);
                layout.addView(tilName);

                TextInputLayout tilValue = new TextInputLayout(context);
                tilValue.setHint("New value");
                TextInputEditText inputValue = new TextInputEditText(context);
                tilValue.addView(inputValue);
                layout.addView(tilValue);

                new MaterialAlertDialogBuilder(context)
                    .setTitle("Set Variable")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveBlockUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "variable";
                        block.event = "immediate";
                        block.action = "setVar";
                        block.params = inputName.getText().toString().trim() + "|" + inputValue.getText().toString().trim();
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                        if (listener != null) listener.onBlocksChanged();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "getVar": {
                TextInputLayout tilName = new TextInputLayout(context);
                tilName.setHint("Variable name");
                TextInputEditText inputName = new TextInputEditText(context);
                tilName.addView(inputName);
                layout.addView(tilName);

                TextInputLayout tilTarget = new TextInputLayout(context);
                tilTarget.setHint("Assign to element (CSS selector, or leave empty for console.log)");
                TextInputEditText inputTarget = new TextInputEditText(context);
                tilTarget.addView(inputTarget);
                layout.addView(tilTarget);

                new MaterialAlertDialogBuilder(context)
                    .setTitle("Get Variable")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveBlockUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "variable";
                        block.event = "immediate";
                        block.action = "getVar";
                        block.params = inputName.getText().toString().trim() + "|" + inputTarget.getText().toString().trim();
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                        if (listener != null) listener.onBlocksChanged();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
        }
    }

    // ---- Existing event/action dialogs ----

    private void showTargetDialog(BlockDef eventDef) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        android.widget.Spinner modeSpinner = new android.widget.Spinner(context);
        String[] targetModes = {"By ID", "By Class", "By Tag"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, targetModes);
        modeSpinner.setAdapter(adapter);
        layout.addView(modeSpinner);

        new MaterialAlertDialogBuilder(context)
            .setTitle(eventDef.label + " - Select Target Mode")
            .setView(layout)
            .setPositiveButton("Next", (dialog, which) -> {
                int pos = modeSpinner.getSelectedItemPosition();
                String mode = pos == 0 ? "id" : pos == 1 ? "class" : "tag";
                String hint = pos == 0 ? "Element ID (e.g. myButton)" : pos == 1 ? "CSS class (e.g. btn-primary)" : "HTML tag (e.g. button)";
                showTargetInputForEvent(eventDef, mode, hint);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showTargetInputForEvent(BlockDef eventDef, String mode, String hint) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        TextInputLayout til = new TextInputLayout(context);
        til.setHint(hint);
        TextInputEditText input = new TextInputEditText(context);
        til.addView(input);
        layout.addView(til);

        new MaterialAlertDialogBuilder(context)
            .setTitle("Target (" + mode + ")")
            .setView(layout)
            .setPositiveButton("Next", (d, w) -> {
                String target = input.getText().toString().trim();
                if (target.isEmpty()) {
                    Toast.makeText(context, "Target cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                showActionSelectorForEvent(eventDef, mode, target);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showActionSelectorForEvent(BlockDef eventDef, String targetMode, String target) {
        List<String> labels = new ArrayList<>();
        List<BlockDef> allActions = new ArrayList<>();
        for (BlockDef b : CSS_BLOCKS) { labels.add("[CSS] " + b.label); allActions.add(b); }
        for (BlockDef b : HTML_BLOCKS) { labels.add("[HTML] " + b.label); allActions.add(b); }

        new MaterialAlertDialogBuilder(context)
            .setTitle("Select Action for " + eventDef.label)
            .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                BlockDef actionDef = allActions.get(which);
                showValueInputDialog(eventDef, actionDef, targetMode, target);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showActionTargetDialog(BlockDef actionDef) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        String[] events = {"On Click", "On Hover", "On Load", "On Input", "On Submit", "On Scroll"};
        String[] eventKeys = {"click", "hover", "load", "input", "submit", "scroll"};
        android.widget.Spinner eventSpinner = new android.widget.Spinner(context);
        android.widget.ArrayAdapter<String> eventAdapter = new android.widget.ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, events);
        eventSpinner.setAdapter(eventAdapter);
        layout.addView(eventSpinner);

        String[] targetModes = {"By ID", "By Class", "By Tag"};
        android.widget.Spinner modeSpinner = new android.widget.Spinner(context);
        android.widget.ArrayAdapter<String> modeAdapter = new android.widget.ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, targetModes);
        modeSpinner.setAdapter(modeAdapter);
        layout.addView(modeSpinner);

        TextInputLayout til = new TextInputLayout(context);
        til.setHint("Target Value");
        TextInputEditText input = new TextInputEditText(context);
        til.addView(input);
        layout.addView(til);

        new MaterialAlertDialogBuilder(context)
            .setTitle(actionDef.label + " - Setup")
            .setView(layout)
            .setPositiveButton("Next", (d, w) -> {
                int eventPos = eventSpinner.getSelectedItemPosition();
                String eventKey = eventKeys[eventPos];
                BlockDef eventDef = new BlockDef(eventKey, events[eventPos], "", CAT_EVENT);

                int modePos = modeSpinner.getSelectedItemPosition();
                String mode = modePos == 0 ? "id" : modePos == 1 ? "class" : "tag";

                String target = input.getText().toString().trim();
                if (!target.isEmpty()) {
                    showValueInputDialog(eventDef, actionDef, mode, target);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showValueInputDialog(BlockDef eventDef, BlockDef actionDef, String targetMode, String target) {
        String hint = getValueHint(actionDef);
        String[] presets = getPresets(actionDef);

        if (presets != null && presets.length > 0) {
            new MaterialAlertDialogBuilder(context)
                .setTitle(actionDef.label + " - Value")
                .setItems(presets, (dialog, which) -> {
                    createBlock(eventDef, actionDef, targetMode, target, presets[which]);
                })
                .setNeutralButton("Custom", (dialog, which) -> {
                    showCustomValueInput(eventDef, actionDef, targetMode, target, hint);
                })
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            showCustomValueInput(eventDef, actionDef, targetMode, target, hint);
        }
    }

    private void showCustomValueInput(BlockDef eventDef, BlockDef actionDef, String targetMode, String target, String hint) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);
        TextInputLayout til = new TextInputLayout(context);
        til.setHint(hint);
        TextInputEditText input = new TextInputEditText(context);
        til.addView(input);
        layout.addView(til);

        new MaterialAlertDialogBuilder(context)
            .setTitle(actionDef.label)
            .setView(layout)
            .setPositiveButton("Add", (d, w) -> {
                String value = input.getText().toString().trim();
                createBlock(eventDef, actionDef, targetMode, target, value);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void createBlock(BlockDef eventDef, BlockDef actionDef, String targetMode, String target, String value) {
        saveBlockUndoState();

        String actionKey = mapActionKey(actionDef);
        String eventKey = mapEventKey(eventDef);
        String params = mapParams(actionDef, value);

        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
        block.targetWidget = target;
        block.targetMode = targetMode;
        block.event = eventKey;
        block.action = actionKey;
        block.params = params;

        logicBlockManager.addBlock(block);
        refreshWorkspace();

        if (listener != null) {
            listener.onBlocksChanged();
        }

        Toast.makeText(context, "Block added: " + eventDef.label + " -> " + actionDef.label,
            Toast.LENGTH_SHORT).show();
    }

    private String mapEventKey(BlockDef eventDef) {
        switch (eventDef.id) {
            case "onClick": return "click";
            case "onHover": return "hover";
            case "onLoad": return "load";
            case "onInput": return "input";
            case "onSubmit": return "submit";
            case "onScroll": return "scroll";
            case "onKeyDown": return "keydown";
            case "onChange": return "change";
            default: return eventDef.id;
        }
    }

    private String mapActionKey(BlockDef actionDef) {
        switch (actionDef.id) {
            case "setDisplay": case "setPosition": case "setOverflow":
            case "setColor": case "setBackground": case "setWidth":
            case "setHeight": case "setMargin": case "setPadding":
            case "setBorder": case "setRadius": case "setOpacity":
            case "setFontSize": case "setTextAlign": case "setZIndex":
            case "setFlex":
                return "changeStyle";
            case "addClass": return "addClass";
            case "removeClass": return "removeClass";
            case "toggleClass": return "toggleClass";
            case "setHref": return "setAttribute";
            case "scrollTo": return "scrollTo";
            case "setText": return "setText";
            case "setHTML": return "setHTML";
            case "showElement": return "showHide";
            case "hideElement": return "showHide";
            case "toggleElement": return "showHide";
            case "setAttribute": return "setAttribute";
            case "removeElement": return "removeElement";
            case "focusInput": return "focusInput";
            case "blurInput": return "blurInput";
            case "alert": return "alert";
            case "navigate": return "navigate";
            case "goToPage": return "goToPage";
            default: return actionDef.id;
        }
    }

    private String mapParams(BlockDef actionDef, String value) {
        switch (actionDef.id) {
            case "setDisplay": return "display:" + value;
            case "setPosition": return "position:" + value;
            case "setOverflow": return "overflow:" + value;
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
            case "setTextAlign": return "textAlign:" + value;
            case "setZIndex": return "zIndex:" + value;
            case "setFlex": return "display:flex;" + value;
            case "setHref": return "href:" + value;
            case "showElement": return "show";
            case "hideElement": return "hide";
            case "toggleElement": return "toggle";
            case "focusInput": return "focus";
            case "blurInput": return "blur";
            default: return value;
        }
    }

    private String getValueHint(BlockDef actionDef) {
        switch (actionDef.id) {
            case "setDisplay": return "block, none, flex, grid, inline-block";
            case "setPosition": return "static, relative, absolute, fixed, sticky";
            case "setOverflow": return "hidden, scroll, auto, visible";
            case "setColor": return "Color (e.g. #ff0000, red, rgb(255,0,0))";
            case "setBackground": return "Color or gradient (e.g. #fff, linear-gradient(...))";
            case "setWidth": return "Width (e.g. 100px, 50%, auto)";
            case "setHeight": return "Height (e.g. 200px, 100vh, auto)";
            case "setMargin": return "Margin (e.g. 10px, 10px 20px)";
            case "setPadding": return "Padding (e.g. 10px, 10px 20px)";
            case "setBorder": return "Border (e.g. 1px solid #333)";
            case "setRadius": return "Radius (e.g. 8px, 50%)";
            case "setOpacity": return "Opacity (0 to 1, e.g. 0.5)";
            case "setFontSize": return "Font size (e.g. 16px, 1.2em)";
            case "setTextAlign": return "left, center, right, justify";
            case "setZIndex": return "Stack order (e.g. 10, 100)";
            case "setFlex": return "Flex props (e.g. flex-direction:row;justify-content:center)";
            case "addClass": return "CSS class name to add";
            case "removeClass": return "CSS class name to remove";
            case "toggleClass": return "CSS class name to toggle";
            case "setHref": return "URL or #section-id";
            case "scrollTo": return "top, bottom, or CSS selector";
            case "setText": return "New text content";
            case "setHTML": return "HTML content (e.g. <p>Hello</p>)";
            case "setAttribute": return "attr:value (e.g. disabled:true)";
            case "removeElement": return "CSS selector or 'self'";
            case "focusInput": return "Focus this element";
            case "blurInput": return "Remove focus from element";
            case "alert": return "Alert message";
            case "navigate": return "URL (e.g. https://example.com)";
            case "goToPage": return "Page name (e.g. about)";
            default: return "Value";
        }
    }

    private String[] getPresets(BlockDef actionDef) {
        switch (actionDef.id) {
            case "setDisplay": return new String[]{"block", "none", "flex", "grid", "inline-block", "inline"};
            case "setPosition": return new String[]{"static", "relative", "absolute", "fixed", "sticky"};
            case "setOverflow": return new String[]{"hidden", "scroll", "auto", "visible"};
            case "setTextAlign": return new String[]{"left", "center", "right", "justify"};
            case "showElement": return new String[]{"show"};
            case "hideElement": return new String[]{"hide"};
            case "toggleElement": return new String[]{"toggle"};
            case "scrollTo": return new String[]{"top", "bottom"};
            case "focusInput": return new String[]{"focus"};
            case "blurInput": return new String[]{"blur"};
            default: return null;
        }
    }

    /**
     * Refresh the workspace to show current blocks.
     */
    public void refreshWorkspace() {
        if (blockWorkspace == null) return;
        blockWorkspace.removeAllViews();

        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();

        if (blocks.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Drop blocks here to build logic\nor tap a block from the palette above");
            empty.setTextColor(Color.parseColor("#555555"));
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(16, 48, 16, 48);
            blockWorkspace.addView(empty);
            return;
        }

        if (workspaceCollapsed) {
            TextView collapsed = new TextView(context);
            collapsed.setText(blocks.size() + " blocks (collapsed - tap Expand to view)");
            collapsed.setTextColor(Color.parseColor("#90CAF9"));
            collapsed.setTextSize(12);
            collapsed.setGravity(Gravity.CENTER);
            collapsed.setPadding(16, 32, 16, 32);
            blockWorkspace.addView(collapsed);
            return;
        }

        for (int i = 0; i < blocks.size(); i++) {
            LogicBlockManager.LogicBlock block = blocks.get(i);
            blockWorkspace.addView(createWorkspaceBlock(block, i));
        }
    }

    private View createWorkspaceBlock(LogicBlockManager.LogicBlock block, int index) {
        MaterialCardView card = new MaterialCardView(context);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 3, 0, 3);
        card.setLayoutParams(cardParams);
        card.setCardElevation(3);
        // Style as puzzle piece shape
        card.setRadius(0); // Removing basic radius

        LinearLayout blockView = new LinearLayout(context);

        GradientDrawable blockShape = new GradientDrawable();
        blockShape.setCornerRadii(new float[]{0, 0, 24, 24, 24, 24, 0, 0});
        blockShape.setColor(Color.parseColor("#1A1A1A"));
        blockShape.setStroke(2, Color.parseColor("#333333"));
        blockView.setBackground(blockShape);
        blockView.setOrientation(LinearLayout.VERTICAL);
        blockView.setPadding(14, 10, 14, 10);

        // Determine block type for coloring
        boolean isLogicBlock = "logic".equals(block.targetMode);
        boolean isVarBlock = "variable".equals(block.targetMode);

        if (!isLogicBlock && !isVarBlock) {
            // Target row (for event/css/html blocks)
            LinearLayout targetRow = new LinearLayout(context);
            targetRow.setOrientation(LinearLayout.HORIZONTAL);
            targetRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView targetBadge = new TextView(context);
            String modePrefix = "id".equals(block.targetMode) ? "#" :
                               "class".equals(block.targetMode) ? "." : "";
            targetBadge.setText("TARGET");
            targetBadge.setTextColor(Color.parseColor(COLOR_TARGET));
            targetBadge.setTextSize(10);
            targetBadge.setTypeface(null, Typeface.BOLD);
            targetRow.addView(targetBadge);

            TextView targetValue = new TextView(context);
            targetValue.setText("  " + modePrefix + block.targetWidget);
            targetValue.setTextColor(Color.parseColor("#CE93D8"));
            targetValue.setTextSize(11);
            targetRow.addView(targetValue);

            TextView modeLabel = new TextView(context);
            modeLabel.setText("  (" + block.targetMode + ")");
            modeLabel.setTextColor(Color.parseColor("#666666"));
            modeLabel.setTextSize(9);
            targetRow.addView(modeLabel);

            blockView.addView(targetRow);
        }

        if (isLogicBlock) {
            // Logic block display
            TextView logicLabel = new TextView(context);
            logicLabel.setText(getLogicBlockLabel(block));
            logicLabel.setTextColor(Color.parseColor(COLOR_LOGIC));
            logicLabel.setTextSize(11);
            logicLabel.setTypeface(null, Typeface.BOLD);
            blockView.addView(logicLabel);

            TextView paramsLabel = new TextView(context);
            paramsLabel.setText(formatLogicParams(block));
            paramsLabel.setTextColor(Color.parseColor("#F48FB1"));
            paramsLabel.setTextSize(10);
            blockView.addView(paramsLabel);
        } else if (isVarBlock) {
            // Variable block display
            TextView varLabel = new TextView(context);
            varLabel.setText(getVarBlockLabel(block));
            varLabel.setTextColor(Color.parseColor(COLOR_VARIABLE));
            varLabel.setTextSize(11);
            varLabel.setTypeface(null, Typeface.BOLD);
            blockView.addView(varLabel);

            TextView paramsLabel = new TextView(context);
            paramsLabel.setText(block.params);
            paramsLabel.setTextColor(Color.parseColor("#80DEEA"));
            paramsLabel.setTextSize(10);
            blockView.addView(paramsLabel);
        } else {
            // Event row
            LinearLayout eventRow = new LinearLayout(context);
            eventRow.setOrientation(LinearLayout.HORIZONTAL);

            TextView whenLabel = new TextView(context);
            whenLabel.setText("WHEN ");
            whenLabel.setTextColor(Color.parseColor(COLOR_EVENT));
            whenLabel.setTextSize(11);
            whenLabel.setTypeface(null, Typeface.BOLD);
            eventRow.addView(whenLabel);

            TextView eventLabel = new TextView(context);
            eventLabel.setText(block.event.toUpperCase());
            eventLabel.setTextColor(Color.parseColor("#FFB74D"));
            eventLabel.setTextSize(11);
            eventRow.addView(eventLabel);
            blockView.addView(eventRow);

            // Action row
            LinearLayout actionRow = new LinearLayout(context);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);

            String actionColor = isHtmlAction(block.action) ? COLOR_HTML : COLOR_CSS;

            TextView doLabel = new TextView(context);
            doLabel.setText("DO ");
            doLabel.setTextColor(Color.parseColor(actionColor));
            doLabel.setTextSize(11);
            doLabel.setTypeface(null, Typeface.BOLD);
            actionRow.addView(doLabel);

            TextView actionLabel = new TextView(context);
            actionLabel.setText(block.action + "(" + block.params + ")");
            String actionValueColor = isHtmlAction(block.action) ? "#81C784" : "#64B5F6";
            actionLabel.setTextColor(Color.parseColor(actionValueColor));
            actionLabel.setTextSize(11);
            actionRow.addView(actionLabel);
            blockView.addView(actionRow);
        }

        card.addView(blockView);

        // Enable long-press for actions
        card.setOnLongClickListener(v -> {
            new MaterialAlertDialogBuilder(context)
                .setTitle("Block Actions")
                .setItems(new String[]{"Delete Block", "Copy Block", "Move Up", "Move Down"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            saveBlockUndoState();
                            logicBlockManager.removeBlock(index);
                            refreshWorkspace();
                            if (listener != null) listener.onBlocksChanged();
                            break;
                        case 1:
                            LogicBlockManager.LogicBlock original = logicBlockManager.getBlocks().get(index);
                            blockClipboard = new LogicBlockManager.LogicBlock();
                            blockClipboard.targetWidget = original.targetWidget;
                            blockClipboard.targetMode = original.targetMode;
                            blockClipboard.event = original.event;
                            blockClipboard.action = original.action;
                            blockClipboard.params = original.params;
                            Toast.makeText(context, "Block copied", Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            if (index > 0) {
                                saveBlockUndoState();
                                List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
                                LogicBlockManager.LogicBlock temp = blocks.get(index);
                                blocks.set(index, blocks.get(index - 1));
                                blocks.set(index - 1, temp);
                                refreshWorkspace();
                                if (listener != null) listener.onBlocksChanged();
                            }
                            break;
                        case 3:
                            List<LogicBlockManager.LogicBlock> blocks2 = logicBlockManager.getBlocks();
                            if (index < blocks2.size() - 1) {
                                saveBlockUndoState();
                                LogicBlockManager.LogicBlock temp2 = blocks2.get(index);
                                blocks2.set(index, blocks2.get(index + 1));
                                blocks2.set(index + 1, temp2);
                                refreshWorkspace();
                                if (listener != null) listener.onBlocksChanged();
                            }
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
            return true;
        });

        return card;
    }

    private String getLogicBlockLabel(LogicBlockManager.LogicBlock block) {
        switch (block.action) {
            case "ifBlock": return "IF (condition)";
            case "ifElseBlock": return "IF / ELSE";
            case "delay": return "DELAY (timeout)";
            case "loop": return "LOOP (repeat)";
            default: return "LOGIC: " + block.action;
        }
    }

    private String formatLogicParams(LogicBlockManager.LogicBlock block) {
        String[] parts = block.params.split("\\|");
        switch (block.action) {
            case "ifBlock":
            case "ifElseBlock":
                if (parts.length >= 4) {
                    String result = parts[0] + " " + parts[1] + " " + parts[2] + " -> " + parts[3];
                    if (parts.length >= 5) result += " ELSE -> " + parts[4];
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

    private String getVarBlockLabel(LogicBlockManager.LogicBlock block) {
        switch (block.action) {
            case "createVar": return "VAR CREATE";
            case "setVar": return "VAR SET";
            case "getVar": return "VAR GET";
            default: return "VAR: " + block.action;
        }
    }

    private boolean isHtmlAction(String action) {
        switch (action) {
            case "setText":
            case "appendChild":
            case "showHide":
            case "navigate":
            case "goToPage":
            case "openPage":
            case "scrollTo":
            case "alert":
            case "removeElement":
            case "setAttribute":
            case "removeAttribute":
            case "focusInput":
            case "blurInput":
                return true;
            default:
                return false;
        }
    }

    // Block definition holder
    public static class BlockDef {
        public String id;
        public String label;
        public String description;
        public String category;

        public BlockDef(String id, String label, String description, String category) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.category = category;
        }
    }

    public interface OnBlocksChangedListener {
        void onBlocksChanged();
    }
}
