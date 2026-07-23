package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectCodeGenerator {

    public static ThemeManager getProjectThemeManager(String projectId) {
        ThemeManager tm = new ThemeManager();
        File themeFile = new File(Environment.getExternalStorageDirectory(), ".dragweb/projects/" + projectId + "/theme.json");
        if (themeFile.exists()) {
            try {
                String json = FileUtil.readFile(themeFile.getAbsolutePath());
                if (json != null && !json.trim().isEmpty()) {
                    tm.fromJson(json);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return tm;
    }

    public static void generateAndSaveAssets(Context context, String projectId, String pageName) {
        if (projectId == null || projectId.isEmpty()) projectId = DesignDataManager.currentProjectId;
        if (projectId == null || projectId.isEmpty()) projectId = DesignActivity.getScId();
        if (projectId == null || projectId.isEmpty()) return;

        if (pageName == null || pageName.isEmpty()) pageName = DesignDataManager.currentPageName;
        if (pageName == null || pageName.isEmpty()) pageName = "index";

        String cleanPage = DesignDataManager.getCleanPageName(pageName);
        File assetsDir = new File(Environment.getExternalStorageDirectory(), ".dragweb/projects/" + projectId + "/assets");
        File cssDir = new File(assetsDir, "css");
        File jsDir = new File(assetsDir, "js");
        cssDir.mkdirs();
        jsDir.mkdirs();

        ThemeManager themeManager = getProjectThemeManager(projectId);

        // 1. Generate & save theme.css
        {
            String themeCss = themeManager.generateGlobalCss();
            File themeFile = new File(cssDir, "theme.css");
            FileUtil.writeFile(themeFile.getAbsolutePath(), themeCss != null ? themeCss : "");
        }

        // 2. Compile & save JS (always to script.js — HTML always links js/script.js)
        {
            String compiledJs = compileJsForFile(context, projectId, cleanPage);
            File jsTargetFile = new File(jsDir, "script.js");
            FileUtil.writeFile(jsTargetFile.getAbsolutePath(), compiledJs);
        }

        // 3. Compile & save CSS (always to style.css — HTML always links css/style.css)
        {
            String compiledCss = compileCssForFile(context, projectId, cleanPage);
            StringBuilder finalCss = new StringBuilder();
            finalCss.append("@import url(\"theme.css\");\n\n");
            if (compiledCss != null && !compiledCss.trim().isEmpty()) {
                finalCss.append(compiledCss).append("\n");
            }
            File cssTargetFile = new File(cssDir, "style.css");
            FileUtil.writeFile(cssTargetFile.getAbsolutePath(), finalCss.toString());
        }
    }

    public static String compileJsForFile(Context context, String projectId, String cleanPage) {
        BlockCodeCompiler compiler = new BlockCodeCompiler(context, projectId);
        StringBuilder sb = new StringBuilder();

        // Collect blocks from the current page AND the global JS script page
        String jsPage = DesignDataManager.getCleanPageName("js/script.js");
        String[] pagesToCompile = (jsPage.equals(cleanPage)) 
            ? new String[]{cleanPage} 
            : new String[]{jsPage, cleanPage};

        for (String pageKey : pagesToCompile) {
            DesignDataManager.loadSavedLogic(context, projectId, pageKey);
            HashMap<String, ArrayList<BlockBean>> blocksMap = DesignDataManager.mapBlocks.get(pageKey);
            if (blocksMap == null || blocksMap.isEmpty()) continue;

            // 1. Compile MoreBlock function definitions
            for (Map.Entry<String, ArrayList<BlockBean>> entry : blocksMap.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("func_") || key.contains("moreBlock")) {
                    String funcName = key;
                    if (key.endsWith("_moreBlock")) {
                        funcName = key.substring(0, key.indexOf("_moreBlock"));
                    } else if (key.startsWith("func_")) {
                        funcName = key.substring(5);
                    }

                    String spec = "Define " + funcName;
                    ArrayList<DesignDataManager.MoreBlockData> funcs = DesignDataManager.getProjectMoreBlocks(projectId);
                    if (funcs != null) {
                        for (DesignDataManager.MoreBlockData mb : funcs) {
                            if (mb.name.equals(funcName)) {
                                spec = mb.spec != null ? mb.spec : mb.name;
                                break;
                            }
                        }
                    }
                    
                    ArrayList<String> paramNames = new ArrayList<>();
                    ArrayList<String> tokens = StringUtil.tokenize(spec);
                    for (String tok : tokens) {
                        if (tok.startsWith("%")) {
                            String pName = tok;
                            if (tok.startsWith("%b.") || tok.startsWith("%d.") || tok.startsWith("%s.")) {
                                pName = tok.substring(3);
                            } else if (tok.length() > 2) {
                                pName = tok.substring(2);
                            }
                            if (pName.contains(".")) {
                                pName = pName.substring(0, pName.indexOf('.'));
                            }
                            paramNames.add(pName);
                        }
                    }
                    StringBuilder paramsSb = new StringBuilder();
                    for (int i = 0; i < paramNames.size(); i++) {
                        if (i > 0) paramsSb.append(", ");
                        paramsSb.append(paramNames.get(i));
                    }

                    String bodyCode = compiler.getSource(0, entry.getValue());
                    if (bodyCode != null && !bodyCode.trim().isEmpty()) {
                        if (bodyCode.startsWith("function ")) {
                            sb.append(bodyCode).append("\n\n");
                        } else {
                            sb.append("function ").append(funcName).append("(").append(paramsSb.toString()).append(") {\n");
                            for (String line : bodyCode.split("\n")) {
                                sb.append("  ").append(line).append("\n");
                            }
                            sb.append("}\n\n");
                        }
                    }
                }
            }

            // 2. Compile event statement blocks
            for (Map.Entry<String, ArrayList<BlockBean>> entry : blocksMap.entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith("func_") && !key.contains("moreBlock")) {
                    String code = compiler.getSource(0, entry.getValue());
                    if (code != null && !code.trim().isEmpty()) {
                        sb.append(code).append("\n\n");
                    }
                }
            }
        }

        return sb.toString().trim();
    }

    public static String compileCssForFile(Context context, String projectId, String cleanPage) {
        StringBuilder cssSb = new StringBuilder();

        // Collect blocks from the current page AND the global CSS stylesheet page
        String cssPage = DesignDataManager.getCleanPageName("css/style.css");
        String[] pagesToCompile = (cssPage.equals(cleanPage)) 
            ? new String[]{cleanPage} 
            : new String[]{cssPage, cleanPage};

        for (String pageKey : pagesToCompile) {
            // Compile logic block styles using BlockCodeCompiler (blocks.json definitions)
            // This uses the proper "code" templates (e.g. "width: %1$s;\n") from blocks.json
            // Only include blocks with CSS categories to avoid JS event code in style.css
            try {
                DesignDataManager.loadSavedLogic(context, projectId, pageKey);
                Map<String, ArrayList<BlockBean>> blocksMap = DesignDataManager.getAllBlocks(pageKey);
                if (blocksMap != null) {
                    BlockCodeCompiler compiler = new BlockCodeCompiler(context, projectId);
                    for (Map.Entry<String, ArrayList<BlockBean>> entry : blocksMap.entrySet()) {
                        ArrayList<BlockBean> cssBlocks = new ArrayList<>();
                        for (BlockBean b : entry.getValue()) {
                            if (b != null && b.category != null && (b.category.equals("css") || b.category.startsWith("css_") || b.category.equals("responsive") || b.category.equals("comment") || b.category.equals("animation"))) {
                                cssBlocks.add(b);
                            }
                        }
                        if (cssBlocks.isEmpty()) continue;
                        String code = compiler.getSource(0, cssBlocks);
                        if (code != null && !code.trim().isEmpty()) {
                            cssSb.append(code).append("\n\n");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 2. Compile visual layout styles if inline styles are toggled off
        ThemeManager themeManager = getProjectThemeManager(projectId);
        if (themeManager != null && !themeManager.isUseInlineStyles()) {
            File layoutFile = new File(Environment.getExternalStorageDirectory(), ".dragweb/projects/" + projectId + "/pages/" + cleanPage + ".json");
            if (layoutFile.exists()) {
                try {
                    String layoutJson = FileUtil.readFile(layoutFile.getAbsolutePath());
                    if (layoutJson != null && !layoutJson.trim().isEmpty()) {
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
                        ArrayList<Map<String, Object>> widgetTree = new com.google.gson.Gson().fromJson(layoutJson, listType);
                        if (widgetTree != null) {
                            collectStylesFromTree(widgetTree, cssSb);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return cssSb.toString().trim();
    }

    public static void collectStylesFromTree(List<Map<String, Object>> tree, StringBuilder cssBuilder) {
        if (tree == null) return;
        for (Map<String, Object> node : tree) {
            collectStylesFromNode(node, cssBuilder);
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectStylesFromNode(Map<String, Object> node, StringBuilder cssBuilder) {
        if (node == null) return;
        String tag = node.containsKey("tag") ? String.valueOf(node.get("tag")) : "div";
        Map<String, Object> function = node.containsKey("function") ? (Map<String, Object>) node.get("function") : null;
        if (function != null) {
            Map<String, Object> style = function.containsKey("style") ? (Map<String, Object>) function.get("style") : null;
            if (style != null && !style.isEmpty()) {
                String selector = "";
                if (function.containsKey("id") && function.get("id") != null && !String.valueOf(function.get("id")).trim().isEmpty()) {
                    selector = "#" + String.valueOf(function.get("id")).trim();
                } else if (function.containsKey("class") && function.get("class") != null && !String.valueOf(function.get("class")).trim().isEmpty()) {
                    selector = "." + String.valueOf(function.get("class")).trim().split("\\s+")[0];
                } else {
                    selector = tag;
                }
                
                cssBuilder.append(selector).append(" {\n");
                for (Map.Entry<String, Object> entry : style.entrySet()) {
                    String cssKey = camelToKebab(entry.getKey());
                    cssBuilder.append("  ").append(cssKey).append(": ").append(entry.getValue()).append(";\n");
                }
                cssBuilder.append("}\n\n");
            }

            if (node.containsKey("children")) {
                Object childrenObj = node.get("children");
                if (childrenObj instanceof List) {
                    collectStylesFromNodeList((List<Map<String, Object>>) childrenObj, cssBuilder);
                }
            }
        }
    }

    private static void collectStylesFromNodeList(List<Map<String, Object>> list, StringBuilder cssBuilder) {
        if (list == null) return;
        for (Map<String, Object> node : list) {
            collectStylesFromNode(node, cssBuilder);
        }
    }

    private static String camelToKebab(String str) {
        if (str == null) return "";
        return str.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
