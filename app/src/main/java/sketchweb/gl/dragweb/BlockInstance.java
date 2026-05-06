package sketchweb.gl.dragweb;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A block placed onto the workspace. Holds a reference to the block's
 * definition id plus the user-supplied input values for its tokens.
 *
 * <p>Children allow nested blocks (e.g. a navbar containing menu links).
 * The destination decides which file the generated code lands in: HTML
 * blocks emit into a page; CSS blocks emit into <code>style.css</code>.
 */
public class BlockInstance implements Serializable {

    @SerializedName("uid")
    public String uid;

    @SerializedName("blockId")
    public String blockId;

    @SerializedName("inputs")
    public List<String> inputs = new ArrayList<>();

    @SerializedName("page")
    public String page = "index.html";

    @SerializedName("children")
    public List<BlockInstance> children = new ArrayList<>();

    @SerializedName("properties")
    public Map<String, String> properties = new HashMap<>();

    public BlockInstance() {
        this.uid = UUID.randomUUID().toString();
    }

    public BlockInstance(String blockId) {
        this();
        this.blockId = blockId;
    }

    public BlockInstance copy() {
        BlockInstance c = new BlockInstance();
        c.blockId = this.blockId;
        c.inputs = new ArrayList<>(this.inputs);
        c.page = this.page;
        c.properties = new HashMap<>(this.properties);
        c.children = new ArrayList<>();
        for (BlockInstance child : this.children) c.children.add(child.copy());
        return c;
    }
}
