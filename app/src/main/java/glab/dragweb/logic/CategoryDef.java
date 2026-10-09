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

    private static List<CategoryDef> sCachedCategories = null;

    public static synchronized void clearCache() {
        sCachedCategories = null;
    }

    public static List<CategoryDef> loadInbuiltCategories(Context context) {
        if (context == null) return new ArrayList<>();
        try (InputStream is = context.getAssets().open("categories.json")) {
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            String json = s.hasNext() ? s.next() : "";
            if (json != null && !json.trim().isEmpty()) {
                List<CategoryDef> loaded = new Gson().fromJson(json, new TypeToken<List<CategoryDef>>(){}.getType());
                if (loaded != null) return loaded;
            }
        } catch (Exception ignored) {}
        return new ArrayList<>();
    }

    public static List<CategoryDef> loadCustomCategories(Context context) {
        if (context == null) return new ArrayList<>();
        java.io.File file = new java.io.File(CustomStorageUtil.getCustomDir(context), "categories.json");
        if (file.exists() && file.length() > 0) {
            String json = FileUtil.readFile(file.getAbsolutePath());
            if (json != null && !json.trim().isEmpty()) {
                try {
                    List<CategoryDef> loaded = new Gson().fromJson(json, new TypeToken<List<CategoryDef>>(){}.getType());
                    if (loaded != null) return loaded;
                } catch (Exception ignored) {}
            }
        }
        return new ArrayList<>();
    }

    public static synchronized List<CategoryDef> getCategories(Context context) {
        if (context == null) return new ArrayList<>();
        if (sCachedCategories != null) {
            return sCachedCategories;
        }
        List<CategoryDef> result = new ArrayList<>(loadInbuiltCategories(context));
        List<CategoryDef> custom = loadCustomCategories(context);
        if (custom != null) {
            result.addAll(custom);
        }
        sCachedCategories = result;
        return result;
    }
}
