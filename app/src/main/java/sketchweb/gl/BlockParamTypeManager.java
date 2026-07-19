package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockParamTypeManager {

    private static final String PARAMS_FILE_PATH = 
        Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/custom/params.json";

    private Map<String, List<String>> paramTypes = new HashMap<>();
    private Gson gson = new Gson();

    public BlockParamTypeManager() {
        load();
    }

    public void load() {
        File file = new File(PARAMS_FILE_PATH);
        if (!file.exists()) {
            // Default built-in params
            paramTypes.put("position", List.of("static", "relative", "absolute", "fixed", "sticky"));
            paramTypes.put("display", List.of("block", "none", "flex", "grid", "inline-block", "inline-flex"));
            paramTypes.put("overflow", List.of("visible", "hidden", "scroll", "auto"));
            paramTypes.put("textAlign", List.of("left", "center", "right", "justify"));
            paramTypes.put("textDecoration", List.of("none", "underline", "overline", "line-through"));
            paramTypes.put("fontWeight", List.of("normal", "bold", "100", "200", "300", "400", "500", "600", "700", "800", "900"));
            paramTypes.put("fontStyle", List.of("normal", "italic", "oblique"));
            paramTypes.put("cursor", List.of("default", "pointer", "move", "text", "wait", "help", "not-allowed", "grab", "grabbing"));
            paramTypes.put("flexDirection", List.of("row", "row-reverse", "column", "column-reverse"));
            paramTypes.put("justifyContent", List.of("flex-start", "flex-end", "center", "space-between", "space-around", "space-evenly"));
            paramTypes.put("alignItems", List.of("stretch", "flex-start", "flex-end", "center", "baseline"));
            paramTypes.put("selector", List.of("body", "h1", "p", ".active", "#main", "div"));
            paramTypes.put("unit", List.of("px", "%", "em", "rem", "vh", "vw"));
            save();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            paramTypes = gson.fromJson(reader, new TypeToken<Map<String, List<String>>>() {}.getType());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        File file = new File(PARAMS_FILE_PATH);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(paramTypes, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> getOptions(String typeName) {
        if (!paramTypes.containsKey(typeName)) {
            if (typeName.equals("selector")) {
                return List.of("body", "h1", "p", ".active", "#main", "div");
            }
            if (typeName.equals("unit")) {
                return List.of("px", "%", "em", "rem", "vh", "vw");
            }
        }
        return paramTypes.getOrDefault(typeName, new ArrayList<>());
    }

    public void addOption(String typeName, String option) {
        List<String> options = paramTypes.computeIfAbsent(typeName, k -> new ArrayList<>());
        if (!options.contains(option)) {
            options.add(option);
            save();
        }
    }

    public void removeOption(String typeName, String option) {
        List<String> options = paramTypes.get(typeName);
        if (options != null) {
            options.remove(option);
            save();
        }
    }

    public Map<String, List<String>> getAllParamTypes() {
        return paramTypes;
    }
    
    public void setOptions(String typeName, List<String> options) {
        paramTypes.put(typeName, options);
        save();
    }
}
