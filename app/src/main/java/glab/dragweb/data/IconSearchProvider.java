package glab.dragweb.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Provides popular and searchable icon base names loaded from
 * {@code assets/icon_libraries.json} (with built-in fallback).
 */
public class IconSearchProvider {

    private static final List<String> FALLBACK_POPULAR = Arrays.asList(
        "home", "user", "settings", "search", "bell", "mail", "heart", "star",
        "shopping-cart", "check", "plus", "x", "camera", "file", "folder", "lock",
        "share", "download", "trash", "sparkles"
    );

    private static final List<String> FALLBACK_ICONS = Arrays.asList(
        "home", "user", "settings", "search", "plus", "minus", "check", "x",
        "arrow-right", "arrow-left", "chevron-right", "chevron-left", "calendar",
        "clock", "mail", "phone", "camera", "video", "music", "file", "folder",
        "heart", "star", "bell", "bookmark", "share", "download", "upload",
        "refresh", "trash", "edit", "external-link", "menu", "dots", "cloud",
        "database", "server", "layout", "box", "package", "tool", "code",
        "terminal", "cpu", "mobile", "laptop", "desktop", "rocket", "flame",
        "bulb", "moon", "sun", "world", "map-pin", "credit-card", "shopping-cart",
        "briefcase", "building", "car", "plane", "info", "help", "warning",
        "error", "play", "pause", "stop", "forward", "backward", "lock", "unlock",
        "sparkles", "tag", "book", "compass", "globe", "layers", "grid", "list"
    );

    private static List<String> cachedPopular;
    private static List<String> cachedCatalogue;

    private static synchronized void ensureLoaded(Context context) {
        if (cachedCatalogue != null && cachedPopular != null) return;
        if (context == null) return;
        try (InputStream is = context.getAssets().open("icon_libraries.json")) {
            byte[] buf = new byte[is.available()];
            int total = 0;
            while (total < buf.length) {
                int r = is.read(buf, total, buf.length - total);
                if (r < 0) break;
                total += r;
            }
            JSONObject root = new JSONObject(new String(buf, 0, total, StandardCharsets.UTF_8));
            JSONArray pop = root.optJSONArray("popularIcons");
            if (pop != null && pop.length() > 0) {
                List<String> list = new ArrayList<>();
                for (int i = 0; i < pop.length(); i++) {
                    String s = pop.optString(i, "").trim();
                    if (!s.isEmpty()) list.add(s);
                }
                cachedPopular = Collections.unmodifiableList(list);
            }
            JSONArray cat = root.optJSONArray("catalogue");
            if (cat != null && cat.length() > 0) {
                List<String> list = new ArrayList<>();
                for (int i = 0; i < cat.length(); i++) {
                    String s = cat.optString(i, "").trim();
                    if (!s.isEmpty()) list.add(s);
                }
                cachedCatalogue = Collections.unmodifiableList(list);
            }
        } catch (Exception ignored) {}
    }

    public static List<String> getPopularIcons(Context context) {
        ensureLoaded(context);
        return cachedPopular != null ? cachedPopular : FALLBACK_POPULAR;
    }

    public static List<String> search(String query) {
        return search(null, query);
    }

    public static List<String> search(Context context, String query) {
        ensureLoaded(context);
        List<String> source = cachedCatalogue != null ? cachedCatalogue : FALLBACK_ICONS;
        if (query == null || query.trim().isEmpty()) return source;
        String q = query.toLowerCase(Locale.US).trim();
        List<String> results = new ArrayList<>();
        for (String name : source) {
            if (name.toLowerCase(Locale.US).contains(q)) {
                results.add(name);
            }
        }
        return results;
    }
}
