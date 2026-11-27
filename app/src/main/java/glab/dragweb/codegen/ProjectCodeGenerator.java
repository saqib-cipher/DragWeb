package glab.dragweb.codegen;

import glab.dragweb.R;
import glab.dragweb.activities.*;
import glab.dragweb.fragments.*;
import glab.dragweb.engine.*;
import glab.dragweb.ui.*;
import glab.dragweb.logic.*;
import glab.dragweb.codegen.*;
import glab.dragweb.data.*;
import glab.dragweb.adapters.*;
import glab.dragweb.models.*;
import glab.dragweb.util.*;
import glab.dragweb.colorpicker.*;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectCodeGenerator {

    public static ThemeManager getProjectThemeManager(String projectId) {
        return getProjectThemeManager(null, projectId);
    }

    public static ThemeManager getProjectThemeManager(Context context, String projectId) {
        ThemeManager tm = new ThemeManager();
        File themeFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/theme.json");
        if (FileUtil.exists(themeFile)) {
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
        File assetsDir = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/assets");
        File cssDir = new File(assetsDir, "css");
        File jsDir = new File(assetsDir, "js");
        FileUtil.makeDir(cssDir.getAbsolutePath());
        FileUtil.makeDir(jsDir.getAbsolutePath());

        ThemeManager themeManager = getProjectThemeManager(context, projectId);

        // 1. Generate & save theme.css
        {
            String themeCss = themeManager.generateGlobalCss();
            File themeFile = new File(cssDir, "theme.css");
            FileUtil.writeFile(themeFile.getAbsolutePath(), themeCss != null ? themeCss : "");
        }

        // 2. Compile & save JS (always to script.js — HTML always links js/script.js)
        {
            String compiledJs = compileJsForFile(context, projectId, cleanPage);
            StringBuilder finalJs = new StringBuilder();
            if (compiledJs != null && !compiledJs.trim().isEmpty()) {
                finalJs.append(compiledJs.trim()).append("\n");
            }

            File jsTargetFile = new File(jsDir, "script.js");
            FileUtil.writeFile(jsTargetFile.getAbsolutePath(), finalJs.toString());
        }

        // 3. Compile & save CSS for global style.css + every page-linked stylesheet from PageManager
        {
            PageManager pm = new PageManager(context, projectId);
            java.util.Set<String> cssTargets = new java.util.LinkedHashSet<>();
            cssTargets.add("css/style.css");
            for (String p : pm.getPages()) {
                cssTargets.add(resolvePageCssFile(pm.getPageConfig(p)));
            }
            for (String relCss : cssTargets) {
                String compiledCss = compileCssForFile(context, projectId, relCss);
                String importPath = relCss.startsWith("css/") ? "theme.css" : "css/theme.css";
                StringBuilder finalCss = new StringBuilder();
                finalCss.append("@import url(\"").append(importPath).append("\");\n\n");
                if (compiledCss != null && !compiledCss.trim().isEmpty()) {
                    finalCss.append(compiledCss).append("\n");
                }
                File cssTargetFile = new File(assetsDir, relCss);
                FileUtil.writeFile(cssTargetFile.getAbsolutePath(), finalCss.toString());
            }
        }
    }

    public static String getMoreBlockCode(String funcName, String spec, String innerCode) {
        ArrayList<String> paramNames = new ArrayList<>();
        if (spec != null && !spec.isEmpty()) {
            ArrayList<String> tokens = StringUtil.tokenize(spec);
            for (String tok : tokens) {
                if (tok.startsWith("%")) {
                    String pName = tok;
                    if (tok.startsWith("%b.") || tok.startsWith("%d.") || tok.startsWith("%s.")) {
                        pName = tok.substring(3);
                    } else if (tok.startsWith("%m.list.") || tok.startsWith("%m.selector.")) {
                        pName = tok.substring(tok.lastIndexOf('.') + 1);
                    } else if (tok.length() > 2) {
                        pName = tok.substring(2);
                    } else if (tok.length() > 1) {
                        pName = tok.substring(1);
                    }
                    if (pName.contains(".")) {
                        pName = pName.substring(0, pName.indexOf('.'));
                    }
                    pName = pName.trim();
                    if (pName.isEmpty() || pName.equals("s") || pName.equals("b") || pName.equals("d")) {
                        pName = "param" + (paramNames.size() + 1);
                    }
                    paramNames.add(pName);
                }
            }
        }

        StringBuilder paramsSb = new StringBuilder();
        for (int i = 0; i < paramNames.size(); i++) {
            if (i > 0) paramsSb.append(", ");
            paramsSb.append(paramNames.get(i));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("function ").append(funcName).append("(").append(paramsSb.toString()).append(") {\n");
        if (innerCode != null && !innerCode.trim().isEmpty()) {
            for (String line : innerCode.split("\n")) {
                if (!line.trim().isEmpty()) {
                    sb.append("  ").append(line).append("\n");
                }
            }
        }
        sb.append("}\n\n");
        return sb.toString();
    }

    private static ArrayList<BlockBean> getMoreBlockBeans(String projectId, String funcName) {
        String funcKey1 = "func_" + funcName;
        String funcKey2 = funcName + "_moreBlock";
        if (DesignDataManager.mapBlocks != null) {
            for (HashMap<String, ArrayList<BlockBean>> map : DesignDataManager.mapBlocks.values()) {
                if (map != null) {
                    if (map.containsKey(funcKey1) && map.get(funcKey1) != null && !map.get(funcKey1).isEmpty()) {
                        return map.get(funcKey1);
                    } else if (map.containsKey(funcKey2) && map.get(funcKey2) != null && !map.get(funcKey2).isEmpty()) {
                        return map.get(funcKey2);
                    }
                }
            }
        }
        return null;
    }

    private static boolean isOrContainsDefinedFunc(ArrayList<BlockBean> blocks) {
        if (blocks == null || blocks.isEmpty()) return false;
        for (BlockBean b : blocks) {
            if (b != null && "definedFunc".equals(b.opCode)) {
                return true;
            }
        }
        return false;
    }

    public static ArrayList<String> addMoreBlockCodes(Context context, String projectId, BlockCodeCompiler compiler) {
        ArrayList<String> moreBlocks = new ArrayList<>();
        java.util.Set<String> compiledFuncsLower = new java.util.HashSet<>();

        // Phase 1: Compile from registered MoreBlock definitions (moreblocks.json)
        ArrayList<DesignDataManager.MoreBlockData> allFuncs = DesignDataManager.getProjectMoreBlocks(projectId);
        if (allFuncs != null) {
            for (DesignDataManager.MoreBlockData mb : allFuncs) {
                if (mb == null || mb.name == null || mb.name.trim().isEmpty()) continue;
                String cleanName = mb.name.trim();
                String lowerName = cleanName.toLowerCase();
                if (compiledFuncsLower.contains(lowerName)) continue;

                ArrayList<BlockBean> funcBlocks = getMoreBlockBeans(projectId, cleanName);
                if (funcBlocks == null || funcBlocks.isEmpty()) continue;
                compiledFuncsLower.add(lowerName);

                ArrayList<BlockBean> bodyBlocks = new ArrayList<>();
                for (BlockBean b : funcBlocks) {
                    if (b != null && !"definedFunc".equals(b.opCode)) {
                        bodyBlocks.add(b);
                    }
                }
                String innerCode = bodyBlocks.isEmpty() ? "" : compiler.getSource(0, bodyBlocks);
                moreBlocks.add(getMoreBlockCode(cleanName, mb.spec != null ? mb.spec.trim() : cleanName, innerCode));
            }
        }

        // Phase 2: Scan mapBlocks for any leftover func_ / _moreBlock keys not yet compiled
        if (DesignDataManager.mapBlocks != null) {
            for (HashMap<String, ArrayList<BlockBean>> map : DesignDataManager.mapBlocks.values()) {
                if (map == null) continue;
                for (Map.Entry<String, ArrayList<BlockBean>> entry : map.entrySet()) {
                    String key = entry.getKey();
                    ArrayList<BlockBean> list = entry.getValue();
                    if (key == null || list == null || list.isEmpty()) continue;

                    String fName = null;
                    String spec = null;

                    if (key.startsWith("func_")) {
                        fName = key.substring(5).trim();
                    } else if (key.endsWith("_moreBlock")) {
                        fName = key.substring(0, key.indexOf("_moreBlock")).trim();
                    }

                    if (fName == null || fName.isEmpty()) continue;
                    if (compiledFuncsLower.contains(fName.toLowerCase())) continue;

                    for (BlockBean b : list) {
                        if (b != null && "definedFunc".equals(b.opCode)) {
                            spec = b.spec != null ? b.spec.trim() : b.opCode;
                            break;
                        }
                    }

                    compiledFuncsLower.add(fName.toLowerCase());

                    ArrayList<BlockBean> bodyBlocks = new ArrayList<>();
                    for (BlockBean b : list) {
                        if (b != null && !"definedFunc".equals(b.opCode)) {
                            bodyBlocks.add(b);
                        }
                    }
                    String innerCode = bodyBlocks.isEmpty() ? "" : compiler.getSource(0, bodyBlocks);
                    moreBlocks.add(getMoreBlockCode(fName, spec != null ? spec : fName, innerCode));
                }
            }
        }

        return moreBlocks;
    }

    private static void emitFunctionDefinition(StringBuilder sb, java.util.Set<String> compiledFuncs, BlockCodeCompiler compiler, String funcName, String spec) {
        if (funcName == null || funcName.trim().isEmpty() || compiledFuncs.contains(funcName)) return;
        ArrayList<BlockBean> funcBlocks = getMoreBlockBeans(null, funcName);
        ArrayList<BlockBean> bodyBlocks = new ArrayList<>();
        if (funcBlocks != null) {
            for (BlockBean b : funcBlocks) {
                if (b != null && !"definedFunc".equals(b.opCode)) {
                    bodyBlocks.add(b);
                }
            }
        }
        String innerCode = bodyBlocks.isEmpty() ? "" : compiler.getSource(0, bodyBlocks);
        sb.append(getMoreBlockCode(funcName, spec, innerCode));
        compiledFuncs.add(funcName);
    }

    public static boolean isSystemEvent(String key) {
        if (key == null) return false;
        return "onPageLoad_onPageLoad".equals(key)
                || "onCreate_initializeLogic".equals(key)
                || "initializeLogic_initializeLogic".equals(key)
                || key.startsWith("page_")
                || key.startsWith("window_")
                || key.startsWith("document_");
    }

    public static java.util.Set<String> collectWidgetIdentifiers(Context context, String projectId, String cleanPage) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        if (context == null || projectId == null || cleanPage == null) return ids;
        try {
            PageManager pm = new PageManager(context, projectId);
            String json = pm.loadPageLayout(cleanPage);
            List<Map<String, Object>> tree = PageManager.parseLayoutTree(json);
            if (tree != null && !tree.isEmpty()) {
                collectIdentifiersFromTree(tree, ids);
                return ids;
            }
            File pageFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/pages/" + cleanPage + ".json");
            if (!pageFile.exists()) {
                pageFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/pages/" + cleanPage.replace(".html", "") + ".json");
            }
            if (pageFile.exists()) {
                String legacyJson = FileUtil.readFile(pageFile.getAbsolutePath());
                List<Map<String, Object>> legacyTree = PageManager.parseLayoutTree(legacyJson);
                collectIdentifiersFromTree(legacyTree, ids);
            }
        } catch (Exception ignored) {}
        return ids;
    }

    public static void collectIdentifiersFromTree(List<Map<String, Object>> tree, java.util.Set<String> ids) {
        if (tree == null || ids == null) return;
        for (Map<String, Object> node : tree) {
            if (node == null) continue;
            if (node.get("id") != null) ids.add(node.get("id").toString().trim());
            if (node.get("tag") != null) ids.add(node.get("tag").toString().trim());
            if (node.get("class") != null) {
                for (String cls : node.get("class").toString().trim().split("\\s+")) {
                    if (!cls.isEmpty()) {
                        ids.add(cls);
                        ids.add("." + cls);
                    }
                }
            }
            if (node.get("function") instanceof Map) {
                Map<?, ?> fn = (Map<?, ?>) node.get("function");
                if (fn.get("id") != null) ids.add(fn.get("id").toString().trim());
                if (fn.get("class") != null) {
                    for (String cls : fn.get("class").toString().trim().split("\\s+")) {
                        if (!cls.isEmpty()) {
                            ids.add(cls);
                            ids.add("." + cls);
                        }
                    }
                }
            }
            if (node.get("children") instanceof List) {
                collectIdentifiersFromTree((List<Map<String, Object>>) node.get("children"), ids);
            }
        }
    }

    public static String compileJsForFile(Context context, String projectId, String cleanPage) {
        BlockCodeCompiler compiler = new BlockCodeCompiler(context, projectId);
        StringBuilder sb = new StringBuilder();

        DesignDataManager.loadSavedLogic(context, projectId, cleanPage);

        java.util.Set<String> validIds = collectWidgetIdentifiers(context, projectId, cleanPage);
        StringBuilder eventsSb = new StringBuilder();

        // 1. Collect event blocks from target page (without duplicating alias events)
        HashMap<String, ArrayList<BlockBean>> blocksMap = DesignDataManager.mapBlocks.get(cleanPage);
        if (blocksMap != null && !blocksMap.isEmpty()) {
            java.util.Set<String> processedEvents = new java.util.HashSet<>();
            for (Map.Entry<String, ArrayList<BlockBean>> entry : blocksMap.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.startsWith("func_") || key.contains("moreBlock")) continue;
                if (isOrContainsDefinedFunc(entry.getValue())) continue;

                // Normalize page load alias events so it only compiles once
                String normalizedKey = key;
                if ("onPageLoad_onPageLoad".equals(key) || "onCreate_initializeLogic".equals(key) || "initializeLogic_initializeLogic".equals(key) || key.equals(cleanPage)) {
                    normalizedKey = "onPageLoad_onPageLoad";
                }
                if (!processedEvents.add(normalizedKey)) continue;

                if (!isSystemEvent(normalizedKey) && validIds != null && !validIds.isEmpty()) {
                    int lastUnder = normalizedKey.lastIndexOf('_');
                    if (lastUnder > 0) {
                        String widgetId = normalizedKey.substring(0, lastUnder);
                        if (!validIds.contains(widgetId)) {
                            continue; // Skip orphaned event from deleted widget
                        }
                    }
                }

                // Filter out non-JS blocks (exclude CSS blocks and HTML blocks)
                ArrayList<BlockBean> jsBlocks = new ArrayList<>();
                for (BlockBean b : entry.getValue()) {
                    if (ExportManager.isJsBlockEligible(b)) {
                        if (validIds != null && !validIds.isEmpty()) {
                            if ("widgets".equalsIgnoreCase(b.category) && b.parameters != null && !b.parameters.isEmpty()) {
                                String sel = b.parameters.get(0) != null ? b.parameters.get(0).trim() : "";
                                if (!sel.isEmpty()) {
                                    String target = sel.startsWith("#") ? sel.substring(1) : sel;
                                    if (!validIds.contains(target) && !validIds.contains(sel)) {
                                        continue;
                                    }
                                }
                            }
                        }
                        jsBlocks.add(b);
                    }
                }
                if (jsBlocks.isEmpty()) continue;

                String code = compiler.getSource(0, jsBlocks);
                if (code != null && !code.trim().isEmpty()) {
                    eventsSb.append(code).append("\n\n");
                }
            }
        }

        String eventCode = eventsSb.toString();

        // 2. Emit registered MoreBlock functions (only if referenced in events)
        ArrayList<String> mbCodes = addMoreBlockCodes(context, projectId, compiler);
        for (String mbCode : mbCodes) {
            if (mbCode != null && !mbCode.trim().isEmpty()) {
                String cleanDef = mbCode.trim();
                String fName = "";
                if (cleanDef.startsWith("function ")) {
                    int parenIdx = cleanDef.indexOf('(');
                    if (parenIdx > 9) {
                        fName = cleanDef.substring(9, parenIdx).trim();
                    }
                }
                if (!fName.isEmpty() && eventCode.contains(fName)) {
                    sb.append(mbCode);
                }
            }
        }

        if (eventsSb.length() > 0) {
            sb.append(eventsSb);
        }

        return sb.toString().trim();
    }

    public static String normalizeAssetRelPath(String rawPath, String fallback) {
        if (rawPath == null || rawPath.trim().isEmpty()) return fallback;
        String clean = rawPath.trim().replace("\\", "/");
        while (clean.startsWith("/")) clean = clean.substring(1);
        return clean.isEmpty() ? fallback : clean;
    }

    /** Resolves which CSS asset file a page links via PageManager (global css/style.css vs custom cssFile). */
    public static String resolvePageCssFile(PageManager.PageConfig cfg) {
        if (cfg != null && !cfg.useGlobalCss && cfg.cssFile != null && !cfg.cssFile.trim().isEmpty()) {
            return normalizeAssetRelPath(cfg.cssFile, "css/style.css");
        }
        return "css/style.css";
    }

    /** Resolves which normalized CSS file path corresponds to an asset path or clean logic key. */
    public static String resolveTargetCssRelPath(String fileOrCleanKey) {
        if (fileOrCleanKey == null || fileOrCleanKey.trim().isEmpty()) return "css/style.css";
        String norm = fileOrCleanKey.trim().replace("\\", "/");
        String lower = norm.toLowerCase(java.util.Locale.US);
        if ("css_style".equals(lower) || "style".equals(lower) || "styles".equals(lower) || "styles.css".equals(lower)) {
            return "css/style.css";
        }
        if (lower.endsWith(".css")) {
            return normalizeAssetRelPath(norm, "css/style.css");
        }
        if (lower.startsWith("css_")) {
            return "css/" + norm.substring(4) + ".css";
        }
        return "css/style.css";
    }

    /** Returns all page names in PageManager whose linked stylesheet matches targetCssRelPath. */
    public static List<String> getPagesLinkedToCssFile(PageManager pm, String targetCssRelPath) {
        List<String> linked = new ArrayList<>();
        if (pm == null) return linked;
        String normTarget = normalizeAssetRelPath(targetCssRelPath, "css/style.css");
        List<String> pages = pm.getPages();
        if (pages == null || pages.isEmpty()) {
            pages = java.util.Collections.singletonList("index");
        }
        for (String p : pages) {
            String cleanP = DesignDataManager.getCleanPageName(p);
            String pageCss = resolvePageCssFile(pm.getPageConfig(cleanP));
            if (normTarget.equalsIgnoreCase(pageCss)) {
                linked.add(cleanP);
            }
        }
        return linked;
    }

    private static boolean isCssTargetBlockEligible(BlockBean b, boolean isCssTargetKey) {
        if (b == null || b.category == null) return false;
        String op = b.opCode != null ? b.opCode.toLowerCase(java.util.Locale.US) : "";
        if (op.startsWith("html") || op.startsWith("view_")) return false;
        String cat = b.category.toLowerCase(java.util.Locale.US).trim();
        if (ExportManager.isCssCategory(cat)) {
            return true;
        }
        return isCssTargetKey && cat.equals("asd");
    }

    /**
     * Compiles ONLY the user's own CSS logic blocks for the given target CSS file (and linked page CSS blocks).
     */
    public static String compileCssLogicOnly(Context context, String projectId, String targetCssOrPage) {
        StringBuilder cssSb = new StringBuilder();
        String relCss = resolveTargetCssRelPath(targetCssOrPage);
        appendCssLogicBlocks(context, projectId, relCss, cssSb);
        return cssSb.toString().trim();
    }

    private static void appendCssLogicBlocks(Context context, String projectId, String targetCssRelPath, StringBuilder cssSb) {
        ThemeManager tm = getProjectThemeManager(context, projectId);
        boolean skipPropStyleBlocks = (tm == null || tm.isUseInlineStyles() || tm.isDisableAllStyling());
        PageManager pm = new PageManager(context, projectId);
        String cssKey = DesignDataManager.getCleanPageName(targetCssRelPath);
        java.util.Set<String> compiledKeys = new java.util.LinkedHashSet<>();
        compiledKeys.add(cssKey);
        for (String linkedPage : getPagesLinkedToCssFile(pm, targetCssRelPath)) {
            compiledKeys.add(linkedPage);
        }

        for (String key : compiledKeys) {
            try {
                DesignDataManager.loadSavedLogic(context, projectId, key);
                Map<String, ArrayList<BlockBean>> blocksMap = DesignDataManager.getAllBlocks(key);
                if (blocksMap != null) {
                    boolean isCssTarget = key.equals(cssKey);
                    BlockCodeCompiler compiler = new BlockCodeCompiler(context, projectId);
                    java.util.Set<String> seenEvents = new java.util.HashSet<>();
                    for (Map.Entry<String, ArrayList<BlockBean>> entry : blocksMap.entrySet()) {
                        String evKey = entry.getKey();
                        if (evKey == null) continue;
                        String normEv = DesignDataManager.canonicalizeEventKey(key, evKey);
                        if (!seenEvents.add(normEv)) continue;
                        ArrayList<BlockBean> cssBlocks = new ArrayList<>();
                        for (BlockBean b : entry.getValue()) {
                            if (skipPropStyleBlocks && b != null && b.isPropertyStyle) continue;
                            if (isCssTargetBlockEligible(b, isCssTarget)) {
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
    }

    /**
     * Collects ONLY widget/canvas property CSS rules for all pages linked to {@code targetCssRelPath}.
     * Used both for external stylesheet output and for syncing property style blocks into the linked CSS Logic Editor.
     */
    public static String collectWidgetPropertyCssForCssFile(Context context, String projectId, String targetCssRelPath) {
        ThemeManager themeManager = getProjectThemeManager(context, projectId);
        boolean noWidgetProps = (themeManager != null && themeManager.isDisableAllStyling());
        boolean useInline = (themeManager == null || themeManager.isUseInlineStyles());
        if (noWidgetProps || useInline) {
            return "";
        }
        PageManager pm = new PageManager(context, projectId);
        List<String> targetPages = getPagesLinkedToCssFile(pm, targetCssRelPath);
        StringBuilder propsSb = new StringBuilder();
        for (String pageToCollect : targetPages) {
            String layoutJson = pm.loadPageLayout(pageToCollect);
            List<Map<String, Object>> widgetTree = PageManager.parseLayoutTree(layoutJson);
            if (widgetTree != null && !widgetTree.isEmpty()) {
                collectStylesFromTree(widgetTree, propsSb);
            } else {
                File layoutFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/pages/" + pageToCollect + ".json");
                if (layoutFile.exists()) {
                    try {
                        String legacyJson = FileUtil.readFile(layoutFile.getAbsolutePath());
                        if (legacyJson != null && !legacyJson.trim().isEmpty()) {
                            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
                            ArrayList<Map<String, Object>> legacyTree = new com.google.gson.Gson().fromJson(legacyJson, listType);
                            if (legacyTree != null) {
                                collectStylesFromTree(legacyTree, propsSb);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return propsSb.toString().trim();
    }

    public static String compileCssForFile(Context context, String projectId, String targetCssOrPage) {
        String relCss = resolveTargetCssRelPath(targetCssOrPage);
        StringBuilder logicSb = new StringBuilder();
        appendCssLogicBlocks(context, projectId, relCss, logicSb);
        String logicCss = logicSb.toString().trim();

        String widgetPropsCss = filterOutExistingCssSelectors(
                collectWidgetPropertyCssForCssFile(context, projectId, relCss),
                logicCss);

        StringBuilder cssSb = new StringBuilder();
        if (widgetPropsCss != null && !widgetPropsCss.isEmpty()) {
            cssSb.append(widgetPropsCss).append("\n\n");
        }
        if (!logicCss.isEmpty()) {
            cssSb.append(logicCss).append("\n\n");
        }
        return cssSb.toString().trim();
    }

    /**
     * Removes any top-level CSS rule from {@code elemCss} whose selector is already
     * defined in {@code logicCss}, preventing duplicate widget-property vs logic-block rules.
     */
    public static String filterOutExistingCssSelectors(String elemCss, String logicCss) {
        if (elemCss == null || elemCss.trim().isEmpty()) return "";
        if (logicCss == null || logicCss.trim().isEmpty()) return elemCss.trim();

        java.util.Set<String> existingSelectors = extractTopLevelCssSelectors(logicCss);
        if (existingSelectors.isEmpty()) return elemCss.trim();

        StringBuilder out = new StringBuilder();
        int len = elemCss.length();
        int i = 0;
        while (i < len) {
            while (i < len && Character.isWhitespace(elemCss.charAt(i))) i++;
            if (i >= len) break;

            int braceOpen = elemCss.indexOf('{', i);
            if (braceOpen < 0) break;

            String selector = elemCss.substring(i, braceOpen).trim();
            int depth = 1;
            int j = braceOpen + 1;
            while (j < len && depth > 0) {
                char c = elemCss.charAt(j);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                j++;
            }
            String fullRule = elemCss.substring(i, j).trim();
            String normalized = normalizeCssSelector(selector);
            if (!existingSelectors.contains(normalized)) {
                out.append(fullRule).append("\n\n");
            }
            i = j;
        }
        return out.toString().trim();
    }

    private static java.util.Set<String> extractTopLevelCssSelectors(String css) {
        java.util.Set<String> selectors = new java.util.HashSet<>();
        int len = css.length();
        int i = 0;
        while (i < len) {
            char c = css.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '/' && i + 1 < len && css.charAt(i + 1) == '*') {
                int end = css.indexOf("*/", i + 2);
                i = (end >= 0) ? (end + 2) : len;
                continue;
            }
            if (c == '@') {
                int semi = css.indexOf(';', i);
                int brace = css.indexOf('{', i);
                if (semi >= 0 && (brace < 0 || semi < brace)) {
                    i = semi + 1;
                    continue;
                }
            }
            int braceOpen = css.indexOf('{', i);
            if (braceOpen < 0) break;
            String sel = css.substring(i, braceOpen).trim();
            selectors.add(normalizeCssSelector(sel));
            int depth = 1;
            int j = braceOpen + 1;
            while (j < len && depth > 0) {
                char ch = css.charAt(j);
                if (ch == '{') depth++;
                else if (ch == '}') depth--;
                j++;
            }
            i = j;
        }
        return selectors;
    }

    private static String normalizeCssSelector(String selector) {
        if (selector == null) return "";
        return selector.replaceAll("/\\*.*?\\*/", "").replaceAll("\\s+", " ").trim();
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

    public static String resolvePageJsFile(PageManager.PageConfig cfg) {
        if (cfg == null || cfg.useGlobalJs || cfg.jsFile == null || cfg.jsFile.trim().isEmpty()) {
            return "js/script.js";
        }
        String f = cfg.jsFile.trim();
        if (f.startsWith("/")) f = f.substring(1);
        return f;
    }

    private static String camelToKebab(String str) {
        if (str == null) return "";
        return str.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
