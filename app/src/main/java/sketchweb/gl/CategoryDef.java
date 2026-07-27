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

    public static void clearCache() {
        // no-op: cache removed; always reads fresh from file
    }

    public static List<CategoryDef> getCategories(Context context) {
        if (context == null) return new ArrayList<>();

        try {
            java.io.File file = new java.io.File(CustomStorageUtil.getCustomDir(context), "categories.json");
            String json = null;
            if (file.exists() && file.length() > 0) {
                json = FileUtil.readFile(file.getAbsolutePath());
            }
            // Fall back to asset if custom file is missing/empty
            if (json == null || json.trim().isEmpty()) {
                try (java.io.InputStream is = context.getAssets().open("categories.json")) {
                    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                    json = s.hasNext() ? s.next() : "";
                } catch (Exception ignored) {}
            }
            if (json != null && !json.trim().isEmpty()) {
                List<CategoryDef> loaded = new Gson().fromJson(json, new TypeToken<List<CategoryDef>>(){}.getType());
                if (loaded != null) return loaded;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
}
