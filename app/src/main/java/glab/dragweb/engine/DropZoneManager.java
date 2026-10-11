package glab.dragweb.engine;

import glab.dragweb.R;
import glab.dragweb.activities.*;
import glab.dragweb.fragments.*;
import glab.dragweb.engine.*;
import glab.dragweb.ui.*;
import glab.dragweb.logic.*;
import glab.dragweb.codegen.*;
import glab.dragweb.data.*;
import glab.dragweb.adapters.*;
import glab.dragweb.models.*;
import glab.dragweb.util.*;
import glab.dragweb.colorpicker.*;

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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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

    public interface OnWidgetCreatedListener {
        void onWidgetCreated(View widgetView);
    }

    private OnWidgetCreatedListener widgetCreatedListener;

    public void setOnWidgetCreatedListener(OnWidgetCreatedListener listener) {
        this.widgetCreatedListener = listener;
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

                    case DragEvent.ACTION_DRAG_LOCATION:
                        if (v instanceof ViewGroup) {
                            updateDropIndicator((ViewGroup) v, event.getX(), event.getY());
                        }
                        return true;

                    case DragEvent.ACTION_DRAG_EXITED:
                        highlightDropZone(v, false);
                        removeDropIndicator();
                        return true;

                    case DragEvent.ACTION_DRAG_ENDED:
                        highlightDropZone(v, false);
                        removeDropIndicator();
                        return true;

                    case DragEvent.ACTION_DROP:
                        highlightDropZone(v, false);
                        ViewGroup container = (ViewGroup) v;
                        int targetIdx = findDropIndex(container, event.getX(), event.getY());
                        removeDropIndicator();

                        ClipData data = event.getClipData();
                        if (data != null && data.getItemCount() > 0) {
                            try {
                                String dragText = data.getItemAt(0).getText().toString();

                                // Handle reorder drags into nested containers
                                if (dragText.startsWith("reorder:")) {
                                    int hash = Integer.parseInt(dragText.replace("reorder:", ""));
                                    View draggedView = findViewByHash((ViewGroup) screen, hash);
                                    if (draggedView != null && draggedView.getParent() instanceof ViewGroup) {
                                        // Prevent dropping into self or own children
                                        if (draggedView == v || isDescendantOf(container, draggedView)) {
                                            return false;
                                        }
                                        ViewGroup oldParent = (ViewGroup) draggedView.getParent();
                                        oldParent.removeView(draggedView);
                                        container.addView(draggedView, Math.min(targetIdx, container.getChildCount()));
                                        WidgetCanvasStyler.getInstance(context).normalizeFlexContainer(oldParent);
                                        WidgetCanvasStyler.getInstance(context).normalizeFlexContainer(container);
                                        if (treeChangedListener != null) {
                                            treeChangedListener.onTreeChanged();
                                        }
                                        return true;
                                    }
                                    return false;
                                }

                                Map<String, Object> widgetDefinition = null;
                                if (dragText.startsWith("{")) {
                                    try {
                                        widgetDefinition = new Gson().fromJson(dragText, new TypeToken<HashMap<String, Object>>(){}.getType());
                                    } catch (Exception ignored) {}
                                }
                                if (widgetDefinition == null) {
                                    try {
                                        int pos = Integer.parseInt(dragText);
                                        if (pos >= 0 && pos < widgets.size()) {
                                            widgetDefinition = widgets.get(pos);
                                        }
                                    } catch (Exception ignored) {}
                                }
                                if (widgetDefinition == null) return false;
                                View newWidgetView = engine.createWidget(widgetDefinition.get("tag").toString());

                                if (newWidgetView != null) {
                                    Map<String, Object> newWidgetMap = (Map<String, Object>) newWidgetView.getTag();
                                    if (newWidgetMap != null) {
                                        for (Map.Entry<String, Object> entry : widgetDefinition.entrySet()) {
                                            if (!"function".equals(entry.getKey()) && !"children".equals(entry.getKey())) {
                                                newWidgetMap.put(entry.getKey(), entry.getValue());
                                            }
                                        }

                                        String tag = widgetDefinition.get("tag") != null ? widgetDefinition.get("tag").toString() : "div";
                                        int count = countWidgetsWithTag(screen, tag) + 1;
                                        String autoClass = tag + "-" + count;
                                        String autoId = tag + "_" + count;

                                        newWidgetMap.put("id", autoId);
                                        newWidgetMap.put("index", targetIdx);
                                        String parentId = "root";
                                        if (container != screen && container.getTag() instanceof Map) {
                                            Object pId = ((Map<?, ?>) container.getTag()).get("id");
                                            if (pId != null) parentId = pId.toString();
                                        }
                                        newWidgetMap.put("parent", parentId);

                                        String convert = "View";
                                        if (tag.equals("button")) convert = "Button";
                                        else if (tag.equals("p") || tag.equals("h1") || tag.equals("h2") || tag.equals("h3") || tag.equals("span") || tag.equals("a")) convert = "TextView";
                                        else if (tag.equals("img")) convert = "ImageView";
                                        else if (tag.equals("div") || tag.equals("section") || tag.equals("header") || tag.equals("footer")) convert = "LinearLayout";
                                        newWidgetMap.put("convert", convert);

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
                                            newFunction.put("class", autoClass);
                                            newFunction.put("id", autoId);

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
                                    container.addView(newWidgetView, Math.min(targetIdx, container.getChildCount()));
                                    WidgetCanvasStyler.getInstance(context).normalizeFlexContainer(container);

                                    selector.registerView(newWidgetView);
                                    newWidgetView.performClick();

                                    // Make new view drop target if it is a container
                                    registerWidgetAsDropZoneIfContainer(newWidgetView);
                                    if (widgetCreatedListener != null) {
                                        widgetCreatedListener.onWidgetCreated(newWidgetView);
                                    }
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

                    default:
                        return false;
                }
            }
        });
    }

    private View dropIndicator;

    private boolean isDropIndicator(View view) {
        return view != null && "__drop_indicator__".equals(view.getTag());
    }

    private View getOrCreateDropIndicator(ViewGroup parent) {
        float density = context.getResources().getDisplayMetrics().density;
        if (dropIndicator == null) {
            dropIndicator = new View(context);
            dropIndicator.setTag("__drop_indicator__");
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setColor(Color.parseColor("#1976D2")); // Material 3 Primary Accent
            gd.setCornerRadius(3 * density);
            dropIndicator.setBackground(gd);
        }
        boolean horizontal = false;
        if (parent instanceof LinearLayout) {
            horizontal = ((LinearLayout) parent).getOrientation() == LinearLayout.HORIZONTAL;
        }
        int thickness = (int) (4 * density + 0.5f);
        if (horizontal) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(thickness, ViewGroup.LayoutParams.MATCH_PARENT);
            int margin = (int) (4 * density + 0.5f);
            lp.setMargins(margin, (int) (2 * density + 0.5f), margin, (int) (2 * density + 0.5f));
            dropIndicator.setLayoutParams(lp);
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, thickness);
            int marginH = (int) (8 * density + 0.5f);
            int marginV = (int) (4 * density + 0.5f);
            lp.setMargins(marginH, marginV, marginH, marginV);
            dropIndicator.setLayoutParams(lp);
        }
        return dropIndicator;
    }

    private void updateDropIndicator(ViewGroup container, float dropX, float dropY) {
        if (container == null) return;
        int targetIdx = findDropIndex(container, dropX, dropY);
        View indicator = getOrCreateDropIndicator(container);
        if (indicator.getParent() == container) {
            int currentIdx = container.indexOfChild(indicator);
            if (currentIdx == targetIdx) {
                return;
            }
            container.removeView(indicator);
        } else {
            removeDropIndicator();
        }
        container.addView(indicator, Math.min(targetIdx, container.getChildCount()));
    }

    private void removeDropIndicator() {
        if (dropIndicator != null && dropIndicator.getParent() instanceof ViewGroup) {
            ((ViewGroup) dropIndicator.getParent()).removeView(dropIndicator);
        }
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
        int effectiveIndex = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (isDropIndicator(child)) continue;
            if (horizontal) {
                float centerX = child.getX() + (child.getWidth() / 2f);
                if (dropX < centerX) return effectiveIndex;
            } else {
                float centerY = child.getY() + (child.getHeight() / 2f);
                if (dropY < centerY) return effectiveIndex;
            }
            effectiveIndex++;
        }
        return effectiveIndex;
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
        if (v == screen) {
            if (highlight) {
                GradientDrawable border = new GradientDrawable();
                border.setColor(Color.WHITE);
                border.setStroke(4, Color.parseColor("#2196F3"));
                v.setBackground(border);
            } else {
                WidgetCanvasStyler.getInstance(context).synchronizeCanvas(screen);
            }
            return;
        }
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

    private int countWidgetsWithTag(View root, String tag) {
        int count = 0;
        if (root == null) return 0;
        Object tagObj = root.getTag();
        if (tagObj instanceof Map) {
            Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
            String childTag = widgetMap.get("tag") != null ? widgetMap.get("tag").toString() : "";
            if (tag.equalsIgnoreCase(childTag)) {
                count++;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                count += countWidgetsWithTag(vg.getChildAt(i), tag);
            }
        }
        return count;
    }
}
