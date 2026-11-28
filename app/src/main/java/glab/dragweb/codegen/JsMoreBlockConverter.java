package glab.dragweb.codegen;

import android.content.Context;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.NodeVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import glab.dragweb.core.BlocksLoader;
import glab.dragweb.data.DesignDataManager;
import glab.dragweb.logic.BlockBean;

/**
 * Extracts top-level JavaScript function declarations into DragWeb MoreBlocks
 * (with parameters and block bodies) and rewrites statement-level calls to those
 * functions in JS logic into MoreBlock call blocks.
 */
public final class JsMoreBlockConverter {

    private static final int MORE_BLOCK_COLOR = -7711273; // 0xFF8A56D7
    private static final Pattern CALL_PATTERN = Pattern.compile(
            "^([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*\\((.*)\\)$",
            Pattern.DOTALL
    );

    private JsMoreBlockConverter() {}

    public static class ExtractedFunction {
        public final String name;
        public final List<String> params;
        public final String spec;
        public final String bodyCode;
        public final int startOffset;
        public final int endOffset;

        ExtractedFunction(String name, List<String> params, String spec, String bodyCode, int startOffset, int endOffset) {
            this.name = name;
            this.params = params;
            this.spec = spec;
            this.bodyCode = bodyCode;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
    }

    /**
     * Converts JS code into main JS blocks while extracting top-level {@code function}
     * declarations into MoreBlocks saved under {@code projectId} / {@code targetJsFile}.
     */
    public static ArrayList<BlockBean> convertJsWithMoreBlocks(
            Context context,
            String projectId,
            String targetJsFile,
            String jsContent) {
        if (jsContent == null || jsContent.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<ExtractedFunction> functions = extractTopLevelFunctions(jsContent);
        if (functions.isEmpty()) {
            return BlocksLoader.generateJsBlocksFromCode(context, jsContent);
        }

        Map<String, DesignDataManager.MoreBlockData> funcMap = new LinkedHashMap<>();
        for (ExtractedFunction fn : functions) {
            DesignDataManager.MoreBlockData mb = new DesignDataManager.MoreBlockData();
            mb.name = fn.name;
            mb.type = " ";
            mb.spec = fn.spec;
            mb.linkedFile = targetJsFile;
            mb.blocks = new ArrayList<>();
            funcMap.put(fn.name, mb);
        }

        String cleanJsName = DesignDataManager.getCleanPageName(targetJsFile);

        for (ExtractedFunction fn : functions) {
            ArrayList<BlockBean> bodyBlocks = (fn.bodyCode != null && !fn.bodyCode.trim().isEmpty())
                    ? BlocksLoader.generateJsBlocksFromCode(context, fn.bodyCode)
                    : new ArrayList<>();
            if (bodyBlocks == null) bodyBlocks = new ArrayList<>();
            rewriteMoreBlockCalls(bodyBlocks, funcMap);

            DesignDataManager.MoreBlockData mb = funcMap.get(fn.name);
            if (mb != null) {
                mb.blocks = bodyBlocks;
                if (projectId != null && !projectId.isEmpty()) {
                    DesignDataManager.saveProjectMoreBlock(projectId, mb);
                    DesignDataManager.setBlocks(cleanJsName, "func_" + fn.name, bodyBlocks);
                    DesignDataManager.setBlocks(cleanJsName, fn.name + "_moreBlock", bodyBlocks);
                }
            }
        }

        String remainingJs = removeRanges(jsContent, functions);
        ArrayList<BlockBean> mainBlocks = !remainingJs.trim().isEmpty()
                ? BlocksLoader.generateJsBlocksFromCode(context, remainingJs)
                : new ArrayList<>();
        if (mainBlocks == null) mainBlocks = new ArrayList<>();
        rewriteMoreBlockCalls(mainBlocks, funcMap);
        return mainBlocks;
    }

    /**
     * Uses Rhino's JS parser to extract top-level named function declarations.
     */
    public static List<ExtractedFunction> extractTopLevelFunctions(String jsContent) {
        List<ExtractedFunction> result = new ArrayList<>();
        if (jsContent == null || jsContent.trim().isEmpty()) return result;

        try {
            CompilerEnvirons env = new CompilerEnvirons();
            env.setRecordingLocalJsDocComments(false);
            env.setAllowMemberExprAsFunctionName(false);
            env.setStrictMode(false);
            env.setRecoverFromErrors(true);
            env.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);

            Parser parser = new Parser(env);
            AstRoot root = parser.parse(jsContent, "script.js", 1);
            if (root == null) return result;

            for (org.mozilla.javascript.Node child = root.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof FunctionNode) {
                    FunctionNode fn = (FunctionNode) child;
                    if (fn.getFunctionType() != FunctionNode.FUNCTION_STATEMENT) continue;
                    String name = fn.getName();
                    if (name == null || name.trim().isEmpty()) continue;

                    List<String> paramNames = new ArrayList<>();
                    List<AstNode> paramNodes = fn.getParams();
                    if (paramNodes != null) {
                        for (AstNode p : paramNodes) {
                            String pName = p != null ? p.toSource().trim() : "";
                            if (!pName.isEmpty()) {
                                paramNames.add(pName);
                            }
                        }
                    }

                    StringBuilder specSb = new StringBuilder(name);
                    for (String p : paramNames) {
                        String safeParam = p.replaceAll("[^a-zA-Z0-9_$]", "");
                        if (safeParam.isEmpty()) safeParam = "param";
                        specSb.append(" %s.").append(safeParam);
                    }

                    int fnStart = Math.max(0, fn.getAbsolutePosition());
                    int fnEnd = Math.min(jsContent.length(), fnStart + fn.getLength());

                    String bodyCode = "";
                    AstNode bodyNode = fn.getBody();
                    if (bodyNode != null) {
                        int bStart = Math.max(0, bodyNode.getAbsolutePosition());
                        int bEnd = Math.min(jsContent.length(), bStart + bodyNode.getLength());
                        if (bEnd > bStart) {
                            String rawBody = jsContent.substring(bStart, bEnd).trim();
                            if (rawBody.startsWith("{") && rawBody.endsWith("}")) {
                                rawBody = rawBody.substring(1, rawBody.length() - 1);
                            }
                            bodyCode = rawBody.trim();
                        }
                    }

                    result.add(new ExtractedFunction(
                            name,
                            paramNames,
                            specSb.toString(),
                            bodyCode,
                            fnStart,
                            fnEnd
                    ));
                }
            }
        } catch (Throwable ignored) {
            // Fallback: if Rhino cannot parse modern syntax cleanly, keep normal JS block conversion.
        }
        return result;
    }

    /**
     * Rewrites {@code asd} raw-code blocks that represent a call to an extracted MoreBlock
     * function into {@code definedFunc} MoreBlock call blocks with their arguments.
     */
    public static void rewriteMoreBlockCalls(
            List<BlockBean> blocks,
            Map<String, DesignDataManager.MoreBlockData> funcMap) {
        if (blocks == null || funcMap == null || funcMap.isEmpty()) return;

        for (BlockBean b : blocks) {
            if (b == null) continue;
            boolean isRawBlock = "asd".equals(b.opCode) || "asd".equals(b.category);
            if (!isRawBlock || b.parameters == null || b.parameters.isEmpty()) continue;

            String raw = b.parameters.get(0);
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.endsWith(";")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            }

            Matcher m = CALL_PATTERN.matcher(trimmed);
            if (!m.matches()) continue;

            String calledName = m.group(1);
            String argsInside = m.group(2);
            DesignDataManager.MoreBlockData targetMb = funcMap.get(calledName);
            if (targetMb == null) continue;

            // Ensure the outer parentheses actually close at the end of this single call
            // (so `foo(a) + bar(b)` isn't misparsed as one call to `foo`).
            if (!isSingleBalancedCall(trimmed, calledName.length())) continue;

            ArrayList<String> parsedArgs = splitTopLevelArgs(argsInside);

            b.opCode = "definedFunc";
            b.type = " ";
            b.blockType = " ";
            b.category = "more_block";
            b.spec = targetMb.spec != null ? targetMb.spec : calledName;
            b.code = "";
            b.color = MORE_BLOCK_COLOR;
            b.parameters = parsedArgs;
        }
    }

    private static boolean isSingleBalancedCall(String expr, int startSearchIdx) {
        int openIdx = expr.indexOf('(', startSearchIdx);
        if (openIdx < 0) return false;
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = openIdx; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (quote != 0) {
                if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i == expr.length() - 1;
                }
            }
        }
        return false;
    }

    public static ArrayList<String> splitTopLevelArgs(String argsText) {
        ArrayList<String> args = new ArrayList<>();
        if (argsText == null || argsText.trim().isEmpty()) return args;

        StringBuilder current = new StringBuilder();
        int paren = 0, bracket = 0, brace = 0;
        char quote = 0;
        boolean escaped = false;

        for (int i = 0; i < argsText.length(); i++) {
            char c = argsText.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (quote != 0) {
                current.append(c);
                if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                current.append(c);
            } else if (c == '(') {
                paren++;
                current.append(c);
            } else if (c == ')') {
                if (paren > 0) paren--;
                current.append(c);
            } else if (c == '[') {
                bracket++;
                current.append(c);
            } else if (c == ']') {
                if (bracket > 0) bracket--;
                current.append(c);
            } else if (c == '{') {
                brace++;
                current.append(c);
            } else if (c == '}') {
                if (brace > 0) brace--;
                current.append(c);
            } else if (c == ',' && paren == 0 && bracket == 0 && brace == 0) {
                args.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty() || !args.isEmpty()) {
            args.add(last);
        }
        return args;
    }

    private static String removeRanges(String source, List<ExtractedFunction> functions) {
        List<ExtractedFunction> sorted = new ArrayList<>(functions);
        sorted.sort(Comparator.comparingInt(f -> f.startOffset));
        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        for (ExtractedFunction fn : sorted) {
            if (fn.startOffset > cursor && fn.startOffset <= source.length()) {
                sb.append(source, cursor, fn.startOffset);
            }
            cursor = Math.max(cursor, Math.min(source.length(), fn.endOffset));
        }
        if (cursor < source.length()) {
            sb.append(source, cursor, source.length());
        }
        return sb.toString();
    }
}
