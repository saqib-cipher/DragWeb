package glab.dragweb.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

import glab.dragweb.codegen.HtmlCode2Block;
import glab.dragweb.logic.BlockBean;
import glab.dragweb.logic.BlockPalette;

/**
 * Pure Rust Code2Block & Palette Engine for DragWeb.
 * Backed by libdragweb_native.so, matching Sketchware DayGreen's BlocksLoader architecture.
 */
public class BlocksLoader {

    private static boolean isLoaded = false;
    private static String loadError = null;

    private static native String nativeLoadBlocksCollection();
    private static native String nativeGenerateHtmlBlocks(String htmlCode, String blocksJson);
    private static native String nativeGenerateCssBlocks(String cssCode, String blocksJson);
    private static native String nativeGenerateJsBlocks(String jsCode, String blocksJson);
    private static native String nativeGenerateHtmlFromBlocks(String blocksJson);

    public static final ArrayList<BlockPalette> HTML_PALETTES = new ArrayList<>();
    public static final ArrayList<BlockPalette> CSS_PALETTES = new ArrayList<>();
    public static final ArrayList<BlockPalette> JS_PALETTES = new ArrayList<>();
    public static final HashMap<String, BlockBean> ALL_INDEXED_BLOCKS = new HashMap<>();

    public static synchronized void init() {
        if (isLoaded) return;
        try {
            System.loadLibrary("dragweb_native");
            String collection = nativeLoadBlocksCollection();
            if (collection != null && !collection.isEmpty() && !collection.equals("{}")) {
                JSONObject root = new JSONObject(collection);
                String[] langs = {"html", "css", "js"};

                HTML_PALETTES.clear();
                CSS_PALETTES.clear();
                JS_PALETTES.clear();
                ALL_INDEXED_BLOCKS.clear();

                for (String langKey : langs) {
                    if (!root.has(langKey)) continue;
                    JSONArray palettes = root.getJSONArray(langKey);
                    for (int i = 0; i < palettes.length(); i++) {
                        JSONObject p = palettes.getJSONObject(i);
                        BlockPalette palette = new BlockPalette(
                                p.optString("name", "Palette"),
                                p.optInt("color", 0xFF607D8B)
                        );

                        JSONArray beans = p.optJSONArray("beans");
                        if (beans != null) {
                            for (int j = 0; j < beans.length(); j++) {
                                JSONObject b = beans.getJSONObject(j);
                                String rawType = b.optString("block_type", " ");
                                if (rawType.isEmpty() || "normal".equalsIgnoreCase(rawType)) {
                                    rawType = " ";
                                }
                                BlockBean blockBean = new BlockBean()
                                        .setOpCode(b.optString("op_code"))
                                        .setHeaderText(b.optString("header_text"))
                                        .setColor(b.optInt("color", palette.getColor()))
                                        .setType(rawType)
                                        .setCode(b.optString("code"))
                                        .setCategory(b.optString("category", palette.getName()))
                                        .setSpec(b.optString("spec"))
                                        .setSpec2(b.optString("spec2"))
                                        .setTokenizedSpec(toStringList(b.optJSONArray("tokenized_spec")))
                                        .setParameters(toStringList(b.optJSONArray("parameters")));

                                palette.blocks().add(blockBean);
                                ALL_INDEXED_BLOCKS.put(blockBean.opCode, blockBean);
                            }
                        }

                        if ("html".equalsIgnoreCase(langKey)) {
                            HTML_PALETTES.add(palette);
                        } else if ("css".equalsIgnoreCase(langKey)) {
                            CSS_PALETTES.add(palette);
                        } else {
                            JS_PALETTES.add(palette);
                        }
                    }
                }
            }
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

    public static ArrayList<BlockPalette> getPalettes(String lang) {
        if (!isLoaded) {
            init();
        }
        if (lang == null) return new ArrayList<>();
        return switch (lang.toLowerCase()) {
            case "html", "xml" -> HTML_PALETTES;
            case "css" -> CSS_PALETTES;
            case "js", "javascript" -> JS_PALETTES;
            default -> new ArrayList<>();
        };
    }

    public static HtmlCode2Block.BlockResult convertHtmlCode(Context context, String htmlCode) {
        if (htmlCode == null || htmlCode.trim().isEmpty()) {
            return new HtmlCode2Block.BlockResult(new ArrayList<>());
        }
        init();
        try {
            String blocksJson = loadBlocksJsonFromAssets(context);
            String jsonResult = nativeGenerateHtmlBlocks(htmlCode, blocksJson);
            if (jsonResult != null && !jsonResult.trim().isEmpty() && !jsonResult.contains("\"Error\":")) {
                return parseJsonToBlockResult(jsonResult);
            }
        } catch (Throwable ignored) {}
        return new HtmlCode2Block.BlockResult(new ArrayList<>());
    }

    public static ArrayList<BlockBean> generateHtmlBlocksFromCode(Context context, String htmlCode) {
        return convertHtmlCode(context, htmlCode).blocks;
    }

    public static HtmlCode2Block.BlockResult convertCssCode(Context context, String cssCode) {
        if (cssCode == null || cssCode.trim().isEmpty()) {
            return new HtmlCode2Block.BlockResult(new ArrayList<>());
        }
        init();
        try {
            String blocksJson = loadBlocksJsonFromAssets(context);
            String jsonResult = nativeGenerateCssBlocks(cssCode, blocksJson);
            if (jsonResult != null && !jsonResult.trim().isEmpty() && !jsonResult.contains("\"Error\":")) {
                return parseJsonToBlockResult(jsonResult);
            }
        } catch (Throwable ignored) {}
        return new HtmlCode2Block.BlockResult(new ArrayList<>());
    }

    public static ArrayList<BlockBean> generateCssBlocksFromCode(Context context, String cssCode) {
        return convertCssCode(context, cssCode).blocks;
    }

    public static HtmlCode2Block.BlockResult convertJsCode(Context context, String jsCode) {
        if (jsCode == null || jsCode.trim().isEmpty()) {
            return new HtmlCode2Block.BlockResult(new ArrayList<>());
        }
        init();
        try {
            String blocksJson = loadBlocksJsonFromAssets(context);
            String jsonResult = nativeGenerateJsBlocks(jsCode, blocksJson);
            if (jsonResult != null && !jsonResult.trim().isEmpty() && !jsonResult.contains("\"Error\":")) {
                return parseJsonToBlockResult(jsonResult);
            }
        } catch (Throwable ignored) {}
        return new HtmlCode2Block.BlockResult(new ArrayList<>());
    }

    public static ArrayList<BlockBean> generateJsBlocksFromCode(Context context, String jsCode) {
        return convertJsCode(context, jsCode).blocks;
    }

    public static String generateCssFromBlocks(Context context, ArrayList<BlockBean> blocks) {
        return HtmlCode2Block.generateCssFromBlocks(context, blocks);
    }

    public static String generateHtmlFromBlocks(Context context, ArrayList<BlockBean> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        if (isLibraryLoaded()) {
            try {
                JSONArray arr = serializeBeansToJson(blocks);
                String nativeHtml = nativeGenerateHtmlFromBlocks(arr.toString());
                if (nativeHtml != null) {
                    return nativeHtml;
                }
            } catch (Throwable ignored) {}
        }
        return HtmlCode2Block.generateHtmlFromBlocks(context, blocks);
    }

    private static JSONArray serializeBeansToJson(ArrayList<BlockBean> blocks) {
        JSONArray arr = new JSONArray();
        for (BlockBean b : blocks) {
            if (b == null) continue;
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", b.id != null ? b.id : "-1");
                obj.put("opCode", b.opCode != null ? b.opCode : "");
                obj.put("spec", b.spec != null ? b.spec : "");
                obj.put("spec2", b.spec2 != null ? b.spec2 : "");
                obj.put("type", b.type != null ? b.type : "normal");
                obj.put("category", b.category != null ? b.category : "");
                obj.put("color", b.color);
                obj.put("code", b.code != null ? b.code : "");
                obj.put("subStack1", b.subStack1);
                obj.put("subStack2", b.subStack2);
                obj.put("nextBlock", b.nextBlock);
                obj.put("stackIndex", b.stackIndex);
                obj.put("isConnectedToRoot", b.isConnectedToRoot);
                obj.put("x", b.x);
                obj.put("y", b.y);
                JSONArray params = new JSONArray();
                if (b.parameters != null) {
                    for (String p : b.parameters) {
                        params.put(p != null ? p : "");
                    }
                }
                obj.put("parameters", params);
                JSONArray pTypes = new JSONArray();
                if (b.paramTypes != null) {
                    for (String pt : b.paramTypes) {
                        pTypes.put(pt != null ? pt : "");
                    }
                }
                obj.put("paramTypes", pTypes);
                arr.put(obj);
            } catch (Exception ignored) {}
        }
        return arr;
    }

    public static HtmlCode2Block.BlockResult parseJsonToBlockResult(String jsonResult) {
        return HtmlCode2Block.parseJsonToBlockResult(jsonResult);
    }

    public static ArrayList<BlockBean> parseJsonArrayToBlocks(JSONArray jsonArray) throws Exception {
        return HtmlCode2Block.parseJsonArrayToBlocks(jsonArray);
    }

    public static ArrayList<String> toStringList(JSONArray arr) {
        ArrayList<String> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.optString(i, ""));
        }
        return list;
    }

    private static String loadBlocksJsonFromAssets(Context context) {
        if (context == null) return "";
        try {
            org.json.JSONArray merged = new org.json.JSONArray();
            try (InputStream is = context.getAssets().open("blocks.json")) {
                String raw = readFullyUtf8(is);
                if (!raw.trim().isEmpty()) {
                    org.json.JSONArray builtIn = new org.json.JSONArray(raw);
                    for (int i = 0; i < builtIn.length(); i++) {
                        merged.put(builtIn.get(i));
                    }
                }
            }
            java.io.File customFile = new java.io.File(glab.dragweb.util.CustomStorageUtil.getCustomDir(context), "blocks.json");
            if (customFile.exists() && customFile.length() > 0) {
                String customRaw = glab.dragweb.util.FileUtil.readFile(customFile.getAbsolutePath());
                if (customRaw != null && !customRaw.trim().isEmpty()) {
                    org.json.JSONArray customArr = new org.json.JSONArray(customRaw);
                    for (int i = 0; i < customArr.length(); i++) {
                        merged.put(customArr.get(i));
                    }
                }
            }
            return merged.toString();
        } catch (Exception ignored) {}
        return "";
    }

    private static String readFullyUtf8(InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }
}
