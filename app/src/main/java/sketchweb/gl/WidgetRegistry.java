package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
                        ensureDefaultWidgets();
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
            ensureDefaultWidgets();
            
            // Save to device immediately so user has local starting set
            saveCustomWidgets(allWidgets);
        } catch (IOException e) {
            loadDefaultWidgets();
            saveCustomWidgets(allWidgets);
        }
    }

    private void sanitizeWidgets(ArrayList<HashMap<String, Object>> widgets) {
        for (HashMap<String, Object> widgetDef : widgets) {
            if (widgetDef != null && "img".equals(widgetDef.get("tag"))) {
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

    private HashMap<String, Object> createDefaultWidget(String name) {
        String tag = "div";
        String color = "#F5F5F5";
        switch (name) {
            case "Text":      tag = "p";      color = "#333333"; break;
            case "Heading":   tag = "h1";     color = "#000000"; break;
            case "Button":    tag = "button"; color = "#FFBB33"; break;
            case "Image":     tag = "img";    color = "#CCCCCC"; break;
            case "Input":     tag = "input";  color = "#FFFFFF"; break;
            case "Container": tag = "div";    color = "#F5F5F5"; break;
        }

        HashMap<String, Object> widget = new HashMap<>();
        widget.put("name", name);
        widget.put("tag", tag);
        widget.put("color", color);
        widget.put("category", "basic");

        HashMap<String, Object> function = new HashMap<>();
        HashMap<String, Object> style = new HashMap<>();

        switch (tag) {
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
        return widget;
    }

    private void loadDefaultWidgets() {
        allWidgets.clear();
        String[] names = {"Text", "Heading", "Button", "Image", "Input", "Container"};
        for (String name : names) {
            allWidgets.add(createDefaultWidget(name));
        }
    }

    private void ensureDefaultWidgets() {
        if (allWidgets == null) {
            allWidgets = new ArrayList<>();
        }
        String[] names = {"Text", "Heading", "Button", "Image", "Input", "Container"};
        for (String name : names) {
            boolean found = false;
            for (HashMap<String, Object> widget : allWidgets) {
                if (widget != null && name.equalsIgnoreCase(String.valueOf(widget.get("name")))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                allWidgets.add(createDefaultWidget(name));
            }
        }
    }

    public ArrayList<HashMap<String, Object>> getAllWidgets() {
        loadWidgets();
        return allWidgets;
    }
    public void importCustomWidgets(String jsonContent) {
        importAndSaveCustomWidgets(jsonContent);
    }

    public ArrayList<HashMap<String, Object>> getWidgetsByCategory(String category) {
        loadWidgets();
        ArrayList<HashMap<String, Object>> filtered = new ArrayList<>();
        for (HashMap<String, Object> widget : allWidgets) {
            if (category.equals(widget.get("category"))) {
                filtered.add(widget);
            }
        }
        return filtered;
    }

    public HashMap<String, Object> getWidgetByName(String name) {
        loadWidgets();
        for (HashMap<String, Object> widget : allWidgets) {
            if (name.equals(widget.get("name"))) {
                return widget;
            }
        }
        return null;
    }

    public File getCustomWidgetsFile() {
        File internalDir = new File(context.getFilesDir(), "custom");
        if (!internalDir.exists()) internalDir.mkdirs();
        File internalFile = new File(internalDir, "widgets.json");

        try {
            String base = Environment.getExternalStorageDirectory().getAbsolutePath();
            File externalFile = new File(base + "/.dragweb/custom/widgets.json");
            File externalParent = externalFile.getParentFile();
            boolean isExternalWritable = externalParent != null && (externalParent.exists() || externalParent.mkdirs()) && externalParent.canWrite();
            
            if (isExternalWritable) {
                return externalFile;
            }
            
            // Migrate old data if present and internal file doesn't exist yet
            if (externalFile.exists() && externalFile.canRead() && !internalFile.exists()) {
                String oldData = FileUtil.readFile(externalFile.getAbsolutePath());
                if (oldData != null && !oldData.trim().isEmpty()) {
                    FileUtil.writeFile(internalFile.getAbsolutePath(), oldData);
                }
            }
        } catch (Exception e) {
        }
        return internalFile;
    }

    public ArrayList<HashMap<String, Object>> loadOnlyCustomWidgets() {
        loadWidgets();
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
        loadWidgets();

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
        loadWidgets();
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

    private String stripMarkdownCodeBlocks(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            if (firstLineBreak != -1) {
                trimmed = trimmed.substring(firstLineBreak + 1);
            } else {
                trimmed = trimmed.substring(3);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }
        return trimmed;
    }

    public boolean importAndSaveCustomWidgets(String jsonContent) {
        try {
            loadWidgets();
            if (jsonContent == null || jsonContent.trim().isEmpty()) return false;
            String cleanedJson = stripMarkdownCodeBlocks(jsonContent);
            ArrayList<HashMap<String, Object>> customWidgets = new ArrayList<>();
            Gson gson = new Gson();
            
            JsonElement root = JsonParser.parseString(cleanedJson);
            JsonArray array = null;
            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("widgets") && obj.get("widgets").isJsonArray()) {
                    array = obj.getAsJsonArray("widgets");
                } else if (obj.has("name") && obj.has("tag")) {
                    HashMap<String, Object> singleWidget = gson.fromJson(obj,
                        new TypeToken<HashMap<String, Object>>(){}.getType());
                    if (singleWidget != null) {
                        customWidgets.add(singleWidget);
                    }
                } else {
                    // Try parsing as a map of widget definitions:
                    // e.g. { "WidgetName": { "tag": "div", "color": "..." } }
                    boolean parsedAsMap = false;
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        if (entry.getValue().isJsonObject()) {
                            JsonObject valObj = entry.getValue().getAsJsonObject();
                            if (valObj.has("tag")) {
                                HashMap<String, Object> widget = gson.fromJson(valObj,
                                    new TypeToken<HashMap<String, Object>>(){}.getType());
                                if (widget != null) {
                                    if (!widget.containsKey("name")) {
                                        widget.put("name", entry.getKey());
                                    }
                                    customWidgets.add(widget);
                                    parsedAsMap = true;
                                }
                            }
                        }
                    }
                    
                    if (!parsedAsMap) {
                        // Treat obj as a single widget definition even if tag/name is missing
                        HashMap<String, Object> widget = gson.fromJson(obj,
                            new TypeToken<HashMap<String, Object>>(){}.getType());
                        if (widget != null) {
                            if (!widget.containsKey("name")) {
                                widget.put("name", "widget_" + System.currentTimeMillis());
                            }
                            if (!widget.containsKey("tag")) {
                                widget.put("tag", "div");
                            }
                            customWidgets.add(widget);
                        }
                    }
                }
            }
            
            if (array != null) {
                ArrayList<HashMap<String, Object>> list = gson.fromJson(array,
                    new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
                if (list != null) {
                    for (HashMap<String, Object> widget : list) {
                        if (widget != null) {
                            if (!widget.containsKey("name")) {
                                widget.put("name", "widget_" + System.currentTimeMillis() + "_" + Math.round(Math.random() * 1000));
                            }
                            if (!widget.containsKey("tag")) {
                                widget.put("tag", "div");
                            }
                            customWidgets.add(widget);
                        }
                    }
                }
            }
            
            if (customWidgets.isEmpty()) return false;
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
        loadWidgets();
        
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
