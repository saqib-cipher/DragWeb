package sketchweb.gl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Provides a curated list of popular icon base names.
 * Library-specific prefixes and suffixes are applied at the UI level.
 */
public class IconSearchProvider {

    private static final List<String> ICONS = new ArrayList<>();

    static {
        String[] popular = {
            "home", "user", "settings", "search", "plus", "minus", "check", "x", 
            "arrow-right", "arrow-left", "chevron-right", "chevron-left", "calendar", 
            "clock", "mail", "phone", "camera", "video", "music", "file", "folder", 
            "heart", "star", "bell", "bookmark", "share", "download", "upload", 
            "refresh", "trash", "edit", "external-link", "menu", "dots", "cloud", 
            "database", "server", "layout", "box", "package", "tool", "code", 
            "terminal", "cpu", "mobile", "laptop", "desktop", "rocket", "flame", 
            "bulb", "moon", "sun", "world", "map-pin", "credit-card", "shopping-cart", 
            "briefcase", "building", "car", "plane", "info", "help", "warning", 
            "error", "play", "pause", "stop", "forward", "backward", "lock", "unlock"
        };
        Collections.addAll(ICONS, popular);
    }

    public static List<String> search(String query) {
        if (query == null || query.isEmpty()) return ICONS;
        String q = query.toLowerCase(Locale.US).trim();
        List<String> results = new ArrayList<>();
        for (String name : ICONS) {
            if (name.contains(q)) {
                results.add(name);
            }
        }
        return results;
    }
}
