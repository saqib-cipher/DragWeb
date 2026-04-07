package sketchweb.gl;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WidgetRegistry {

    private Context context;
    private ArrayList<HashMap<String, Object>> allWidgets = new ArrayList<>();

    public WidgetRegistry(Context context) {
        this.context = context;
        loadWidgets();
    }

    private void loadWidgets() {
        try {
            InputStream is = context.getAssets().open("widgets.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");

            allWidgets = new Gson().fromJson(json,
                new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());

            // Replace http image sources with placeholder references
            for (HashMap<String, Object> widgetDef : allWidgets) {
                if ("img".equals(widgetDef.get("tag"))) {
                    Map<String, Object> function = (Map<String, Object>) widgetDef.get("function");
                    if (function != null && function.containsKey("src")) {
                        String src = function.get("src").toString();
                        if (src.startsWith("http")) {
                            function.put("src", "android.R.drawable.ic_menu_gallery");
                        }
                    }
                }
            }
        } catch (IOException e) {
            loadDefaultWidgets();
        }
    }

    private void loadDefaultWidgets() {
        allWidgets.clear();
        String[] names = {"Text", "Heading", "Button", "Image", "Input", "Container"};
        String[] tags = {"p", "h1", "button", "img", "input", "div"};
        String[] colors = {"#333333", "#000000", "#FFBB33", "#CCCCCC", "#FFFFFF", "#F5F5F5"};

        for (int i = 0; i < names.length; i++) {
            HashMap<String, Object> widget = new HashMap<>();
            widget.put("name", names[i]);
            widget.put("tag", tags[i]);
            widget.put("color", colors[i]);
            widget.put("category", "basic");

            HashMap<String, Object> function = new HashMap<>();
            HashMap<String, Object> style = new HashMap<>();

            switch (tags[i]) {
                case "p":
                    function.put("text", "Hello, world!");
                    style.put("fontSize", "16px");
                    break;
                case "h1":
                    function.put("text", "Your Page Title");
                    style.put("fontSize", "32px");
                    style.put("fontWeight", "bold");
                    break;
                case "button":
                    function.put("text", "Click Me");
                    style.put("padding", "10px 20px");
                    style.put("backgroundColor", "#FFBB33");
                    break;
                case "img":
                    function.put("src", "android.R.drawable.ic_menu_gallery");
                    style.put("width", "100%");
                    break;
                case "input":
                    function.put("type", "text");
                    function.put("placeholder", "Enter text");
                    style.put("width", "100%");
                    style.put("padding", "8px");
                    break;
                case "div":
                    style.put("padding", "16px");
                    style.put("backgroundColor", "#F5F5F5");
                    break;
            }
            function.put("style", style);
            widget.put("function", function);
            allWidgets.add(widget);
        }
    }

    public ArrayList<HashMap<String, Object>> getAllWidgets() {
        return allWidgets;
    }

    public ArrayList<HashMap<String, Object>> getWidgetsByCategory(String category) {
        ArrayList<HashMap<String, Object>> filtered = new ArrayList<>();
        for (HashMap<String, Object> widget : allWidgets) {
            if (category.equals(widget.get("category"))) {
                filtered.add(widget);
            }
        }
        return filtered;
    }

    public HashMap<String, Object> getWidgetByName(String name) {
        for (HashMap<String, Object> widget : allWidgets) {
            if (name.equals(widget.get("name"))) {
                return widget;
            }
        }
        return null;
    }
}
