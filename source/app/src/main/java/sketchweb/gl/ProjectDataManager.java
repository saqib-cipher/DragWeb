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

    public boolean exportAllProjectsAsZip(Uri outputUri) {
        File internalProjectsDir = new File(context.getFilesDir(), "projects");
        String extProjectsPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/projects";
        File externalProjectsDir = new File(extProjectsPath);
        if (!internalProjectsDir.exists() && !externalProjectsDir.exists()) {
            return false;
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

        try (InputStream fis = context.getContentResolver().openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {

            String internalBase = internalProjectsDir.getCanonicalPath();
            String externalBase = externalProjectsDir.getCanonicalPath();

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                File outFile;
                String safeBase;

                // Cleanly map entries. Drop layout.json and index.json.
                if (entryName.endsWith("layout.json") || entryName.endsWith("index.json")) {
                    zis.closeEntry();
                    continue;
                }

                if (entryName.startsWith("pages/") || entryName.startsWith("styles/") || entryName.startsWith("widgets/") || entryName.startsWith("data/")) {
                    // Flatten data back into internal projects dir
                    String[] parts = entryName.split("/");
                    String fileName = parts[parts.length - 1];
                    outFile = new File(internalProjectsDir, fileName);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(fileName, result.importedProjectIds);
                } else if (entryName.startsWith("assets/")) {
                    String relative = entryName.substring("assets/".length());
                    // For standard single project ZIP, files are mapped to the first imported project ID or dynamically
                    // The easiest fix is to map it to the external directory under the extracted project ID
                    // Assuming we find the ID from page files. Since zip iterations are unpredictable,
                    // a more robust way is to just assume the zip filename or wait.
                    // But we can extract it to a temporary directory, and then move it when we know the ID?
                    // Or we extract directly if we can guess the ID from the zip or we will use externalProjectsDir directly
                    // Wait, standard single-project exports name the ZIP `ProjectName_ProjectId.zip`.
                    // Let's just create a generic asset folder and we can map it later, OR
                    // since DragWeb stores assets in `.dragweb/projects/{projectId}/assets/`, and the zip
                    // only has one `assets/` folder, we can guess the projectId from the other files.
                    // Actually, let's place it temporarily and then handle it after loop.
                    outFile = new File(externalProjectsDir, "temp_assets/" + relative);
                    safeBase = externalBase;
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
                    // Legacy backup compatibility: treat root files as internal.
                    outFile = new File(internalProjectsDir, entryName);
                    safeBase = internalBase;
                    captureProjectIdFromInternalFile(entryName, result.importedProjectIds);
                }

                String outCanonical = outFile.getCanonicalPath();
                if (!outCanonical.startsWith(safeBase + File.separator) && !outCanonical.equals(safeBase)) {
                    zis.closeEntry();
                    continue;
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

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = zis.read(buffer)) != -1) {
                        fos.write(buffer, 0, count);
                    }
                }
                zis.closeEntry();
            }

            // Move temporary assets to proper project folder
            if (!result.importedProjectIds.isEmpty()) {
                String id = result.importedProjectIds.iterator().next();
                File tempAssets = new File(externalProjectsDir, "temp_assets");
                if (tempAssets.exists() && tempAssets.isDirectory()) {
                    File targetAssets = new File(externalProjectsDir, id + "/assets");
                    if (!targetAssets.exists()) targetAssets.mkdirs();

                    File[] assets = tempAssets.listFiles();
                    if (assets != null) {
                        for (File asset : assets) {
                            asset.renameTo(new File(targetAssets, asset.getName()));
                        }
                    }
                    tempAssets.delete();
                }
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

    private void captureProjectIdFromExternalEntry(String relativePath, Set<String> projectIds) {
        int slash = relativePath.indexOf('/');
        if (slash > 0) {
            String candidate = relativePath.substring(0, slash);
            if (!candidate.isEmpty()) {
                projectIds.add(candidate);
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
        if (id != null && !id.isEmpty()) {
            projectIds.add(id);
        }
    }

    private void addDirectoryToZip(ZipOutputStream zos, File dir, String prefix) throws IOException {
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
