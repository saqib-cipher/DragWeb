package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HierarchyTreeAdapter extends RecyclerView.Adapter<HierarchyTreeAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(View widgetView);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(View widgetView);
    }

    private List<TreeNode> flatList = new ArrayList<>();
    private Set<Integer> collapsedNodes = new HashSet<>();
    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;
    private View selectedWidgetView;
    private Context context;

    public HierarchyTreeAdapter(Context context) {
        this.context = context;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setSelectedView(View view) {
        this.selectedWidgetView = view;
        notifyDataSetChanged();
    }

    public void buildTree(ViewGroup screen) {
        flatList.clear();
        addNode(screen, 0, "body");
        notifyDataSetChanged();
    }

    private void addNode(View view, int depth, String forceName) {
        String tag = "unknown";
        String name = forceName;
        boolean isContainer = view instanceof ViewGroup;

        if (view.getTag() instanceof Map) {
            Map<String, Object> widgetMap = (Map<String, Object>) view.getTag();
            if (widgetMap.containsKey("tag")) {
                tag = widgetMap.get("tag").toString();
            }
            if (name == null) {
                Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
                if (function != null && function.containsKey("text")) {
                    String text = function.get("text").toString();
                    if (text.length() > 15) text = text.substring(0, 15) + "...";
                    name = tag + " \"" + text + "\"";
                } else {
                    name = tag;
                }
            }
        }

        TreeNode node = new TreeNode();
        node.view = view;
        node.depth = depth;
        node.tag = tag;
        node.name = name != null ? name : tag;
        node.isContainer = isContainer;
        node.childCount = isContainer ? ((ViewGroup) view).getChildCount() : 0;
        node.nodeId = System.identityHashCode(view);

        flatList.add(node);

        if (isContainer && !collapsedNodes.contains(node.nodeId)) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                addNode(vg.getChildAt(i), depth + 1, null);
            }
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        layout.setPadding(8, 6, 8, 6);
        layout.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new ViewHolder(layout);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        TreeNode node = flatList.get(position);
        LinearLayout layout = (LinearLayout) holder.itemView;
        layout.removeAllViews();

        // Indent
        int indentPx = node.depth * 24;
        layout.setPadding(8 + indentPx, 6, 8, 6);

        // Collapse/expand arrow
        TextView arrow = new TextView(context);
        arrow.setTextSize(12);
        arrow.setTextColor(Color.parseColor("#90A4AE"));
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(24, ViewGroup.LayoutParams.WRAP_CONTENT);
        arrow.setLayoutParams(arrowParams);
        if (node.isContainer && node.childCount > 0) {
            boolean collapsed = collapsedNodes.contains(node.nodeId);
            arrow.setText(collapsed ? "▶" : "▼");
            arrow.setOnClickListener(v -> {
                if (collapsedNodes.contains(node.nodeId)) {
                    collapsedNodes.remove(node.nodeId);
                } else {
                    collapsedNodes.add(node.nodeId);
                }
                // Rebuild tree from root
                if (flatList.size() > 0) {
                    TreeNode root = flatList.get(0);
                    if (root.view instanceof ViewGroup) {
                        buildTree((ViewGroup) root.view);
                    }
                }
            });
        } else {
            arrow.setText("  ");
        }
        layout.addView(arrow);

        // Tag icon indicator
        TextView icon = new TextView(context);
        icon.setTextSize(10);
        icon.setPadding(0, 0, 8, 0);
        icon.setTextColor(getTagColor(node.tag));
        icon.setText(getTagIcon(node.tag));
        layout.addView(icon);

        // Name
        TextView nameView = new TextView(context);
        nameView.setText(node.name);
        nameView.setTextSize(12);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);

        boolean isSelected = node.view == selectedWidgetView;
        if (isSelected) {
            nameView.setTextColor(Color.parseColor("#2196F3"));
            nameView.setTypeface(null, Typeface.BOLD);
            layout.setBackgroundColor(Color.parseColor("#1A2196F3"));
        } else {
            nameView.setTextColor(Color.parseColor("#CFD8DC"));
            nameView.setTypeface(null, Typeface.NORMAL);
            layout.setBackgroundColor(Color.TRANSPARENT);
        }
        layout.addView(nameView);

        // Click to select widget
        layout.setOnClickListener(v -> {
            if (clickListener != null && node.depth > 0) {
                clickListener.onItemClick(node.view);
            }
        });

        layout.setOnLongClickListener(v -> {
            if (longClickListener != null && node.depth > 0) {
                longClickListener.onItemLongClick(node.view);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return flatList.size();
    }

    private String getTagIcon(String tag) {
        switch (tag) {
            case "div": return "□";
            case "section": return "§";
            case "header": return "▤";
            case "footer": return "▦";
            case "nav": return "☰";
            case "p": case "h1": case "h2": case "h3": case "span": case "label": return "T";
            case "button": return "⊞";
            case "img": return "🖼";
            case "input": case "textarea": return "▭";
            case "a": return "🔗";
            case "ul": case "ol": return "≡";
            case "form": return "📋";
            case "video": return "▶";
            case "audio": return "♫";
            case "canvas": return "🎨";
            case "table": return "⊞";
            case "hr": return "─";
            default: return "◇";
        }
    }

    private int getTagColor(String tag) {
        switch (tag) {
            case "div": case "section": return Color.parseColor("#90CAF9");
            case "header": case "footer": case "nav": return Color.parseColor("#80CBC4");
            case "p": case "h1": case "h2": case "h3": case "span": return Color.parseColor("#FFE082");
            case "button": return Color.parseColor("#FFCC80");
            case "img": return Color.parseColor("#CE93D8");
            case "input": case "textarea": return Color.parseColor("#A5D6A7");
            case "a": return Color.parseColor("#90CAF9");
            case "form": return Color.parseColor("#80DEEA");
            default: return Color.parseColor("#B0BEC5");
        }
    }

    static class TreeNode {
        View view;
        int depth;
        String tag;
        String name;
        boolean isContainer;
        int childCount;
        int nodeId;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View v) { super(v); }
    }
}
