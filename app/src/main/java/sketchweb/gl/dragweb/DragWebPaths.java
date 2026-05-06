package sketchweb.gl.dragweb;

import android.content.Context;

import java.io.File;

/**
 * Resolves the spec'd <code>/.dragweb/</code> directory hierarchy onto the
 * device. Because the literal filesystem root is not writable on Android, the
 * tree is mirrored under the app's internal files directory.
 *
 * <pre>
 * /.dragweb/
 *  ├── custom/
 *  │    └── blocks.json
 *  └── projects/
 *       └── &lt;project_name&gt;/
 *            ├── project.json
 *            ├── index.html
 *            ├── style.css
 *            └── assets/
 * </pre>
 */
public final class DragWebPaths {

    public static final String ROOT_DIR_NAME = ".dragweb";
    public static final String CUSTOM_DIR_NAME = "custom";
    public static final String CUSTOM_BLOCKS_FILE = "blocks.json";
    public static final String PROJECTS_DIR_NAME = "projects";
    public static final String PROJECT_FILE = "project.json";
    public static final String ASSETS_DIR_NAME = "assets";

    private DragWebPaths() {}

    public static File root(Context ctx) {
        File f = new File(ctx.getFilesDir(), ROOT_DIR_NAME);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File customDir(Context ctx) {
        File f = new File(root(ctx), CUSTOM_DIR_NAME);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File customBlocksFile(Context ctx) {
        return new File(customDir(ctx), CUSTOM_BLOCKS_FILE);
    }

    public static File projectsDir(Context ctx) {
        File f = new File(root(ctx), PROJECTS_DIR_NAME);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File projectDir(Context ctx, String projectName) {
        File f = new File(projectsDir(ctx), sanitize(projectName));
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File projectFile(Context ctx, String projectName) {
        return new File(projectDir(ctx, projectName), PROJECT_FILE);
    }

    public static File projectAssetsDir(Context ctx, String projectName) {
        File f = new File(projectDir(ctx, projectName), ASSETS_DIR_NAME);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static String sanitize(String name) {
        if (name == null) return "untitled";
        String trimmed = name.trim().replaceAll("[^A-Za-z0-9_\\-]", "_");
        return trimmed.isEmpty() ? "untitled" : trimmed;
    }
}
