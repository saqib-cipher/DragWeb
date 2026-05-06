package sketchweb.gl.dragweb;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the bundled standard blocks from <code>assets/dragweb_blocks.json</code>
 * and merges any user-defined custom blocks from
 * <code>/.dragweb/custom/blocks.json</code>. Custom blocks override standard
 * blocks of the same id so users can swap implementations without losing the
 * id.
 */
public class DragWebBlockRegistry {

    private static final String STANDARD_ASSET = "dragweb_blocks.json";

    private final Context context;
    private final Map<String, DragWebBlock> blocksById = new LinkedHashMap<>();

    public DragWebBlockRegistry(Context context) {
        this.context = context.getApplicationContext();
        reload();
    }

    public void reload() {
        blocksById.clear();
        for (DragWebBlock b : loadStandard(context)) blocksById.put(b.id, b);
        for (DragWebBlock b : loadCustom(context)) blocksById.put(b.id, b);
    }

    public DragWebBlock get(String id) {
        return blocksById.get(id);
    }

    public List<DragWebBlock> all() {
        return new ArrayList<>(blocksById.values());
    }

    public List<DragWebBlock> byCategory(String category) {
        List<DragWebBlock> out = new ArrayList<>();
        for (DragWebBlock b : blocksById.values()) {
            String c = b.category == null ? "HTML" : b.category;
            if (c.equalsIgnoreCase(category)) out.add(b);
        }
        return out;
    }

    public List<String> categories() {
        List<String> seen = new ArrayList<>();
        for (DragWebBlock b : blocksById.values()) {
            String c = b.category == null ? "HTML" : b.category;
            if (!seen.contains(c)) seen.add(c);
        }
        return seen;
    }

    /** Saves a list of custom blocks to <code>/.dragweb/custom/blocks.json</code>. */
    public void saveCustomBlocks(List<DragWebBlock> customBlocks) throws IOException {
        File f = DragWebPaths.customBlocksFile(context);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter w = new FileWriter(f)) {
            gson.toJson(customBlocks, w);
        }
        reload();
    }

    public void addOrUpdateCustomBlock(DragWebBlock block) throws IOException {
        List<DragWebBlock> existing = loadCustom(context);
        boolean replaced = false;
        for (int i = 0; i < existing.size(); i++) {
            if (existing.get(i).id.equals(block.id)) {
                existing.set(i, block);
                replaced = true;
                break;
            }
        }
        if (!replaced) existing.add(block);
        saveCustomBlocks(existing);
    }

    public void removeCustomBlock(String id) throws IOException {
        List<DragWebBlock> existing = loadCustom(context);
        existing.removeIf(b -> b.id.equals(id));
        saveCustomBlocks(existing);
    }

    public List<DragWebBlock> getCustomBlocks() {
        return loadCustom(context);
    }

    private static List<DragWebBlock> loadStandard(Context ctx) {
        try (InputStream is = ctx.getAssets().open(STANDARD_ASSET);
             Reader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Type t = new TypeToken<List<DragWebBlock>>() {}.getType();
            List<DragWebBlock> blocks = new Gson().fromJson(r, t);
            return blocks == null ? new ArrayList<>() : blocks;
        } catch (IOException | JsonSyntaxException e) {
            return new ArrayList<>();
        }
    }

    private static List<DragWebBlock> loadCustom(Context ctx) {
        File f = DragWebPaths.customBlocksFile(ctx);
        if (!f.exists()) return new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
            Type t = new TypeToken<List<DragWebBlock>>() {}.getType();
            List<DragWebBlock> blocks = new Gson().fromJson(r, t);
            return blocks == null ? new ArrayList<>() : blocks;
        } catch (IOException | JsonSyntaxException e) {
            return new ArrayList<>();
        }
    }
}
