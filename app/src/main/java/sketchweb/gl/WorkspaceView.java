package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vertical Sketchware/Blockly-style block workspace.
 *
 * <p>This is the modern replacement for the old free-canvas
 * {@code WorkspaceView}. Blocks are stacked top-to-bottom in a single
 * scrollable column. Container blocks (cblock / loop / condition) render an
 * interior slot that other blocks can be nested into. There is no zoom,
 * no pan, no free positioning.
 *
 * <p>Identity and ordering are tracked by {@link LogicBlockManager.LogicBlock#id}
 * and {@link LogicBlockManager.LogicBlock#parentBlockId}. Sibling order is
 * implied by position inside the master list.
 */
public class WorkspaceView extends ScrollView {

    private final LinearLayout stack;
    private final View insertionIndicator;
    private final Map<String, BlockView> viewByBlockId = new HashMap<>();

    private LogicBlockManager logicBlockManager;
    private OnBlockInteractionListener interactionListener;
    private List<BlockDef> defs = new ArrayList<>();
    private BlockChipFactory chipFactory;
    private BlockDragDropManager dragDropManager;

    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1.0f;
    private float translationX = 0f;
    private float translationY = 0f;
    private float lastTouchX;
    private float lastTouchY;
    private int activePointerId = -1;
    private boolean isPanning = false;

    private void initZoomPan(Context context) {
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureListener());
    }

    private class ScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.8f, Math.min(scaleFactor, 1.2f));

            View container = getChildAt(0);
            if (container != null) {
                container.setScaleX(scaleFactor);
                container.setScaleY(scaleFactor);
            }
            return true;
        }
    }

    public WorkspaceView(Context context) { this(context, null); }
    public WorkspaceView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }

    public WorkspaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setFillViewport(true);
        initZoomPan(context);

        // Top-level container so we can overlay an insertion indicator on top
        // of the vertical stack without breaking ScrollView's single-child rule.
        FrameContainer container = new FrameContainer(context);
        addView(container, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        stack = new LinearLayout(context);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setPadding(dp(8), dp(8), dp(8), dp(120));
        container.addView(stack, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        insertionIndicator = new View(context);
        insertionIndicator.setBackgroundColor(Color.parseColor("#FFEB3B"));
        insertionIndicator.setVisibility(GONE);
        container.addView(insertionIndicator, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        scaleGestureDetector.onTouchEvent(ev);

        if (ev.getPointerCount() > 1) {
            requestDisallowInterceptTouchEvent(true);
        }

        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                lastTouchX = ev.getX();
                lastTouchY = ev.getY();
                activePointerId = ev.getPointerId(0);
                isPanning = false;
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                isPanning = false;
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (ev.getPointerCount() == 1 && activePointerId != -1) {
                    int pointerIndex = ev.findPointerIndex(activePointerId);
                    if (pointerIndex != -1) {
                        float x = ev.getX(pointerIndex);
                        float y = ev.getY(pointerIndex);

                        float dx = x - lastTouchX;
                        float dy = y - lastTouchY;

                        translationX += dx;
                        translationY += dy;

                        float maxPanX = dp(50);
                        float maxPanY = dp(30);

                        translationX = Math.max(-maxPanX, Math.min(translationX, maxPanX));
                        translationY = Math.max(-maxPanY, Math.min(translationY, maxPanY));

                        View container = getChildAt(0);
                        if (container != null) {
                            container.setTranslationX(translationX);
                            container.setTranslationY(translationY);
                        }

                        lastTouchX = x;
                        lastTouchY = y;
                        isPanning = true;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                activePointerId = -1;
                isPanning = false;
                break;
            }
        }

        return super.onTouchEvent(ev);
    }

    /**
     * Wire the workspace to its data backend, definition table and chip
     * factory. Called once from the activity during onCreate.
     */
    public void configure(LogicBlockManager manager,
                          List<BlockDef> definitions,
                          BlockChipFactory chipFactory,
                          BlockDragDropManager dragDrop) {
        this.logicBlockManager = manager;
        this.defs = definitions != null ? definitions : new ArrayList<>();
        this.chipFactory = chipFactory;
        this.dragDropManager = dragDrop;
        if (dragDrop != null) dragDrop.attachWorkspaceTarget(this);
    }

    /** Back-compat: kept so older call sites still link. */
    public void setLogicBlockManager(LogicBlockManager manager) {
        this.logicBlockManager = manager;
    }

    /** Resolve the active manager so the drag/drop layer can introspect blocks. */
    public LogicBlockManager getLogicBlockManager() {
        return logicBlockManager;
    }

    /**
     * Re-render the entire stack from the current
     * {@link LogicBlockManager#getBlocks()} list. Cheap because each block view
     * is a flat {@link android.widget.LinearLayout LinearLayout} – no Canvas
     * paths, no large RecyclerView item recycling overhead.
     */
    public void rebuild() {
        stack.removeAllViews();
        viewByBlockId.clear();
        if (logicBlockManager == null) return;
        renderChildrenInto(stack, null);
    }

    /** Back-compat: behaves identically to {@link #rebuild()}. */
    public void setBlocks(List<LogicBlockManager.LogicBlock> blocks) {
        if (logicBlockManager != null && blocks != null && blocks != logicBlockManager.getBlocks()) {
            logicBlockManager.getBlocks().clear();
            logicBlockManager.getBlocks().addAll(blocks);
        }
        rebuild();
    }

    public void setOnBlockInteractionListener(OnBlockInteractionListener listener) {
        this.interactionListener = listener;
    }

    /**
     * Back-compat shim: the new editor handles drops via
     * {@link BlockDragDropManager} but we keep the old API so {@code
     * LogicBlockActivity} compiles unchanged when it routes through us.
     */
    public void setupDropListener(OnBlockDroppedListener listener) {
        // No-op: the modern editor uses BlockDragDropManager for snap-style
        // drops. The legacy free-drop callback no longer fires.
    }

    // -------------------------------------------------------------------
    // Insertion / move / delete API used by BlockDragDropManager
    // -------------------------------------------------------------------

    int indexFromY(float y) {
        // ScrollView delivers DragEvent Y relative to the visible viewport, so
        // add the scroll offset to land in the inner content's coordinate
        // space before comparing against block midpoints.
        float adjustedY = (y - translationY) / scaleFactor;
        float content = adjustedY + getScrollY();
        return computeInsertIndexAbsolute(stack, content);
    }

    int indexInSlotFromY(BlockView container, float y) {
        LinearLayout slot = container.getStackSlot();
        if (slot == null) return 0;
        // y comes from a drag listener attached to the slot itself, so it is
        // already in slot-local coordinates.
        return computeInsertIndexLocal(slot, y);
    }

    /**
     * Coordinate-space-aware insertion search: {@code y} is in the same
     * coordinate system as {@code host.getTop() + child.getTop()}. The stack
     * lives at {@code FrameContainer (0,0)} so we just compare child tops.
     */
    private int computeInsertIndexAbsolute(LinearLayout host, float y) {
        int count = host.getChildCount();
        float baseTop = host.getTop();
        for (int i = 0; i < count; i++) {
            View child = host.getChildAt(i);
            float top = baseTop + child.getTop();
            float midpoint = top + child.getHeight() / 2f;
            if (y < midpoint) return i;
        }
        return count;
    }

    private int computeInsertIndexLocal(LinearLayout host, float y) {
        int count = host.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = host.getChildAt(i);
            float midpoint = child.getTop() + child.getHeight() / 2f;
            if (y < midpoint) return i;
        }
        return count;
    }

    void insertNewBlock(BlockDef def, @Nullable BlockView containerView, int siblingIndex) {
        if (logicBlockManager == null || def == null) return;
        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
        block.id = "blk_" + System.currentTimeMillis();
        block.action = def.id;
        block.category = def.category;
        block.shape = def.resolvedShape();
        block.event = "immediate";
        block.spec = def.resolvedTemplate();
        block.targetMode = LogicBlockManager.TARGET_MODE_ID;
        block.targetWidget = "";
        block.parentBlockId = containerView != null ? containerView.getBlock().id : null;
        block.paramValues = defaultParamValues(def);
        block.params = joinPipe(block.paramValues);
        insertIntoMaster(block, containerView, siblingIndex);
        rebuild();
    }

    void moveBlockTo(String blockId, @Nullable BlockView containerView, int siblingIndex) {
        if (logicBlockManager == null) return;
        LogicBlockManager.LogicBlock target = findBlockById(blockId);
        if (target == null) return;
        // Disallow nesting a container inside its own descendants.
        if (containerView != null && isAncestor(target.id, containerView.getBlock().id)) return;

        // Detach the moved block plus its descendants from the master list.
        List<LogicBlockManager.LogicBlock> all = logicBlockManager.getBlocks();
        List<LogicBlockManager.LogicBlock> chain = collectSubtree(target);
        all.removeAll(chain);

        target.parentBlockId = containerView != null ? containerView.getBlock().id : null;

        insertChainAt(chain, target.parentBlockId, siblingIndex);
        rebuild();
    }

    void deleteBlockChainById(String id) {
        if (logicBlockManager == null) return;
        LogicBlockManager.LogicBlock target = findBlockById(id);
        if (target == null) return;
        List<LogicBlockManager.LogicBlock> chain = collectSubtree(target);
        logicBlockManager.getBlocks().removeAll(chain);
        rebuild();
    }

    void showInsertionIndicator(float y) {
        float adjustedY = (y - translationY) / scaleFactor;
        float content = adjustedY + getScrollY();
        int idx = computeInsertIndexAbsolute(stack, content);
        float top;
        float baseTop = stack.getTop();
        if (idx <= 0) {
            top = baseTop;
        } else if (idx >= stack.getChildCount()) {
            View last = stack.getChildAt(stack.getChildCount() - 1);
            top = last != null ? (baseTop + last.getTop() + last.getHeight()) : baseTop;
        } else {
            View child = stack.getChildAt(idx);
            top = baseTop + child.getTop();
        }
        insertionIndicator.setVisibility(VISIBLE);
        insertionIndicator.setTranslationY(top - dp(1));
    }

    void hideInsertionIndicator() {
        insertionIndicator.setVisibility(GONE);
    }

    // -------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------

    private void renderChildrenInto(LinearLayout host, @Nullable String parentId) {
        for (LogicBlockManager.LogicBlock b : logicBlockManager.getBlocks()) {
            if (!sameParent(b.parentBlockId, parentId)) continue;
            BlockDef def = findDef(b.action);
            BlockView view = new BlockView(getContext(), b, def, chipFactory, dragDropManager, this::onBlockMutated);
            viewByBlockId.put(b.id, view);
            host.addView(view);
            if (view.isContainer()) {
                renderChildrenInto(view.getStackSlot(), b.id);
                if (dragDropManager != null) {
                    dragDropManager.attachContainerSlot(view.getStackSlot(), view);
                }
            }
            if (dragDropManager != null) dragDropManager.attachWorkspaceSource(view);
        }
    }

    private void onBlockMutated(LogicBlockManager.LogicBlock block) {
        if (interactionListener != null) interactionListener.onWorkspaceChanged();
    }

    private static boolean sameParent(String a, String b) {
        if (a == null || a.isEmpty()) return b == null || b.isEmpty();
        return a.equals(b);
    }

    private BlockDef findDef(String id) {
        if (id == null) return null;
        for (BlockDef d : defs) if (id.equals(d.id)) return d;
        return null;
    }

    private LogicBlockManager.LogicBlock findBlockById(String id) {
        if (logicBlockManager == null || id == null) return null;
        for (LogicBlockManager.LogicBlock b : logicBlockManager.getBlocks()) {
            if (id.equals(b.id)) return b;
        }
        return null;
    }

    private boolean isAncestor(String ancestorId, String maybeDescendantId) {
        String cursor = maybeDescendantId;
        int safety = 0;
        while (cursor != null && safety++ < 256) {
            if (cursor.equals(ancestorId)) return true;
            LogicBlockManager.LogicBlock b = findBlockById(cursor);
            if (b == null) break;
            cursor = b.parentBlockId;
        }
        return false;
    }

    private List<LogicBlockManager.LogicBlock> collectSubtree(LogicBlockManager.LogicBlock root) {
        List<LogicBlockManager.LogicBlock> out = new ArrayList<>();
        out.add(root);
        for (int i = 0; i < out.size(); i++) {
            String id = out.get(i).id;
            if (id == null) continue;
            for (LogicBlockManager.LogicBlock b : logicBlockManager.getBlocks()) {
                if (id.equals(b.parentBlockId) && !out.contains(b)) out.add(b);
            }
        }
        return out;
    }

    private void insertIntoMaster(LogicBlockManager.LogicBlock block,
                                  @Nullable BlockView containerView,
                                  int siblingIndex) {
        String parentId = containerView != null ? containerView.getBlock().id : null;
        List<LogicBlockManager.LogicBlock> all = logicBlockManager.getBlocks();
        int target = resolveMasterIndex(parentId, siblingIndex);
        all.add(Math.min(target, all.size()), block);
    }

    private void insertChainAt(List<LogicBlockManager.LogicBlock> chain,
                               @Nullable String parentId,
                               int siblingIndex) {
        List<LogicBlockManager.LogicBlock> all = logicBlockManager.getBlocks();
        int target = resolveMasterIndex(parentId, siblingIndex);
        for (int i = 0; i < chain.size(); i++) {
            int pos = Math.min(target + i, all.size());
            all.add(pos, chain.get(i));
        }
    }

    private int resolveMasterIndex(@Nullable String parentId, int siblingIndex) {
        List<LogicBlockManager.LogicBlock> all = logicBlockManager.getBlocks();
        int seen = 0;
        for (int i = 0; i < all.size(); i++) {
            if (sameParent(all.get(i).parentBlockId, parentId)) {
                if (seen == siblingIndex) return i;
                seen++;
            }
        }
        return all.size();
    }

    private static List<String> defaultParamValues(BlockDef def) {
        List<String> values = new ArrayList<>();
        if (def == null) return values;
        for (ChipInput input : def.resolvedInputs()) {
            values.add(input.defaultValue != null ? input.defaultValue : "");
        }
        return values;
    }

    private static String joinPipe(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append('|');
            String v = values.get(i);
            sb.append(v != null ? v : "");
        }
        return sb.toString();
    }

    private int dp(int px) {
        return (int) (px * getResources().getDisplayMetrics().density);
    }

    // -------------------------------------------------------------------
    // Listener types (legacy + new)
    // -------------------------------------------------------------------

    public interface OnBlockInteractionListener {
        void onWorkspaceChanged();
    }

    public interface OnBlockDroppedListener {
        void onBlockDropped(float x, float y, android.view.DragEvent event);
    }

    /** Tiny FrameLayout-style holder so the indicator overlays the stack. */
    private static final class FrameContainer extends android.widget.FrameLayout {
        FrameContainer(Context context) { super(context); }
    }
}
