package glab.dragweb;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import glab.dragweb.codegen.HtmlCssImporter;
import glab.dragweb.data.DesignDataManager;
import glab.dragweb.logic.BlockBean;
import glab.dragweb.logic.BlockCodeCompiler;
import com.google.gson.Gson;

public class CodeToBlockTest {

    @Test
    public void testConvertRawMapsToBeans() {
        System.out.println("TEST CWD: " + new java.io.File(".").getAbsolutePath());
        System.out.println("TEST DEFS: " + glab.dragweb.logic.BlockDef.getDefinitions(null).size());
        HtmlCssImporter importer = new HtmlCssImporter(null);

        List<Map<String, Object>> rawBlocks = new ArrayList<>();

        Map<String, Object> block1 = new HashMap<>();
        block1.put("id", "1001");
        block1.put("action", "commentBlockCss");
        List<String> params1 = new ArrayList<>();
        params1.add("My CSS Comment");
        block1.put("paramValues", params1);
        rawBlocks.add(block1);

        Map<String, Object> block2 = new HashMap<>();
        block2.put("id", "1002");
        block2.put("action", "unknownCustomOp");
        List<String> params2 = new ArrayList<>();
        params2.add("alert(1);");
        block2.put("paramValues", params2);
        rawBlocks.add(block2);

        ArrayList<BlockBean> beans = importer.convertRawMapsToBeans(rawBlocks);

        Assert.assertNotNull(beans);
        Assert.assertEquals(2, beans.size());

        // Block 1 check - matches commentBlockCss in blocks.json
        BlockBean bean1 = beans.get(0);
        Assert.assertEquals("commentBlockCss", bean1.opCode);
        Assert.assertEquals(1, bean1.parameters.size());
        Assert.assertEquals("My CSS Comment", bean1.parameters.get(0));

        // Block 2 check - unknown action must become asdN stack block
        BlockBean bean2 = beans.get(1);
        Assert.assertEquals("asdN", bean2.opCode);
        Assert.assertEquals(" ", bean2.type);
        Assert.assertEquals("normal", bean2.blockType);
        Assert.assertEquals(1, bean2.parameters.size());
        Assert.assertEquals("alert(1);", bean2.parameters.get(0));
    }

    @Test
    public void testValueBlockIdRemapping() {
        HtmlCssImporter importer = new HtmlCssImporter(null);

        List<Map<String, Object>> rawBlocks = new ArrayList<>();

        // Child value block
        Map<String, Object> childBlock = new HashMap<>();
        childBlock.put("id", "child_99");
        childBlock.put("action", "jsQuerySelector");
        List<String> childParams = new ArrayList<>();
        childParams.add("#btn");
        childBlock.put("paramValues", childParams);
        rawBlocks.add(childBlock);

        // Parent block referencing child
        Map<String, Object> parentBlock = new HashMap<>();
        parentBlock.put("id", "parent_100");
        parentBlock.put("action", "jsSetInnerHTML2");
        List<String> parentParams = new ArrayList<>();
        parentParams.add("@child_99");
        parentParams.add("Click me");
        parentBlock.put("paramValues", parentParams);
        rawBlocks.add(parentBlock);

        ArrayList<BlockBean> beans = importer.convertRawMapsToBeans(rawBlocks);
        Assert.assertEquals(2, beans.size());

        BlockBean childBean = beans.get(0);
        BlockBean parentBean = beans.get(1);

        // Parent's parameter should now reference @" + childBean.id
        Assert.assertEquals("@" + childBean.id, parentBean.parameters.get(0));
        Assert.assertEquals("Click me", parentBean.parameters.get(1));
    }

    @Test
    public void testDeserializePageLogicDataRawFallback() {
        // Test JSON with raw map array
        List<Map<String, Object>> rawList = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("id", "55");
        item.put("action", "asdN");
        List<String> p = new ArrayList<>();
        p.add("console.log('test')");
        item.put("paramValues", p);
        rawList.add(item);

        String json = new Gson().toJson(rawList);

        DesignDataManager.PageLogicData data = DesignDataManager.deserializePageLogicData(json, "test_page");
        Assert.assertNotNull(data);
        Assert.assertNotNull(data.blocks);
        Assert.assertTrue(data.blocks.containsKey("test_page"));

        ArrayList<BlockBean> pageBlocks = data.blocks.get("test_page");
        Assert.assertNotNull(pageBlocks);
        Assert.assertEquals(1, pageBlocks.size());
        Assert.assertEquals("asdN", pageBlocks.get(0).opCode);
        Assert.assertEquals("console.log('test')", pageBlocks.get(0).parameters.get(0));
    }

    @Test
    public void testImportJsToBeansAndCompile() {
        HtmlCssImporter importer = new HtmlCssImporter(null);
        String js = "console.log('hello world');";
        ArrayList<BlockBean> beans = importer.importJsToBeans(js);

        Assert.assertNotNull(beans);
        Assert.assertFalse("Should parse JS into beans", beans.isEmpty());

        BlockBean bean = beans.get(0);
        Assert.assertEquals("jsConsoleLog", bean.opCode);
        Assert.assertEquals("hello world", bean.parameters.get(0));

        BlockCodeCompiler compiler = new BlockCodeCompiler(null, "test_proj");
        String compiled = compiler.getSource(0, beans);
        Assert.assertNotNull(compiled);
        Assert.assertTrue("Compiled code should contain console.log", compiled.contains("console.log(\"hello world\")"));
    }

    @Test
    public void testImportCssToBeans() {
        HtmlCssImporter importer = new HtmlCssImporter(null);
        String css = "body { background-color: #ff0000; }";
        ArrayList<BlockBean> beans = importer.importCssToBeans(css);

        Assert.assertNotNull(beans);
        Assert.assertFalse("Should parse CSS into beans", beans.isEmpty());

        // Selector block + property block
        Assert.assertTrue(beans.size() >= 2);
        Assert.assertEquals("cssSelector", beans.get(0).opCode);
        Assert.assertEquals("setBackgroundColor", beans.get(1).opCode);
        Assert.assertEquals("#ff0000", beans.get(1).parameters.get(0));
    }

    @Test
    public void testCompileAsdNBlock() {
        BlockCodeCompiler compiler = new BlockCodeCompiler(null, "test_proj");
        ArrayList<BlockBean> blocks = new ArrayList<>();
        BlockBean asd = new BlockBean();
        asd.id = "1";
        asd.opCode = "asdN";
        asd.parameters.add("const answer = 42;");
        blocks.add(asd);

        String compiled = compiler.getSource(0, blocks);
        Assert.assertNotNull(compiled);
        Assert.assertTrue("Compiled code should contain raw statement", compiled.contains("const answer = 42;"));
    }
}
