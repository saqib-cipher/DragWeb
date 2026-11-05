package glab.dragweb.core;

import android.content.Context;
import org.json.JSONArray;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import glab.dragweb.codegen.HtmlCode2Block;
import glab.dragweb.logic.BlockBean;

/**
 * Native Rust & Java Code2Block Engine for DragWeb.
 * Backed by libdragweb_native.so, loading specs dynamically from assets/blocks.json (zero hardcoding).
 */
public class BlocksLoader {

    private static boolean isLoaded = false;
    private static String loadError = null;

    private static native String nativeGenerateHtmlBlocks(String htmlCode, String blocksJson);

    public static synchronized void init() {
        if (isLoaded) return;
        try {
            System.loadLibrary("dragweb_native");
            isLoaded = true;
        } catch (Throwable t) {
            isLoaded = false;
            loadError = t.getMessage();
        }
    }

    public static boolean isLibraryLoaded() {
        if (!isLoaded) {
            init();
        }
        return isLoaded;
    }

    public static String getLoadError() {
        return loadError != null ? loadError : "Unknown error";
    }

    public static HtmlCode2Block.BlockResult convertHtmlCode(Context context, String htmlCode) {
        if (htmlCode == null || htmlCode.trim().isEmpty()) {
            return new HtmlCode2Block.BlockResult(new ArrayList<>());
        }

        // Fast path: Rust native engine
        if (isLibraryLoaded()) {
            try {
                String blocksJson = loadBlocksJsonFromAssets(context);
                String jsonResult = nativeGenerateHtmlBlocks(htmlCode, blocksJson);
                if (jsonResult != null && !jsonResult.trim().isEmpty() && !jsonResult.contains("\"Error\":")) {
                    return parseJsonToBlockResult(jsonResult);
                }
            } catch (Throwable ignored) {
                // Fall back to Java engine if native call fails
            }
        }

        // Seamless fallback to pure Java engine
        return HtmlCode2Block.convertHtmlCodeInternal(context, htmlCode);
    }

    public static ArrayList<BlockBean> generateHtmlBlocksFromCode(Context context, String htmlCode) {
        HtmlCode2Block.BlockResult res = convertHtmlCode(context, htmlCode);
        return res.blocks;
    }

    public static String generateHtmlFromBlocks(Context context, ArrayList<BlockBean> blocks) {
        return HtmlCode2Block.generateHtmlFromBlocks(context, blocks);
    }

    public static HtmlCode2Block.BlockResult parseJsonToBlockResult(String jsonResult) {
        return HtmlCode2Block.parseJsonToBlockResult(jsonResult);
    }

    public static ArrayList<BlockBean> parseJsonArrayToBlocks(JSONArray jsonArray) throws Exception {
        return HtmlCode2Block.parseJsonArrayToBlocks(jsonArray);
    }

    private static String loadBlocksJsonFromAssets(Context context) {
        if (context == null) return "";
        try (InputStream is = context.getAssets().open("blocks.json")) {
            byte[] buf = new byte[is.available()];
            int read = is.read(buf);
            if (read > 0) {
                return new String(buf, 0, read, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}
        return "";
    }
}
