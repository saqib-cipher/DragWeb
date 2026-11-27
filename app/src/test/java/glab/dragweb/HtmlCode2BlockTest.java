package glab.dragweb;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import glab.dragweb.codegen.HtmlCode2Block;
import glab.dragweb.codegen.HtmlCssImporter;
import glab.dragweb.codegen.PageCodeGenerator;
import glab.dragweb.logic.BlockBean;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class HtmlCode2BlockTest {

    @Test
    public void testSeparatePropertiesAndTwoWaySync() {
        // 1. Construct widget block tree mimicking DayGreen reference:
        // Convert : p
        //   subStack1 (Properties):
        //     id="p_1" -> class="bek" -> width 250px -> height 120px -> color #ff0000 -> text "Hello World"
        ArrayList<BlockBean> blocks = new ArrayList<>();

        BlockBean viewBlock = new BlockBean();
        viewBlock.id = "10";
        viewBlock.opCode = "html_convert";
        viewBlock.spec = "Convert : %m.htmlTag";
        viewBlock.parameters.add("p");
        viewBlock.subStack1 = 11;
        viewBlock.subStack2 = -1;
        viewBlock.nextBlock = -1;
        viewBlock.isConnectedToRoot = true;
        blocks.add(viewBlock);

        // id
        BlockBean idBlock = new BlockBean();
        idBlock.id = "11";
        idBlock.opCode = "htmlAttrId";
        idBlock.parameters.add("p_1");
        idBlock.nextBlock = 12;
        blocks.add(idBlock);

        // class
        BlockBean classBlock = new BlockBean();
        classBlock.id = "12";
        classBlock.opCode = "htmlAttrClass";
        classBlock.parameters.add("bek");
        classBlock.nextBlock = 13;
        blocks.add(classBlock);

        // width
        BlockBean widthBlock = new BlockBean();
        widthBlock.id = "13";
        widthBlock.opCode = "setWidth";
        widthBlock.parameters.add("250px");
        widthBlock.nextBlock = 14;
        blocks.add(widthBlock);

        // height
        BlockBean heightBlock = new BlockBean();
        heightBlock.id = "14";
        heightBlock.opCode = "setHeight";
        heightBlock.parameters.add("120px");
        heightBlock.nextBlock = 15;
        blocks.add(heightBlock);

        // color
        BlockBean colorBlock = new BlockBean();
        colorBlock.id = "15";
        colorBlock.opCode = "setColor";
        colorBlock.parameters.add("#ff0000");
        colorBlock.nextBlock = 16;
        blocks.add(colorBlock);

        // text
        BlockBean textBlock = new BlockBean();
        textBlock.id = "16";
        textBlock.opCode = "htmlText";
        textBlock.parameters.add("Hello World");
        textBlock.nextBlock = -1;
        blocks.add(textBlock);

        // 2. Generate HTML from blocks
        String html = HtmlCode2Block.generateHtmlFromBlocks(null, blocks);
        assertNotNull("Generated HTML should not be null", html);
        assertTrue("HTML should contain id=\"p_1\"", html.contains("id=\"p_1\""));
        assertTrue("HTML should contain class=\"bek\"", html.contains("class=\"bek\""));
        assertTrue("HTML should aggregate styles into style attribute", html.contains("style="));
        assertTrue("Style should contain width: 250px", html.contains("width: 250px"));
        assertTrue("Style should contain height: 120px", html.contains("height: 120px"));
        assertTrue("Style should contain color: #ff0000", html.contains("color: #ff0000"));
        assertTrue("HTML should contain Hello World", html.contains("Hello World"));
        assertTrue("HTML should end with </p>", html.contains("</p>"));

        // 3. Import HTML back into widget tree
        HtmlCssImporter importer = new HtmlCssImporter(null);
        HtmlCssImporter.ImportResult result = importer.importHtmlCss(html, null);
        assertTrue("Import must succeed", result.success);
        assertNotNull("Widget tree must exist", result.widgetTree);
        assertEquals("Must have 1 root widget", 1, result.widgetTree.size());

        Map<String, Object> node = result.widgetTree.get(0);
        assertEquals("Tag must be p", "p", node.get("tag"));
        Map<String, Object> function = (Map<String, Object>) node.get("function");
        assertNotNull("function map must exist", function);
        assertEquals("id must be p_1", "p_1", function.get("id"));
        assertEquals("class must be bek", "bek", function.get("class"));
        assertEquals("text must be Hello World", "Hello World", function.get("text"));

        Map<String, Object> styles = (Map<String, Object>) function.get("style");
        assertNotNull("styles map must exist", styles);
        assertEquals("width must be 250px", "250px", styles.get("width"));
        assertEquals("height must be 120px", "120px", styles.get("height"));
        assertEquals("color must be #ff0000", "#ff0000", styles.get("color"));
    }

    @Test
    public void testGenerateCssFromBlocks() {
        ArrayList<BlockBean> blocks = new ArrayList<>();

        BlockBean selBlock = new BlockBean();
        selBlock.id = "1";
        selBlock.opCode = "cssSelector";
        selBlock.parameters.add(".mycard");
        selBlock.subStack1 = 2;
        selBlock.nextBlock = -1;
        blocks.add(selBlock);

        BlockBean widthBlock = new BlockBean();
        widthBlock.id = "2";
        widthBlock.opCode = "setWidth";
        widthBlock.parameters.add("100%");
        widthBlock.nextBlock = 3;
        blocks.add(widthBlock);

        BlockBean heightBlock = new BlockBean();
        heightBlock.id = "3";
        heightBlock.opCode = "setHeight";
        heightBlock.parameters.add("200px");
        heightBlock.nextBlock = -1;
        blocks.add(heightBlock);

        String css = HtmlCode2Block.generateCssFromBlocks(null, blocks);
        assertNotNull(css);
        assertTrue("Must contain selector .mycard", css.contains(".mycard {"));
        assertTrue("Must contain width: 100%", css.contains("width: 100%;"));
        assertTrue("Must contain height: 200px", css.contains("height: 200px;"));
    }

    @Test
    public void testBlocksJsonContainsNoInbuiltHtmlBlocks() throws Exception {
        File f = new File("src/main/assets/blocks.json");
        if (!f.exists()) {
            f = new File("app/src/main/assets/blocks.json");
        }
        assertTrue("blocks.json must exist for test", f.exists());
        String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertFalse("blocks.json must not contain category html", content.contains("\"category\": \"html\""));
        assertFalse("blocks.json must not contain category html_attr", content.contains("\"category\": \"html_attr\""));
    }

    @Test
    public void testViewConvertAndCanvasRoundTrip() {
        ArrayList<BlockBean> blocks = new ArrayList<>();

        BlockBean section = new BlockBean();
        section.id = "10";
        section.opCode = "view_convert";
        section.type = "e";
        section.spec2 = "children";
        section.parameters.add("section");
        section.subStack1 = 11;
        section.subStack2 = 13;
        section.isConnectedToRoot = true;
        blocks.add(section);

        BlockBean dataAttr = new BlockBean();
        dataAttr.id = "11";
        dataAttr.opCode = "htmlAttrData";
        dataAttr.parameters.add("theme");
        dataAttr.parameters.add("dark");
        dataAttr.nextBlock = 12;
        blocks.add(dataAttr);

        BlockBean clsAttr = new BlockBean();
        clsAttr.id = "12";
        clsAttr.opCode = "htmlAttrClass";
        clsAttr.parameters.add("hero");
        blocks.add(clsAttr);

        BlockBean inputLeaf = new BlockBean();
        inputLeaf.id = "13";
        inputLeaf.opCode = "view_convert_leaf";
        inputLeaf.type = "c";
        inputLeaf.parameters.add("input");
        inputLeaf.subStack1 = 14;
        inputLeaf.nextBlock = 16;
        blocks.add(inputLeaf);

        BlockBean nameAttr = new BlockBean();
        nameAttr.id = "14";
        nameAttr.opCode = "htmlAttrName";
        nameAttr.parameters.add("email");
        nameAttr.nextBlock = 15;
        blocks.add(nameAttr);

        BlockBean reqAttr = new BlockBean();
        reqAttr.id = "15";
        reqAttr.opCode = "htmlAttrRequired";
        blocks.add(reqAttr);

        BlockBean btnLeaf = new BlockBean();
        btnLeaf.id = "16";
        btnLeaf.opCode = "view_convert_leaf";
        btnLeaf.type = "c";
        btnLeaf.parameters.add("button");
        btnLeaf.subStack1 = 17;
        blocks.add(btnLeaf);

        BlockBean btnText = new BlockBean();
        btnText.id = "17";
        btnText.opCode = "htmlText";
        btnText.parameters.add("Go");
        blocks.add(btnText);

        String html = HtmlCode2Block.generateHtmlFromBlocks(null, blocks);
        assertTrue(html.contains("<section"));
        assertTrue(html.contains("data-theme=\"dark\""));
        assertTrue(html.contains("name=\"email\""));
        assertTrue(html.contains("required"));
        assertTrue(html.contains("<button>Go</button>"));

        HtmlCssImporter importer = new HtmlCssImporter(null);
        HtmlCssImporter.ImportResult res = importer.importHtmlCssForBlockSync(html);
        assertTrue(res.success);
        assertNotNull(res.widgetTree);
        assertEquals(1, res.widgetTree.size());

        PageCodeGenerator pcg = new PageCodeGenerator();
        String regenerated = pcg.generateBodyHtmlFromTree(res.widgetTree);
        assertTrue("Tag section must survive round-trip: " + regenerated, regenerated.contains("<section"));
        assertTrue("data-theme must survive round-trip: " + regenerated, regenerated.contains("data-theme=\"dark\""));
        assertTrue("name attribute must survive round-trip: " + regenerated, regenerated.contains("name=\"email\""));
        assertTrue("required attribute must survive round-trip: " + regenerated, regenerated.contains("required"));
        assertTrue("button text must survive round-trip: " + regenerated, regenerated.contains("Go</button>"));
    }

    @Test
    public void testThemeCssLockAndBlocksJsonShapes() throws Exception {
        assertTrue(glab.dragweb.activities.LogicBlockActivity.isLockedThemeCss("theme.css"));
        assertTrue(glab.dragweb.activities.LogicBlockActivity.isLockedThemeCss("css/theme.css"));
        assertTrue(glab.dragweb.activities.LogicBlockActivity.isLockedThemeCss("css_theme"));
        assertFalse(glab.dragweb.activities.LogicBlockActivity.isLockedThemeCss("css/style.css"));

        File f = new File("src/main/assets/blocks.json");
        if (!f.exists()) {
            f = new File("app/src/main/assets/blocks.json");
        }
        String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertFalse("blocks.json must not have blockType normal", content.contains("\"blockType\": \"normal\""));
        assertFalse("blocks.json must not have type normal", content.contains("\"type\": \"normal\""));
        assertFalse("blocks.json must not have $m.unit typo", content.contains("$m.unit"));
        assertTrue("setRadius should use %d %m.unit", content.contains("\"spec\": \"border-radius %d %m.unit\""));
        assertTrue("setWidth should use %d %m.unit", content.contains("\"spec\": \"width %d %m.unit\""));
        assertTrue("setPadding should use %d %m.unit", content.contains("\"spec\": \"padding %d %m.unit\""));
        assertTrue("setRadiusMulti should use 4x %d %m.unit", content.contains("\"spec\": \"border-radius TL %d %m.unit TR %d %m.unit BR %d %m.unit BL %d %m.unit\""));
    }

    @Test
    public void testParamJsonUnitsAndCssUnitBlockCompilation() throws Exception {
        File pf = new File("src/main/assets/param.json");
        if (!pf.exists()) {
            pf = new File("app/src/main/assets/param.json");
        }
        String paramContent = new String(Files.readAllBytes(pf.toPath()), StandardCharsets.UTF_8);
        for (String unit : Arrays.asList("\"px\"", "\"%\"", "\"em\"", "\"rem\"", "\"vw\"", "\"vh\"", "\"vmin\"", "\"vmax\"", "\"ch\"", "\"pt\"", "\"fr\"", "\"deg\"", "\"rad\"", "\"auto\"")) {
            assertTrue("param.json unit list must include " + unit, paramContent.contains(unit));
        }

        ArrayList<BlockBean> cssBlocks = new ArrayList<>();
        BlockBean selector = new BlockBean();
        selector.id = "1";
        selector.opCode = "cssSelector";
        selector.blockType = "c";
        selector.parameters.add(".card");
        selector.subStack1 = 2;
        selector.nextBlock = -1;
        cssBlocks.add(selector);

        BlockBean rSingle = new BlockBean();
        rSingle.id = "2";
        rSingle.opCode = "setRadius";
        rSingle.parameters.addAll(Arrays.asList("8", "px"));
        rSingle.nextBlock = 3;
        cssBlocks.add(rSingle);

        BlockBean rMulti = new BlockBean();
        rMulti.id = "3";
        rMulti.opCode = "setRadiusMulti";
        rMulti.parameters.addAll(Arrays.asList("4", "px", "8", "px", "12", "px", "16", "px"));
        rMulti.nextBlock = 4;
        cssBlocks.add(rMulti);

        BlockBean widthAuto = new BlockBean();
        widthAuto.id = "4";
        widthAuto.opCode = "setWidth";
        widthAuto.parameters.addAll(Arrays.asList("0", "auto"));
        widthAuto.nextBlock = -1;
        cssBlocks.add(widthAuto);

        String css = HtmlCode2Block.generateCssFromBlocks(null, cssBlocks);
        assertTrue("Single-value radius must render 8px: " + css, css.contains("border-radius: 8px;"));
        assertTrue("Multi-value radius must render 4px 8px 12px 16px: " + css, css.contains("border-radius: 4px 8px 12px 16px;"));
        assertTrue("auto unit must render width: auto: " + css, css.contains("width: auto;"));
    }
}

