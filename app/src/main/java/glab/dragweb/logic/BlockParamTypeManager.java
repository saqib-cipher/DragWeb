package glab.dragweb.logic;

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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlockParamTypeManager {

    private Context context;
    private Map<String, List<String>> inbuiltParamTypes = new LinkedHashMap<>();
    private Map<String, List<String>> customParamTypes = new LinkedHashMap<>();
    private Gson gson = new Gson();

    public BlockParamTypeManager() {
        load(null);
    }

    public BlockParamTypeManager(Context context) {
        this.context = context;
        load(context);
    }

    public void load() {
        load(this.context);
    }

    public void load(Context context) {
        this.context = context;
        loadInbuilt(context);
        loadCustom(context);
    }

    private void loadInbuilt(Context context) {
        inbuiltParamTypes.clear();
        if (context != null) {
            try (InputStream is = context.getAssets().open("param.json");
                 java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = is.read(buffer)) != -1) baos.write(buffer, 0, n);
                String assetJson = baos.toString("UTF-8");
                Map<String, List<String>> loaded = gson.fromJson(assetJson,
                    new TypeToken<Map<String, List<String>>>() {}.getType());
                if (loaded != null && !loaded.isEmpty()) {
                    inbuiltParamTypes.putAll(loaded);
                    return;
                }
            } catch (Exception ignored) {}
        }
        inbuiltParamTypes.putAll(getDefaults());
    }

    private void loadCustom(Context context) {
        customParamTypes.clear();
        if (context == null) return;
        try {
            File file = new File(CustomStorageUtil.getCustomDir(context), "param.json");
            if (file.exists() && file.length() > 0) {
                String json = FileUtil.readFile(file.getAbsolutePath());
                if (json != null && !json.trim().isEmpty() && !json.trim().equals("{}")) {
                    Map<String, List<String>> loaded = gson.fromJson(json,
                        new TypeToken<Map<String, List<String>>>() {}.getType());
                    if (loaded != null) {
                        customParamTypes.putAll(loaded);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, List<String>> getDefaults() {
        Map<String, List<String>> d = new LinkedHashMap<>();
        d.put("position", Arrays.asList("static", "relative", "absolute", "fixed", "sticky"));
        d.put("display", Arrays.asList("block", "none", "flex", "grid", "inline-block", "inline-flex"));
        d.put("overflow", Arrays.asList("visible", "hidden", "scroll", "auto"));
        d.put("textAlign", Arrays.asList("left", "center", "right", "justify"));
        d.put("textDecoration", Arrays.asList("none", "underline", "overline", "line-through"));
        d.put("fontWeight", Arrays.asList("normal", "bold", "100", "200", "300", "400", "500", "600", "700", "800", "900"));
        d.put("fontStyle", Arrays.asList("normal", "italic", "oblique"));
        d.put("cursor", Arrays.asList("default", "pointer", "move", "text", "wait", "help", "not-allowed", "grab", "grabbing"));
        d.put("flexDirection", Arrays.asList("row", "row-reverse", "column", "column-reverse"));
        d.put("flexWrap", Arrays.asList("nowrap", "wrap", "wrap-reverse"));
        d.put("borderStyle", Arrays.asList("none", "solid", "dashed", "dotted", "double", "groove", "ridge", "inset", "outset"));
        d.put("textTransform", Arrays.asList("none", "capitalize", "uppercase", "lowercase"));
        d.put("justifyContent", Arrays.asList("flex-start", "flex-end", "center", "space-between", "space-around", "space-evenly"));
        d.put("alignItems", Arrays.asList("stretch", "flex-start", "flex-end", "center", "baseline"));
        d.put("selector", Arrays.asList("body", "h1", "p", ".active", "#main", "div"));
        d.put("unit", Arrays.asList("px", "%", "em", "rem", "vh", "vw","deg","rad"));
        d.put("formMethod", Arrays.asList("GET", "POST", "dialog"));
        d.put("transitionProperty", Arrays.asList("all", "color", "background-color", "transform", "opacity", "width", "height", "margin", "padding", "border-radius", "border-color", "box-shadow"));
        d.put("timingFunction", Arrays.asList("linear", "ease", "ease-in", "ease-out", "ease-in-out", "step-start", "step-end"));
        d.put("shadowType", Arrays.asList("outline", "inset"));
        d.put("filterType", Arrays.asList("blur", "grayscale", "hue-rotate", "invert", "saturate", "sepia"));
        return d;
    }

    public void save() {
        try {
            File dir = CustomStorageUtil.getCustomDir(this.context);
            if (dir == null) return;
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "param.json");
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(customParamTypes);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.getBytes("UTF-8"));
                fos.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<String, List<String>> getAllParamTypes() {
        Map<String, List<String>> merged = new LinkedHashMap<>(inbuiltParamTypes);
        merged.putAll(customParamTypes);
        return merged;
    }

    public Map<String, List<String>> getCustomParamTypes() {
        return customParamTypes;
    }

    public List<String> getOptions(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) return new ArrayList<>();
        String clean = cleanTypeName(typeName);
        Map<String, List<String>> all = getAllParamTypes();
        if (all.containsKey(clean)) {
            return new ArrayList<>(all.get(clean));
        }
        for (Map.Entry<String, List<String>> entry : all.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(clean)) {
                return new ArrayList<>(entry.getValue());
            }
        }
        return new ArrayList<>();
    }

    public boolean hasType(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) return false;
        String clean = cleanTypeName(typeName);
        Map<String, List<String>> all = getAllParamTypes();
        if (all.containsKey(clean)) return true;
        for (String k : all.keySet()) {
            if (k.equalsIgnoreCase(clean)) return true;
        }
        return false;
    }

    private String findMatchingKey(String typeName) {
        String clean = cleanTypeName(typeName);
        for (String k : customParamTypes.keySet()) {
            if (k.equalsIgnoreCase(clean)) return k;
        }
        return clean;
    }

    private String cleanTypeName(String typeName) {
        if (typeName == null) return "";
        String s = typeName.trim();
        if (s.startsWith("%m.")) s = s.substring(3);
        else if (s.startsWith("%s.")) s = s.substring(3);
        else if (s.startsWith("m.")) s = s.substring(2);
        else if (s.startsWith("s.")) s = s.substring(2);
        return s;
    }

    public void addOption(String typeName, String option) {
        if (typeName == null || typeName.trim().isEmpty()) return;
        String key = findMatchingKey(typeName);
        List<String> options = customParamTypes.computeIfAbsent(key, k -> new ArrayList<>());
        if (option != null && !option.trim().isEmpty() && !options.contains(option.trim())) {
            options.add(option.trim());
        }
        save();
    }

    public void removeOption(String typeName, String option) {
        if (typeName == null || typeName.trim().isEmpty()) return;
        String key = findMatchingKey(typeName);
        List<String> options = customParamTypes.get(key);
        if (options != null) {
            options.remove(option);
            save();
        }
    }

    public void setOptions(String typeName, List<String> options) {
        if (typeName == null || typeName.trim().isEmpty()) return;
        String key = findMatchingKey(typeName);
        customParamTypes.put(key, options != null ? new ArrayList<>(options) : new ArrayList<>());
        save();
    }
}
