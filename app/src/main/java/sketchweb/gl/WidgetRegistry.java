package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
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
        File customFile = getCustomWidgetsFile();
        if (customFile != null && customFile.exists()) {
            try {
                String json = FileUtil.readFile(customFile.getAbsolutePath());
                if (json != null && !json.isEmpty()) {
                    allWidgets = new Gson().fromJson(json,
                        new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
                    if (allWidgets != null && !allWidgets.isEmpty()) {
                        sanitizeWidgets(allWidgets);
                        return;
                    }
                }
            } catch (Exception e) {
                Log.w("WidgetRegistry", "Could not load custom widgets from file: " + e.getMessage());
            }
        }

        // Fallback to assets
        try {
            InputStream is = context.getAssets().open("widgets.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");

            allWidgets = new Gson().fromJson(json,
                new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
            sanitizeWidgets(allWidgets);
            
            // Save to device immediately so user has local starting set
            saveCustomWidgets(allWidgets);
        } catch (IOException e) {
            loadDefaultWidgets();
            saveCustomWidgets(allWidgets);
        }
    }

    private void sanitizeWidgets(ArrayList<HashMap<String, Object>> widgets) {
        for (HashMap<String, Object> widgetDef : widgets) {
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

    public void importCustomWidgets(String jsonContent) {
        try {
            ArrayList<HashMap<String, Object>> customWidgets = new Gson().fromJson(jsonContent,
                new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
            if (customWidgets != null) {
                sanitizeWidgets(customWidgets);
                allWidgets.addAll(customWidgets);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public File getCustomWidgetsFile() {
        try {
            String base = Environment.getExternalStorageDirectory().getAbsolutePath();
            return new File(base + "/.dragweb/custom/widgets.json");
        } catch (Exception e) {
            File internal = new File(context.getFilesDir(), "custom");
            if (!internal.exists()) internal.mkdirs();
            return new File(internal, "widgets.json");
        }
    }

    public ArrayList<HashMap<String, Object>> loadOnlyCustomWidgets() {
        return allWidgets;
    }

    public void saveCustomWidgets(ArrayList<HashMap<String, Object>> customList) {
        try {
            File customFile = getCustomWidgetsFile();
            if (customFile == null) return;
            File parent = customFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            String json = new Gson().toJson(customList);
            FileUtil.writeFile(customFile.getAbsolutePath(), json);
            // Reload all widgets so the registry reflects changes
            loadWidgets();
        } catch (Exception e) {
            Log.w("WidgetRegistry", "Could not save custom widgets: " + e.getMessage());
        }
    }

    public void updateOrAddWidget(HashMap<String, Object> widget) {
        if (widget == null) return;
        String name = widget.containsKey("name") ? widget.get("name").toString() : "";
        if (name.isEmpty()) return;

        boolean replaced = false;
        for (int i = 0; i < allWidgets.size(); i++) {
            String existing = allWidgets.get(i).containsKey("name")
                ? allWidgets.get(i).get("name").toString() : "";
            if (name.equalsIgnoreCase(existing)) {
                allWidgets.set(i, widget);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            allWidgets.add(widget);
        }
        saveCustomWidgets(allWidgets);
    }

    public void deleteWidget(String name) {
        if (name == null || name.isEmpty()) return;
        for (int i = 0; i < allWidgets.size(); i++) {
            String existing = allWidgets.get(i).containsKey("name")
                ? allWidgets.get(i).get("name").toString() : "";
            if (name.equalsIgnoreCase(existing)) {
                allWidgets.remove(i);
                break;
            }
        }
        saveCustomWidgets(allWidgets);
    }

    public boolean importAndSaveCustomWidgets(String jsonContent) {
        try {
            if (jsonContent == null || jsonContent.trim().isEmpty()) return false;
            String trimmed = jsonContent.trim();
            ArrayList<HashMap<String, Object>> customWidgets = null;
            Gson gson = new Gson();
            
            if (trimmed.startsWith("[")) {
                customWidgets = gson.fromJson(trimmed,
                    new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
            } else if (trimmed.startsWith("{")) {
                HashMap<String, Object> singleWidget = gson.fromJson(trimmed,
                    new TypeToken<HashMap<String, Object>>(){}.getType());
                if (singleWidget != null && singleWidget.containsKey("name") && singleWidget.containsKey("tag")) {
                    customWidgets = new ArrayList<>();
                    customWidgets.add(singleWidget);
                }
            }
            
            if (customWidgets == null || customWidgets.isEmpty()) return false;
            sanitizeWidgets(customWidgets);

            // Merge custom widgets: replace duplicates by name, add new ones
            for (HashMap<String, Object> custom : customWidgets) {
                String customName = custom.containsKey("name") ? custom.get("name").toString() : "";
                if (customName.isEmpty()) continue;
                boolean replaced = false;
                for (int i = 0; i < allWidgets.size(); i++) {
                    String existingName = allWidgets.get(i).containsKey("name")
                        ? allWidgets.get(i).get("name").toString() : "";
                    if (customName.equalsIgnoreCase(existingName)) {
                        allWidgets.set(i, custom);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    allWidgets.add(custom);
                }
            }

            // Save back
            saveCustomWidgets(allWidgets);
            return true;
        } catch (Exception e) {
            Log.e("WidgetRegistry", "Error importing custom widgets", e);
            return false;
        }
    }

    public void addWidgetAfter(String afterName, HashMap<String, Object> widget) {
        if (widget == null) return;
        String name = widget.containsKey("name") ? widget.get("name").toString() : "";
        if (name.isEmpty()) return;
        
        // Remove existing if any
        deleteWidget(name);
        
        int insertIndex = -1;
        for (int i = 0; i < allWidgets.size(); i++) {
            String existing = allWidgets.get(i).containsKey("name")
                ? allWidgets.get(i).get("name").toString() : "";
            if (afterName.equalsIgnoreCase(existing)) {
                insertIndex = i + 1;
                break;
            }
        }
        if (insertIndex >= 0 && insertIndex <= allWidgets.size()) {
            allWidgets.add(insertIndex, widget);
        } else {
            allWidgets.add(widget);
        }
        saveCustomWidgets(allWidgets);
    }
}
