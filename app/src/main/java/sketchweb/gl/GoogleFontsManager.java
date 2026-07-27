package sketchweb.gl;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GoogleFontsManager {

    private final Context context;
    private final String projectId;
    private final Set<String> enabled = new LinkedHashSet<>();
    private final List<CustomImport> customImports = new ArrayList<>();
    private final Set<String> selectedPages = new LinkedHashSet<>();

    public GoogleFontsManager(Context context, String projectId) {
        this.context = context;
        this.projectId = projectId;
        load();
    }

    public Set<String> enabledFonts() { return Collections.unmodifiableSet(enabled); }
    public boolean isEnabled(String name) { return enabled.contains(name); }

    public void enable(String name) { if (name != null && !name.isEmpty()) { enabled.add(name); persist(); } }
    public void disable(String name) { if (enabled.remove(name)) persist(); }
    public void setEnabled(List<String> fonts) { enabled.clear(); enabled.addAll(fonts); persist(); }

    public List<CustomImport> getCustomImports() { return new ArrayList<>(customImports); }
    public void setCustomImports(List<CustomImport> imports) { customImports.clear(); customImports.addAll(imports); persist(); }
    public void addCustomImport(CustomImport ci) { customImports.add(ci); persist(); }
    public void removeCustomImport(int index) { if (index >= 0 && index < customImports.size()) { customImports.remove(index); persist(); } }

    public String generateGoogleFontsLinkTag() {
        return generateGoogleFontsLinkTag(null);
    }

    public String generateGoogleFontsLinkTag(String pageName) {
        // If pageName is null (backward compat), include links for all pages.
        // Otherwise only include if this page is in the selected pages set.
        if (pageName != null && !selectedPages.contains(pageName)) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // Enabled Google Fonts from assets/fonts.json
        List<FontItem> all = loadFontsFromAssets(context);
        boolean hasAny = false;
        for (FontItem f : all) {
            if (enabled.contains(f.name) && f.href != null && !f.href.isEmpty()) {
                if (!hasAny) {
                    hasAny = true;
                    sb.append("  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">\n");
                    sb.append("  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>\n");
                }
                sb.append("  <link href=\"").append(f.href).append("\" rel=\"stylesheet\">\n");
            }
        }

        // Custom imports
        for (CustomImport ci : customImports) {
            if (ci.href != null && !ci.href.isEmpty()) {
                sb.append("  ").append(ci.href).append("\n");
            }
        }

        return sb.toString();
    }

    public Set<String> getSelectedPages() { return Collections.unmodifiableSet(selectedPages); }
    public void setSelectedPages(Set<String> pages) { selectedPages.clear(); selectedPages.addAll(pages); persist(); }

    public static List<FontItem> loadFontsFromAssets(Context context) {
        List<FontItem> list = new ArrayList<>();
        try (InputStream is = context.getAssets().open("fonts.json");
             BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                FontItem item = new FontItem();
                item.name = obj.getString("name");
                item.category = obj.optString("category", "");
                item.href = obj.optString("href", "");
                list.add(item);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public static class FontItem {
        public String name, category, href;
        public FontItem() {}
        public FontItem(String name, String category, String href) {
            this.name = name; this.category = category; this.href = href;
        }
    }

    public static class CustomImport {
        public String name;
        public String href;
        public String category;
        public CustomImport() {}
        public CustomImport(String name, String href) { this.name = name; this.href = href; }
        public CustomImport(String name, String href, String category) { this.name = name; this.href = href; this.category = category; }
    }

    // -------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------

    File configFile() {
        File dir = new File(android.os.Environment.getExternalStorageDirectory(), ".dragweb/projects/" + projectId);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "fontimports.json");
    }

    private void load() {
        File f = configFile();
        if (!f.exists()) {
            selectedPages.add("index");
            return;
        }
        String body = FileUtil.readFile(f.getAbsolutePath());
        if (body == null) {
            selectedPages.add("index");
            return;
        }
        try {
            Map<String, Object> map = new Gson().fromJson(body, new TypeToken<Map<String, Object>>(){}.getType());
            if (map != null) {
                if (map.containsKey("enabled")) {
                    List<String> list = (List<String>) map.get("enabled");
                    if (list != null) enabled.addAll(list);
                }
                if (map.containsKey("custom")) {
                    String customJson = new Gson().toJson(map.get("custom"));
                    List<CustomImport> list = new Gson().fromJson(customJson, new TypeToken<List<CustomImport>>(){}.getType());
                    if (list != null) customImports.addAll(list);
                }
                if (map.containsKey("selectedPages")) {
                    List<String> list = (List<String>) map.get("selectedPages");
                    if (list != null) selectedPages.addAll(list);
                }
            }
            if (selectedPages.isEmpty()) {
                selectedPages.add("index");
            }
        } catch (Exception e) { e.printStackTrace(); selectedPages.add("index"); }
    }

    private void persist() {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("enabled", new ArrayList<>(enabled));
        map.put("custom", customImports);
        map.put("selectedPages", new ArrayList<>(selectedPages));
        FileUtil.writeFile(configFile().getAbsolutePath(), new Gson().toJson(map));
    }
}
