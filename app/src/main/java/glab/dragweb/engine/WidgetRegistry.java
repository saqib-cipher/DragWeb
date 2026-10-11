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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WidgetRegistry {

    private Context context;
    private ArrayList<HashMap<String, Object>> allWidgets = new ArrayList<>();

    public WidgetRegistry(Context context) {
        this.context = context;
        loadWidgets();
    }

    private void loadWidgets() {
        allWidgets = new ArrayList<>();
        ArrayList<HashMap<String, Object>> inbuilt = loadInbuiltWidgets();
        if (inbuilt != null) {
            allWidgets.addAll(inbuilt);
        }
        ArrayList<HashMap<String, Object>> custom = loadOnlyCustomWidgets();
        if (custom != null) {
            allWidgets.addAll(custom);
        }
    }

    public ArrayList<HashMap<String, Object>> loadInbuiltWidgets() {
        try {
            InputStream is = context.getAssets().open("widgets.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            ArrayList<HashMap<String, Object>> inbuilt = new Gson().fromJson(json,
                new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
            if (inbuilt != null) {
                sanitizeWidgets(inbuilt);
                return inbuilt;
            }
        } catch (Exception e) {
            Log.w("WidgetRegistry", "Could not load inbuilt widgets: " + e.getMessage());
        }
        return new ArrayList<>();
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
        File file = new File(FileUtil.getDragWebDir(context), "custom/widgets.json");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return file;
    }

    public static class WidgetCategory {
        public String id;
        public String name;
        public String icon;
    }

    public static final String[] AVAILABLE_ICONS = new String[] {
        "box", "columns_3", "stack_2", "layout_grid", "layout_list", "arrows_move_vertical",
        "cursor_text", "puzzle", "photo", "world", "pencil", "pencil_code",
        "check_bg", "video", "file_music", "source_code", "ic_list_view", "table",
        "palette", "bulb", "blocks", "layout_grid_add", "file_code", "code_plus",
        "droplet", "shadow", "folder"
    };

    public static int getIconDrawableRes(Context context, String iconName) {
        if (iconName == null || iconName.trim().isEmpty()) return R.drawable.pencil_question;
        String name = iconName.trim().toLowerCase();
        if (context != null) {
            int resId = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            if (resId != 0) return resId;
        }
        return getIconDrawableRes(name);
    }

    public static int getIconDrawableRes(String iconName) {
        if (iconName == null) return R.drawable.blocks;
        switch (iconName.toLowerCase().trim()) {
            case "box": return R.drawable.box;
            case "columns_3": return R.drawable.columns_3;
            case "stack_2": return R.drawable.stack_2;
            case "layout_grid": return R.drawable.layout_grid;
            case "layout_list": return R.drawable.layout_list;
            case "arrows_move_vertical": return R.drawable.arrows_move_vertical;
            case "cursor_text": return R.drawable.cursor_text;
            case "puzzle": return R.drawable.puzzle;
            case "photo": return R.drawable.photo;
            case "world": return R.drawable.world;
            case "pencil": return R.drawable.pencil;
            case "pencil_code": return R.drawable.pencil_code;
            case "check_bg": return R.drawable.check_bg;
            case "video": return R.drawable.video;
            case "file_music": return R.drawable.file_music;
            case "source_code": return R.drawable.source_code;
            case "ic_list_view": return R.drawable.ic_list_view;
            case "table": return R.drawable.table;
            case "palette": return R.drawable.palette;
            case "bulb": return R.drawable.bulb;
            case "blocks": return R.drawable.blocks;
            case "layout_grid_add": return R.drawable.layout_grid_add;
            case "file_code": return R.drawable.file_code;
            case "code_plus": return R.drawable.code_plus;
            case "droplet": return R.drawable.droplet;
            case "shadow": return R.drawable.shadow;
            case "folder": return R.drawable.folder;
            case "layers_linked": return R.drawable.layers_linked;
            case "text_size": return R.drawable.text_size;
            case "focus_centered": return R.drawable.focus_centered;
            case "text_color": return R.drawable.text_color;
            case "alphabet_cyrillic": return R.drawable.alphabet_cyrillic;
            case "background": return R.drawable.background;
            case "border_radius": return R.drawable.border_radius;
            case "border_style": return R.drawable.border_style;
            case "box_padding": return R.drawable.box_padding;
            case "box_margin": return R.drawable.box_margin;
            case "refresh": return R.drawable.refresh;
            case "arrow_autofit_width": return R.drawable.arrow_autofit_width;
            case "arrow_autofit_height": return R.drawable.arrow_autofit_height;
            case "box_model_2": return R.drawable.box_model_2;
            case "settings_code": return R.drawable.settings_code;
            case "pencil_question": return R.drawable.pencil_question;
            default: return R.drawable.blocks;
        }
    }

    public Map<String, Map<String, Object>> loadDesignProperties() {
        Map<String, Map<String, Object>> map = new HashMap<>();
        try {
            InputStream is = context.getAssets().open("design_properties.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            Map<String, Map<String, Object>> inbuilt = new Gson().fromJson(json,
                new TypeToken<Map<String, Map<String, Object>>>(){}.getType());
            if (inbuilt != null) {
                map.putAll(inbuilt);
            }
        } catch (Exception e) {
            Log.w("WidgetRegistry", "Could not load inbuilt design properties: " + e.getMessage());
        }
        File customFile = new File(FileUtil.getDragWebDir(context), "custom/design_properties.json");
        if (customFile.exists() && customFile.length() > 0) {
            try {
                String json = FileUtil.readFile(customFile.getAbsolutePath());
                if (json != null && !json.trim().isEmpty()) {
                    Map<String, Map<String, Object>> custom = new Gson().fromJson(json,
                        new TypeToken<Map<String, Map<String, Object>>>(){}.getType());
                    if (custom != null) {
                        map.putAll(custom);
                    }
                }
            } catch (Exception e) {
                Log.w("WidgetRegistry", "Could not load custom design properties: " + e.getMessage());
            }
        }
        return map;
    }

    public List<WidgetCategory> getWidgetCategories() {
        try {
            InputStream is = context.getAssets().open("widget_categories.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            return new Gson().fromJson(json, new TypeToken<List<WidgetCategory>>(){}.getType());
        } catch (Exception e) {
            Log.w("WidgetRegistry", "Could not load widget categories: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public ArrayList<HashMap<String, Object>> loadOnlyCustomWidgets() {
        File customFile = getCustomWidgetsFile();
        if (customFile != null && customFile.exists() && customFile.length() > 0) {
            try {
                String json = FileUtil.readFile(customFile.getAbsolutePath());
                if (json != null && !json.trim().isEmpty()) {
                    ArrayList<HashMap<String, Object>> custom = new Gson().fromJson(json,
                        new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
                    if (custom != null) {
                        // Filter out any accidentally saved inbuilt widgets
                        ArrayList<HashMap<String, Object>> inbuilt = loadInbuiltWidgets();
                        Set<String> inbuiltNames = new HashSet<>();
                        for (HashMap<String, Object> in : inbuilt) {
                            if (in != null && in.containsKey("name")) {
                                inbuiltNames.add(String.valueOf(in.get("name")).toLowerCase());
                            }
                        }
                        ArrayList<HashMap<String, Object>> filtered = new ArrayList<>();
                        for (HashMap<String, Object> c : custom) {
                            if (c != null && c.containsKey("name")) {
                                String cName = String.valueOf(c.get("name")).toLowerCase();
                                if (!inbuiltNames.contains(cName)) {
                                    filtered.add(c);
                                }
                            }
                        }
                        sanitizeWidgets(filtered);
                        return filtered;
                    }
                }
            } catch (Exception e) {
                Log.w("WidgetRegistry", "Could not load custom widgets: " + e.getMessage());
            }
        }
        return new ArrayList<>();
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

    public void saveWidget(HashMap<String, Object> widget) {
        updateOrAddWidget(widget);
    }

    public void updateOrAddWidget(HashMap<String, Object> widget) {
        if (widget == null) return;
        String name = widget.containsKey("name") ? widget.get("name").toString() : "";
        if (name.isEmpty()) return;

        ArrayList<HashMap<String, Object>> custom = loadOnlyCustomWidgets();
        boolean replaced = false;
        for (int i = 0; i < custom.size(); i++) {
            String existing = custom.get(i).containsKey("name")
                ? custom.get(i).get("name").toString() : "";
            if (name.equalsIgnoreCase(existing)) {
                custom.set(i, widget);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            custom.add(widget);
        }
        saveCustomWidgets(custom);
    }

    public void deleteWidget(String name) {
        if (name == null || name.isEmpty()) return;
        ArrayList<HashMap<String, Object>> custom = loadOnlyCustomWidgets();
        for (int i = 0; i < custom.size(); i++) {
            String existing = custom.get(i).containsKey("name")
                ? custom.get(i).get("name").toString() : "";
            if (name.equalsIgnoreCase(existing)) {
                custom.remove(i);
                break;
            }
        }
        saveCustomWidgets(custom);
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
            ArrayList<HashMap<String, Object>> existingCustom = loadOnlyCustomWidgets();
            for (HashMap<String, Object> custom : customWidgets) {
                String customName = custom.containsKey("name") ? custom.get("name").toString() : "";
                if (customName.isEmpty()) continue;
                boolean replaced = false;
                for (int i = 0; i < existingCustom.size(); i++) {
                    String existingName = existingCustom.get(i).containsKey("name")
                        ? existingCustom.get(i).get("name").toString() : "";
                    if (customName.equalsIgnoreCase(existingName)) {
                        existingCustom.set(i, custom);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    existingCustom.add(custom);
                }
            }

            // Save back only custom widgets
            saveCustomWidgets(existingCustom);
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
        
        ArrayList<HashMap<String, Object>> custom = loadOnlyCustomWidgets();
        // Remove existing if any
        for (int i = 0; i < custom.size(); i++) {
            if (name.equalsIgnoreCase(String.valueOf(custom.get(i).get("name")))) {
                custom.remove(i);
                break;
            }
        }
        
        int insertIndex = -1;
        for (int i = 0; i < custom.size(); i++) {
            String existing = custom.get(i).containsKey("name")
                ? custom.get(i).get("name").toString() : "";
            if (afterName.equalsIgnoreCase(existing)) {
                insertIndex = i + 1;
                break;
            }
        }
        if (insertIndex >= 0 && insertIndex <= custom.size()) {
            custom.add(insertIndex, widget);
        } else {
            custom.add(widget);
        }
        saveCustomWidgets(custom);
    }
}
