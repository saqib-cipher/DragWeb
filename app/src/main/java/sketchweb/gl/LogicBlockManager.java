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

    public void showAddBlockDialog(String targetWidgetTag, OnBlockAddedListener listener) {
        showAddBlockDialog(targetWidgetTag, TARGET_MODE_ID, listener);
    }

    public void showAddBlockDialog(String targetWidgetTag, String targetMode, OnBlockAddedListener listener) {
        // Note: onClick/onHover removed – interactions are handled via CSS pseudo-class blocks
        String[] events = {
            "On Input", "On Submit", "On Scroll", "On Key Down", "On Change",
            "CSS :hover (style)", "CSS :focus (style)", "CSS :active (style)", "CSS :visited (style)"
        };
        String[] eventKeys = {
            EVENT_INPUT, EVENT_SUBMIT, EVENT_SCROLL, EVENT_KEYDOWN, EVENT_CHANGE,
            EVENT_CSS_HOVER, EVENT_CSS_FOCUS, EVENT_CSS_ACTIVE, EVENT_CSS_VISITED
        };

        String modeLabel = TARGET_MODE_CLASS.equals(targetMode) ? "." :
                           TARGET_MODE_TAG.equals(targetMode) ? "" : "#";

        new MaterialAlertDialogBuilder(context)
            .setTitle("Select Event for " + modeLabel + targetWidgetTag)
            .setItems(events, (dialog, which) -> {
                String selectedEvent = eventKeys[which];
                showActionDialog(targetWidgetTag, targetMode, selectedEvent, listener);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showActionDialog(String targetWidgetTag, String targetMode, String event, OnBlockAddedListener listener) {
        String[] actions = {
            "Change Style", "Animate", "Navigate To URL",
            "Go To Page", "Open Page (New Tab)",
            "Show/Hide Element", "Set Text", "Set HTML", "Add CSS Class",
            "Remove CSS Class", "Toggle CSS Class", "Show Alert",
            "Console Log", "Set Attribute", "Remove Attribute",
            "Set Input Value", "Append Child HTML", "Prepend Child HTML",
            "Create Element", "Remove Element",
            "Custom JavaScript", "Fetch API", "LocalStorage Set/Get",
            "Scroll To", "Copy to Clipboard", "Delay Then Run",
            "Set Href"
        };
        String[] actionKeys = {
            ACTION_CHANGE_STYLE, ACTION_ANIMATE, ACTION_NAVIGATE,
            ACTION_GO_TO_PAGE, ACTION_OPEN_PAGE,
            ACTION_SHOW_HIDE, ACTION_SET_TEXT, ACTION_SET_HTML, ACTION_ADD_CLASS,
            ACTION_REMOVE_CLASS, ACTION_TOGGLE_CLASS, ACTION_ALERT,
            ACTION_CONSOLE_LOG, ACTION_SET_ATTRIBUTE, ACTION_REMOVE_ATTRIBUTE,
            ACTION_SET_VALUE, ACTION_APPEND_CHILD, ACTION_PREPEND_CHILD,
            ACTION_CREATE_ELEMENT, ACTION_REMOVE_ELEMENT,
            ACTION_CUSTOM_JS, ACTION_FETCH_API, ACTION_LOCAL_STORAGE,
            ACTION_SCROLL_TO, ACTION_COPY_CLIPBOARD, ACTION_DELAY,
            ACTION_SET_HREF
        };

        new MaterialAlertDialogBuilder(context)
            .setTitle("Select Action")
            .setItems(actions, (dialog, which) -> {
                String selectedAction = actionKeys[which];
                showActionParamsDialog(targetWidgetTag, targetMode, event, selectedAction, listener);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showActionParamsDialog(String targetWidgetTag, String targetMode, String event, String action, OnBlockAddedListener listener) {
        android.widget.EditText input = new android.widget.EditText(context);
        input.setPadding(48, 32, 48, 32);

        String hint = getHintForAction(action);
        input.setHint(hint);

        new MaterialAlertDialogBuilder(context)
            .setTitle("Action Parameters")
            .setView(input)
            .setPositiveButton("Add", (dialog, which) -> {
                String value = input.getText().toString().trim();
                LogicBlock block = new LogicBlock();
                block.targetWidget = targetWidgetTag;
                block.targetMode = targetMode;
                block.event = event;
                block.action = action;
                block.params = value;
                blocks.add(block);
                if (listener != null) {
                    listener.onBlockAdded(block);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private String getHintForAction(String action) {
        switch (action) {
            case ACTION_CHANGE_STYLE: return "property:value (e.g. color:red)";
            case ACTION_ANIMATE: return "animation name (e.g. fadeIn, slideUp, pulse)";
            case ACTION_NAVIGATE: return "URL (e.g. https://example.com)";
            case ACTION_GO_TO_PAGE: return "Page name (e.g. about, contact)";
            case ACTION_OPEN_PAGE: return "Page name to open in new tab";
            case ACTION_SHOW_HIDE: return "toggle, show, or hide";
            case ACTION_SET_TEXT: return "New text content";
            case ACTION_ADD_CLASS: return "CSS class name to add";
            case ACTION_REMOVE_CLASS: return "CSS class name to remove";
            case ACTION_TOGGLE_CLASS: return "CSS class name to toggle";
            case ACTION_ALERT: return "Alert message";
            case ACTION_CONSOLE_LOG: return "Message to log";
            case ACTION_SET_ATTRIBUTE: return "attr:value (e.g. disabled:true)";
            case ACTION_REMOVE_ATTRIBUTE: return "Attribute name (e.g. disabled)";
            case ACTION_SET_VALUE: return "New input value";
            case ACTION_SET_HTML: return "HTML content (e.g. <p>Hello</p>)";
            case ACTION_APPEND_CHILD: return "HTML to append (e.g. <p>Hello</p>)";
            case ACTION_PREPEND_CHILD: return "HTML to prepend (e.g. <p>First</p>)";
            case ACTION_CREATE_ELEMENT: return "tag|content (e.g. p|Hello World)";
            case ACTION_REMOVE_ELEMENT: return "Selector to remove (or 'self')";
            case ACTION_CUSTOM_JS: return "JavaScript code";
            case ACTION_FETCH_API: return "URL|method|body (e.g. /api/data|GET|)";
            case ACTION_LOCAL_STORAGE: return "set:key:value or get:key";
            case ACTION_SCROLL_TO: return "top, bottom, or selector";
            case ACTION_COPY_CLIPBOARD: return "Text to copy (or 'self' for element text)";
            case ACTION_DELAY: return "ms|action (e.g. 1000|alert:Done!)";
            case ACTION_SET_HREF: return "URL or #section-id";
            case ACTION_SET_TRANSFORM: return "CSS transform (e.g. rotate(45deg))";
            case ACTION_SET_TRANSITION: return "CSS transition (e.g. all 0.3s ease)";
            default: return "Parameters";
        }
    }

    public void showBlocksDialog() {
        if (blocks.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                .setTitle("Logic Blocks")
                .setMessage("No logic blocks added yet.\nSelect an event from the Event tab to add blocks.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 16);
        scrollView.addView(container);

        // Group blocks by event
        Map<String, List<Integer>> groupedBlocks = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            LogicBlock block = blocks.get(i);
            String key = block.event != null ? block.event : "immediate";
            if (!groupedBlocks.containsKey(key)) {
                groupedBlocks.put(key, new ArrayList<>());
            }
            groupedBlocks.get(key).add(i);
        }

        for (Map.Entry<String, List<Integer>> entry : groupedBlocks.entrySet()) {
            TextView eventHeader = new TextView(context);
            eventHeader.setText(getEventDisplayName(entry.getKey()));
            eventHeader.setTextColor(Color.parseColor("#FF9800"));
            eventHeader.setTextSize(13);
            eventHeader.setTypeface(null, Typeface.BOLD);
            eventHeader.setPadding(0, 12, 0, 6);
            container.addView(eventHeader);

            for (int index : entry.getValue()) {
                LogicBlock block = blocks.get(index);
                final int blockIndex = index;

                LinearLayout blockView = new LinearLayout(context);
                blockView.setOrientation(LinearLayout.VERTICAL);
                blockView.setPadding(16, 10, 16, 10);

                GradientDrawable blockBg = new GradientDrawable();
                blockBg.setCornerRadius(12);
                blockBg.setColor(Color.parseColor("#1E2030"));
                blockBg.setStroke(1, Color.parseColor("#333355"));
                blockView.setBackground(blockBg);

                LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blockParams.setMargins(0, 3, 0, 3);
                blockView.setLayoutParams(blockParams);

                // Target info
                if (block.targetWidget != null && !block.targetWidget.isEmpty()) {
                    LinearLayout targetRow = new LinearLayout(context);
                    targetRow.setOrientation(LinearLayout.HORIZONTAL);

                    TextView targetLabel = new TextView(context);
                    targetLabel.setText("TARGET ");
                    targetLabel.setTextColor(Color.parseColor("#9C27B0"));
                    targetLabel.setTextSize(11);
                    targetLabel.setTypeface(null, Typeface.BOLD);
                    targetRow.addView(targetLabel);

                    String modePrefix = "id".equals(block.targetMode) ? "#" :
                        "class".equals(block.targetMode) ? "." : "";
                    TextView targetValue = new TextView(context);
                    targetValue.setText(modePrefix + block.targetWidget);
                    targetValue.setTextColor(Color.parseColor("#CE93D8"));
                    targetValue.setTextSize(11);
                    targetRow.addView(targetValue);
                    blockView.addView(targetRow);
                }

                // Action label
                LinearLayout actionRow = new LinearLayout(context);
                actionRow.setOrientation(LinearLayout.HORIZONTAL);

                TextView doLabel = new TextView(context);
                doLabel.setText("DO ");
                doLabel.setTextColor(Color.parseColor("#4CAF50"));
                doLabel.setTextSize(11);
                doLabel.setTypeface(null, Typeface.BOLD);
                actionRow.addView(doLabel);

                TextView actionLabel = new TextView(context);
                actionLabel.setText(block.action + "(" + block.params + ")");
                actionLabel.setTextColor(Color.parseColor("#81C784"));
                actionLabel.setTextSize(11);
                actionRow.addView(actionLabel);
                blockView.addView(actionRow);

                blockView.setOnLongClickListener(v -> {
                    new MaterialAlertDialogBuilder(context)
                        .setTitle("Delete Block?")
                        .setMessage("Remove this logic block?")
                        .setPositiveButton("Delete", (d, w) -> {
                            removeBlock(blockIndex);
                            showBlocksDialog();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                    return true;
                });

                container.addView(blockView);
            }
        }

        new MaterialAlertDialogBuilder(context)
            .setTitle("Logic Blocks (" + blocks.size() + ")")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show();
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
        // Group "property: value" lines by selector while preserving insertion order.
        java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, String>> bySelector =
            new java.util.LinkedHashMap<>();
        StringBuilder css = new StringBuilder();

        for (LogicBlock block : blocks) {
            if (!isStaticCssBlock(block)) continue;
            
            if (block.action != null && block.action.startsWith("css")) {
                String selector = buildSelector(block);
                String content = "";
                String fullSel = selector;
                
                if ("cssSelector".equals(block.action)) {
                    String[] innerParts = block.params.split("\\|", 3);
                    if (innerParts.length >= 3) {
                        String type = innerParts[0];
                        String sel = innerParts[1];
                        content = innerParts[2];
                        fullSel = "id".equals(type) ? "#" + sel : ("class".equals(type) ? "." + sel : sel);
                    }
                } else if (block.action.startsWith("css")) {
                    // pseudo-classes/elements: action name like 'cssHover' -> ':hover'
                    String pseudo = getPseudoSuffixFromAction(block.action);
                    if (!pseudo.isEmpty()) {
                        if (pseudo.contains("%n")) {
                            String[] innerParts = block.params.split("\\|", 2);
                            if (innerParts.length >= 2) {
                                fullSel = selector + pseudo.replace("%n", innerParts[0]);
                                content = innerParts[1];
                            }
                        } else {
                            fullSel = selector + pseudo;
                            content = block.params; // For these, params is just the inner code
                        }
                    }
                }
                
                if (!content.isEmpty()) {
                    css.append("  ").append(fullSel).append(" {\n    ")
                       .append(content.trim().replace("\n", "\n    "))
                       .append("\n  }\n");
                }
                continue;
            }

            String[] parts = block.params.split(":", 2);
            if (parts.length != 2) continue;
            String property = camelToKebab(parts[0].trim());
            String value = parts[1].trim();
            if (property.isEmpty() || value.isEmpty()) continue;

            String selector = buildSelector(block);
            java.util.LinkedHashMap<String, String> rules = bySelector.get(selector);
            if (rules == null) {
                rules = new java.util.LinkedHashMap<>();
                bySelector.put(selector, rules);
            }
            rules.put(property, value);
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
        return "// Logic blocks generated by DragWeb\n"
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
            if (!"asd".equals(block.event)) continue;
            if (!wantId.equals(block.action)) continue;
            String body = block.params != null ? block.params.trim() : "";
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
        public String targetWidget;
        public String targetMode = TARGET_MODE_ID; // "id", "class", or "tag"
        public String event;
        public String action;
        public String params;
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
