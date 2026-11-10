package glab.dragweb.data;

import glab.dragweb.util.FileUtil;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PageManager {

    public static class PageConfig {
        public String name = "index";
        public boolean useGlobalCss = true;
        public String cssFile = "css/style.css";
        public boolean useGlobalJs = true;
        public String jsFile = "js/script.js";

        public PageConfig() {
            this("index");
        }

        public PageConfig(String name) {
            this.name = normalizePageName(name);
            this.useGlobalCss = true;
            this.cssFile = "css/style.css";
            this.useGlobalJs = true;
            this.jsFile = "js/script.js";
        }
    }

    private Context context;
    private String projectId;
    private List<String> pages = new ArrayList<>();
    private Map<String, PageConfig> pageConfigs = new LinkedHashMap<>();
    private String currentPage = "index";

    // In-memory cache of page layouts: pageName -> layout JSON
    private Map<String, String> pageLayoutCache = new HashMap<>();

    public PageManager(Context context, String projectId) {
        this.context = context;
        this.projectId = projectId;
        loadPageList();
        loadAllLayoutsFromUnifiedFile();
    }

    public List<String> getPages() {
        return new ArrayList<>(pages);
    }

    public List<PageConfig> getPageConfigs() {
        List<PageConfig> list = new ArrayList<>();
        for (String p : pages) {
            String clean = normalizePageName(p);
            PageConfig cfg = pageConfigs.get(clean);
            if (cfg == null) {
                cfg = new PageConfig(clean);
                pageConfigs.put(clean, cfg);
            }
            list.add(cfg);
        }
        return list;
    }

    public PageConfig getPageConfig(String pageName) {
        String clean = normalizePageName(pageName);
        PageConfig cfg = pageConfigs.get(clean);
        if (cfg == null) {
            cfg = new PageConfig(clean);
            pageConfigs.put(clean, cfg);
        }
        return cfg;
    }

    public void savePageConfig(PageConfig cfg) {
        if (cfg == null || cfg.name == null) return;
        String clean = normalizePageName(cfg.name);
        cfg.name = clean;
        pageConfigs.put(clean, cfg);
        if (!pages.contains(clean)) {
            pages.add(clean);
        }
        savePageList();
    }

    public static String normalizePageName(String pageName) {
        if (pageName == null || pageName.trim().isEmpty()) return "index";
        String clean = pageName.trim();
        if (clean.endsWith(".html")) {
            clean = clean.substring(0, clean.length() - 5);
        } else if (clean.endsWith(".htm")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        return clean.isEmpty() ? "index" : clean;
    }

    public String getCurrentPage() {
        return currentPage != null ? currentPage : "index";
    }

    public void setCurrentPage(String page) {
        this.currentPage = normalizePageName(page);
    }

    public void invalidateCache(String pageName) {
        if (pageName != null) {
            String norm = normalizePageName(pageName);
            pageLayoutCache.remove(norm);
            pageLayoutCache.remove(pageName);
        } else {
            pageLayoutCache.clear();
        }
    }

    public File getUnifiedLayoutFile() {
        File projDir = new File(FileUtil.getDragWebDir(context), "projects/" + projectId);
        if (!projDir.exists()) projDir.mkdirs();
        return new File(projDir, "layout");
    }

    public void addPage(String pageName) {
        String clean = normalizePageName(pageName);
        if (!pages.contains(clean)) {
            pages.add(clean);
            pageConfigs.put(clean, new PageConfig(clean));
            savePageList();
            savePageLayout(clean, "[]");
        }
    }

    public void removePage(String pageName) {
        String clean = normalizePageName(pageName);
        if ("index".equals(clean)) return; // Can't remove index
        pages.remove(clean);
        pageConfigs.remove(clean);
        pageLayoutCache.remove(clean);
        savePageList();
        saveAllLayoutsToUnifiedFile();

        // Remove from logic
        DesignDataManager.removePage(context, projectId, clean);

        // Remove page from fontimports.json selectedPages if present
        File fontFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/fontimports.json");
        if (FileUtil.exists(fontFile)) {
            try {
                String body = FileUtil.readFile(fontFile.getAbsolutePath());
                if (body != null && !body.isEmpty()) {
                    Map<String, Object> map = new Gson().fromJson(body, new TypeToken<Map<String, Object>>(){}.getType());
                    if (map != null && map.containsKey("selectedPages")) {
                        List<String> list = (List<String>) map.get("selectedPages");
                        if (list != null && list.remove(clean)) {
                            FileUtil.writeFile(fontFile.getAbsolutePath(), new Gson().toJson(map));
                        }
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        // Delete generated HTML file from assets directory
        File generatedHtml = new File(FileUtil.getDragWebDir(context),
            "projects/" + projectId + "/assets/" + clean + ".html");
        FileUtil.deleteFile(generatedHtml.getAbsolutePath());
    }

    public void renamePage(String oldName, String newName) {
        String oldClean = normalizePageName(oldName);
        String newClean = normalizePageName(newName);
        if ("index".equals(oldClean)) return;
        int idx = pages.indexOf(oldClean);
        if (idx >= 0) {
            pages.set(idx, newClean);
            PageConfig cfg = pageConfigs.remove(oldClean);
            if (cfg != null) {
                cfg.name = newClean;
                pageConfigs.put(newClean, cfg);
            } else {
                pageConfigs.put(newClean, new PageConfig(newClean));
            }
            String cached = pageLayoutCache.remove(oldClean);
            if (cached != null) {
                pageLayoutCache.put(newClean, cached);
            }
            savePageList();
            saveAllLayoutsToUnifiedFile();
            DesignDataManager.renamePage(context, projectId, oldClean, newClean);
        }
    }

    public String loadPageLayout(String pageName) {
        pageName = normalizePageName(pageName);
        if (pageLayoutCache.containsKey(pageName)) {
            String cached = pageLayoutCache.get(pageName);
            if (cached != null && !cached.trim().isEmpty()) {
                return cached;
            }
        }

        // Load unified layout file if not yet loaded
        loadAllLayoutsFromUnifiedFile();

        if (pageLayoutCache.containsKey(pageName)) {
            String cached = pageLayoutCache.get(pageName);
            if (cached != null && !cached.trim().isEmpty()) {
                return cached;
            }
        }

        return "[]";
    }

    public void savePageLayout(String pageName, String json) {
        pageName = normalizePageName(pageName);
        if (json == null || json.isEmpty()) {
            json = "[]";
        }

        pageLayoutCache.put(pageName, json);
        if (!pages.contains(pageName)) {
            pages.add(pageName);
            if (!pageConfigs.containsKey(pageName)) {
                pageConfigs.put(pageName, new PageConfig(pageName));
            }
            savePageList();
        }
        saveAllLayoutsToUnifiedFile();
    }

    public void loadAllLayoutsFromUnifiedFile() {
        File unified = getUnifiedLayoutFile();
        if (FileUtil.exists(unified)) {
            String content = FileUtil.readFile(unified.getAbsolutePath());
            parseUnifiedLayoutContent(content);
        } else {
            migrateLegacyLayoutFiles();
        }
    }

    public void parseUnifiedLayoutContent(String content) {
        if (content == null || content.trim().isEmpty()) return;
        String[] lines = content.split("\r?\n");
        String currentSection = null;
        StringBuilder sectionSb = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("@")) {
                if (currentSection != null) {
                    processLayoutSection(currentSection, sectionSb.toString());
                    sectionSb.setLength(0);
                }
                currentSection = trimmed.substring(1).trim();
            } else if (currentSection != null && !trimmed.isEmpty()) {
                sectionSb.append(trimmed).append("\n");
            }
        }
        if (currentSection != null) {
            processLayoutSection(currentSection, sectionSb.toString());
        }
    }

    private void processLayoutSection(String sectionHeader, String sectionContent) {
        String cleanPage = normalizePageName(sectionHeader);
        List<Map<String, Object>> tree = parseLayoutTree(sectionContent);
        String json = new Gson().toJson(tree);
        pageLayoutCache.put(cleanPage, json);
        if (!pages.contains(cleanPage)) {
            pages.add(cleanPage);
            if (!pageConfigs.containsKey(cleanPage)) {
                pageConfigs.put(cleanPage, new PageConfig(cleanPage));
            }
        }
    }

    public void saveAllLayoutsToUnifiedFile() {
        StringBuilder sb = new StringBuilder();
        List<String> allPages = new ArrayList<>(pages);
        for (String p : pageLayoutCache.keySet()) {
            String clean = normalizePageName(p);
            if (!allPages.contains(clean)) {
                allPages.add(clean);
            }
        }
        if (allPages.isEmpty()) {
            allPages.add("index");
        }

        for (String p : allPages) {
            String clean = normalizePageName(p);
            String json = pageLayoutCache.get(clean);
            if (json == null || json.trim().isEmpty()) {
                json = "[]";
            }
            sb.append("@").append(clean).append(".html\n");
            List<Map<String, Object>> tree = parseLayoutTree(json);
            List<Map<String, Object>> flatList = flattenLayoutTree(tree);
            if (flatList != null && !flatList.isEmpty()) {
                for (Map<String, Object> node : flatList) {
                    sb.append(new Gson().toJson(node)).append("\n");
                }
            }
        }

        File unified = getUnifiedLayoutFile();
        FileUtil.writeFile(unified.getAbsolutePath(), sb.toString());
        cleanupLegacyFiles();
    }

    private void migrateLegacyLayoutFiles() {
        File projDir = new File(FileUtil.getDragWebDir(context), "projects/" + projectId);
        if (!projDir.exists()) return;

        // Check pages directory
        File pagesDir = new File(projDir, "pages");
        if (pagesDir.exists() && pagesDir.isDirectory()) {
            File[] files = pagesDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().endsWith(".json")) {
                        String pageName = normalizePageName(f.getName().replace(".json", ""));
                        String json = FileUtil.readFile(f.getAbsolutePath());
                        if (json != null && !json.trim().isEmpty()) {
                            pageLayoutCache.put(pageName, json);
                            if (!pages.contains(pageName)) {
                                pages.add(pageName);
                                pageConfigs.put(pageName, new PageConfig(pageName));
                            }
                        }
                    }
                }
            }
        }

        // Check layout.json fallback for index
        if (!pageLayoutCache.containsKey("index")) {
            File layoutJson = new File(projDir, "layout.json");
            if (layoutJson.exists()) {
                String json = FileUtil.readFile(layoutJson.getAbsolutePath());
                if (json != null && !json.trim().isEmpty() && !"[]".equals(json.trim())) {
                    pageLayoutCache.put("index", json);
                }
            }
        }

        if (!pageLayoutCache.isEmpty()) {
            saveAllLayoutsToUnifiedFile();
        }
    }

    public void cleanupLegacyFiles() {
        try {
            File projDir = new File(FileUtil.getDragWebDir(context), "projects/" + projectId);
            cleanupLegacyFiles(projDir);
        } catch (Exception ignored) {}
    }

    public static void cleanupLegacyFiles(File projDir) {
        if (projDir == null || !projDir.exists()) return;
        try {
            // Delete legacy layout.json
            File legacyLayout = new File(projDir, "layout.json");
            if (legacyLayout.exists()) legacyLayout.delete();

            // Delete useless root index.html
            File uselessIndexHtml = new File(projDir, "index.html");
            if (uselessIndexHtml.exists()) uselessIndexHtml.delete();

            // Delete legacy pages/ directory
            File pagesDir = new File(projDir, "pages");
            if (pagesDir.exists()) {
                FileUtil.deleteFile(pagesDir.getAbsolutePath());
            }

            // Delete legacy layouts/ directory
            File layoutsDir = new File(projDir, "layouts");
            if (layoutsDir.exists()) {
                FileUtil.deleteFile(layoutsDir.getAbsolutePath());
            }

            // Delete legacy projectId.json in projects dir
            String projName = projDir.getName();
            File legacyProj = new File(projDir.getParentFile(), projName + ".json");
            if (legacyProj.exists()) legacyProj.delete();
        } catch (Exception ignored) {}
    }

    /**
     * Recursively flattens a hierarchical layout tree into flat lines with "parent" and "index"
     * attributes, matching the Sketchware flat-line format.
     */
    public static List<Map<String, Object>> flattenLayoutTree(List<Map<String, Object>> tree) {
        List<Map<String, Object>> flatList = new ArrayList<>();
        if (tree == null || tree.isEmpty()) return flatList;
        flattenNodes(tree, "root", flatList);
        return flatList;
    }

    private static void flattenNodes(List<Map<String, Object>> nodes, String parentId, List<Map<String, Object>> flatList) {
        if (nodes == null) return;
        for (int i = 0; i < nodes.size(); i++) {
            Map<String, Object> orig = nodes.get(i);
            if (orig == null) continue;
            Map<String, Object> copy = new LinkedHashMap<>(orig);
            Object childrenObj = copy.remove("children");

            String widgetId = null;
            if (copy.get("id") != null && !copy.get("id").toString().trim().isEmpty()) {
                widgetId = copy.get("id").toString().trim();
            } else if (copy.get("function") instanceof Map) {
                Map<?, ?> fn = (Map<?, ?>) copy.get("function");
                if (fn.get("id") != null && !fn.get("id").toString().trim().isEmpty()) {
                    widgetId = fn.get("id").toString().trim();
                }
            }
            if (widgetId == null || widgetId.isEmpty()) {
                String tag = copy.get("tag") != null ? copy.get("tag").toString() : "widget";
                widgetId = tag + "_" + (flatList.size() + 1);
            }
            copy.put("id", widgetId);
            copy.put("parent", parentId);
            copy.put("index", i);

            // Ensure text, class, convert are synced at root level of the node
            if (copy.get("function") instanceof Map) {
                Map<?, ?> fn = (Map<?, ?>) copy.get("function");
                if (!copy.containsKey("text") && fn.containsKey("text")) {
                    copy.put("text", fn.get("text"));
                }
                if (!copy.containsKey("class") && fn.containsKey("class")) {
                    copy.put("class", fn.get("class"));
                }
            }

            flatList.add(copy);

            if (childrenObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) childrenObj;
                flattenNodes(children, widgetId, flatList);
            }
        }
    }

    /**
     * Robust parser that parses both JSON array format and Sketchware format
     * (flat list with parent attributes or newline-delimited JSON objects).
     */
    public static List<Map<String, Object>> parseLayoutTree(String json) {
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            return new ArrayList<>();
        }
        String trimmed = json.trim();
        List<Map<String, Object>> rawList = null;

        if (trimmed.startsWith("[")) {
            try {
                rawList = new Gson().fromJson(trimmed, new TypeToken<List<Map<String, Object>>>(){}.getType());
            } catch (Exception ignored) {}
        }

        if (rawList == null || rawList.isEmpty()) {
            rawList = new ArrayList<>();
            String[] lines = trimmed.split("\r?\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("@") || line.startsWith("#")) continue;
                if (line.startsWith("{") && line.endsWith("}")) {
                    try {
                        Map<String, Object> obj = new Gson().fromJson(line, new TypeToken<Map<String, Object>>(){}.getType());
                        if (obj != null) rawList.add(obj);
                    } catch (Exception ignored) {}
                }
            }
        }

        if (rawList == null || rawList.isEmpty()) return new ArrayList<>();

        // Check if already a nested tree with children
        boolean hasNestedChildren = false;
        for (Map<String, Object> node : rawList) {
            if (node.containsKey("children") && node.get("children") instanceof List && !((List<?>) node.get("children")).isEmpty()) {
                hasNestedChildren = true;
                break;
            }
        }
        if (hasNestedChildren) {
            return rawList;
        }

        // Flat list: organize into tree by parent and index
        Map<String, Map<String, Object>> idToNode = new HashMap<>();
        Map<String, List<Map<String, Object>>> parentToChildren = new HashMap<>();
        List<Map<String, Object>> rootNodes = new ArrayList<>();

        int autoIdx = 1;
        for (Map<String, Object> node : rawList) {
            String id = null;
            if (node.containsKey("id") && node.get("id") != null && !node.get("id").toString().trim().isEmpty()) {
                id = node.get("id").toString().trim();
            } else if (node.get("function") instanceof Map) {
                Map<?, ?> fn = (Map<?, ?>) node.get("function");
                if (fn.containsKey("id") && fn.get("id") != null && !fn.get("id").toString().trim().isEmpty()) {
                    id = fn.get("id").toString().trim();
                    node.put("id", id);
                }
            }
            if (id == null || id.isEmpty()) {
                String tag = node.get("tag") != null ? node.get("tag").toString() : "widget";
                id = tag + "_" + (autoIdx++);
                node.put("id", id);
            }
            idToNode.put(id, node);

            String parent = node.containsKey("parent") && node.get("parent") != null ? node.get("parent").toString().trim() : "root";
            if ("root".equalsIgnoreCase(parent) || parent.isEmpty()) {
                rootNodes.add(node);
            } else {
                if (!parentToChildren.containsKey(parent)) {
                    parentToChildren.put(parent, new ArrayList<>());
                }
                parentToChildren.get(parent).add(node);
            }
        }

        // Sort siblings by index if present
        java.util.Comparator<Map<String, Object>> indexComparator = (a, b) -> {
            int ia = getIndexValue(a);
            int ib = getIndexValue(b);
            return Integer.compare(ia, ib);
        };

        rootNodes.sort(indexComparator);
        for (List<Map<String, Object>> children : parentToChildren.values()) {
            children.sort(indexComparator);
        }

        for (Map.Entry<String, List<Map<String, Object>>> entry : parentToChildren.entrySet()) {
            Map<String, Object> parentNode = idToNode.get(entry.getKey());
            if (parentNode != null) {
                parentNode.put("children", entry.getValue());
            } else {
                rootNodes.addAll(entry.getValue());
            }
        }

        return rootNodes.isEmpty() ? rawList : rootNodes;
    }

    private static int getIndexValue(Map<String, Object> node) {
        if (node == null) return 0;
        Object idx = node.get("index");
        if (idx instanceof Number) {
            return ((Number) idx).intValue();
        } else if (idx != null) {
            try {
                return Integer.parseInt(idx.toString().trim());
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public void saveAllPages() {
        saveAllLayoutsToUnifiedFile();
    }

    private void loadPageList() {
        pages.clear();
        pageConfigs.clear();

        File metaFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/pages.json");
        if (FileUtil.exists(metaFile)) {
            try {
                String json = FileUtil.readFile(metaFile.getAbsolutePath());
                if (json != null && !json.trim().isEmpty()) {
                    String trimmed = json.trim();
                    if (trimmed.startsWith("[")) {
                        // Check if list of PageConfig objects
                        try {
                            List<PageConfig> loadedConfigs = new Gson().fromJson(trimmed, new TypeToken<List<PageConfig>>(){}.getType());
                            if (loadedConfigs != null && !loadedConfigs.isEmpty() && loadedConfigs.get(0) != null && loadedConfigs.get(0).name != null) {
                                for (PageConfig cfg : loadedConfigs) {
                                    if (cfg != null && cfg.name != null && !cfg.name.trim().isEmpty()) {
                                        String clean = normalizePageName(cfg.name);
                                        cfg.name = clean;
                                        if (!pages.contains(clean)) {
                                            pages.add(clean);
                                        }
                                        pageConfigs.put(clean, cfg);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}

                        // If not parsed as PageConfig, try as List<String> (legacy format)
                        if (pages.isEmpty()) {
                            try {
                                List<String> stringList = new Gson().fromJson(trimmed, new TypeToken<List<String>>(){}.getType());
                                if (stringList != null) {
                                    for (String s : stringList) {
                                        String clean = normalizePageName(s);
                                        if (!pages.contains(clean)) {
                                            pages.add(clean);
                                            pageConfigs.put(clean, new PageConfig(clean));
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception e) {
                Log.w("PageManager", "Could not load pages.json: " + e.getMessage());
            }
        }

        // Ensure index page exists
        if (!pages.contains("index")) {
            pages.add(0, "index");
        }
        if (!pageConfigs.containsKey("index")) {
            pageConfigs.put("index", new PageConfig("index"));
        }

        savePageList();
    }

    public void savePageList() {
        try {
            File metaFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/pages.json");
            File metaDir = metaFile.getParentFile();
            if (metaDir != null && !metaDir.exists()) metaDir.mkdirs();

            List<PageConfig> list = new ArrayList<>();
            for (String p : pages) {
                String clean = normalizePageName(p);
                PageConfig cfg = pageConfigs.get(clean);
                if (cfg == null) {
                    cfg = new PageConfig(clean);
                    pageConfigs.put(clean, cfg);
                }
                list.add(cfg);
            }
            FileUtil.writeFile(metaFile.getAbsolutePath(), new GsonBuilder().setPrettyPrinting().create().toJson(list));
        } catch (Exception e) {
            Log.w("PageManager", "Could not save pages.json: " + e.getMessage());
        }
    }
}
