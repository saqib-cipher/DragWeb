package sketchweb.gl;

import android.content.Context;
import android.view.View;
import java.util.HashMap;
import java.util.Map;

public class WidgetUpdater {
    private Context context;
    private WidgetBuilderEngine engine;

    public WidgetUpdater(Context context, WidgetBuilderEngine engine) {
        this.context = context;
        this.engine = engine;
    }

    public void updateWidget(View view, String value, Map<String, Object> styleUpdates) {
        if (view == null) return;

        Object tagObj = view.getTag();
        if (!(tagObj instanceof Map)) {
            // View wasn't initialized by engine properly, we'll try to initialize it now
            tagObj = new HashMap<String, Object>();
            ((Map<String, Object>)tagObj).put("tag", "div"); // default
            view.setTag(tagObj);
        }

        Map<String, Object> widgetMap = (Map<String, Object>) tagObj;

        Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
        if (function == null) {
            function = new HashMap<>();
            widgetMap.put("function", function);
        }

        // Handle special non-style attributes like 'text'
        if (styleUpdates.containsKey("text")) {
             function.put("text", styleUpdates.get("text"));
             styleUpdates.remove("text");
        }
        if (styleUpdates.containsKey("placeholder")) {
             function.put("placeholder", styleUpdates.get("placeholder"));
             styleUpdates.remove("placeholder");
        }

        if (!styleUpdates.isEmpty()) {
            Map<String, Object> style = (Map<String, Object>) function.get("style");
            if (style == null) {
                style = new HashMap<>();
                function.put("style", style);
            }

            for (Map.Entry<String, Object> entry : styleUpdates.entrySet()) {
                style.put(entry.getKey(), entry.getValue());
            }
        }

        engine.applyPropertiesToView(view, widgetMap);
    }
}
