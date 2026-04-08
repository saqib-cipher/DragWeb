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

    public static final String EVENT_CLICK = "click";
    public static final String EVENT_HOVER = "hover";
    public static final String EVENT_INPUT = "input";
    public static final String EVENT_LOAD = "load";
    public static final String EVENT_SUBMIT = "submit";
    public static final String EVENT_SCROLL = "scroll";
    public static final String EVENT_KEYDOWN = "keydown";
    public static final String EVENT_CHANGE = "change";

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
    public static final String ACTION_REMOVE_ELEMENT = "removeElement";
    public static final String ACTION_CUSTOM_JS = "customJs";
    public static final String ACTION_FETCH_API = "fetchApi";
    public static final String ACTION_LOCAL_STORAGE = "localStorage";
    public static final String ACTION_SCROLL_TO = "scrollTo";
    public static final String ACTION_COPY_CLIPBOARD = "copyClipboard";
    public static final String ACTION_DELAY = "delay";
    public static final String ACTION_GO_TO_PAGE = "goToPage";
    public static final String ACTION_OPEN_PAGE = "openPage";

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

    public void showAddBlockDialog(String targetWidgetTag, OnBlockAddedListener listener) {
        showAddBlockDialog(targetWidgetTag, TARGET_MODE_ID, listener);
    }

    public void showAddBlockDialog(String targetWidgetTag, String targetMode, OnBlockAddedListener listener) {
        String[] events = {
            "On Click", "On Hover", "On Input", "On Page Load",
            "On Submit", "On Scroll", "On Key Down", "On Change"
        };
        String[] eventKeys = {
            EVENT_CLICK, EVENT_HOVER, EVENT_INPUT, EVENT_LOAD,
            EVENT_SUBMIT, EVENT_SCROLL, EVENT_KEYDOWN, EVENT_CHANGE
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
            "Show/Hide Element", "Set Text", "Add CSS Class",
            "Remove CSS Class", "Toggle CSS Class", "Show Alert",
            "Console Log", "Set Attribute", "Remove Attribute",
            "Set Input Value", "Append Child HTML", "Remove Element",
            "Custom JavaScript", "Fetch API", "LocalStorage Set/Get",
            "Scroll To", "Copy to Clipboard", "Delay Then Run"
        };
        String[] actionKeys = {
            ACTION_CHANGE_STYLE, ACTION_ANIMATE, ACTION_NAVIGATE,
            ACTION_GO_TO_PAGE, ACTION_OPEN_PAGE,
            ACTION_SHOW_HIDE, ACTION_SET_TEXT, ACTION_ADD_CLASS,
            ACTION_REMOVE_CLASS, ACTION_TOGGLE_CLASS, ACTION_ALERT,
            ACTION_CONSOLE_LOG, ACTION_SET_ATTRIBUTE, ACTION_REMOVE_ATTRIBUTE,
            ACTION_SET_VALUE, ACTION_APPEND_CHILD, ACTION_REMOVE_ELEMENT,
            ACTION_CUSTOM_JS, ACTION_FETCH_API, ACTION_LOCAL_STORAGE,
            ACTION_SCROLL_TO, ACTION_COPY_CLIPBOARD, ACTION_DELAY
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
            case ACTION_APPEND_CHILD: return "HTML to append (e.g. <p>Hello</p>)";
            case ACTION_REMOVE_ELEMENT: return "Selector to remove (or 'self')";
            case ACTION_CUSTOM_JS: return "JavaScript code";
            case ACTION_FETCH_API: return "URL|method|body (e.g. /api/data|GET|)";
            case ACTION_LOCAL_STORAGE: return "set:key:value or get:key";
            case ACTION_SCROLL_TO: return "top, bottom, or selector";
            case ACTION_COPY_CLIPBOARD: return "Text to copy (or 'self' for element text)";
            case ACTION_DELAY: return "ms|action (e.g. 1000|alert:Done!)";
            default: return "Parameters";
        }
    }

    public void showBlocksDialog() {
        if (blocks.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                .setTitle("Logic Blocks")
                .setMessage("No logic blocks added yet.\nSelect a widget and add events from the Event tab.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 16);
        scrollView.addView(container);

        // Group blocks by widget
        Map<String, List<Integer>> groupedBlocks = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            LogicBlock block = blocks.get(i);
            String key = block.targetWidget;
            if (!groupedBlocks.containsKey(key)) {
                groupedBlocks.put(key, new ArrayList<>());
            }
            groupedBlocks.get(key).add(i);
        }

        for (Map.Entry<String, List<Integer>> entry : groupedBlocks.entrySet()) {
            TextView widgetHeader = new TextView(context);
            // Show target with mode prefix
            String headerTarget = entry.getKey();
            if (!blocks.isEmpty()) {
                for (int idx : entry.getValue()) {
                    LogicBlock b = blocks.get(idx);
                    if (TARGET_MODE_ID.equals(b.targetMode)) headerTarget = "#" + b.targetWidget;
                    else if (TARGET_MODE_CLASS.equals(b.targetMode)) headerTarget = "." + b.targetWidget;
                    else if (TARGET_MODE_TAG.equals(b.targetMode)) headerTarget = "<" + b.targetWidget + ">";
                    break;
                }
            }
            widgetHeader.setText(headerTarget);
            widgetHeader.setTextColor(Color.parseColor("#64B5F6"));
            widgetHeader.setTextSize(13);
            widgetHeader.setTypeface(null, Typeface.BOLD);
            widgetHeader.setPadding(0, 12, 0, 6);
            container.addView(widgetHeader);

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

                // Event label
                LinearLayout eventRow = new LinearLayout(context);
                eventRow.setOrientation(LinearLayout.HORIZONTAL);

                TextView whenLabel = new TextView(context);
                whenLabel.setText("WHEN ");
                whenLabel.setTextColor(Color.parseColor("#FF9800"));
                whenLabel.setTextSize(11);
                whenLabel.setTypeface(null, Typeface.BOLD);
                eventRow.addView(whenLabel);

                TextView eventLabel = new TextView(context);
                eventLabel.setText(block.event.toUpperCase());
                eventLabel.setTextColor(Color.parseColor("#FFB74D"));
                eventLabel.setTextSize(11);
                eventRow.addView(eventLabel);
                blockView.addView(eventRow);

                // Action label
                LinearLayout actionRow = new LinearLayout(context);
                actionRow.setOrientation(LinearLayout.HORIZONTAL);

                TextView doLabel = new TextView(context);
                doLabel.setText("  DO  ");
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
        // Fallback: data-widget attribute
        return "[data-widget='" + target + "']";
    }

    public String generateJavaScript() {
        if (blocks.isEmpty()) return "";

        StringBuilder js = new StringBuilder();
        js.append("// Logic blocks generated by DragWeb\n");
        js.append("document.addEventListener('DOMContentLoaded', function() {\n");

        for (LogicBlock block : blocks) {
            String selector = buildSelector(block);
            String eventName = block.event;

            js.append("  // ").append(block.event).append(" -> ").append(block.action).append("\n");

            if (EVENT_LOAD.equals(eventName)) {
                js.append("  (function() {\n");
                js.append("    ").append(generateActionJs(block, "document.body"));
                js.append("  })();\n\n");
            } else {
                String jsEvent = eventName;
                if ("hover".equals(eventName)) jsEvent = "mouseenter";

                js.append("  document.querySelectorAll('").append(selector).append("').forEach(function(el) {\n");
                js.append("    el.addEventListener('").append(jsEvent).append("', function(event) {\n");
                js.append("      ").append(generateActionJs(block, "el"));
                js.append("    });\n");
                js.append("  });\n\n");
            }
        }

        js.append("});\n");
        return js.toString();
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

            case ACTION_APPEND_CHILD:
                return elVar + ".insertAdjacentHTML('beforeend', '" + escapeJs(block.params) + "');\n";

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

            default:
                return "// Unknown action\n";
        }
    }

    private String escapeJs(String str) {
        return str.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    public String toJson() {
        return new Gson().toJson(blocks);
    }

    public void fromJson(String json) {
        try {
            List<LogicBlock> loaded = new Gson().fromJson(json, new TypeToken<List<LogicBlock>>(){}.getType());
            if (loaded != null) {
                blocks.clear();
                blocks.addAll(loaded);
            }
        } catch (Exception e) {
            // ignore
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
}
