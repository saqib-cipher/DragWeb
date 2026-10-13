package glab.dragweb.data;

import glab.dragweb.util.FileUtil;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageManager {

    private Context context;
    private String projectId;
    private List<String> pages = new ArrayList<>();
    private String currentPage = "index";

    // In-memory cache of page layouts to prevent data loss during page switching
    private Map<String, String> pageLayoutCache = new HashMap<>();

    public PageManager(Context context, String projectId) {
        this.context = context;
        this.projectId = projectId;
        loadPageList();
    }

    public List<String> getPages() {
        return new ArrayList<>(pages);
    }

    public String getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(String page) {
        this.currentPage = page;
    }

    public void addPage(String pageName) {
        if (!pages.contains(pageName)) {
            pages.add(pageName);
            savePageList();
            // Create empty layout file for the new page
            savePageLayout(pageName, "[]");

            // Create empty logic file in .dragweb
            try {
                File extDir = new File(FileUtil.getDragWebDir(context), "projects/" + projectId);
                if (!extDir.exists()) extDir.mkdirs();
                File extLogic = new File(extDir, pageName + "_logic.json");
                if (!extLogic.exists()) {
                    FileUtil.writeFile(extLogic.getAbsolutePath(), "{}");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void removePage(String pageName) {
        if ("index".equals(pageName)) return; // Can't remove index
        pages.remove(pageName);
        pageLayoutCache.remove(pageName);
        savePageList();
        // Delete layout files
        File layoutFile = getPageLayoutFile(pageName);
        FileUtil.deleteFile(layoutFile.getAbsolutePath());
        File legacyFile = getLegacyPageFile(pageName);
        FileUtil.deleteFile(legacyFile.getAbsolutePath());
        // Delete logic file from .dragweb
        File extLogicFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/" + pageName + "_logic.json");
        FileUtil.deleteFile(extLogicFile.getAbsolutePath());
        // Remove page from fontimports.json selectedPages if present
        File fontFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/fontimports.json");
        if (FileUtil.exists(fontFile)) {
            try {
                String body = FileUtil.readFile(fontFile.getAbsolutePath());
                if (body != null && !body.isEmpty()) {
                    Map<String, Object> map = new Gson().fromJson(body, new TypeToken<Map<String, Object>>(){}.getType());
                    if (map != null && map.containsKey("selectedPages")) {
                        List<String> list = (List<String>) map.get("selectedPages");
                        if (list != null && list.remove(pageName)) {
                            FileUtil.writeFile(fontFile.getAbsolutePath(), new Gson().toJson(map));
                        }
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        // Delete generated HTML file from assets directory (left by export sync)
        File generatedHtml = new File(FileUtil.getDragWebDir(context),
            "projects/" + projectId + "/assets/" + pageName + ".html");
        FileUtil.deleteFile(generatedHtml.getAbsolutePath());
    }

    public void renamePage(String oldName, String newName) {
        if ("index".equals(oldName)) return;
        int idx = pages.indexOf(oldName);
        if (idx >= 0) {
            pages.set(idx, newName);
            savePageList();
            // Move cached layout
            String cached = pageLayoutCache.remove(oldName);
            if (cached != null) {
                pageLayoutCache.put(newName, cached);
            }
            // Rename layout file
            File oldFile = getPageLayoutFile(oldName);
            File newFile = getPageLayoutFile(newName);
            if (oldFile.exists()) {
                oldFile.renameTo(newFile);
            }
            File oldLegacy = getLegacyPageFile(oldName);
            File newLegacy = getLegacyPageFile(newName);
            if (oldLegacy.exists()) {
                oldLegacy.renameTo(newLegacy);
            }
            // Rename logic file in .dragweb
            File extDir = new File(FileUtil.getDragWebDir(context), "projects/" + projectId);
            File oldExtLogic = new File(extDir, oldName + "_logic.json");
            File newExtLogic = new File(extDir, newName + "_logic.json");
            if (oldExtLogic.exists()) {
                oldExtLogic.renameTo(newExtLogic);
            }
        }
    }

    public String loadPageLayout(String pageName) {
        // Check in-memory cache first for unsaved changes
        if (pageLayoutCache.containsKey(pageName)) {
            String cached = pageLayoutCache.get(pageName);
            if (cached != null && !cached.isEmpty()) {
                return cached;
            }
        }

        // 1. Try external layouts/pageName.json first
        File layoutsFile = getLayoutsPageFile(pageName);
        if (FileUtil.exists(layoutsFile)) {
            String json = FileUtil.readFile(layoutsFile.getAbsolutePath());
            if (json != null && !json.trim().isEmpty()) {
                pageLayoutCache.put(pageName, json);
                FileUtil.writeFile(getPageLayoutFile(pageName).getAbsolutePath(), json);
                return json;
            }
        }

        // 2. Try external pages/pageName.json
        File file = getPageLayoutFile(pageName);
        if (FileUtil.exists(file)) {
            String json = FileUtil.readFile(file.getAbsolutePath());
            if (json != null && !json.trim().isEmpty()) {
                pageLayoutCache.put(pageName, json);
                FileUtil.writeFile(layoutsFile.getAbsolutePath(), json);
                return json;
            }
        }

        // Fallback for "index" page layout to layout.json or projectId.json
        if ("index".equals(pageName)) {
            File layoutFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/layout.json");
            if (FileUtil.exists(layoutFile)) {
                String json = FileUtil.readFile(layoutFile.getAbsolutePath());
                if (json != null && !json.trim().isEmpty() && !"[]".equals(json.trim())) {
                    pageLayoutCache.put(pageName, json);
                    File extFile = getPageLayoutFile(pageName);
                    FileUtil.writeFile(extFile.getAbsolutePath(), json);
                    FileUtil.writeFile(getLayoutsPageFile(pageName).getAbsolutePath(), json);
                    return json;
                }
            }
            File projFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/" + projectId + ".json");
            if (FileUtil.exists(projFile)) {
                String json = FileUtil.readFile(projFile.getAbsolutePath());
                if (json != null && !json.trim().isEmpty() && !"[]".equals(json.trim())) {
                    pageLayoutCache.put(pageName, json);
                    File extFile = getPageLayoutFile(pageName);
                    FileUtil.writeFile(extFile.getAbsolutePath(), json);
                    FileUtil.writeFile(getLayoutsPageFile(pageName).getAbsolutePath(), json);
                    return json;
                }
            }
            File rootProjFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + ".json");
            if (FileUtil.exists(rootProjFile)) {
                String json = FileUtil.readFile(rootProjFile.getAbsolutePath());
                if (json != null && !json.trim().isEmpty() && !"[]".equals(json.trim())) {
                    pageLayoutCache.put(pageName, json);
                    File extFile = getPageLayoutFile(pageName);
                    FileUtil.writeFile(extFile.getAbsolutePath(), json);
                    FileUtil.writeFile(getLayoutsPageFile(pageName).getAbsolutePath(), json);
                    return json;
                }
            }
        }

        // Try legacy projectId_pageName.json in projects dir
        File legacyFile = getLegacyPageFile(pageName);
        if (FileUtil.exists(legacyFile)) {
            String json = FileUtil.readFile(legacyFile.getAbsolutePath());
            if (json != null && !json.isEmpty()) {
                pageLayoutCache.put(pageName, json);
                return json;
            }
        }

        return "[]";
    }

    public void savePageLayout(String pageName, String json) {
        if (json == null || json.isEmpty()) {
            json = "[]";
        }

        // Always update in-memory cache
        pageLayoutCache.put(pageName, json);

        // 1. Save to pages/pageName.json
        File extFile = getPageLayoutFile(pageName);
        File extDir = extFile.getParentFile();
        if (extDir != null && !FileUtil.exists(extDir)) FileUtil.makeDir(extDir.getAbsolutePath());
        FileUtil.writeFile(extFile.getAbsolutePath(), json);

        // 2. Save to layouts/pageName.json
        File layoutsFile = getLayoutsPageFile(pageName);
        File layoutsDir = layoutsFile.getParentFile();
        if (layoutsDir != null && !FileUtil.exists(layoutsDir)) FileUtil.makeDir(layoutsDir.getAbsolutePath());
        FileUtil.writeFile(layoutsFile.getAbsolutePath(), json);

        // 3. Keep layout.json in root synchronized for index page
        if ("index".equals(pageName)) {
            File layoutFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/layout.json");
            FileUtil.writeFile(layoutFile.getAbsolutePath(), json);
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

        // Flat list (Sketchware format): organize into tree by parent and index
        Map<String, Map<String, Object>> idToNode = new HashMap<>();
        Map<String, List<Map<String, Object>>> parentToChildren = new HashMap<>();
        List<Map<String, Object>> rootNodes = new ArrayList<>();

        for (Map<String, Object> node : rawList) {
            String id = node.containsKey("id") && node.get("id") != null ? node.get("id").toString() : null;
            if (id != null) {
                idToNode.put(id, node);
            }
            String parent = node.containsKey("parent") && node.get("parent") != null ? node.get("parent").toString() : "root";
            if ("root".equalsIgnoreCase(parent) || parent.isEmpty()) {
                rootNodes.add(node);
            } else {
                if (!parentToChildren.containsKey(parent)) {
                    parentToChildren.put(parent, new ArrayList<>());
                }
                parentToChildren.get(parent).add(node);
            }
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

    /**
     * Save all cached page layouts to disk.
     * Call this when saving the project to ensure nothing is lost.
     */
    public void saveAllPages() {
        for (Map.Entry<String, String> entry : pageLayoutCache.entrySet()) {
            String pageName = entry.getKey();
            String json = entry.getValue();
            if (json != null && !json.isEmpty()) {
                savePageLayout(pageName, json);
            }
        }
    }

    private File getPageLayoutFile(String pageName) {
        String basePath = FileUtil.getDragWebDir(context).getAbsolutePath()
            + "/projects/" + projectId + "/pages";
        File pagesDir = new File(basePath);
        if (!FileUtil.exists(pagesDir)) FileUtil.makeDir(pagesDir.getAbsolutePath());
        return new File(pagesDir, pageName + ".json");
    }

    private File getLayoutsPageFile(String pageName) {
        String basePath = FileUtil.getDragWebDir(context).getAbsolutePath()
            + "/projects/" + projectId + "/layouts";
        File layoutsDir = new File(basePath);
        if (!FileUtil.exists(layoutsDir)) FileUtil.makeDir(layoutsDir.getAbsolutePath());
        return new File(layoutsDir, pageName + ".json");
    }

    private File getLegacyPageFile(String pageName) {
        File dir = new File(FileUtil.getDragWebDir(context), "projects");
        return new File(dir, projectId + "_" + pageName + ".json");
    }

    private void loadPageList() {
        pages.clear();

        // Try loading from pages metadata file
        String metaPath = FileUtil.getDragWebDir(context).getAbsolutePath()
            + "/projects/" + projectId + "/pages.json";
        File metaFile = new File(metaPath);
        if (FileUtil.exists(metaFile)) {
            try {
                String json = FileUtil.readFile(metaPath);
                List<String> loaded = new Gson().fromJson(json, new TypeToken<List<String>>(){}.getType());
                if (loaded != null && !loaded.isEmpty()) {
                    pages.addAll(loaded);
                    if (!pages.contains("index")) {
                        pages.add(0, "index");
                    }
                    return;
                }
            } catch (Exception e) {
                Log.w("PageManager", "Could not load pages.json: " + e.getMessage());
            }
        }

        // Also scan for existing page files
        String pagesPath = FileUtil.getDragWebDir(context).getAbsolutePath()
            + "/projects/" + projectId + "/pages";
        File pagesDir = new File(pagesPath);
        if (FileUtil.exists(pagesDir) || FileUtil.isDirectory(pagesDir)) {
            File[] pageFiles = FileUtil.listFiles(pagesDir);
            if (pageFiles != null) {
                for (File f : pageFiles) {
                    if (f.getName().endsWith(".json")) {
                        String name = f.getName().replace(".json", "");
                        if (!pages.contains(name)) {
                            pages.add(name);
                        }
                    }
                }
            }
        }

        // Ensure index page exists
        if (!pages.contains("index")) {
            pages.add(0, "index");
        }

        savePageList();
    }

    private void savePageList() {
        try {
            String metaPath = FileUtil.getDragWebDir(context).getAbsolutePath()
                + "/projects/" + projectId + "/pages.json";
            File metaDir = new File(metaPath).getParentFile();
            if (metaDir != null && !metaDir.exists()) metaDir.mkdirs();
            FileUtil.writeFile(metaPath, new Gson().toJson(pages));
        } catch (Exception e) {
            Log.w("PageManager", "Could not save pages.json: " + e.getMessage());
        }
    }
}
