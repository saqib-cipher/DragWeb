package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogicBlockManager {

    // Page-based events (Sketchware pattern)
    public static final String EVENT_PAGE_LOAD = "pageLoad";
    public static final String EVENT_VISIBLE = "visible";
    public static final String EVENT_HIDDEN = "hidden";
    public static final String EVENT_DESTROY = "destroy";
    public static final String EVENT_PAGE_SCROLL = "pageScroll";
    public static final String EVENT_PAGE_INPUT = "pageInput";

    // Element events (retained for backward compatibility when loading saved projects)
    public static final String EVENT_CLICK = "click";
    public static final String EVENT_HOVER = "hover";
    public static final String EVENT_INPUT = "input";
    public static final String EVENT_LOAD = "load";
    public static final String EVENT_SUBMIT = "submit";
    public static final String EVENT_SCROLL = "scroll";
    public static final String EVENT_KEYDOWN = "keydown";
    public static final String EVENT_CHANGE = "change";

    public String category; // Used for UI grouping

    // CSS pseudo-class events (generate pure CSS rules, no JavaScript)
    public static final String EVENT_CSS_HOVER   = "css:hover";
    public static final String EVENT_CSS_FOCUS   = "css:focus";
    public static final String EVENT_CSS_ACTIVE  = "css:active";
    public static final String EVENT_CSS_VISITED = "css:visited";

    public static final String ACTION_CHANGE_STYLE = "changeStyle";
    public static final String ACTION_ANIMATE = "animate";
    public static final String ACTION_NAVIGATE = "navigate";
    public static final String ACTION_SHOW_HIDE = "showHide";
    public static final String ACTION_SET_TEXT = "setText";
    public static final String ACTION_ADD_CLASS = "addClass";
    public static final String ACTION_REMOVE_CLASS = "removeClass";
    public static final String ACTION_TOGGLE_CLASS = "toggleClass";
    public static final String ACTION_ALERT = "alert";
    public static final String ACTION_CONSOLE_LOG = "consoleLog";
    public static final String ACTION_SET_ATTRIBUTE = "setAttribute";
    public static final String ACTION_REMOVE_ATTRIBUTE = "removeAttribute";
    public static final String ACTION_SET_VALUE = "setValue";
    public static final String ACTION_APPEND_CHILD = "appendChild";
    public static final String ACTION_PREPEND_CHILD = "prependChild";
    public static final String ACTION_CREATE_ELEMENT = "createElement";
    public static final String ACTION_REMOVE_ELEMENT = "removeElement";
    public static final String ACTION_CUSTOM_JS = "customJs";
    public static final String ACTION_FETCH_API = "fetchApi";
    public static final String ACTION_LOCAL_STORAGE = "localStorage";
    public static final String ACTION_SCROLL_TO = "scrollTo";
    public static final String ACTION_COPY_CLIPBOARD = "copyClipboard";
    public static final String ACTION_DELAY = "delay";
    public static final String ACTION_GO_TO_PAGE = "goToPage";
    public static final String ACTION_OPEN_PAGE = "openPage";
    public static final String ACTION_SET_HTML = "setHTML";
    public static final String ACTION_FOCUS_INPUT = "focusInput";
    public static final String ACTION_BLUR_INPUT = "blurInput";
    public static final String ACTION_SET_HREF = "setHref";
    public static final String ACTION_SET_TRANSFORM = "setTransform";
    public static final String ACTION_SET_TRANSITION = "setTransition";

    // Logic block actions
    public static final String ACTION_IF_BLOCK = "ifBlock";
    public static final String ACTION_IF_ELSE_BLOCK = "ifElseBlock";
    public static final String ACTION_LOOP = "loop";

    // Variable block actions
    public static final String ACTION_CREATE_VAR = "createVar";
    public static final String ACTION_SET_VAR = "setVar";
    public static final String ACTION_GET_VAR = "getVar";

    public static final String TARGET_MODE_ID = "id";
    public static final String TARGET_MODE_CLASS = "class";
    public static final String TARGET_MODE_TAG = "tag";

    private Context context;
    private List<LogicBlock> blocks = new ArrayList<>();

    public LogicBlockManager(Context context) {
        this.context = context;
    }

    public void addBlock(LogicBlock block) {
        blocks.add(block);
    }

    public void removeBlock(int index) {
        if (index >= 0 && index < blocks.size()) {
            blocks.remove(index);
        }
    }


    public List<LogicBlock> getBlocks() {
        return blocks;
    }

    public List<LogicBlock> getBlocksForWidget(String widgetTag) {
        List<LogicBlock> result = new ArrayList<>();
        for (LogicBlock block : blocks) {
            if (widgetTag.equals(block.targetWidget)) {
                result.add(block);
            }
        }
        return result;
    }

    /**
     * Get blocks filtered by a specific page event type.
     */
    public List<LogicBlock> getBlocksForEvent(String eventType) {
        List<LogicBlock> result = new ArrayList<>();
        for (LogicBlock block : blocks) {
            if (eventType.equals(block.event)) {
                result.add(block);
            }
        }
        return result;
    }

    /**
     * Count blocks for a specific event type.
     */
    public int getBlockCountForEvent(String eventType) {
        int count = 0;
        for (LogicBlock block : blocks) {
            if (eventType.equals(block.event)) {
                count++;
            }
        }
        return count;
    }


    /**
     * Get display name for an event type.
     */
    public static String getEventDisplayName(String eventType) {
        switch (eventType) {
            case EVENT_PAGE_LOAD: return "On Page Load";
            case EVENT_VISIBLE: return "On Visible";
            case EVENT_HIDDEN: return "On Hidden";
            case EVENT_DESTROY: return "On Destroy";
            case EVENT_PAGE_SCROLL: return "On Scroll";
            case EVENT_PAGE_INPUT: return "On Input";
            case EVENT_CLICK: return "On Click (legacy)";
            case EVENT_HOVER: return "On Hover (legacy)";
            case EVENT_CSS_HOVER:   return "CSS :hover";
            case EVENT_CSS_FOCUS:   return "CSS :focus";
            case EVENT_CSS_ACTIVE:  return "CSS :active";
            case EVENT_CSS_VISITED: return "CSS :visited";
            case EVENT_INPUT: return "On Input";
            case EVENT_LOAD: return "On Load";
            case EVENT_SUBMIT: return "On Submit";
            case EVENT_SCROLL: return "On Scroll";
            case EVENT_KEYDOWN: return "On Key Down";
            case EVENT_CHANGE: return "On Change";
            case "immediate": return "Immediate";
            default: return eventType;
        }
    }

    private String buildSelector(LogicBlock block) {
        String target = block.targetWidget;
        String mode = block.targetMode;
        if (TARGET_MODE_ID.equals(mode)) {
            return "#" + target;
        } else if (TARGET_MODE_CLASS.equals(mode)) {
            return "." + target;
        } else if (TARGET_MODE_TAG.equals(mode)) {
            return target;
        }
        return "[data-widget='" + target + "']";
    }

    /**
     * Returns true if the block's event is a CSS pseudo-class rule (not JS).
     */
    private boolean isCssPseudoEvent(String event) {
        return event != null && event.startsWith("css:");
    }

    /**
     * Returns true when this block should be expressed as a static CSS rule
     * rather than runtime JS. CSS-style mutations applied at page-load time
     * (or with no event) are emitted as `selector { property: value; }` rules
     * so the browser styles the element directly.
     */
    private boolean isStaticCssBlock(LogicBlock block) {
        if (block == null || block.action == null || block.params == null) return false;
        if (block.action.startsWith("css")) return true;
        if (ACTION_CHANGE_STYLE.equals(block.action)) {
            String ev = block.event;
            if (ev == null || ev.isEmpty()) return true;
            return "immediate".equals(ev) || EVENT_LOAD.equals(ev) || EVENT_PAGE_LOAD.equals(ev);
        }
        return false;
    }

    /**
     * Generate a single CSS stylesheet for blocks that should be applied as
     * static styles. Rules are grouped by selector and emitted in the form:
     *
     * <pre>
     * .myClass {
     *   width: 48px;
     *   height: 48px;
     *   border-radius: var(--radius-sm);
     * }
     * </pre>
     *
     * Output is suitable for embedding in a {@code <style>} block.
     */
    public String generateBaseCssRules() {
        StringBuilder css = new StringBuilder();
        java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, String>> bySelector =
            new java.util.LinkedHashMap<>();

        // 1. Process modern blocks (from spec templates)
        for (LogicBlock b : blocks) {
            if (b.parentBlockId != null && !b.parentBlockId.isEmpty()) continue;
            if (!isCssEmitting(b)) continue;
            
            // If it's a direct style block (like cssSelector { %m.space }), try to group it
            String rendered = applyChipTemplate(b);
            if (rendered != null && rendered.contains("{")) {
                int openBrace = rendered.indexOf('{');
                int closeBrace = rendered.lastIndexOf('}');
                if (openBrace > 0 && closeBrace > openBrace) {
                    String selector = rendered.substring(0, openBrace).trim();
                    String body = rendered.substring(openBrace + 1, closeBrace).trim();
                    
                    java.util.LinkedHashMap<String, String> rules = bySelector.get(selector);
                    if (rules == null) {
                        rules = new java.util.LinkedHashMap<>();
                        bySelector.put(selector, rules);
                    }
                    
                    // Parse simple rules: "key: val;"
                    String[] ruleLines = body.split(";");
                    for (String line : ruleLines) {
                        String[] pair = line.split(":", 2);
                        if (pair.length == 2) {
                            rules.put(pair[0].trim(), pair[1].trim());
                        }
                    }
                } else {
                    // Fallback for complex templates
                    css.append(rendered).append("\n");
                }
            }
        }

        // 2. Process legacy changeStyle blocks
        for (LogicBlock block : blocks) {
            if (!isStaticChangeStyleBlock(block)) continue;
            if (block.params == null) continue;
            String[] parts = block.params.split(":", 2);
            if (parts.length == 2) {
                String property = camelToKebab(parts[0].trim());
                String value = parts[1].trim();
                String selector = buildSelector(block);
                java.util.LinkedHashMap<String, String> rules = bySelector.get(selector);
                if (rules == null) {
                    rules = new java.util.LinkedHashMap<>();
                    bySelector.put(selector, rules);
                }
                rules.put(property, value);
            }
        }

        // 3. Emit consolidated rules
        for (Map.Entry<String, java.util.LinkedHashMap<String, String>> entry : bySelector.entrySet()) {
            css.append("  ").append(entry.getKey()).append(" {\n");
            for (Map.Entry<String, String> rule : entry.getValue().entrySet()) {
                css.append("    ").append(rule.getKey()).append(": ").append(rule.getValue()).append(";\n");
            }
            css.append("  }\n");
        }
        return css.toString();
    }

    /**
     * Categories whose blocks are written as CSS rules, not JavaScript. Used
     * by the template-driven code generator to decide which top-level blocks
     * contribute to the page stylesheet.
     */
    private boolean isCssEmitting(LogicBlock b) {
        if (b == null) return false;
        if ("css".equals(b.category)) return true;
        if ("animation".equals(b.category)) return true;
        // Group / comment blocks are passthroughs that wrap their children's
        // CSS output – they only contribute when their first child does.
        if ("groupBlock".equals(b.action) || "commentBlock".equals(b.action)) return true;
        return false;
    }

    private boolean isStaticChangeStyleBlock(LogicBlock b) {
        if (b == null || b.action == null) return false;
        if (!ACTION_CHANGE_STYLE.equals(b.action)) return false;
        String ev = b.event;
        return ev == null || ev.isEmpty()
            || "immediate".equals(ev) || EVENT_LOAD.equals(ev) || EVENT_PAGE_LOAD.equals(ev);
    }

    /**
     * Recursively emit one block (and any nested children) as CSS. Container
     * blocks rendered with {@code %m.space} have their child blocks inlined
     * inside the braces. Group blocks add a {@code &lt;name&gt;} comment ribbon
     * around their children so generated CSS preserves the user's grouping.
     */
    private void emitCssBlock(StringBuilder out, LogicBlock b, int depth) {
        if (b == null) return;
        String indent = repeatStr("  ", depth);

        if ("groupBlock".equals(b.action)) {
            String name = paramAt(b, 0);
            if (name == null || name.isEmpty()) name = "group";
            out.append(indent).append("/* <").append(name).append("> */\n");
            for (LogicBlock c : blocks) {
                if (b.id != null && b.id.equals(c.parentBlockId)) {
                    emitCssBlock(out, c, depth);
                }
            }
            out.append(indent).append("/* </").append(name).append("> */\n\n");
            return;
        }
        if ("commentBlock".equals(b.action)) {
            String comment = paramAt(b, 0);
            if (comment != null && !comment.isEmpty()) {
                out.append(indent).append("/* ").append(comment).append(" */\n");
            }
            return;
        }

        String rendered = applyChipTemplate(b);
        if (rendered == null || rendered.isEmpty()) return;

        if (rendered.contains("@@CHILDREN@@")) {
            StringBuilder children = new StringBuilder();
            for (LogicBlock c : blocks) {
                if (b.id != null && b.id.equals(c.parentBlockId)) {
                    emitCssBlock(children, c, depth + 1);
                }
            }
            String childIndent = repeatStr("  ", depth);
            String filled = rendered.replace("@@CHILDREN@@",
                "\n" + children.toString() + childIndent);
            out.append(indent).append(filled).append("\n");
        } else {
            out.append(indent).append(rendered).append("\n");
        }
    }

    /**
     * Substitute {@code %n}, {@code %s}, {@code %b} and {@code %m.<kind>}
     * tokens in a block's {@code spec} template with the chip values held in
     * {@code paramValues}. {@code %m.space} is preserved as the marker
     * {@code @@CHILDREN@@} so the caller knows where to splice nested code.
     */
    String applyChipTemplate(LogicBlock b) {
        if (b == null) return "";
        String tmpl = b.spec;
        if (tmpl == null || tmpl.isEmpty()) return "";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "%(?:m\\.([a-zA-Z_]+)|([nsbd]))");
        java.util.regex.Matcher m = p.matcher(tmpl);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        int idx = 0;
        while (m.find()) {
            sb.append(tmpl, last, m.start());
            String selectorKind = m.group(1);
            if ("space".equals(selectorKind)) {
                sb.append("@@CHILDREN@@");
            } else if (selectorKind != null && "selector".equals(selectorKind)) {
                String value = paramAt(b, idx);
                sb.append(value != null ? value : "");
                idx++;
            } else {
                String value = paramAt(b, idx);
                sb.append(value != null ? value : "");
                idx++;
            }
            last = m.end();
        }
        sb.append(tmpl.substring(last));
        return sb.toString();
    }

    private static String paramAt(LogicBlock b, int idx) {
        if (b == null || b.paramValues == null || idx < 0 || idx >= b.paramValues.size()) return "";
        String v = b.paramValues.get(idx);
        return v != null ? v : "";
    }

    private static String repeatStr(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * Math.max(n, 0));
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    private String camelToKebab(String name) {
        if (name == null || name.isEmpty()) return name;
        // Already kebab-case (e.g. "border-radius") – leave untouched.
        if (name.indexOf('-') >= 0) return name.toLowerCase();
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('-');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Generate CSS pseudo-class rules for blocks that use CSS-only interactions.
     * Rules are grouped by selector and pseudo-class to prevent duplicate rule blocks.
     * Output is suitable for embedding in a {@code <style>} block.
     *
     * Example output:  #btn1:hover { color: red; font-size: 14px; }
     */
    public String generateCssPseudoRules() {
        java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, String>> bySelector =
            new java.util.LinkedHashMap<>();
        StringBuilder css = new StringBuilder();

        for (LogicBlock block : blocks) {
            if (!isCssPseudoEvent(block.event)) continue;
            String pseudoClass = block.event.substring("css:".length()); // "hover", "focus", etc.
            String selector = buildSelector(block) + ":" + pseudoClass;

            if (block.params != null) {
                String[] parts = block.params.split(":", 2);
                if (parts.length == 2) {
                    String prop = camelToKebab(parts[0].trim());
                    String val = parts[1].trim();

                    java.util.LinkedHashMap<String, String> rules = bySelector.get(selector);
                    if (rules == null) {
                        rules = new java.util.LinkedHashMap<>();
                        bySelector.put(selector, rules);
                    }
                    rules.put(prop, val);
                }
            }
        }

        for (Map.Entry<String, java.util.LinkedHashMap<String, String>> entry : bySelector.entrySet()) {
            css.append("  ").append(entry.getKey()).append(" {\n");
            for (Map.Entry<String, String> rule : entry.getValue().entrySet()) {
                css.append("    ").append(rule.getKey()).append(": ").append(rule.getValue()).append(";\n");
            }
            css.append("  }\n");
        }
        return css.toString();
    }


    /**
     * Generate JavaScript with HTML-first approach.
     * Prefers modifying HTML/CSS directly over heavy JS.
     * CSS pseudo-class blocks, ASD source blocks, and empty event-container
     * markers are excluded here. Returns "" when no JS-emitting block is
     * present so the export side can omit the {@code <script>} tag entirely.
     */
    public String generateJavaScript() {
        if (blocks.isEmpty()) return "";

        StringBuilder js = new StringBuilder();
        boolean emittedAny = false;

        for (LogicBlock block : blocks) {
            String eventName = block.event;

            // Skip CSS pseudo-class blocks – they are written as CSS, not JS
            if (isCssPseudoEvent(eventName)) continue;
            // Skip ASD raw-source blocks – they are emitted verbatim elsewhere
            if ("asd".equals(eventName)) continue;
            // Skip empty group placeholders.
            if (block.action == null || block.action.isEmpty()) continue;
            if ("event_container".equals(block.action)) continue;
            // Skip blocks that are emitted as static CSS rules (changeStyle on
            // page-load / immediate). Those are output by generateBaseCssRules().
            if (isStaticCssBlock(block)) continue;
            // Skip new-schema CSS/animation/meta/asd blocks – the template
            // codegen handled them already.
            if ("css".equals(block.category) || "animation".equals(block.category)
                || "meta".equals(block.category) || "asd".equals(block.category)) continue;
            if ("groupBlock".equals(block.action) || "commentBlock".equals(block.action)) continue;
            // Skip nested children – they're emitted by their parent container.
            if (block.parentBlockId != null && !block.parentBlockId.isEmpty()) continue;

            emittedAny = true;
            js.append("  // ").append(eventName != null ? eventName : "immediate")
              .append(" -> ").append(block.action).append("\n");

            // Immediate blocks (logic/variable) - execute inline
            if ("immediate".equals(eventName)) {
                js.append("  ").append(generateActionJs(block, "document.body"));
                js.append("\n");
            }
            // Page-based events
            else if (EVENT_PAGE_LOAD.equals(eventName) || EVENT_LOAD.equals(eventName)) {
                js.append("  // Page Load - execute immediately in DOMContentLoaded\n");
                js.append("  (function() {\n");
                String el = resolveElement(block);
                js.append("    ").append(generateActionJs(block, el));
                js.append("  })();\n\n");
            }
            else if (EVENT_VISIBLE.equals(eventName)) {
                js.append("  document.addEventListener('visibilitychange', function() {\n");
                js.append("    if (!document.hidden) {\n");
                String el = resolveElement(block);
                js.append("      ").append(generateActionJs(block, el));
                js.append("    }\n");
                js.append("  });\n\n");
            }
            else if (EVENT_HIDDEN.equals(eventName)) {
                js.append("  document.addEventListener('visibilitychange', function() {\n");
                js.append("    if (document.hidden) {\n");
                String el = resolveElement(block);
                js.append("      ").append(generateActionJs(block, el));
                js.append("    }\n");
                js.append("  });\n\n");
            }
            else if (EVENT_DESTROY.equals(eventName)) {
                js.append("  window.addEventListener('beforeunload', function() {\n");
                String el = resolveElement(block);
                js.append("    ").append(generateActionJs(block, el));
                js.append("  });\n\n");
            }
            else if (EVENT_PAGE_SCROLL.equals(eventName)) {
                js.append("  window.addEventListener('scroll', function() {\n");
                String el = resolveElement(block);
                js.append("    ").append(generateActionJs(block, el));
                js.append("  });\n\n");
            }
            else if (EVENT_PAGE_INPUT.equals(eventName)) {
                js.append("  document.querySelectorAll('input, textarea, select').forEach(function(el) {\n");
                js.append("    el.addEventListener('input', function(event) {\n");
                js.append("      ").append(generateActionJs(block, "el"));
                js.append("    });\n");
                js.append("  });\n\n");
            }
            // Element-scoped events
            else {
                String selector = buildSelector(block);
                String jsEvent = eventName;
                if ("hover".equals(eventName)) jsEvent = "mouseenter";

                js.append("  document.querySelectorAll('").append(selector).append("').forEach(function(el) {\n");
                js.append("    el.addEventListener('").append(jsEvent).append("', function(event) {\n");
                js.append("      ").append(generateActionJs(block, "el"));
                js.append("    });\n");
                js.append("  });\n\n");
            }
        }

        if (!emittedAny) return "";
        // Wrap output with named sections so the export reads cleanly:
        //   ────── events / page-load / element handlers / api ──────
        return "// =====================================================\n"
            + "// Logic blocks (events / page-load / element handlers)\n"
            + "// =====================================================\n"
            + "document.addEventListener('DOMContentLoaded', function() {\n"
            + js.toString()
            + "});\n";
    }

    /**
     * Bundle all ASD source blocks for a given target slot. {@code spec} is
     * one of {@code "html"}, {@code "css"}, {@code "js"}, {@code "head"}, or
     * {@code "meta"} — matching the asd block ids. Output is stripped of
     * blank entries and joined with a blank line so the export reads cleanly.
     */
    public String generateAsdSource(String spec) {
        if (spec == null) return "";
        String wantId = "asd" + Character.toUpperCase(spec.charAt(0))
            + spec.substring(1).toLowerCase();
        StringBuilder out = new StringBuilder();
        for (LogicBlock block : blocks) {
            if (!wantId.equals(block.action)) continue;
            // Accept both legacy ({@code event=="asd"}) and modern ({@code
            // category=="asd"}, {@code event=="immediate"}) ASD blocks.
            boolean isAsd = "asd".equals(block.event) || "asd".equals(block.category);
            if (!isAsd) continue;
            String body = "";
            // Modern blocks store the source in {@code paramValues[0]}; legacy
            // ones still keep it in {@code params}.
            if (block.paramValues != null && !block.paramValues.isEmpty()) {
                String v = block.paramValues.get(0);
                if (v != null) body = v;
            }
            if (body.isEmpty() && block.params != null) body = block.params;
            body = body.trim();
            if (body.isEmpty()) continue;
            if (out.length() > 0) out.append("\n\n");
            out.append(body);
        }
        return out.toString();
    }

    /**
     * Resolve element variable for page-level events that may target specific elements.
     */
    private String resolveElement(LogicBlock block) {
        if (block.targetWidget != null && !block.targetWidget.isEmpty()) {
            String selector = buildSelector(block);
            return "document.querySelector('" + selector + "')";
        }
        return "document.body";
    }

    private String generateActionJs(LogicBlock block, String elVar) {
        switch (block.action) {
            case ACTION_CHANGE_STYLE: {
                String[] parts = block.params.split(":", 2);
                if (parts.length == 2) {
                    return elVar + ".style." + parts[0].trim() + " = '" + parts[1].trim() + "';\n";
                }
                return "// Invalid style params\n";
            }
            case ACTION_ANIMATE:
                return elVar + ".style.animation = '" + block.params + " 0.5s ease';\n";

            case ACTION_NAVIGATE:
                return "window.location.href = '" + escapeJs(block.params) + "';\n";

            case ACTION_GO_TO_PAGE:
                return "window.location.href = '" + escapeJs(block.params) + ".html';\n";

            case ACTION_OPEN_PAGE:
                return "window.open('" + escapeJs(block.params) + ".html', '_blank');\n";

            case ACTION_SHOW_HIDE:
                if ("toggle".equals(block.params)) {
                    return elVar + ".style.display = " + elVar + ".style.display === 'none' ? '' : 'none';\n";
                } else if ("hide".equals(block.params)) {
                    return elVar + ".style.display = 'none';\n";
                } else {
                    return elVar + ".style.display = '';\n";
                }

            case ACTION_SET_TEXT:
                return elVar + ".textContent = '" + escapeJs(block.params) + "';\n";

            case ACTION_ADD_CLASS:
                return elVar + ".classList.add('" + escapeJs(block.params) + "');\n";

            case ACTION_REMOVE_CLASS:
                return elVar + ".classList.remove('" + escapeJs(block.params) + "');\n";

            case ACTION_TOGGLE_CLASS:
                return elVar + ".classList.toggle('" + escapeJs(block.params) + "');\n";

            case ACTION_ALERT:
                return "alert('" + escapeJs(block.params) + "');\n";

            case ACTION_CONSOLE_LOG:
                return "console.log('" + escapeJs(block.params) + "');\n";

            case ACTION_SET_ATTRIBUTE: {
                String[] parts = block.params.split(":", 2);
                if (parts.length == 2) {
                    return elVar + ".setAttribute('" + escapeJs(parts[0].trim()) + "', '" + escapeJs(parts[1].trim()) + "');\n";
                }
                return "// Invalid attribute params\n";
            }
            case ACTION_REMOVE_ATTRIBUTE:
                return elVar + ".removeAttribute('" + escapeJs(block.params) + "');\n";

            case ACTION_SET_VALUE:
                return elVar + ".value = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_HTML:
                return elVar + ".innerHTML = '" + escapeJs(block.params) + "';\n";

            case ACTION_APPEND_CHILD:
                return elVar + ".insertAdjacentHTML('beforeend', '" + escapeJs(block.params) + "');\n";

            case ACTION_PREPEND_CHILD:
                return elVar + ".insertAdjacentHTML('afterbegin', '" + escapeJs(block.params) + "');\n";

            case ACTION_CREATE_ELEMENT: {
                String[] parts = block.params.split("\\|", 2);
                String tag = parts.length > 0 ? parts[0].trim() : "div";
                String content = parts.length > 1 ? parts[1].trim() : "";
                return "var newEl = document.createElement('" + escapeJs(tag) + "'); "
                    + "newEl.textContent = '" + escapeJs(content) + "'; "
                    + elVar + ".appendChild(newEl);\n";
            }

            case ACTION_REMOVE_ELEMENT:
                if ("self".equals(block.params)) {
                    return elVar + ".remove();\n";
                }
                return "document.querySelector('" + escapeJs(block.params) + "')?.remove();\n";

            case ACTION_CUSTOM_JS:
                return block.params + "\n";

            case ACTION_FETCH_API: {
                String[] parts = block.params.split("\\|", 3);
                String url = parts.length > 0 ? parts[0].trim() : "";
                String method = parts.length > 1 ? parts[1].trim() : "GET";
                String body = parts.length > 2 ? parts[2].trim() : "";
                StringBuilder fetchJs = new StringBuilder();
                fetchJs.append("fetch('").append(escapeJs(url)).append("', {method:'").append(method).append("'");
                if (!body.isEmpty()) {
                    fetchJs.append(",headers:{'Content-Type':'application/json'},body:'").append(escapeJs(body)).append("'");
                }
                fetchJs.append("}).then(r=>r.json()).then(data=>{console.log(data)}).catch(e=>console.error(e));\n");
                return fetchJs.toString();
            }

            case ACTION_LOCAL_STORAGE: {
                String[] parts = block.params.split(":", 3);
                if (parts.length >= 2 && "set".equals(parts[0])) {
                    String key = parts[1];
                    String val = parts.length > 2 ? parts[2] : "";
                    return "localStorage.setItem('" + escapeJs(key) + "','" + escapeJs(val) + "');\n";
                } else if (parts.length >= 2 && "get".equals(parts[0])) {
                    return "var _val = localStorage.getItem('" + escapeJs(parts[1]) + "'); console.log(_val);\n";
                }
                return "// Invalid localStorage params\n";
            }

            case ACTION_SCROLL_TO:
                if ("top".equals(block.params)) {
                    return "window.scrollTo({top:0,behavior:'smooth'});\n";
                } else if ("bottom".equals(block.params)) {
                    return "window.scrollTo({top:document.body.scrollHeight,behavior:'smooth'});\n";
                }
                return "document.querySelector('" + escapeJs(block.params) + "')?.scrollIntoView({behavior:'smooth'});\n";

            case ACTION_COPY_CLIPBOARD:
                if ("self".equals(block.params)) {
                    return "navigator.clipboard.writeText(" + elVar + ".textContent);\n";
                }
                return "navigator.clipboard.writeText('" + escapeJs(block.params) + "');\n";

            case ACTION_DELAY: {
                String[] parts = block.params.split("\\|", 2);
                String ms = parts.length > 0 ? parts[0].trim() : "1000";
                String delayedCode = parts.length > 1 ? parts[1].trim() : "// delayed action";
                return "setTimeout(function(){" + delayedCode + "}," + ms + ");\n";
            }

            case ACTION_FOCUS_INPUT:
                return elVar + ".focus();\n";

            case ACTION_BLUR_INPUT:
                return elVar + ".blur();\n";

            case ACTION_SET_HREF: {
                // HTML-first: directly modify href attribute
                return elVar + ".setAttribute('href', '" + escapeJs(block.params) + "');\n";
            }

            case ACTION_SET_TRANSFORM:
                return elVar + ".style.transform = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_TRANSITION:
                return elVar + ".style.transition = '" + escapeJs(block.params) + "';\n";

            case ACTION_IF_BLOCK: {
                String[] parts = block.params.split("\\|", 5);
                if (parts.length >= 4) {
                    String left = parts[0].trim();
                    String op = parts[1].trim();
                    String right = parts[2].trim();
                    String thenCode = parts[3].trim();
                    return "if (" + left + " " + op + " " + right + ") { " + thenCode + " }\n";
                }
                return "// Invalid if block params\n";
            }

            case ACTION_IF_ELSE_BLOCK: {
                String[] parts = block.params.split("\\|", 5);
                if (parts.length >= 5) {
                    String left = parts[0].trim();
                    String op = parts[1].trim();
                    String right = parts[2].trim();
                    String thenCode = parts[3].trim();
                    String elseCode = parts[4].trim();
                    return "if (" + left + " " + op + " " + right + ") { " + thenCode + " } else { " + elseCode + " }\n";
                } else if (parts.length >= 4) {
                    String left = parts[0].trim();
                    String op = parts[1].trim();
                    String right = parts[2].trim();
                    String thenCode = parts[3].trim();
                    return "if (" + left + " " + op + " " + right + ") { " + thenCode + " }\n";
                }
                return "// Invalid if-else block params\n";
            }

            case ACTION_LOOP: {
                String[] parts = block.params.split("\\|", 2);
                String count = parts.length > 0 ? parts[0].trim() : "5";
                String loopCode = parts.length > 1 ? parts[1].trim() : "// loop body";
                return "for (var i = 0; i < " + count + "; i++) { " + loopCode + " }\n";
            }

            case ACTION_CREATE_VAR: {
                String[] parts = block.params.split("\\|", 3);
                String name = parts.length > 0 ? parts[0].trim() : "myVar";
                String type = parts.length > 1 ? parts[1].trim() : "any";
                String initVal = parts.length > 2 ? parts[2].trim() : "";
                if (initVal.isEmpty()) {
                    if ("number".equals(type)) initVal = "0";
                    else if ("boolean".equals(type)) initVal = "false";
                    else if ("string".equals(type)) initVal = "''";
                    else if ("color".equals(type)) initVal = "'#000000'";
                    else initVal = "null";
                } else {
                    if (("string".equals(type) || "color".equals(type)) && !initVal.startsWith("'") && !initVal.startsWith("\"")) {
                        initVal = "'" + escapeJs(initVal) + "'";
                    }
                }
                return "var " + name + " = " + initVal + ";\n";
            }

            case ACTION_SET_VAR: {
                String[] parts = block.params.split("\\|", 2);
                String name = parts.length > 0 ? parts[0].trim() : "myVar";
                String val = parts.length > 1 ? parts[1].trim() : "null";
                return name + " = " + val + ";\n";
            }

            case ACTION_GET_VAR: {
                String[] parts = block.params.split("\\|", 2);
                String name = parts.length > 0 ? parts[0].trim() : "myVar";
                String target = parts.length > 1 ? parts[1].trim() : "";
                if (!target.isEmpty()) {
                    return "document.querySelector('" + escapeJs(target) + "').textContent = " + name + ";\n";
                }
                return "console.log(" + name + ");\n";
            }

            default:
                return "// Unknown action\n";
        }
    }

    private String escapeJs(String str) {
        return str.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    /**
     * Sketchware-style template engine. Replaces positional tokens
     * ({@code %1$s}, {@code %2$s}, ...) inside a block template with the
     * supplied values and returns the resulting source code string.
     *
     * <p>Used by {@link CustomBlockManager} to convert visual custom blocks
     * into static HTML or CSS at code-generation time. The same engine
     * powers selector tokens (%m.id, %m.class, %m.tag, %m.file, %m.section)
     * because the values supplied are resolved selectors.
     */
    public String applyTemplate(String template, List<String> values) {
        if (template == null) return "";
        if (values == null) return template;
        String result = template;
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i) != null ? values.get(i) : "";
            // Positional string tokens: %1$s, %2$s, ...
            result = result.replace("%" + (i + 1) + "$s", value);
            // Positional decimal tokens: %1$d, %2$d, ...
            result = result.replace("%" + (i + 1) + "$d", value);
        }
        return result;
    }

    public String toJson() {
        return new Gson().toJson(blocks);
    }

    public void fromJson(String json) {
        if (json == null || json.trim().isEmpty()) return;

        // Reject obviously-incompatible payloads early. The custom-block
        // library uses keys like "template" / "display" / "category" – if we
        // see those it means somebody routed the wrong JSON into here, and
        // parsing it as LogicBlock[] would silently produce all-null entries
        // that then crash the activity when rendered.
        String head = json.trim();
        if (head.startsWith("{")) return;
        if (head.contains("\"template\"") && head.contains("\"display\"")) return;

        try {
            List<LogicBlock> loaded = new Gson().fromJson(json, new TypeToken<List<LogicBlock>>(){}.getType());
            if (loaded == null) return;
            blocks.clear();
            for (LogicBlock b : loaded) {
                if (b == null) continue;
                // Require at minimum an action: blocks without one have no
                // meaning and only serve as crash bait for the renderer.
                if (b.action == null || b.action.isEmpty()) continue;
                // Backfill non-null defaults for everything else so render
                // and codegen paths can rely on them.
                if (b.event == null) b.event = "immediate";
                if (b.params == null) b.params = "";
                if (b.targetMode == null) b.targetMode = TARGET_MODE_ID;
                if (b.targetWidget == null) b.targetWidget = "";
                blocks.add(b);
            }
        } catch (Exception e) {
            // Leave existing blocks intact rather than wiping them on a parse
            // error – avoids losing the user's work to a single bad file.
        }
    }

    public static class LogicBlock {
        public String category;
        public String targetWidget;
        public String targetMode = TARGET_MODE_ID; // "id", "class", or "tag"
        public String event;
        public String action;
        public String params;
        public String shape;
        public float x;
        public float y;
        public String spec; // Advanced rendering template (e.g., "set %m.view to %s")
        public String nextBlockId; // ID of the block attached below
        public String parentBlockId; // ID of the block it's attached to
        public String subStackId;  // ID of the first block inside a C-shape
        public String id; // Unique ID for referencing
        public List<String> paramValues = new ArrayList<>(); // Values for tokens in spec
        public boolean collapsed;
    }

    public interface OnBlockAddedListener {
        void onBlockAdded(LogicBlock block);
    }

    private String getPseudoSuffixFromAction(String action) {
        switch (action) {
            case "cssHover": return ":hover";
            case "cssFocus": return ":focus";
            case "cssActive": return ":active";
            case "cssVisited": return ":visited";
            case "cssBefore": return "::before";
            case "cssAfter": return "::after";
            case "cssFirstChild": return ":first-child";
            case "cssLastChild": return ":last-child";
            case "cssNthChild": return ":nth-child(%n)";
            default: return "";
        }
    }
}
