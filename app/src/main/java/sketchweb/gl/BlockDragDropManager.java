package sketchweb.gl;

import android.content.ClipData;
import android.content.ClipDescription;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Build;
import android.view.DragEvent;
import android.view.View;
import android.widget.LinearLayout;

/**
 * Drag/drop coordinator for the vertical block workspace.
 *
 * <p>Handles three interaction patterns:
 * <ol>
 *   <li><b>Palette → workspace</b> – tap or drag a palette entry to add a new
 *       block at the bottom of the active stack.</li>
 *   <li><b>Workspace reorder</b> – long-press a block to drag it; release
 *       inserts it at the nearest snap point (between two stack siblings or
 *       inside a container slot).</li>
 *   <li><b>Drop-to-delete</b> – release on the delete bar to remove the block
 *       and any attached children.</li>
 * </ol>
 *
 * <p>No free-floating positioning, no zoom, no pan. Insertion is determined
 * by simple Y-distance comparisons against the laid-out block views, which
 * keeps the algorithm O(n) and avoids any custom Canvas rendering.
 */
final class BlockDragDropManager {

    interface Host {
        WorkspaceView getWorkspace();
        BlockDef findDef(String id);
        BlockChipFactory getChipFactory();
        BlockView.OnBlockChanged getBlockChangedListener();
        void onWorkspaceMutated();
        void saveBlockToCollection(String blockId);
        void duplicateBlock(String blockId);
    }

    private static final String DRAG_LABEL = "sketchweb.block";
    private static final String SOURCE_PALETTE = "palette:";
    private static final String SOURCE_WORKSPACE = "workspace:";

    private final Host host;
    private View activeIndicator;

    BlockDragDropManager(Host host) {
        this.host = host;
    }

    // -------------------------------------------------------------------
    // Palette → workspace
    // -------------------------------------------------------------------

    void attachPaletteSource(View paletteView, BlockDef def) {
        paletteView.setOnLongClickListener(v -> {
            ClipData data = ClipData.newPlainText(DRAG_LABEL, SOURCE_PALETTE + def.id);
            View.DragShadowBuilder shadow = new BlockDragShadow(v);
            if (Build.VERSION.SDK_INT >= 24) {
                v.startDragAndDrop(data, shadow, def, 0);
            } else {
                v.startDrag(data, shadow, def, 0);
            }
            return true;
        });
    }

    // -------------------------------------------------------------------
    // Workspace blocks (long-press to drag, release to snap)
    // -------------------------------------------------------------------

    void attachWorkspaceSource(BlockView blockView) {
        blockView.setOnLongClickListener(v -> {
            ClipData data = ClipData.newPlainText(DRAG_LABEL,
                SOURCE_WORKSPACE + blockView.getBlock().id);
            BlockDragShadow shadow = new BlockDragShadow(v);
            if (Build.VERSION.SDK_INT >= 24) {
                v.startDragAndDrop(data, shadow, blockView, 0);
            } else {
                v.startDrag(data, shadow, blockView, 0);
            }
            return true;
        });
    }

    // -------------------------------------------------------------------
    // Drop targets
    // -------------------------------------------------------------------

    /** Wire the workspace itself as a drop target. */
    void attachWorkspaceTarget(WorkspaceView workspace) {
        workspace.setOnDragListener((v, event) -> handleWorkspaceDrag(workspace, event));
    }

    /** Wire a container slot inside a {@link BlockView} as a drop target. */
    void attachContainerSlot(LinearLayout slot, BlockView host) {
        slot.setOnDragListener((v, event) -> handleSlotDrag(slot, host, event));
    }

    /**
     * Wire a chip view as a drop target for value blocks. Dropping a
     * {@code valueString} / {@code valueNumber} / {@code valueBoolean} /
     * {@code valueColor} block onto a chip fills the chip with the source's
     * rendered value. Source workspace value-blocks are NOT auto-removed –
     * the user can keep them as a reusable reference or delete by hand.
     */
    void attachChipTarget(View chip, OnChipDropAccepted accepted) {
        chip.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return descriptionMatches(event);
                case DragEvent.ACTION_DRAG_ENTERED:
                    chip.setAlpha(0.7f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                case DragEvent.ACTION_DRAG_ENDED:
                    chip.setAlpha(1f);
                    return true;
                case DragEvent.ACTION_DROP:
                    chip.setAlpha(1f);
                    String payload = readPayload(event);
                    String value = resolveValueFromPayload(payload);
                    BlockDef def = resolveDefFromPayload(payload);
                    if (value != null && accepted != null) {
                        accepted.onAccepted(value, def);
                        return true;
                    }
                    return false;
                default:
                    return true;
            }
        });
    }

    /**
     * Resolve a draggable payload to its rendered scalar value. Only succeeds
     * for {@link #isValueDef(BlockDef) value-style block definitions}; any other
     * payload yields {@code null} so the chip can fall through and let the
     * normal workspace-level drop logic handle reordering instead.
     */
    private String resolveValueFromPayload(String payload) {
        if (payload == null) return null;
        if (payload.startsWith(SOURCE_PALETTE)) {
            String defId = payload.substring(SOURCE_PALETTE.length());
            BlockDef def = host.findDef(defId);
            if (def == null || !isValueDef(def)) return null;
            // Use the def's first input default as the rendered value.
            java.util.List<ChipInput> inputs = def.resolvedInputs();
            if (inputs.isEmpty() || inputs.get(0).defaultValue == null) return "";
            StringBuilder sb = new StringBuilder();
            for (ChipInput ci : inputs) sb.append(ci.defaultValue != null ? ci.defaultValue : "");
            return sb.toString();
        }
        if (payload.startsWith(SOURCE_WORKSPACE)) {
            String id = payload.substring(SOURCE_WORKSPACE.length());
            LogicBlockManager mgr = host.getWorkspace() != null
                ? host.getWorkspace().getLogicBlockManager() : null;
            if (mgr == null) return null;
            LogicBlockManager.LogicBlock src = null;
            for (LogicBlockManager.LogicBlock b : mgr.getBlocks()) {
                if (id.equals(b.id)) { src = b; break; }
            }
            if (src == null) return null;
            BlockDef def = host.findDef(src.action);
            if (def == null || !isValueDef(def)) return null;
            // Render the value by concatenating the chip values – mirrors
            // {@code applyChipTemplate} for the simple linear-token case used
            // by every value-shaped block.
            StringBuilder sb = new StringBuilder();
            if (src.paramValues != null) {
                for (String v : src.paramValues) sb.append(v != null ? v : "");
            }
            return sb.toString();
        }
        return null;
    }

    private static boolean isValueDef(BlockDef def) {
        if (def == null) return false;
        if (def.isReporter()) return true;
        return "value".equals(def.category);
    }

    private BlockDef resolveDefFromPayload(String payload) {
        if (payload == null) return null;
        if (payload.startsWith(SOURCE_PALETTE)) {
            return host.findDef(payload.substring(SOURCE_PALETTE.length()));
        }
        if (payload.startsWith(SOURCE_WORKSPACE)) {
            String id = payload.substring(SOURCE_WORKSPACE.length());
            LogicBlockManager mgr = host.getWorkspace() != null
                ? host.getWorkspace().getLogicBlockManager() : null;
            if (mgr == null) return null;
            for (LogicBlockManager.LogicBlock b : mgr.getBlocks()) {
                if (id.equals(b.id)) return host.findDef(b.action);
            }
        }
        return null;
    }

    interface OnChipDropAccepted {
        void onAccepted(String value, BlockDef droppedDef);
    }

    /** Wire the delete bar. */
    void attachDeleteBar(View deleteBar) {
        attachActionTarget(deleteBar, id -> {
            host.getWorkspace().deleteBlockChainById(id);
            host.onWorkspaceMutated();
        });
    }

    /** Wire the save bar. */
    void attachSaveBar(View saveBar) {
        attachActionTarget(saveBar, id -> {
            host.saveBlockToCollection(id);
        });
    }



    /** Wire the duplicate bar. */
    void attachDuplicateBar(View duplicateBar) {
        attachActionTarget(duplicateBar, id -> {
            host.duplicateBlock(id);
            host.onWorkspaceMutated();
        });
    }

    private void attachActionTarget(View target, OnAction action) {
        target.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return descriptionMatches(event);
                case DragEvent.ACTION_DRAG_ENTERED:
                    target.setAlpha(0.6f);
                    target.setScaleX(1.05f);
                    target.setScaleY(1.05f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    target.setAlpha(1f);
                    target.setScaleX(1f);
                    target.setScaleY(1f);
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    target.setAlpha(1f);
                    target.setScaleX(1f);
                    target.setScaleY(1f);
                    return true;
                case DragEvent.ACTION_DROP:
                    target.setAlpha(1f);
                    target.setScaleX(1f);
                    target.setScaleY(1f);
                    String payload = readPayload(event);
                    if (payload != null && payload.startsWith(SOURCE_WORKSPACE)) {
                        String id = payload.substring(SOURCE_WORKSPACE.length());
                        action.onAction(id);
                    }
                    return true;
                default:
                    return true;
            }
        });
    }

    private interface OnAction {
        void onAction(String blockId);
    }

    // -------------------------------------------------------------------
    // Workspace drag handling
    // -------------------------------------------------------------------

    private boolean handleWorkspaceDrag(WorkspaceView workspace, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return descriptionMatches(event);
            case DragEvent.ACTION_DRAG_LOCATION:
                showInsertionIndicator(workspace, event.getY());
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                hideInsertionIndicator();
                return true;
            case DragEvent.ACTION_DROP:
                hideInsertionIndicator();
                return performDrop(workspace, event, null);
            default:
                return true;
        }
    }

    private boolean handleSlotDrag(LinearLayout slot, BlockView slotOwner, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return descriptionMatches(event);
            case DragEvent.ACTION_DRAG_ENTERED:
                slot.setAlpha(0.85f);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                slot.setAlpha(1f);
                return true;
            case DragEvent.ACTION_DROP:
                slot.setAlpha(1f);
                return performDrop(host.getWorkspace(), event, slotOwner);
            default:
                return true;
        }
    }

    private boolean performDrop(WorkspaceView workspace, DragEvent event, BlockView slotOwner) {
        String payload = readPayload(event);
        if (payload == null) return false;

        if (payload.startsWith(SOURCE_PALETTE)) {
            String defId = payload.substring(SOURCE_PALETTE.length());
            BlockDef def = host.findDef(defId);
            if (def == null) return false;
            int index = slotOwner == null
                ? indexFromY(workspace, event.getY())
                : workspace.indexInSlotFromY(slotOwner, event.getY());
            workspace.insertNewBlock(def, slotOwner, index);
            host.onWorkspaceMutated();
            return true;
        }

        if (payload.startsWith(SOURCE_WORKSPACE)) {
            String id = payload.substring(SOURCE_WORKSPACE.length());
            int index = slotOwner == null
                ? indexFromY(workspace, event.getY())
                : workspace.indexInSlotFromY(slotOwner, event.getY());
            workspace.moveBlockTo(id, slotOwner, index);
            host.onWorkspaceMutated();
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------
    // Snapping helpers
    // -------------------------------------------------------------------

    private int indexFromY(WorkspaceView workspace, float y) {
        return workspace.indexFromY(y);
    }

    private void showInsertionIndicator(WorkspaceView workspace, float y) {
        workspace.showInsertionIndicator(y);
    }

    private void hideInsertionIndicator() {
        host.getWorkspace().hideInsertionIndicator();
    }

    private boolean descriptionMatches(DragEvent event) {
        ClipDescription desc = event.getClipDescription();
        return desc != null && desc.getLabel() != null
            && DRAG_LABEL.contentEquals(desc.getLabel());
    }

    private String readPayload(DragEvent event) {
        if (event.getClipData() == null || event.getClipData().getItemCount() == 0) return null;
        CharSequence c = event.getClipData().getItemAt(0).getText();
        return c != null ? c.toString() : null;
    }

    // -------------------------------------------------------------------
    // Drag shadow that mimics the actual block while dragging.
    // -------------------------------------------------------------------

    private static final class BlockDragShadow extends View.DragShadowBuilder {
        private final int shadowAlpha = 0xCC;

        BlockDragShadow(View view) {
            super(view);
        }

        @Override
        public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
            View v = getView();
            int w = Math.max(v.getWidth(), 80);
            int h = Math.max(v.getHeight(), 40);
            outShadowSize.set(w, h);
            outShadowTouchPoint.set(w / 2, h / 2);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            View v = getView();
            Paint dim = new Paint();
            dim.setColor(Color.argb(shadowAlpha, 0, 0, 0));
            canvas.save();
            v.draw(canvas);
            canvas.restore();
        }
    }
}
