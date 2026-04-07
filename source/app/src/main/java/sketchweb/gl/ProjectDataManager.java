package sketchweb.gl;

import android.content.Context;
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

    public void saveProject(View screen, String projectName) {
        List<Map<String, Object>> widgetTree = serializeViewTree(screen);
        String json = new Gson().toJson(widgetTree);

        File dir = new File(context.getFilesDir(), "projects");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(dir, projectName + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadProject(View screen, String projectName, WidgetBuilderEngine engine, WidgetSelector selector, DropZoneManager dropZoneManager) {
        File dir = new File(context.getFilesDir(), "projects");
        File file = new File(dir, projectName + ".json");

        if (!file.exists()) return;

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
            // Restore widget map logic
            Map<String, Object> newWidgetMap = new HashMap<>(nodeMap);
            newWidgetMap.remove("children"); // Don't keep recursive children list in view tag

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
