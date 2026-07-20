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

    private Context context;
    private Map<String, List<String>> paramTypes = new HashMap<>();
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
        // Step 1: Try reading from storage (same pattern as BlockDef / blocks.json)
        try {
            File file = new File(CustomStorageUtil.getCustomDir(context), "param.json");
            if (file.exists() && file.length() > 0) {
                String json = FileUtil.readFile(file.getAbsolutePath());
                if (json != null && !json.trim().isEmpty() && !json.trim().equals("{}")) {
                    Map<String, List<String>> loaded = gson.fromJson(json, new TypeToken<Map<String, List<String>>>() {}.getType());
                    if (loaded != null && !loaded.isEmpty()) {
                        paramTypes = loaded;
                        return;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Step 2: Fall back to assets (same pattern as how blocks.json reads from assets)
        if (context != null) {
            try (InputStream is = context.getAssets().open("param.json");
                 java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, n);
                }
                String assetJson = baos.toString("UTF-8");
                Map<String, List<String>> loaded = gson.fromJson(assetJson, new TypeToken<Map<String, List<String>>>() {}.getType());
                if (loaded != null && !loaded.isEmpty()) {
                    paramTypes = loaded;
                    save(); // Persist to storage so next load reads from disk
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (paramTypes == null) {
            paramTypes = new HashMap<>();
        }
    }

    public void save() {
        if (paramTypes == null || paramTypes.isEmpty()) {
            return; // Never overwrite disk file with empty map
        }
        try {
            File dir = CustomStorageUtil.getCustomDir(this.context);
            File file = new File(dir, "param.json");
            if (dir != null && !dir.exists()) dir.mkdirs();
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(paramTypes);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                fos.write(json.getBytes("UTF-8"));
                fos.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> getOptions(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) return new ArrayList<>();
        String clean = cleanTypeName(typeName);
        if (paramTypes.containsKey(clean)) {
            return new ArrayList<>(paramTypes.get(clean));
        }
        for (Map.Entry<String, List<String>> entry : paramTypes.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(clean)) {
                return new ArrayList<>(entry.getValue());
            }
        }
        return new ArrayList<>();
    }

    public boolean hasType(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) return false;
        String clean = cleanTypeName(typeName);
        if (paramTypes.containsKey(clean)) return true;
        for (String k : paramTypes.keySet()) {
            if (k.equalsIgnoreCase(clean)) return true;
        }
        return false;
    }

    private String findMatchingKey(String typeName) {
        String clean = cleanTypeName(typeName);
        for (String k : paramTypes.keySet()) {
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
        List<String> options = paramTypes.computeIfAbsent(key, k -> new ArrayList<>());
        if (option != null && !option.trim().isEmpty() && !options.contains(option.trim())) {
            options.add(option.trim());
        }
        save();
    }

    public void removeOption(String typeName, String option) {
        if (typeName == null || typeName.trim().isEmpty()) return;
        String key = findMatchingKey(typeName);
        List<String> options = paramTypes.get(key);
        if (options != null) {
            options.remove(option);
            save();
        }
    }

    public Map<String, List<String>> getAllParamTypes() {
        return paramTypes;
    }

    public void setOptions(String typeName, List<String> options) {
        if (typeName == null || typeName.trim().isEmpty()) return;
        String key = findMatchingKey(typeName);
        paramTypes.put(key, options != null ? new ArrayList<>(options) : new ArrayList<>());
        save();
    }
}
