package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.google.gson.Gson;
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

    private Context context;

    public ProjectDataManager(Context context) {
        this.context = context;
    }

    public void saveProject(View screen, String projectId) {
        List<Map<String, Object>> widgetTree = serializeViewTree(screen);
        String json = new Gson().toJson(widgetTree);

        // Save to internal storage
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

        // Also save to external persistent storage
        saveToExternalStorage(projectId, json);
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
            Log.w("ProjectDataManager", "Could not save to external: " + e.getMessage());
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
