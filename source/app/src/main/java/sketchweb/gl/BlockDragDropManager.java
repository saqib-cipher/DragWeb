package sketchweb.gl;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
 * Provides visual block palettes for Events, CSS Actions, and HTML Actions
 * that can be dragged into a workspace to build logic flows.
 */
public class BlockDragDropManager {

    // Block categories
    public static final String CAT_EVENT = "event";
    public static final String CAT_CSS = "css";
    public static final String CAT_HTML = "html";

    // Colors for block categories
    private static final String COLOR_EVENT = "#FF9800";
    private static final String COLOR_EVENT_BG = "#2D1F00";
    private static final String COLOR_CSS = "#2196F3";
    private static final String COLOR_CSS_BG = "#001F3D";
    private static final String COLOR_HTML = "#4CAF50";
    private static final String COLOR_HTML_BG = "#002D00";
    private static final String COLOR_TARGET = "#9C27B0";
    private static final String COLOR_TARGET_BG = "#1A0025";

    private Context context;
    private LogicBlockManager logicBlockManager;
    private LinearLayout blockWorkspace;
    private LinearLayout blockPalette;
    private OnBlocksChangedListener listener;

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
        new BlockDef("alert", "Show Alert", "Browser alert dialog", CAT_HTML),
        new BlockDef("navigate", "Navigate", "Go to URL", CAT_HTML),
        new BlockDef("goToPage", "Go To Page", "Navigate to project page", CAT_HTML),
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

        // Block palette (horizontal scrollable)
        root.addView(buildBlockPalette());

        // Target info hint
        TextView hint = new TextView(context);
        hint.setText("Drag blocks below to build logic. Long-press to delete.");
        hint.setTextSize(11);
        hint.setTextColor(Color.parseColor("#888888"));
        hint.setPadding(16, 8, 16, 4);
        root.addView(hint);

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

        Chip chipCss = createFilterChip("CSS Actions", COLOR_CSS);
        chipCss.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) showCategory(CAT_CSS);
        });

        Chip chipHtml = createFilterChip("HTML Actions", COLOR_HTML);
        chipHtml.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) showCategory(CAT_HTML);
        });

        chipGroup.addView(chipEvent);
        chipGroup.addView(chipCss);
        chipGroup.addView(chipHtml);
        hsv.addView(chipGroup);
        return hsv;
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

    private void showCategory(String category) {
        if (blockPalette == null) return;
        blockPalette.removeAllViews();

        BlockDef[] blocks;
        switch (category) {
            case CAT_CSS: blocks = CSS_BLOCKS; break;
            case CAT_HTML: blocks = HTML_BLOCKS; break;
            default: blocks = EVENT_BLOCKS; break;
        }

        for (BlockDef def : blocks) {
            blockPalette.addView(createPaletteBlock(def));
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
            default: bgColor = COLOR_EVENT_BG; textColor = COLOR_EVENT; break;
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10);
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

    /**
     * Show dialog to configure a block dropped from the palette.
     */
    public void showAddBlockFromPalette(BlockDef def) {
        if (CAT_EVENT.equals(def.category)) {
            // Event block: need target + then let user add actions
            showTargetDialog(def);
        } else {
            // Action block: need target + value
            showActionTargetDialog(def);
        }
    }

    private void showTargetDialog(BlockDef eventDef) {
        String[] targetModes = {"By ID", "By Class", "By Tag"};
        new MaterialAlertDialogBuilder(context)
            .setTitle(eventDef.label + " - Select Target")
            .setItems(targetModes, (dialog, which) -> {
                String mode;
                String hint;
                switch (which) {
                    case 0: mode = "id"; hint = "Element ID (e.g. myButton)"; break;
                    case 1: mode = "class"; hint = "CSS class (e.g. btn-primary)"; break;
                    default: mode = "tag"; hint = "HTML tag (e.g. button)"; break;
                }
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
        // Show all CSS + HTML actions as choices
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
        // For standalone action blocks, pick event + target + value
        String[] events = {"On Click", "On Hover", "On Load", "On Input", "On Submit", "On Scroll"};
        String[] eventKeys = {"click", "hover", "load", "input", "submit", "scroll"};

        new MaterialAlertDialogBuilder(context)
            .setTitle(actionDef.label + " - Select Event")
            .setItems(events, (dialog, which) -> {
                String eventKey = eventKeys[which];
                BlockDef eventDef = new BlockDef(eventKey, events[which], "", CAT_EVENT);

                String[] targetModes = {"By ID", "By Class", "By Tag"};
                new MaterialAlertDialogBuilder(context)
                    .setTitle("Select Target")
                    .setItems(targetModes, (d2, w2) -> {
                        String mode = w2 == 0 ? "id" : w2 == 1 ? "class" : "tag";
                        String hint = w2 == 0 ? "Element ID" : w2 == 1 ? "CSS class" : "HTML tag";

                        LinearLayout layout = new LinearLayout(context);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(48, 16, 48, 0);
                        TextInputLayout til = new TextInputLayout(context);
                        til.setHint(hint);
                        TextInputEditText input = new TextInputEditText(context);
                        til.addView(input);
                        layout.addView(til);

                        new MaterialAlertDialogBuilder(context)
                            .setTitle("Enter Target")
                            .setView(layout)
                            .setPositiveButton("Next", (d3, w3) -> {
                                String target = input.getText().toString().trim();
                                if (!target.isEmpty()) {
                                    showValueInputDialog(eventDef, actionDef, mode, target);
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showValueInputDialog(BlockDef eventDef, BlockDef actionDef, String targetMode, String target) {
        String hint = getValueHint(actionDef);
        String[] presets = getPresets(actionDef);

        if (presets != null && presets.length > 0) {
            // Show preset options
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
        // Map block def to LogicBlockManager action key
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
            case "setDisplay": return "changeStyle";
            case "setPosition": return "changeStyle";
            case "setOverflow": return "changeStyle";
            case "setColor": return "changeStyle";
            case "setBackground": return "changeStyle";
            case "setWidth": return "changeStyle";
            case "setHeight": return "changeStyle";
            case "setMargin": return "changeStyle";
            case "setPadding": return "changeStyle";
            case "setBorder": return "changeStyle";
            case "setRadius": return "changeStyle";
            case "setOpacity": return "changeStyle";
            case "setFontSize": return "changeStyle";
            case "setTextAlign": return "changeStyle";
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
            case "setHref": return "href:" + value;
            case "showElement": return "show";
            case "hideElement": return "hide";
            case "toggleElement": return "toggle";
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
            case "addClass": return "CSS class name to add";
            case "removeClass": return "CSS class name to remove";
            case "toggleClass": return "CSS class name to toggle";
            case "setHref": return "URL or #section-id";
            case "scrollTo": return "top, bottom, or CSS selector";
            case "setText": return "New text content";
            case "setHTML": return "HTML content (e.g. <p>Hello</p>)";
            case "setAttribute": return "attr:value (e.g. disabled:true)";
            case "removeElement": return "CSS selector or 'self'";
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
        card.setRadius(10);

        LinearLayout blockView = new LinearLayout(context);
        blockView.setOrientation(LinearLayout.VERTICAL);
        blockView.setPadding(14, 10, 14, 10);

        // Target row
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

        // Mode indicator
        TextView modeLabel = new TextView(context);
        modeLabel.setText("  (" + block.targetMode + ")");
        modeLabel.setTextColor(Color.parseColor("#666666"));
        modeLabel.setTextSize(9);
        targetRow.addView(modeLabel);

        blockView.addView(targetRow);

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

        // Determine action color
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

        card.addView(blockView);

        // Enable drag reorder
        card.setOnLongClickListener(v -> {
            new MaterialAlertDialogBuilder(context)
                .setTitle("Block Actions")
                .setItems(new String[]{"Delete Block", "Move Up", "Move Down"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            logicBlockManager.removeBlock(index);
                            refreshWorkspace();
                            if (listener != null) listener.onBlocksChanged();
                            break;
                        case 1:
                            if (index > 0) {
                                List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
                                LogicBlockManager.LogicBlock temp = blocks.get(index);
                                blocks.set(index, blocks.get(index - 1));
                                blocks.set(index - 1, temp);
                                refreshWorkspace();
                                if (listener != null) listener.onBlocksChanged();
                            }
                            break;
                        case 2:
                            List<LogicBlockManager.LogicBlock> blocks2 = logicBlockManager.getBlocks();
                            if (index < blocks2.size() - 1) {
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
