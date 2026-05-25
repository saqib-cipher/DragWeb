package sketchweb.gl;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DropZoneManager {
    public interface OnTreeChangedListener {
        void onTreeChanged();
    }

    private static final Set<String> LAYOUT_TAGS = new HashSet<>(Arrays.asList(
        "div", "section", "header", "footer", "nav", "main", "article", "aside", "form", "ul", "ol", "table",
        "linear", "container"
    ));

    private Context context;
    private View screen;
    private ArrayList<HashMap<String, Object>> widgets;
    private WidgetBuilderEngine engine;
    private WidgetSelector selector;
    private OnTreeChangedListener treeChangedListener;

    public DropZoneManager(Context context, View screen, ArrayList<HashMap<String, Object>> widgets, WidgetBuilderEngine engine, WidgetSelector selector) {
        this.context = context;
        this.screen = screen;
        this.widgets = widgets;
        this.engine = engine;
        this.selector = selector;
    }

    public void setOnTreeChangedListener(OnTreeChangedListener listener) {
        this.treeChangedListener = listener;
    }

    private boolean isLayoutWidget(View view) {
        if (!(view instanceof ViewGroup)) return false;
        Object tagObj = view.getTag();
        if (tagObj instanceof Map) {
            Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
            String tag = widgetMap.containsKey("tag") ? widgetMap.get("tag").toString() : "";
            return LAYOUT_TAGS.contains(tag);
        }
        return false;
    }

    public void registerWidgetAsDropZoneIfContainer(View view) {
        if (!(view instanceof ViewGroup)) {
            return;
        }

        // Layout widgets get larger minimum size for easier dropping. The
        // previous implementation passed raw integers to setMinimumHeight /
        // setPadding which are interpreted as pixels, so on a hi-DPI device
        // an "80" turned into roughly 25dp – too small to hit reliably.
        // Convert to actual pixels using the display density.
        if (isLayoutWidget(view)) {
            float density = view.getResources().getDisplayMetrics().density;
            int minHeightPx = (int) (80 * density + 0.5f);
            view.setMinimumHeight(minHeightPx);
            if (view instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) view;
                int padHPx = (int) (8 * density + 0.5f);
                int padVPx = (int) (12 * density + 0.5f);
                if (ll.getPaddingTop() < padVPx && ll.getPaddingBottom() < padVPx) {
                    ll.setPadding(
                        Math.max(ll.getPaddingLeft(), padHPx),
                        Math.max(ll.getPaddingTop(), padVPx),
                        Math.max(ll.getPaddingRight(), padHPx),
                        Math.max(ll.getPaddingBottom(), padVPx)
                    );
                }
            }
        }

        view.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                int action = event.getAction();
                switch (action) {
                    case DragEvent.ACTION_DRAG_STARTED: {
                        // Some Android versions may deliver a started event
                        // with a null ClipDescription; treat it as a non-drop.
                        ClipDescription desc = event.getClipDescription();
                        if (desc == null) return false;
                        return desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
                    }

                    case DragEvent.ACTION_DRAG_ENTERED:
                        highlightDropZone(v, true);
                        return true;

                    case DragEvent.ACTION_DRAG_EXITED:
                        highlightDropZone(v, false);
                        return true;

                    case DragEvent.ACTION_DROP:
                        highlightDropZone(v, false);
                        ClipData data = event.getClipData();

                        if (data != null && data.getItemCount() > 0) {
                            try {
                                String dragText = data.getItemAt(0).getText().toString();

                                // Handle reorder drags into nested containers
                                if (dragText.startsWith("reorder:")) {
                                    int hash = Integer.parseInt(dragText.replace("reorder:", ""));
                                    View draggedView = findViewByHash((ViewGroup) screen, hash);
                                    if (draggedView != null && draggedView.getParent() instanceof ViewGroup) {
                                        ViewGroup container = (ViewGroup) v;
                                        // Prevent dropping into self or own children
                                        if (draggedView == v || isDescendantOf(container, draggedView)) {
                                            return false;
                                        }
                                        ViewGroup oldParent = (ViewGroup) draggedView.getParent();
                                        oldParent.removeView(draggedView);
                                        int targetIdx = findDropIndex(container, event.getX(), event.getY());
                                        container.addView(draggedView, Math.min(targetIdx, container.getChildCount()));
                                        if (treeChangedListener != null) {
                                            treeChangedListener.onTreeChanged();
                                        }
                                        return true;
                                    }
                                    return false;
                                }

                                int pos = Integer.parseInt(dragText);
                                if (pos < 0 || pos >= widgets.size()) return false;
                                Map<String, Object> widgetDefinition = widgets.get(pos);
                                View newWidgetView = engine.createWidget(widgetDefinition.get("tag").toString());

                                if (newWidgetView != null) {
                                    Map<String, Object> newWidgetMap = (Map<String, Object>) newWidgetView.getTag();
                                    if (newWidgetMap != null) {
                                        Map<String, Object> defFunction = (Map<String, Object>) widgetDefinition.get("function");
                                        if (defFunction != null) {
                                            Map<String, Object> newFunction = (Map<String, Object>) newWidgetMap.get("function");
                                            if (newFunction == null) {
                                                newFunction = new HashMap<>();
                                                newWidgetMap.put("function", newFunction);
                                            }

                                            for (Map.Entry<String, Object> entry : defFunction.entrySet()) {
                                                if (!"style".equals(entry.getKey())) {
                                                    newFunction.put(entry.getKey(), entry.getValue());
                                                }
                                            }

                                            Map<String, Object> defStyle = (Map<String, Object>) defFunction.get("style");
                                            if (defStyle != null) {
                                                Map<String, Object> newStyle = (Map<String, Object>) newFunction.get("style");
                                                if (newStyle == null) {
                                                    newStyle = new HashMap<>();
                                                    newFunction.put("style", newStyle);
                                                }
                                                newStyle.putAll(defStyle);
                                            }
                                        }
                                    }

                                    engine.applyPropertiesToView(newWidgetView, newWidgetMap);

                                    ViewGroup container = (ViewGroup) v;

                                    // Accurate child insertion based on drop position. For
                                    // horizontal containers the helper compares against X.
                                    int targetIndex = findDropIndex(container, event.getX(), event.getY());
                                    container.addView(newWidgetView, Math.min(targetIndex, container.getChildCount()));

                                    selector.registerView(newWidgetView);
                                    newWidgetView.performClick();

                                    // Make new view drop target if it is a container
                                    registerWidgetAsDropZoneIfContainer(newWidgetView);
                                    if (treeChangedListener != null) {
                                        treeChangedListener.onTreeChanged();
                                    }
                                }
                                return true; // Handled drop
                            } catch (NumberFormatException e) {
                                Log.e("DropZoneManager", "Drag data error: " + e.getMessage());
                            } catch (Exception e) {
                                Log.e("DropZoneManager", "Error creating widget on drop: " + e.getMessage());
                            }
                        }
                        return false;

                    case DragEvent.ACTION_DRAG_ENDED:
                        highlightDropZone(v, false);
                        return true;

                    case DragEvent.ACTION_DRAG_LOCATION:
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private int findDropIndex(ViewGroup parent, float dropY) {
        return findDropIndex(parent, 0f, dropY);
    }

    /**
     * Locate the insertion index, taking the parent's layout orientation
     * into account. For horizontal LinearLayouts we compare against the
     * pointer's X coordinate so a drop onto a flex-row container places the
     * widget at the spatially correct position instead of always at one end.
     */
    private int findDropIndex(ViewGroup parent, float dropX, float dropY) {
        boolean horizontal = false;
        if (parent instanceof LinearLayout) {
            horizontal = ((LinearLayout) parent).getOrientation() == LinearLayout.HORIZONTAL;
        }
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (horizontal) {
                float centerX = child.getX() + (child.getWidth() / 2f);
                if (dropX < centerX) return i;
            } else {
                float centerY = child.getY() + (child.getHeight() / 2f);
                if (dropY < centerY) return i;
            }
        }
        return parent.getChildCount();
    }

    private boolean isDescendantOf(View parent, View potentialAncestor) {
        View current = parent;
        while (current != null) {
            if (current == potentialAncestor) return true;
            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
        }
        return false;
    }

    private View findViewByHash(ViewGroup parent, int hash) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.hashCode() == hash) return child;
            if (child instanceof ViewGroup) {
                View found = findViewByHash((ViewGroup) child, hash);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void highlightDropZone(View v, boolean highlight) {
        if (highlight) {
            GradientDrawable border = new GradientDrawable();
            border.setColor(Color.parseColor("#E3F2FD"));
            border.setStroke(3, Color.parseColor("#2196F3"));
            border.setCornerRadius(8);
            v.setBackground(border);
        } else {
            // Restore from widget tag style if available
            Object tagObj = v.getTag();
            if (tagObj instanceof Map) {
                Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
                engine.applyPropertiesToView(v, widgetMap);
            } else {
                v.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }
}
