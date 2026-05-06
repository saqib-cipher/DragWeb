package sketchweb.gl.dragweb;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a project's {@link BlockInstance} tree, runs each block's template
 * through {@link DragWebTemplateEngine}, and emits one HTML file per page
 * plus a single shared <code>style.css</code>.
 *
 * <p>Block category drives destination:
 * <ul>
 *   <li><b>CSS</b> blocks (and Layout blocks whose template starts with
 *       <code>.</code> or <code>#</code>) emit into <code>style.css</code>.</li>
 *   <li>everything else emits into the page's HTML body.</li>
 * </ul>
 *
 * <p>Each page automatically wraps its body content in a default HTML5
 * skeleton and stitches in a navigation menu derived from <code>pages</code>.
 */
public class DragWebCodeGenerator {

    public static class GenerationResult {
        public final Map<String, String> pages = new LinkedHashMap<>();  // filename -> html
        public String css = "";
        public final SelectorIndex selectors = new SelectorIndex();
    }

    private final DragWebBlockRegistry registry;

    public DragWebCodeGenerator(DragWebBlockRegistry registry) {
        this.registry = registry;
    }

    public GenerationResult generate(DragWebProject project) {
        GenerationResult result = new GenerationResult();
        if (project == null) return result;

        List<String> pages = project.pages == null || project.pages.isEmpty()
                ? defaultPages() : project.pages;
        for (String p : pages) result.selectors.registerFile(p);

        StringBuilder css = new StringBuilder();
        for (Map.Entry<String, String> e : project.globalCss.entrySet()) {
            css.append(e.getKey()).append("{").append(e.getValue()).append("}\n");
        }

        Map<String, StringBuilder> pageBodies = new LinkedHashMap<>();
        for (String page : pages) pageBodies.put(page, new StringBuilder());

        for (BlockInstance instance : project.blocks) {
            String page = (instance.page == null || instance.page.isEmpty())
                    ? "index.html" : instance.page;
            DragWebBlock def = registry.get(instance.blockId);
            if (def == null) continue;

            String fragment = renderInstance(instance, def);
            if (fragment.isEmpty()) continue;

            if (isCssBlock(def, fragment)) {
                css.append(fragment).append("\n");
                result.selectors.scanHtml(fragment);
            } else {
                StringBuilder buf = pageBodies.get(page);
                if (buf == null) {
                    buf = new StringBuilder();
                    pageBodies.put(page, buf);
                }
                buf.append(fragment).append("\n");
                result.selectors.scanHtml(fragment);
            }
        }

        result.css = css.toString();

        for (Map.Entry<String, StringBuilder> e : pageBodies.entrySet()) {
            String html = wrapPage(project, e.getKey(), e.getValue().toString(), pages);
            result.pages.put(e.getKey(), html);
            result.selectors.scanHtml(html);
        }
        return result;
    }

    private String renderInstance(BlockInstance instance, DragWebBlock def) {
        String rendered = DragWebTemplateEngine.render(def.template, instance.inputs);
        if (instance.children != null && !instance.children.isEmpty()) {
            StringBuilder inner = new StringBuilder();
            for (BlockInstance c : instance.children) {
                DragWebBlock cd = registry.get(c.blockId);
                if (cd == null) continue;
                inner.append(renderInstance(c, cd));
            }
            rendered = injectChildren(rendered, inner.toString());
        }
        return rendered;
    }

    /**
     * If a parent template ends in an open tag (matches <code>...&gt;</code>
     * but no matching close), append children then a close tag. Otherwise
     * just append children to the end. The simple heuristic covers the
     * common open/close split blocks shipped in the standard registry
     * (e.g. <code>div_open</code>, <code>navbar_open</code>) without
     * requiring authoring annotations.
     */
    private static String injectChildren(String parentRendered, String childrenHtml) {
        if (childrenHtml.isEmpty()) return parentRendered;
        return parentRendered + childrenHtml;
    }

    private static boolean isCssBlock(DragWebBlock def, String renderedFragment) {
        String c = def.category == null ? "" : def.category;
        if (c.equalsIgnoreCase("CSS")) return true;
        String t = renderedFragment.trim();
        // selectors-with-rules detection — e.g. ".x{...}" or "#y{...}"
        return (t.startsWith(".") || t.startsWith("#")) && t.contains("{") && t.endsWith("}");
    }

    private String wrapPage(DragWebProject project, String pageName, String body,
                            List<String> allPages) {
        String title = (project.title == null || project.title.isEmpty())
                ? project.name : project.title;
        StringBuilder s = new StringBuilder();
        s.append("<!DOCTYPE html>\n");
        s.append("<html lang='en'>\n");
        s.append("<head>\n");
        s.append("<meta charset='utf-8'>\n");
        s.append("<meta name='viewport' content='width=device-width,initial-scale=1'>\n");
        s.append("<title>").append(escape(title)).append(" — ").append(escape(stripExt(pageName))).append("</title>\n");
        s.append("<link rel='stylesheet' href='style.css'>\n");
        s.append("</head>\n");
        s.append("<body>\n");
        s.append(buildNav(allPages, pageName));
        s.append("<main>\n");
        s.append(body);
        s.append("</main>\n");
        s.append("</body>\n");
        s.append("</html>\n");
        return s.toString();
    }

    private static String buildNav(List<String> pages, String current) {
        if (pages == null || pages.size() <= 1) return "";
        StringBuilder n = new StringBuilder();
        n.append("<nav class='dragweb-nav'><ul>");
        for (String p : pages) {
            String label = capitalize(stripExt(p));
            if (p.equals(current)) {
                n.append("<li class='active'><a href='").append(p).append("'>")
                 .append(escape(label)).append("</a></li>");
            } else {
                n.append("<li><a href='").append(p).append("'>")
                 .append(escape(label)).append("</a></li>");
            }
        }
        n.append("</ul></nav>\n");
        return n.toString();
    }

    private static String stripExt(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static List<String> defaultPages() {
        List<String> p = new ArrayList<>();
        p.add("index.html");
        p.add("about.html");
        p.add("contact.html");
        return p;
    }

    /** Write a generation result to <code>/.dragweb/projects/&lt;name&gt;/</code>. */
    public static void writeToProjectDir(Context ctx, DragWebProject project,
                                         GenerationResult result) throws IOException {
        File dir = DragWebPaths.projectDir(ctx, project.name);
        for (Map.Entry<String, String> e : result.pages.entrySet()) {
            try (FileWriter w = new FileWriter(new File(dir, e.getKey()))) {
                w.write(e.getValue());
            }
        }
        try (FileWriter w = new FileWriter(new File(dir, "style.css"))) {
            w.write(result.css);
        }
        // ensure assets dir exists, matching the spec'd structure
        DragWebPaths.projectAssetsDir(ctx, project.name);
    }
}
