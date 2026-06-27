package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A single rendered block in the vertical workspace.
 *
 * <p>Replaces the previous Canvas-drawn puzzle path with a lightweight
 * {@link LinearLayout}. Visual style: rounded compact pill, category color
 * fill, subtle shadow, inline editable chips.
 *
 * <p>Container blocks (cblock / loop / condition) expose an interior
 * {@link #getStackSlot() stack slot} other block views can be inserted into,
 * giving nested rendering without nested {@code MaterialCardView}s.
 */
class BlockView extends LinearLayout {

    /** Tag used by the drag/drop system to identify a block view at runtime. */
    static final int TAG_BLOCK_VIEW = 0x0F010001;

    private final LogicBlockManager.LogicBlock block;
    private final BlockDef def;
    private final BlockChipFactory chipFactory;
    private final OnBlockChanged onChange;
    private final BlockDragDropManager dragDropManager;
    private boolean autoExpanded = false;
    private boolean dropReceived = false;

    private LinearLayout headerRow;
    private LinearLayout stackSlot;
    private TextView dragHandle;
    private final Handler collapseHandler = new Handler(Looper.getMainLooper());
    private final Runnable collapseRunnable;

    interface OnBlockChanged {
        void onBlockChanged(LogicBlockManager.LogicBlock block);
    }

    BlockView(Context context,
              LogicBlockManager.LogicBlock block,
              BlockDef def,
              BlockChipFactory chipFactory,
              BlockDragDropManager dragDropManager,
              OnBlockChanged onChange) {
        super(context);
        this.block = block;
        this.def = def;
        this.chipFactory = chipFactory;
        this.dragDropManager = dragDropManager;
        this.onChange = onChange;
        this.collapseRunnable = () -> {
            if (autoExpanded) {
                autoExpanded = false;
                this.block.collapsed = true;
                applyCollapsedState(true, true);
                if (this.onChange != null) this.onChange.onBlockChanged(this.block);
            }
        };

        setOrientation(VERTICAL);
        setTag(TAG_BLOCK_VIEW, this);
        setClipToPadding(false);

        ViewGroup.MarginLayoutParams lp = new MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(2), dp(1), dp(2), dp(1));
        setLayoutParams(lp);

        applyShape();
        buildViewHierarchy();

        if (!stackSlots.isEmpty()) {
            attachContainerExpandBehavior();
            applyCollapsedState(block.collapsed, false);
        }
    }

    private final List<LinearLayout> stackSlots = new ArrayList<>();
    private LinearLayout firstHeaderRow;

    private void buildViewHierarchy() {
        stackSlots.clear();
        removeAllViews();

        String label = block.labelOverride != null ? block.labelOverride : (def != null && def.label != null ? def.label : (block.action != null ? block.action : "block"));
        List<ChipInput> inputs = block.inputsOverride != null ? block.inputsOverride : (def != null ? def.resolvedInputs() : new ArrayList<>());
        ensureParamCapacity(inputs.size());

        boolean templateHasSpace = label.contains("%m.space");
        boolean isContainer = (def != null && def.isContainer()) || (block != null && block.spec != null && block.spec.contains("%m.space"));

        Pattern p = Pattern.compile("%(?:(?:(\\d+)\\$)?(?:m\\.([a-zA-Z_\\.]+)|([nsbd]))|(selector))");
        Matcher m = p.matcher(label);
        
        int last = 0;
        int chipIdx = 0;
        int slotIdx = 0;

        LinearLayout currentRow = createHeaderRow();
        firstHeaderRow = currentRow;
        addDragHandle(currentRow);
        addView(currentRow);

        while (m.find()) {
            String pre = label.substring(last, m.start());
            if (!pre.isEmpty()) {
                currentRow.addView(buildText(pre));
            }

            String mType = m.group(2);
            if ("space".equals(mType)) {
                LinearLayout slot = createSlotView(slotIdx);
                addView(slot);
                stackSlots.add(slot);
                slotIdx++;
                
                currentRow = createHeaderRow();
                addView(currentRow);
            } else {
                if (chipIdx < inputs.size()) {
                    currentRow.addView(buildChip(inputs.get(chipIdx), chipIdx));
                }
                chipIdx++;
            }
            last = m.end();
        }

        String tail = label.substring(last);
        if (!tail.isEmpty()) {
            currentRow.addView(buildText(tail));
        }

        for (int i = chipIdx; i < inputs.size(); i++) {
            ChipInput ci = inputs.get(i);
            if ("container".equals(ci.type)) continue;
            currentRow.addView(buildChip(ci, i));
        }

        if (isContainer && !templateHasSpace) {
            LinearLayout slot = createSlotView(slotIdx);
            addView(slot);
            stackSlots.add(slot);
        }
        
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child instanceof LinearLayout && child != firstHeaderRow) {
                LinearLayout row = (LinearLayout) child;
                Object tag = row.getTag(TAG_BLOCK_VIEW);
                if (tag instanceof String && ((String) tag).startsWith("stack_")) continue;
                if (row.getChildCount() == 0) {
                    removeViewAt(i);
                }
            }
        }

        headerRow = firstHeaderRow;
    }

    private LinearLayout createHeaderRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void addDragHandle(LinearLayout row) {
        dragHandle = new TextView(getContext());
        boolean hasSlots = (def != null && def.isContainer()) || (block != null && block.spec != null && block.spec.contains("%m.space")) || !stackSlots.isEmpty();
        dragHandle.setText(hasSlots ? (block.collapsed ? "▶" : "▼") : "☰");
        dragHandle.setTextColor(0x99FFFFFF);
        dragHandle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        dragHandle.setPadding(dp(4), 0, dp(10), 0);
        row.addView(dragHandle);
    }

    private LinearLayout createSlotView(int slotIndex) {
        LinearLayout slot = new LinearLayout(getContext());
        slot.setOrientation(VERTICAL);

        int slotColor = 0x33000000;
        GradientDrawable slotBg = new GradientDrawable();
        slotBg.setColor(slotColor);
        slotBg.setCornerRadius(dp(8));
        slot.setBackground(slotBg);

        int margin = dp(8);
        LayoutParams slp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        slp.setMargins(dp(28), margin / 2, margin, margin);
        slot.setLayoutParams(slp);
        slot.setMinimumHeight(dp(36));
        slot.setPadding(dp(4), dp(4), dp(4), dp(4));
        slot.setTag(TAG_BLOCK_VIEW, "stack_" + slotIndex);

        return slot;
    }

    /**
     * Unified expand/collapse behavior for any container block.
     * Click the drag handle to toggle, or hover over while dragging to auto-expand.
     */
    private void attachContainerExpandBehavior() {
        if (headerRow == null) return;

        applyCollapsedState(block.collapsed, false);

        if (dragHandle != null) {
            dragHandle.setOnClickListener(v -> {
                block.collapsed = !block.collapsed;
                applyCollapsedState(block.collapsed, true);
                if (onChange != null) onChange.onBlockChanged(block);
            });
            dragHandle.setText(block.collapsed ? "▶" : "▼");
        }

        headerRow.setOnDragListener(new android.view.View.OnDragListener() {
            @Override
            public boolean onDrag(android.view.View v, android.view.DragEvent event) {
                switch (event.getAction()) {
                    case android.view.DragEvent.ACTION_DRAG_STARTED: return true;
                    case android.view.DragEvent.ACTION_DRAG_ENTERED:
                        collapseHandler.removeCallbacks(collapseRunnable);
                        if (block.collapsed) {
                            block.collapsed = false;
                            applyCollapsedState(false, true);
                            autoExpanded = true;
                            if (onChange != null) onChange.onBlockChanged(block);
                        }
                        setHeaderHighlight(true);
                        return true;
                    case android.view.DragEvent.ACTION_DRAG_EXITED:
                        setHeaderHighlight(false);
                        if (autoExpanded) {
                            collapseHandler.postDelayed(collapseRunnable, 500);
                        }
                        return true;
                    case android.view.DragEvent.ACTION_DRAG_ENDED:
                        collapseHandler.removeCallbacks(collapseRunnable);
                        if (autoExpanded && !dropReceived) {
                            autoExpanded = false;
                            block.collapsed = true;
                            applyCollapsedState(true, true);
                            if (onChange != null) onChange.onBlockChanged(block);
                        }
                        autoExpanded = false;
                        dropReceived = false;
                        setHeaderHighlight(false);
                        return true;
                    case android.view.DragEvent.ACTION_DROP:
                        collapseHandler.removeCallbacks(collapseRunnable);
                        dropReceived = true;
                        autoExpanded = false; 
                        setHeaderHighlight(false);
                        return false;
                    default: return false;
                }
            }
        });

        for (LinearLayout slot : stackSlots) {
            slot.setOnDragListener(new android.view.View.OnDragListener() {
                @Override
                public boolean onDrag(android.view.View v, android.view.DragEvent event) {
                    switch (event.getAction()) {
                        case android.view.DragEvent.ACTION_DRAG_STARTED:
                            return true;
                        case android.view.DragEvent.ACTION_DRAG_ENTERED:
                            collapseHandler.removeCallbacks(collapseRunnable);
                            return true;
                        case android.view.DragEvent.ACTION_DRAG_EXITED:
                            if (autoExpanded) {
                                collapseHandler.postDelayed(collapseRunnable, 500);
                            }
                            return true;
                        case android.view.DragEvent.ACTION_DROP:
                            collapseHandler.removeCallbacks(collapseRunnable);
                            dropReceived = true;
                            autoExpanded = false;
                            return false;
                        case android.view.DragEvent.ACTION_DRAG_ENDED:
                            collapseHandler.removeCallbacks(collapseRunnable);
                            return true;
                    }
                    return false;
                }
            });
        }
    }

    /** Animate the show/hide transition so the workspace doesn't snap. */
    private void applyCollapsedState(boolean collapsed, boolean animate) {
        int childCount = getChildCount();
        for (int i = 1; i < childCount; i++) {
            View child = getChildAt(i);
            if (animate && child.getVisibility() == (collapsed ? VISIBLE : GONE)) {
                child.setAlpha(collapsed ? 1f : 0f);
                child.setVisibility(VISIBLE);
                child.animate()
                    .alpha(collapsed ? 0f : 1f)
                    .setDuration(140)
                    .withEndAction(() -> child.setVisibility(collapsed ? GONE : VISIBLE))
                    .start();
            } else {
                child.setVisibility(collapsed ? GONE : VISIBLE);
                child.setAlpha(1f);
            }
        }
        if (dragHandle != null) dragHandle.setText(collapsed ? "▶" : "▼");
    }

    private void setHeaderHighlight(boolean on) {
        if (headerRow == null) return;
        headerRow.setAlpha(on ? 0.85f : 1f);
    }

    LogicBlockManager.LogicBlock getBlock() {
        return block;
    }

    BlockDef getDef() {
        return def;
    }

    LinearLayout getStackSlot() {
        return stackSlots.isEmpty() ? null : stackSlots.get(0);
    }

    List<LinearLayout> getStackSlots() {
        return stackSlots;
    }

    boolean isContainer() {
        return !stackSlots.isEmpty();
    }

    /** Highlight when a drop is hovering directly above/below this block. */
    void setDropHighlight(boolean above, boolean below) {
        int top = above ? dp(3) : 0;
        int bot = below ? dp(3) : 0;
        if (headerRow != null) {
            headerRow.setPadding(headerRow.getPaddingLeft(), dp(8) + top, headerRow.getPaddingRight(), dp(8) + bot);
        }
    }

    // -------------------------------------------------------------------
    // Building blocks
    // -------------------------------------------------------------------

    private void applyShape() {
        int color = def != null ? Color.parseColor(def.resolvedColor()) : BlockCategoryPalette.colorIntForCategory(block.category);
        int outline = BlockCategoryPalette.darken(color);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setStroke(dp(2), outline);
        String shape = def != null ? def.resolvedShape() : "stack";

        float r;
        switch (shape) {
            case "event":   r = dp(20); break;
            case "value":
            case "reporter": r = dp(22); break;
            case "boolean": r = dp(18); break;
            default:        r = dp(10); break;
        }
        bg.setCornerRadius(r);
        setBackground(bg);
        setElevation(dp(2));
    }

    private TextView buildText(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text.trim());
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        int pad = dp(4);
        tv.setPadding(pad, 0, pad, 0);
        return tv;
    }

    private View buildChip(ChipInput input, int index) {
        String value = paramValueAt(index);
        int baseColor = def != null ? Color.parseColor(def.resolvedColor()) : BlockCategoryPalette.colorIntForCategory(block.category);
        boolean isAsd = block != null && ("asd".equals(block.category) || (def != null && "asd".equals(def.category)));
        View chip = chipFactory.buildChip(isAsd, input, value, baseColor, (chipId, newValue) -> {
            setParamValue(index, newValue);
            syncLegacyParams();
            if (onChange != null) onChange.onBlockChanged(block);
        });
        if (dragDropManager != null) {
            dragDropManager.attachChipTarget(chip, (accepted, droppedDef) -> {
                if (droppedDef != null && droppedDef.label != null && droppedDef.label.contains("%")) {
                    expandChipWithBlock(index, droppedDef);
                } else {
                    setParamValue(index, accepted);
                    syncLegacyParams();
                    refreshChipTextIfTextView(chip, accepted);
                    if (onChange != null) onChange.onBlockChanged(block);
                }
            });
        }
        return chip;
    }

    private void expandChipWithBlock(int index, BlockDef droppedDef) {
        String currentLabel = block.labelOverride != null ? block.labelOverride : (def != null && def.label != null ? def.label : (block.action != null ? block.action : "block"));
        List<ChipInput> currentInputs = block.inputsOverride != null ? new ArrayList<>(block.inputsOverride) : (def != null ? new ArrayList<>(def.resolvedInputs()) : new ArrayList<>());
        
        Pattern p = Pattern.compile("%(?:(?:(\\d+)\\$)?(?:m\\.([a-zA-Z_\\.]+)|([nsbd]))|(selector))");
        Matcher m = p.matcher(currentLabel);
        int currentChipIdx = 0;
        int tokenStart = -1;
        int tokenEnd = -1;
        while (m.find()) {
            if (currentChipIdx == index) {
                tokenStart = m.start();
                tokenEnd = m.end();
                break;
            }
            currentChipIdx++;
        }
        
        if (tokenStart != -1) {
            String droppedLabel = droppedDef.label;
            List<ChipInput> droppedInputs = droppedDef.resolvedInputs();
            
            String newLabel = currentLabel.substring(0, tokenStart) + droppedLabel + currentLabel.substring(tokenEnd);
            
            if (index < currentInputs.size()) currentInputs.remove(index);
            if (index < block.paramValues.size()) block.paramValues.remove(index);
            
            for (int i = 0; i < droppedInputs.size(); i++) {
                currentInputs.add(index + i, droppedInputs.get(i));
                String defaultVal = droppedInputs.get(i).defaultValue != null ? droppedInputs.get(i).defaultValue : "";
                if (block.paramValues.size() >= index + i) {
                    block.paramValues.add(index + i, defaultVal);
                } else {
                    block.paramValues.add(defaultVal);
                }
            }
            
            if (block.spec != null) {
                Matcher sm = p.matcher(block.spec);
                int specTokenStart = -1;
                int specTokenEnd = -1;
                int specChipIdx = 0;
                while (sm.find()) {
                    if (specChipIdx == index) {
                        specTokenStart = sm.start();
                        specTokenEnd = sm.end();
                        break;
                    }
                    specChipIdx++;
                }
                if (specTokenStart != -1 && droppedDef.code != null) {
                    block.spec = block.spec.substring(0, specTokenStart) + droppedDef.code + block.spec.substring(specTokenEnd);
                }
            }
            
            block.labelOverride = newLabel;
            block.inputsOverride = currentInputs;
            syncLegacyParams();
            
            buildViewHierarchy();
            if (onChange != null) onChange.onBlockChanged(block);
        }
    }

    private void refreshChipTextIfTextView(View chip, String text) {
        if (chip instanceof TextView) {
            ((TextView) chip).setText(text == null || text.isEmpty() ? "..." : text);
        }
    }

    private void ensureParamCapacity(int n) {
        if (block.paramValues == null) block.paramValues = new ArrayList<>();
        // Migrate older blocks that only carry the legacy pipe-joined `params`
        // string – split it once into discrete chip values so the modern
        // editor can show them.
        if (block.paramValues.isEmpty() && block.params != null && !block.params.isEmpty()) {
            String[] parts = block.params.split("\\|", -1);
            for (String p : parts) block.paramValues.add(p);
        }
        while (block.paramValues.size() < n) block.paramValues.add("");
        if (def == null) return;
        List<ChipInput> ins = def.resolvedInputs();
        for (int i = 0; i < ins.size() && i < block.paramValues.size(); i++) {
            String v = block.paramValues.get(i);
            if ((v == null || v.isEmpty()) && ins.get(i).defaultValue != null) {
                block.paramValues.set(i, ins.get(i).defaultValue);
            }
        }
    }

    private String paramValueAt(int index) {
        if (block.paramValues != null && index < block.paramValues.size()) {
            return block.paramValues.get(index);
        }
        return "";
    }

    private void setParamValue(int index, String value) {
        if (block.paramValues == null) block.paramValues = new ArrayList<>();
        while (block.paramValues.size() <= index) block.paramValues.add("");
        block.paramValues.set(index, value != null ? value : "");
    }

    /**
     * Mirror {@code paramValues} into the legacy {@code params} string so
     * {@link LogicBlockManager} code generators (which still read
     * {@code params}) keep producing the same output.
     */
    private void syncLegacyParams() {
        if (block.paramValues == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < block.paramValues.size(); i++) {
            if (i > 0) sb.append('|');
            String v = block.paramValues.get(i);
            sb.append(v != null ? v : "");
        }
        block.params = sb.toString();
    }

    private int dp(int px) {
        return (int) (px * getResources().getDisplayMetrics().density);
    }
}
