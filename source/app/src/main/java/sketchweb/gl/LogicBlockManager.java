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

    public static final String ACTION_CHANGE_STYLE = "changeStyle";
    public static final String ACTION_ANIMATE = "animate";
    public static final String ACTION_NAVIGATE = "navigate";
    public static final String ACTION_SHOW_HIDE = "showHide";
    public static final String ACTION_SET_TEXT = "setText";
    public static final String ACTION_ADD_CLASS = "addClass";
    public static final String ACTION_ALERT = "alert";

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
        String[] events = {"On Click", "On Hover", "On Input", "On Page Load"};
        String[] eventKeys = {EVENT_CLICK, EVENT_HOVER, EVENT_INPUT, EVENT_LOAD};

        new MaterialAlertDialogBuilder(context)
            .setTitle("Select Event for <" + targetWidgetTag + ">")
            .setItems(events, (dialog, which) -> {
                String selectedEvent = eventKeys[which];
                showActionDialog(targetWidgetTag, selectedEvent, listener);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showActionDialog(String targetWidgetTag, String event, OnBlockAddedListener listener) {
        String[] actions = {"Change Style", "Animate", "Navigate To URL", "Show/Hide Element", "Set Text", "Add CSS Class", "Show Alert"};
        String[] actionKeys = {ACTION_CHANGE_STYLE, ACTION_ANIMATE, ACTION_NAVIGATE, ACTION_SHOW_HIDE, ACTION_SET_TEXT, ACTION_ADD_CLASS, ACTION_ALERT};

        new MaterialAlertDialogBuilder(context)
            .setTitle("Select Action")
            .setItems(actions, (dialog, which) -> {
                String selectedAction = actionKeys[which];
                showActionParamsDialog(targetWidgetTag, event, selectedAction, listener);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showActionParamsDialog(String targetWidgetTag, String event, String action, OnBlockAddedListener listener) {
        android.widget.EditText input = new android.widget.EditText(context);
        input.setPadding(48, 32, 48, 32);

        String hint = "";
        switch (action) {
            case ACTION_CHANGE_STYLE:
                hint = "property:value (e.g. color:red)";
                break;
            case ACTION_ANIMATE:
                hint = "animation name (e.g. fadeIn, slideUp, pulse)";
                break;
            case ACTION_NAVIGATE:
                hint = "URL (e.g. https://example.com)";
                break;
            case ACTION_SHOW_HIDE:
                hint = "toggle, show, or hide";
                break;
            case ACTION_SET_TEXT:
                hint = "New text content";
                break;
            case ACTION_ADD_CLASS:
                hint = "CSS class name";
                break;
            case ACTION_ALERT:
                hint = "Alert message";
                break;
        }
        input.setHint(hint);

        new MaterialAlertDialogBuilder(context)
            .setTitle("Action Parameters")
            .setView(input)
            .setPositiveButton("Add", (dialog, which) -> {
                String value = input.getText().toString().trim();
                LogicBlock block = new LogicBlock();
                block.targetWidget = targetWidgetTag;
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

        // Group blocks by widget for better organization
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
            // Widget header
            TextView widgetHeader = new TextView(context);
            widgetHeader.setText("<" + entry.getKey() + ">");
            widgetHeader.setTextColor(Color.parseColor("#64B5F6"));
            widgetHeader.setTextSize(13);
            widgetHeader.setTypeface(null, Typeface.BOLD);
            widgetHeader.setPadding(0, 12, 0, 6);
            container.addView(widgetHeader);

            for (int index : entry.getValue()) {
                LogicBlock block = blocks.get(index);
                final int blockIndex = index;

                // Block card with stacking visual
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

                // Event label (orange - "WHEN")
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

                // Action label (green - "DO")
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

    public String generateJavaScript() {
        if (blocks.isEmpty()) return "";

        StringBuilder js = new StringBuilder();
        js.append("// Logic blocks generated by DragWeb\n");
        js.append("document.addEventListener('DOMContentLoaded', function() {\n");

        for (LogicBlock block : blocks) {
            String selector = "[data-widget='" + block.targetWidget + "']";
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
                js.append("    el.addEventListener('").append(jsEvent).append("', function() {\n");
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
            case ACTION_CHANGE_STYLE:
                String[] parts = block.params.split(":", 2);
                if (parts.length == 2) {
                    return elVar + ".style." + parts[0].trim() + " = '" + parts[1].trim() + "';\n";
                }
                return "// Invalid style params\n";

            case ACTION_ANIMATE:
                return elVar + ".style.animation = '" + block.params + " 0.5s ease';\n";

            case ACTION_NAVIGATE:
                return "window.location.href = '" + block.params + "';\n";

            case ACTION_SHOW_HIDE:
                if ("toggle".equals(block.params)) {
                    return elVar + ".style.display = " + elVar + ".style.display === 'none' ? '' : 'none';\n";
                } else if ("hide".equals(block.params)) {
                    return elVar + ".style.display = 'none';\n";
                } else {
                    return elVar + ".style.display = '';\n";
                }

            case ACTION_SET_TEXT:
                return elVar + ".textContent = '" + block.params.replace("'", "\\'") + "';\n";

            case ACTION_ADD_CLASS:
                return elVar + ".classList.toggle('" + block.params.replace("'", "\\'") + "');\n";

            case ACTION_ALERT:
                return "alert('" + block.params.replace("'", "\\'") + "');\n";

            default:
                return "// Unknown action\n";
        }
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
        public String event;
        public String action;
        public String params;
    }

    public interface OnBlockAddedListener {
        void onBlockAdded(LogicBlock block);
    }
}
