package sketchweb.gl;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DropZoneManager {

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

    public void registerWidgetAsDropZoneIfContainer(View view) {
        if (!(view instanceof ViewGroup)) {
            return;
        }

        view.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                // If it's the root screen, we might want to skip handling here if it's handled by main
                // But normally this allows nested containers to handle their own drops.

                int action = event.getAction();
                switch (action) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);

                    case DragEvent.ACTION_DRAG_ENTERED:
                        v.setBackgroundColor(Color.parseColor("#BBDEFB")); // light blue highlight
                        return true;

                    case DragEvent.ACTION_DRAG_EXITED:
                        v.setBackgroundColor(Color.TRANSPARENT);
                        return true;

                    case DragEvent.ACTION_DROP:
                        v.setBackgroundColor(Color.TRANSPARENT);
                        ClipData data = event.getClipData();

                        if (data != null && data.getItemCount() > 0) {
                            try {
                                int pos = Integer.parseInt(data.getItemAt(0).getText().toString());
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
                        v.setBackgroundColor(Color.TRANSPARENT);
                        return true;

                    case DragEvent.ACTION_DRAG_LOCATION:
                        return true;

                    default:
                        return false;
                }
            }
        });
    }
}
