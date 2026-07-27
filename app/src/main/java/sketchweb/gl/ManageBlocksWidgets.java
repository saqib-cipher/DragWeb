package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sketchware-style custom block template engine for DragWeb.
 *
 * <p>Loads block templates from {@code /.dragweb/custom/blocks.json}, manages
 * per-page block instances, and renders them into static HTML/CSS source code
 * that is injected into the generated pages by {@link PageCodeGenerator} and
 * {@link ExportManager}.
 */
public class ManageBlocksWidgets {

    public static final String CATEGORY_HTML = "HTML";
    public static final String CATEGORY_CSS = "CSS";

    private static final String TAG = "ManageBlocksWidgets";
    private static final String LIBRARY_REL_PATH = "/.dragweb/custom/blocks.json";

    private final Context context;
    private final List<CustomBlockDef> definitions = new ArrayList<>();
    private final List<CustomBlockInstance> instances = new ArrayList<>();

    public ManageBlocksWidgets(Context context) {
        this.context = context;
        loadLibrary();
    }

    // -------------------------------------------------------------------------
    // Definitions (palette / library)
    // -------------------------------------------------------------------------

    public List<CustomBlockDef> getDefinitions() {
        loadLibrary();
        return new ArrayList<>(definitions);
    }

    public CustomBlockDef findDefinition(String id) {
        if (id == null) return null;
        loadLibrary();
        for (CustomBlockDef def : definitions) {
            if (id.equals(def.id)) return def;
        }
        return null;
    }

    public List<CustomBlockDef> getDefinitionsForCategory(String category) {
        List<CustomBlockDef> out = new ArrayList<>();
        if (category == null) return out;
        loadLibrary();
        for (CustomBlockDef def : definitions) {
            if (category.equalsIgnoreCase(def.category)) out.add(def);
        }
        return out;
    }

    public List<String> getBlockCategories() {
        Set<String> unique = new LinkedHashSet<>();
        // First read from assets blocks.json
        try (InputStream is = context.getAssets().open("blocks.json")) {
            byte[] buf = new byte[is.available()];
            int read = is.read(buf);
            if (read > 0) {
                String json = new String(buf, 0, read, "UTF-8");
                JsonElement root = JsonParser.parseString(json);
                if (root.isJsonArray()) {
                    JsonArray array = root.getAsJsonArray();
                    for (JsonElement el : array) {
                        if (el.isJsonObject()) {
                            JsonObject obj = el.getAsJsonObject();
                            if (obj.has("category")) {
                                String cat = obj.get("category").getAsString().trim().toLowerCase();
                                if (!cat.isEmpty()) unique.add(cat);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading categories from assets blocks.json: " + e.getMessage());
        }

        // Then read from custom definitions in library
        loadLibrary();
        for (CustomBlockDef def : definitions) {
            if (def.category != null && !def.category.isEmpty()) {
                unique.add(def.category.trim().toLowerCase());
            }
        }
        return new ArrayList<>(unique);
    }

    public void addDefinition(CustomBlockDef def) {
        if (def == null || def.id == null || def.id.isEmpty()) return;
        loadLibrary();
        def.isCustom = true;
        // Replace by id if present.
        for (int i = 0; i < definitions.size(); i++) {
            if (def.id.equals(definitions.get(i).id)) {
                definitions.set(i, def);
                saveLibrary();
                return;
            }
        }
        definitions.add(def);
        saveLibrary();
    }

    public void addDefinitionAfter(String afterId, CustomBlockDef def) {
        if (def == null || def.id == null || def.id.isEmpty()) return;
        loadLibrary();
        def.isCustom = true;
        // If it already exists, replace/remove it first.
        removeDefinition(def.id);
        
        int insertIndex = -1;
        for (int i = 0; i < definitions.size(); i++) {
            if (afterId.equals(definitions.get(i).id)) {
                insertIndex = i + 1;
                break;
            }
        }
        if (insertIndex >= 0 && insertIndex <= definitions.size()) {
            definitions.add(insertIndex, def);
        } else {
            definitions.add(def);
        }
        saveLibrary();
    }

    public void removeDefinition(String id) {
        if (id == null) return;
        loadLibrary();
        for (int i = 0; i < definitions.size(); i++) {
            if (id.equals(definitions.get(i).id)) {
                definitions.remove(i);
                saveLibrary();
                return;
            }
        }
    }

    public boolean importCustomBlocks(String json) {
        loadLibrary();
        List<CustomBlockDef> parsed = parseLibraryJson(json);
        if (parsed.isEmpty()) return false;
        for (CustomBlockDef def : parsed) {
            if (def == null || def.id == null || def.id.isEmpty()) continue;
            def.isCustom = true;
            boolean replaced = false;
            for (int i = 0; i < definitions.size(); i++) {
                if (def.id.equals(definitions.get(i).id)) {
                    definitions.set(i, def);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                definitions.add(def);
            }
        }
        saveLibrary();
        return true;
    }

    public void loadLibrary() {
        definitions.clear();
        File file = libraryFile();
        if (file != null && file.exists()) {
            String customJson = readLibraryFile();
            List<CustomBlockDef> customParsed = parseLibraryJson(customJson);
            if (!customParsed.isEmpty()) {
                for (CustomBlockDef def : customParsed) {
                    if (def != null) {
                        def.isCustom = true;
                        definitions.add(def);
                    }
                }
                return;
            }
        }

        // Fallback: seed with defaults & assets
        String bundled = readBundledLibrary();
        List<CustomBlockDef> standardParsed = parseLibraryJson(bundled);
        if (!standardParsed.isEmpty()) {
            for (CustomBlockDef def : standardParsed) {
                if (def != null) {
                    def.isCustom = true;
                    definitions.add(def);
                }
            }
        }

        for (CustomBlockDef def : definitions) {
            def.isCustom = true;
        }

        saveLibrary();
    }

    private String stripMarkdownCodeBlocks(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            if (firstLineBreak != -1) {
                trimmed = trimmed.substring(firstLineBreak + 1);
            } else {
                trimmed = trimmed.substring(3);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }
        return trimmed;
    }

    private CustomBlockDef parseSingleBlockDef(JsonObject obj, Gson gson) {
        CustomBlockDef def = gson.fromJson(obj, CustomBlockDef.class);
        if (def == null) return null;
        
        // Map spec -> display (or legacy label -> display) if display is missing
        if (def.display == null || def.display.isEmpty()) {
            if (obj.has("spec")) {
                def.display = obj.get("spec").getAsString();
            } else if (obj.has("label")) {
                def.display = obj.get("label").getAsString();
            }
        }
        // Map code -> template if template is missing
        if ((def.template == null || def.template.isEmpty()) && obj.has("code")) {
            def.template = obj.get("code").getAsString();
        }
        
        if (def.id == null || def.id.isEmpty()) {
            def.id = "block_" + System.currentTimeMillis();
        }
        if (def.template == null) {
            def.template = "";
        }
        if (def.category == null || def.category.isEmpty()) {
            def.category = CATEGORY_HTML;
        }
        return def;
    }

    private List<CustomBlockDef> parseLibraryJson(String json) {
        List<CustomBlockDef> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            String cleanedJson = stripMarkdownCodeBlocks(json);
            JsonElement root = JsonParser.parseString(cleanedJson);
            JsonArray array = null;
            Gson gson = new Gson();
            
            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("blocks") && obj.get("blocks").isJsonArray()) {
                    array = obj.getAsJsonArray("blocks");
                } else if (obj.has("id") && (obj.has("template") || obj.has("code"))) {
                    CustomBlockDef def = parseSingleBlockDef(obj, gson);
                    if (def != null) {
                        out.add(def);
                        return out;
                    }
                } else {
                    // Try parsing as a map of block definitions:
                    // e.g. { "block_id": { "display": "...", "template": "..." } }
                    boolean parsedAsMap = false;
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        if (entry.getValue().isJsonObject()) {
                            JsonObject valObj = entry.getValue().getAsJsonObject();
                            if (valObj.has("template") || valObj.has("display") || valObj.has("code") || valObj.has("label")) {
                                CustomBlockDef def = parseSingleBlockDef(valObj, gson);
                                if (def != null) {
                                    if (def.id == null || def.id.isEmpty() || def.id.startsWith("block_")) {
                                        def.id = entry.getKey();
                                    }
                                    out.add(def);
                                    parsedAsMap = true;
                                }
                            }
                        }
                    }
                    
                    if (!parsedAsMap) {
                        // Treat obj as a single CustomBlockDef (even if ID is missing)
                        CustomBlockDef def = parseSingleBlockDef(obj, gson);
                        if (def != null) {
                            out.add(def);
                            return out;
                        }
                    }
                }
            }
            
            if (array != null) {
                for (JsonElement el : array) {
                    try {
                        if (el.isJsonObject()) {
                            CustomBlockDef def = parseSingleBlockDef(el.getAsJsonObject(), gson);
                            if (def != null) {
                                out.add(def);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse library JSON: " + e.getMessage());
        }
        return out;
    }

    private String readBundledLibrary() {
        File file = libraryFile();
        if (file != null && file.exists()) {
            return FileUtil.readFile(file.getAbsolutePath());
        }
        return null;
    }

    public void saveLibrary() {
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(definitions);
        File file = libraryFile();
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileUtil.writeFile(file.getAbsolutePath(), json);
        BlockDef.clearCache();
    }

    private String readLibraryFile() {
        File file = libraryFile();
        if (file == null || !file.exists()) return null;
        try {
            return FileUtil.readFile(file.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }

    private File libraryFile() {
        return new File(CustomStorageUtil.getCustomDir(context), "blocks.json");
    }

    // -------------------------------------------------------------------------
    // Per-page instances
    // -------------------------------------------------------------------------

    public List<CustomBlockInstance> getInstances() {
        return instances;
    }

    public void addInstance(CustomBlockInstance instance) {
        if (instance != null) instances.add(instance);
    }

    public void removeInstance(int index) {
        if (index >= 0 && index < instances.size()) instances.remove(index);
    }

    public void duplicateInstance(int index) {
        if (index < 0 || index >= instances.size()) return;
        CustomBlockInstance orig = instances.get(index);
        CustomBlockInstance copy = new CustomBlockInstance();
        copy.defId = orig.defId;
        copy.values = orig.values != null ? new ArrayList<>(orig.values) : new ArrayList<>();
        instances.add(copy);
    }

    public void moveInstance(int from, int to) {
        if (from < 0 || from >= instances.size() || to < 0 || to >= instances.size()) return;
        CustomBlockInstance b = instances.remove(from);
        instances.add(to, b);
    }

    public void clearInstances() {
        instances.clear();
    }

    public String instancesToJson() {
        return new Gson().toJson(instances);
    }

    public void instancesFromJson(String json) {
        instances.clear();
        if (json == null || json.trim().isEmpty()) return;
        try {
            List<CustomBlockInstance> loaded = new Gson().fromJson(
                json,
                new TypeToken<List<CustomBlockInstance>>(){}.getType()
            );
            if (loaded != null) instances.addAll(loaded);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse custom block instances: " + e.getMessage());
        }
    }

    public void loadInstancesForPage(String projectId, String pageName) {
        File file = pageInstanceFile(projectId, pageName);
        if (file == null || !file.exists()) {
            instances.clear();
            return;
        }
        instancesFromJson(FileUtil.readFile(file.getAbsolutePath()));
    }

    public void saveInstancesForPage(String projectId, String pageName) {
        File file = pageInstanceFile(projectId, pageName);
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileUtil.writeFile(file.getAbsolutePath(), instancesToJson());
    }

    private File pageInstanceFile(String projectId, String pageName) {
        if (projectId == null) projectId = "";
        if (pageName == null || pageName.isEmpty()) pageName = "index";
        File dir = new File(context.getFilesDir(), "projects");
        return new File(dir, projectId + "_" + pageName + ".cblocks");
    }

    // -------------------------------------------------------------------------
    // Template engine + token detection
    // -------------------------------------------------------------------------

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "%(?:(\\d+)\\$([sd])|([sd])|m\\.(id|class|tag|file|section))"
    );

    public List<Token> detectTokens(String template) {
        List<Token> out = new ArrayList<>();
        if (template == null) return out;
        Matcher m = TOKEN_PATTERN.matcher(template);
        int positional = 0;
        while (m.find()) {
            Token t = new Token();
            t.literal = m.group(0);
            if (m.group(1) != null) {
                t.position = Integer.parseInt(m.group(1));
                t.type = "s".equals(m.group(2)) ? "string" : "number";
            } else if (m.group(3) != null) {
                positional++;
                t.position = positional;
                t.type = "s".equals(m.group(3)) ? "string" : "number";
            } else {
                positional++;
                t.position = positional;
                t.type = "m." + m.group(4);
            }
            out.add(t);
        }
        return out;
    }

    public String renderInstance(CustomBlockInstance instance) {
        if (instance == null) return "";
        CustomBlockDef def = findDefinition(instance.defId);
        if (def == null || def.template == null) return "";
        LogicBlockManager engine = new LogicBlockManager(context);
        List<String> values = instance.values != null ? instance.values : new ArrayList<>();
        return engine.applyTemplate(def.template, values);
    }

    public String renderAllHtml() {
        StringBuilder sb = new StringBuilder();
        for (CustomBlockInstance inst : instances) {
            CustomBlockDef def = findDefinition(inst.defId);
            if (def == null) continue;
            if (!CATEGORY_HTML.equalsIgnoreCase(def.category)) continue;
            String rendered = renderInstance(inst);
            if (rendered.isEmpty()) continue;
            sb.append(rendered).append("\n");
        }
        return sb.toString();
    }

    public String renderAllCss() {
        StringBuilder sb = new StringBuilder();
        for (CustomBlockInstance inst : instances) {
            CustomBlockDef def = findDefinition(inst.defId);
            if (def == null) continue;
            if (!CATEGORY_CSS.equalsIgnoreCase(def.category)) continue;
            String rendered = renderInstance(inst);
            if (rendered.isEmpty()) continue;
            sb.append(rendered).append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public Set<String> collectIds(List<Map<String, Object>> tree) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (tree == null) return out;
        for (Map<String, Object> node : tree) {
            Object fnObj = node.get("function");
            if (fnObj instanceof Map) {
                Object idObj = ((Map<String, Object>) fnObj).get("id");
                if (idObj != null) {
                    String id = idObj.toString().trim();
                    if (!id.isEmpty()) out.add(id);
                }
            }
            Object children = node.get("children");
            if (children instanceof List) {
                out.addAll(collectIds((List<Map<String, Object>>) children));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public Set<String> collectClasses(List<Map<String, Object>> tree) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (tree == null) return out;
        for (Map<String, Object> node : tree) {
            Object fnObj = node.get("function");
            if (fnObj instanceof Map) {
                Object classObj = ((Map<String, Object>) fnObj).get("class");
                if (classObj != null) {
                    for (String cls : classObj.toString().trim().split("\\s+")) {
                        if (!cls.isEmpty()) out.add(cls);
                    }
                }
            }
            Object children = node.get("children");
            if (children instanceof List) {
                out.addAll(collectClasses((List<Map<String, Object>>) children));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public Set<String> collectTags(List<Map<String, Object>> tree) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (tree == null) return out;
        for (Map<String, Object> node : tree) {
            Object tagObj = node.get("tag");
            if (tagObj != null) {
                String tag = tagObj.toString().trim();
                if (!tag.isEmpty()) out.add(tag);
            }
            Object children = node.get("children");
            if (children instanceof List) {
                out.addAll(collectTags((List<Map<String, Object>>) children));
            }
        }
        return out;
    }

    public List<String> collectFiles(PageManager pageManager) {
        List<String> out = new ArrayList<>();
        if (pageManager == null) return out;
        for (String pageName : pageManager.getPages()) {
            out.add(pageName + ".html");
        }
        return out;
    }

    public List<String> collectSections(List<Map<String, Object>> tree) {
        return new ArrayList<>(collectIds(tree));
    }

    public List<String> suggestionsForToken(Token token,
                                            List<Map<String, Object>> tree,
                                            PageManager pageManager) {
        if (token == null) return new ArrayList<>();
        switch (token.type) {
            case "m.id":      return new ArrayList<>(collectIds(tree));
            case "m.class":   return new ArrayList<>(collectClasses(tree));
            case "m.tag":     return new ArrayList<>(collectTags(tree));
            case "m.file":    return collectFiles(pageManager);
            case "m.section": return collectSections(tree);
            default:          return new ArrayList<>();
        }
    }



    public static class CustomBlockDef {
        public String id;
        public String display;
        public String template;
        public String category;
        public boolean isCustom = false;
    }

    public static class CustomBlockInstance {
        public String defId;
        public List<String> values = new ArrayList<>();
    }

    public static class Token {
        public String literal;
        public int position;
        public String type;
    }
}
