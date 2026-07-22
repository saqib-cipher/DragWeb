package sketchweb.gl;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which external icon libraries the project depends on and renders the
 * matching {@code <link>} / {@code <script>} tags into generated HTML.
 *
 * <p>Each entry is stored once per project in
 * {@code projects/<projectId>.icons} (one library id per line, comments with
 * {@code #}). Code generators read the list via {@link #generateHtmlIncludes()}
 * and emit clean, deduplicated CDN tags into the page {@code <head>}.
 *
 * <p>Adding more libraries is a one-line change in {@link #LIBRARIES}.
 */
public final class IconLibraryManager {

    /** A single icon library descriptor (CDN URL + recommended HTML usage). */
    public static final class Library {
        public final String id;
        public final String displayName;
        public final String version;
        public final String href;       // CSS or JS URL
        public final boolean isStylesheet;
        public final String integrity;  // optional SRI hash (may be null)
        public final String prefixHint; // example HTML usage shown in pickers

        Library(String id, String displayName, String version, String href,
                boolean isStylesheet, String integrity, String prefixHint) {
            this.id = id;
            this.displayName = displayName;
            this.version = version;
            this.href = href;
            this.isStylesheet = isStylesheet;
            this.integrity = integrity;
            this.prefixHint = prefixHint;
        }
    }

    /**
     * The full catalogue of supported libraries. Insertion order is the order
     * they appear in pickers and in the generated HTML head.
     */
    public static final Map<String, Library> LIBRARIES = buildCatalogue();

    private static Map<String, Library> buildCatalogue() {
        LinkedHashMap<String, Library> m = new LinkedHashMap<>();
        m.put("material-icons", new Library(
            "material-icons",
            "Material Icons",
            "latest",
            "https://cdn.jsdelivr.net/npm/@material-icons/font@latest/css/all.css",
            true, null,
            "<i class=\"material-icons mi-home\"></i>"));
        m.put("material-symbols", new Library(
            "material-symbols",
            "Material Symbols (Outlined)",
            "latest",
            "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,0,0",
            true, null,
            "<span class=\"material-symbols-outlined\">favorite</span>"));
        m.put("font-awesome", new Library(
            "font-awesome",
            "Font Awesome",
            "6.5.1",
            "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css",
            true, null,
            "<i class=\"fa-solid fa-rocket\"></i>"));
        m.put("bootstrap-icons", new Library(
            "bootstrap-icons",
            "Bootstrap Icons",
            "1.11.3",
            "https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css",
            true, null,
            "<i class=\"bi bi-house-door\"></i>"));
        m.put("feather-icons", new Library(
            "feather-icons",
            "Feather Icons",
            "4.29.1",
            "https://cdn.jsdelivr.net/npm/feather-icons@4.29.1/dist/feather.min.js",
            false, null,
            "<i data-feather=\"check-circle\"></i>"));
        m.put("lucide-icons", new Library(
            "lucide-icons",
            "Lucide Icons",
            "latest",
            "https://unpkg.com/lucide@latest/dist/umd/lucide.min.js",
            false, null,
            "<i data-lucide=\"camera\"></i>"));
        m.put("heroicons", new Library(
            "heroicons",
            "Heroicons (CDN sprite)",
            "2.x",
            "https://unpkg.com/heroicons@2/24/outline/sprite.svg",
            true, null,
            "<svg class=\"size-6\"><use href=\"#user\"/></svg>"));
        m.put("remix-icon", new Library(
            "remix-icon",
            "Remix Icon",
            "4.2.0",
            "https://cdn.jsdelivr.net/npm/remixicon@4.2.0/fonts/remixicon.css",
            true, null,
            "<i class=\"ri-home-line\"></i>"));
        m.put("phosphor-icons", new Library(
            "phosphor-icons",
            "Phosphor Icons",
            "2.0.3",
            "https://unpkg.com/@phosphor-icons/web@2.0.3",
            false, null,
            "<i class=\"ph ph-rocket\"></i>"));
        m.put("tabler-icons", new Library(
            "tabler-icons",
            "Tabler Icons",
            "3.5.0",
                "https://cdn.jsdelivr.net/npm/@tabler/icons-webfont@latest/dist/tabler-icons.min.css",
            true, null,
            "<i class=\"ti ti-rocket\"></i>"));
        return Collections.unmodifiableMap(m);
    }

    private final Context context;
    private final String projectId;
    private final Set<String> enabled = new LinkedHashSet<>();

    public IconLibraryManager(Context context, String projectId) {
        this.context = context;
        this.projectId = projectId;
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

    /**
     * Render the {@code <link>} / {@code <script>} tags for every enabled
     * library, in catalogue order. Emits two leading spaces of indentation so
     * the output drops cleanly into a {@code <head>} block.
     */
    public String generateHtmlIncludes() {
        if (enabled.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Library lib : LIBRARIES.values()) {
            if (!enabled.contains(lib.id)) continue;
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
        // Some JS-based libraries need a one-line bootstrap call after the DOM
        // is parsed; emit those as a second pass for cleanliness.
        StringBuilder boot = new StringBuilder();
        if (enabled.contains("feather-icons")) {
            boot.append("    if (window.feather) feather.replace();\n");
        }
        if (enabled.contains("lucide-icons")) {
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

    /**
     * Lookup hint markup for an icon library — used by the assets / icon
     * panel to show a worked example next to the toggle.
     */
    public static String exampleFor(String libId) {
        Library lib = LIBRARIES.get(libId);
        return lib == null ? "" : lib.prefixHint;
    }

    // -------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------

    File configFile() {
        File dir = new File(android.os.Environment.getExternalStorageDirectory(), ".dragweb/projects/" + projectId);
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
