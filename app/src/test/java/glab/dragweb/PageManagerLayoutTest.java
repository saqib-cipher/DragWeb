package glab.dragweb;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import glab.dragweb.data.PageManager;
import com.google.gson.Gson;

public class PageManagerLayoutTest {

    @Test
    public void testFlattenAndParseLayoutTree() {
        // Construct a nested tree: div -> [p, button]
        Map<String, Object> div = new LinkedHashMap<>();
        div.put("tag", "div");
        div.put("id", "div_1");
        div.put("class", "my-container");

        List<Map<String, Object>> divChildren = new ArrayList<>();

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tag", "p");
        p.put("id", "p_1");
        p.put("text", "Hello, world!");
        divChildren.add(p);

        Map<String, Object> button = new LinkedHashMap<>();
        button.put("tag", "button");
        button.put("id", "btn_1");
        button.put("text", "Submit");
        divChildren.add(button);

        div.put("children", divChildren);

        List<Map<String, Object>> originalTree = new ArrayList<>();
        originalTree.add(div);

        // 1. Flatten tree
        List<Map<String, Object>> flatList = PageManager.flattenLayoutTree(originalTree);
        assertEquals(3, flatList.size());

        // Check flat nodes
        Map<String, Object> flatDiv = flatList.get(0);
        assertEquals("div_1", flatDiv.get("id"));
        assertEquals("root", flatDiv.get("parent"));
        assertEquals(0, flatDiv.get("index"));
        assertFalse(flatDiv.containsKey("children"));

        Map<String, Object> flatP = flatList.get(1);
        assertEquals("p_1", flatP.get("id"));
        assertEquals("div_1", flatP.get("parent"));
        assertEquals(0, flatP.get("index"));
        assertEquals("Hello, world!", flatP.get("text"));

        Map<String, Object> flatBtn = flatList.get(2);
        assertEquals("btn_1", flatBtn.get("id"));
        assertEquals("div_1", flatBtn.get("parent"));
        assertEquals(1, flatBtn.get("index"));
        assertEquals("Submit", flatBtn.get("text"));

        // 2. Format as flat lines (Sketchware style)
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> node : flatList) {
            sb.append(new Gson().toJson(node)).append("\n");
        }
        String serialized = sb.toString();

        // Verify newline delimited JSON lines
        String[] lines = serialized.trim().split("\n");
        assertEquals(3, lines.length);

        // 3. Reconstruct tree with parseLayoutTree
        List<Map<String, Object>> reconstructedTree = PageManager.parseLayoutTree(serialized);
        assertEquals(1, reconstructedTree.size());

        Map<String, Object> reconDiv = reconstructedTree.get(0);
        assertEquals("div_1", reconDiv.get("id"));
        assertTrue(reconDiv.containsKey("children"));

        List<Map<String, Object>> reconChildren = (List<Map<String, Object>>) reconDiv.get("children");
        assertEquals(2, reconChildren.size());

        Map<String, Object> reconP = reconChildren.get(0);
        assertEquals("p_1", reconP.get("id"));
        assertEquals("Hello, world!", reconP.get("text"));

        Map<String, Object> reconBtn = reconChildren.get(1);
        assertEquals("btn_1", reconBtn.get("id"));
        assertEquals("Submit", reconBtn.get("text"));
    }
}

