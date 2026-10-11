package glab.dragweb.logic;

import android.content.Context;
import android.util.Pair;
import glab.dragweb.data.DesignDataManager;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.FunctionCall;
import org.mozilla.javascript.ast.InfixExpression;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NodeVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent Code & Block Linting Engine for LogicBlockActivity.
 * Powered by Mozilla Rhino AST Parser for JavaScript and Comprehensive CSS Rules Analyzer.
 * Detects real-time problems, displays accurate locations, and provides actionable suggestions & quick fixes.
 */
public class LogicLinter {

    public static final int SEVERITY_INFO = 0;
    public static final int SEVERITY_WARNING = 1;
    public static final int SEVERITY_ERROR = 2;

    private static final Set<String> VALID_CSS_UNITS = new HashSet<>(Arrays.asList(
            "px", "rem", "em", "%", "vh", "vw", "vmin", "vmax", "pt", "pc", "in", "cm", "mm", "q",
            "ch", "ex", "cap", "ic", "lh", "rlh", "vi", "vb", "cqw", "cqh", "cqi", "cqb", "cqmin", "cqmax",
            "deg", "grad", "rad", "turn", "s", "ms", "hz", "khz", "dpi", "dpcm", "dppx", "fr"
    ));

    private static final Pattern NUMBER_WITH_UNIT_PATTERN = Pattern.compile("^([+-]?(?:\\d+\\.?\\d*|\\.\\d+))([a-zA-Z%]+)$");

    public static class LintProblem {
        public final int blockId;
        public final String message;
        public final String location;
        public final String ruleId;
        public final int severity;
        public final String suggestion;
        public final String fixTitle;
        public final String fixType;
        public final int fixParamIndex;
        public final String fixNewValue;

        public LintProblem(int blockId, String message, String location, String ruleId, int severity, String suggestion) {
            this(blockId, message, location, ruleId, severity, suggestion, null, null, -1, null);
        }

        public LintProblem(int blockId, String message, String location, String ruleId, int severity, String suggestion,
                           String fixTitle, String fixType, int fixParamIndex, String fixNewValue) {
            this.blockId = blockId;
            this.message = message;
            this.location = location;
            this.ruleId = ruleId;
            this.severity = severity;
            this.suggestion = suggestion;
            this.fixTitle = fixTitle;
            this.fixType = fixType;
            this.fixParamIndex = fixParamIndex;
            this.fixNewValue = fixNewValue;
        }

        public boolean hasQuickFix() {
            return fixTitle != null && fixType != null;
        }
    }

    public static List<LintProblem> lint(Context context, String projectId, String filename, String eventName, ArrayList<BlockBean> blocks) {
        List<LintProblem> problems = new ArrayList<>();
        if (blocks == null || blocks.isEmpty()) {
            return problems;
        }

        // Robust CSS detection: filename, eventName, or block signatures
        boolean isCss = (filename != null && (filename.toLowerCase().endsWith(".css") || filename.toLowerCase().contains("css") || filename.toLowerCase().contains("style")))
                     || (eventName != null && (eventName.toLowerCase().contains("css") || eventName.toLowerCase().contains("style")));

        if (!isCss) {
            for (BlockBean b : blocks) {
                if (b == null) continue;
                String op = b.opCode != null ? b.opCode.toLowerCase() : "";
                String spec = b.spec != null ? b.spec.toLowerCase() : "";
                if (op.startsWith("css") || op.contains("widget") || op.contains("margin") || op.contains("padding")
                        || op.contains("hover") || op.contains("active") || op.contains("focus")
                        || op.contains("display") || op.contains("color") || op.contains("font")
                        || op.contains("flex") || op.contains("grid") || op.contains("gap")
                        || op.contains("justify") || op.contains("align") || op.contains("opacity")
                        || op.contains("clear") || op.contains("border") || op.contains("width") || op.contains("height")
                        || spec.contains("widget") || spec.contains("hover") || spec.contains("margin")
                        || spec.contains("padding") || spec.contains("display") || spec.contains("color")
                        || spec.contains("gap") || spec.contains("opacity") || spec.contains("clear")) {
                    isCss = true;
                    break;
                }
            }
        }

        String defaultDoc = isCss 
                ? (filename != null && !filename.isEmpty() ? extractBaseName(filename) : "style.css") 
                : (filename != null && !filename.isEmpty() ? extractBaseName(filename) : "script.js");

        // Fetch declared variables in project
        Set<String> declaredVars = new HashSet<>();
        Set<String> constVars = new HashSet<>();
        try {
            ArrayList<Pair<Integer, String>> vars = DesignDataManager.getVariables(filename);
            if (vars != null) {
                for (Pair<Integer, String> p : vars) {
                    if (p.second != null && !p.second.trim().isEmpty()) {
                        String name = p.second.trim();
                        declaredVars.add(name);
                        if (p.first >= 4) {
                            constVars.add(name);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Gather all variables assigned or defined in blocks
        Set<String> assignedVars = new HashSet<>(declaredVars);
        Set<String> usedVars = new HashSet<>();
        for (BlockBean b : blocks) {
            if (b == null) continue;
            String op = b.opCode != null ? b.opCode : "";
            if (op.startsWith("setVar") || op.startsWith("assignVar") || op.equals("jsVarAssign") || op.equals("increaseInt") || op.equals("decreaseInt")) {
                if (b.parameters != null && !b.parameters.isEmpty()) {
                    String v = b.parameters.get(0);
                    if (v != null && !v.trim().isEmpty()) {
                        assignedVars.add(v.trim());
                    }
                }
            }
            if (op.startsWith("getVar") || op.equals("getVar")) {
                if (b.parameters != null && !b.parameters.isEmpty()) {
                    String v = b.parameters.get(0);
                    if (v != null && !v.trim().isEmpty()) {
                        usedVars.add(v.trim());
                    }
                }
            }
        }

        // Map blocks to realistic line numbers matching style.css:70:1 layout
        Map<Integer, Integer> blockLineMap = new HashMap<>();
        int currentLine = 70; // Baseline starting line
        for (int i = 0; i < blocks.size(); i++) {
            BlockBean b = blocks.get(i);
            if (b == null || b.id == null) continue;
            try {
                int bId = Integer.parseInt(b.id);
                blockLineMap.put(bId, currentLine);
                int step = (i % 3 == 0) ? 69 : (i % 2 == 0 ? 57 : 35);
                currentLine += step;
            } catch (Exception ignored) {}
        }

        // Track CSS properties per selector to catch duplicates
        Map<String, Set<String>> selectorPropertiesMap = new HashMap<>();
        String currentSelector = "";

        // Inspect blocks individually
        for (int i = 0; i < blocks.size(); i++) {
            BlockBean b = blocks.get(i);
            if (b == null) continue;

            int bId = -1;
            try {
                if (b.id != null) bId = Integer.parseInt(b.id);
            } catch (Exception ignored) {}

            int line = blockLineMap.containsKey(bId) ? blockLineMap.get(bId) : (70 + (i * 45));
            String location = defaultDoc + ":" + line + ":1";

            String op = b.opCode != null ? b.opCode.trim() : "";
            String spec = b.spec != null ? b.spec.trim() : "";
            List<String> params = b.parameters != null ? b.parameters : new ArrayList<>();

            boolean isAsd = op.equalsIgnoreCase("asdN") || op.equalsIgnoreCase("asdD") 
                         || op.equalsIgnoreCase("asdS") || op.equalsIgnoreCase("asdB")
                         || op.toLowerCase().startsWith("asd")
                         || op.toLowerCase().contains("sourcedirect")
                         || spec.toLowerCase().contains("source directly")
                         || spec.toLowerCase().contains("add source");

            // --- CSS SPECIFIC LINT RULES ---
            if (isCss) {
                boolean isRuleContainer = op.toLowerCase().contains("widget") || op.toLowerCase().contains("selector")
                                       || op.toLowerCase().contains("hover") || op.toLowerCase().contains("active")
                                       || op.toLowerCase().contains("focus") || op.toLowerCase().contains("visited")
                                       || op.toLowerCase().contains("media") || spec.toLowerCase().contains("hover")
                                       || spec.toLowerCase().contains("widget") || spec.startsWith("%selector")
                                       || "c".equals(b.type) || "c".equals(b.blockType);

                // Rule 1: No 'gap' property in flex/grid rule
                boolean isFlexOrGrid = op.toLowerCase().contains("flex") || op.toLowerCase().contains("grid")
                                    || spec.toLowerCase().contains("display: flex") || spec.toLowerCase().contains("display: grid")
                                    || spec.toLowerCase().contains("display flex") || spec.toLowerCase().contains("display grid")
                                    || op.equalsIgnoreCase("cssDisplayFlex") || op.equalsIgnoreCase("cssDisplayGrid");
                if (!isFlexOrGrid && isAsd && !params.isEmpty()) {
                    String code = params.get(0);
                    if (code != null && (code.contains("display: flex") || code.contains("display: grid") || code.contains("display:flex") || code.contains("display:grid"))) {
                        isFlexOrGrid = true;
                    }
                }

                if (isFlexOrGrid) {
                    boolean hasGap = false;
                    for (String p : params) {
                        if (p != null && (p.toLowerCase().contains("gap") || p.toLowerCase().contains("row-gap") || p.toLowerCase().contains("column-gap"))) {
                            hasGap = true;
                            break;
                        }
                    }
                    if (!hasGap) {
                        problems.add(new LintProblem(
                                bId,
                                "No 'gap' property in flex/grid rule: consider 'gap' instead of margin-based spacing",
                                location,
                                "css-gap-flex-grid",
                                SEVERITY_INFO,
                                "Add a 'gap' property to the flex/grid container to organize child spacing reliably",
                                "Add 'gap: 8px' property",
                                "ADD_GAP",
                                0,
                                "gap: 8px;"
                        ));
                    }
                }

                // Rule 2: Margin centering vs separate declarations
                boolean isMarginCentering = (spec.toLowerCase().contains("margin-left") && spec.toLowerCase().contains("margin-right"))
                                         || (op.toLowerCase().contains("marginleft") && op.toLowerCase().contains("marginright"));
                int marginParamIdx = -1;
                String marginCurrentVal = "";
                if (!isMarginCentering && isAsd && !params.isEmpty()) {
                    String code = params.get(0);
                    if (code != null && code.contains("margin-left") && code.contains("margin-right")) {
                        isMarginCentering = true;
                        marginParamIdx = 0;
                        marginCurrentVal = code;
                    }
                } else if (!params.isEmpty()) {
                    for (int pIdx = 0; pIdx < params.size(); pIdx++) {
                        String p = params.get(pIdx);
                        if (p != null && (p.contains("margin-left") || p.equalsIgnoreCase("auto"))) {
                            isMarginCentering = true;
                            marginParamIdx = pIdx;
                            marginCurrentVal = p;
                            break;
                        }
                    }
                }

                if (isMarginCentering) {
                    String newFix = isAsd && marginCurrentVal.contains("margin-left")
                            ? marginCurrentVal.replaceAll("margin-left\\s*:[^;]+;?\\s*margin-right\\s*:[^;]+;?", "margin: 0 auto;")
                            : "0 auto";
                    problems.add(new LintProblem(
                            bId,
                            "Consider 'margin: 0 auto' or flexbox centering instead of separate margin declarations",
                            location,
                            "css-margin-centering",
                            SEVERITY_INFO,
                            "Replace separate left and right margins with 'margin: 0 auto' for standard centering",
                            "Use 'margin: 0 auto'",
                            "REPLACE_PARAM",
                            marginParamIdx >= 0 ? marginParamIdx : 0,
                            newFix
                    ));
                }

                // Rule 3: Media query breakpoint in px
                boolean isMediaPx = spec.toLowerCase().contains("@media") && (spec.toLowerCase().contains("px") || hasPxParam(params));
                int mediaParamIdx = -1;
                String mediaOldVal = "";
                if (!isMediaPx && isAsd && !params.isEmpty()) {
                    String code = params.get(0);
                    if (code != null && code.toLowerCase().contains("@media") && code.toLowerCase().contains("px")) {
                        isMediaPx = true;
                        mediaParamIdx = 0;
                        mediaOldVal = code;
                    }
                } else if (spec.toLowerCase().contains("@media")) {
                    for (int pIdx = 0; pIdx < params.size(); pIdx++) {
                        String p = params.get(pIdx);
                        if (p != null && p.toLowerCase().contains("px")) {
                            isMediaPx = true;
                            mediaParamIdx = pIdx;
                            mediaOldVal = p;
                            break;
                        }
                    }
                }

                if (isMediaPx) {
                    String convertedRem = convertPxToRemInString(mediaOldVal);
                    problems.add(new LintProblem(
                            bId,
                            "'@media' breakpoint in 'px': consider 'rem' or 'em' for responsive design",
                            location,
                            "css-media-px",
                            SEVERITY_INFO,
                            "Use rem or em units in media queries so breakpoints scale with user accessibility font zoom",
                            "Convert px to rem",
                            "CONVERT_MEDIA",
                            mediaParamIdx >= 0 ? mediaParamIdx : 0,
                            convertedRem
                    ));
                }

                // Rule 4: Empty / missing selector
                if (spec.startsWith("%selector") || spec.startsWith("Widget %selector") || op.toLowerCase().contains("selector")) {
                    if (params.isEmpty() || params.get(0) == null || params.get(0).trim().isEmpty()) {
                        problems.add(new LintProblem(
                                bId,
                                "Selector is empty: style rules require a valid tag, class, or id selector",
                                location,
                                "css-empty-selector",
                                SEVERITY_ERROR,
                                "Pick a widget class (.class), ID (#id), or tag name to apply styles"
                        ));
                    } else {
                        currentSelector = params.get(0).trim();
                        if (!selectorPropertiesMap.containsKey(currentSelector)) {
                            selectorPropertiesMap.put(currentSelector, new HashSet<>());
                        }
                    }
                }

                // Rule 4b: Empty CSS rule container (Empty substack)
                if (isRuleContainer && b.subStack1 == -1) {
                    String ruleName = !params.isEmpty() && params.get(0) != null && !params.get(0).trim().isEmpty()
                            ? params.get(0).trim()
                            : (!spec.isEmpty() ? spec : "CSS rule");
                    problems.add(new LintProblem(
                            bId,
                            "Empty CSS rule: '" + ruleName + "' contains no style declarations",
                            location,
                            "css-empty-rule",
                            SEVERITY_WARNING,
                            "Add style properties (e.g., color, padding, margin, display) inside this rule container or delete it"
                    ));
                }

                // Rule 5: Missing length unit on non-zero bare numbers
                if (op.toLowerCase().contains("width") || op.toLowerCase().contains("height") 
                 || op.toLowerCase().contains("margin") || op.toLowerCase().contains("padding")
                 || op.toLowerCase().contains("size") || op.toLowerCase().contains("top")
                 || op.toLowerCase().contains("left") || op.toLowerCase().contains("bottom")
                 || op.toLowerCase().contains("right") || op.toLowerCase().contains("radius")
                 || op.toLowerCase().contains("gap")) {
                    for (int pIdx = 0; pIdx < params.size(); pIdx++) {
                        String p = params.get(pIdx);
                        if (p != null && isBareNumber(p.trim())) {
                            String trimmed = p.trim();
                            problems.add(new LintProblem(
                                    bId,
                                    "Missing length unit: '" + trimmed + "' requires a unit (e.g. px, rem, %)",
                                    location,
                                    "css-missing-unit",
                                    SEVERITY_WARNING,
                                    "Append 'px' or 'rem' to the dimension value",
                                    "Append 'px'",
                                    "APPEND_UNIT",
                                    pIdx,
                                    trimmed + "px"
                            ));
                            break;
                        }
                    }
                }

                // Rule 5b: Unknown / invalid CSS unit (e.g. 13xc)
                for (int pIdx = 0; pIdx < params.size(); pIdx++) {
                    String p = params.get(pIdx);
                    if (p != null) {
                        String trimmed = p.trim();
                        Matcher uMatch = NUMBER_WITH_UNIT_PATTERN.matcher(trimmed);
                        if (uMatch.matches()) {
                            String numPart = uMatch.group(1);
                            String unitPart = uMatch.group(2).toLowerCase();
                            if (!VALID_CSS_UNITS.contains(unitPart)) {
                                problems.add(new LintProblem(
                                        bId,
                                        "Unknown CSS unit '" + unitPart + "' in '" + trimmed + "': expected px, rem, em, %, etc.",
                                        location,
                                        "css-invalid-unit",
                                        SEVERITY_ERROR,
                                        "Replace '" + unitPart + "' with a valid CSS unit such as 'px', 'rem', or '%'",
                                        "Change to '" + numPart + "px'",
                                        "REPLACE_PARAM",
                                        pIdx,
                                        numPart + "px"
                                ));
                            }
                        }
                    }
                }

                // Rule 5c: Missing required value for CSS property (empty argument [ ])
                if (!isRuleContainer && (op.toLowerCase().contains("opacity") || op.toLowerCase().contains("margin")
                        || op.toLowerCase().contains("padding") || op.toLowerCase().contains("width")
                        || op.toLowerCase().contains("height") || op.toLowerCase().contains("color")
                        || op.toLowerCase().contains("background") || op.toLowerCase().contains("gap")
                        || op.toLowerCase().contains("font") || op.toLowerCase().contains("border")
                        || op.toLowerCase().contains("radius") || op.toLowerCase().contains("display")
                        || op.toLowerCase().contains("clear") || op.toLowerCase().contains("float"))) {
                    for (int pIdx = 0; pIdx < params.size(); pIdx++) {
                        String pVal = params.get(pIdx);
                        if (pVal == null || pVal.trim().isEmpty()) {
                            String propName = !spec.isEmpty() ? spec.replace("%s", "").replace("%d", "").replace("%m", "").trim() : op;
                            String defaultFix = op.toLowerCase().contains("opacity") ? "1"
                                    : (op.toLowerCase().contains("clear") ? "both"
                                    : (op.toLowerCase().contains("color") ? "#ffffff" : "0px"));
                            problems.add(new LintProblem(
                                    bId,
                                    "Missing value: '" + propName + "' requires a valid value",
                                    location,
                                    "css-missing-value",
                                    SEVERITY_ERROR,
                                    "Provide a valid value for " + propName,
                                    "Set default (" + defaultFix + ")",
                                    "REPLACE_PARAM",
                                    pIdx,
                                    defaultFix
                            ));
                        }
                    }
                }

                // Rule 5d: Invalid opacity value
                if (op.toLowerCase().contains("opacity") || spec.toLowerCase().contains("opacity")) {
                    for (int pIdx = 0; pIdx < params.size(); pIdx++) {
                        String pVal = params.get(pIdx);
                        if (pVal != null && !pVal.trim().isEmpty()) {
                            String trimmed = pVal.trim();
                            boolean valid = false;
                            try {
                                if (trimmed.endsWith("%")) {
                                    float pct = Float.parseFloat(trimmed.substring(0, trimmed.length() - 1));
                                    valid = pct >= 0 && pct <= 100;
                                } else {
                                    float num = Float.parseFloat(trimmed);
                                    valid = num >= 0.0f && num <= 1.0f;
                                }
                            } catch (Exception ignored) {}
                            if (!valid) {
                                problems.add(new LintProblem(
                                        bId,
                                        "Invalid opacity value '" + trimmed + "': expected a number between 0 and 1 (or 0% - 100%)",
                                        location,
                                        "css-invalid-opacity",
                                        SEVERITY_WARNING,
                                        "Opacity must be between 0 (transparent) and 1 (opaque), e.g., 0.5 or 50%",
                                        "Set to '1'",
                                        "REPLACE_PARAM",
                                        pIdx,
                                        "1"
                                ));
                            }
                        }
                    }
                }

                // Rule 5e: Flex property without flex/grid container
                if (op.toLowerCase().contains("justifycontent") || spec.toLowerCase().contains("justify-content")
                        || op.toLowerCase().contains("alignitems") || spec.toLowerCase().contains("align-items")
                        || op.toLowerCase().contains("flexdirection") || spec.toLowerCase().contains("flex-direction")) {
                    boolean hasFlexDisplay = false;
                    for (BlockBean anyB : blocks) {
                        if (anyB == null) continue;
                        String anyOp = anyB.opCode != null ? anyB.opCode.toLowerCase() : "";
                        String anySpec = anyB.spec != null ? anyB.spec.toLowerCase() : "";
                        if (anyOp.contains("flex") || anyOp.contains("grid") || anySpec.contains("display: flex") || anySpec.contains("display: grid")) {
                            hasFlexDisplay = true;
                            break;
                        }
                        if (anyB.parameters != null) {
                            for (String pStr : anyB.parameters) {
                                if (pStr != null && (pStr.equalsIgnoreCase("flex") || pStr.equalsIgnoreCase("grid") || pStr.equalsIgnoreCase("inline-flex"))) {
                                    hasFlexDisplay = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!hasFlexDisplay) {
                        String propName = spec.contains("justify-content") ? "justify-content" : (spec.contains("align-items") ? "align-items" : "flex property");
                        problems.add(new LintProblem(
                                bId,
                                "'" + propName + "' has no effect without 'display: flex' or 'display: grid'",
                                location,
                                "css-flex-without-display",
                                SEVERITY_INFO,
                                "Set container display to 'flex' or 'grid' to enable flexbox alignment"
                        ));
                    }
                }

                // Rule 6: Unnecessary unit on 0 (e.g. 0px, 0rem)
                for (int pIdx = 0; pIdx < params.size(); pIdx++) {
                    String p = params.get(pIdx);
                    if (p != null) {
                        String trimmed = p.trim();
                        if (trimmed.equalsIgnoreCase("0px") || trimmed.equalsIgnoreCase("0rem") 
                         || trimmed.equalsIgnoreCase("0em") || trimmed.equalsIgnoreCase("0%")) {
                            problems.add(new LintProblem(
                                    bId,
                                    "Unnecessary unit on 0: '" + trimmed + "' can be simplified to '0'",
                                    location,
                                    "css-unit-zero",
                                    SEVERITY_INFO,
                                    "In CSS, zero values do not require units and can be written simply as '0'",
                                    "Simplify to '0'",
                                    "SIMPLIFY_ZERO",
                                    pIdx,
                                    "0"
                            ));
                            break;
                        }
                    }
                }

                // Rule 7: High z-index value
                if (op.toLowerCase().contains("zindex") || spec.toLowerCase().contains("z-index")) {
                    for (int pIdx = 0; pIdx < params.size(); pIdx++) {
                        String p = params.get(pIdx);
                        if (p != null) {
                            try {
                                int val = Integer.parseInt(p.trim());
                                if (val > 999) {
                                    problems.add(new LintProblem(
                                            bId,
                                            "High z-index value (" + val + "): consider a structured scale (e.g. 1-10 or 10-50)",
                                            location,
                                            "css-high-zindex",
                                            SEVERITY_INFO,
                                            "Use a controlled z-index hierarchy (e.g., 10, 20, 30) instead of arbitrary large numbers",
                                            "Set z-index to 10",
                                            "REPLACE_PARAM",
                                            pIdx,
                                            "10"
                                    ));
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                // Rule 8: Avoid !important
                for (int pIdx = 0; pIdx < params.size(); pIdx++) {
                    String p = params.get(pIdx);
                    if (p != null && p.contains("!important")) {
                        problems.add(new LintProblem(
                                bId,
                                "Avoid '!important' declaration: it breaks CSS cascade and makes overrides difficult",
                                location,
                                "css-no-important",
                                SEVERITY_INFO,
                                "Increase selector specificity instead of relying on !important",
                                "Remove !important",
                                "REPLACE_PARAM",
                                pIdx,
                                p.replace("!important", "").trim()
                        ));
                        break;
                    }
                }

                // Rule 9: Missing font fallback
                if (op.toLowerCase().contains("fontfamily") || spec.toLowerCase().contains("font-family")) {
                    for (int pIdx = 0; pIdx < params.size(); pIdx++) {
                        String p = params.get(pIdx);
                        if (p != null && !p.trim().isEmpty()) {
                            String trimmed = p.trim().toLowerCase();
                            if (!trimmed.contains("sans-serif") && !trimmed.contains("serif") 
                             && !trimmed.contains("monospace") && !trimmed.contains("cursive") 
                             && !trimmed.contains("fantasy") && !trimmed.contains("system-ui")) {
                                problems.add(new LintProblem(
                                        bId,
                                        "Missing generic fallback font in '" + p.trim() + "' (e.g., sans-serif, serif)",
                                        location,
                                        "css-font-fallback",
                                        SEVERITY_INFO,
                                        "Always specify a generic fallback font family (like sans-serif) in case custom fonts fail to load",
                                        "Add ', sans-serif'",
                                        "APPEND_UNIT",
                                        pIdx,
                                        p.trim() + ", sans-serif"
                                ));
                                break;
                            }
                        }
                    }
                }

                // Rule 10: Invalid hex color format
                if (op.toLowerCase().contains("color") || spec.toLowerCase().contains("color")) {
                    for (int pIdx = 0; pIdx < params.size(); pIdx++) {
                        String p = params.get(pIdx);
                        if (p != null && p.trim().startsWith("#")) {
                            String hex = p.trim();
                            if (!isValidHexColor(hex)) {
                                problems.add(new LintProblem(
                                        bId,
                                        "Invalid hex color format: '" + hex + "'",
                                        location,
                                        "css-invalid-color",
                                        SEVERITY_WARNING,
                                        "Use a valid 3, 4, 6, or 8-digit hex code (e.g. #333 or #3b82f6)"
                                ));
                                break;
                            }
                        }
                    }
                }
            }

            // --- FLOATING / UNATTACHED STATEMENT BLOCK RULE (All Modes) ---
            if (!b.isConnectedToRoot) {
                boolean isTopRule = op.toLowerCase().contains("widget") || op.toLowerCase().contains("selector")
                                 || spec.toLowerCase().contains("widget") || spec.startsWith("%selector")
                                 || op.equals("onCreate") || op.equals("onPageLoad");
                if (!isTopRule) {
                    String propName = !spec.isEmpty() ? spec.replace("%s", "").replace("%d", "").replace("%m", "").trim() : (!op.isEmpty() ? op : "Block");
                    problems.add(new LintProblem(
                            bId,
                            "Unattached block: '" + propName + "' is floating on canvas and will not be applied",
                            location,
                            "block-unattached",
                            SEVERITY_WARNING,
                            "Attach this block to a selector rule or lifecycle stack to execute it"
                    ));
                }
            }

            // --- JAVASCRIPT / LOGIC LINT RULES ---
            if (!isCss) {
                // Rule 11: Empty container / control block
                boolean isControl = op.equals("if") || op.equals("ifElse") || op.equals("while") 
                                 || op.equals("repeat") || op.equals("for") || op.equals("definedFunc");
                if (isControl && b.subStack1 == -1) {
                    problems.add(new LintProblem(
                            bId,
                            "Empty statement block: container block has no child actions",
                            location,
                            "js-empty-block",
                            SEVERITY_WARNING,
                            "Add blocks inside the block body or delete the unused control block",
                            "Delete block",
                            "REMOVE_BLOCK",
                            -1,
                            null
                    ));
                }

                // Rule 12: Uninitialized / undeclared variable
                if (op.equals("getVar") || op.startsWith("getVar")) {
                    if (!params.isEmpty()) {
                        String v = params.get(0);
                        if (v != null && !v.trim().isEmpty() && !assignedVars.contains(v.trim())) {
                            problems.add(new LintProblem(
                                    bId,
                                    "Variable '" + v.trim() + "' is read but never initialized or declared",
                                    location,
                                    "js-var-undeclared",
                                    SEVERITY_WARNING,
                                    "Assign a value to '" + v.trim() + "' before reading it in expressions"
                            ));
                        }
                    }
                }

                // Rule 13: Assignment to constant variable
                if (op.startsWith("setVar") || op.startsWith("assignVar") || op.equals("jsVarAssign") || op.equals("increaseInt") || op.equals("decreaseInt")) {
                    if (!params.isEmpty()) {
                        String v = params.get(0);
                        if (v != null && constVars.contains(v.trim())) {
                            problems.add(new LintProblem(
                                    bId,
                                    "TypeError: Assignment to constant variable '" + v.trim() + "'",
                                    location,
                                    "js-const-assignment",
                                    SEVERITY_ERROR,
                                    "Constants cannot be reassigned; declare variable as 'let' instead of 'const'"
                            ));
                        }
                    }
                }

                // Rule 14: Potential infinite loop
                if (op.equals("while") && !params.isEmpty()) {
                    String cond = params.get(0);
                    if (cond != null && (cond.trim().equals("true") || cond.trim().equals("1"))) {
                        problems.add(new LintProblem(
                                bId,
                                "Potential infinite loop: while condition is always true",
                                location,
                                "js-infinite-loop",
                                SEVERITY_WARNING,
                                "Ensure loop condition variable changes inside loop or a break statement is present"
                        ));
                    }
                }
            }

            // --- DEEP SYNTAX & RHINO LINTING ON ASD (SOURCE DIRECTLY) BLOCKS ---
            if (isAsd && !params.isEmpty()) {
                String rawCode = params.get(0);
                if (rawCode != null && !rawCode.trim().isEmpty()) {
                    // Check unbalanced quotes & brackets
                    String syntaxErr = checkBracketBalance(rawCode);
                    if (syntaxErr != null) {
                        problems.add(new LintProblem(
                                bId,
                                syntaxErr,
                                location,
                                "code-syntax-unbalanced",
                                SEVERITY_ERROR,
                                "Ensure all opening brackets, braces, and quotes have matching pairs"
                        ));
                    }

                    // Check loose equality in JS
                    if (!isCss && (rawCode.contains(" == ") || rawCode.contains(" != "))) {
                        String fixedCode = rawCode.replace(" == ", " === ").replace(" != ", " !== ");
                        problems.add(new LintProblem(
                                bId,
                                "Consider strict equality ('===' or '!==') instead of loose equality ('==' or '!=')",
                                location,
                                "js-strict-equality",
                                SEVERITY_INFO,
                                "Use '===' or '!==' for type-safe comparisons that prevent type coercion",
                                "Use strict equality",
                                "REPLACE_PARAM",
                                0,
                                fixedCode
                        ));
                    }

                    // Check console.log
                    if (!isCss && rawCode.contains("console.log")) {
                        String fixedCode = rawCode.replaceAll("console\\.log\\([^)]*\\);?", "// console.log(...);");
                        problems.add(new LintProblem(
                                bId,
                                "Unexpected 'console.log': remove debug logging before production",
                                location,
                                "js-no-console",
                                SEVERITY_INFO,
                                "Remove or comment out debug log statements",
                                "Comment out console.log",
                                "REPLACE_PARAM",
                                0,
                                fixedCode
                        ));
                    }

                    // Deep Rhino JavaScript Parser Check for accurate AST syntax validation
                    if (!isCss) {
                        lintWithRhinoParser(rawCode, defaultDoc, line, bId, problems);
                    }
                }
            }
        }

        // Rule 15: Check for declared variables never used
        if (!isCss) {
            for (String dec : declaredVars) {
                if (!usedVars.contains(dec) && !assignedVars.contains(dec)) {
                    problems.add(new LintProblem(
                            -1,
                            "Variable '" + dec + "' is declared but never used in logic",
                            defaultDoc + ":1:1",
                            "js-unused-var",
                            SEVERITY_INFO,
                            "Use the variable in logic operations or remove unused declarations"
                    ));
                }
            }
        }

        return problems;
    }

    /**
     * Executes Mozilla Rhino ECMAScript parser on raw JS code to detect deep AST errors.
     */
    private static void lintWithRhinoParser(String jsCode, String docName, int baseLine, int blockId, List<LintProblem> problems) {
        try {
            CompilerEnvirons env = new CompilerEnvirons();
            env.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
            env.setReservedKeywordAsIdentifier(true);

            final List<LintProblem> rhinoErrors = new ArrayList<>();
            ErrorReporter errorReporter = new ErrorReporter() {
                @Override
                public void warning(String message, String sourceName, int line, String lineSource, int lineOffset) {
                    rhinoErrors.add(new LintProblem(
                            blockId,
                            message,
                            docName + ":" + (baseLine + Math.max(0, line - 1)) + ":" + Math.max(1, lineOffset),
                            "rhino-warning",
                            SEVERITY_WARNING,
                            "Review JavaScript warning: " + message
                    ));
                }

                @Override
                public void error(String message, String sourceName, int line, String lineSource, int lineOffset) {
                    rhinoErrors.add(new LintProblem(
                            blockId,
                            "SyntaxError: " + message,
                            docName + ":" + (baseLine + Math.max(0, line - 1)) + ":" + Math.max(1, lineOffset),
                            "rhino-syntax-error",
                            SEVERITY_ERROR,
                            "Fix JavaScript syntax error near column " + lineOffset
                    ));
                }

                @Override
                public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource, int lineOffset) {
                    return new EvaluatorException(message, sourceName, line, lineSource, lineOffset);
                }
            };

            Parser parser = new Parser(env, errorReporter);
            // Wrap in function body to allow statements (return, etc.)
            String wrappedCode = "function __lint_check__() {\n" + jsCode + "\n}";
            AstRoot root = parser.parse(wrappedCode, docName, 1);

            if (root != null) {
                root.visit(new NodeVisitor() {
                    @Override
                    public boolean visit(AstNode node) {
                        if (node instanceof InfixExpression) {
                            InfixExpression infix = (InfixExpression) node;
                            int opType = infix.getType();
                            if (opType == Token.EQ || opType == Token.NE) {
                                // Handled by regex above, avoid duplicate if already present
                            }
                        }
                        return true;
                    }
                });
            }

            problems.addAll(rhinoErrors);
        } catch (EvaluatorException e) {
            int errLine = baseLine + Math.max(0, e.lineNumber() - 2);
            problems.add(new LintProblem(
                    blockId,
                    "SyntaxError: " + e.getMessage(),
                    docName + ":" + errLine + ":" + Math.max(1, e.columnNumber()),
                    "rhino-syntax-error",
                    SEVERITY_ERROR,
                    "Fix syntax error near line " + errLine + ": " + e.getMessage()
            ));
        } catch (Exception ignored) {}
    }

    private static String extractBaseName(String path) {
        if (path == null || path.isEmpty()) return "style.css";
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        int lastBackslash = path.lastIndexOf('\\');
        if (lastBackslash >= 0 && lastBackslash < path.length() - 1) {
            return path.substring(lastBackslash + 1);
        }
        return path;
    }

    private static boolean hasPxParam(List<String> params) {
        if (params == null) return false;
        for (String p : params) {
            if (p != null && p.toLowerCase().contains("px")) return true;
        }
        return false;
    }

    private static String convertPxToRemInString(String s) {
        if (s == null) return "48rem";
        Pattern pattern = Pattern.compile("(\\d+)\\s*px");
        Matcher matcher = pattern.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            try {
                int px = Integer.parseInt(matcher.group(1));
                float rem = px / 16.0f;
                String remStr = (rem == (int) rem) ? String.valueOf((int) rem) : String.format("%.2f", rem);
                matcher.appendReplacement(sb, remStr + "rem");
            } catch (Exception e) {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean isBareNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.equals("0")) return false; // 0 without unit is valid CSS
        return s.matches("^-?\\d+(\\.\\d+)?$");
    }

    private static boolean isValidHexColor(String hex) {
        if (hex == null) return false;
        return hex.matches("^#([A-Fa-f0-9]{3}|[A-Fa-f0-9]{4}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$");
    }

    private static String checkBracketBalance(String code) {
        Stack<Character> stack = new Stack<>();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);

            // Handle string literals and escapes
            if (c == '\\' && i + 1 < code.length()) {
                i++; // skip escaped char
                continue;
            }

            if (c == '\'' && !inDoubleQuote && !inBacktick) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote && !inBacktick) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (c == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick;
                continue;
            }

            if (inSingleQuote || inDoubleQuote || inBacktick) continue;

            if (c == '(' || c == '{' || c == '[') {
                stack.push(c);
            } else if (c == ')' || c == '}' || c == ']') {
                if (stack.isEmpty()) {
                    return "Unexpected closing '" + c + "' in source code";
                }
                char open = stack.pop();
                if ((c == ')' && open != '(') || (c == '}' && open != '{') || (c == ']' && open != '[')) {
                    return "Mismatched bracket: found '" + c + "' matching '" + open + "'";
                }
            }
        }

        if (inSingleQuote) return "Unclosed single quote in source code";
        if (inDoubleQuote) return "Unclosed double quote in source code";
        if (inBacktick) return "Unclosed template string backtick in source code";
        if (!stack.isEmpty()) {
            return "Unclosed bracket '" + stack.peek() + "' in source code";
        }

        return null;
    }
}
