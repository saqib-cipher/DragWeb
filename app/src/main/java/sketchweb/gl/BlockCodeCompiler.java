package sketchweb.gl;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class BlockCodeCompiler {

    private final Context context;
    private final String projectId;

    private final java.util.Set<String> declaredVars = new java.util.HashSet<>();

    public BlockCodeCompiler(Context context, String projectId) {
        this.context = context;
        this.projectId = projectId;
    }

    public String getSource(int type, ArrayList<BlockBean> blocks) {
        declaredVars.clear();
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

    public String compileEventOrFunction(String key, ArrayList<BlockBean> bodyBlocks) {
        ArrayList<BlockBean> fullList = new ArrayList<>();
        BlockBean root = new BlockBean();
        root.id = "0";

        if (key.contains("moreBlock") || key.startsWith("func_") || key.startsWith("func")) {
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
                        if (!spec.startsWith("Define ") && !spec.startsWith("definedFunc ")) {
                            spec = "Define " + spec;
                        }
                        break;
                    }
                }
            }
            root.opCode = "definedFunc";
            root.spec = spec;
        } else {
            root.opCode = "initializeLogic";
            root.spec = "When On Page Load";
        }

        if (bodyBlocks != null && !bodyBlocks.isEmpty()) {
            BlockBean firstBody = bodyBlocks.get(0);
            try {
                root.nextBlock = Integer.parseInt(firstBody.id);
            } catch (Exception ignored) {}
            fullList.add(root);
            fullList.addAll(bodyBlocks);
        } else {
            fullList.add(root);
        }

        return getSource(0, fullList);
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

    private boolean isFunctionDefinitionRoot(BlockBean block, ArrayList<BlockBean> allBlocks) {
        if (block == null) return false;
        if (!isChildOfAny(block, allBlocks)) {
            String op = block.opCode != null ? block.opCode : "";
            String spec = block.spec != null ? block.spec : "";
            if ("definedFunc".equals(op) || "moreBlock".equals(op) || op.startsWith("func")) {
                return true;
            }
            if (spec.startsWith("When func") || spec.startsWith("definedFunc") || spec.startsWith("Define ")) {
                return true;
            }
        }
        return false;
    }

    private boolean isEventHatBlock(BlockBean block, ArrayList<BlockBean> allBlocks) {
        if (block == null) return false;
        if (!isChildOfAny(block, allBlocks)) {
            if (isFunctionDefinitionRoot(block, allBlocks)) {
                return false;
            }
            String op = block.opCode != null ? block.opCode : "";
            String spec = block.spec != null ? block.spec : "";
            if (op.equals("initializeLogic") || op.equals("onClick") || op.equals("onCheckedChange")
                    || op.equals("onItemSelected") || op.equals("onItemClicked") || op.equals("onTextChanged")
                    || spec.startsWith("When ") || spec.startsWith("when ")) {
                return true;
            }
        }
        return false;
    }

    private String compileBlockAndNext(BlockBean block, ArrayList<BlockBean> allBlocks, int indentLevel) {
        if (block == null) return "";

        if (isFunctionDefinitionRoot(block, allBlocks)) {
            return compileFunctionDefinition(block, allBlocks, indentLevel);
        }

        StringBuilder sb = new StringBuilder();
        BlockBean current = block;

        if (isEventHatBlock(current, allBlocks)) {
            int nextId = current.nextBlock;
            current = findBlockById(String.valueOf(nextId), allBlocks);
        }

        while (current != null) {
            String code = compileBlock(current, allBlocks, indentLevel);
            if (!code.isEmpty()) {
                sb.append(code).append("\n");
            }
            int nextId = current.nextBlock;
            current = null;
            if (nextId >= 0) {
                current = findBlockById(String.valueOf(nextId), allBlocks);
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
        BlockBean current = findBlockById(String.valueOf(subStackId), allBlocks);
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
            if ("true".equals(block.opCode)) return "true";
            if ("false".equals(block.opCode)) return "false";
            if ("getArg".equals(block.opCode)) {
                if (block.spec != null && !block.spec.trim().isEmpty()) {
                    return block.spec.trim();
                }
                if (block.parameters != null && !block.parameters.isEmpty()) {
                    return block.parameters.get(0);
                }
                return "";
            }
            if (block.opCode != null && (block.opCode.equals("getVar") || block.opCode.equals("getListVar") || block.opCode.startsWith("getVar"))) {
                if (block.parameters != null && !block.parameters.isEmpty()) {
                    String vName = block.parameters.get(0);
                    if (vName != null && !vName.trim().isEmpty()) {
                        return vName.trim();
                    }
                }
                return block.spec != null ? block.spec.trim() : "";
            }
            if (block.opCode != null && (block.opCode.equals("setVarBoolean") || block.opCode.equals("setVarInt") || block.opCode.equals("setVarString") || block.opCode.startsWith("setVar"))) {
                String varName = (block.parameters != null && !block.parameters.isEmpty()) ? block.parameters.get(0) : "";
                if (varName != null && !varName.trim().isEmpty()) {
                    String cleanVar = varName.trim();
                    if (!declaredVars.contains(cleanVar)) {
                        declaredVars.add(cleanVar);
                        boolean isConst = false;
                        ArrayList<android.util.Pair<Integer, String>> vars = DesignDataManager.getVariables(LogicBlockActivity.filename);
                        for (android.util.Pair<Integer, String> p : vars) {
                            if (p.second.equals(cleanVar) && p.first >= 4) {
                                isConst = true;
                                break;
                            }
                        }
                        codeTemplate = isConst ? "const %1$s = %2$s;\n" : "let %1$s = %2$s;\n";
                    } else {
                        codeTemplate = "%1$s = %2$s;\n";
                    }
                } else {
                    codeTemplate = "%1$s = %2$s;\n";
                }
            } else if (block.opCode != null && block.opCode.equals("increaseInt")) {
                codeTemplate = "%1$s += %2$s;\n";
            } else if (block.opCode != null && block.opCode.equals("decreaseInt")) {
                codeTemplate = "%1$s -= %2$s;\n";
            } else if ("definedFunc".equals(block.opCode) || (block.spec != null && !block.spec.trim().isEmpty() && !block.opCode.startsWith("get") && !block.opCode.startsWith("set"))) {
                if (!isChildOfAny(block, allBlocks)) {
                    return compileFunctionDefinition(block, allBlocks, indentLevel);
                } else {
                    return compileMoreBlockCall(block, allBlocks, indentLevel);
                }
            } else {
                return "";
            }
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

    private String compileFunctionDefinition(BlockBean block, ArrayList<BlockBean> allBlocks, int indentLevel) {
        String spec = block.spec != null ? block.spec : "";
        String funcName = "";
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
            } else {
                if ("When".equalsIgnoreCase(tok) || "func".equalsIgnoreCase(tok) || "definedFunc".equalsIgnoreCase(tok) || "Define".equalsIgnoreCase(tok)) {
                    continue;
                }
                if (funcName.isEmpty()) {
                    funcName = tok;
                }
            }
        }
        if (funcName.isEmpty()) {
            funcName = block.opCode != null ? block.opCode : "func";
        }

        StringBuilder paramsSb = new StringBuilder();
        for (int i = 0; i < paramNames.size(); i++) {
            if (i > 0) paramsSb.append(", ");
            paramsSb.append(paramNames.get(i));
        }

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < indentLevel; i++) {
            indent.append("  ");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("function ").append(funcName).append("(").append(paramsSb.toString()).append(") {\n");

        if (block.nextBlock >= 0) {
            BlockBean nextBlock = findBlockById(String.valueOf(block.nextBlock), allBlocks);
            if (nextBlock != null) {
                String bodyCode = compileBlockAndNext(nextBlock, allBlocks, indentLevel + 1);
                if (!bodyCode.isEmpty()) {
                    sb.append(bodyCode).append("\n");
                }
            }
        }

        sb.append(indent).append("}\n");
        return sb.toString();
    }

    private String compileMoreBlockCall(BlockBean block, ArrayList<BlockBean> allBlocks, int indentLevel) {
        String spec = block.spec != null ? block.spec : "";
        if (spec.trim().isEmpty()) return "";

        ArrayList<String> tokens = StringUtil.tokenize(spec);
        String funcName = "";
        for (String tok : tokens) {
            if (!tok.startsWith("%")) {
                funcName = tok;
                break;
            }
        }
        if (funcName.isEmpty()) {
            String[] rawTokens = spec.trim().split("\\s+");
            funcName = rawTokens[0].replaceAll("%[sdb]|%m\\.[a-zA-Z0-9_.]+", "").trim();
        }

        ArrayList<String> argsList = new ArrayList<>();
        if (block.parameters != null) {
            for (String param : block.parameters) {
                if (param.startsWith("@")) {
                    String childId = param.substring(1);
                    BlockBean childBlock = findBlockById(childId, allBlocks);
                    if (childBlock != null) {
                        argsList.add(compileBlock(childBlock, allBlocks, 0));
                    } else {
                        argsList.add("");
                    }
                } else {
                    argsList.add(param);
                }
            }
        }

        StringBuilder argsSb = new StringBuilder();
        for (int i = 0; i < argsList.size(); i++) {
            if (i > 0) argsSb.append(", ");
            argsSb.append(argsList.get(i));
        }

        String callStr = funcName + "(" + argsSb.toString() + ")";
        String bType = block.type != null ? block.type : (block.blockType != null ? block.blockType : " ");
        if (" ".equals(bType) || "normal".equalsIgnoreCase(bType) || "c".equals(bType) || "".equals(bType)) {
            callStr += ";";
        }

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < indentLevel; i++) {
            indent.append("  ");
        }
        return indent.toString() + callStr;
    }
}
