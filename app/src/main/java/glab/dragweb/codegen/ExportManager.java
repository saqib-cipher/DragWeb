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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ExportManager {

    private Context context;
    private ThemeManager themeManager;
    private IconLibraryManager iconLibraryManager;
    private AnimationLibraryManager animationLibraryManager;
    private GoogleFontsManager googleFontsManager;
    private String projectId;

    /**
     * Per-export accumulator for element-level CSS. Cleared at the start of
     * each call to {@link #generateExportFiles} so widget styles get appended
     * into {@code css/style.css} instead of being baked inline on the element.
     */
    private final StringBuilder elementCssBuffer = new StringBuilder();
    private final java.util.Set<String> collectedWidgetScripts = new java.util.LinkedHashSet<>();

    public ExportManager(Context context, ThemeManager themeManager) {
        this.context = context;
        this.themeManager = themeManager;
    }

    public void collectWidgetScript(Map<String, Object> function) {
        if (function == null) return;
        String s = function.containsKey("script:js") && function.get("script:js") != null
                ? String.valueOf(function.get("script:js"))
                : (function.containsKey("script") && function.get("script") != null ? String.valueOf(function.get("script")) : null);
        if (s != null && !s.trim().isEmpty()) {
            collectedWidgetScripts.add(s.trim());
        }
    }

    public static boolean isInternalFunctionKey(String key, Object value) {
        if (key == null || key.trim().isEmpty()) return true;
        String k = key.trim();
        if (k.equals("id") || k.equals("class") || k.equals("style")
            || k.equals("tag") || k.equals("children") || k.equals("text")
            || k.equals("href") || k.equals("src") || k.equals("placeholder")
            || k.equals("type") || k.equals("alt") || k.equals("controls")
            || k.startsWith("on")) {
            return true;
        }
        if (k.equalsIgnoreCase("customProperties")
            || k.equalsIgnoreCase("script")
            || k.equalsIgnoreCase("script:js")
            || k.equalsIgnoreCase("style:css")
            || k.equalsIgnoreCase("stylePresets")
            || k.equalsIgnoreCase("items")
            || k.equalsIgnoreCase("icons")
            || k.equalsIgnoreCase("brand")
            || k.equalsIgnoreCase("headers")
            || k.equalsIgnoreCase("rows")
            || k.equalsIgnoreCase("options")
            || k.equalsIgnoreCase("slides")
            || k.equalsIgnoreCase("blocks")
            || k.equalsIgnoreCase("preset")
            || k.equalsIgnoreCase("convert")
            || k.equalsIgnoreCase("locked")
            || k.equalsIgnoreCase("hidden")
            || k.equalsIgnoreCase("name")
            || k.equalsIgnoreCase("category")
            || k.equalsIgnoreCase("tag")
            || k.equalsIgnoreCase("children")) {
            return true;
        }
        if (value instanceof Map || value instanceof List || value instanceof java.util.Collection) {
            return true;
        }
        if (value != null) {
            String valStr = value.toString().trim();
            if (valStr.startsWith("{") || valStr.startsWith("[")
                || valStr.contains("function ") || valStr.contains("function(")
                || valStr.contains("const ") || valStr.contains("let ")
                || valStr.contains("document.") || valStr.contains("\n")) {
                return true;
            }
        }
        return !k.matches("^[a-zA-Z_:][a-zA-Z0-9_.:-]*$");
    }

    private void appendGeneratedChildrenForItems(StringBuilder html, String tag, Map<String, Object> function, String indent) {
        if (function == null) return;
        if ("ul".equals(tag) || "ol".equals(tag)) {
            if (function.containsKey("items") && function.get("items") instanceof List) {
                List<?> items = (List<?>) function.get("items");
                if (!items.isEmpty()) {
                    html.append("\n");
                    for (Object item : items) {
                        if (item != null) {
                            html.append(indent).append("  <li>").append(escapeHtml(item.toString())).append("</li>\n");
                        }
                    }
                    html.append(indent);
                }
            }
        } else if ("select".equals(tag)) {
            if (function.containsKey("items") && function.get("items") instanceof List) {
                List<?> items = (List<?>) function.get("items");
                if (!items.isEmpty()) {
                    html.append("\n");
                    for (Object item : items) {
                        if (item != null) {
                            String v = item.toString().toLowerCase().replace(" ", "-");
                            html.append(indent).append("  <option value=\"").append(escapeHtml(v)).append("\">")
                                .append(escapeHtml(item.toString())).append("</option>\n");
                        }
                    }
                    html.append(indent);
                }
            }
        } else if ("nav".equals(tag)) {
            IconLibraryManager.appendNavChildrenHtml(html, function, indent);
        } else if ("table".equals(tag)) {
            if (function.containsKey("headers") && function.get("headers") instanceof List) {
                List<?> headers = (List<?>) function.get("headers");
                if (!headers.isEmpty()) {
                    html.append("\n").append(indent).append("  <thead>\n").append(indent).append("    <tr>\n");
                    for (Object h : headers) {
                        if (h != null) {
                            html.append(indent).append("      <th style=\"border:1px solid #E2E8F0;padding:8px 12px;\">")
                                .append(escapeHtml(h.toString())).append("</th>\n");
                        }
                    }
                    html.append(indent).append("    </tr>\n").append(indent).append("  </thead>\n");
                }
            }
            if (function.containsKey("rows") && function.get("rows") instanceof List) {
                List<?> rows = (List<?>) function.get("rows");
                if (!rows.isEmpty()) {
                    html.append(indent).append("  <tbody>\n");
                    for (Object r : rows) {
                        if (r instanceof List) {
                            html.append(indent).append("    <tr>\n");
                            for (Object c : (List<?>) r) {
                                html.append(indent).append("      <td style=\"border:1px solid #E2E8F0;padding:8px 12px;\">")
                                    .append(escapeHtml(c != null ? c.toString() : "")).append("</td>\n");
                            }
                            html.append(indent).append("    </tr>\n");
                        }
                    }
                    html.append(indent).append("  </tbody>\n");
                }
                html.append(indent);
            }
        }
    }

    public void setIconLibraryManager(IconLibraryManager m) {
        this.iconLibraryManager = m;
    }

    public void setAnimationLibraryManager(AnimationLibraryManager m) {
        this.animationLibraryManager = m;
    }

    public void setGoogleFontsManager(GoogleFontsManager m) {
        this.googleFontsManager = m;
    }

    /**
     * Set the active project id so non-ZIP exports can locate the matching
     * assets folder ({@code .dragweb/projects/&lt;id&gt;/assets}). Without this
     * the HTML export still works, but ships with an empty assets directory.
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public static boolean isCssCategory(String category) {
        if (category == null) return false;
        String cat = category.toLowerCase().trim();
        return cat.equals("css") || cat.startsWith("css") || cat.startsWith("css_")
            || cat.equals("responsive") || cat.equals("comment") || cat.equals("animation");
    }

    public static boolean isHtmlCategory(String category) {
        if (category == null) return false;
        String cat = category.toLowerCase().trim();
        return cat.equals("html") || cat.startsWith("html");
    }

    public static boolean isJsCategory(String category) {
        if (category == null) return false;
        return !isCssCategory(category) && !isHtmlCategory(category);
    }

    public String compileBlocksForMode(String cleanPage, String mode) {
        return compileBlocksForMode(cleanPage, mode, false);
    }

    public String compileBlocksForMode(String cleanPage, String mode, boolean skipPropertyStyleBlocks) {
        if ("html".equalsIgnoreCase(mode)) return "";
        DesignDataManager.loadSavedLogic(context, projectId, cleanPage);
        Map<String, ArrayList<BlockBean>> allBlocks = DesignDataManager.getAllBlocks(cleanPage);
        if (allBlocks == null || allBlocks.isEmpty()) {
            File logicFile = new File(FileUtil.getDragWebDir(), "projects/" + projectId + "/" + cleanPage + "_logic.json");
            if (logicFile.exists()) {
                try {
                    String content = FileUtil.readFile(logicFile.getAbsolutePath());
                    if (content != null && !content.trim().isEmpty()) {
                        DesignDataManager.PageLogicData data = DesignDataManager.deserializePageLogicData(content, cleanPage);
                        if (data != null && data.blocks != null) {
                            allBlocks = data.blocks;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        java.util.Set<String> validIds = ProjectCodeGenerator.collectWidgetIdentifiers(context, projectId, cleanPage);
        return compileBlocksMapForMode(context, projectId, cleanPage, allBlocks, mode, validIds, skipPropertyStyleBlocks);
    }

    public static String compileBlocksMapForMode(Context context,
                                                 String projectId,
                                                 String cleanPage,
                                                 Map<String, ArrayList<BlockBean>> allBlocks,
                                                 String mode,
                                                 java.util.Set<String> validIds) {
        return compileBlocksMapForMode(context, projectId, cleanPage, allBlocks, mode, validIds, false);
    }

    /**
     * Compile a page or asset block map into CSS or JS while deduplicating aliased
     * page-load event keys (e.g. {@code onPageLoad_onPageLoad}, {@code onCreate_initializeLogic},
     * {@code immediate}, and {@code cleanPage}) so identical logic is emitted only once.
     */
    public static String compileBlocksMapForMode(Context context,
                                                 String projectId,
                                                 String cleanPage,
                                                 Map<String, ArrayList<BlockBean>> allBlocks,
                                                 String mode,
                                                 java.util.Set<String> validIds,
                                                 boolean skipPropertyStyleBlocks) {
        if ("html".equalsIgnoreCase(mode) || allBlocks == null || allBlocks.isEmpty()) {
            return "";
        }
        try {
            BlockCodeCompiler compiler = new BlockCodeCompiler(context, projectId);
            StringBuilder sb = new StringBuilder();
            java.util.Set<String> seenEvents = new java.util.LinkedHashSet<>();
            ArrayList<BlockBean> canonicalPageLoad = allBlocks.get("onPageLoad_onPageLoad");
            boolean preferCanonicalPageLoad = canonicalPageLoad != null && !canonicalPageLoad.isEmpty();

            for (Map.Entry<String, ArrayList<BlockBean>> entry : allBlocks.entrySet()) {
                String key = entry.getKey();
                if (key != null && (key.startsWith("func_") || key.contains("moreBlock"))) continue;
                ArrayList<BlockBean> blockList = entry.getValue();
                if (blockList == null || blockList.isEmpty()) continue;

                String normalizedEvent = DesignDataManager.canonicalizeEventKey(cleanPage, key);
                if ("onPageLoad_onPageLoad".equals(normalizedEvent)
                        && preferCanonicalPageLoad
                        && !"onPageLoad_onPageLoad".equals(key)) {
                    continue;
                }
                if (!seenEvents.add(normalizedEvent)) {
                    continue;
                }

                if (key != null && validIds != null && !validIds.isEmpty() && !ProjectCodeGenerator.isSystemEvent(normalizedEvent)) {
                    int lastUnder = key.lastIndexOf('_');
                    if (lastUnder > 0) {
                        String widgetId = key.substring(0, lastUnder);
                        if (!validIds.contains(widgetId)) {
                            continue; // Orphaned event from deleted widget
                        }
                    }
                }

                ArrayList<BlockBean> filtered = new ArrayList<>();
                boolean isCssTarget = cleanPage != null && cleanPage.startsWith("css");
                for (BlockBean b : blockList) {
                    if (b == null) continue;
                    if (skipPropertyStyleBlocks && b.isPropertyStyle && "css".equalsIgnoreCase(mode)) {
                        continue;
                    }
                    String cat = b.category != null ? b.category : "";
                    if (validIds != null && !validIds.isEmpty()) {
                        if ("widgets".equalsIgnoreCase(cat) && b.parameters != null && !b.parameters.isEmpty()) {
                            String sel = b.parameters.get(0) != null ? b.parameters.get(0).trim() : "";
                            if (!sel.isEmpty()) {
                                String target = sel.startsWith("#") ? sel.substring(1) : sel;
                                if (!validIds.contains(target) && !validIds.contains(sel)) {
                                    continue; // Target widget does not exist on page
                                }
                            }
                        }
                    }
                    if ("css".equalsIgnoreCase(mode)) {
                        if (isCssCategory(cat) || (isCssTarget && "asd".equalsIgnoreCase(cat.trim()))) filtered.add(b);
                    } else if ("html".equalsIgnoreCase(mode)) {
                        if (isHtmlCategory(cat)) filtered.add(b);
                    } else if ("js".equalsIgnoreCase(mode)) {
                        if (!isCssTarget && isJsCategory(cat)) filtered.add(b);
                    } else {
                        filtered.add(b);
                    }
                }
                if (filtered.isEmpty()) continue;
                String code = compiler.getSource(0, filtered);
                if (code != null && !code.trim().isEmpty()) {
                    sb.append(code).append("\n\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String compileBlocksFromLogicFile(String cleanPage) {
        return compileBlocksForMode(cleanPage, "all");
    }

    private String compileBlocksFromLogicFile(String cleanPage, String categoryPrefix) {
        return compileBlocksForMode(cleanPage, categoryPrefix);
    }

    private String compileBlocksFromLogicFile(String cleanPage, String categoryPrefix, String excludePrefix) {
        if ("html".equalsIgnoreCase(excludePrefix)) {
            return compileBlocksForMode(cleanPage, "js");
        }
        return compileBlocksForMode(cleanPage, categoryPrefix != null ? categoryPrefix : "all");
    }

    public static class ExportResult {
        public String htmlContent;
        public String cssContent;
        public String jsContent;
        public File exportDir;
        public boolean success;
        public String message;
    }

    public ExportResult generateExportFiles(View screen, String projectName, LogicBlockManager logicBlockManager) {
        return generateExportFiles(screen, projectName, logicBlockManager, null);
    }

    public ExportResult generateExportFiles(View screen, String projectName,
                                            LogicBlockManager logicBlockManager,
                                            ManageBlocksWidgets customBlockManager) {
        return generateExportFiles(screen, projectName, logicBlockManager, customBlockManager, null, null);
    }

    public ExportResult generateExportFiles(View screen, String projectName,
                                            LogicBlockManager logicBlockManager,
                                            ManageBlocksWidgets customBlockManager,
                                            File customExportDir) {
        return generateExportFiles(screen, projectName, logicBlockManager, customBlockManager, customExportDir, null);
    }

    public ExportResult generateExportFiles(View screen, String projectName,
                                            LogicBlockManager logicBlockManager,
                                            ManageBlocksWidgets customBlockManager,
                                            File customExportDir,
                                            PageManager livePageManager) {
        ExportResult result = new ExportResult();
        
        // Load all project pages using PageManager
        PageManager pageManager = livePageManager;
        if (pageManager == null) {
            pageManager = new PageManager(context, projectId);
        }
        List<String> allPages = pageManager.getPages();
        if (allPages.isEmpty()) {
            allPages.add("index");
        }
        String currentPageName = pageManager.getCurrentPage();

        // Load unified logic file (and migrate legacy _logic.json if needed)
        DesignDataManager.loadSavedLogic(context, projectId, "index");
        for (String p : allPages) {
            DesignDataManager.loadSavedLogic(context, projectId, p);
        }

        Map<String, String> pageHtmlMap = new HashMap<>();
        StringBuilder accumulatedCssBuffer = new StringBuilder();
        StringBuilder accumulatedJsBuffer = new StringBuilder();
        Map<String, StringBuilder> perFileElementCss = new java.util.LinkedHashMap<>();
        Map<String, StringBuilder> perFileUserCss = new java.util.LinkedHashMap<>();
        Map<String, StringBuilder> perFileJs = new java.util.LinkedHashMap<>();
        elementCssBuffer.setLength(0);
        collectedWidgetScripts.clear();

        boolean useInline = (themeManager == null || themeManager.isUseInlineStyles());
        boolean noStyles = (themeManager != null && themeManager.isDisableAllStyling());
        boolean skipPropStyleBlocks = useInline || noStyles;

        // Load default global CSS stylesheet blocks (ASD + CSS compiled once by compileBlocksForMode)
        String globalCssClean = DesignDataManager.getCleanPageName("css/style.css");
        String cssBlockCode = compileBlocksForMode(globalCssClean, "css", skipPropStyleBlocks);
        if (!cssBlockCode.isEmpty()) {
            accumulatedCssBuffer.append(cssBlockCode).append("\n");
        }

        String keyframesCss = (animationLibraryManager != null)
                ? animationLibraryManager.generateLocalKeyframesCss("")
                : AnimationLibrary.generateKeyframesCss("");
        if (keyframesCss != null && !keyframesCss.trim().isEmpty()) {
            accumulatedCssBuffer.append(keyframesCss.trim()).append("\n");
        }

        // Load default global JS script blocks (ASD + JS compiled once by compileBlocksForMode)
        String globalJsClean = DesignDataManager.getCleanPageName("js/script.js");
        String jsBlockCode = compileBlocksForMode(globalJsClean, "js");
        if (!jsBlockCode.isEmpty()) {
            accumulatedJsBuffer.append(jsBlockCode).append("\n");
        }
        perFileJs.put("js/script.js", accumulatedJsBuffer);

        for (String pageName : allPages) {
            String htmlContent = "";
            LogicBlockManager pageLogic = null;
            elementCssBuffer.setLength(0);
            PageManager.PageConfig pageCfg = pageManager.getPageConfig(pageName);
            String linkedCss = ProjectCodeGenerator.resolvePageCssFile(pageCfg);
            String linkedJs = ProjectCodeGenerator.resolvePageJsFile(pageCfg);

            if (pageName.equals(currentPageName) && screen != null) {
                // Live page
                htmlContent = generateHtml(screen, projectName, customBlockManager, logicBlockManager, pageName);
                pageLogic = logicBlockManager;
            } else {
                // Loaded page from json
                String pageJson = pageManager.loadPageLayout(pageName);
                pageLogic = new LogicBlockManager(context);
                String cleanName = DesignDataManager.getCleanPageName(pageName);
                Map<String, ArrayList<BlockBean>> pageBlocks = DesignDataManager.getAllBlocks(cleanName);
                if (pageBlocks != null && !pageBlocks.isEmpty()) {
                    Map<String, Object> rootMap = new HashMap<>();
                    rootMap.put("blocks", pageBlocks);
                    pageLogic.fromJson(new Gson().toJson(rootMap));
                } else {
                    File logicFile = new File(FileUtil.getDragWebDir(), "projects/" + projectId + "/" + cleanName + "_logic.json");
                    if (logicFile.exists()) {
                        String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
                        pageLogic.fromJson(logicJson);
                    }
                }

                List<Map<String, Object>> widgetTree = null;
                try {
                    widgetTree = glab.dragweb.data.PageManager.parseLayoutTree(pageJson);
                } catch (Exception e) {
                    Log.w("ExportManager", "Error parsing widget tree for page: " + pageName, e);
                }
                htmlContent = generateHtmlFromTree(widgetTree, pageName, customBlockManager, pageLogic);
            }

            // Clean assets reference in HTML: replace "assets/" with ""
            htmlContent = htmlContent.replace("assets/", "");

            pageHtmlMap.put(pageName, htmlContent);

            if (elementCssBuffer.length() > 0) {
                perFileElementCss.computeIfAbsent(linkedCss, k -> new StringBuilder())
                        .append(elementCssBuffer);
                elementCssBuffer.setLength(0);
            }

            String cleanPage = DesignDataManager.getCleanPageName(pageName);
            if (!"js/script.js".equals(linkedJs) && !perFileJs.containsKey(linkedJs)) {
                StringBuilder customJsBuf = new StringBuilder();
                String targetJsBlocks = compileBlocksForMode(DesignDataManager.getCleanPageName(linkedJs), "js");
                if (!targetJsBlocks.isEmpty()) {
                    customJsBuf.append(targetJsBlocks).append("\n");
                }
                perFileJs.put(linkedJs, customJsBuf);
            }

            // Accumulate page-specific CSS & JS rules into the page's linked files
            if (pageLogic != null) {
                StringBuilder pageCssTarget = perFileUserCss.computeIfAbsent(linkedCss, k -> new StringBuilder());
                String pseudoRules = pageLogic.generateCssPseudoRules();
                if (pseudoRules != null && !pseudoRules.trim().isEmpty()) {
                    pageCssTarget.append(pseudoRules).append("\n");
                }
                String pageCssBlocks = compileBlocksForMode(cleanPage, "css", skipPropStyleBlocks);
                if (!pageCssBlocks.isEmpty()) {
                    pageCssTarget.append(pageCssBlocks).append("\n");
                }

                // Accumulate page-specific JS rules (compileBlocksForMode already includes ASD blocks once)
                String pageJsBlocks = compileBlocksForMode(cleanPage, "js");
                if (!pageJsBlocks.isEmpty()) {
                    perFileJs.computeIfAbsent(linkedJs, k -> new StringBuilder())
                            .append(pageJsBlocks).append("\n");
                }
            }
        }

        StringBuilder allHtmlSb = new StringBuilder();
        for (String htmlVal : pageHtmlMap.values()) {
            if (htmlVal != null) allHtmlSb.append(htmlVal).append(" ");
        }
        String allHtml = allHtmlSb.toString();

        // MoreBlock function definitions (only include if referenced in JS or HTML)
        ArrayList<String> funcDefs = new ArrayList<>();
        {
            BlockCodeCompiler funcCompiler = new BlockCodeCompiler(context, projectId);
            funcDefs.addAll(ProjectCodeGenerator.addMoreBlockCodes(context, projectId, funcCompiler));
        }

        // Custom block CSS
        if (customBlockManager != null) {
            String customCss = customBlockManager.renderAllCss();
            if (customCss != null && !customCss.trim().isEmpty()) {
                accumulatedCssBuffer.append(customCss).append("\n");
            }
        }

        // Build final CSS per linked stylesheet (css/style.css + any per-page custom CSS files)
        java.util.Set<String> allLinkedCss = new java.util.LinkedHashSet<>();
        allLinkedCss.add("css/style.css");
        for (String p : allPages) {
            allLinkedCss.add(ProjectCodeGenerator.resolvePageCssFile(pageManager.getPageConfig(p)));
        }
        allLinkedCss.addAll(perFileElementCss.keySet());
        allLinkedCss.addAll(perFileUserCss.keySet());

        Map<String, String> exportedCssFiles = new java.util.LinkedHashMap<>();
        String globalThemeImport = "@import url(\"theme.css\");\n\n";
        for (String relCss : allLinkedCss) {
            StringBuilder fileSb = new StringBuilder();
            String importHref = relCss.startsWith("css/") ? "theme.css" : "css/theme.css";
            fileSb.append("@import url(\"").append(importHref).append("\");\n\n");

            // Compile user/logic CSS for this stylesheet first
            StringBuilder logicCssSb = new StringBuilder();
            if ("css/style.css".equals(relCss)) {
                String globalCss = accumulatedCssBuffer.toString().replace("assets/", "").trim();
                if (!globalCss.isEmpty()) {
                    logicCssSb.append(globalCss).append("\n\n");
                }
                if (perFileUserCss.get(relCss) != null && perFileUserCss.get(relCss).length() > 0) {
                    logicCssSb.append(perFileUserCss.get(relCss).toString().replace("assets/", "")).append("\n");
                }
            } else {
                // Only compile the linked stylesheet's own blocks; linked pages' blocks are already in perFileUserCss
                String customSheetCss = compileBlocksForMode(DesignDataManager.getCleanPageName(relCss), "css", skipPropStyleBlocks);
                if (customSheetCss != null && !customSheetCss.isEmpty()) {
                    logicCssSb.append(customSheetCss.replace("assets/", "")).append("\n\n");
                }
                StringBuilder extraPageCss = perFileUserCss.get(relCss);
                if (extraPageCss != null && extraPageCss.length() > 0) {
                    logicCssSb.append(extraPageCss.toString().replace("assets/", "")).append("\n");
                }
            }
            String logicCss = logicCssSb.toString().trim();

            // Emit unrepresented widget property styles only if not already present in logic CSS blocks
            StringBuilder elemSb = perFileElementCss.get(relCss);
            if (!skipPropStyleBlocks && elemSb != null && elemSb.length() > 0) {
                String filteredProps = ProjectCodeGenerator.filterOutExistingCssSelectors(elemSb.toString(), logicCss);
                if (!filteredProps.isEmpty()) {
                    fileSb.append(filteredProps).append("\n\n");
                }
            }
            if (!logicCss.isEmpty()) {
                fileSb.append(logicCss).append("\n");
            }
            exportedCssFiles.put(relCss, fileSb.toString().trim() + "\n");
        }

        String finalCssContent = exportedCssFiles.containsKey("css/style.css")
                ? exportedCssFiles.get("css/style.css")
                : globalThemeImport;

        // Build final JS per linked script file (js/script.js + any per-page custom JS files)
        Map<String, String> exportedJsFiles = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> jsEntry : perFileJs.entrySet()) {
            StringBuilder jsSb = jsEntry.getValue();
            String currentJs = jsSb.toString();

            if (!funcDefs.isEmpty()) {
                StringBuilder funcSb = new StringBuilder();
                for (String def : funcDefs) {
                    if (def == null || def.trim().isEmpty()) continue;
                    String cleanDef = def.trim();
                    String fName = "";
                    if (cleanDef.startsWith("function ")) {
                        int parenIdx = cleanDef.indexOf('(');
                        if (parenIdx > 9) {
                            fName = cleanDef.substring(9, parenIdx).trim();
                        }
                    }
                    if (!fName.isEmpty() && (currentJs.contains(fName) || allHtml.contains(fName))) {
                        funcSb.append(def).append("\n");
                    }
                }
                if (funcSb.length() > 0) {
                    jsSb.insert(0, funcSb.toString() + "\n");
                }
            }

            if (!collectedWidgetScripts.isEmpty()) {
                StringBuilder wsSb = new StringBuilder();
                String curJs = jsSb.toString();
                for (String script : collectedWidgetScripts) {
                    if (script == null || script.trim().isEmpty()) continue;
                    if (isScriptReferenced(script, curJs, allHtml)) {
                        wsSb.append(script).append("\n\n");
                    }
                }
                if (wsSb.length() > 0) {
                    jsSb.append("\n/* Widget Scripts */\n").append(wsSb);
                }
            }

            jsSb.append("/* ----- init ----- */\n");
            jsSb.append("document.addEventListener('DOMContentLoaded', function() {\n");
            jsSb.append("  if (window.feather) try { feather.replace(); } catch (e) {}\n");
            jsSb.append("  if (window.lucide)  try { lucide.createIcons(); } catch (e) {}\n");
            jsSb.append("});\n");

            exportedJsFiles.put(jsEntry.getKey(), jsSb.toString().replace("assets/", ""));
        }

        String finalJsContent = exportedJsFiles.containsKey("js/script.js")
                ? exportedJsFiles.get("js/script.js")
                : "";

        File exportDir = customExportDir != null ? customExportDir : new File(FileUtil.getDragWebDir(context), "exports/" + sanitizeFileName(projectName));
        if (customExportDir == null && exportDir.exists()) {
            deleteDirectory(exportDir);
        }
        exportDir.mkdirs();

        File cssDir = new File(exportDir, "css");
        File jsDir = new File(exportDir, "js");
        cssDir.mkdirs();
        jsDir.mkdirs();

        result.htmlContent = pageHtmlMap.get(currentPageName);
        if (result.htmlContent == null) {
            result.htmlContent = pageHtmlMap.get("index");
        }
        result.cssContent = finalCssContent;
        result.jsContent = finalJsContent;
        result.exportDir = exportDir;

        try {
            // Write HTML files in the root folder
            for (Map.Entry<String, String> entry : pageHtmlMap.entrySet()) {
                writeFile(new File(exportDir, entry.getKey() + ".html"), entry.getValue());
            }

            // Write theme.css with variables and base theme rules (never wiped by User CSS Only toggle)
            String themeCss = themeManager != null ? themeManager.generateGlobalCss() : new ThemeManager().generateGlobalCss();
            writeFile(new File(cssDir, "theme.css"), themeCss != null ? themeCss : "");

            // Write every linked CSS stylesheet (css/style.css + per-page custom stylesheets)
            for (Map.Entry<String, String> cssEntry : exportedCssFiles.entrySet()) {
                File targetCss = new File(exportDir, cssEntry.getKey());
                if (targetCss.getParentFile() != null) targetCss.getParentFile().mkdirs();
                writeFile(targetCss, cssEntry.getValue());
            }

            // Write every linked JS file (js/script.js + per-page custom scripts)
            for (Map.Entry<String, String> jsEntry : exportedJsFiles.entrySet()) {
                File targetJs = new File(exportDir, jsEntry.getKey());
                if (targetJs.getParentFile() != null) targetJs.getParentFile().mkdirs();
                writeFile(targetJs, jsEntry.getValue());
            }

            // Sync into projects/<projectId>/assets only when exportDir is a separate folder
            File extAssetsDir = (projectId != null && !projectId.isEmpty())
                    ? new File(FileUtil.getDragWebDir(), "projects/" + projectId + "/assets")
                    : null;
            boolean exportingIntoProjectAssets = extAssetsDir != null
                    && exportDir.getCanonicalPath().equals(extAssetsDir.getCanonicalPath());

            if (!exportingIntoProjectAssets && extAssetsDir != null) {
                try {
                    File extThemeFile = new File(extAssetsDir, "css/theme.css");
                    if (extThemeFile.getParentFile() != null) extThemeFile.getParentFile().mkdirs();
                    writeFile(extThemeFile, themeCss != null ? themeCss : "");

                    for (Map.Entry<String, String> cssEntry : exportedCssFiles.entrySet()) {
                        File extCssFile = new File(extAssetsDir, cssEntry.getKey());
                        if (extCssFile.getParentFile() != null) extCssFile.getParentFile().mkdirs();
                        writeFile(extCssFile, cssEntry.getValue());
                    }

                    for (Map.Entry<String, String> jsEntry : exportedJsFiles.entrySet()) {
                        File extJsFile = new File(extAssetsDir, jsEntry.getKey());
                        if (extJsFile.getParentFile() != null) extJsFile.getParentFile().mkdirs();
                        writeFile(extJsFile, jsEntry.getValue());
                    }
                } catch (Exception e) {
                    Log.w("ExportManager", "Failed to sync compiled CSS/JS with assets: " + e.getMessage());
                }
            }

            if (!exportingIntoProjectAssets) {
                copyAssetsClean(exportDir);
            }

            result.success = true;
            result.message = "Exported to: " + exportDir.getAbsolutePath();
        } catch (IOException e) {
            result.success = false;
            result.message = "Export failed: " + e.getMessage();
        }

        return result;
    }

    private void copyAssetsClean(File targetRootDir) {
        if (projectId == null || projectId.isEmpty()) return;
        try {
            String panelPath = FileUtil.getDragWebDir().getAbsolutePath() + "/projects/" + projectId + "/assets";
            File src = new File(panelPath);
            if (!src.exists() || !src.isDirectory()) return;

            // Skip copying if the target directory is the assets directory itself
            if (targetRootDir.getCanonicalPath().equals(src.getCanonicalPath())) {
                return;
            }

            File[] files = src.listFiles();
            if (files == null) return;
            for (File file : files) {
                // Don't overwrite compiled css/js with stale external copies
                if (file.isDirectory() && (file.getName().equals("css") || file.getName().equals("js"))) continue;
                if (file.isDirectory()) {
                    copyDirectory(file, new File(targetRootDir, file.getName()));
                } else {
                    copyDirectory(file, new File(targetRootDir, file.getName()));
                }
            }
        } catch (Exception e) {
            Log.w("ExportManager", "Could not mirror assets: " + e.getMessage());
        }
    }

    private PageManager.PageConfig getPageConfigSafe(String pageName) {
        if (context == null || projectId == null || projectId.isEmpty()) return null;
        return new PageManager(context, projectId).getPageConfig(pageName);
    }

    private void appendPageHeadMeta(StringBuilder html, PageManager.PageConfig pageCfg, String fallbackTitle) {
        String resolvedTitle = (pageCfg != null && pageCfg.pageTitle != null && !pageCfg.pageTitle.trim().isEmpty())
                ? pageCfg.pageTitle.trim()
                : fallbackTitle;
        String metaDesc = (pageCfg != null && pageCfg.metaDescription != null && !pageCfg.metaDescription.trim().isEmpty())
                ? pageCfg.metaDescription.trim()
                : fallbackTitle;
        html.append("  <meta name=\"description\" content=\"").append(escapeHtml(metaDesc)).append("\">\n");
        if (pageCfg != null) {
            if (pageCfg.metaKeywords != null && !pageCfg.metaKeywords.trim().isEmpty()) {
                html.append("  <meta name=\"keywords\" content=\"").append(escapeHtml(pageCfg.metaKeywords.trim())).append("\">\n");
            }
            if (pageCfg.metaAuthor != null && !pageCfg.metaAuthor.trim().isEmpty()) {
                html.append("  <meta name=\"author\" content=\"").append(escapeHtml(pageCfg.metaAuthor.trim())).append("\">\n");
            }
            if (pageCfg.metaThemeColor != null && !pageCfg.metaThemeColor.trim().isEmpty()) {
                html.append("  <meta name=\"theme-color\" content=\"").append(escapeHtml(pageCfg.metaThemeColor.trim())).append("\">\n");
            }
            if (pageCfg.ogTitle != null && !pageCfg.ogTitle.trim().isEmpty()) {
                html.append("  <meta property=\"og:title\" content=\"").append(escapeHtml(pageCfg.ogTitle.trim())).append("\">\n");
            }
            if (pageCfg.ogDescription != null && !pageCfg.ogDescription.trim().isEmpty()) {
                html.append("  <meta property=\"og:description\" content=\"").append(escapeHtml(pageCfg.ogDescription.trim())).append("\">\n");
            }
            if (pageCfg.ogImage != null && !pageCfg.ogImage.trim().isEmpty()) {
                html.append("  <meta property=\"og:image\" content=\"").append(escapeHtml(pageCfg.ogImage.trim())).append("\">\n");
            }
            if (pageCfg.favicon != null && !pageCfg.favicon.trim().isEmpty()) {
                html.append("  <link rel=\"icon\" href=\"").append(escapeHtml(pageCfg.favicon.trim())).append("\">\n");
            }
        }
        html.append("  <meta name=\"generator\" content=\"DragWeb\">\n");
        html.append("  <title>").append(escapeHtml(resolvedTitle)).append("</title>\n");
    }

    private String generateHtmlFromTree(List<Map<String, Object>> widgetTree, String pageName, ManageBlocksWidgets customBlockManager,
                                        LogicBlockManager logicBlockManager) {
        PageManager.PageConfig pageCfg = getPageConfigSafe(pageName);
        StringBuilder bodyBuilder = new StringBuilder();

        if (widgetTree != null) {
            for (Map<String, Object> nodeMap : widgetTree) {
                bodyBuilder.append(generateElementHtmlFromNode(nodeMap, 1));
            }
        }

        if (customBlockManager != null) {
            String customHtml = customBlockManager.renderAllHtml();
            if (customHtml != null && !customHtml.trim().isEmpty()) {
                bodyBuilder.append("\n  <!-- Custom blocks -->\n  ");
                bodyBuilder.append(customHtml.replace("\n", "\n  "));
                bodyBuilder.append("\n");
            }
        }

        if (logicBlockManager != null) {
            String asdHtml = logicBlockManager.generateAsdSource("html");
            if (asdHtml != null && !asdHtml.trim().isEmpty()) {
                bodyBuilder.append("\n  <!-- ASD HTML source -->\n  ");
                bodyBuilder.append(asdHtml.replace("\n", "\n  "));
                bodyBuilder.append("\n");
            }
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        appendPageHeadMeta(html, pageCfg, pageName);

        if (logicBlockManager != null) {
            String asdMeta = logicBlockManager.generateAsdSource("meta");
            if (asdMeta != null && !asdMeta.trim().isEmpty()) {
                html.append("  ").append(asdMeta.replace("\n", "\n  ")).append("\n");
            }
        }

        Set<String> detectedIcons = IconLibraryManager.detectLibrariesInHtml(bodyBuilder.toString());
        if (iconLibraryManager != null) {
            String includes = iconLibraryManager.generateHtmlIncludes(detectedIcons);
            if (includes != null && !includes.isEmpty()) html.append(includes);
        } else if (!detectedIcons.isEmpty()) {
            String includes = IconLibraryManager.generateHtmlIncludesFor(context, detectedIcons);
            if (includes != null && !includes.isEmpty()) html.append(includes);
        }
        if (animationLibraryManager != null) {
            String includes = animationLibraryManager.generateHtmlIncludes();
            if (includes != null && !includes.isEmpty()) html.append(includes);
        }

        if (googleFontsManager != null) {
            String fonts = googleFontsManager.generateGoogleFontsLinkTag(pageName);
            if (fonts != null && !fonts.isEmpty()) html.append("  ").append(fonts);
        }

        html.append(getStylesheetLink(pageName));

        if (logicBlockManager != null) {
            String asdHead = logicBlockManager.generateAsdSource("head");
            if (asdHead != null && !asdHead.trim().isEmpty()) {
                html.append("  ").append(asdHead.replace("\n", "\n  ")).append("\n");
            }
        }

        if (pageCfg != null && pageCfg.customHeadCode != null && !pageCfg.customHeadCode.trim().isEmpty()) {
            html.append("  ").append(pageCfg.customHeadCode.trim().replace("\n", "\n  ")).append("\n");
        }

        html.append("</head>\n");
        html.append("<body>\n");
        html.append(bodyBuilder);

        String scriptSrc = (pageCfg != null && !pageCfg.useGlobalJs && pageCfg.jsFile != null && !pageCfg.jsFile.trim().isEmpty())
                ? pageCfg.jsFile.trim()
                : "js/script.js";
        html.append("\n  <script src=\"").append(escapeHtml(scriptSrc)).append("\" defer></script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }

    @SuppressWarnings("unchecked")
    private String generateElementHtmlFromNode(Map<String, Object> widgetMap, int indent) {
        if (widgetMap == null) return "";

        String tag = widgetMap.containsKey("tag") ? widgetMap.get("tag").toString() : "div";
        Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
        if (function == null) return "";
        collectWidgetScript(function);

        String indentStr = repeat("  ", indent);
        StringBuilder html = new StringBuilder();
        html.append(indentStr).append("<").append(tag);

        boolean useInline = (themeManager == null || themeManager.isUseInlineStyles());
        boolean noStyles = (themeManager != null && themeManager.isDisableAllStyling());
        String generatedClass = "";
        StringBuilder classAttr = new StringBuilder();

        if (useInline && !noStyles) {
            String userClass = function.containsKey("class") ? String.valueOf(function.get("class")).trim() : "";
            if (!userClass.isEmpty()) {
                String[] classes = userClass.split("\\s+");
                generatedClass = classes[0];
            } else {
                generatedClass = tag;
            }
        }

        if (function.containsKey("class")) {
            String userClass = String.valueOf(function.get("class")).trim();
            if (!userClass.isEmpty()) {
                if (classAttr.length() > 0) classAttr.append(' ');
                classAttr.append(userClass);
            }
        }

        if (classAttr.length() > 0) {
            html.append(" class=\"").append(escapeHtml(classAttr.toString())).append("\"");
        }

        if (function.containsKey("id")) {
            String elId = String.valueOf(function.get("id")).trim();
            if (!elId.isEmpty()) html.append(" id=\"").append(escapeHtml(elId)).append("\"");
        }

        for (Map.Entry<String, Object> entry : function.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("on") && entry.getValue() != null) {
                String val = String.valueOf(entry.getValue()).trim();
                if (!val.isEmpty()) {
                    html.append(" ").append(key).append("=\"").append(escapeHtml(val)).append("\"");
                }
            }
        }

        // Output custom attributes (data-*, role, aria-*, target, etc.)
        for (Map.Entry<String, Object> entry : function.entrySet()) {
            String key = entry.getKey();
            if (!isInternalFunctionKey(key, entry.getValue())) {
                if (entry.getValue() != null) {
                    html.append(" ").append(key).append("=\"").append(escapeHtml(entry.getValue().toString())).append("\"");
                }
            }
        }

        // Output inline style attribute if useInline is true and widget property styling is active
        if (useInline && !noStyles) {
            Map<String, Object> style = (Map<String, Object>) function.get("style");
            if (style != null && !style.isEmpty()) {
                StringBuilder styleSb = new StringBuilder();
                for (Map.Entry<String, Object> entry : style.entrySet()) {
                    String val = entry.getValue() != null ? entry.getValue().toString().trim() : "";
                    if (val.isEmpty()) continue;
                    styleSb.append(camelToKebab(entry.getKey())).append(": ").append(val).append("; ");
                }
                if (styleSb.length() > 0) {
                    html.append(" style=\"").append(escapeHtml(styleSb.toString().trim())).append("\"");
                }
            }
        } else if (!useInline && !noStyles) {
            Map<String, Object> style = (Map<String, Object>) function.get("style");
            if (style != null && !style.isEmpty()) {
                String selector;
                if (function.containsKey("id") && function.get("id") != null && !String.valueOf(function.get("id")).trim().isEmpty()) {
                    selector = "#" + String.valueOf(function.get("id")).trim();
                } else if (function.containsKey("class") && function.get("class") != null && !String.valueOf(function.get("class")).trim().isEmpty()) {
                    selector = "." + String.valueOf(function.get("class")).trim().split("\\s+")[0];
                } else {
                    selector = tag;
                }
                elementCssBuffer.append(selector).append(" {\n");
                for (Map.Entry<String, Object> entry : style.entrySet()) {
                    String val = entry.getValue() != null ? entry.getValue().toString().trim() : "";
                    if (val.isEmpty()) continue;
                    String cssKey = camelToKebab(entry.getKey());
                    elementCssBuffer.append("  ").append(cssKey).append(": ")
                        .append(val).append(";\n");
                }
                elementCssBuffer.append("}\n\n");
            }
        }

        if ("img".equals(tag) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeHtml(resolveAssetPath(function.get("src").toString()))).append("\"");
            html.append(" alt=\"Image\"");
        }
        if (("input".equals(tag) || "textarea".equals(tag)) && function.containsKey("type")) {
            html.append(" type=\"").append(escapeHtml(function.get("type").toString())).append("\"");
        }
        if (("input".equals(tag) || "textarea".equals(tag)) && function.containsKey("placeholder")) {
            html.append(" placeholder=\"").append(escapeHtml(function.get("placeholder").toString())).append("\"");
        }
        if ("a".equals(tag) && function.containsKey("href")) {
            html.append(" href=\"").append(escapeHtml(function.get("href").toString())).append("\"");
        }
        if (("video".equals(tag) || "audio".equals(tag)) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeHtml(function.get("src").toString())).append("\"");
        }
        if (("video".equals(tag) || "audio".equals(tag)) && "true".equals(String.valueOf(function.get("controls")))) {
            html.append(" controls");
        }
        if ("iframe".equals(tag) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeHtml(function.get("src").toString())).append("\"");
        }

        if ("input".equals(tag) || "img".equals(tag) || "hr".equals(tag) || "br".equals(tag)) {
            html.append(" />\n");
            return html.toString();
        }

        html.append(">");

        if (function.containsKey("text")) {
            html.append(escapeHtml(function.get("text").toString()));
        }

        StringBuilder childrenHtml = new StringBuilder();
        boolean isItemDriven = (("ul".equals(tag) || "ol".equals(tag) || "select".equals(tag) || "nav".equals(tag))
                && function.containsKey("items"))
                || ("table".equals(tag) && (function.containsKey("headers") || function.containsKey("rows")));
        if (!isItemDriven && widgetMap.containsKey("children")) {
            Object childrenObj = widgetMap.get("children");
            if (childrenObj instanceof List) {
                List<Map<String, Object>> children = (List<Map<String, Object>>) childrenObj;
                for (Map<String, Object> child : children) {
                    String ch = generateElementHtmlFromNode(child, indent + 1);
                    if (ch != null && !ch.trim().isEmpty()) {
                        childrenHtml.append(ch);
                    }
                }
            }
        }
        if (childrenHtml.length() > 0) {
            html.append("\n").append(childrenHtml).append(indentStr);
        } else {
            appendGeneratedChildrenForItems(html, tag, function, indentStr);
        }

        html.append("</").append(tag).append(">\n");
        return html.toString();
    }

    private String resolveAssetPath(String rawSrc) {
        if (rawSrc == null) return "";
        if (rawSrc.startsWith("data:")) return rawSrc;
        if (rawSrc.startsWith("assets/")) return rawSrc;
        int idx = rawSrc.indexOf("/assets/");
        if (idx >= 0) {
            return "assets/" + rawSrc.substring(idx + "/assets/".length());
        }
        return rawSrc;
    }

    public File exportAsZip(View screen, String projectName, String projectId, LogicBlockManager logicBlockManager) throws IOException {
        return exportAsZip(screen, projectName, projectId, logicBlockManager, null);
    }

    public File exportAsZip(View screen, String projectName, String projectId,
                            LogicBlockManager logicBlockManager,
                            ManageBlocksWidgets customBlockManager) throws IOException {
        ExportResult result = generateExportFiles(screen, projectName, logicBlockManager, customBlockManager);
        if (!result.success) {
            throw new IOException(result.message);
        }

        String zipFileName = sanitizeFileName(projectName) + "_" + sanitizeFileName(projectId) + ".zip";

        File zipFile = null;
        try {
            String extPath = FileUtil.getDragWebDir().getAbsolutePath() + "/export";
            File extDir = new File(extPath);
            if (!extDir.exists()) extDir.mkdirs();
            zipFile = new File(extDir, zipFileName);
        } catch (Exception e) {
            Log.w("ExportManager", "External storage unavailable, using internal: " + e.getMessage());
        }

        if (zipFile == null || (!zipFile.getParentFile().exists() && !zipFile.getParentFile().mkdirs())) {
            File internalDir = new File(FileUtil.getDragWebDir(context), "exports");
            if (!internalDir.exists()) internalDir.mkdirs();
            zipFile = new File(internalDir, zipFileName);
        }

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            zipDirectoryContents(zos, result.exportDir, "");
        }
        return zipFile;
    }

    private void zipDirectoryContents(ZipOutputStream zos, File dir, String prefix) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                zipDirectoryContents(zos, file, prefix + file.getName() + "/");
            } else {
                addFileToZip(zos, prefix + file.getName(), file);
            }
        }
    }

    private void addFileToZip(ZipOutputStream zos, String entryName, File file) throws IOException {
        byte[] buffer = new byte[4096];
        try (FileInputStream fis = new FileInputStream(file)) {
            zos.putNextEntry(new ZipEntry(entryName));
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
        }
    }

    private void addDirectoryToZip(ZipOutputStream zos, File dir, String prefix) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, prefix + file.getName() + "/");
            } else {
                addFileToZip(zos, prefix + file.getName(), file);
            }
        }
    }

    private String generateHtml(View screen, String projectName, ManageBlocksWidgets customBlockManager,
                                LogicBlockManager logicBlockManager, String pageName) {
        PageManager.PageConfig pageCfg = getPageConfigSafe(pageName);
        StringBuilder bodyBuilder = new StringBuilder();

        if (screen instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) screen;
            for (int i = 0; i < vg.getChildCount(); i++) {
                bodyBuilder.append(generateElementHtml(vg.getChildAt(i), 1));
            }
        }

        // Custom-block HTML appended into the body (after widget tree).
        if (customBlockManager != null) {
            String customHtml = customBlockManager.renderAllHtml();
            if (customHtml != null && !customHtml.trim().isEmpty()) {
                bodyBuilder.append("\n  <!-- Custom blocks -->\n  ");
                bodyBuilder.append(customHtml.replace("\n", "\n  "));
                bodyBuilder.append("\n");
            }
        }

        // ASD raw-HTML source blocks (authored in the Logic Blocks editor).
        if (logicBlockManager != null) {
            String asdHtml = logicBlockManager.generateAsdSource("html");
            if (asdHtml != null && !asdHtml.trim().isEmpty()) {
                bodyBuilder.append("\n  <!-- ASD HTML source -->\n  ");
                bodyBuilder.append(asdHtml.replace("\n", "\n  "));
                bodyBuilder.append("\n");
            }
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        appendPageHeadMeta(html, pageCfg, projectName);

        // ASD <meta> additions go before icon CDN includes.
        if (logicBlockManager != null) {
            String asdMeta = logicBlockManager.generateAsdSource("meta");
            if (asdMeta != null && !asdMeta.trim().isEmpty()) {
                html.append("  ").append(asdMeta.replace("\n", "\n  ")).append("\n");
            }
        }

        Set<String> detectedIcons = IconLibraryManager.detectLibrariesInHtml(bodyBuilder.toString());
        if (iconLibraryManager != null) {
            String includes = iconLibraryManager.generateHtmlIncludes(detectedIcons);
            if (includes != null && !includes.isEmpty()) html.append(includes);
        } else if (!detectedIcons.isEmpty()) {
            String includes = IconLibraryManager.generateHtmlIncludesFor(context, detectedIcons);
            if (includes != null && !includes.isEmpty()) html.append(includes);
        }
        if (animationLibraryManager != null) {
            String includes = animationLibraryManager.generateHtmlIncludes();
            if (includes != null && !includes.isEmpty()) html.append(includes);
        }

        if (googleFontsManager != null) {
            String fonts = googleFontsManager.generateGoogleFontsLinkTag(pageName);
            if (fonts != null && !fonts.isEmpty()) html.append("  ").append(fonts);
        }

        html.append(getStylesheetLink(pageName));

        // ASD <head> source (e.g. extra <link> or <script src> tags) goes last
        // so it can override anything emitted above.
        if (logicBlockManager != null) {
            String asdHead = logicBlockManager.generateAsdSource("head");
            if (asdHead != null && !asdHead.trim().isEmpty()) {
                html.append("  ").append(asdHead.replace("\n", "\n  ")).append("\n");
            }
        }

        if (pageCfg != null && pageCfg.customHeadCode != null && !pageCfg.customHeadCode.trim().isEmpty()) {
            html.append("  ").append(pageCfg.customHeadCode.trim().replace("\n", "\n  ")).append("\n");
        }

        html.append("</head>\n");
        html.append("<body>\n");
        html.append(bodyBuilder);

        String scriptSrc = (pageCfg != null && !pageCfg.useGlobalJs && pageCfg.jsFile != null && !pageCfg.jsFile.trim().isEmpty())
                ? pageCfg.jsFile.trim()
                : "js/script.js";
        html.append("\n  <script src=\"").append(escapeHtml(scriptSrc)).append("\" defer></script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }

    private String generateElementHtml(View view, int indent) {
        if (view == null) return "";

        Object tagObj = view.getTag();
        if (!(tagObj instanceof Map)) return "";

        Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
        String tag = widgetMap.containsKey("tag") ? widgetMap.get("tag").toString() : "div";
        Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
        if (function == null) return "";
        collectWidgetScript(function);

        String indentStr = repeat("  ", indent);
        StringBuilder html = new StringBuilder();
        html.append(indentStr).append("<").append(tag);

        // Generated class name and user class check.
        boolean useInline = (themeManager == null || themeManager.isUseInlineStyles());
        boolean noStyles = (themeManager != null && themeManager.isDisableAllStyling());
        String generatedClass = "";
        StringBuilder classAttr = new StringBuilder();

        if (useInline && !noStyles) {
            String userClass = function.containsKey("class") ? String.valueOf(function.get("class")).trim() : "";
            if (!userClass.isEmpty()) {
                String[] classes = userClass.split("\\s+");
                generatedClass = classes[0];
            } else {
                generatedClass = tag;
            }
        }

        if (function.containsKey("class")) {
            String userClass = String.valueOf(function.get("class")).trim();
            if (!userClass.isEmpty()) {
                if (classAttr.length() > 0) classAttr.append(' ');
                classAttr.append(userClass);
            }
        }

        if (classAttr.length() > 0) {
            html.append(" class=\"").append(escapeHtml(classAttr.toString())).append("\"");
        }

        if (function.containsKey("id")) {
            String elId = String.valueOf(function.get("id")).trim();
            if (!elId.isEmpty()) html.append(" id=\"").append(escapeHtml(elId)).append("\"");
        }

        // JS Event attributes
        for (Map.Entry<String, Object> entry : function.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("on") && entry.getValue() != null) {
                String val = String.valueOf(entry.getValue()).trim();
                if (!val.isEmpty()) {
                    html.append(" ").append(key).append("=\"").append(escapeHtml(val)).append("\"");
                }
            }
        }

        // Output custom / other attributes (data-*, role, target, etc.)
        for (Map.Entry<String, Object> entry : function.entrySet()) {
            String key = entry.getKey();
            if (!isInternalFunctionKey(key, entry.getValue()) /*

                && !"href".equals(key) && !"src".equals(key) && !"placeholder".equals(key)
                && !"type".equals(key) && !"alt".equals(key) && !"controls".equals(key)
                */ ) {
                if (entry.getValue() != null) {
                    html.append(" ").append(key).append("=\"").append(escapeHtml(entry.getValue().toString())).append("\"");
                }
            }
        }

        // Output inline style attribute if useInline is true
        if (useInline && !noStyles) {
            Map<String, Object> style = (Map<String, Object>) function.get("style");
            if (style != null && !style.isEmpty()) {
                StringBuilder styleSb = new StringBuilder();
                for (Map.Entry<String, Object> entry : style.entrySet()) {
                    String val = entry.getValue() != null ? entry.getValue().toString().trim() : "";
                    if (val.isEmpty()) continue;
                    styleSb.append(camelToKebab(entry.getKey())).append(": ").append(val).append("; ");
                }
                html.append(" style=\"").append(escapeHtml(styleSb.toString().trim())).append("\"");
            }
        }

        // Output element styles to external CSS ONLY when Inline Styling is disabled
        if (!useInline && !noStyles) {
            Map<String, Object> style = (Map<String, Object>) function.get("style");
            if (style != null && !style.isEmpty()) {
                String selector = "";
                if (function.containsKey("id") && function.get("id") != null && !String.valueOf(function.get("id")).trim().isEmpty()) {
                    selector = "#" + String.valueOf(function.get("id")).trim();
                } else if (function.containsKey("class") && function.get("class") != null && !String.valueOf(function.get("class")).trim().isEmpty()) {
                    selector = "." + String.valueOf(function.get("class")).trim().split("\\s+")[0];
                } else {
                    selector = tag;
                }
                
                elementCssBuffer.append(selector).append(" {\n");
                for (Map.Entry<String, Object> entry : style.entrySet()) {
                    String val = entry.getValue() != null ? entry.getValue().toString().trim() : "";
                    if (val.isEmpty()) continue;
                    String cssKey = camelToKebab(entry.getKey());
                    elementCssBuffer.append("  ").append(cssKey).append(": ")
                        .append(val).append(";\n");
                }
                elementCssBuffer.append("}\n\n");
            }
        }

        // Tag-specific attributes
        if ("img".equals(tag) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeHtml(function.get("src").toString())).append("\"");
            html.append(" alt=\"Image\"");
        }
        if (("input".equals(tag) || "textarea".equals(tag)) && function.containsKey("type")) {
            html.append(" type=\"").append(escapeHtml(function.get("type").toString())).append("\"");
        }
        if (("input".equals(tag) || "textarea".equals(tag)) && function.containsKey("placeholder")) {
            html.append(" placeholder=\"").append(escapeHtml(function.get("placeholder").toString())).append("\"");
        }
        if ("a".equals(tag) && function.containsKey("href")) {
            html.append(" href=\"").append(escapeHtml(function.get("href").toString())).append("\"");
        }
        if (("video".equals(tag) || "audio".equals(tag)) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeHtml(function.get("src").toString())).append("\"");
        }
        if (("video".equals(tag) || "audio".equals(tag)) && "true".equals(String.valueOf(function.get("controls")))) {
            html.append(" controls");
        }
        if ("iframe".equals(tag) && function.containsKey("src")) {
            html.append(" src=\"").append(escapeHtml(function.get("src").toString())).append("\"");
        }

        // Self-closing tags
        if ("input".equals(tag) || "img".equals(tag) || "hr".equals(tag) || "br".equals(tag)) {
            html.append(" />\n");
            return html.toString();
        }

        html.append(">");

        // Text content
        if (function.containsKey("text")) {
            html.append(escapeHtml(function.get("text").toString()));
        }

        // Children
        StringBuilder childrenHtml = new StringBuilder();
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View childView = vg.getChildAt(i);
                if (childView == null || Boolean.TRUE.equals(childView.getTag(R.id.tag_internal_preview))) {
                    continue;
                }
                String ch = generateElementHtml(childView, indent + 1);
                if (ch != null && !ch.trim().isEmpty()) {
                    childrenHtml.append(ch);
                }
            }
        }
        if (childrenHtml.length() > 0) {
            html.append("\n").append(childrenHtml).append(indentStr);
        } else {
            appendGeneratedChildrenForItems(html, tag, function, indentStr);
        }

        html.append("</").append(tag).append(">\n");
        return html.toString();
    }

    private String generateCss(View screen, LogicBlockManager logicBlockManager,
                               ManageBlocksWidgets customBlockManager) {
        StringBuilder css = new StringBuilder();
        String keyframes = (animationLibraryManager != null)
                ? animationLibraryManager.generateLocalKeyframesCss("")
                : AnimationLibrary.generateKeyframesCss("");
        if (keyframes != null && !keyframes.trim().isEmpty()) {
            css.append(keyframes.trim()).append("\n\n");
        }

        // CSS pseudo-class interaction rules (hover, focus, active – no JS needed)
        if (logicBlockManager != null) {
            String pseudoRules = logicBlockManager.generateCssPseudoRules();
            if (pseudoRules != null && !pseudoRules.trim().isEmpty()) {
                css.append(pseudoRules);
                css.append("\n");
            }
            String baseRules = logicBlockManager.generateBaseCssRules();
            if (baseRules != null && !baseRules.trim().isEmpty()) {
                css.append(baseRules);
                css.append("\n");
            }
        }

        // Custom block CSS templates (e.g. ".menu{color:red;}" from class_color block)
        if (customBlockManager != null) {
            String customCss = customBlockManager.renderAllCss();
            if (customCss != null && !customCss.trim().isEmpty()) {
                css.append(customCss);
                css.append("\n");
            }
        }

        // ASD raw CSS source authored in the Logic Blocks editor.
        if (logicBlockManager != null) {
            String asdCss = logicBlockManager.generateAsdSource("css");
            if (asdCss != null && !asdCss.trim().isEmpty()) {
                css.append("/* ASD CSS source */\n");
                css.append(asdCss);
                css.append("\n");
            }
        }

        css.append("/* Element Styles */\n");
        if (elementCssBuffer.length() > 0) {
            css.append(elementCssBuffer);
        }
        return css.toString();
    }

    private String generateJs(LogicBlockManager logicBlockManager) {
        StringBuilder js = new StringBuilder();
        js.append("/* ==========================================================\n");
        js.append(" * DragWeb generated runtime — DO NOT edit by hand.\n");
        js.append(" * Sections: state, init, events, api, animations.\n");
        js.append(" * ========================================================== */\n\n");
        js.append("'use strict';\n\n");

        // ----- state -----
        js.append("/* ----- state ----- */\n");
        js.append("var DW = window.DW = window.DW || { state: {}, components: {} };\n\n");

        // ----- logic blocks JS (already structured by LogicBlockManager) -----
        String logicJs = logicBlockManager != null ? logicBlockManager.generateJavaScript() : "";
        if (logicJs != null && !logicJs.isEmpty()) {
            js.append("/* ----- logic blocks ----- */\n");
            js.append(logicJs).append("\n");
        }

        // ----- ASD raw JS source authored in the Logic Blocks editor -----
        String asdJs = logicBlockManager != null ? logicBlockManager.generateAsdSource("js") : "";
        if (asdJs != null && !asdJs.trim().isEmpty()) {
            js.append("\n/* ----- user JS (ASD) ----- */\n");
            js.append(asdJs).append("\n");
        }

        // ----- init -----
        js.append("/* ----- init ----- */\n");
        js.append("document.addEventListener('DOMContentLoaded', function() {\n");
        js.append("  // Re-initialise icon libraries (no-op if not loaded).\n");
        js.append("  if (window.feather) try { feather.replace(); } catch (e) {}\n");
        js.append("  if (window.lucide)  try { lucide.createIcons(); } catch (e) {}\n");
        js.append("});\n");
        return js.toString();
    }

    private void writeFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private void addToZip(ZipOutputStream zos, String fileName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        zos.write(content.getBytes("UTF-8"));
        zos.closeEntry();
    }

    public boolean importZipBackup(File zipFile) {
        if (zipFile == null || !zipFile.exists()) return false;
        
        File projectDir = new File(FileUtil.getDragWebDir(context), "projects");
        if (!projectDir.exists()) projectDir.mkdirs();
        
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile))) {
            String projectCanonical = projectDir.getCanonicalPath() + File.separator;
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                
                if (name.startsWith("data/")) {
                    String fileName = name.substring(5);
                    File target = new File(projectDir, fileName);
                    if (!target.getCanonicalPath().startsWith(projectCanonical)) {
                        zis.closeEntry();
                        continue;
                    }
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
            return true;
        } catch (IOException e) {
            Log.e("ExportManager", "Import failed: " + e.getMessage());
            return false;
        }
    }

    // Better import logic: extract all to temp, then move to right places
    public boolean restoreProjectFromZip(File zipFile) {
        File tempDir = new File(context.getCacheDir(), "import_temp_" + System.currentTimeMillis());
        tempDir.mkdirs();
        
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile))) {
            String tempCanonical = tempDir.getCanonicalPath() + File.separator;
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(tempDir, entry.getName());
                if (!file.getCanonicalPath().startsWith(tempCanonical)) {
                    zis.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
            
            // Now move from temp to actual locations
            File dataDir = new File(tempDir, "data");
            String foundProjectId = null;
            if (dataDir.exists()) {
                File[] files = dataDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().endsWith(".json")) {
                            String possibleId = f.getName().replace(".json", "");
                            File metaFile = new File(dataDir, possibleId + ".meta");
                            if (metaFile.exists()) {
                                foundProjectId = possibleId;
                                break;
                            }
                        }
                    }
                    
                    if (foundProjectId != null) {
                        // Move data files
                        File targetDataDir = new File(FileUtil.getDragWebDir(context), "projects");
                        targetDataDir.mkdirs();
                        for (File f : files) {
                            f.renameTo(new File(targetDataDir, f.getName()));
                        }
                        
                        // Move assets
                        File assetsDir = new File(tempDir, "assets");
                        if (assetsDir.exists()) {
                            String targetAssetsPath = FileUtil.getDragWebDir(context).getAbsolutePath()
                                + "/projects/" + foundProjectId + "/assets";
                            File targetAssetsDir = new File(targetAssetsPath);
                            targetAssetsDir.mkdirs();
                            copyDirectory(assetsDir, targetAssetsDir);
                        }
                    }
                }
            }
            
            // Clean up temp dir
            deleteDirectory(tempDir);
            return true;
        } catch (IOException e) {
            Log.e("ExportManager", "Restore failed: " + e.getMessage());
            deleteDirectory(tempDir);
            return false;
        }
    }

    private void copyDirectory(File sourceLocation, File targetLocation) throws IOException {
        if (sourceLocation.isDirectory()) {
            if (!targetLocation.exists()) {
                targetLocation.mkdirs();
            }
            
            String[] children = sourceLocation.list();
            if (children != null) {
                for (String child : children) {
                    copyDirectory(new File(sourceLocation, child), new File(targetLocation, child));
                }
            }
        } else {
            try (InputStream in = new FileInputStream(sourceLocation);
                 OutputStream out = new FileOutputStream(targetLocation)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    private void deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            path.delete();
        }
    }

    private String getStylesheetLink(String pageName) {
        if (projectId == null || projectId.isEmpty()) {
            return "  <link rel=\"stylesheet\" href=\"css/style.css\">\n";
        }
        
        PageManager pm = new PageManager(context, projectId);
        PageManager.PageConfig cfg = pm.getPageConfig(pageName);
        if (cfg != null && !cfg.useGlobalCss && cfg.cssFile != null && !cfg.cssFile.isEmpty()) {
            if (cfg.cssFile.equals("css/style.css")) {
                return "  <link rel=\"stylesheet\" href=\"css/style.css\">\n";
            }
            return "  <link rel=\"stylesheet\" href=\"" + cfg.cssFile + "\">\n";
        }

        File metaFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/pages/" + pageName + ".meta");
        if (!metaFile.exists()) {
            metaFile = new File(FileUtil.getDragWebDir(context), "projects/" + projectId + "_" + pageName + ".meta");
        }
        
        if (metaFile.exists()) {
            try {
                String json = FileUtil.readFile(metaFile.getAbsolutePath());
                Map<String, Object> map = new Gson().fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                if (map != null) {
                    Boolean useGlobalCss = (Boolean) map.get("useGlobalCss");
                    String customCssPath = (String) map.get("customCssPath");
                    if (useGlobalCss != null && !useGlobalCss) {
                        if (customCssPath != null && !customCssPath.isEmpty()) {
                            if (customCssPath.equals("css/style.css")) {
                                return "  <link rel=\"stylesheet\" href=\"css/style.css\">\n";
                            }
                            return "  <link rel=\"stylesheet\" href=\"" + customCssPath + "\">\n";
                        }
                    }
                }
            } catch (Exception e) {
                Log.w("ExportManager", "Error reading page metadata: " + e.getMessage());
            }
        }
        return "  <link rel=\"stylesheet\" href=\"css/style.css\">\n";
    }

    public static boolean isScriptReferenced(String script, String curJs, String allHtml) {
        if (script == null || script.trim().isEmpty()) return false;
        String cur = (curJs != null ? curJs : "") + " " + (allHtml != null ? allHtml : "");
        String s = script.toLowerCase();
        if (s.contains("setprogress") && !cur.toLowerCase().contains("progress")) return false;
        if (s.contains("togglenavbarmenu") && !cur.toLowerCase().contains("nav")) return false;
        if (s.contains("submitform") && !cur.toLowerCase().contains("form")) return false;
        if (s.contains("filtertable") && !cur.toLowerCase().contains("table")) return false;
        if (s.contains("openmodal") && !cur.toLowerCase().contains("modal")) return false;
        if (s.contains("toggleaccordion") && !cur.toLowerCase().contains("accordion")) return false;
        if (s.contains("carouselnext") && !cur.toLowerCase().contains("carousel")) return false;
        if (s.contains("showtoast") && !cur.toLowerCase().contains("toast")) return false;
        if (s.contains("toggledropdown") && !cur.toLowerCase().contains("dropdown")) return false;
        if (s.contains("setrating") && !cur.toLowerCase().contains("rating")) return false;
        if (s.contains("animatecounter") && !cur.toLowerCase().contains("counter")) return false;

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("function\\s+([a-zA-Z0-9_$]+)\\s*\\(").matcher(script);
        boolean hasFunc = false;
        while (m.find()) {
            hasFunc = true;
            String fName = m.group(1);
            if (curJs != null && curJs.contains(fName)) return true;
            if (allHtml != null && allHtml.contains(fName)) return true;
        }
        return !hasFunc;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private String camelToKebab(String str) {
        if (str == null) return "";
        return str.replaceAll("([A-Z])", "-$1").toLowerCase();
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "project";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}
