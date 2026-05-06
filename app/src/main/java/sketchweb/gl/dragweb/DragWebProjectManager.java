package sketchweb.gl.dragweb;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Save / load / list / delete projects under <code>/.dragweb/projects/</code>. */
public class DragWebProjectManager {

    private final Context context;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public DragWebProjectManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<String> listProjects() {
        File dir = DragWebPaths.projectsDir(context);
        File[] files = dir.listFiles(File::isDirectory);
        if (files == null) return new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (File f : files) names.add(f.getName());
        Collections.sort(names);
        return names;
    }

    public boolean exists(String projectName) {
        return DragWebPaths.projectFile(context, projectName).exists();
    }

    public DragWebProject load(String projectName) throws IOException {
        File f = DragWebPaths.projectFile(context, projectName);
        if (!f.exists()) {
            DragWebProject fresh = new DragWebProject(projectName);
            save(fresh);
            return fresh;
        }
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            DragWebProject p = gson.fromJson(r, DragWebProject.class);
            if (p == null) p = new DragWebProject(projectName);
            if (p.pages == null || p.pages.isEmpty()) p.pages = new ArrayList<>();
            if (p.pages.isEmpty()) p.pages.add("index.html");
            return p;
        } catch (JsonSyntaxException e) {
            throw new IOException("Corrupt project.json: " + e.getMessage(), e);
        }
    }

    public void save(DragWebProject project) throws IOException {
        File f = DragWebPaths.projectFile(context, project.name);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileWriter w = new FileWriter(f)) {
            gson.toJson(project, w);
        }
    }

    public void delete(String projectName) {
        File dir = DragWebPaths.projectDir(context, projectName);
        deleteRecursive(dir);
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}
