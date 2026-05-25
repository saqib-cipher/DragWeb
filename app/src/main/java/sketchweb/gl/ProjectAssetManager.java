package sketchweb.gl;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton to share harvested project assets (IDs, Classes, Tags) 
 * across activities (MainActivity, LogicBlockActivity).
 */
public class ProjectAssetManager {
    private static ProjectAssetManager instance;
    
    private List<String> ids = new ArrayList<>();
    private List<String> classes = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private ThemeManager themeManager;

    /** Listeners notified whenever the class list changes (for realtime chip sync). */
    private final java.util.List<Runnable> classListeners = new ArrayList<>();

    private ProjectAssetManager() {}

    public static synchronized ProjectAssetManager getInstance() {
        if (instance == null) instance = new ProjectAssetManager();
        return instance;
    }

    public void update(List<String> ids, List<String> classes, List<String> tags) {
        boolean classesChanged = !equalsList(this.classes, classes);
        this.ids = ids != null ? ids : new ArrayList<>();
        this.classes = classes != null ? classes : new ArrayList<>();
        this.tags = tags != null ? tags : new ArrayList<>();
        if (classesChanged) {
            // Notify outside the singleton lock; copy to avoid concurrent mod.
            for (Runnable r : new ArrayList<>(classListeners)) {
                try { r.run(); } catch (Throwable ignored) {}
            }
        }
    }

    public List<String> getIds() { return ids; }
    public List<String> getClasses() { return classes; }
    public List<String> getTags() { return tags; }

    /** Optionally share the project's ThemeManager (used by block color chip). */
    public void setThemeManager(ThemeManager tm) { this.themeManager = tm; }
    public ThemeManager getThemeManager() { return themeManager; }

    /** Register a callback fired whenever the class list changes. */
    public void addClassListListener(Runnable r) {
        if (r != null && !classListeners.contains(r)) classListeners.add(r);
    }
    public void removeClassListListener(Runnable r) {
        classListeners.remove(r);
    }

    private static boolean equalsList(List<String> a, List<String> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            String va = a.get(i), vb = b.get(i);
            if (va == null ? vb != null : !va.equals(vb)) return false;
        }
        return true;
    }
}
