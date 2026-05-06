package sketchweb.gl.dragweb;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Project state. Persisted as JSON at
 * <code>/.dragweb/projects/&lt;name&gt;/project.json</code>.
 *
 * <p>Per the task spec, the simplest possible shape would be:
 * <pre>{ "blocks": [] }</pre>
 * We extend that with a list of pages so multi-page websites are first-class
 * (the spec calls out generating index.html, about.html, contact.html).
 */
public class DragWebProject implements Serializable {

    @SerializedName("name")
    public String name = "Untitled";

    @SerializedName("title")
    public String title = "";

    @SerializedName("pages")
    public List<String> pages = new ArrayList<>();

    @SerializedName("blocks")
    public List<BlockInstance> blocks = new ArrayList<>();

    /** Class-level CSS rules applied to all pages, keyed by class/id selector. */
    @SerializedName("globalCss")
    public Map<String, String> globalCss = new LinkedHashMap<>();

    public DragWebProject() {}

    public DragWebProject(String name) {
        this.name = name;
        this.title = name;
        this.pages.add("index.html");
        this.pages.add("about.html");
        this.pages.add("contact.html");
    }

    public List<BlockInstance> blocksForPage(String page) {
        List<BlockInstance> out = new ArrayList<>();
        for (BlockInstance b : blocks) {
            if (page.equals(b.page == null ? "index.html" : b.page)) out.add(b);
        }
        return out;
    }
}
