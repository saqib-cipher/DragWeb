package glab.dragweb;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import glab.dragweb.activities.LogicBlockActivity;
import glab.dragweb.codegen.ExportManager;
import glab.dragweb.codegen.ProjectCodeGenerator;
import glab.dragweb.data.DesignDataManager;
import glab.dragweb.data.PageManager;
import glab.dragweb.data.ThemeManager;
import glab.dragweb.logic.BlockBean;
import glab.dragweb.logic.BlockDef;
import glab.dragweb.logic.ChipInput;
import glab.dragweb.util.WebCodeFormatter;

public class ThemeAndResetLogicTest {

    @Test
    public void testThemeManagerPreservesGlobalCssWhenWidgetStylingSkipped() {
        ThemeManager tm = new ThemeManager();
        tm.setDisableAllStyling(true);
        tm.setUseInlineStyles(false);

        String globalCss = tm.generateGlobalCss();
        assertNotNull(globalCss);
        assertFalse("Global theme CSS must not be blanked when skipping widget property styles", globalCss.trim().isEmpty());
        assertTrue(globalCss.contains(":root"));
        assertTrue(globalCss.contains("body"));
    }

    @Test
    public void testDesignDataManagerCleanNamesAndReset() {
        DesignDataManager.initMaps();
        assertEquals("css_style", DesignDataManager.getCleanPageName("css/style.css"));
        assertEquals("css_style", DesignDataManager.getCleanPageName("styles.css"));
        assertEquals("js_script", DesignDataManager.getCleanPageName("js/script.js"));
        assertEquals("js_script", DesignDataManager.getCleanPageName("script.js"));
        assertEquals("css_custom", DesignDataManager.getCleanPageName("css/custom.css"));

        assertEquals("styles.css", DesignDataManager.getCanonicalTargetName("css_style"));
        assertEquals("script.js", DesignDataManager.getCanonicalTargetName("js_script"));
        assertEquals("css_custom.css", DesignDataManager.getCanonicalTargetName("css_custom"));
        assertEquals("js_app.js", DesignDataManager.getCanonicalTargetName("js_app"));

        ArrayList<BlockBean> blocks = new ArrayList<>();
        BlockBean b = new BlockBean();
        b.id = "10";
        b.opCode = "cssSelector";
        blocks.add(b);
        DesignDataManager.setBlocks("css/style.css", "onCreate_initializeLogic", blocks);
        assertEquals(1, DesignDataManager.getBlocks("css/style.css", "onCreate_initializeLogic").size());

        DesignDataManager.clearTargetLogic(null, null, "css/style.css");
        assertTrue(DesignDataManager.getBlocks("css/style.css", "onCreate_initializeLogic").isEmpty());
        assertTrue(DesignDataManager.getBlocks("styles.css", "onCreate_initializeLogic").isEmpty());
    }

    @Test
    public void testResolveLinkedCssFilePerPageConfig() {
        PageManager.PageConfig globalCfg = new PageManager.PageConfig("index");
        globalCfg.useGlobalCss = true;
        globalCfg.cssFile = "css/custom.css";
        assertEquals("css/style.css", ProjectCodeGenerator.resolvePageCssFile(globalCfg));

        PageManager.PageConfig customCfg = new PageManager.PageConfig("about");
        customCfg.useGlobalCss = false;
        customCfg.cssFile = "/css/about.css";
        assertEquals("css/about.css", ProjectCodeGenerator.resolvePageCssFile(customCfg));

        assertEquals("css/about.css", ProjectCodeGenerator.resolveTargetCssRelPath("css_about"));
        assertEquals("css/style.css", ProjectCodeGenerator.resolveTargetCssRelPath("styles.css"));
    }

    @Test
    public void testMergeCssPropertyAndUserBlocksPreservesUserRulesWithoutIdCollision() {
        ArrayList<BlockBean> props = new ArrayList<>();
        BlockBean pRule1 = new BlockBean();
        pRule1.id = "10";
        pRule1.opCode = "cssSelector";
        pRule1.isConnectedToRoot = true;
        pRule1.nextBlock = 12;
        pRule1.subStack1 = 11;
        pRule1.parameters = new ArrayList<>(Collections.singletonList("#btn1"));

        BlockBean pDecl1 = new BlockBean();
        pDecl1.id = "11";
        pDecl1.opCode = "setColor";
        pDecl1.parameters = new ArrayList<>(Collections.singletonList("#ffffff"));

        BlockBean pRule2 = new BlockBean();
        pRule2.id = "12";
        pRule2.opCode = "cssSelector";
        pRule2.subStack1 = 13;
        pRule2.parameters = new ArrayList<>(Collections.singletonList("#card1"));

        BlockBean pDecl2 = new BlockBean();
        pDecl2.id = "13";
        pDecl2.opCode = "setPadding";
        pDecl2.parameters = new ArrayList<>(java.util.Arrays.asList("16", "px"));

        Collections.addAll(props, pRule1, pDecl1, pRule2, pDecl2);

        // User already customized #btn1 in their own blocks using overlapping IDs 10..11
        ArrayList<BlockBean> user = new ArrayList<>();
        BlockBean uRule = new BlockBean();
        uRule.id = "10";
        uRule.opCode = "cssSelector";
        uRule.isConnectedToRoot = true;
        uRule.subStack1 = 11;
        uRule.parameters = new ArrayList<>(Collections.singletonList("#btn1"));

        BlockBean uDecl = new BlockBean();
        uDecl.id = "11";
        uDecl.opCode = "setColor";
        uDecl.parameters = new ArrayList<>(Collections.singletonList("#ff0000"));
        Collections.addAll(user, uRule, uDecl);

        ArrayList<BlockBean> merged = LogicBlockActivity.mergeCssPropertyAndUserBlocks(props, user);
        // Should keep #card1 (2 blocks) + user's #btn1 (2 blocks, shifted IDs) = 4 blocks, NO duplicate #btn1
        assertEquals(4, merged.size());
        Set<String> uniqueIds = new HashSet<>();
        int rootCount = 0;
        for (BlockBean mb : merged) {
            assertTrue("IDs must not collide after merge: " + mb.id, uniqueIds.add(mb.id));
            if (mb.isConnectedToRoot) rootCount++;
        }
        assertEquals("Only one block in merged chain should connect to root", 1, rootCount);
        assertEquals("#card1", merged.get(0).parameters.get(0));
        assertTrue("Tail property rule should chain to shifted user block", merged.get(0).nextBlock >= 0);
    }

    @Test
    public void testWebCodeFormatterHtmlCssJsJson() {
        String html = WebCodeFormatter.format("<div><p>Hello</p><button>Go</button></div>", "htmlmixed");
        assertTrue(html.contains("<div>\n  <p>Hello</p>\n  <button>Go</button>\n</div>"));

        String css = WebCodeFormatter.format(".btn{color:red;padding:8px;}", "css");
        assertTrue(css.contains(".btn {\n  color: red;\n  padding: 8px;\n}"));

        String js = WebCodeFormatter.format("function hi(){console.log(\"a\");}", "javascript");
        assertTrue(js.contains("function hi() {\n  console.log(\"a\");\n}"));

        String json = WebCodeFormatter.format("{\"a\":1,\"b\":2}", "json");
        assertTrue(json.contains("\n  \"a\": 1"));
    }

    @Test
    public void testCanonicalizeEventKeyAndDeduplicateAliasedBlocks() {
        assertEquals("onPageLoad_onPageLoad", DesignDataManager.canonicalizeEventKey("css_style", "onPageLoad_onPageLoad"));
        assertEquals("onPageLoad_onPageLoad", DesignDataManager.canonicalizeEventKey("css_style", "onCreate_initializeLogic"));
        assertEquals("onPageLoad_onPageLoad", DesignDataManager.canonicalizeEventKey("css_style", "immediate"));
        assertEquals("onPageLoad_onPageLoad", DesignDataManager.canonicalizeEventKey("css_style", "css_style"));
        assertEquals("btn1_onClick", DesignDataManager.canonicalizeEventKey("index", "btn1_onClick"));

        // Populate aliased keys with an ASD CSS block and verify compileBlocksMapForMode emits it ONCE
        ArrayList<BlockBean> ruleBlocks = new ArrayList<>();
        BlockBean asdCss = new BlockBean();
        asdCss.id = "1";
        asdCss.opCode = "asd_source";
        asdCss.category = "asd";
        asdCss.isConnectedToRoot = true;
        asdCss.parameters = new ArrayList<>(Collections.singletonList("#heroTitle { color: #112233; }"));
        ruleBlocks.add(asdCss);

        Map<String, ArrayList<BlockBean>> aliasedMap = new LinkedHashMap<>();
        aliasedMap.put("onCreate_initializeLogic", ruleBlocks);
        aliasedMap.put("onPageLoad_onPageLoad", ruleBlocks);
        aliasedMap.put("immediate", ruleBlocks);
        aliasedMap.put("css_style", ruleBlocks);

        String compiled = ExportManager.compileBlocksMapForMode(null, null, "css_style", aliasedMap, "css", null);
        int first = compiled.indexOf("#heroTitle");
        assertTrue("Expected compiled CSS to contain #heroTitle selector, got: " + compiled, first >= 0);
        assertEquals("Aliased page-load keys must not emit #heroTitle twice", -1, compiled.indexOf("#heroTitle", first + 1));

        // Verify JS mode also deduplicates aliased page-load keys while preserving distinct events
        ArrayList<BlockBean> loadJsBlocks = new ArrayList<>();
        BlockBean loadJs = new BlockBean();
        loadJs.id = "1";
        loadJs.opCode = "asd_source";
        loadJs.category = "asd";
        loadJs.isConnectedToRoot = true;
        loadJs.parameters = new ArrayList<>(Collections.singletonList("console.log('initOnce');"));
        loadJsBlocks.add(loadJs);

        ArrayList<BlockBean> clickJsBlocks = new ArrayList<>();
        BlockBean clickJs = new BlockBean();
        clickJs.id = "2";
        clickJs.opCode = "asd_source";
        clickJs.category = "asd";
        clickJs.isConnectedToRoot = true;
        clickJs.parameters = new ArrayList<>(Collections.singletonList("console.log('clicked');"));
        clickJsBlocks.add(clickJs);

        Map<String, ArrayList<BlockBean>> jsMap = new LinkedHashMap<>();
        jsMap.put("onCreate_initializeLogic", loadJsBlocks);
        jsMap.put("onPageLoad_onPageLoad", loadJsBlocks);
        jsMap.put("immediate", loadJsBlocks);
        jsMap.put("btn1_onClick", clickJsBlocks);

        String compiledJs = ExportManager.compileBlocksMapForMode(null, null, "index", jsMap, "js", null);
        int firstInit = compiledJs.indexOf("initOnce");
        assertTrue("Expected compiled JS to contain initOnce", firstInit >= 0);
        assertEquals("Aliased page-load keys must not emit JS twice", -1, compiledJs.indexOf("initOnce", firstInit + 1));
        assertTrue("Distinct event handlers must still be emitted", compiledJs.contains("clicked"));

        PageManager.PageConfig globalJsCfg = new PageManager.PageConfig("index");
        globalJsCfg.useGlobalJs = true;
        globalJsCfg.jsFile = "js/custom.js";
        assertEquals("js/script.js", ProjectCodeGenerator.resolvePageJsFile(globalJsCfg));

        PageManager.PageConfig customJsCfg = new PageManager.PageConfig("about");
        customJsCfg.useGlobalJs = false;
        customJsCfg.jsFile = "/js/about.js";
        assertEquals("js/about.js", ProjectCodeGenerator.resolvePageJsFile(customJsCfg));
    }

    @Test
    public void testFilterDuplicateCssSelectorsAndSkipPropertyStyleBlocks() {
        String elemCss = "#h2_2 {\n  font-size: 28px;\n}\n\n#newCard {\n  padding: 12px;\n}\n";
        String logicCss = "#h2_2 {\n  font-size: 32px;\n}\n";
        String filtered = ProjectCodeGenerator.filterOutExistingCssSelectors(elemCss, logicCss);
        assertFalse("Existing selector #h2_2 must not be duplicated from properties", filtered.contains("#h2_2"));
        assertTrue("Unrepresented selector #newCard must remain", filtered.contains("#newCard"));

        // Verify mergeCssPropertyAndUserBlocks marks property blocks and skipPropertyStyleBlocks filters them
        ArrayList<BlockBean> propBlocks = new ArrayList<>();
        BlockBean prop = new BlockBean();
        prop.id = "1";
        prop.opCode = "asd_source";
        prop.category = "asd";
        prop.isConnectedToRoot = true;
        prop.parameters = new ArrayList<>(Collections.singletonList("#h2_2 { font-size: 28px; }"));
        propBlocks.add(prop);

        ArrayList<BlockBean> userBlocks = new ArrayList<>();
        BlockBean user = new BlockBean();
        user.id = "2";
        user.opCode = "asd_source";
        user.category = "asd";
        user.isConnectedToRoot = true;
        user.parameters = new ArrayList<>(Collections.singletonList(".custom-btn { color: red; }"));
        userBlocks.add(user);

        ArrayList<BlockBean> merged = LogicBlockActivity.mergeCssPropertyAndUserBlocks(propBlocks, userBlocks);
        assertTrue(merged.get(0).isPropertyStyle);
        assertFalse(merged.get(1).isPropertyStyle);

        Map<String, ArrayList<BlockBean>> blockMap = new LinkedHashMap<>();
        blockMap.put("onCreate_initializeLogic", merged);

        String onlyUserCss = ExportManager.compileBlocksMapForMode(null, null, "css_style", blockMap, "css", null, true);
        assertFalse("Property style blocks must be skipped when skipPropertyStyleBlocks is true", onlyUserCss.contains("#h2_2"));
        assertTrue("User style blocks must be preserved", onlyUserCss.contains(".custom-btn"));
    }

    @Test
    public void testRewrittenAssetsIntegrityColorChipsAndJsRoundtrip() throws Exception {
        File assetsDir = new File("app/src/main/assets");
        if (!assetsDir.exists()) {
            assetsDir = new File("src/main/assets");
        }
        assertTrue("Assets dir must exist", assetsDir.exists());

        String catsJson = new String(Files.readAllBytes(new File(assetsDir, "categories.json").toPath()), StandardCharsets.UTF_8);
        String paramsJson = new String(Files.readAllBytes(new File(assetsDir, "param.json").toPath()), StandardCharsets.UTF_8);
        String blocksJson = new String(Files.readAllBytes(new File(assetsDir, "blocks.json").toPath()), StandardCharsets.UTF_8);

        Gson gson = new Gson();
        Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
        Type mapListType = new TypeToken<Map<String, List<String>>>() {}.getType();
        List<Map<String, Object>> categories = gson.fromJson(catsJson, listType);
        Map<String, List<String>> params = gson.fromJson(paramsJson, mapListType);
        BlockDef[] defs = gson.fromJson(blocksJson, BlockDef[].class);

        Set<String> catIds = new HashSet<>();
        Set<String> purpleHex = new HashSet<>(Arrays.asList("#7B1FA2", "#512DA8", "#4527A0", "#9C27B0", "#7E57C2"));
        for (Map<String, Object> c : categories) {
            catIds.add(String.valueOf(c.get("id")));
            Object col = c.get("catColor");
            if (col != null) {
                assertFalse("No purple category color: " + col, purpleHex.contains(col.toString().toUpperCase()));
            }
        }
        assertTrue("param.json must include color", params.containsKey("color"));

        Set<String> seenIds = new HashSet<>();
        Pattern mPat = Pattern.compile("%m\\.([a-zA-Z0-9_]+)");
        Map<String, BlockDef> defMap = new LinkedHashMap<>();
        for (BlockDef d : defs) {
            assertTrue("Duplicate block id: " + d.id, seenIds.add(d.id));
            assertTrue("Missing category on " + d.id + ": " + d.category, catIds.contains(d.category));
            if (d.color != null) {
                assertFalse("No purple block color on " + d.id, purpleHex.contains(d.color.toUpperCase()));
            }
            if ("s".equals(d.blockType) || "d".equals(d.blockType) || "b".equals(d.blockType)) {
                assertFalse("Reporter " + d.id + " code must not end with newline", d.code != null && d.code.endsWith("\n"));
            }
            String combinedSpec = ((d.spec != null ? d.spec : "") + " " + (d.spec2 != null ? d.spec2 : "")).trim();
            assertFalse("Raw %color token must not appear in " + d.id, combinedSpec.contains("%color"));
            Matcher m = mPat.matcher(combinedSpec);
            while (m.find()) {
                String pname = m.group(1);
                if (!"list".equals(pname) && !"fontFamily".equals(pname)) {
                    assertTrue("Missing param key '" + pname + "' for " + d.id, params.containsKey(pname));
                }
            }
            defMap.put(d.id, d);
        }

        // Verify %m.color and legacy %color resolve as color ChipInput
        List<ChipInput> bgInputs = defMap.get("setBackground").resolvedInputs();
        assertEquals(1, bgInputs.size());
        assertEquals("color", bgInputs.get(0).type);
        assertEquals("color", bgInputs.get(0).selector);

        List<ChipInput> navBgInputs = defMap.get("navbarSetBackground").resolvedInputs();
        assertEquals(2, navBgInputs.size());
        assertEquals("color", navBgInputs.get(1).type);

        BlockDef legacyColorDef = new BlockDef();
        legacyColorDef.id = "legacyColor";
        legacyColorDef.spec = "bg %color";
        legacyColorDef.code = "background: %1$s;";
        assertEquals("color", legacyColorDef.resolvedInputs().get(0).type);

        // Verify jsSetTextContent has unquoted receiver (%var -> inputOnly) and auto-quoted text (%s -> !inputOnly)
        List<ChipInput> textInputs = defMap.get("jsSetTextContent").resolvedInputs();
        assertEquals(2, textInputs.size());
        assertTrue(textInputs.get(0).inputOnly);
        assertFalse(textInputs.get(1).inputOnly);

        // Verify jsTryCatch catch variable param from spec2 marks inputOnly=true (so catch(err) is never quoted)
        List<ChipInput> tryInputs = defMap.get("jsTryCatch").resolvedInputs();
        assertTrue(tryInputs.size() >= 1);
        assertTrue(tryInputs.get(0).inputOnly);

        // Verify jsFunctionDefine has 2 inputOnly args (name + params) + body placeholder, no %b bug
        List<ChipInput> fnInputs = defMap.get("jsFunctionDefine").resolvedInputs();
        assertTrue(fnInputs.size() >= 2);
        assertEquals("text", fnInputs.get(0).type);
        assertEquals("text", fnInputs.get(1).type);
        assertTrue(fnInputs.get(0).inputOnly);
        assertTrue(fnInputs.get(1).inputOnly);

        // Verify jsAddEventCallback has 3 params (target, event, callback)
        List<ChipInput> evCbInputs = defMap.get("jsAddEventCallback").resolvedInputs();
        assertEquals(3, evCbInputs.size());
        assertEquals("event", evCbInputs.get(1).selector);

        // Verify ExportManager preserves non-widget CSS rules (`button`, `li`) even when validIds is non-empty
        BlockBean cssRule = new BlockBean();
        cssRule.id = "10";
        cssRule.opCode = "cssSelector";
        cssRule.category = "css";
        cssRule.spec = "Widget %selector";
        cssRule.code = "%1$s {\n %2$s\n}\n";
        cssRule.isConnectedToRoot = true;
        cssRule.subStack1 = 11;
        cssRule.parameters = new ArrayList<>(Collections.singletonList("button"));

        BlockBean cssDecl = new BlockBean();
        cssDecl.id = "11";
        cssDecl.opCode = "setMarginLeft";
        cssDecl.category = "Layout & Dimensions";
        cssDecl.code = "margin-left: %1$s%2$s;\n";
        cssDecl.nextBlock = 12;
        cssDecl.parameters = new ArrayList<>(Arrays.asList("5", "px"));

        BlockBean cssDecl2 = new BlockBean();
        cssDecl2.id = "12";
        cssDecl2.opCode = "asdN";
        cssDecl2.category = "asd";
        cssDecl2.parameters = new ArrayList<>(Collections.singletonList("border: none;"));

        Map<String, ArrayList<BlockBean>> cssMap = new LinkedHashMap<>();
        cssMap.put("onPageLoad_onPageLoad", new ArrayList<>(Arrays.asList(cssRule, cssDecl, cssDecl2)));
        Set<String> validIds = new HashSet<>(Arrays.asList("taskInput", "addBtn"));
        String compiledCss = ExportManager.compileBlocksMapForMode(null, null, "css_style", cssMap, "css", validIds, false);
        assertTrue("Expected button selector preserved, got: " + compiledCss, compiledCss.contains("button {"));
        assertTrue("Expected margin-left nested inside button, got: " + compiledCss, compiledCss.contains("margin-left: 5px;"));
        assertTrue("Expected border: none inside button (not orphaned), got: " + compiledCss, compiledCss.indexOf("border: none;") < compiledCss.lastIndexOf("}"));

        // Verify HTML blocks in page logic do not leak into JS output
        BlockBean htmlTag = new BlockBean();
        htmlTag.id = "20";
        htmlTag.opCode = "html_tag_generic";
        htmlTag.category = "Views";
        htmlTag.code = "<%1$s %2$s>\n%3$s\n</%1$s>";
        htmlTag.isConnectedToRoot = true;
        assertFalse("HTML block must not be eligible for JS", ExportManager.isJsBlockEligible(htmlTag));

        BlockBean htmlText = new BlockBean();
        htmlText.id = "21";
        htmlText.opCode = "html_text";
        htmlText.category = "Text & Typography";
        htmlText.code = "%1$s";
        htmlText.parameters = new ArrayList<>(Collections.singletonList("My To-Do List"));
        htmlText.isConnectedToRoot = true;
        Map<String, ArrayList<BlockBean>> cssWithHtmlText = new LinkedHashMap<>();
        cssWithHtmlText.put("onPageLoad_onPageLoad", new ArrayList<>(Arrays.asList(cssRule, cssDecl, cssDecl2, htmlText)));
        String cssOut = ExportManager.compileBlocksMapForMode(null, null, "css_style", cssWithHtmlText, "css", validIds, false);
        assertFalse("html_text must not leak into CSS output", cssOut.contains("My To-Do List"));

        assertTrue(DesignDataManager.isJsLogicTarget("script.js"));
        assertTrue(DesignDataManager.isJsLogicTarget("js_script"));
        assertFalse(DesignDataManager.isJsLogicTarget("styles.css"));
        assertFalse(DesignDataManager.isJsLogicTarget("index.html"));
        DesignDataManager.PageLogicData emptyData = new DesignDataManager.PageLogicData();
        String cssLogic = DesignDataManager.serializePageLogicData(emptyData, "styles.css");
        assertFalse(cssLogic.contains("_var"));
        assertFalse(cssLogic.contains("_func"));
        String jsLogic = DesignDataManager.serializePageLogicData(emptyData, "script.js");
        assertTrue(jsLogic.contains("@script.js_var"));

        // Verify HTML linting: duplicate ID + missing alt/src on img
        ArrayList<BlockBean> htmlLintBlocks = new ArrayList<>();
        BlockBean img = new BlockBean();
        img.id = "1";
        img.opCode = "view_convert";
        img.category = "Views";
        img.parameters = new ArrayList<>(Collections.singletonList("img"));
        img.subStack1 = -1;
        BlockBean id1 = new BlockBean();
        id1.id = "2";
        id1.opCode = "htmlAttrId";
        id1.category = "html_attr";
        id1.parameters = new ArrayList<>(Collections.singletonList("hero"));
        BlockBean id2 = new BlockBean();
        id2.id = "3";
        id2.opCode = "htmlAttrId";
        id2.category = "html_attr";
        id2.parameters = new ArrayList<>(Collections.singletonList("hero"));
        htmlLintBlocks.add(img);
        htmlLintBlocks.add(id1);
        htmlLintBlocks.add(id2);
        List<glab.dragweb.logic.LogicLinter.LintProblem> htmlProblems =
                glab.dragweb.logic.LogicLinter.lint(null, "p1", "index.html", "onPageLoad", htmlLintBlocks);
        assertTrue(htmlProblems.stream().anyMatch(p -> "html-duplicate-id".equals(p.ruleId)));
        assertTrue(htmlProblems.stream().anyMatch(p -> "html-img-src".equals(p.ruleId)));
        assertTrue(htmlProblems.stream().anyMatch(p -> "html-img-alt".equals(p.ruleId)));

        // Verify JS linting: const reassignment + empty selector + Rhino syntax error
        ArrayList<BlockBean> jsLintBlocks = new ArrayList<>();
        BlockBean cDecl = new BlockBean();
        cDecl.id = "10";
        cDecl.opCode = "jsVarConst";
        cDecl.category = "js_logic";
        cDecl.parameters = new ArrayList<>(Arrays.asList("tax", "10"));
        BlockBean cAssign = new BlockBean();
        cAssign.id = "11";
        cAssign.opCode = "jsVarAssign";
        cAssign.category = "js_logic";
        cAssign.parameters = new ArrayList<>(Arrays.asList("tax", "20"));
        BlockBean badAsd = new BlockBean();
        badAsd.id = "12";
        badAsd.opCode = "asdN";
        badAsd.category = "asd";
        badAsd.parameters = new ArrayList<>(Collections.singletonList("const broken = (;"));
        jsLintBlocks.add(cDecl);
        jsLintBlocks.add(cAssign);
        jsLintBlocks.add(badAsd);
        List<glab.dragweb.logic.LogicLinter.LintProblem> jsProblems =
                glab.dragweb.logic.LogicLinter.lint(null, "p1", "script.js", "onPageLoad", jsLintBlocks);
        assertTrue(jsProblems.stream().anyMatch(p -> "js-const-assignment".equals(p.ruleId)));
        assertTrue(jsProblems.stream().anyMatch(p -> "code-syntax-unbalanced".equals(p.ruleId) || "rhino-syntax-error".equals(p.ruleId)));
    }
}
