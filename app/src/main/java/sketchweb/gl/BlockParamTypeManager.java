package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockParamTypeManager {

    private static final String PARAMS_FILE_PATH = 
        Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/custom/params.json";
    private static final String PARAM_ALT_PATH = 
        Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/custom/param.json";

    private Map<String, List<String>> paramTypes = new HashMap<>();
    private Gson gson = new Gson();

    public BlockParamTypeManager() {
        load(null);
    }

    public BlockParamTypeManager(Context context) {
        load(context);
    }

    public void load() {
        load(null);
    }

    public void load(Context context) {
        File file = CustomStorageUtil.getCustomFile(context, "param.json");
        try {
            String json = FileUtil.readFile(file.getAbsolutePath());
            if (json != null && !json.trim().isEmpty()) {
                Map<String, List<String>> loaded = gson.fromJson(json, new TypeToken<Map<String, List<String>>>() {}.getType());
                if (loaded != null) {
                    paramTypes = loaded;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (paramTypes == null) {
            paramTypes = new HashMap<>();
        }
    }

    public void save() {
        File file = CustomStorageUtil.getCustomFile(null, "param.json");
        try {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(paramTypes);
            FileUtil.writeFile(file.getAbsolutePath(), json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> getOptions(String typeName) {
        return paramTypes.getOrDefault(typeName, new ArrayList<>());
    }

    public void addOption(String typeName, String option) {
        List<String> options = paramTypes.computeIfAbsent(typeName, k -> new ArrayList<>());
        if (option != null && !option.isEmpty() && !options.contains(option)) {
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
        paramTypes.put(typeName, options != null ? options : new ArrayList<>());
        save();
    }
}
