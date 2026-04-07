package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
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

    public void showAddBlockDialog(String targetWidgetTag, OnBlockAddedListener listener) {
        String[] events = {"On Click", "On Hover", "On Input", "On Page Load"};
        String[] eventKeys = {EVENT_CLICK, EVENT_HOVER, EVENT_INPUT, EVENT_LOAD};

        new MaterialAlertDialogBuilder(context)
            .setTitle("Select Event")
            .setItems(events, (dialog, which) -> {
                String selectedEvent = eventKeys[which];
                showActionDialog(targetWidgetTag, selectedEvent, listener);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showActionDialog(String targetWidgetTag, String event, OnBlockAddedListener listener) {
        String[] actions = {"Change Style", "Animate", "Navigate To URL", "Show/Hide Element", "Set Text", "Show Alert"};
        String[] actionKeys = {ACTION_CHANGE_STYLE, ACTION_ANIMATE, ACTION_NAVIGATE, ACTION_SHOW_HIDE, ACTION_SET_TEXT, ACTION_ALERT};

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
                hint = "animation name (e.g. fadeIn, slideUp)";
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
                .setMessage("No logic blocks added yet.\nSelect a widget and add events from the logic tab.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 16, 32, 16);
        scrollView.addView(container);

        for (int i = 0; i < blocks.size(); i++) {
            LogicBlock block = blocks.get(i);
            final int index = i;

            LinearLayout blockView = new LinearLayout(context);
            blockView.setOrientation(LinearLayout.VERTICAL);
            blockView.setPadding(16, 12, 16, 12);
            blockView.setBackgroundColor(Color.parseColor("#1E1E2E"));

            LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blockParams.setMargins(0, 4, 0, 4);
            blockView.setLayoutParams(blockParams);

            // Event label
            TextView eventLabel = new TextView(context);
            eventLabel.setText("WHEN " + block.event.toUpperCase() + " on <" + block.targetWidget + ">");
            eventLabel.setTextColor(Color.parseColor("#FF9800"));
            eventLabel.setTextSize(12);
            eventLabel.setTypeface(null, Typeface.BOLD);
            blockView.addView(eventLabel);

            // Action label
            TextView actionLabel = new TextView(context);
            actionLabel.setText("DO " + block.action + "(" + block.params + ")");
            actionLabel.setTextColor(Color.parseColor("#4CAF50"));
            actionLabel.setTextSize(12);
            blockView.addView(actionLabel);

            blockView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(context)
                    .setTitle("Delete Block?")
                    .setMessage("Remove this logic block?")
                    .setPositiveButton("Delete", (d, w) -> {
                        removeBlock(index);
                        showBlocksDialog();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return true;
            });

            container.addView(blockView);
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
