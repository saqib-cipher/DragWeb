package glab.dragweb.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import glab.dragweb.util.FileUtil;

/**
 * Tracks external icon libraries, loads catalogue metadata from
 * {@code assets/icon_libraries.json}, auto-detects icon libraries referenced in
 * generated HTML, and emits deduplicated {@code <link>} / {@code <script>} tags.
 */
public final class IconLibraryManager {

    /** A single icon library descriptor (CDN URL + recommended HTML usage). */
    public static final class Library {
        public final String id;
        public final String shortName;
        public final String displayName;
        public final String version;
        public final String href;       // CSS or JS URL
        public final boolean isStylesheet;
        public final String integrity;  // optional SRI hash (may be null)
        public final String prefixHint; // example HTML usage shown in pickers
        public final String prefix;
        public final String suffix;
        public final boolean picker;

        Library(String id, String displayName, String version, String href,
                boolean isStylesheet, String integrity, String prefixHint) {
            this(id, displayName, displayName, version, href, isStylesheet, integrity, prefixHint, "", "", false);
        }

        Library(String id, String shortName, String displayName, String version, String href,
                boolean isStylesheet, String integrity, String prefixHint,
                String prefix, String suffix, boolean picker) {
            this.id = id;
            this.shortName = shortName;
            this.displayName = displayName;
            this.version = version;
            this.href = href;
            this.isStylesheet = isStylesheet;
            this.integrity = integrity;
            this.prefixHint = prefixHint;
            this.prefix = prefix != null ? prefix : "";
            this.suffix = suffix != null ? suffix : "";
            this.picker = picker;
        }
    }

    private static final LinkedHashMap<String, Library> MUTABLE_LIBRARIES = buildFallbackCatalogue();
    public static final Map<String, Library> LIBRARIES = Collections.unmodifiableMap(MUTABLE_LIBRARIES);
    private static boolean loadedFromAssets = false;

    private static final Pattern PAT_LUCIDE = Pattern.compile("data-lucide\\s*=\\s*[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_FEATHER = Pattern.compile("data-feather\\s*=\\s*[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_TABLER = Pattern.compile("\\bti\\s+ti-[a-z0-9-]+|\\bti-[a-z0-9-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_FA = Pattern.compile("\\bfa-(?:solid|regular|brands|light|thin|duotone|sharp)\\b|\\bfas\\s+fa-|\\bfar\\s+fa-|\\bfab\\s+fa-|\\bfa-[a-z0-9-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_BOOTSTRAP = Pattern.compile("\\bbi\\s+bi-[a-z0-9-]+|\\bbi-[a-z0-9-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_PHOSPHOR = Pattern.compile("\\bph(?:-(?:thin|light|bold|fill|duotone))?\\s+ph-[a-z0-9-]+|\\bph-[a-z0-9-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_REMIX = Pattern.compile("\\bri-[a-z0-9-]+-(?:line|fill)\\b|\\bri-[a-z0-9-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_MATERIAL_ICONS = Pattern.compile("\\bmaterial-icons\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_MATERIAL_SYMBOLS = Pattern.compile("\\bmaterial-symbols-(?:outlined|rounded|sharp)\\b", Pattern.CASE_INSENSITIVE);

    private static LinkedHashMap<String, Library> buildFallbackCatalogue() {
        LinkedHashMap<String, Library> m = new LinkedHashMap<>();
        m.put("tabler-icons", new Library(
            "tabler-icons", "Tabler", "Tabler Icons", "3.5.0",
            "https://cdn.jsdelivr.net/npm/@tabler/icons-webfont@latest/dist/tabler-icons.min.css",
            true, null, "<i class=\"ti ti-rocket\"></i>", "ti ti-", "", true));
        m.put("font-awesome", new Library(
            "font-awesome", "FontAwesome", "Font Awesome", "6.5.1",
            "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css",
            true, null, "<i class=\"fa-solid fa-rocket\"></i>", "fa-solid fa-", "", true));
        m.put("lucide-icons", new Library(
            "lucide-icons", "Lucide", "Lucide Icons", "latest",
            "https://unpkg.com/lucide@latest/dist/umd/lucide.min.js",
            false, null, "<i data-lucide=\"camera\"></i>", "lucide-", "", true));
        m.put("bootstrap-icons", new Library(
            "bootstrap-icons", "Bootstrap", "Bootstrap Icons", "1.11.3",
            "https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css",
            true, null, "<i class=\"bi bi-house-door\"></i>", "bi bi-", "", true));
        m.put("phosphor-icons", new Library(
            "phosphor-icons", "Phosphor", "Phosphor Icons", "2.0.3",
            "https://unpkg.com/@phosphor-icons/web@2.0.3",
            false, null, "<i class=\"ph ph-rocket\"></i>", "ph ph-", "", true));
        m.put("remix-icon", new Library(
            "remix-icon", "Remix", "Remix Icon", "4.2.0",
            "https://cdn.jsdelivr.net/npm/remixicon@4.2.0/fonts/remixicon.css",
            true, null, "<i class=\"ri-home-line\"></i>", "ri-", "-line", true));
        m.put("material-icons", new Library(
            "material-icons", "Material", "Material Icons", "latest",
            "https://cdn.jsdelivr.net/npm/@material-icons/font@latest/css/all.css",
            true, null, "<i class=\"material-icons mi-home\"></i>", "material-icons mi-", "", true));
        m.put("material-symbols", new Library(
            "material-symbols", "Symbols", "Material Symbols (Outlined)", "latest",
            "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,0,0",
            true, null, "<span class=\"material-symbols-outlined\">favorite</span>", "", "", false));
        m.put("feather-icons", new Library(
            "feather-icons", "Feather", "Feather Icons", "4.29.1",
            "https://cdn.jsdelivr.net/npm/feather-icons@4.29.1/dist/feather.min.js",
            false, null, "<i data-feather=\"check-circle\"></i>", "", "", false));
        m.put("heroicons", new Library(
            "heroicons", "Heroicons", "Heroicons (CDN sprite)", "2.x",
            "https://unpkg.com/heroicons@2/24/outline/sprite.svg",
            true, null, "<svg class=\"size-6\"><use href=\"#user\"/></svg>", "", "", false));
        return m;
    }

    public static synchronized void ensureLoaded(Context context) {
        if (loadedFromAssets || context == null) return;
        try (InputStream is = context.getAssets().open("icon_libraries.json")) {
            byte[] buf = new byte[is.available()];
            int total = 0;
            while (total < buf.length) {
                int r = is.read(buf, total, buf.length - total);
                if (r < 0) break;
                total += r;
            }
            JSONObject root = new JSONObject(new String(buf, 0, total, StandardCharsets.UTF_8));
            JSONArray libs = root.optJSONArray("libraries");
            if (libs != null && libs.length() > 0) {
                LinkedHashMap<String, Library> parsed = new LinkedHashMap<>();
                for (int i = 0; i < libs.length(); i++) {
                    JSONObject o = libs.optJSONObject(i);
                    if (o == null) continue;
                    String id = o.optString("id", "").trim();
                    if (id.isEmpty()) continue;
                    parsed.put(id, new Library(
                        id,
                        o.optString("shortName", o.optString("displayName", id)),
                        o.optString("displayName", id),
                        o.optString("version", "latest"),
                        o.optString("href", ""),
                        o.optBoolean("isStylesheet", true),
                        o.isNull("integrity") ? null : o.optString("integrity", null),
                        o.optString("prefixHint", ""),
                        o.optString("prefix", ""),
                        o.optString("suffix", ""),
                        o.optBoolean("picker", false)
                    ));
                }
                if (!parsed.isEmpty()) {
                    MUTABLE_LIBRARIES.clear();
                    MUTABLE_LIBRARIES.putAll(parsed);
                }
            }
            loadedFromAssets = true;
        } catch (Exception ignored) {}
    }

    public static List<Library> pickerLibraries(Context context) {
        ensureLoaded(context);
        List<Library> list = new ArrayList<>();
        for (Library lib : LIBRARIES.values()) {
            if (lib.picker) list.add(lib);
        }
        return list;
    }

    /**
     * Scans generated HTML body markup to detect which icon libraries are actually referenced,
     * so previews and exported sites include the required icon fonts/scripts automatically.
     */
    public static Set<String> detectLibrariesInHtml(String html) {
        Set<String> detected = new LinkedHashSet<>();
        if (html == null || html.isEmpty()) return detected;
        if (PAT_LUCIDE.matcher(html).find()) detected.add("lucide-icons");
        if (PAT_FEATHER.matcher(html).find()) detected.add("feather-icons");
        if (PAT_TABLER.matcher(html).find()) detected.add("tabler-icons");
        if (PAT_FA.matcher(html).find()) detected.add("font-awesome");
        if (PAT_BOOTSTRAP.matcher(html).find()) detected.add("bootstrap-icons");
        if (PAT_PHOSPHOR.matcher(html).find()) detected.add("phosphor-icons");
        if (PAT_REMIX.matcher(html).find()) detected.add("remix-icon");
        if (PAT_MATERIAL_ICONS.matcher(html).find()) detected.add("material-icons");
        if (PAT_MATERIAL_SYMBOLS.matcher(html).find()) detected.add("material-symbols");
        return detected;
    }

    /**
     * Emits the icon markup inside a generated {@code <nav>} item, supporting class-based
     * libraries (Tabler, FontAwesome, Bootstrap, Phosphor, Remix, Material) as well as Lucide.
     */
    public static void appendNavIconHtml(StringBuilder html, String iconName) {
        if (html == null || iconName == null) return;
        String trimmed = iconName.trim();
        if (trimmed.isEmpty()) return;

        String lower = trimmed.toLowerCase(Locale.US);
        String classAttr = null;
        if (lower.startsWith("ti ti-") || lower.startsWith("ph ph-") || lower.startsWith("bi bi-")
                || lower.startsWith("fa-") || lower.startsWith("fas ") || lower.startsWith("far ")
                || lower.startsWith("fab ") || lower.startsWith("fa ") || lower.startsWith("ri-")
                || lower.startsWith("material-")) {
            classAttr = trimmed;
        } else if (lower.startsWith("ti-")) {
            classAttr = "ti " + trimmed;
        } else if (lower.startsWith("bi-")) {
            classAttr = "bi " + trimmed;
        } else if (lower.startsWith("ph-")) {
            classAttr = "ph " + trimmed;
        }

        if (classAttr != null) {
            html.append("<i class=\"").append(escapeHtmlAttr(classAttr))
                .append("\" style=\"font-size:16px;line-height:1;\"></i> ");
        } else {
            String lucideName = lower.startsWith("lucide-")
                ? trimmed.substring("lucide-".length())
                : trimmed;
            html.append("<i data-lucide=\"").append(escapeHtmlAttr(lucideName))
                .append("\" style=\"width:16px;height:16px;\"></i> ");
        }
    }

    /**
     * Emits responsive {@code <nav>} brand + menu link markup shared by preview and export.
     */
    public static void appendNavChildrenHtml(StringBuilder html, Map<String, Object> function, String indent) {
        if (html == null || function == null) return;
        if (!(function.get("items") instanceof List)) return;
        List<?> items = (List<?>) function.get("items");
        if (items.isEmpty()) return;

        html.append("\n");
        String brand = function.containsKey("brand") && function.get("brand") != null
                ? function.get("brand").toString().trim() : "";
        if (!brand.isEmpty()) {
            html.append(indent).append("  <span class=\"nav-brand\" style=\"font-weight:700;font-size:18px;letter-spacing:-0.01em;white-space:nowrap;\">")
                .append(escapeHtmlText(brand)).append("</span>\n");
        }

        html.append(indent).append("  <div class=\"nav-menu\" style=\"display:flex;flex-wrap:wrap;gap:6px 14px;align-items:center;min-width:0;\">\n");
        List<?> icons = (function.containsKey("icons") && function.get("icons") instanceof List)
                ? (List<?>) function.get("icons") : null;
        List<?> hrefs = (function.containsKey("hrefs") && function.get("hrefs") instanceof List)
                ? (List<?>) function.get("hrefs")
                : ((function.containsKey("links") && function.get("links") instanceof List) ? (List<?>) function.get("links") : null);

        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item == null) continue;
            String targetHref = "#";
            if (hrefs != null && i < hrefs.size() && hrefs.get(i) != null) {
                String h = hrefs.get(i).toString().trim();
                if (!h.isEmpty()) targetHref = h;
            }
            String iconName = (icons != null && i < icons.size() && icons.get(i) != null)
                    ? icons.get(i).toString().trim() : "";
            html.append(indent).append("    <a href=\"").append(escapeHtmlAttr(targetHref))
                .append("\" class=\"nav-item\" style=\"color:inherit;text-decoration:none;display:inline-flex;align-items:center;gap:6px;padding:6px 10px;border-radius:6px;font-size:14px;font-weight:500;white-space:nowrap;\">");
            if (!iconName.isEmpty()) {
                appendNavIconHtml(html, iconName);
            }
            html.append(escapeHtmlText(item.toString())).append("</a>\n");
        }
        html.append(indent).append("  </div>\n");
        html.append(indent);
    }

    private static String escapeHtmlText(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeHtmlAttr(String s) {
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private final Context context;
    private final String projectId;
    private final Set<String> enabled = new LinkedHashSet<>();

    public IconLibraryManager(Context context, String projectId) {
        this.context = context;
        this.projectId = projectId;
        ensureLoaded(context);
        load();
    }

    public List<Library> allLibraries() {
        return new ArrayList<>(LIBRARIES.values());
    }

    public Set<String> enabledIds() {
        return Collections.unmodifiableSet(enabled);
    }

    public boolean isEnabled(String id) {
        return enabled.contains(id);
    }

    public void enable(String id) {
        if (id == null) return;
        if (LIBRARIES.containsKey(id)) {
            enabled.add(id);
            persist();
        }
    }

    public void disable(String id) {
        if (id == null) return;
        if (enabled.remove(id)) persist();
    }

    public void toggle(String id) {
        if (isEnabled(id)) disable(id);
        else enable(id);
    }

    public String generateHtmlIncludes() {
        return generateHtmlIncludes(Collections.emptySet());
    }

    public String generateHtmlIncludes(Collection<String> extraIds) {
        Set<String> merged = new LinkedHashSet<>(enabled);
        if (extraIds != null) {
            for (String id : extraIds) {
                if (id != null && LIBRARIES.containsKey(id)) merged.add(id);
            }
        }
        return generateHtmlIncludesFor(context, merged);
    }

    public static String generateHtmlIncludesFor(Context context, Collection<String> ids) {
        ensureLoaded(context);
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Library lib : LIBRARIES.values()) {
            if (!ids.contains(lib.id)) continue;
            if (lib.isStylesheet) {
                sb.append("  <link rel=\"stylesheet\" href=\"")
                  .append(lib.href).append("\"");
                if (lib.integrity != null) {
                    sb.append(" integrity=\"").append(lib.integrity)
                      .append("\" crossorigin=\"anonymous\"");
                }
                sb.append(">\n");
            } else {
                sb.append("  <script src=\"").append(lib.href)
                  .append("\" defer></script>\n");
            }
        }
        StringBuilder boot = new StringBuilder();
        if (ids.contains("feather-icons")) {
            boot.append("    if (window.feather) feather.replace();\n");
        }
        if (ids.contains("lucide-icons")) {
            boot.append("    if (window.lucide) lucide.createIcons();\n");
        }
        if (boot.length() > 0) {
            sb.append("  <script>\n");
            sb.append("    document.addEventListener('DOMContentLoaded', function() {\n");
            sb.append(boot);
            sb.append("    });\n");
            sb.append("  </script>\n");
        }
        return sb.toString();
    }

    public static String exampleFor(String libId) {
        Library lib = LIBRARIES.get(libId);
        return lib == null ? "" : lib.prefixHint;
    }

    // -------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------

    File configFile() {
        File dir = new File(FileUtil.getDragWebDir(), "projects/" + projectId);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "icons.json");
    }

    private void load() {
        File f = configFile();
        if (!f.exists()) return;
        String body = FileUtil.readFile(f.getAbsolutePath());
        if (body == null) return;
        for (String raw : body.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String id = line.toLowerCase(Locale.US);
            if (LIBRARIES.containsKey(id)) enabled.add(id);
        }
    }

    private void persist() {
        File f = configFile();
        StringBuilder sb = new StringBuilder();
        sb.append("# DragWeb icon-library configuration. One library id per line.\n");
        for (String id : enabled) sb.append(id).append('\n');
        FileUtil.writeFile(f.getAbsolutePath(), sb.toString());
    }
}
