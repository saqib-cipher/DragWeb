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

    private ProjectAssetManager() {}

    public static synchronized ProjectAssetManager getInstance() {
        if (instance == null) instance = new ProjectAssetManager();
        return instance;
    }

    public void update(List<String> ids, List<String> classes, List<String> tags) {
        this.ids = ids != null ? ids : new ArrayList<>();
        this.classes = classes != null ? classes : new ArrayList<>();
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public List<String> getIds() { return ids; }
    public List<String> getClasses() { return classes; }
    public List<String> getTags() { return tags; }
}
