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

    public static final String ACTION_SET_DISPLAY = "setDisplay";
    public static final String ACTION_SET_POSITION = "setPosition";
    public static final String ACTION_SET_OVERFLOW = "setOverflow";
    public static final String ACTION_SET_COLOR = "setColor";
    public static final String ACTION_SET_BACKGROUND = "setBackground";
    public static final String ACTION_SET_WIDTH = "setWidth";
    public static final String ACTION_SET_HEIGHT = "setHeight";
    public static final String ACTION_SET_MARGIN = "setMargin";
    public static final String ACTION_SET_PADDING = "setPadding";
    public static final String ACTION_SET_BORDER = "setBorder";
    public static final String ACTION_SET_RADIUS = "setRadius";
    public static final String ACTION_SET_OPACITY = "setOpacity";
    public static final String ACTION_NAVIGATE = "navigate";
    public static final String ACTION_SHOW = "show";
    public static final String ACTION_HIDE = "hide";
    public static final String ACTION_SET_TEXT = "setText";
    public static final String ACTION_SET_HTML = "setHTML";
    public static final String ACTION_SET_HREF = "setHref";
    public static final String ACTION_ADD_CLASS = "addClass";
    public static final String ACTION_REMOVE_CLASS = "removeClass";
    public static final String ACTION_TOGGLE_CLASS = "toggleClass";
    public static final String ACTION_SCROLL_TO = "scrollTo";
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
            "CSS: setDisplay",
            "CSS: setPosition",
            "CSS: setOverflow",
            "CSS: setColor",
            "CSS: setBackground",
            "CSS: setWidth",
            "CSS: setHeight",
            "CSS: setMargin",
            "CSS: setPadding",
            "CSS: setBorder",
            "CSS: setRadius",
            "CSS: setOpacity",
            "CSS: addClass",
            "CSS: removeClass",
            "CSS: toggleClass",
            "HTML: setHref",
            "HTML: scrollTo",
            "HTML: setText",
            "HTML: setHTML",
            "HTML: show",
            "HTML: hide",
            "HTML: goToPage",
            "HTML: openPage",
            "HTML: navigateToUrl"
        };
        String[] actionKeys = {
            ACTION_SET_DISPLAY,
            ACTION_SET_POSITION,
            ACTION_SET_OVERFLOW,
            ACTION_SET_COLOR,
            ACTION_SET_BACKGROUND,
            ACTION_SET_WIDTH,
            ACTION_SET_HEIGHT,
            ACTION_SET_MARGIN,
            ACTION_SET_PADDING,
            ACTION_SET_BORDER,
            ACTION_SET_RADIUS,
            ACTION_SET_OPACITY,
            ACTION_ADD_CLASS,
            ACTION_REMOVE_CLASS,
            ACTION_TOGGLE_CLASS,
            ACTION_SET_HREF,
            ACTION_SCROLL_TO,
            ACTION_SET_TEXT,
            ACTION_SET_HTML,
            ACTION_SHOW,
            ACTION_HIDE,
            ACTION_GO_TO_PAGE,
            ACTION_OPEN_PAGE,
            ACTION_NAVIGATE
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
            case ACTION_SET_DISPLAY: return "none, flex, block, inline-block";
            case ACTION_SET_POSITION: return "static, relative, absolute, fixed";
            case ACTION_SET_OVERFLOW: return "hidden, scroll, auto, visible";
            case ACTION_SET_COLOR: return "Text color (e.g. #222222)";
            case ACTION_SET_BACKGROUND: return "Color/gradient (e.g. #111 or linear-gradient(...))";
            case ACTION_SET_WIDTH: return "Width value (e.g. 100%, 240px)";
            case ACTION_SET_HEIGHT: return "Height value (e.g. auto, 60px)";
            case ACTION_SET_MARGIN: return "Margin value (e.g. 8px or 8px 12px)";
            case ACTION_SET_PADDING: return "Padding value (e.g. 8px or 8px 12px)";
            case ACTION_SET_BORDER: return "Border value (e.g. 1px solid #ddd)";
            case ACTION_SET_RADIUS: return "Border radius (e.g. 8px)";
            case ACTION_SET_OPACITY: return "0 to 1 (e.g. 0.7)";
            case ACTION_NAVIGATE: return "URL (e.g. https://example.com)";
            case ACTION_GO_TO_PAGE: return "Page name (e.g. about, contact)";
            case ACTION_OPEN_PAGE: return "Page name to open in new tab";
            case ACTION_SHOW: return "Show selected target";
            case ACTION_HIDE: return "Hide selected target";
            case ACTION_SET_TEXT: return "New text content";
            case ACTION_SET_HTML: return "HTML content (e.g. <b>Hello</b>)";
            case ACTION_SET_HREF: return "URL or #section";
            case ACTION_ADD_CLASS: return "CSS class name to add";
            case ACTION_REMOVE_CLASS: return "CSS class name to remove";
            case ACTION_TOGGLE_CLASS: return "CSS class name to toggle";
            case ACTION_SCROLL_TO: return "id/class/tag value to scroll to (e.g. #section1)";
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
                actionLabel.setText("[" + getActionCategory(block.action).toUpperCase() + "] "
                    + block.action + "(" + block.params + ")");
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
            case ACTION_SET_DISPLAY:
                return elVar + ".style.display = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_POSITION:
                return elVar + ".style.position = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_OVERFLOW:
                return elVar + ".style.overflow = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_COLOR:
                return elVar + ".style.color = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_BACKGROUND:
                return elVar + ".style.background = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_WIDTH:
                return elVar + ".style.width = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_HEIGHT:
                return elVar + ".style.height = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_MARGIN:
                return elVar + ".style.margin = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_PADDING:
                return elVar + ".style.padding = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_BORDER:
                return elVar + ".style.border = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_RADIUS:
                return elVar + ".style.borderRadius = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_OPACITY:
                return elVar + ".style.opacity = '" + escapeJs(block.params) + "';\n";

            case ACTION_NAVIGATE:
                return "window.location.href = '" + escapeJs(block.params) + "';\n";

            case ACTION_GO_TO_PAGE:
                return "window.location.href = '" + escapeJs(block.params) + ".html';\n";

            case ACTION_OPEN_PAGE:
                return "window.open('" + escapeJs(block.params) + ".html', '_blank');\n";

            case ACTION_SHOW:
                return elVar + ".style.display = '';\n";

            case ACTION_HIDE:
                return elVar + ".style.display = 'none';\n";

            case ACTION_SET_TEXT:
                return elVar + ".textContent = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_HTML:
                return elVar + ".innerHTML = '" + escapeJs(block.params) + "';\n";

            case ACTION_SET_HREF:
                return elVar + ".setAttribute('href', '" + escapeJs(block.params) + "');\n";

            case ACTION_ADD_CLASS:
                return elVar + ".classList.add('" + escapeJs(block.params) + "');\n";

            case ACTION_REMOVE_CLASS:
                return elVar + ".classList.remove('" + escapeJs(block.params) + "');\n";

            case ACTION_TOGGLE_CLASS:
                return elVar + ".classList.toggle('" + escapeJs(block.params) + "');\n";

            case ACTION_SCROLL_TO:
                return "document.querySelector('" + escapeJs(block.params) + "')?.scrollIntoView({behavior:'smooth'});\n";

            default:
                return "// Unknown action\n";
        }
    }

    private String escapeJs(String str) {
        return str.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    public String getActionCategory(String action) {
        if (ACTION_SET_DISPLAY.equals(action)
            || ACTION_SET_POSITION.equals(action)
            || ACTION_SET_OVERFLOW.equals(action)
            || ACTION_SET_COLOR.equals(action)
            || ACTION_SET_BACKGROUND.equals(action)
            || ACTION_SET_WIDTH.equals(action)
            || ACTION_SET_HEIGHT.equals(action)
            || ACTION_SET_MARGIN.equals(action)
            || ACTION_SET_PADDING.equals(action)
            || ACTION_SET_BORDER.equals(action)
            || ACTION_SET_RADIUS.equals(action)
            || ACTION_SET_OPACITY.equals(action)
            || ACTION_ADD_CLASS.equals(action)
            || ACTION_REMOVE_CLASS.equals(action)
            || ACTION_TOGGLE_CLASS.equals(action)) {
            return "css";
        }
        if (ACTION_SET_HREF.equals(action)
            || ACTION_SCROLL_TO.equals(action)
            || ACTION_SET_TEXT.equals(action)
            || ACTION_SET_HTML.equals(action)
            || ACTION_SHOW.equals(action)
            || ACTION_HIDE.equals(action)
            || ACTION_GO_TO_PAGE.equals(action)
            || ACTION_OPEN_PAGE.equals(action)
            || ACTION_NAVIGATE.equals(action)) {
            return "html";
        }
        return "logic";
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
