package sketchweb.gl;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ProjectDataManager {

    private static final String TAG = "ProjectDataManager";
    private Context context;
    private Gson gson = new GsonBuilder().create();

    public ProjectDataManager(Context context) {
        this.context = context;
    }

    public static class ImportResult {
        public final Set<String> importedProjectIds = new HashSet<>();
        public boolean success;
        public String message;
    }

    public void saveProject(View screen, String projectId) {
        List<Map<String, Object>> widgetTree = serializeViewTree(screen);
        String json = gson.toJson(widgetTree);

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

            Map<String, String> pageLayouts = new HashMap<>();
            for (String page : pageManager.getPages()) {
                String pageJson = pageManager.loadPageLayout(page);
                if (pageJson != null && !pageJson.isEmpty()) {
                    pageLayouts.put(page, pageJson);
                }
            }
            projectBundle.put("pageLayouts", pageLayouts);
        }

        try (OutputStream fos = context.getContentResolver().openOutputStream(outputUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {
            if (internalProjectsDir.exists()) {
                addDirectoryToZip(zos, internalProjectsDir, "internal/projects/");
            }
            if (externalProjectsDir.exists()) {
                addDirectoryToZip(zos, externalProjectsDir, "external/projects/");
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to export all projects zip: " + e.getMessage());
            return false;
        }
    }

    public boolean exportSingleProjectAsZip(String projectId, Uri outputUri) {
        File internalProjectsDir = new File(context.getFilesDir(), "projects");
        String extProjectPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/projects/" + projectId;
        File externalProjectDir = new File(extProjectPath);

        try (OutputStream fos = context.getContentResolver().openOutputStream(outputUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {

            if (internalProjectsDir.exists()) {
                File[] files = internalProjectsDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.isFile()) continue;
                        String name = file.getName();
                        if (name.startsWith(projectId + ".") || name.startsWith(projectId + "_")) {
                            addFileToZip(zos, file, "internal/projects/" + name);
                        }
                    }
                }
            }
            allLogic.put(pageManager.getCurrentPage(), logicBlockManager.toJson());
            projectBundle.put("logicBlocks", allLogic);
        }

            if (externalProjectDir.exists()) {
                addDirectoryToZip(zos, externalProjectDir, "external/projects/" + projectId + "/");
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to export project zip: " + e.getMessage());
            return false;
        }
    }

    public ImportResult importProjectsFromZip(Uri zipUri) {
        ImportResult result = new ImportResult();
        File internalProjectsDir = new File(context.getFilesDir(), "projects");
        if (!internalProjectsDir.exists()) {
            internalProjectsDir.mkdirs();
        }

        File externalProjectsDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/projects");
        if (!externalProjectsDir.exists()) {
            externalProjectsDir.mkdirs();
        }

        saveToExternalStorage(projectId, bundleJson);
    }

    /**
     * Load full project data from a JSON bundle.
     */
    public Map<String, Object> loadFullProject(String projectId) {
        File dir = new File(context.getFilesDir(), "projects");
        File bundleFile = new File(dir, projectId + ".json");

        if (!bundleFile.exists()) {
            bundleFile = tryLoadExternalBundle(projectId);
        }

                if (entry.isDirectory()) {
                    if (!outFile.exists()) outFile.mkdirs();
                    zis.closeEntry();
                    continue;
                }

                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

            if (bundle != null && bundle.containsKey("version")) {
                return bundle;
            }

            result.success = true;
            result.message = "Imported " + result.importedProjectIds.size() + " project(s)";
        } catch (Exception e) {
            result.success = false;
            result.message = e.getMessage();
            Log.e(TAG, "Failed to import projects zip: " + e.getMessage());
        }
        return result;
    }

    /**
     * Export a single project as a ZIP file to the given URI.
     * Contains: all internal project files + external assets directory.
     */
    public boolean exportProjectAsZip(Uri uri, String projectId) {
        try {
            OutputStream fos = context.getContentResolver().openOutputStream(uri);
            if (fos == null) return false;

            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos));
            File dir = new File(context.getFilesDir(), "projects");

            // Add all project files from internal storage
            File[] allFiles = dir.listFiles();
            if (allFiles != null) {
                for (File file : allFiles) {
                    if (!file.isFile()) continue;
                    String name = file.getName();
                    if (name.startsWith(projectId + ".") || name.startsWith(projectId + "_")) {
                        addFileToZip(zos, "projects/" + name, file);
                    }
                }
            }

            // Add external assets
            String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId;
            File extDir = new File(extPath);
            if (extDir.exists()) {
                addDirectoryToZip(zos, extDir, "external/" + projectId + "/");
            }

            zos.close();
            fos.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Export project ZIP failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Export all projects as a ZIP file to the given URI.
     */
    public boolean exportAllProjectsAsZip(Uri uri) {
        File dir = new File(context.getFilesDir(), "projects");
        if (!dir.exists() || dir.listFiles() == null) return false;

        try {
            OutputStream fos = context.getContentResolver().openOutputStream(uri);
            if (fos == null) return false;

            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos));

            // Add all internal project files
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        addFileToZip(zos, "projects/" + file.getName(), file);
                    }
                }
            }

            // Add all external project directories
            String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects";
            File extDir = new File(extPath);
            if (extDir.exists()) {
                addDirectoryToZip(zos, extDir, "external/");
            }

            zos.close();
            fos.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Export all projects ZIP failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Import project(s) from a ZIP file URI.
     * Restores all internal project files and external assets.
     * Returns list of project IDs that were imported.
     */
    public List<String> importProjectsFromZip(Uri uri) {
        List<String> importedIds = new ArrayList<>();
        File internalDir = new File(context.getFilesDir(), "projects");
        if (!internalDir.exists()) internalDir.mkdirs();

        try {
            String canonicalDirPath = internalDir.getCanonicalPath();
            String extBasePath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects";
            File extBaseDir = new File(extBasePath);
            if (!extBaseDir.exists()) extBaseDir.mkdirs();
            String canonicalExtPath = extBaseDir.getCanonicalPath();

            InputStream fis = context.getContentResolver().openInputStream(uri);
            if (fis == null) return importedIds;

            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                File outFile;
                String safeCheckPath;
                if (entryName.startsWith("external/")) {
                    String relativePath = entryName.substring("external/".length());
                    outFile = new File(extBaseDir, relativePath);
                    safeCheckPath = canonicalExtPath;
                } else if (entryName.startsWith("projects/")) {
                    String relativePath = entryName.substring("projects/".length());
                    outFile = new File(internalDir, relativePath);
                    safeCheckPath = canonicalDirPath;

                    // Track imported project IDs from .json files
                    if (relativePath.endsWith(".json") && !relativePath.contains("_")) {
                        String pid = relativePath.replace(".json", "");
                        if (!importedIds.contains(pid)) {
                            importedIds.add(pid);
                        }
                    }
                } else {
                    // Legacy format without prefix - treat as internal
                    outFile = new File(internalDir, entryName);
                    safeCheckPath = canonicalDirPath;

                    if (entryName.endsWith(".json") && !entryName.contains("_") && !entryName.contains("/")) {
                        String pid = entryName.replace(".json", "");
                        if (!importedIds.contains(pid)) {
                            importedIds.add(pid);
                        }
                    }
                }

                // Path traversal protection
                String canonicalFilePath = outFile.getCanonicalPath();
                if (!canonicalFilePath.startsWith(safeCheckPath + File.separator) &&
                    !canonicalFilePath.equals(safeCheckPath)) {
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    if (!outFile.exists()) outFile.mkdirs();
                    zis.closeEntry();
                    continue;
                }

                File parentFile = outFile.getParentFile();
                if (parentFile != null && !parentFile.exists()) {
                    parentFile.mkdirs();
                }

                FileOutputStream fos = new FileOutputStream(outFile);
                byte[] buffer = new byte[4096];
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    fos.write(buffer, 0, count);
                }
                fos.close();
                zis.closeEntry();
            }
            zis.close();
            fis.close();

        } catch (Exception e) {
            Log.e(TAG, "Import projects from ZIP failed: " + e.getMessage());
        }

        return importedIds;
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
        }
    }

    private void captureProjectIdFromInternalFile(String fileName, Set<String> projectIds) {
        if (fileName == null || fileName.isEmpty()) return;
        String id = null;
        if (fileName.contains("_")) {
            id = fileName.substring(0, fileName.indexOf('_'));
        } else if (fileName.contains(".")) {
            id = fileName.substring(0, fileName.indexOf('.'));
        }
    }

    /**
     * Restore page layouts from a full project bundle.
     */
    public void restorePageLayouts(Map<String, Object> bundle, String projectId, PageManager pageManager) {
        if (bundle == null || pageManager == null) return;

        try {
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

    // ---- Helper methods ----

    private void addFileToZip(ZipOutputStream zos, String entryName, File file) throws IOException {
        byte[] buffer = new byte[4096];
        FileInputStream fis = new FileInputStream(file);
        zos.putNextEntry(new ZipEntry(entryName));
        int length;
        while ((length = fis.read(buffer)) > 0) {
            zos.write(buffer, 0, length);
        }
        zos.closeEntry();
        fis.close();
    }

    private void addDirectoryToZip(ZipOutputStream zos, File dir, String prefix) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, prefix + file.getName() + "/");
            } else {
                addFileToZip(zos, prefix + file.getName(), file);
            }
        }
    }

    private void collectAssetPaths(File dir, String basePath, List<String> paths) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, prefix + file.getName() + "/");
            } else {
                addFileToZip(zos, file, prefix + file.getName());
            }
        }
    }

    private void addFileToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        byte[] buffer = new byte[4096];
        try (FileInputStream fis = new FileInputStream(file)) {
            zos.putNextEntry(new ZipEntry(entryName));
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
        }
    }

    public void loadProject(View screen, String projectId, WidgetBuilderEngine engine, WidgetSelector selector, DropZoneManager dropZoneManager) {
        File dir = new File(context.getFilesDir(), "projects");
        File file = new File(dir, projectId + ".json");

        if (!file.exists()) {
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
