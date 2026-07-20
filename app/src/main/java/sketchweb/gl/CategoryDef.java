package sketchweb.gl;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CategoryDef {
    public String id;
    public String name;
    public String catColor;
    public String type; // css | js | common

    private static List<CategoryDef> cachedCategories;

    public static void clearCache() {
        cachedCategories = null;
    }

    public static List<CategoryDef> getCategories(Context context) {
        if (cachedCategories != null && !cachedCategories.isEmpty()) {
            return cachedCategories;
        }
        cachedCategories = new ArrayList<>();
        if (context == null) return cachedCategories;

        try {
            java.io.File file = CustomStorageUtil.getCustomFile(context, "categories.json");
            String json = FileUtil.readFile(file.getAbsolutePath());
            if (json != null && !json.trim().isEmpty()) {
                List<CategoryDef> loaded = new Gson().fromJson(json, new TypeToken<List<CategoryDef>>(){}.getType());
                if (loaded != null) {
                    cachedCategories = loaded;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cachedCategories;
    }
}
