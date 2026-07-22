package sketchweb.gl;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which external animation libraries and local keyframe presets the project depends on.
 */
public final class AnimationLibraryManager {

    public static final String ANIMATE_CSS_ID = "animate.css";
    public static final String ANIMATE_CSS_HREF = "https://cdnjs.cloudflare.com/ajax/libs/animate.css/4.1.1/animate.min.css";

    private final Context context;
    private final String projectId;
    private final Set<String> enabled = new LinkedHashSet<>();

    public AnimationLibraryManager(Context context, String projectId) {
        this.context = context;
        this.projectId = projectId;
        load();
    }

    public List<String> allLocalAnimations() {
        return new ArrayList<>(AnimationLibrary.getAllAnimations());
    }

    public Set<String> enabledIds() {
        return Collections.unmodifiableSet(enabled);
    }

    public boolean isEnabled(String id) {
        return enabled.contains(id);
    }

    public void enable(String id) {
        if (id == null) return;
        if (id.equals(ANIMATE_CSS_ID) || AnimationLibrary.getAllAnimations().contains(id)) {
            enabled.add(id);
            persist();
        }
    }

    public void disable(String id) {
        if (id == null) return;
        if (enabled.remove(id)) persist();
    }

    public void toggle(String id) {
        if (isEnabled(id)) disable(id);
        else enable(id);
    }

    public String generateHtmlIncludes() {
        if (enabled.contains(ANIMATE_CSS_ID)) {
            return "  <link rel=\"stylesheet\" href=\"" + ANIMATE_CSS_HREF + "\">\n";
        }
        return "";
    }

    public String generateLocalKeyframesCss(String indent) {
        StringBuilder sb = new StringBuilder();
        for (String id : enabled) {
            if (id.equals(ANIMATE_CSS_ID)) continue;
            String body = AnimationLibrary.getKeyframeBody(id);
            if (body != null) {
                sb.append(indent).append("@keyframes ").append(id).append(" { ")
                  .append(body).append(" }\n");
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------

    File configFile() {
        File dir = new File(android.os.Environment.getExternalStorageDirectory(), ".dragweb/projects/" + projectId);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "animations.json");
    }

    private void load() {
        File f = configFile();
        if (!f.exists()) return;
        String body = FileUtil.readFile(f.getAbsolutePath());
        if (body == null) return;
        for (String raw : body.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            // Case-sensitive for animation names since CSS keyframes are case-sensitive
            if (line.equals(ANIMATE_CSS_ID) || AnimationLibrary.getAllAnimations().contains(line)) {
                enabled.add(line);
            }
        }
    }

    private void persist() {
        File f = configFile();
        StringBuilder sb = new StringBuilder();
        sb.append("# DragWeb animation-library configuration. One animation id per line.\n");
        for (String id : enabled) sb.append(id).append('\n');
        FileUtil.writeFile(f.getAbsolutePath(), sb.toString());
    }
}
