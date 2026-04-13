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

    // CSS states (replaces JS events)
    public static final String STATE_DEFAULT = "default";
    public static final String STATE_HOVER = "hover";
    public static final String STATE_FOCUS = "focus";
    public static final String STATE_ACTIVE = "active";

    // Actions focused on CSS/HTML attributes
    public static final String ACTION_CHANGE_STYLE = "changeStyle";
    public static final String ACTION_ADD_CLASS = "addClass";
    public static final String ACTION_REMOVE_CLASS = "removeClass";
    public static final String ACTION_TOGGLE_CLASS = "toggleClass";
    public static final String ACTION_SET_ATTRIBUTE = "setAttribute";
    public static final String ACTION_SET_TEXT = "setText";
    public static final String ACTION_CUSTOM_JS = "customJs"; // optional escape hatch

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

    /** Count blocks for a specific state. */
    public int getBlockCountForState(String state) {
        int count = 0;
        for (LogicBlock block : blocks) {
            if (state.equals(block.event)) {
                count++;
            }
        }
        return count;
    }

    public void showAddBlockDialog(String targetWidgetTag, OnBlockAddedListener listener) {
        showAddBlockDialog(targetWidgetTag, TARGET_MODE_ID, listener);
    }

    public void showAddBlockDialog(String targetWidgetTag, String targetMode, OnBlockAddedListener listener) {
        String[] states = { "Default", "Hover", "Focus", "Active" };
        String[] stateKeys = { STATE_DEFAULT, STATE_HOVER, STATE_FOCUS, STATE_ACTIVE };

        String modeLabel = TARGET_MODE_CLASS.equals(targetMode) ? "." :
                           TARGET_MODE_TAG.equals(targetMode) ? "" : "#";

        new MaterialAlertDialogBuilder(context)
            .setTitle("Select state for " + modeLabel + targetWidgetTag)
            .setItems(states, (dialog, which) -> {
                String selectedState = stateKeys[which];
                showActionDialog(targetWidgetTag, targetMode, selectedState, listener);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showActionDialog(String targetWidgetTag, String targetMode, String state, OnBlockAddedListener listener) {
        String[] actions = {
            "Change Style", "Add Class", "Remove Class", "Toggle Class",
            "Set Attribute", "Set Text", "Custom JavaScript"
        };
        String[] actionKeys = {
            ACTION_CHANGE_STYLE, ACTION_ADD_CLASS, ACTION_REMOVE_CLASS, ACTION_TOGGLE_CLASS,
            ACTION_SET_ATTRIBUTE, ACTION_SET_TEXT, ACTION_CUSTOM_JS
        };

        new MaterialAlertDialogBuilder(context)
            .setTitle("Select Action")
            .setItems(actions, (dialog, which) -> {
                String selectedAction = actionKeys[which];
                showActionParamsDialog(targetWidgetTag, targetMode, state, selectedAction, listener);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showActionParamsDialog(String targetWidgetTag, String targetMode, String state, String action, OnBlockAddedListener listener) {
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
                block.event = state;
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
            case ACTION_ADD_CLASS: return "Class name";
            case ACTION_REMOVE_CLASS: return "Class name";
            case ACTION_TOGGLE_CLASS: return "Class name";
            case ACTION_SET_ATTRIBUTE: return "attr:value (e.g. aria-label:Close)";
            case ACTION_SET_TEXT: return "New text content";
            case ACTION_CUSTOM_JS: return "Custom JS (optional)";
            default: return "Value";
        }
    }

    public void showBlocksDialog() {
        if (blocks.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                .setTitle("Logic Blocks")
                .setMessage("No style blocks added yet.\nSelect a state to add CSS/HTML blocks.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 16);
        scrollView.addView(container);

        // Group blocks by state
        Map<String, List<Integer>> groupedBlocks = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            LogicBlock block = blocks.get(i);
            String key = block.event != null ? block.event : STATE_DEFAULT;
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

    /** Get display name for a state. */
    public static String getEventDisplayName(String eventType) {
        switch (eventType) {
            case STATE_HOVER: return "Hover";
            case STATE_FOCUS: return "Focus";
            case STATE_ACTIVE: return "Active";
            case STATE_DEFAULT:
            default: return "Default";
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
     * Generate CSS-first rules. Only Custom JS is emitted as JavaScript.
     */
    public String generateCssRules() {
        if (blocks.isEmpty()) return "";

        StringBuilder css = new StringBuilder();
        css.append("/* Generated by DragWeb blocks (CSS-first) */\n");

        for (LogicBlock block : blocks) {
            String selector = buildSelector(block);
            String state = block.event != null ? block.event : STATE_DEFAULT;
            String pseudo = "";
            switch (state) {
                case STATE_HOVER: pseudo = ":hover"; break;
                case STATE_FOCUS: pseudo = ":focus"; break;
                case STATE_ACTIVE: pseudo = ":active"; break;
                default: pseudo = "";
            }

            if (ACTION_CHANGE_STYLE.equals(block.action)) {
                css.append(selector).append(pseudo).append(" {\n");
                css.append(cssFromParams(block.params));
                css.append("}\n");
            } else if (ACTION_ADD_CLASS.equals(block.action)) {
                // Approximate utility application with a comment; actual class application would need JS
                css.append("/* addClass ").append(block.params).append(" to ").append(selector).append(pseudo).append(" */\n");
            } else if (ACTION_REMOVE_CLASS.equals(block.action) || ACTION_TOGGLE_CLASS.equals(block.action)) {
                css.append("/* class change (").append(block.action).append(") requires JS; skipped for CSS-only */\n");
            } else if (ACTION_SET_ATTRIBUTE.equals(block.action)) {
                css.append("/* setAttribute ").append(block.params).append(" on ").append(selector).append(pseudo).append(" */\n");
            } else if (ACTION_SET_TEXT.equals(block.action)) {
                css.append("/* setText requires JS to modify DOM text; skipped */\n");
            }
        }

        return css.toString();
    }

    /** Minimal JS generation: only custom JS is preserved. */
    public String generateJavaScript() {
        if (blocks.isEmpty()) return "";
        StringBuilder js = new StringBuilder();
        for (LogicBlock block : blocks) {
            if (ACTION_CUSTOM_JS.equals(block.action)) {
                js.append("// custom js\n").append(block.params).append("\n");
            }
        }
        return js.toString();
    }

    private String cssFromParams(String params) {
        if (params == null || params.isEmpty()) return "";
        if (params.contains(":")) {
            return "  " + params.replace(";", ";\n  ") + ";\n";
        }
        return "  /* invalid style params: " + params + " */\n";
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
