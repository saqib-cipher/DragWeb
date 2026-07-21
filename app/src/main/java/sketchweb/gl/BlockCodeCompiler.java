package sketchweb.gl;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class BlockCodeCompiler {

    private final Context context;
    private final String projectId;

    public BlockCodeCompiler(Context context, String projectId) {
        this.context = context;
        this.projectId = projectId;
    }

    public String getSource(int type, ArrayList<BlockBean> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }

        // Find all root blocks (blocks that are not connected or nested under any other block)
        ArrayList<BlockBean> rootBlocks = new ArrayList<>();
        for (BlockBean b : blocks) {
            if (!isChildOfAny(b, blocks)) {
                rootBlocks.add(b);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (BlockBean root : rootBlocks) {
            String compiled = compileBlockAndNext(root, blocks, 0);
            if (!compiled.isEmpty()) {
                sb.append(compiled).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private boolean isChildOfAny(BlockBean target, ArrayList<BlockBean> blocks) {
        if (target.id == null || target.id.isEmpty()) return false;
        int targetId = Integer.parseInt(target.id);
        for (BlockBean b : blocks) {
            if (b.nextBlock == targetId) return true;
            if (b.subStack1 == targetId) return true;
            if (b.subStack2 == targetId) return true;
            // Check if target is nested as an argument block in b
            for (String param : b.parameters) {
                if (param.startsWith("@") && param.substring(1).equals(target.id)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String compileBlockAndNext(BlockBean block, ArrayList<BlockBean> allBlocks, int indentLevel) {
        StringBuilder sb = new StringBuilder();
        BlockBean current = block;
        while (current != null) {
            String code = compileBlock(current, allBlocks, indentLevel);
            if (!code.isEmpty()) {
                sb.append(code).append("\n");
            }
            int nextId = current.nextBlock;
            current = null;
            if (nextId >= 0) {
                for (BlockBean b : allBlocks) {
                    if (b.id != null && b.id.equals(String.valueOf(nextId))) {
                        current = b;
                        break;
                    }
                }
            }
        }
        // Remove trailing newline if any
        String res = sb.toString();
        if (res.endsWith("\n")) {
            res = res.substring(0, res.length() - 1);
        }
        return res;
    }

    public String compileSubstack(int subStackId, ArrayList<BlockBean> allBlocks, int indentLevel) {
        if (subStackId < 0) return "";
        BlockBean current = null;
        for (BlockBean b : allBlocks) {
            if (b.id != null && b.id.equals(String.valueOf(subStackId))) {
                current = b;
                break;
            }
        }
        if (current == null) return "";
        return compileBlockAndNext(current, allBlocks, indentLevel);
    }

    private BlockBean findBlockById(String id, ArrayList<BlockBean> blocks) {
        for (BlockBean b : blocks) {
            if (b.id != null && b.id.equals(id)) {
                return b;
            }
        }
        return null;
    }

    public String compileBlock(BlockBean block, ArrayList<BlockBean> allBlocks, int indentLevel) {
        if (block == null) return "";

        // Find definition for this block
        BlockDef def = null;
        for (BlockDef d : BlockDef.getDefinitions(context)) {
            if (block.opCode.equals(d.getOpCode())) {
                def = d;
                break;
            }
        }

        // If not found in blocks.json, check saved collections
        if (def == null) {
            for (PaletteSelector.CategoryItem cat : PaletteSelector.categoriesList) {
                if (cat.type == 3) {
                    try {
                        String json = FileUtil.readFile(cat.blockJsonPath);
                        if (json != null && !json.isEmpty()) {
                            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<BlockDef>>(){}.getType();
                            ArrayList<BlockDef> list = new com.google.gson.Gson().fromJson(json, listType);
                            if (list != null) {
                                for (BlockDef d : list) {
                                    if (block.opCode.equals(d.getOpCode())) {
                                        def = d;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (def != null) break;
            }
        }

        String codeTemplate = (def != null) ? def.code : "";
        if (codeTemplate == null || codeTemplate.isEmpty()) {
            if (block.opCode.equals("true")) return "true";
            if (block.opCode.equals("false")) return "false";
            if (block.opCode.equals("getVar")) {
                return block.spec != null ? block.spec : "";
            }
            return "";
        }

        // Create indent prefix
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < indentLevel; i++) {
            indent.append("  ");
        }

        // Compile parameters/arguments
        ArrayList<Object> compiledParams = new ArrayList<>();
        for (String param : block.parameters) {
            if (param.startsWith("@")) {
                String childId = param.substring(1);
                BlockBean childBlock = findBlockById(childId, allBlocks);
                if (childBlock != null) {
                    compiledParams.add(compileBlock(childBlock, allBlocks, 0));
                } else {
                    compiledParams.add("");
                }
            } else {
                compiledParams.add(param);
            }
        }

        // Check if the block is a container (C or E blockType) and append substacks as format arguments
        if (def != null && def.blockType != null) {
            if (def.blockType.equals("c")) {
                String subCode = compileSubstack(block.subStack1, allBlocks, indentLevel + 1);
                compiledParams.add(subCode);
            } else if (def.blockType.equals("e")) {
                String subCode = compileSubstack(block.subStack1, allBlocks, indentLevel + 1);
                String subCode2 = compileSubstack(block.subStack2, allBlocks, indentLevel + 1);
                compiledParams.add(subCode);
                compiledParams.add(subCode2);
            }
        } else if (block.subStack1 >= 0) {
            String subCode = compileSubstack(block.subStack1, allBlocks, indentLevel + 1);
            compiledParams.add(subCode);
            if (block.subStack2 >= 0) {
                String subCode2 = compileSubstack(block.subStack2, allBlocks, indentLevel + 1);
                compiledParams.add(subCode2);
            }
        }

        // Compile substacks if template references them (for backward-compatibility)
        if (codeTemplate.contains("%m.space")) {
            String subCode = compileSubstack(block.subStack1, allBlocks, indentLevel + 1);
            codeTemplate = codeTemplate.replace("%m.space", subCode);
        }
        if (codeTemplate.contains("%m.space2")) {
            String subCode2 = compileSubstack(block.subStack2, allBlocks, indentLevel + 1);
            codeTemplate = codeTemplate.replace("%m.space2", subCode2);
        }

        // Normalize template placeholders (replace %selector, %b, %n, %d, %var, %var.s, %var.b, %var.d with %s)
        String normalizedTemplate = codeTemplate.replace("%selector", "%s")
                                                 .replace("%var.s", "%s")
                                                 .replace("%var.b", "%s")
                                                 .replace("%var.d", "%s")
                                                 .replace("%var", "%s")
                                                 .replace("%b", "%s")
                                                 .replace("%n", "%s")
                                                 .replace("%d", "%s");

        String formatted = "";
        try {
            formatted = String.format(normalizedTemplate, compiledParams.toArray());
        } catch (Exception e) {
            e.printStackTrace();
            formatted = normalizedTemplate;
        }

        // Add indent to each line of the formatted code
        String[] lines = formatted.split("\n");
        StringBuilder res = new StringBuilder();
        for (String line : lines) {
            if (res.length() > 0) {
                res.append("\n");
            }
            // Only prepend indent to non-empty lines
            if (!line.trim().isEmpty()) {
                res.append(indent).append(line);
            } else {
                res.append(line);
            }
        }

        return res.toString();
    }
}
