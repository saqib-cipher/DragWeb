package sketchweb.gl;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class WorkspaceView extends View {

    private final List<LogicBlockManager.LogicBlock> blocks = new ArrayList<>();
    private LogicBlockManager logicBlockManager;
    private OnBlockInteractionListener interactionListener;

    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float scaleFactor = 1f;
    private float offsetX = 0;
    private float offsetY = 0;

    private static final float BLOCK_WIDTH = 400f;
    private static final float BLOCK_MIN_HEIGHT = 90f;
    private static final float SNAP_DISTANCE = 60f;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private LogicBlockManager.LogicBlock draggingBlock;
    private float dragStartX, dragStartY;

    private float lastTouchX;
    private float lastTouchY;

    public WorkspaceView(Context context) {
        super(context);
        init(context);
    }

    public WorkspaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WorkspaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackgroundColor(Color.parseColor("#0D1117"));

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32);
        textPaint.setFakeBoldText(true);

        gridPaint.setColor(Color.parseColor("#1B2430"));
        gridPaint.setStrokeWidth(1.5f);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);
        strokePaint.setColor(Color.parseColor("#44FFFFFF"));

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void setLogicBlockManager(LogicBlockManager manager) {
        this.logicBlockManager = manager;
    }

    public void setBlocks(List<LogicBlockManager.LogicBlock> newBlocks) {
        this.blocks.clear();
        this.blocks.addAll(newBlocks);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scaleFactor, scaleFactor);

        drawGrid(canvas);

        // Draw only top-level blocks; children are drawn recursively
        for (LogicBlockManager.LogicBlock block : blocks) {
            if (block.parentBlockId == null || block.parentBlockId.isEmpty()) {
                drawBlockAndChain(canvas, block);
            }
        }

        canvas.restore();
        drawDeleteZone(canvas);
    }

    private void drawGrid(Canvas canvas) {
        int gridSize = 80;
        int width = getWidth() * 3;
        int height = getHeight() * 3;

        for (int x = -width; x < width; x += gridSize) {
            canvas.drawLine(x, -height, x, height, gridPaint);
        }

        for (int y = -height; y < height; y += gridSize) {
            canvas.drawLine(-width, y, width, y, gridPaint);
        }
    }

    private void drawBlockAndChain(Canvas canvas, LogicBlockManager.LogicBlock block) {
        drawBlock(canvas, block);
        
        // Draw sub-stack if it's a container
        if (block.subStackId != null && !block.subStackId.isEmpty()) {
            LogicBlockManager.LogicBlock sub = findBlockById(block.subStackId);
            if (sub != null) {
                sub.x = block.x + 60;
                sub.y = block.y + BLOCK_MIN_HEIGHT;
                drawBlockAndChain(canvas, sub);
            }
        }

        // Draw next block in chain
        if (block.nextBlockId != null && !block.nextBlockId.isEmpty()) {
            LogicBlockManager.LogicBlock next = findBlockById(block.nextBlockId);
            if (next != null) {
                next.x = block.x;
                next.y = block.y + getBlockTotalHeight(block);
                drawBlockAndChain(canvas, next);
            }
        }
    }

    private float getBlockTotalHeight(LogicBlockManager.LogicBlock block) {
        float h = BLOCK_MIN_HEIGHT;
        if ("C".equals(block.shape) || "E".equals(block.shape)) {
            float subH = 0;
            if (block.subStackId != null && !block.subStackId.isEmpty()) {
                subH = getChainHeight(block.subStackId);
            }
            h = Math.max(h, 60 + subH + 40); // Top + SubStack + Bottom
        }
        return h;
    }

    private float getChainHeight(String firstBlockId) {
        float h = 0;
        LogicBlockManager.LogicBlock current = findBlockById(firstBlockId);
        while (current != null) {
            h += getBlockTotalHeight(current);
            current = findBlockById(current.nextBlockId);
        }
        return h;
    }

    private void drawBlock(Canvas canvas, LogicBlockManager.LogicBlock block) {
        float x = block.x;
        float y = block.y;
        float w = BLOCK_WIDTH;
        float h = getBlockTotalHeight(block);

        blockPaint.setColor(getCategoryColor(block.category));
        
        Path path;
        if ("C".equals(block.shape) || "E".equals(block.shape)) {
            path = createContainerPath(x, y, w, h);
        } else if ("cap".equals(block.shape)) {
            path = createCapPath(x, y, w, h);
        } else if ("value".equals(block.shape)) {
            path = createValuePath(x, y, w, h);
        } else {
            path = createPuzzlePath(x, y, w, h);
        }
        
        canvas.drawPath(path, blockPaint);
        canvas.drawPath(path, strokePaint);

        // Draw Content (Labels and Chips)
        drawBlockContent(canvas, block);
    }

    private void drawBlockContent(Canvas canvas, LogicBlockManager.LogicBlock block) {
        String spec = block.spec != null ? block.spec : block.action;
        float drawX = block.x + 30;
        float drawY = block.y + 55;

        String[] parts = spec.split("(?=%)|(?<=%)");
        int paramIdx = 0;

        for (String part : parts) {
            if (part.startsWith("%")) {
                // Draw Chip
                String val = "...";
                if (block.paramValues != null && paramIdx < block.paramValues.size()) {
                    val = block.paramValues.get(paramIdx);
                } else if (block.params != null) {
                    String[] vals = block.params.split("\\|");
                    if (paramIdx < vals.length && !vals[paramIdx].isEmpty()) {
                        val = vals[paramIdx];
                    }
                }
                
                float chipW = textPaint.measureText(val) + 40;
                RectF chipRect = new RectF(drawX, drawY - 35, drawX + chipW, drawY + 15);
                
                Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                chipPaint.setColor(Color.WHITE);
                canvas.drawRoundRect(chipRect, 20, 20, chipPaint);
                
                Paint chipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                chipTextPaint.setColor(getCategoryColor(block.category));
                chipTextPaint.setTextSize(28);
                chipTextPaint.setFakeBoldText(true);
                canvas.drawText(val, drawX + 20, drawY, chipTextPaint);
                
                drawX += chipW + 15;
                paramIdx++;
            } else {
                // Draw Text
                canvas.drawText(part, drawX, drawY, textPaint);
                drawX += textPaint.measureText(part) + 10;
            }
        }
    }

    private int getCategoryColor(String category) {
        if (category == null) return Color.GRAY;
        switch (category) {
            case "css": return Color.parseColor("#2196F3");
            case "html": return Color.parseColor("#4CAF50");
            case "logic": return Color.parseColor("#E91E63");
            case "event": return Color.parseColor("#FF9800");
            case "variable": return Color.parseColor("#00BCD4");
            case "asd": return Color.parseColor("#455A64");
            case "value": return Color.parseColor("#7E57C2");
            default: return Color.parseColor("#607D8B");
        }
    }

    private Path createPuzzlePath(float x, float y, float w, float h) {
        Path path = new Path();
        path.moveTo(x, y);
        // Top notch
        path.lineTo(x + 40, y);
        path.lineTo(x + 55, y + 15);
        path.lineTo(x + 85, y + 15);
        path.lineTo(x + 100, y);
        path.lineTo(x + w, y);
        path.lineTo(x + w, y + h);
        // Bottom notch
        path.lineTo(x + 100, y + h);
        path.lineTo(x + 85, y + h + 15);
        path.lineTo(x + 55, y + h + 15);
        path.lineTo(x + 40, y + h);
        path.lineTo(x, y + h);
        path.close();
        return path;
    }

    private Path createCapPath(float x, float y, float w, float h) {
        Path path = new Path();
        path.moveTo(x, y + 20);
        path.quadTo(x + w/2, y - 20, x + w, y + 20);
        path.lineTo(x + w, y + h);
        path.lineTo(x + 100, y + h);
        path.lineTo(x + 85, y + h + 15);
        path.lineTo(x + 55, y + h + 15);
        path.lineTo(x + 40, y + h);
        path.lineTo(x, y + h);
        path.close();
        return path;
    }

    private Path createValuePath(float x, float y, float w, float h) {
        Path path = new Path();
        path.addRoundRect(new RectF(x, y, x + w, y + h), h/2, h/2, Path.Direction.CW);
        return path;
    }

    private Path createContainerPath(float x, float y, float w, float h) {
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x + 40, y);
        path.lineTo(x + 55, y + 15);
        path.lineTo(x + 85, y + 15);
        path.lineTo(x + 100, y);
        path.lineTo(x + w, y);
        path.lineTo(x + w, y + 50);
        path.lineTo(x + 120, y + 50);
        path.lineTo(x + 105, y + 65);
        path.lineTo(x + 75, y + 65);
        path.lineTo(x + 60, y + 50);
        path.lineTo(x + 60, y + h - 40);
        path.lineTo(x + 75, y + h - 25);
        path.lineTo(x + 105, y + h - 25);
        path.lineTo(x + 120, y + h - 40);
        path.lineTo(x + w, y + h - 40);
        path.lineTo(x + w, y + h);
        path.lineTo(x, y + h);
        path.close();
        return path;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        float x = (event.getX() - offsetX) / scaleFactor;
        float y = (event.getY() - offsetY) / scaleFactor;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                draggingBlock = findTouchedBlock(x, y);
                if (draggingBlock != null) {
                    dragStartX = x - draggingBlock.x;
                    dragStartY = y - draggingBlock.y;
                    
                    // Detach from parent
                    if (draggingBlock.parentBlockId != null) {
                        LogicBlockManager.LogicBlock parent = findBlockById(draggingBlock.parentBlockId);
                        if (parent != null) {
                            if (draggingBlock.id.equals(parent.nextBlockId)) parent.nextBlockId = null;
                            if (draggingBlock.id.equals(parent.subStackId)) parent.subStackId = null;
                        }
                        draggingBlock.parentBlockId = null;
                    }
                    
                    blocks.remove(draggingBlock);
                    blocks.add(draggingBlock); // Bring to front
                }
                lastTouchX = x;
                lastTouchY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                if (draggingBlock != null) {
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;
                    moveBlockAndChain(draggingBlock, dx, dy);
                    invalidate();
                }
                lastTouchX = x;
                lastTouchY = y;
                break;
            case MotionEvent.ACTION_UP:
                if (draggingBlock != null) {
                    trySnap(draggingBlock);
                    
                    // Check Delete
                    if (event.getX() > getWidth() - 240 && event.getY() > getHeight() - 160) {
                        deleteBlockAndChain(draggingBlock);
                    }
                }
                draggingBlock = null;
                if (interactionListener != null) interactionListener.onWorkspaceChanged();
                invalidate();
                break;
        }
        return true;
    }

    private void moveBlockAndChain(LogicBlockManager.LogicBlock block, float dx, float dy) {
        block.x += dx;
        block.y += dy;
        if (block.nextBlockId != null) {
            LogicBlockManager.LogicBlock next = findBlockById(block.nextBlockId);
            if (next != null) moveBlockAndChain(next, dx, dy);
        }
        if (block.subStackId != null) {
            LogicBlockManager.LogicBlock sub = findBlockById(block.subStackId);
            if (sub != null) moveBlockAndChain(sub, dx, dy);
        }
    }

    private void deleteBlockAndChain(LogicBlockManager.LogicBlock block) {
        if (block.nextBlockId != null) {
            LogicBlockManager.LogicBlock next = findBlockById(block.nextBlockId);
            if (next != null) deleteBlockAndChain(next);
        }
        if (block.subStackId != null) {
            LogicBlockManager.LogicBlock sub = findBlockById(block.subStackId);
            if (sub != null) deleteBlockAndChain(sub);
        }
        blocks.remove(block);
    }

    private LogicBlockManager.LogicBlock findTouchedBlock(float x, float y) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            LogicBlockManager.LogicBlock block = blocks.get(i);
            float h = getBlockTotalHeight(block);
            if (x >= block.x && x <= block.x + BLOCK_WIDTH && y >= block.y && y <= block.y + h) {
                return block;
            }
        }
        return null;
    }

    private LogicBlockManager.LogicBlock findBlockById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (LogicBlockManager.LogicBlock b : blocks) {
            if (id.equals(b.id)) return b;
        }
        return null;
    }

    private void trySnap(LogicBlockManager.LogicBlock moving) {
        for (LogicBlockManager.LogicBlock target : blocks) {
            if (target == moving || isChildOf(target, moving)) continue;

            float snapX = target.x;
            float snapY = target.y + getBlockTotalHeight(target);

            // Bottom snap
            if (Math.abs(moving.x - snapX) < SNAP_DISTANCE && Math.abs(moving.y - snapY) < SNAP_DISTANCE) {
                moving.x = snapX;
                moving.y = snapY;
                moving.parentBlockId = target.id;
                target.nextBlockId = moving.id;
                return;
            }

            // Sub-stack snap (C-shape)
            if ("C".equals(target.shape) || "E".equals(target.shape)) {
                float subX = target.x + 60;
                float subY = target.y + BLOCK_MIN_HEIGHT;
                if (Math.abs(moving.x - subX) < SNAP_DISTANCE && Math.abs(moving.y - subY) < SNAP_DISTANCE) {
                    moving.x = subX;
                    moving.y = subY;
                    moving.parentBlockId = target.id;
                    target.subStackId = moving.id;
                    return;
                }
            }
        }
    }

    private boolean isChildOf(LogicBlockManager.LogicBlock potentialChild, LogicBlockManager.LogicBlock potentialParent) {
        String parentId = potentialChild.parentBlockId;
        while (parentId != null && !parentId.isEmpty()) {
            if (parentId.equals(potentialParent.id)) return true;
            LogicBlockManager.LogicBlock p = findBlockById(parentId);
            parentId = (p != null) ? p.parentBlockId : null;
        }
        return false;
    }

    private void drawDeleteZone(Canvas canvas) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.parseColor("#44E53935"));
        RectF rect = new RectF(getWidth() - 240, getHeight() - 160, getWidth() - 40, getHeight() - 40);
        canvas.drawRoundRect(rect, 32, 32, paint);
        
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(Color.parseColor("#E53935"));
        canvas.drawRoundRect(rect, 32, 32, paint);

        textPaint.setColor(Color.parseColor("#E53935"));
        textPaint.setTextSize(36);
        float textW = textPaint.measureText("DELETE");
        canvas.drawText("DELETE", rect.centerX() - textW/2, rect.centerY() + 12, textPaint);
    }

    public void setOnBlockInteractionListener(OnBlockInteractionListener listener) {
        this.interactionListener = listener;
    }

    public void setupDropListener(final OnBlockDroppedListener droppedListener) {
        setOnDragListener((v, event) -> {
            if (event.getAction() == android.view.DragEvent.ACTION_DROP) {
                float x = (event.getX() - offsetX) / scaleFactor;
                float y = (event.getY() - offsetY) / scaleFactor;
                if (droppedListener != null) {
                    droppedListener.onBlockDropped(x, y, event);
                }
                return true;
            }
            return true;
        });
    }

    public interface OnBlockDroppedListener {
        void onBlockDropped(float x, float y, android.view.DragEvent event);
    }

    public interface OnBlockInteractionListener {
        void onWorkspaceChanged();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.3f, Math.min(scaleFactor, 3.0f));
            invalidate();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (draggingBlock == null) {
                offsetX -= distanceX;
                offsetY -= distanceY;
                invalidate();
            }
            return true;
        }
    }
}