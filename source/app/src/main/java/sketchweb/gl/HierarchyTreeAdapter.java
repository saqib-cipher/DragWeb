package sketchweb.gl;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
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
    private String filterQuery = "";

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

    public void setFilter(String query) {
        this.filterQuery = query != null ? query.toLowerCase() : "";
        if (flatList.size() > 0) {
            TreeNode root = flatList.get(0);
            if (root.view instanceof ViewGroup) {
                buildTree((ViewGroup) root.view);
            }
        }
    }

    public void buildTree(ViewGroup screen) {
        flatList.clear();
        addNode(screen, 0, "body");
        notifyDataSetChanged();
    }

    private void addNode(View view, int depth, String forceName) {
        String tag = "unknown";
        String name = forceName;
        String id = "";
        boolean isContainer = view instanceof ViewGroup;

        if (view.getTag() instanceof Map) {
            Map<String, Object> widgetMap = (Map<String, Object>) view.getTag();
            if (widgetMap.containsKey("tag")) {
                tag = widgetMap.get("tag").toString();
            }
            if (widgetMap.containsKey("id")) {
                id = widgetMap.get("id").toString();
            }
            if (name == null) {
                Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
                if (function != null && function.containsKey("text")) {
                    String text = function.get("text").toString();
                    if (text.length() > 15) text = text.substring(0, 15) + "...";
                    name = tag + " \"" + text + "\"";
                } else if (!id.isEmpty()) {
                    name = tag + " #" + id;
                } else {
                    name = tag;
                }
            }
        }

        // Apply filter
        if (!filterQuery.isEmpty() && depth > 0) {
            String searchable = (name + " " + tag + " " + id).toLowerCase();
            if (!searchable.contains(filterQuery)) {
                // Still add children in case they match
                if (isContainer) {
                    ViewGroup vg = (ViewGroup) view;
                    for (int i = 0; i < vg.getChildCount(); i++) {
                        addNode(vg.getChildAt(i), depth, null);
                    }
                }
                return;
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
        layout.setGravity(Gravity.CENTER_VERTICAL);
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
        int indentPx = node.depth * 16;
        layout.setPadding(6 + indentPx, 5, 6, 5);

        // Collapse/expand arrow
        TextView arrow = new TextView(context);
        arrow.setTextSize(11);
        arrow.setTextColor(Color.parseColor("#78909C"));
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(20, ViewGroup.LayoutParams.WRAP_CONTENT);
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

        // Tag icon indicator (colored dot)
        View dot = new View(context);
        int dotSize = 10;
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotParams.setMargins(2, 0, 6, 0);
        dot.setLayoutParams(dotParams);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(getTagColor(node.tag));
        dot.setBackground(dotBg);
        layout.addView(dot);

        // Name
        TextView nameView = new TextView(context);
        nameView.setText(node.name);
        nameView.setTextSize(11);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);

        boolean isSelected = node.view == selectedWidgetView;
        if (isSelected) {
            nameView.setTextColor(Color.parseColor("#2196F3"));
            nameView.setTypeface(null, Typeface.BOLD);
            GradientDrawable selectedBg = new GradientDrawable();
            selectedBg.setColor(Color.parseColor("#1A2196F3"));
            selectedBg.setCornerRadius(8);
            layout.setBackground(selectedBg);
        } else {
            nameView.setTextColor(Color.parseColor("#B0BEC5"));
            nameView.setTypeface(null, Typeface.NORMAL);
            layout.setBackgroundColor(Color.TRANSPARENT);
        }

        // Child count badge
        if (node.isContainer && node.childCount > 0) {
            nameView.setText(node.name + " (" + node.childCount + ")");
        }

        layout.addView(nameView);

        // Click to select widget
        layout.setOnClickListener(v -> {
            if (clickListener != null && node.depth > 0) {
                clickListener.onItemClick(node.view);
            }
        });

        // Long click for drag reorder
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
            case "img": return "⊡";
            case "input": case "textarea": return "▭";
            case "a": return "⊕";
            case "ul": case "ol": return "≡";
            case "form": return "⊟";
            case "video": return "▶";
            case "audio": return "♫";
            case "canvas": return "◆";
            case "table": return "⊞";
            case "hr": return "─";
            default: return "◇";
        }
    }

    private int getTagColor(String tag) {
        switch (tag) {
            case "div": case "section": return Color.parseColor("#42A5F5");
            case "header": case "footer": case "nav": return Color.parseColor("#26A69A");
            case "p": case "h1": case "h2": case "h3": case "span": return Color.parseColor("#FFCA28");
            case "button": return Color.parseColor("#FFA726");
            case "img": return Color.parseColor("#AB47BC");
            case "input": case "textarea": return Color.parseColor("#66BB6A");
            case "a": return Color.parseColor("#42A5F5");
            case "form": return Color.parseColor("#26C6DA");
            case "ul": case "ol": return Color.parseColor("#78909C");
            case "video": case "audio": return Color.parseColor("#EF5350");
            case "table": return Color.parseColor("#8D6E63");
            default: return Color.parseColor("#90A4AE");
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
