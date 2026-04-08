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

    private static final Set<String> LAYOUT_TAGS = new HashSet<>(Arrays.asList(
        "div", "section", "header", "footer", "nav", "main", "article", "aside", "form", "ul", "ol", "table"
    ));

    private Context context;
    private View screen;
    private ArrayList<HashMap<String, Object>> widgets;
    private WidgetBuilderEngine engine;
    private WidgetSelector selector;

    public DropZoneManager(Context context, View screen, ArrayList<HashMap<String, Object>> widgets, WidgetBuilderEngine engine, WidgetSelector selector) {
        this.context = context;
        this.screen = screen;
        this.widgets = widgets;
        this.engine = engine;
        this.selector = selector;
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

        // Layout widgets get larger minimum size for easier dropping
        if (isLayoutWidget(view)) {
            view.setMinimumHeight(80);
            if (view instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) view;
                // Ensure layout widgets have at least some padding for drop targets
                if (ll.getPaddingTop() < 8 && ll.getPaddingBottom() < 8) {
                    ll.setPadding(
                        Math.max(ll.getPaddingLeft(), 8),
                        Math.max(ll.getPaddingTop(), 12),
                        Math.max(ll.getPaddingRight(), 8),
                        Math.max(ll.getPaddingBottom(), 12)
                    );
                }
            }
        }

        view.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                int action = event.getAction();
                switch (action) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);

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
                                // Skip reorder drags - let the main handler deal with them
                                if (dragText.startsWith("reorder:")) {
                                    return false;
                                }

                                int pos = Integer.parseInt(dragText);
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

                                    // Position drop near closest child view
                                    float dropY = event.getY();
                                    int targetIndex = -1;
                                    float minDistance = Float.MAX_VALUE;

                                    for (int i = 0; i < container.getChildCount(); i++) {
                                        View child = container.getChildAt(i);
                                        float centerY = child.getY() + (child.getHeight() / 2);
                                        float distance = Math.abs(dropY - centerY);
                                        if (distance < minDistance) {
                                            minDistance = distance;
                                            targetIndex = i;
                                        }
                                    }

                                    if (targetIndex != -1) {
                                        View targetView = container.getChildAt(targetIndex);
                                        float centerY = targetView.getY() + (targetView.getHeight() / 2);
                                        if (dropY < centerY) {
                                            container.addView(newWidgetView, targetIndex);
                                        } else {
                                            container.addView(newWidgetView, targetIndex + 1);
                                        }
                                    } else {
                                        container.addView(newWidgetView); // fallback to end
                                    }

                                    selector.registerView(newWidgetView);
                                    newWidgetView.performClick();

                                    // Make new view drop target if it is a container
                                    registerWidgetAsDropZoneIfContainer(newWidgetView);
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
