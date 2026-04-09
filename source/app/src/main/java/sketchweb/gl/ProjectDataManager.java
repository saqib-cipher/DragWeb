package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectDataManager {

    private static final String TAG = "ProjectDataManager";
    private Context context;
    private Gson gson = new GsonBuilder().create();

    public ProjectDataManager(Context context) {
        this.context = context;
    }

    public void saveProject(View screen, String projectId) {
        List<Map<String, Object>> widgetTree = serializeViewTree(screen);
        String json = gson.toJson(widgetTree);

        // Save to internal storage only; external save is handled by MainActivity
        File dir = new File(context.getFilesDir(), "projects");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(dir, projectId + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Save complete project data as a single JSON bundle.
     * Includes: pages, widgets/hierarchy, styles/theme, logic blocks, events, and asset paths.
     */
    public void saveFullProject(String projectId, View screen, PageManager pageManager,
                                ThemeManager themeManager, LogicBlockManager logicBlockManager) {
        Map<String, Object> projectBundle = new HashMap<>();
        projectBundle.put("version", 2);
        projectBundle.put("projectId", projectId);

        // Save current page layout
        List<Map<String, Object>> widgetTree = serializeViewTree(screen);
        projectBundle.put("layout", widgetTree);

        // Save page list
        if (pageManager != null) {
            projectBundle.put("pages", pageManager.getPages());
            projectBundle.put("currentPage", pageManager.getCurrentPage());

            // Save all page layouts
            Map<String, String> pageLayouts = new HashMap<>();
            for (String page : pageManager.getPages()) {
                String pageJson = pageManager.loadPageLayout(page);
                if (pageJson != null && !pageJson.isEmpty()) {
                    pageLayouts.put(page, pageJson);
                }
            }
            projectBundle.put("pageLayouts", pageLayouts);
        }

        // Save theme/styles
        if (themeManager != null) {
            projectBundle.put("theme", themeManager.toJson());
        }

        // Save logic blocks for all pages
        if (logicBlockManager != null && pageManager != null) {
            Map<String, String> allLogic = new HashMap<>();
            File dir = new File(context.getFilesDir(), "projects");
            for (String page : pageManager.getPages()) {
                File logicFile = new File(dir, projectId + "_" + page + ".logic");
                if (logicFile.exists()) {
                    String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
                    if (logicJson != null && !logicJson.isEmpty()) {
                        allLogic.put(page, logicJson);
                    }
                }
            }
            // Also include current page's in-memory logic
            allLogic.put(pageManager.getCurrentPage(), logicBlockManager.toJson());
            projectBundle.put("logicBlocks", allLogic);
        }

        // Save asset file list
        try {
            String assetsPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId + "/assets";
            File assetsDir = new File(assetsPath);
            if (assetsDir.exists()) {
                List<String> assetPaths = new ArrayList<>();
                collectAssetPaths(assetsDir, assetsDir.getAbsolutePath(), assetPaths);
                projectBundle.put("assetPaths", assetPaths);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not collect asset paths: " + e.getMessage());
        }

        // Save metadata
        File dir = new File(context.getFilesDir(), "projects");
        File metaFile = new File(dir, projectId + ".meta");
        if (metaFile.exists()) {
            String metaJson = FileUtil.readFile(metaFile.getAbsolutePath());
            if (metaJson != null && !metaJson.isEmpty()) {
                projectBundle.put("metadata", metaJson);
            }
        }

        // Write to internal storage
        String bundleJson = gson.toJson(projectBundle);
        if (!dir.exists()) dir.mkdirs();
        File bundleFile = new File(dir, projectId + ".json");
        try (FileWriter writer = new FileWriter(bundleFile)) {
            writer.write(bundleJson);
        } catch (IOException e) {
            Log.e(TAG, "Failed to save full project: " + e.getMessage());
        }

        // Write to external storage
        saveToExternalStorage(projectId, bundleJson);
    }

    /**
     * Load full project data from a JSON bundle.
     * Restores pages, widgets, styles, logic blocks, and events.
     */
    public Map<String, Object> loadFullProject(String projectId) {
        File dir = new File(context.getFilesDir(), "projects");
        File bundleFile = new File(dir, projectId + ".json");

        // Try internal first
        if (!bundleFile.exists()) {
            bundleFile = tryLoadExternalBundle(projectId);
        }

        if (bundleFile == null || !bundleFile.exists()) return null;

        try (FileReader reader = new FileReader(bundleFile)) {
            Map<String, Object> bundle = gson.fromJson(reader,
                new TypeToken<Map<String, Object>>(){}.getType());

            if (bundle != null && bundle.containsKey("version")) {
                // This is a v2 full bundle
                return bundle;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse full project bundle: " + e.getMessage());
        }

        return null;
    }

    /**
     * Restore logic blocks from a full project bundle.
     */
    public void restoreLogicBlocks(Map<String, Object> bundle, String projectId) {
        if (bundle == null || !bundle.containsKey("logicBlocks")) return;

        try {
            Map<String, String> allLogic = (Map<String, String>) bundle.get("logicBlocks");
            if (allLogic == null) return;

            File dir = new File(context.getFilesDir(), "projects");
            if (!dir.exists()) dir.mkdirs();

            for (Map.Entry<String, String> entry : allLogic.entrySet()) {
                String pageName = entry.getKey();
                String logicJson = entry.getValue();
                File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
                FileUtil.writeFile(logicFile.getAbsolutePath(), logicJson);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not restore logic blocks: " + e.getMessage());
        }
    }

    /**
     * Restore theme from a full project bundle.
     */
    public void restoreTheme(Map<String, Object> bundle, String projectId) {
        if (bundle == null || !bundle.containsKey("theme")) return;

        try {
            String themeJson = bundle.get("theme").toString();
            File dir = new File(context.getFilesDir(), "projects");
            if (!dir.exists()) dir.mkdirs();
            File themeFile = new File(dir, projectId + ".theme");
            FileUtil.writeFile(themeFile.getAbsolutePath(), themeJson);
        } catch (Exception e) {
            Log.w(TAG, "Could not restore theme: " + e.getMessage());
        }
    }

    /**
     * Restore page layouts from a full project bundle.
     */
    public void restorePageLayouts(Map<String, Object> bundle, String projectId, PageManager pageManager) {
        if (bundle == null || pageManager == null) return;

        try {
            // Restore pages list
            if (bundle.containsKey("pages")) {
                List<String> pages = (List<String>) bundle.get("pages");
                if (pages != null) {
                    for (String page : pages) {
                        if (!"index".equals(page)) {
                            pageManager.addPage(page);
                        }
                    }
                }
            }

            // Restore page layouts
            if (bundle.containsKey("pageLayouts")) {
                Map<String, String> pageLayouts = (Map<String, String>) bundle.get("pageLayouts");
                if (pageLayouts != null) {
                    for (Map.Entry<String, String> entry : pageLayouts.entrySet()) {
                        pageManager.savePageLayout(entry.getKey(), entry.getValue());
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not restore page layouts: " + e.getMessage());
        }
    }

    private void collectAssetPaths(File dir, String basePath, List<String> paths) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collectAssetPaths(file, basePath, paths);
            } else {
                String relativePath = file.getAbsolutePath().substring(basePath.length());
                paths.add(relativePath);
            }
        }
    }

    private File tryLoadExternalBundle(String projectId) {
        try {
            String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId + "/project_bundle.json";
            File extFile = new File(extPath);
            if (extFile.exists()) {
                return extFile;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not load external bundle: " + e.getMessage());
        }
        return null;
    }

    private void saveToExternalStorage(String projectId, String json) {
        try {
            String basePath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId;
            File extDir = new File(basePath);
            if (!extDir.exists()) extDir.mkdirs();

            File layoutFile = new File(extDir, "layout.json");
            FileUtil.writeFile(layoutFile.getAbsolutePath(), json);
        } catch (Exception e) {
            Log.w(TAG, "Could not save to external: " + e.getMessage());
        }
    }

    public void loadProject(View screen, String projectId, WidgetBuilderEngine engine, WidgetSelector selector, DropZoneManager dropZoneManager) {
        File dir = new File(context.getFilesDir(), "projects");
        File file = new File(dir, projectId + ".json");

        // Try internal storage first
        if (!file.exists()) {
            // Try loading from external persistent storage
            file = tryLoadFromExternal(projectId, dir);
        }

        if (file == null || !file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            List<Map<String, Object>> widgetTree = new Gson().fromJson(reader, new TypeToken<List<Map<String, Object>>>() {}.getType());

            if (screen instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) screen;
                vg.removeAllViews();

                for (Map<String, Object> nodeMap : widgetTree) {
                    buildViewTree(nodeMap, vg, engine, selector, dropZoneManager);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File tryLoadFromExternal(String projectId, File internalDir) {
        try {
            String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId + "/layout.json";
            File extFile = new File(extPath);
            if (extFile.exists()) {
                // Copy to internal storage for consistency
                String json = FileUtil.readFile(extPath);
                if (json != null && !json.isEmpty()) {
                    if (!internalDir.exists()) internalDir.mkdirs();
                    File internalFile = new File(internalDir, projectId + ".json");
                    FileUtil.writeFile(internalFile.getAbsolutePath(), json);
                    return internalFile;
                }
            }
        } catch (Exception e) {
            Log.w("ProjectDataManager", "Could not load from external: " + e.getMessage());
        }
        return null;
    }

    public List<Map<String, String>> loadAllProjectsFromExternal() {
        List<Map<String, String>> projects = new ArrayList<>();
        try {
            String basePath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects";
            File projectsDir = new File(basePath);
            if (projectsDir.exists() && projectsDir.isDirectory()) {
                File[] dirs = projectsDir.listFiles();
                if (dirs != null) {
                    for (File dir : dirs) {
                        if (dir.isDirectory()) {
                            File layoutFile = new File(dir, "layout.json");
                            if (layoutFile.exists()) {
                                Map<String, String> project = new HashMap<>();
                                project.put("name", dir.getName());
                                project.put("path", layoutFile.getAbsolutePath());
                                projects.add(project);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w("ProjectDataManager", "Could not scan external projects: " + e.getMessage());
        }
        return projects;
    }

    private List<Map<String, Object>> serializeViewTree(View view) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (!(view instanceof ViewGroup)) return nodes;

        ViewGroup vg = (ViewGroup) view;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            Object tagObj = child.getTag();

            if (tagObj instanceof Map) {
                Map<String, Object> widgetMap = new HashMap<>((Map<String, Object>) tagObj);

                if (child instanceof ViewGroup) {
                    List<Map<String, Object>> children = serializeViewTree(child);
                    if (!children.isEmpty()) {
                        widgetMap.put("children", children);
                    }
                }
                nodes.add(widgetMap);
            }
        }
        return nodes;
    }

    private void buildViewTree(Map<String, Object> nodeMap, ViewGroup parent, WidgetBuilderEngine engine, WidgetSelector selector, DropZoneManager dropZoneManager) {
        if (!nodeMap.containsKey("tag")) return;

        String tag = nodeMap.get("tag").toString();
        View newView = engine.createWidget(tag);

        if (newView != null) {
            Map<String, Object> newWidgetMap = new HashMap<>(nodeMap);
            newWidgetMap.remove("children");

            engine.applyPropertiesToView(newView, newWidgetMap);
            newView.setTag(newWidgetMap);

            parent.addView(newView);

            selector.registerView(newView);
            dropZoneManager.registerWidgetAsDropZoneIfContainer(newView);

            if (nodeMap.containsKey("children") && newView instanceof ViewGroup) {
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get("children");
                for (Map<String, Object> childMap : children) {
                    buildViewTree(childMap, (ViewGroup) newView, engine, selector, dropZoneManager);
                }
            }
        }
    }
}
