package sketchweb.gl;

import android.view.View;
import android.view.ViewGroup;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UndoRedoManager {

    public interface OnStateChangeListener {
        void onStateChanged(boolean canUndo, boolean canRedo);
    }

    private List<String> undoStack = new ArrayList<>();
    private List<String> redoStack = new ArrayList<>();
    private static final int MAX_HISTORY = 50;
    private Gson gson = new Gson();
    private OnStateChangeListener listener;

    public void setOnStateChangeListener(OnStateChangeListener listener) {
        this.listener = listener;
    }

    public void saveState(ViewGroup screen) {
        List<Map<String, Object>> tree = serializeViewTree(screen);
        String json = gson.toJson(tree);

        if (!undoStack.isEmpty() && undoStack.get(undoStack.size() - 1).equals(json)) {
            return;
        }

        undoStack.add(json);
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.remove(0);
        }
        redoStack.clear();
        notifyListener();
    }

    public boolean canUndo() {
        return undoStack.size() > 1;
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public List<Map<String, Object>> undo() {
        if (!canUndo()) return null;

        String current = undoStack.remove(undoStack.size() - 1);
        redoStack.add(current);

        String previous = undoStack.get(undoStack.size() - 1);
        notifyListener();
        return gson.fromJson(previous, new TypeToken<List<Map<String, Object>>>(){}.getType());
    }

    public List<Map<String, Object>> redo() {
        if (!canRedo()) return null;

        String next = redoStack.remove(redoStack.size() - 1);
        undoStack.add(next);
        notifyListener();
        return gson.fromJson(next, new TypeToken<List<Map<String, Object>>>(){}.getType());
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        notifyListener();
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onStateChanged(canUndo(), canRedo());
        }
    }

    private List<Map<String, Object>> serializeViewTree(ViewGroup parent) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            Object tagObj = child.getTag();
            if (tagObj instanceof Map) {
                Map<String, Object> widgetMap = new HashMap<>((Map<String, Object>) tagObj);
                if (child instanceof ViewGroup) {
                    List<Map<String, Object>> children = serializeViewTree((ViewGroup) child);
                    if (!children.isEmpty()) {
                        widgetMap.put("children", children);
                    }
                }
                nodes.add(widgetMap);
            }
        }
        return nodes;
    }
}
