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
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ProjectDataManager {

    private static final String TAG = "ProjectDataManager";

    // ZIP folder names used by the new clean backup format
    private static final String ZIP_PAGES       = "pages/";
    private static final String ZIP_STYLES      = "styles/";
    private static final String ZIP_LOGIC       = "logic/";
    private static final String ZIP_ASSETS      = "assets/";
    private static final String ZIP_META        = "meta/";
    private static final String ZIP_WIDGETS     = "widgets/";
    private static final String ZIP_COMPONENTS  = "components/";
    private static final String ZIP_ANIMATIONS  = "animations/";
    private static final String ZIP_ICONS       = "icons/";
    private static final String ZIP_CUSTOM      = "custom/";
    private static final String ZIP_BREAKPOINTS = "breakpoints/";

    private Context context;
    private Gson gson = new GsonBuilder().create();

    public ProjectDataManager(Context context) {
        this.context = context;
    }

    /** Generate a short unique project ID using a numeric system (project_XX) */
    public static String generateProjectId(Context context) {
        File dir = new File(context.getFilesDir(), "projects");
        int maxNumber = 0;
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".json")) {
                        String fileId = file.getName().replace(".json", "");
                        if (fileId.startsWith("project_")) {
                            try {
                                String numStr = fileId.substring("project_".length());
                                // Support IDs like project_01 or project_01_about
                                if (numStr.contains("_")) {
                                    numStr = numStr.substring(0, numStr.indexOf("_"));
                                }
                                int num = Integer.parseInt(numStr);
                                if (num > maxNumber) {
                                    maxNumber = num;
                                }
                            } catch (NumberFormatException e) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }
        int nextNumber = maxNumber + 1;
        return String.format(java.util.Locale.US, "project_%02d", nextNumber);
    }

    public static class ImportResult {
        public final Set<String> importedProjectIds = new HashSet<>();
        public boolean success;
        public String message;
    }

    // -------------------------------------------------------------------------
    // Save / Load
    // -------------------------------------------------------------------------

    public void saveProject(View screen, String projectId) {
        List<Map<String, Object>> widgetTree = serializeViewTree(screen);
        String json = gson.toJson(widgetTree);

        File dir = new File(context.getFilesDir(), "projects");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, projectId + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadProject(View screen, String projectId, WidgetBuilderEngine engine,
                            WidgetSelector selector, DropZoneManager dropZoneManager) {
        File dir = new File(context.getFilesDir(), "projects");
        File file = new File(dir, projectId + ".json");

        if (!file.exists()) {
            file = tryLoadFromExternal(projectId, dir);
        }
        if (file == null || !file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            List<Map<String, Object>> widgetTree = new Gson().fromJson(
                    reader, new TypeToken<List<Map<String, Object>>>() {}.getType());

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

    // -------------------------------------------------------------------------
    // Export (new clean ZIP structure)
    // -------------------------------------------------------------------------

    /**
     * Export a single project as a ZIP with the canonical folder structure:
     *
     *   pages/    – widget-tree JSON files (replaces the old layout.json duplication)
     *   styles/   – theme file
     *   logic/    – per-page logic-block files
     *   assets/   – project assets (images, etc.)
     *   meta/     – project metadata
     *   widgets/  – custom widget definitions (if any)
     */
    public boolean exportSingleProjectAsZip(String projectId, Uri outputUri) {
        File internalDir = new File(context.getFilesDir(), "projects");
        String extAssetsPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId + "/assets";
        String extCustomWidgetsPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/custom/widgets.json";
        String extCustomParamsPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/custom/params.json";

        try (OutputStream fos = context.getContentResolver().openOutputStream(outputUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {

            if (internalDir.exists()) {
                File[] files = internalDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.isFile()) continue;
                        String name = file.getName();

                        if (name.equals(projectId + ".json")) {
                            // Main widget tree → pages/
                            addFileToZip(zos, ZIP_PAGES + name, file);

                        } else if (name.startsWith(projectId + "_") && name.endsWith(".json")) {
                            // Per-page layout → pages/
                            addFileToZip(zos, ZIP_PAGES + name, file);

                        } else if (name.equals(projectId + ".theme")) {
                            // Theme → styles/
                            addFileToZip(zos, ZIP_STYLES + name, file);



                        } else if (name.equals(projectId + ".meta")) {
                            // Metadata → meta/
                            addFileToZip(zos, ZIP_META + name, file);

                        } else if (name.equals(projectId + ".icons")) {
                            // Icon library config → icons/
                            addFileToZip(zos, ZIP_ICONS + name, file);

                        } else if (name.equals(projectId + ".components.json")) {
                            // Reusable components → components/
                            addFileToZip(zos, ZIP_COMPONENTS + name, file);

                        } else if (name.equals(projectId + ".animations")) {
                            // Custom animation presets → animations/
                            addFileToZip(zos, ZIP_ANIMATIONS + name, file);

                        } else if (name.equals(projectId + ".breakpoints.json")) {
                            // Responsive breakpoints → breakpoints/
                            addFileToZip(zos, ZIP_BREAKPOINTS + name, file);
                        }
                        // Other files (e.g. belonging to other projects) are skipped
                    }
                }
            }

            // Export logic blocks from projects/logic folder
            File logicDir = new File(internalDir, "logic");
            if (logicDir.exists() && logicDir.isDirectory()) {
                File[] logicFiles = logicDir.listFiles();
                if (logicFiles != null) {
                    for (File file : logicFiles) {
                        if (!file.isFile()) continue;
                        String name = file.getName();
                        if ((name.startsWith(projectId + "_") || name.startsWith(projectId + ".")) && name.endsWith(".logic")) {
                            addFileToZip(zos, ZIP_LOGIC + name, file);
                        }
                    }
                }
            }

            // Assets → assets/{projectId}/
            File assetsDir = new File(extAssetsPath);
            if (assetsDir.exists() && assetsDir.isDirectory()) {
                addDirectoryToZip(zos, assetsDir, ZIP_ASSETS + projectId + "/");
            }

            // Custom widgets (shared) → widgets/
            File customWidgets = new File(extCustomWidgetsPath);
            if (customWidgets.exists()) {
                addFileToZip(zos, ZIP_WIDGETS + "custom.json", customWidgets);
            }

            // Custom params (shared) → custom/
            File customParams = new File(extCustomParamsPath);
            if (customParams.exists()) {
                addFileToZip(zos, ZIP_CUSTOM + "params.json", customParams);
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to export single project zip: " + e.getMessage());
            return false;
        }
    }

    /**
     * Export ALL projects as a ZIP using the same canonical folder structure.
     * Each project's files are sorted into pages/, styles/, logic/, etc.
     */
    public boolean exportAllProjectsAsZip(Uri outputUri) {
        File internalDir = new File(context.getFilesDir(), "projects");
        String extProjectsBase = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects";

        if (!internalDir.exists() && !new File(extProjectsBase).exists()) {
            return false;
        }

        try (OutputStream fos = context.getContentResolver().openOutputStream(outputUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {

            // Internal project files – sort by extension into canonical folders
            if (internalDir.exists()) {
                File[] files = internalDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.isFile()) continue;
                        String name = file.getName();
                        String zipPath = classifyInternalFile(name);
                        if (zipPath != null) {
                            addFileToZip(zos, zipPath, file);
                        }
                    }
                }
                // Scan the logic/ subdirectory too for all projects' logic files
                File logicDir = new File(internalDir, "logic");
                if (logicDir.exists() && logicDir.isDirectory()) {
                    File[] logicFiles = logicDir.listFiles();
                    if (logicFiles != null) {
                        for (File file : logicFiles) {
                            if (!file.isFile()) continue;
                            String name = file.getName();
                            if (name.endsWith(".logic")) {
                                addFileToZip(zos, ZIP_LOGIC + name, file);
                            }
                        }
                    }
                }
            }

            // External assets for each project
            File extProjects = new File(extProjectsBase);
            if (extProjects.exists() && extProjects.isDirectory()) {
                File[] projectDirs = extProjects.listFiles();
                if (projectDirs != null) {
                    for (File dir : projectDirs) {
                        if (!dir.isDirectory()) continue;
                        File assets = new File(dir, "assets");
                        if (assets.exists()) {
                            addDirectoryToZip(zos, assets, ZIP_ASSETS + dir.getName() + "/");
                        }
                    }
                }
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to export all projects zip: " + e.getMessage());
            return false;
        }
    }

    /**
     * Classify an internal project file into the correct ZIP folder path.
     * Returns null if the file should not be included.
     */
    private String classifyInternalFile(String name) {
        if (name.endsWith(".icons")) return ZIP_ICONS + name;
        if (name.endsWith(".components.json")) return ZIP_COMPONENTS + name;
        if (name.endsWith(".animations")) return ZIP_ANIMATIONS + name;
        if (name.endsWith(".breakpoints.json")) return ZIP_BREAKPOINTS + name;
        if (name.endsWith(".json")) return ZIP_PAGES + name;
        if (name.endsWith(".theme")) return ZIP_STYLES + name;
        if (name.endsWith(".logic")) return ZIP_LOGIC + name;
        if (name.endsWith(".meta")) return ZIP_META + name;
        return null;
    }

    // -------------------------------------------------------------------------
    // Import (handles new canonical structure + backward-compatible old structure)
    // -------------------------------------------------------------------------

    public ImportResult importProjectsFromZip(Uri zipUri) {
        ImportResult result = new ImportResult();
        File internalProjectsDir = new File(context.getFilesDir(), "projects");
        if (!internalProjectsDir.exists()) internalProjectsDir.mkdirs();

        String extBase = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb";
        File externalProjectsDir = new File(extBase + "/projects");
        if (!externalProjectsDir.exists()) externalProjectsDir.mkdirs();

        try (InputStream fis = context.getContentResolver().openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {

            String internalBase = internalProjectsDir.getCanonicalPath();
            String externalBase = externalProjectsDir.getCanonicalPath();
            String extDragwebBase = new File(extBase).getCanonicalPath();

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                File outFile;
                String safeBase;

                // ---- New canonical structure ----
                if (entryName.startsWith(ZIP_PAGES)) {
                    // pages/{projectId}.json → internal projects dir
                    String relative = entryName.substring(ZIP_PAGES.length());
                    outFile = new File(internalProjectsDir, relative);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(relative, result.importedProjectIds);

                } else if (entryName.startsWith(ZIP_STYLES)) {
                    // styles/{projectId}.theme → internal projects dir
                    String relative = entryName.substring(ZIP_STYLES.length());
                    outFile = new File(internalProjectsDir, relative);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(relative, result.importedProjectIds);

                } else if (entryName.startsWith(ZIP_LOGIC)) {
                    // logic/{projectId}_{page}.logic → internal projects/logic dir
                    String relative = entryName.substring(ZIP_LOGIC.length());
                    File logicDir = new File(internalProjectsDir, "logic");
                    if (!logicDir.exists()) logicDir.mkdirs();
                    outFile = new File(logicDir, relative);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(relative, result.importedProjectIds);

                } else if (entryName.startsWith(ZIP_META)) {
                    // meta/{projectId}.meta → internal projects dir
                    String relative = entryName.substring(ZIP_META.length());
                    outFile = new File(internalProjectsDir, relative);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(relative, result.importedProjectIds);

                } else if (entryName.startsWith(ZIP_ICONS)) {
                    // icons/{projectId}.icons → internal projects dir
                    String relative = entryName.substring(ZIP_ICONS.length());
                    outFile = new File(internalProjectsDir, relative);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(relative, result.importedProjectIds);

                } else if (entryName.startsWith(ZIP_COMPONENTS)) {
                    // components/{projectId}.components.json → internal projects dir
                    String relative = entryName.substring(ZIP_COMPONENTS.length());
                    outFile = new File(internalProjectsDir, relative);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(relative, result.importedProjectIds);

                } else if (entryName.startsWith(ZIP_ANIMATIONS)) {
                    // animations/{projectId}.animations.json → internal projects dir
                    String relative = entryName.substring(ZIP_ANIMATIONS.length());
                    outFile = new File(internalProjectsDir, relative);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(relative, result.importedProjectIds);

                } else if (entryName.startsWith(ZIP_BREAKPOINTS)) {
                    // breakpoints/{projectId}.breakpoints.json → internal projects dir
                    String relative = entryName.substring(ZIP_BREAKPOINTS.length());
                    outFile = new File(internalProjectsDir, relative);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(relative, result.importedProjectIds);

                } else if (entryName.startsWith(ZIP_ASSETS)) {
                    // assets/{projectId}/... → external /.dragweb/projects/{projectId}/assets/
                    String relative = entryName.substring(ZIP_ASSETS.length());
                    // relative = "{projectId}/file" or just "file" (legacy flat)
                    outFile = new File(externalProjectsDir, relative.isEmpty() ? "." : relative);
                    safeBase = externalBase;
                    // Capture projectId from first path segment
                    captureProjectIdFromExternalEntry(relative, result.importedProjectIds);

                } else if (entryName.startsWith(ZIP_WIDGETS)) {
                    // widgets/custom.json → external /.dragweb/custom/
                    String relative = entryName.substring(ZIP_WIDGETS.length());
                    File customDir = new File(extBase + "/custom");
                    if (!customDir.exists()) customDir.mkdirs();
                    outFile = new File(customDir, relative);
                    safeBase = extDragwebBase;

                } else if (entryName.startsWith(ZIP_CUSTOM)) {
                    // custom/params.json → external /.dragweb/custom/
                    String relative = entryName.substring(ZIP_CUSTOM.length());
                    File customDir = new File(extBase + "/custom");
                    if (!customDir.exists()) customDir.mkdirs();
                    outFile = new File(customDir, relative);
                    safeBase = extDragwebBase;

                } else if (entryName.startsWith("data/")) {
                    // data/{projectId}.* → internal projects dir
                    String relative = entryName.substring("data/".length());
                    outFile = new File(internalProjectsDir, relative);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(relative, result.importedProjectIds);

                // ---- Backward-compatible old structure ----
                } else if (entryName.startsWith("external/projects/")) {
                    String relative = entryName.substring("external/projects/".length());
                    outFile = new File(externalProjectsDir, relative);
                    safeBase = externalBase;
                    captureProjectIdFromExternalEntry(relative, result.importedProjectIds);

                } else if (entryName.startsWith("external/")) {
                    String relative = entryName.substring("external/".length());
                    outFile = new File(externalProjectsDir, relative);
                    safeBase = externalBase;
                    captureProjectIdFromExternalEntry(relative, result.importedProjectIds);

                } else if (entryName.startsWith("internal/projects/")) {
                    String relative = entryName.substring("internal/projects/".length());
                    outFile = new File(internalProjectsDir, relative);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(relative, result.importedProjectIds);

                } else {
                    // Legacy root-level files → internal
                    outFile = new File(internalProjectsDir, entryName);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(entryName, result.importedProjectIds);
                }

                // Path-traversal safety check
                String outCanonical = outFile.getCanonicalPath();
                if (!outCanonical.startsWith(safeBase + File.separator)
                        && !outCanonical.equals(safeBase)) {
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    if (!outFile.exists()) outFile.mkdirs();
                    zis.closeEntry();
                    continue;
                }

                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = zis.read(buffer)) != -1) {
                        fos.write(buffer, 0, count);
                    }
                }
                zis.closeEntry();
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

    // -------------------------------------------------------------------------
    // External storage discovery
    // -------------------------------------------------------------------------

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
                        if (!dir.isDirectory()) continue;
                        // Support both old layout.json and new structure
                        File layoutFile = new File(dir, "layout.json");
                        if (!layoutFile.exists()) {
                            // Check if internal .json exists for this project
                            File internalJson = new File(
                                    context.getFilesDir() + "/projects/" + dir.getName() + ".json");
                            if (!internalJson.exists()) continue;
                            layoutFile = internalJson;
                        }
                        Map<String, String> project = new HashMap<>();
                        project.put("name", dir.getName());
                        project.put("path", layoutFile.getAbsolutePath());
                        projects.add(project);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not scan external projects: " + e.getMessage());
        }
        return projects;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private File tryLoadFromExternal(String projectId, File internalDir) {
        try {
            // Try new internal-mirrored path first
            String extNewPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                    + "/.dragweb/projects/" + projectId + "/" + projectId + ".json";
            File extNew = new File(extNewPath);
            if (extNew.exists()) {
                return copyToInternal(extNew, internalDir, projectId + ".json");
            }

            // Fall back to legacy layout.json
            String extLegacyPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                    + "/.dragweb/projects/" + projectId + "/layout.json";
            File extLegacy = new File(extLegacyPath);
            if (extLegacy.exists()) {
                return copyToInternal(extLegacy, internalDir, projectId + ".json");
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not load from external: " + e.getMessage());
        }
        return null;
    }

    private File copyToInternal(File src, File internalDir, String destName) throws IOException {
        String json = FileUtil.readFile(src.getAbsolutePath());
        if (json == null || json.isEmpty()) return null;
        if (!internalDir.exists()) internalDir.mkdirs();
        File dest = new File(internalDir, destName);
        FileUtil.writeFile(dest.getAbsolutePath(), json);
        return dest;
    }

    private void captureProjectIdFromExternalEntry(String relativePath, Set<String> projectIds) {
        int slash = relativePath.indexOf('/');
        if (slash > 0) {
            String candidate = relativePath.substring(0, slash);
            if (!candidate.isEmpty()) projectIds.add(candidate);
        }
    }

    private void captureProjectIdFromInternalFile(String fileName, Set<String> projectIds) {
        if (fileName == null || fileName.isEmpty()) return;
        String id;
        int dotIdx = fileName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
        
        if (baseName.startsWith("project_")) {
            int secondUnderscore = baseName.indexOf('_', 8);
            if (secondUnderscore > 0) {
                id = baseName.substring(0, secondUnderscore);
            } else {
                id = baseName;
            }
        } else {
            int firstUnderscore = baseName.indexOf('_');
            if (firstUnderscore > 0) {
                id = baseName.substring(0, firstUnderscore);
            } else {
                id = baseName;
            }
        }
        if (!id.isEmpty()) projectIds.add(id);
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

    private void addFileToZip(ZipOutputStream zos, String entryName, File file) throws IOException {
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

    // -------------------------------------------------------------------------
    // View tree serialization / deserialization
    // -------------------------------------------------------------------------

    private List<Map<String, Object>> serializeViewTree(View view) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (!(view instanceof ViewGroup)) return nodes;

        ViewGroup vg = (ViewGroup) view;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            Object tagObj = child.getTag();

            if (tagObj instanceof Map) {
                @SuppressWarnings("unchecked")
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

    private void buildViewTree(Map<String, Object> nodeMap, ViewGroup parent,
                               WidgetBuilderEngine engine, WidgetSelector selector,
                               DropZoneManager dropZoneManager) {
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
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get("children");
                for (Map<String, Object> childMap : children) {
                    buildViewTree(childMap, (ViewGroup) newView, engine, selector, dropZoneManager);
                }
            }
        }
    }
}
