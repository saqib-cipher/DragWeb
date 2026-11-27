package glab.dragweb;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import glab.dragweb.codegen.HtmlCssImporter;
import glab.dragweb.codegen.PageCodeGenerator;
import glab.dragweb.data.IconLibraryManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IconLibraryAndNavbarTest {

    @Test
    public void testDetectLibrariesInHtml() {
        String html = "<nav>"
                + "<a class=\"nav-item\"><i class=\"ti ti-home\"></i> Home</a>"
                + "<a class=\"nav-item\"><i class=\"fa-solid fa-user\"></i> User</a>"
                + "<a class=\"nav-item\"><i data-lucide=\"sparkles\"></i> Sparkles</a>"
                + "<a class=\"nav-item\"><i class=\"bi bi-gear\"></i> Settings</a>"
                + "<a class=\"nav-item\"><i class=\"ph ph-bell\"></i> Bell</a>"
                + "<a class=\"nav-item\"><i class=\"ri-mail-line\"></i> Mail</a>"
                + "<i class=\"material-icons mi-star\"></i>"
                + "</nav>";

        Set<String> libs = IconLibraryManager.detectLibrariesInHtml(html);
        assertTrue(libs.contains("tabler-icons"));
        assertTrue(libs.contains("font-awesome"));
        assertTrue(libs.contains("lucide-icons"));
        assertTrue(libs.contains("bootstrap-icons"));
        assertTrue(libs.contains("phosphor-icons"));
        assertTrue(libs.contains("remix-icon"));
        assertTrue(libs.contains("material-icons"));
    }

    @Test
    public void testNavbarGenerationSupportsAllIconLibrariesAndAutoIncludesCdns() {
        Map<String, Object> navFn = new LinkedHashMap<>();
        navFn.put("brand", "DragWeb");
        navFn.put("items", Arrays.asList("Home", "Profile", "Search", "Docs"));
        navFn.put("icons", Arrays.asList("ti ti-home", "fa-solid fa-user", "lucide-search", "book"));

        Map<String, Object> navNode = new LinkedHashMap<>();
        navNode.put("tag", "nav");
        navNode.put("function", navFn);

        List<Map<String, Object>> tree = new ArrayList<>();
        tree.add(navNode);

        PageCodeGenerator gen = new PageCodeGenerator();
        gen.setProjectInfo("TestSite", null);
        String html = gen.generateFullCodeFromTree(tree, null, null);

        // Correct per-library markup instead of hardcoding data-lucide for ti/fa
        assertTrue(html.contains("<div class=\"nav-menu\""));
        assertTrue(html.contains("flex-wrap:wrap"));
        assertTrue(html.contains("<i class=\"ti ti-home\""));
        assertFalse(html.contains("data-lucide=\"ti ti-home\""));
        assertTrue(html.contains("<i class=\"fa-solid fa-user\""));
        assertFalse(html.contains("data-lucide=\"fa-solid fa-user\""));
        assertTrue(html.contains("<i data-lucide=\"search\""));
        assertTrue(html.contains("<i data-lucide=\"book\""));

        // Auto-injected CDN includes for detected libraries
        assertTrue(html.contains("tabler-icons.min.css"));
        assertTrue(html.contains("font-awesome/6.5.1/css/all.min.css"));
        assertTrue(html.contains("lucide@latest/dist/umd/lucide.min.js"));
        assertTrue(html.contains("lucide.createIcons()"));

        // Verify HTML -> Canvas round-trip preserves Navbar brand, items, icons, and lists
        HtmlCssImporter importer = new HtmlCssImporter();
        HtmlCssImporter.ImportResult res = importer.importHtmlCssForBlockSync(html);
        assertTrue(res.success);
        assertNotNull(res.widgetTree);
        assertEquals(1, res.widgetTree.size());
        Map<String, Object> navOut = res.widgetTree.get(0);
        assertEquals("nav", navOut.get("tag"));
        Map<?, ?> fnOut = (Map<?, ?>) navOut.get("function");
        assertNotNull(fnOut);
        assertEquals("DragWeb", fnOut.get("brand"));
        assertEquals(Arrays.asList("Home", "Profile", "Search", "Docs"), fnOut.get("items"));
        assertEquals(Arrays.asList("ti ti-home", "fa-solid fa-user", "search", "book"), fnOut.get("icons"));
        assertFalse(navOut.containsKey("children"));
    }
}
