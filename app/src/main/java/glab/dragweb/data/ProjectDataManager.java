package glab.dragweb.data;

import glab.dragweb.R;
import glab.dragweb.activities.*;
import glab.dragweb.fragments.*;
import glab.dragweb.engine.*;
import glab.dragweb.ui.*;
import glab.dragweb.logic.*;
import glab.dragweb.codegen.*;
import glab.dragweb.data.*;
import glab.dragweb.adapters.*;
import glab.dragweb.models.*;
import glab.dragweb.util.*;
import glab.dragweb.colorpicker.*;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ProjectDataManager {

    private static final String TAG = "ProjectDataManager";
    private static final Gson gson = new Gson();

    private Context context;

    public ProjectDataManager(Context context) {
        this.context = context;
    }

    private static int extractProjectNumber(String name) {
        String s = name;
        // Strip old project_ prefix if present
        if (s.startsWith("project_")) s = s.substring(8);
        // Strip file extension if present
        int dot = s.indexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        // Strip trailing suffix (e.g. project_01_theme → 01)
        int us = s.indexOf('_');
        if (us > 0) s = s.substring(0, us);
        // Parse leading digits
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
            else break;
        }
        if (digits.length() == 0) return -1;
        try { return Integer.parseInt(digits.toString()); } catch (Exception e) { return -1; }
    }

    public static String generateProjectId(Context context) {
        int maxNum = 0;
        File extDir = new File(FileUtil.getDragWebDir(context), "projects");
        if (extDir.exists()) {
            File[] dirs = FileUtil.listFiles(extDir);
            if (dirs != null) {
                for (File f : dirs) {
                    int n = extractProjectNumber(f.getName());
                    if (n > maxNum) maxNum = n;
                }
            }
        }
        return String.format(Locale.US, "%02d", maxNum + 1);
    }

    public static String generateProjectId(Context context, Set<String> exclude) {
        int maxNum = 0;
        File extDir = new File(FileUtil.getDragWebDir(context), "projects");
        if (extDir.exists()) {
            File[] dirs = FileUtil.listFiles(extDir);
            if (dirs != null) {
                for (File f : dirs) {
                    int n = extractProjectNumber(f.getName());
                    if (n > maxNum) maxNum = n;
                }
            }
        }
        if (exclude != null) {
            while (exclude.contains(String.format(Locale.US, "%02d", maxNum + 1))) {
                maxNum++;
            }
        }
        return String.format(Locale.US, "%02d", maxNum + 1);
    }

    // ──────────────────────────────────────────────
    // Import result
    // ──────────────────────────────────────────────

    public static class ImportResult {
        public boolean success = false;
        public String message = "";
        public List<String> importedProjectIds = new ArrayList<>();
    }

    // ──────────────────────────────────────────────
    // Save / Load
    // ──────────────────────────────────────────────

    public void saveProject(View screen, String projectId, Runnable onComplete) {
        List<Map<String, Object>> widgetTree = serializeViewTree(screen);
        new Thread(() -> {
            String json = gson.toJson(widgetTree);

            // Save to .dragweb/projects/projectId and .dragweb/projects/projectId.json
            File extDir = new File(FileUtil.getDragWebDir(context), "projects/" + projectId);
            if (!extDir.exists()) extDir.mkdirs();
            FileUtil.writeFile(new File(extDir, "layout.json").getAbsolutePath(), json);
            FileUtil.writeFile(new File(extDir, projectId + ".json").getAbsolutePath(), json);
            FileUtil.writeFile(new File(new File(FileUtil.getDragWebDir(context), "projects"), projectId + ".json").getAbsolutePath(), json);

            if (onComplete != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete);
            }
        }).start();
    }

    public void loadProject(View screen, String projectId, WidgetBuilderEngine engine,
                            WidgetSelector selector, DropZoneManager dropZoneManager, Runnable onComplete) {
        new Thread(() -> {
            File projectsDir = new File(FileUtil.getDragWebDir(context), "projects");
            File file = new File(projectsDir, projectId + "/" + projectId + ".json");
            if (!file.exists()) {
                file = new File(projectsDir, projectId + "/layout.json");
            }
            if (!file.exists()) {
                file = new File(projectsDir, projectId + ".json");
            }
            if (!file.exists()) {
                file = new File(projectsDir, projectId + "/pages/index.json");
            }

            if (!file.exists()) {
                if (onComplete != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete);
                }
                return;
            }

            try (FileReader reader = new FileReader(file)) {
                final List<Map<String, Object>> widgetTree = new Gson().fromJson(
                        reader, new TypeToken<List<Map<String, Object>>>() {}.getType());

                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (screen instanceof ViewGroup) {
                        ViewGroup vg = (ViewGroup) screen;
                        vg.removeAllViews();
                        if (widgetTree != null) {
                            for (Map<String, Object> nodeMap : widgetTree) {
                                buildViewTree(nodeMap, vg, engine, selector, dropZoneManager);
                            }
                        }
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading project", e);
                if (onComplete != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete);
                }
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Export – ZIP project content files only (no project ID wrapping).
    // Backup contains project files + metadata (name/desc) but NOT the ID.
    // -------------------------------------------------------------------------

    public boolean exportSingleProjectAsZip(String projectId, Uri outputUri) {
        File extProjDir = new File(FileUtil.getDragWebDir(), "projects/" + projectId);

        if (!extProjDir.exists()) return false;

        try (OutputStream fos = context.getContentResolver().openOutputStream(outputUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {

            // Add project files (skip project.meta — we write a clean version)
            addDirectoryToZip(zos, extProjDir, "", "project.meta");

            // Write clean metadata without the project ID
            String metaJson = makeCleanMetaJson(extProjDir);
            if (metaJson != null) {
                zos.putNextEntry(new ZipEntry("project.meta"));
                byte[] metaBytes = metaJson.getBytes(StandardCharsets.UTF_8);
                zos.write(metaBytes, 0, metaBytes.length);
                zos.closeEntry();
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to export single project zip: " + e.getMessage());
            return false;
        }
    }

    public boolean exportAllProjectsAsZip(Uri outputUri) {
        String extBase = FileUtil.getDragWebDir(context).getAbsolutePath() + "/projects";
        File extDir = new File(extBase);

        try (OutputStream fos = context.getContentResolver().openOutputStream(outputUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {

            if (extDir.exists()) {
                File[] dirs = FileUtil.listFiles(extDir);
                if (dirs != null) {
                    for (File d : dirs) {
                        if (!d.isDirectory()) continue;
                        String folderName = sanitizeDirName(d.getName());
                        // Add project files (skip project.meta — we write a clean version)
                        addDirectoryToZip(zos, d, folderName + "/", "project.meta");
                        // Write clean metadata without the project ID
                        String metaJson = makeCleanMetaJson(d);
                        if (metaJson != null) {
                            zos.putNextEntry(new ZipEntry(folderName + "/project.meta"));
                            byte[] metaBytes = metaJson.getBytes(StandardCharsets.UTF_8);
                            zos.write(metaBytes, 0, metaBytes.length);
                            zos.closeEntry();
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

    /** Read project.meta and return JSON stripped of the `id` field */
    private String makeCleanMetaJson(File projDir) {
        File metaFile = new File(projDir, "project.meta");
        if (!metaFile.exists()) return null;
        try {
            String raw = FileUtil.readFile(metaFile.getAbsolutePath());
            if (raw == null || raw.isEmpty()) return null;
            Map<String, String> meta = gson.fromJson(raw, new TypeToken<Map<String, String>>(){}.getType());
            if (meta == null) return null;
            meta.remove("id");
            return gson.toJson(meta);
        } catch (Exception e) {
            return null;
        }
    }

    /** Make a safe directory name from a project ID for all-projects backup */
    private String sanitizeDirName(String projId) {
        // Replace non-alphanumeric characters, keep readable
        return projId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    // -------------------------------------------------------------------------
    // Import – always creates fresh projects with new IDs.
    // Extracts backup content directly to new project directories.
    // -------------------------------------------------------------------------

    public ImportResult importProjectsFromZip(Uri zipUri) {
        ImportResult result = new ImportResult();
        String extProjectsPath = new File(FileUtil.getDragWebDir(context), "projects").getAbsolutePath();

        // ── First pass: determine project grouping ──
        boolean hasRootLevelFiles = false;
        Set<String> topLevelDirs = new HashSet<>();
        try (InputStream fis = context.getContentResolver().openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                int slash = name.indexOf('/');
                if (slash > 0) {
                    String root = name.substring(0, slash);
                    topLevelDirs.add(root);
                } else {
                    // Root-level file (no slash) → single-project backup
                    hasRootLevelFiles = true;
                }
                zis.closeEntry();
            }
        } catch (Exception ex) {
            result.success = false;
            result.message = "Failed to scan zip: " + ex.getMessage();
            Log.e(TAG, "Failed to scan zip", ex);
            return result;
        }

        Set<String> projectRoots = new HashSet<>();
        if (hasRootLevelFiles) {
            // Files at ZIP root → single project, ignore subdirectory prefixes
            projectRoots.add("");
        } else {
            // No root files → each top-level directory is a separate project
            for (String dir : topLevelDirs) {
                if (!dir.startsWith(".")) projectRoots.add(dir);
            }
            if (projectRoots.isEmpty()) projectRoots.add("");
        }

        // ── Second pass: extract each project with a fresh ID ──
        File extRoot = new File(extProjectsPath);
        if (!extRoot.exists()) extRoot.mkdirs();

        try {
            String externalCanonical = extRoot.getCanonicalPath();
            Set<String> usedInImport = new HashSet<>();

            for (String root : projectRoots) {
                String newId = generateProjectId(context, usedInImport);
                usedInImport.add(newId);
                File extProjDir = new File(extProjectsPath, newId);
                if (!extProjDir.exists()) extProjDir.mkdirs();

                String prefix = root.isEmpty() ? "" : root + "/";
                Map<String, String> backupMeta = new HashMap<>();

                try (InputStream fis = context.getContentResolver().openInputStream(zipUri);
                     ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {
                    ZipEntry e;
                    while ((e = zis.getNextEntry()) != null) {
                        String ename = e.getName();
                        if (!ename.startsWith(prefix)) { zis.closeEntry(); continue; }
                        String relative = prefix.isEmpty() ? ename : ename.substring(prefix.length());
                        if (relative.isEmpty()) { zis.closeEntry(); continue; }

                        // Capture metadata but don't extract as a file
                        if (relative.equals("project.meta")) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buf = new byte[4096]; int len;
                            while ((len = zis.read(buf)) > 0) baos.write(buf, 0, len);
                            String metaStr = baos.toString("UTF-8");
                            try {
                                Map<String, String> m = gson.fromJson(metaStr,
                                        new TypeToken<Map<String, String>>() {}.getType());
                                if (m != null) backupMeta.putAll(m);
                            } catch (Exception ignored) {}
                            zis.closeEntry();
                            continue;
                        }

                        // Write file into target project directory
                        File target = new File(extProjDir, relative);
                        if (!target.getCanonicalPath().startsWith(externalCanonical)) {
                            Log.w(TAG, "Zip path traversal attempt: " + ename);
                            zis.closeEntry();
                            continue;
                        }

                        if (e.isDirectory()) {
                            target.mkdirs();
                        } else {
                            File parent = target.getParentFile();
                            if (parent != null && !parent.exists()) parent.mkdirs();
                            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(target))) {
                                byte[] buf = new byte[4096]; int len;
                                while ((len = zis.read(buf)) > 0) bos.write(buf, 0, len);
                            }
                        }
                        zis.closeEntry();
                    }
                }

                // Write metadata file with fresh project ID
                backupMeta.put("id", newId);
                if (!backupMeta.containsKey("name")) backupMeta.put("name", newId);
                if (!backupMeta.containsKey("description")) backupMeta.put("description", "Imported project");
                FileUtil.writeFile(
                        new File(extProjDir, "project.meta").getAbsolutePath(),
                        gson.toJson(backupMeta));

                // Save layout file copies
                File layoutFile = new File(extProjDir, "layout.json");
                if (layoutFile.exists()) {
                    try {
                        String json = FileUtil.readFile(layoutFile.getAbsolutePath());
                        if (json != null) {
                            FileUtil.writeFile(
                                    new File(extProjDir, newId + ".json").getAbsolutePath(), json);
                            FileUtil.writeFile(
                                    new File(new File(extProjectsPath), newId + ".json").getAbsolutePath(), json);
                        }
                    } catch (Exception ignored) {}
                }

                result.importedProjectIds.add(newId);
            }

            result.success = true;
            result.message = "Imported " + result.importedProjectIds.size() + " project(s)";
        } catch (Exception e) {
            result.success = false;
            result.message = e.getMessage();
            Log.e(TAG, "Failed to import projects zip", e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Storage discovery
    // -------------------------------------------------------------------------

    public List<Map<String, String>> loadAllProjectsFromExternal() {
        List<Map<String, String>> projects = new ArrayList<>();
        try {
            File projectsDir = new File(FileUtil.getDragWebDir(context), "projects");
            if (projectsDir.exists() && projectsDir.isDirectory()) {
                File[] dirs = FileUtil.listFiles(projectsDir);
                if (dirs != null) {
                    for (File dir : dirs) {
                        if (!dir.isDirectory()) continue;
                        File layoutFile = new File(dir, "layout.json");
                        if (!layoutFile.exists()) {
                            layoutFile = new File(dir, dir.getName() + ".json");
                        }
                        if (!layoutFile.exists()) {
                            layoutFile = new File(projectsDir, dir.getName() + ".json");
                        }
                        if (!layoutFile.exists()) continue;

                        Map<String, String> project = new HashMap<>();
                        project.put("name", dir.getName());
                        project.put("path", layoutFile.getAbsolutePath());
                        projects.add(project);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not scan projects: " + e.getMessage());
        }
        return projects;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private File tryLoadFromExternal(String projectId, File internalDir) {
        try {
            // Try new internal-mirrored path first
            String extNewPath = FileUtil.getDragWebDir().getAbsolutePath() + "/projects/" + projectId + "/" + projectId + ".json";
            File extNew = new File(extNewPath);
            if (extNew.exists()) {
                return copyToInternal(extNew, internalDir, projectId + ".json");
            }

            // Fall back to legacy layout.json
            String extLegacyPath = FileUtil.getDragWebDir().getAbsolutePath() + "/projects/" + projectId + "/layout.json";
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

    private void addDirectoryToZip(ZipOutputStream zos, File dir, String prefix, String... skipFiles) throws IOException {
        Set<String> skip = skipFiles.length > 0 ? new HashSet<>(Arrays.asList(skipFiles)) : new HashSet<>();
        File[] files = FileUtil.listFiles(dir);
        if (files == null) return;
        for (File file : files) {
            if (skip.contains(file.getName())) continue;
            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, prefix + file.getName() + "/", skipFiles);
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

            if (nodeMap.containsKey("children")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get("children");

                ViewGroup newParent;
                if (newView instanceof ViewGroup) {
                    newParent = (ViewGroup) newView;
                } else {
                    newParent = parent;
                }

                for (Map<String, Object> childMap : children) {
                    buildViewTree(childMap, newParent, engine, selector, dropZoneManager);
                }
            }

            android.view.ViewGroup.LayoutParams params = engine.generateLayoutParams(newView);
            if (params != null) {
                parent.addView(newView, params);
            }
        }
    }

    // ──────────────────────────────────────────────
    // Embedding preset / font helpers
    // ──────────────────────────────────────────────

    private Map<String, Object> loadPreset(String assetPath) {
        try {
            AssetManager am = context.getAssets();
            InputStream is = am.open(assetPath);
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            String json = s.hasNext() ? s.next() : "";
            is.close();
            return gson.fromJson(json, Map.class);
        } catch (Exception e) {
            Log.e(TAG, "Error loading preset: " + assetPath, e);
            return null;
        }
    }

    public void addEmbeddingProject(String externalCss, String jsCode, String extraCssPath, String assetCss) {
        String projectId = generateProjectId(context);
        File extProjectDir = new File(FileUtil.getDragWebDir(), "projects/" + projectId);
        extProjectDir.mkdirs();

        Map<String, Object> preset = loadPreset("presets/embedding.json");
        if (preset != null) {
            String json = gson.toJson(preset);
            File layoutFile = new File(extProjectDir, "layout.json");
            try (FileWriter fw = new FileWriter(layoutFile)) {
                fw.write(json);
            } catch (IOException e) {
                Log.e(TAG, "Error writing layout.json", e);
            }
        }

        // Write external CSS
        if (externalCss != null && !externalCss.isEmpty()) {
            File cssFile = new File(extProjectDir, "external.css");
            try (FileWriter fw = new FileWriter(cssFile)) {
                fw.write(externalCss);
            } catch (IOException e) {
                Log.e(TAG, "Error writing external.css", e);
            }
        }

        // Write extra CSS
        if (extraCssPath != null && !extraCssPath.isEmpty()) {
            File extraCssFile = new File(extProjectDir, "extra.css");
            try (FileWriter fw = new FileWriter(extraCssFile)) {
                fw.write(extraCssPath);
            } catch (IOException e) {
                Log.e(TAG, "Error writing extra.css", e);
            }
        }

        // Write asset CSS
        if (assetCss != null && !assetCss.isEmpty()) {
            File assetCssFile = new File(extProjectDir, "asset.css");
            try (FileWriter fw = new FileWriter(assetCssFile)) {
                fw.write(assetCss);
            } catch (IOException e) {
                Log.e(TAG, "Error writing asset.css", e);
            }
        }

        // Write JS
        if (jsCode != null && !jsCode.isEmpty()) {
            File jsFile = new File(extProjectDir, "functions.js");
            try (FileWriter fw = new FileWriter(jsFile)) {
                fw.write(jsCode);
            } catch (IOException e) {
                Log.e(TAG, "Error writing functions.js", e);
            }
        }
    }
}
