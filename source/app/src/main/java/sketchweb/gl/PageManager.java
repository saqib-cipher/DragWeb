package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
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
        }
    }

    public void removePage(String pageName) {
        if ("index".equals(pageName)) return; // Can't remove index
        pages.remove(pageName);
        pageLayoutCache.remove(pageName);
        savePageList();
        // Delete layout files
        File layoutFile = getPageLayoutFile(pageName);
        if (layoutFile.exists()) {
            layoutFile.delete();
        }
        File internalFile = getInternalPageFile(pageName);
        if (internalFile.exists()) {
            internalFile.delete();
        }
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
            File oldInternal = getInternalPageFile(oldName);
            File newInternal = getInternalPageFile(newName);
            if (oldInternal.exists()) {
                oldInternal.renameTo(newInternal);
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

        // Try external storage first (primary storage)
        File file = getPageLayoutFile(pageName);
        if (file.exists()) {
            String json = FileUtil.readFile(file.getAbsolutePath());
            if (json != null && !json.isEmpty()) {
                pageLayoutCache.put(pageName, json);
                return json;
            }
        }

        // Try internal storage as fallback
        File internalFile = getInternalPageFile(pageName);
        if (internalFile.exists()) {
            String json = FileUtil.readFile(internalFile.getAbsolutePath());
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

        // Save to internal storage
        File internalDir = new File(context.getFilesDir(), "projects");
        if (!internalDir.exists()) internalDir.mkdirs();
        File internalFile = new File(internalDir, projectId + "_" + pageName + ".json");
        FileUtil.writeFile(internalFile.getAbsolutePath(), json);

        // Save to external storage
        File extFile = getPageLayoutFile(pageName);
        File extDir = extFile.getParentFile();
        if (!extDir.exists()) extDir.mkdirs();
        FileUtil.writeFile(extFile.getAbsolutePath(), json);
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
                // Save to internal storage
                File internalDir = new File(context.getFilesDir(), "projects");
                if (!internalDir.exists()) internalDir.mkdirs();
                File internalFile = new File(internalDir, projectId + "_" + pageName + ".json");
                FileUtil.writeFile(internalFile.getAbsolutePath(), json);

                // Save to external storage
                File extFile = getPageLayoutFile(pageName);
                File extDir = extFile.getParentFile();
                if (!extDir.exists()) extDir.mkdirs();
                FileUtil.writeFile(extFile.getAbsolutePath(), json);
            }
        }
    }

    private File getPageLayoutFile(String pageName) {
        String basePath = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/.dragweb/projects/" + projectId + "/pages";
        File pagesDir = new File(basePath);
        if (!pagesDir.exists()) pagesDir.mkdirs();
        return new File(pagesDir, pageName + ".json");
    }

    private File getInternalPageFile(String pageName) {
        File dir = new File(context.getFilesDir(), "projects");
        return new File(dir, projectId + "_" + pageName + ".json");
    }

    private void loadPageList() {
        pages.clear();

        // Try loading from pages metadata file
        String metaPath = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/.dragweb/projects/" + projectId + "/pages.json";
        File metaFile = new File(metaPath);
        if (metaFile.exists()) {
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
        String pagesPath = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/.dragweb/projects/" + projectId + "/pages";
        File pagesDir = new File(pagesPath);
        if (pagesDir.exists() && pagesDir.isDirectory()) {
            File[] pageFiles = pagesDir.listFiles();
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
            String metaPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId + "/pages.json";
            File metaDir = new File(metaPath).getParentFile();
            if (!metaDir.exists()) metaDir.mkdirs();
            FileUtil.writeFile(metaPath, new Gson().toJson(pages));
        } catch (Exception e) {
            Log.w("PageManager", "Could not save pages.json: " + e.getMessage());
        }
    }
}
