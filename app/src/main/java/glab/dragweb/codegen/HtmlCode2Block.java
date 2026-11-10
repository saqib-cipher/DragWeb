package glab.dragweb.codegen;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import glab.dragweb.logic.BlockBean;
import glab.dragweb.logic.BlockDef;

/**
 * High-Level Code2Block and Block2Code Conversion Engine for HTML.
 * Converts raw HTML code into hierarchical BlockBean structures and vice-versa,
 * using definitions loaded dynamically from assets/blocks.json (and custom widgets).
 */
public class HtmlCode2Block {

    private static final Set<String> VOID_ELEMENTS = new HashSet<>();
    static {
        Collections.addAll(VOID_ELEMENTS,
                "area", "base", "br", "col", "embed", "hr", "img", "input",
                "link", "meta", "param", "source", "track", "wbr"
        );
    }

    public static class BlockResult {
        public final boolean isSuccess;
        public final ArrayList<BlockBean> blocks;
        public final String errorMessage;

        public BlockResult(ArrayList<BlockBean> blocks) {
            this.isSuccess = true;
            this.blocks = blocks != null ? blocks : new ArrayList<>();
            this.errorMessage = null;
        }

        public BlockResult(String errorMessage) {
            this.isSuccess = false;
            this.blocks = new ArrayList<>();
            this.errorMessage = errorMessage;
        }
    }

    // =========================================================================
    // Public APIs: HTML Code -> BlockBeans
    // =========================================================================

    public static BlockResult convertHtmlCode(Context context, String htmlCode) {
        return glab.dragweb.core.BlocksLoader.convertHtmlCode(context, htmlCode);
    }

    public static BlockResult convertHtmlCodeInternal(Context context, String htmlCode) {
        if (htmlCode == null || htmlCode.trim().isEmpty()) {
            return new BlockResult(new ArrayList<>());
        }
        try {
            ArrayList<BlockBean> blocks = generateHtmlBlocksFromCode(context, htmlCode);
            return new BlockResult(blocks);
        } catch (Exception e) {
            return new BlockResult("HTML Conversion failed: " + e.getMessage());
        }
    }

    public static ArrayList<BlockBean> generateHtmlBlocksFromCode(Context context, String htmlCode) {
        if (htmlCode == null || htmlCode.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<BlockDef> allDefs = BlockDef.getDefinitions(context);
        Map<String, BlockDef> tagMap = new HashMap<>();
        Map<String, BlockDef> attrMap = new HashMap<>();
        BlockDef fallbackContainerDef = null;
        BlockDef fallbackLeafDef = null;
        BlockDef fallbackAttrGenericDef = null;
        BlockDef textDef = null;
        BlockDef commentDef = null;
        BlockDef asdDef = null;

        if (allDefs != null) {
            for (BlockDef def : allDefs) {
                if (def == null) continue;
                String op = def.getOpCode();
                String id = def.id != null ? def.id : "";

                if ("html_convert".equalsIgnoreCase(id) || "html_convert".equalsIgnoreCase(op)) {
                    fallbackContainerDef = def;
                } else if ("html_convert_leaf".equalsIgnoreCase(id) || "html_convert_leaf".equalsIgnoreCase(op)) {
                    fallbackLeafDef = def;
                } else if ("htmlAttrGeneric".equalsIgnoreCase(id) || "htmlAttrGeneric".equalsIgnoreCase(op)) {
                    fallbackAttrGenericDef = def;
                } else if ("htmlText".equalsIgnoreCase(id) || "htmlText".equalsIgnoreCase(op)) {
                    textDef = def;
                } else if ("htmlComment".equalsIgnoreCase(id) || "htmlComment".equalsIgnoreCase(op)) {
                    commentDef = def;
                } else if ("asdHtml".equalsIgnoreCase(id) || "asdN".equalsIgnoreCase(id)) {
                    asdDef = def;
                }

                // Check attribute blocks
                if ("html_attr".equalsIgnoreCase(def.category) || id.startsWith("htmlAttr")) {
                    String cleanAttrName = id.replace("htmlAttr", "").toLowerCase();
                    if (!cleanAttrName.isEmpty()) {
                        attrMap.put(cleanAttrName, def);
                    }
                } else if ("html".equalsIgnoreCase(def.category)) {
                    // Check tag blocks
                    String cleanTagName = id.replace("html", "").toLowerCase();
                    if (!cleanTagName.isEmpty()) {
                        tagMap.put(cleanTagName, def);
                    }
                }
            }
        }

        // Parse HTML tokens into hierarchical node tree
        List<HtmlNode> rootNodes = parseHtmlToNodes(htmlCode);
        List<HtmlNode> bodyNodes = extractBodyChildren(rootNodes);
        if (bodyNodes != null) {
            rootNodes = bodyNodes;
        }
        GeneratorContext ctx = new GeneratorContext(
                tagMap, attrMap, fallbackContainerDef, fallbackLeafDef,
                fallbackAttrGenericDef, textDef, commentDef, asdDef
        );

        BlockBean prevRoot = null;
        for (HtmlNode node : rootNodes) {
            int blockId = processNode(node, ctx);
            if (blockId >= 0) {
                BlockBean currentRoot = ctx.findBlock(blockId);
                if (currentRoot != null) {
                    if (prevRoot != null) {
                        prevRoot.nextBlock = blockId;
                    }
                    prevRoot = currentRoot;
                }
            }
        }

        // Sort blocks by numeric id for deterministic order, matching xml_blocks_generator.rs
        ctx.blocks.sort((b1, b2) -> {
            try {
                return Integer.compare(Integer.parseInt(b1.id), Integer.parseInt(b2.id));
            } catch (Exception e) {
                return 0;
            }
        });

        // Ensure all generated blocks are connected to root and have valid stackIndex
        int idx = 0;
        for (BlockBean b : ctx.blocks) {
            b.isConnectedToRoot = true;
            b.stackIndex = idx++;
        }

        return ctx.blocks;
    }

    // =========================================================================
    // Public APIs: BlockBeans -> HTML Code
    // =========================================================================

    public static String generateHtmlFromBlocks(Context context, ArrayList<BlockBean> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }

        Map<String, BlockBean> map = new HashMap<>();
        Set<Integer> nonRootIds = new HashSet<>();

        for (BlockBean b : blocks) {
            if (b.id != null && !b.id.isEmpty()) {
                map.put(b.id, b);
            }
            if (b.nextBlock >= 0) nonRootIds.add(b.nextBlock);
            if (b.subStack1 >= 0) nonRootIds.add(b.subStack1);
            if (b.subStack2 >= 0) nonRootIds.add(b.subStack2);
            if (b.parameters != null) {
                for (String p : b.parameters) {
                    if (p != null && p.startsWith("@")) {
                        try {
                            nonRootIds.add(Integer.parseInt(p.substring(1)));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        // Collect all root blocks (preserving chain order)
        List<BlockBean> roots = new ArrayList<>();
        for (BlockBean b : blocks) {
            try {
                int idInt = Integer.parseInt(b.id);
                if (!nonRootIds.contains(idInt)) {
                    roots.add(b);
                }
            } catch (NumberFormatException ignored) {
                if (!nonRootIds.contains(-1)) {
                    roots.add(b);
                }
            }
        }

        if (roots.isEmpty()) {
            roots.add(blocks.get(0));
        }

        StringBuilder sb = new StringBuilder();
        Set<String> visited = new HashSet<>();

        for (BlockBean root : roots) {
            BlockBean curr = root;
            while (curr != null && visited.add(curr.id)) {
                renderBlock(curr, map, sb, 0);
                if (curr.nextBlock >= 0) {
                    curr = map.get(String.valueOf(curr.nextBlock));
                } else {
                    break;
                }
            }
        }

        return sb.toString().trim();
    }

    private static void renderBlock(BlockBean block, Map<String, BlockBean> map, StringBuilder sb, int indentLevel) {
        if (block == null) return;

        String indent = "  ".repeat(indentLevel);

        // 1. Text Block
        if ("htmlText".equals(block.opCode)) {
            String text = (!block.parameters.isEmpty() && block.parameters.get(0) != null)
                    ? block.parameters.get(0) : "";
            sb.append(indent).append(text).append("\n");
            return;
        }

        // 2. Comment Block
        if ("htmlComment".equals(block.opCode)) {
            String text = (!block.parameters.isEmpty() && block.parameters.get(0) != null)
                    ? block.parameters.get(0) : "";
            sb.append(indent).append("<!-- ").append(text).append(" -->\n");
            return;
        }

        // 3. Raw Source / ASD Block
        if ("asdHtml".equals(block.opCode) || "asdN".equals(block.opCode) || "addSourceDirectly".equals(block.opCode)) {
            String raw = (!block.parameters.isEmpty() && block.parameters.get(0) != null)
                    ? block.parameters.get(0) : "";
            sb.append(indent).append(raw).append("\n");
            return;
        }

        String tagName = resolveTagNameFromBlock(block);
        boolean isVoid = VOID_ELEMENTS.contains(tagName.toLowerCase());

        sb.append(indent).append("<").append(tagName);

        // Render attributes from subStack1
        if (block.subStack1 >= 0) {
            BlockBean attrCurr = map.get(String.valueOf(block.subStack1));
            Set<String> attrVisited = new HashSet<>();
            while (attrCurr != null && attrVisited.add(attrCurr.id)) {
                String attrStr = formatAttributeBlock(attrCurr);
                if (attrStr != null && !attrStr.isEmpty()) {
                    if (!attrStr.startsWith(" ")) {
                        sb.append(" ");
                    }
                    sb.append(attrStr.trim());
                }
                if (attrCurr.nextBlock >= 0) {
                    attrCurr = map.get(String.valueOf(attrCurr.nextBlock));
                } else {
                    break;
                }
            }
        }

        // Render children or self-close
        if (isVoid) {
            sb.append(" />\n");
        } else {
            if (block.subStack2 >= 0) {
                sb.append(">\n");
                BlockBean childCurr = map.get(String.valueOf(block.subStack2));
                Set<String> childVisited = new HashSet<>();
                while (childCurr != null && childVisited.add(childCurr.id)) {
                    renderBlock(childCurr, map, sb, indentLevel + 1);
                    if (childCurr.nextBlock >= 0) {
                        childCurr = map.get(String.valueOf(childCurr.nextBlock));
                    } else {
                        break;
                    }
                }
                sb.append(indent).append("</").append(tagName).append(">\n");
            } else {
                sb.append("></").append(tagName).append(">\n");
            }
        }
    }

    private static String resolveTagNameFromBlock(BlockBean block) {
        if (block == null) return "div";
        if (block.parameters != null && !block.parameters.isEmpty()) {
            String p0 = block.parameters.get(0);
            if (p0 != null && !p0.trim().isEmpty() && !p0.startsWith("@")) {
                if ("html_convert".equals(block.opCode) || "html_convert_leaf".equals(block.opCode)
                        || "view_convert".equals(block.opCode) || "view_convert_leaf".equals(block.opCode)) {
                    String clean = p0.trim();
                    String lower = clean.toLowerCase();
                    if (lower.contains("linearlayout") || lower.contains("framelayout")
                            || lower.contains("relativelayout") || lower.contains("coordinatorlayout")
                            || lower.contains("constraintlayout") || lower.contains("cardview")
                            || lower.contains("scrollview") || lower.contains("recyclerview")
                            || lower.contains("viewpager")) {
                        return "div";
                    } else if (lower.contains("textview")) {
                        return "p";
                    } else if (lower.contains("button")) {
                        return "button";
                    } else if (lower.contains("imageview")) {
                        return "img";
                    } else if (lower.contains("edittext")) {
                        return "input";
                    }
                    return clean;
                }
            }
        }
        if (block.opCode != null && block.opCode.startsWith("html")) {
            String sub = block.opCode.substring(4).toLowerCase();
            if ("paragraph".equals(sub)) return "p";
            if ("unorderedlist".equals(sub)) return "ul";
            if ("orderedlist".equals(sub)) return "ol";
            if ("listitem".equals(sub)) return "li";
            if ("anchor".equals(sub)) return "a";
            if ("image".equals(sub)) return "img";
            if ("heading".equals(sub)) return "h1";
            if (!sub.isEmpty()) return sub;
        }
        if (block.spec != null && block.spec.startsWith("<") && block.spec.endsWith(">")) {
            String tag = block.spec.substring(1, block.spec.length() - 1).trim();
            if (!tag.isEmpty() && !tag.contains("%")) return tag;
        }
        return "div";
    }

    private static String formatAttributeBlock(BlockBean attr) {
        if (attr == null) return "";
        String op = attr.opCode != null ? attr.opCode : "";

        if (op.startsWith("htmlAttr")) {
            String attrName = op.substring(8).toLowerCase();
            if ("generic".equals(attrName)) {
                if (attr.parameters != null && attr.parameters.size() >= 2) {
                    return attr.parameters.get(0) + "=\"" + attr.parameters.get(1) + "\"";
                }
            } else if ("data".equals(attrName)) {
                if (attr.parameters != null && attr.parameters.size() >= 2) {
                    return "data-" + attr.parameters.get(0) + "=\"" + attr.parameters.get(1) + "\"";
                }
            } else if ("disabled".equals(attrName) || "checked".equals(attrName)
                    || "required".equals(attrName) || "readonly".equals(attrName)
                    || "hidden".equals(attrName) || "autofocus".equals(attrName)
                    || "multiple".equals(attrName) || "selected".equals(attrName)) {
                return attrName;
            } else if ("arialabel".equals(attrName)) {
                String val = (attr.parameters != null && !attr.parameters.isEmpty()) ? attr.parameters.get(0) : "";
                return "aria-label=\"" + val + "\"";
            } else if ("buttontype".equals(attrName)) {
                String val = (attr.parameters != null && !attr.parameters.isEmpty()) ? attr.parameters.get(0) : "";
                return "type=\"" + val + "\"";
            } else if ("formenctype".equals(attrName)) {
                String val = (attr.parameters != null && !attr.parameters.isEmpty()) ? attr.parameters.get(0) : "";
                return "enctype=\"" + val + "\"";
            } else if ("ariaexpanded".equals(attrName)) {
                String val = (attr.parameters != null && !attr.parameters.isEmpty()) ? attr.parameters.get(0) : "";
                return "aria-expanded=\"" + val + "\"";
            } else if ("ariahidden".equals(attrName)) {
                String val = (attr.parameters != null && !attr.parameters.isEmpty()) ? attr.parameters.get(0) : "";
                return "aria-hidden=\"" + val + "\"";
            } else if ("ariadescribedby".equals(attrName)) {
                String val = (attr.parameters != null && !attr.parameters.isEmpty()) ? attr.parameters.get(0) : "";
                return "aria-describedby=\"" + val + "\"";
            } else {
                String val = (attr.parameters != null && !attr.parameters.isEmpty()) ? attr.parameters.get(0) : "";
                return attrName + "=\"" + val + "\"";
            }
        }

        if ("id".equalsIgnoreCase(op)) {
            String val = (attr.parameters != null && !attr.parameters.isEmpty()) ? attr.parameters.get(0) : "";
            return "id=\"" + val + "\"";
        }
        if ("class".equalsIgnoreCase(op)) {
            String val = (attr.parameters != null && !attr.parameters.isEmpty()) ? attr.parameters.get(0) : "";
            return "class=\"" + val + "\"";
        }
        if ("style".equalsIgnoreCase(op)) {
            String val = (attr.parameters != null && !attr.parameters.isEmpty()) ? attr.parameters.get(0) : "";
            return "style=\"" + val + "\"";
        }

        if (attr.code != null && attr.code.contains("%")) {
            try {
                if (attr.parameters != null && !attr.parameters.isEmpty()) {
                    String fmt = attr.code.contains("%1$s") ? attr.code : attr.code.replace("%s", "%1$s");
                    return String.format(fmt, attr.parameters.toArray());
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    // =========================================================================
    // Generator Context & Node Processor
    // =========================================================================

    private static class GeneratorContext {
        int counter = 10;
        final ArrayList<BlockBean> blocks = new ArrayList<>();
        final Map<String, BlockDef> tagMap;
        final Map<String, BlockDef> attrMap;
        final BlockDef fallbackContainerDef;
        final BlockDef fallbackLeafDef;
        final BlockDef fallbackAttrGenericDef;
        final BlockDef textDef;
        final BlockDef commentDef;
        final BlockDef asdDef;

        GeneratorContext(Map<String, BlockDef> tagMap, Map<String, BlockDef> attrMap,
                         BlockDef fallbackContainerDef, BlockDef fallbackLeafDef,
                         BlockDef fallbackAttrGenericDef, BlockDef textDef,
                         BlockDef commentDef, BlockDef asdDef) {
            this.tagMap = tagMap;
            this.attrMap = attrMap;
            this.fallbackContainerDef = fallbackContainerDef;
            this.fallbackLeafDef = fallbackLeafDef;
            this.fallbackAttrGenericDef = fallbackAttrGenericDef;
            this.textDef = textDef;
            this.commentDef = commentDef;
            this.asdDef = asdDef;
        }

        int nextId() {
            return counter++;
        }

        void addBlock(BlockBean b) {
            blocks.add(b);
        }

        BlockBean findBlock(int id) {
            String strId = String.valueOf(id);
            for (BlockBean b : blocks) {
                if (strId.equals(b.id)) return b;
            }
            return null;
        }
    }

    private static int processNode(HtmlNode node, GeneratorContext ctx) {
        if (node instanceof HtmlCommentNode commentNode) {
            BlockBean b = createBlockFromDef(ctx.commentDef, "htmlComment", "<!-- %s.inputOnly -->", "normal", "#607D8B", ctx.nextId());
            b.parameters.add(commentNode.comment);
            ctx.addBlock(b);
            return Integer.parseInt(b.id);
        }

        if (node instanceof HtmlTextNode textNode) {
            String text = textNode.text.trim();
            if (text.isEmpty()) return -1;
            BlockBean b = createBlockFromDef(ctx.textDef, "htmlText", "text %s.inputOnly", "normal", "#607D8B", ctx.nextId());
            b.parameters.add(text);
            ctx.addBlock(b);
            return Integer.parseInt(b.id);
        }

        if (node instanceof HtmlElementNode elem) {
            String tagLower = elem.tagName.toLowerCase();
            boolean isVoid = VOID_ELEMENTS.contains(tagLower) || elem.isSelfClosing;

            BlockDef matchedDef = ctx.tagMap.get(tagLower);
            if (matchedDef == null) {
                // Check tag aliases
                if ("p".equals(tagLower)) matchedDef = ctx.tagMap.get("paragraph");
                else if ("a".equals(tagLower)) matchedDef = ctx.tagMap.get("anchor");
                else if ("img".equals(tagLower)) matchedDef = ctx.tagMap.get("image");
                else if ("ul".equals(tagLower)) matchedDef = ctx.tagMap.get("unorderedlist");
                else if ("ol".equals(tagLower)) matchedDef = ctx.tagMap.get("orderedlist");
                else if ("li".equals(tagLower)) matchedDef = ctx.tagMap.get("listitem");
            }

            BlockBean viewBlock;
            int viewId = ctx.nextId();

            if (matchedDef != null) {
                viewBlock = createBlockFromDef(matchedDef, matchedDef.getOpCode(), matchedDef.getSpec(), matchedDef.getType(), matchedDef.color, viewId);
                if (("html_convert".equals(matchedDef.getOpCode()) || "html_convert_leaf".equals(matchedDef.getOpCode())
                        || "view_convert".equals(matchedDef.getOpCode()) || "view_convert_leaf".equals(matchedDef.getOpCode()))
                        && viewBlock.parameters.isEmpty()) {
                    viewBlock.parameters.add(elem.tagName);
                }
            } else if (isVoid) {
                viewBlock = createBlockFromDef(ctx.fallbackLeafDef, "html_convert_leaf", "< %m.htmlTag />", "c", "#607D8B", viewId);
                viewBlock.parameters.add(elem.tagName);
            } else {
                viewBlock = createBlockFromDef(ctx.fallbackContainerDef, "html_convert", "< %m.htmlTag >", "e", "#607D8B", viewId);
                viewBlock.parameters.add(elem.tagName);
            }

            // 1. Process Attributes (subStack1)
            int firstAttrId = -1;
            BlockBean prevAttr = null;

            for (Map.Entry<String, String> entry : elem.attributes.entrySet()) {
                String attrName = entry.getKey();
                String attrVal = entry.getValue();

                BlockDef attrDef = ctx.attrMap.get(attrName.toLowerCase());
                int attrId = ctx.nextId();
                BlockBean attrBlock;

                if (attrDef != null) {
                    attrBlock = createBlockFromDef(attrDef, attrDef.getOpCode(), attrDef.getSpec(), " ", attrDef.color, attrId);
                    if (attrBlock.spec.contains("%")) {
                        attrBlock.parameters.add(attrVal);
                    }
                } else if (attrName.toLowerCase().startsWith("data-")) {
                    BlockDef dataDef = ctx.attrMap.get("data");
                    attrBlock = createBlockFromDef(dataDef, "htmlAttrData", "data- %s.inputOnly =\" %s.inputOnly \"", " ", "#78909C", attrId);
                    attrBlock.parameters.add(attrName.substring(5));
                    attrBlock.parameters.add(attrVal);
                } else {
                    attrBlock = createBlockFromDef(ctx.fallbackAttrGenericDef, "htmlAttrGeneric", "%s.inputOnly =\" %s.inputOnly \"", " ", "#78909C", attrId);
                    attrBlock.parameters.add(attrName);
                    attrBlock.parameters.add(attrVal);
                }

                if (firstAttrId == -1) firstAttrId = attrId;
                if (prevAttr != null) prevAttr.nextBlock = attrId;
                prevAttr = attrBlock;
                ctx.addBlock(attrBlock);
            }

            viewBlock.subStack1 = firstAttrId;

            // 2. Process Children (subStack2)
            if (!isVoid && !elem.children.isEmpty()) {
                int firstChildId = -1;
                BlockBean prevChild = null;

                for (HtmlNode child : elem.children) {
                    int childId = processNode(child, ctx);
                    if (childId >= 0) {
                        BlockBean childBlock = ctx.findBlock(childId);
                        if (childBlock != null) {
                            if (firstChildId == -1) firstChildId = childId;
                            if (prevChild != null) prevChild.nextBlock = childId;
                            prevChild = childBlock;
                        }
                    }
                }
                viewBlock.subStack2 = firstChildId;
            } else {
                viewBlock.subStack2 = -1;
            }

            ctx.addBlock(viewBlock);
            return viewId;
        }

        return -1;
    }

    private static BlockBean createBlockFromDef(BlockDef def, String defaultOpCode, String defaultSpec,
                                                String defaultType, String defaultColor, int id) {
        BlockBean bean = new BlockBean();
        bean.id = String.valueOf(id);
        bean.opCode = (def != null && def.getOpCode() != null) ? def.getOpCode() : defaultOpCode;
        bean.spec = (def != null && def.getSpec() != null) ? def.getSpec() : defaultSpec;
        bean.spec2 = (def != null && def.getSpec2() != null && !def.getSpec2().isEmpty()) ? def.getSpec2() : ("e".equals(defaultType) ? "children" : "");
        bean.type = (def != null && def.getType() != null && !def.getType().isEmpty()) ? def.getType() : defaultType;
        bean.blockType = (def != null && def.blockType != null) ? def.blockType : bean.type;
        if ("e".equals(bean.blockType) && (bean.spec2 == null || bean.spec2.isEmpty())) {
            bean.spec2 = "children";
        }
        bean.category = (def != null && def.category != null) ? def.category : "html";
        bean.code = (def != null && def.code != null) ? def.code : "";

        String colorStr = (def != null && def.color != null && !def.color.isEmpty()) ? def.color : defaultColor;
        try {
            bean.color = Color.parseColor(colorStr);
        } catch (Throwable e) {
            bean.color = -10453621;
        }

        bean.paramTypes = parseSpecParamTypes(bean.spec);

        return bean;
    }

    public static ArrayList<String> parseSpecParamTypes(String spec) {
        ArrayList<String> types = new ArrayList<>();
        if (spec == null || spec.isEmpty()) return types;
        int i = 0;
        int len = spec.length();
        while (i < len) {
            if (spec.charAt(i) == '%' && i + 1 < len) {
                i++;
                StringBuilder token = new StringBuilder();
                while (i < len) {
                    char c = spec.charAt(i);
                    if (c == ' ' || c == '<' || c == '>' || c == '"' || c == '\'' || c == '/') {
                        break;
                    }
                    token.append(c);
                    i++;
                }
                String t = token.toString().replaceAll("[,;()\\[\\]]+$", "");
                if (!t.isEmpty()) {
                    types.add(t);
                }
            } else {
                i++;
            }
        }
        return types;
    }

    // =========================================================================
    // HTML AST Nodes & Tokenizer
    // =========================================================================

    public abstract static class HtmlNode {}

    public static class HtmlElementNode extends HtmlNode {
        public String tagName;
        public final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        public final List<HtmlNode> children = new ArrayList<>();
        public boolean isSelfClosing = false;

        public HtmlElementNode(String tagName) {
            this.tagName = tagName;
        }
    }

    public static class HtmlTextNode extends HtmlNode {
        public String text;

        public HtmlTextNode(String text) {
            this.text = text;
        }
    }

    public static class HtmlCommentNode extends HtmlNode {
        public String comment;

        public HtmlCommentNode(String comment) {
            this.comment = comment;
        }
    }

    private static List<HtmlNode> extractBodyChildren(List<HtmlNode> nodes) {
        if (nodes == null) return null;
        for (HtmlNode node : nodes) {
            if (node instanceof HtmlElementNode) {
                HtmlElementNode elem = (HtmlElementNode) node;
                if ("html".equalsIgnoreCase(elem.tagName)) {
                    for (HtmlNode ch : elem.children) {
                        if (ch instanceof HtmlElementNode) {
                            HtmlElementNode chElem = (HtmlElementNode) ch;
                            if ("body".equalsIgnoreCase(chElem.tagName)) {
                                return chElem.children;
                            }
                        }
                    }
                    return elem.children;
                } else if ("body".equalsIgnoreCase(elem.tagName)) {
                    return elem.children;
                }
            }
        }
        return null;
    }

    public static List<HtmlNode> parseHtmlToNodes(String html) {
        List<HtmlNode> rootNodes = new ArrayList<>();
        if (html == null || html.trim().isEmpty()) return rootNodes;

        int pos = 0;
        int len = html.length();

        while (pos < len) {
            int tagStart = html.indexOf('<', pos);

            if (tagStart < 0) {
                String text = html.substring(pos).trim();
                if (!text.isEmpty()) {
                    rootNodes.add(new HtmlTextNode(text));
                }
                break;
            }

            if (tagStart > pos) {
                String text = html.substring(pos, tagStart).trim();
                if (!text.isEmpty()) {
                    rootNodes.add(new HtmlTextNode(text));
                }
            }

            // Comment <!-- ... -->
            if (html.startsWith("<!--", tagStart)) {
                int commentEnd = html.indexOf("-->", tagStart + 4);
                if (commentEnd >= 0) {
                    String comment = html.substring(tagStart + 4, commentEnd).trim();
                    rootNodes.add(new HtmlCommentNode(comment));
                    pos = commentEnd + 3;
                } else {
                    pos = len;
                }
                continue;
            }

            // Doctype <!DOCTYPE ...> or XML declaration <?xml ...?>
            if (html.startsWith("<!", tagStart) || html.startsWith("<?", tagStart)) {
                int dtEnd = html.indexOf('>', tagStart);
                pos = (dtEnd >= 0) ? dtEnd + 1 : len;
                continue;
            }

            // Closing tag outside matching: skip
            if (tagStart + 1 < len && html.charAt(tagStart + 1) == '/') {
                int closingEnd = html.indexOf('>', tagStart);
                pos = (closingEnd >= 0) ? closingEnd + 1 : len;
                continue;
            }

            // Parse opening element tag
            ParsedTag parsed = parseTagAt(html, tagStart);
            if (parsed == null) {
                pos = tagStart + 1;
                continue;
            }

            pos = parsed.endPos;

            HtmlElementNode elem = new HtmlElementNode(parsed.tagName);
            elem.attributes.putAll(parsed.attributes);
            elem.isSelfClosing = parsed.isSelfClosing;

            boolean isVoid = VOID_ELEMENTS.contains(parsed.tagName.toLowerCase()) || parsed.isSelfClosing;

            if (!isVoid) {
                // If script or style, capture verbatim body
                if ("script".equalsIgnoreCase(parsed.tagName) || "style".equalsIgnoreCase(parsed.tagName)) {
                    String closeTag = "</" + parsed.tagName.toLowerCase() + ">";
                    int endIdx = html.toLowerCase().indexOf(closeTag, pos);
                    if (endIdx >= 0) {
                        String body = html.substring(pos, endIdx).trim();
                        if (!body.isEmpty()) {
                            elem.children.add(new HtmlTextNode(body));
                        }
                        pos = endIdx + closeTag.length();
                    } else {
                        pos = len;
                    }
                } else {
                    int closeIdx = findMatchingClosingTag(html, pos, parsed.tagName);
                    if (closeIdx >= 0) {
                        String innerHtml = html.substring(pos, closeIdx);
                        elem.children.addAll(parseHtmlToNodes(innerHtml));
                        pos = closeIdx + ("</" + parsed.tagName + ">").length();
                    }
                }
            }

            rootNodes.add(elem);
        }

        return rootNodes;
    }

    private static class ParsedTag {
        String tagName;
        final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        boolean isSelfClosing = false;
        int endPos;
    }

    private static ParsedTag parseTagAt(String html, int startPos) {
        int len = html.length();
        if (startPos >= len || html.charAt(startPos) != '<') return null;

        int pos = startPos + 1;

        // Tag name
        StringBuilder nameSb = new StringBuilder();
        while (pos < len && !Character.isWhitespace(html.charAt(pos)) && html.charAt(pos) != '>' && html.charAt(pos) != '/') {
            nameSb.append(html.charAt(pos));
            pos++;
        }
        String tagName = nameSb.toString().trim();
        if (tagName.isEmpty()) return null;

        ParsedTag tag = new ParsedTag();
        tag.tagName = tagName;

        while (pos < len) {
            while (pos < len && Character.isWhitespace(html.charAt(pos))) pos++;
            if (pos >= len) break;

            if (html.charAt(pos) == '>') {
                pos++;
                break;
            }

            if (html.charAt(pos) == '/') {
                tag.isSelfClosing = true;
                pos++;
                if (pos < len && html.charAt(pos) == '>') {
                    pos++;
                    break;
                }
                continue;
            }

            // Attribute Name
            StringBuilder attrName = new StringBuilder();
            while (pos < len && html.charAt(pos) != '=' && html.charAt(pos) != '>' && !Character.isWhitespace(html.charAt(pos)) && html.charAt(pos) != '/') {
                attrName.append(html.charAt(pos));
                pos++;
            }
            String name = attrName.toString().trim();

            while (pos < len && Character.isWhitespace(html.charAt(pos))) pos++;

            if (pos < len && html.charAt(pos) == '=') {
                pos++; // skip '='
                while (pos < len && Character.isWhitespace(html.charAt(pos))) pos++;

                if (pos < len) {
                    String value;
                    if (html.charAt(pos) == '"' || html.charAt(pos) == '\'') {
                        char quote = html.charAt(pos);
                        pos++;
                        StringBuilder valSb = new StringBuilder();
                        while (pos < len && html.charAt(pos) != quote) {
                            valSb.append(html.charAt(pos));
                            pos++;
                        }
                        if (pos < len) pos++; // skip closing quote
                        value = valSb.toString();
                    } else {
                        StringBuilder valSb = new StringBuilder();
                        while (pos < len && !Character.isWhitespace(html.charAt(pos)) && html.charAt(pos) != '>' && html.charAt(pos) != '/') {
                            valSb.append(html.charAt(pos));
                            pos++;
                        }
                        value = valSb.toString();
                    }
                    if (!name.isEmpty()) {
                        tag.attributes.put(name, value);
                    }
                }
            } else {
                if (!name.isEmpty()) {
                    tag.attributes.put(name, name);
                }
            }
        }

        tag.endPos = pos;
        return tag;
    }

    private static int findMatchingClosingTag(String html, int startPos, String tagName) {
        int depth = 1;
        int pos = startPos;
        int len = html.length();
        String openPattern = "<" + tagName.toLowerCase();
        String closePattern = "</" + tagName.toLowerCase() + ">";

        while (pos < len && depth > 0) {
            int nextOpen = html.toLowerCase().indexOf(openPattern, pos);
            int nextClose = html.toLowerCase().indexOf(closePattern, pos);

            if (nextClose < 0) return -1;

            if (nextOpen >= 0 && nextOpen < nextClose) {
                // Verify nextOpen is followed by whitespace, '>', or '/'
                int charAfter = nextOpen + openPattern.length();
                if (charAfter < len) {
                    char c = html.charAt(charAfter);
                    if (Character.isWhitespace(c) || c == '>' || c == '/') {
                        depth++;
                    }
                }
                pos = nextOpen + openPattern.length();
            } else {
                depth--;
                if (depth == 0) {
                    return nextClose;
                }
                pos = nextClose + closePattern.length();
            }
        }
        return -1;
    }

    // =========================================================================
    // JSON Serialization Helpers (Sketchware/BlocksLoader format)
    // =========================================================================

    public static BlockResult parseJsonToBlockResult(String jsonResult) {
        if (jsonResult == null || jsonResult.trim().isEmpty()) {
            return new BlockResult("HTML generator returned empty result.");
        }
        try {
            Object parsed = new JSONTokener(jsonResult).nextValue();
            if (parsed instanceof JSONObject obj && obj.has("Error")) {
                return new BlockResult("Parse Error: " + obj.optString("Error", "Unknown error"));
            }
            if (!(parsed instanceof JSONArray jsonArray)) {
                return new BlockResult("Unexpected output format from HTML generator: " + jsonResult);
            }
            ArrayList<BlockBean> blocks = parseJsonArrayToBlocks(jsonArray);
            return new BlockResult(blocks);
        } catch (Exception e) {
            return new BlockResult("Failed to parse HTML blocks: " + e.getMessage());
        }
    }

    public static ArrayList<BlockBean> parseJsonArrayToBlocks(JSONArray jsonArray) throws Exception {
        ArrayList<BlockBean> blocks = new ArrayList<>();
        if (jsonArray == null) return blocks;

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject b = jsonArray.getJSONObject(i);

            String idStr = b.optString("id", "");
            if (idStr.isEmpty()) {
                idStr = String.valueOf(b.optInt("id", i + 10));
            }
            String opCode = b.optString("op_code", b.optString("opCode", ""));
            String spec = b.optString("spec", "");
            String type = b.optString("block_type", b.optString("type", " "));

            BlockBean blockBean = new BlockBean(idStr, spec, type, opCode);
            blockBean.color = b.optInt("color", 0);
            blockBean.code = b.optString("code", "");
            blockBean.spec2 = b.optString("spec2", "");
            blockBean.category = b.optString("category", "html");
            blockBean.nextBlock = b.optInt("next_block", b.optInt("nextBlock", -1));
            blockBean.subStack1 = b.optInt("sub_stack1", b.optInt("subStack1", -1));
            blockBean.subStack2 = b.optInt("sub_stack2", b.optInt("subStack2", -1));
            blockBean.stackIndex = b.optInt("stack_index", b.optInt("stackIndex", i));
            blockBean.isConnectedToRoot = b.optBoolean("is_connected_to_root", b.optBoolean("isConnectedToRoot", true));
            blockBean.x = b.optInt("x", 0);
            blockBean.y = b.optInt("y", 0);

            JSONArray paramsArr = b.optJSONArray("parameters");
            if (paramsArr != null) {
                for (int j = 0; j < paramsArr.length(); j++) {
                    blockBean.parameters.add(paramsArr.optString(j, ""));
                }
            }

            JSONArray paramTypesArr = b.optJSONArray("param_types");
            if (paramTypesArr == null) {
                paramTypesArr = b.optJSONArray("paramTypes");
            }
            if (paramTypesArr != null) {
                for (int j = 0; j < paramTypesArr.length(); j++) {
                    blockBean.paramTypes.add(paramTypesArr.optString(j, ""));
                }
            }

            blocks.add(blockBean);
        }
        return blocks;
    }
}
