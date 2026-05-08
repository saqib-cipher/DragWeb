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

    private LinearLayout headerRow;
    private LinearLayout stackSlot;
    private TextView dragHandle;

    interface OnBlockChanged {
        void onBlockChanged(LogicBlockManager.LogicBlock block);
    }

    BlockView(Context context,
              LogicBlockManager.LogicBlock block,
              BlockDef def,
              BlockChipFactory chipFactory,
              OnBlockChanged onChange) {
        super(context);
        this.block = block;
        this.def = def;
        this.chipFactory = chipFactory;
        this.onChange = onChange;

        setOrientation(VERTICAL);
        setTag(TAG_BLOCK_VIEW, this);
        setClipToPadding(false);

        ViewGroup.MarginLayoutParams lp = new MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(2), dp(1), dp(2), dp(1));
        setLayoutParams(lp);

        applyShape();
        buildHeader();
        if (def != null && def.isContainer()) buildStackSlot();
    }

    LogicBlockManager.LogicBlock getBlock() {
        return block;
    }

    BlockDef getDef() {
        return def;
    }

    LinearLayout getStackSlot() {
        return stackSlot;
    }

    boolean isContainer() {
        return stackSlot != null;
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

    private void buildHeader() {
        headerRow = new LinearLayout(getContext());
        headerRow.setOrientation(HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(10), dp(8), dp(10), dp(8));
        addView(headerRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Drag handle on the left – long-press to drag, short-press passthrough.
        dragHandle = new TextView(getContext());
        dragHandle.setText("☰");
        dragHandle.setTextColor(0x66FFFFFF);
        dragHandle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dragHandle.setPadding(dp(2), 0, dp(8), 0);
        headerRow.addView(dragHandle);

        renderTemplate(headerRow);
    }

    private void buildStackSlot() {
        stackSlot = new LinearLayout(getContext());
        stackSlot.setOrientation(VERTICAL);

        int slotColor = 0x33000000;
        GradientDrawable slotBg = new GradientDrawable();
        slotBg.setColor(slotColor);
        slotBg.setCornerRadius(dp(8));
        stackSlot.setBackground(slotBg);

        int margin = dp(8);
        LayoutParams slp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        slp.setMargins(dp(28), margin / 2, margin, margin);
        stackSlot.setLayoutParams(slp);
        stackSlot.setMinimumHeight(dp(36));
        stackSlot.setPadding(dp(4), dp(4), dp(4), dp(4));
        stackSlot.setTag(TAG_BLOCK_VIEW, "stack");

        addView(stackSlot);
    }

    /**
     * Render the block label, splicing inline chips wherever the label or
     * template carries a token. Label tokens win over template tokens so we
     * get human-readable copy like "Set Width %n%m.unit" when present.
     */
    private void renderTemplate(LinearLayout into) {
        String label = def != null && def.label != null ? def.label : (block.action != null ? block.action : "block");
        List<ChipInput> inputs = def != null ? def.resolvedInputs() : new ArrayList<>();
        ensureParamCapacity(inputs.size());

        // Token positions inside the *label* if it has any; otherwise we just
        // append chips after the label text.
        Pattern p = Pattern.compile("%(?:m\\.[a-zA-Z]+|[nsbd]|(\\d+)\\$[sd])");
        Matcher m = p.matcher(label);
        int last = 0;
        int chipIdx = 0;
        boolean anyToken = false;
        while (m.find()) {
            anyToken = true;
            String pre = label.substring(last, m.start());
            if (!pre.isEmpty()) into.addView(buildText(pre));
            if (chipIdx < inputs.size()) {
                into.addView(buildChip(inputs.get(chipIdx), chipIdx));
            }
            chipIdx++;
            last = m.end();
        }
        if (!anyToken) {
            into.addView(buildText(label));
            for (int i = 0; i < inputs.size(); i++) {
                ChipInput ci = inputs.get(i);
                if ("container".equals(ci.type)) continue;
                into.addView(buildChip(ci, i));
            }
        } else {
            String tail = label.substring(last);
            if (!tail.isEmpty()) into.addView(buildText(tail));
            // Append any chips not consumed by tokens.
            for (int i = chipIdx; i < inputs.size(); i++) {
                ChipInput ci = inputs.get(i);
                if ("container".equals(ci.type)) continue;
                into.addView(buildChip(ci, i));
            }
        }
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
        return chipFactory.buildChip(input, value, baseColor, (chipId, newValue) -> {
            setParamValue(index, newValue);
            syncLegacyParams();
            if (onChange != null) onChange.onBlockChanged(block);
        });
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
