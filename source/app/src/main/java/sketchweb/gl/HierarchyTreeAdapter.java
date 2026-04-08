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
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.card.MaterialCardView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
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

    public interface OnReorderListener {
        void onReorder(View movedView, ViewGroup newParent, int newIndex);
    }

    private List<TreeNode> flatList = new ArrayList<>();
    private Set<Integer> collapsedNodes = new HashSet<>();
    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;
    private OnReorderListener reorderListener;
    private View selectedWidgetView;
    private Context context;
    private String filterQuery = "";
    private ViewGroup rootScreen;
    private ItemTouchHelper touchHelper;

    public HierarchyTreeAdapter(Context context) {
        this.context = context;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnReorderListener(OnReorderListener listener) {
        this.reorderListener = listener;
    }

    public void setSelectedView(View view) {
        this.selectedWidgetView = view;
        notifyDataSetChanged();
    }

    public void setFilter(String query) {
        this.filterQuery = query != null ? query.toLowerCase() : "";
        if (rootScreen != null) {
            buildTree(rootScreen);
        }
    }

    public void buildTree(ViewGroup screen) {
        this.rootScreen = screen;
        flatList.clear();
        addNode(screen, 0, "body");
        notifyDataSetChanged();
    }

    public void attachToRecyclerView(RecyclerView rv) {
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();

                // Don't move root (body)
                if (fromPos == 0 || toPos == 0) return false;

                TreeNode fromNode = flatList.get(fromPos);
                TreeNode toNode = flatList.get(toPos);

                // Perform the actual view reorder
                if (fromNode.view != null && fromNode.view.getParent() instanceof ViewGroup) {
                    ViewGroup parent = (ViewGroup) fromNode.view.getParent();
                    int fromIndex = parent.indexOfChild(fromNode.view);
                    if (fromIndex >= 0) {
                        parent.removeView(fromNode.view);

                        ViewGroup targetParent;
                        int toIndex;

                        // If we are dropping onto a container node itself, make it a child
                        if (toNode.isContainer && toNode.view instanceof ViewGroup) {
                            targetParent = (ViewGroup) toNode.view;
                            toIndex = targetParent.getChildCount(); // Append to end of container
                        } else {
                            // Otherwise put it next to the target node in its parent
                            targetParent = toNode.view.getParent() instanceof ViewGroup ?
                                (ViewGroup) toNode.view.getParent() : parent;
                            toIndex = targetParent.indexOfChild(toNode.view);
                            if (toIndex < 0) toIndex = targetParent.getChildCount();

                            // If moving down within the same parent, adjust index
                            if (targetParent == parent && toPos > fromPos) {
                                toIndex = Math.min(toIndex + 1, parent.getChildCount());
                            }
                        }

                        targetParent.addView(fromNode.view, Math.min(toIndex, targetParent.getChildCount()));

                        if (reorderListener != null) {
                            reorderListener.onReorder(fromNode.view, targetParent, toIndex);
                        }
                    }
                }

                // Update flat list
                Collections.swap(flatList, fromPos, toPos);
                notifyItemMoved(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // Rebuild tree after drag
                if (rootScreen != null) {
                    buildTree(rootScreen);
                }
            }
        });
        touchHelper.attachToRecyclerView(rv);
    }

    public void startDrag(RecyclerView.ViewHolder holder) {
        if (touchHelper != null) {
            touchHelper.startDrag(holder);
        }
    }

    private void addNode(View view, int depth, String forceName) {
        String tag = "unknown";
        String name = forceName;
        String id = "";
        String cssClass = "";
        boolean isContainer = view instanceof ViewGroup;
        boolean isLocked = false;
        boolean isHidden = false;

        if (view.getTag() instanceof Map) {
            Map<String, Object> widgetMap = (Map<String, Object>) view.getTag();
            if (widgetMap.containsKey("tag")) {
                tag = widgetMap.get("tag").toString();
            }
            if (widgetMap.containsKey("id")) {
                id = widgetMap.get("id").toString();
            }
            if (widgetMap.containsKey("locked")) {
                isLocked = Boolean.TRUE.equals(widgetMap.get("locked"));
            }
            if (widgetMap.containsKey("hidden")) {
                isHidden = Boolean.TRUE.equals(widgetMap.get("hidden"));
            }
            Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
            if (function != null) {
                if (function.containsKey("id") && id.isEmpty()) {
                    id = function.get("id").toString();
                }
                if (function.containsKey("class")) {
                    cssClass = function.get("class").toString();
                }
            }
            if (name == null) {
                if (function != null && function.containsKey("text")) {
                    String text = function.get("text").toString();
                    if (text.length() > 15) text = text.substring(0, 15) + "...";
                    name = tag + " \"" + text + "\"";
                } else if (!id.isEmpty()) {
                    name = tag + " #" + id;
                } else if (!cssClass.isEmpty()) {
                    String shortClass = cssClass.length() > 12 ? cssClass.substring(0, 12) + ".." : cssClass;
                    name = tag + " ." + shortClass;
                } else {
                    name = tag;
                }
            }
        }

        // Apply filter
        if (!filterQuery.isEmpty() && depth > 0) {
            String searchable = (name + " " + tag + " " + id + " " + cssClass).toLowerCase();
            if (!searchable.contains(filterQuery)) {
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
        node.id = id;
        node.cssClass = cssClass;
        node.isContainer = isContainer;
        node.childCount = isContainer ? ((ViewGroup) view).getChildCount() : 0;
        node.nodeId = System.identityHashCode(view);
        node.isLocked = isLocked;
        node.isHidden = isHidden;

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
        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(context);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(4, 4, 4, 4);
        card.setLayoutParams(params);
        card.setCardElevation(1);
        card.setRadius(16);
        card.setStrokeWidth(2);
        card.setStrokeColor(Color.parseColor("#E0E0E0"));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(12, 12, 12, 12);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(layout);
        return new ViewHolder(card);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        TreeNode node = flatList.get(position);
        com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) holder.itemView;
        LinearLayout layout = (LinearLayout) card.getChildAt(0);
        layout.removeAllViews();

        // Indent with tree lines
        int indentPx = node.depth * 20;
        layout.setPadding(8 + indentPx, 6, 8, 6);

        // Tree connector line for children
        if (node.depth > 0) {
            TextView connector = new TextView(context);
            connector.setText("\u2502 ");
            connector.setTextSize(10);
            connector.setTextColor(Color.parseColor("#37474F"));
            LinearLayout.LayoutParams connParams = new LinearLayout.LayoutParams(
                16, ViewGroup.LayoutParams.WRAP_CONTENT);
            connector.setLayoutParams(connParams);
            layout.addView(connector);
        }

        // Drag handle (for non-root items)
        if (node.depth > 0 && !node.isLocked) {
            TextView dragHandle = new TextView(context);
            dragHandle.setText("\u2261"); // hamburger icon
            dragHandle.setTextSize(16);
            dragHandle.setTextColor(Color.parseColor("#78909C"));
            LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                28, ViewGroup.LayoutParams.WRAP_CONTENT);
            dragHandle.setLayoutParams(handleParams);
            dragHandle.setGravity(Gravity.CENTER);
            dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    startDrag(holder);
                }
                return false;
            });
            layout.addView(dragHandle);
        }

        // Collapse/expand arrow
        TextView arrow = new TextView(context);
        arrow.setTextSize(12);
        arrow.setTextColor(Color.parseColor("#90A4AE"));
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(24, ViewGroup.LayoutParams.WRAP_CONTENT);
        arrowParams.setMargins(0, 0, 2, 0);
        arrow.setLayoutParams(arrowParams);
        arrow.setGravity(Gravity.CENTER);
        if (node.isContainer && node.childCount > 0) {
            boolean collapsed = collapsedNodes.contains(node.nodeId);
            arrow.setText(collapsed ? "\u25B6" : "\u25BC");
            arrow.setOnClickListener(v -> {
                if (collapsedNodes.contains(node.nodeId)) {
                    collapsedNodes.remove(node.nodeId);
                } else {
                    collapsedNodes.add(node.nodeId);
                }
                if (rootScreen != null) {
                    buildTree(rootScreen);
                }
            });
        } else {
            arrow.setText("  ");
        }
        layout.addView(arrow);

        // Tag icon indicator (colored dot)
        View dot = new View(context);
        int dotSize = 12;
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotParams.setMargins(2, 0, 6, 0);
        dot.setLayoutParams(dotParams);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(getTagColor(node.tag));
        dot.setBackground(dotBg);
        layout.addView(dot);

        // Name + info column
        LinearLayout nameCol = new LinearLayout(context);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameColParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameCol.setLayoutParams(nameColParams);

        TextView nameView = new TextView(context);
        String displayName = node.name;
        if (node.isContainer && node.childCount > 0) {
            displayName += " (" + node.childCount + ")";
        }
        // Add lock/hidden indicators
        if (node.isLocked) displayName = "\uD83D\uDD12 " + displayName;
        if (node.isHidden) displayName = "\uD83D\uDC41 " + displayName;

        nameView.setText(displayName);
        nameView.setTextSize(16);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);

        boolean isSelected = node.view == selectedWidgetView;
        nameView.setTypeface(null, Typeface.BOLD); // Always bold as requested
        if (isSelected) {
            nameView.setTextColor(Color.parseColor("#2196F3"));
            card.setStrokeColor(Color.parseColor("#2196F3"));
            card.setStrokeWidth(4);
            card.setCardBackgroundColor(Color.parseColor("#1A2196F3"));
        } else if (node.depth == 0) {
            nameView.setTextColor(Color.parseColor("#CFD8DC"));
            card.setStrokeColor(Color.parseColor("#37474F"));
            card.setStrokeWidth(2);
            card.setCardBackgroundColor(Color.parseColor("#0D37474F"));
        } else {
            nameView.setTextColor(Color.parseColor("#CFD8DC"));
            card.setStrokeColor(Color.parseColor("#E0E0E0"));
            card.setStrokeWidth(2);
            card.setCardBackgroundColor(Color.parseColor("#FFFFFF")); // Ensure light background for dark text, or match theme
            nameView.setTextColor(Color.parseColor("#333333")); // Use dark text for contrast

            // Adjust infoView and connector text colors if they exist
            arrow.setTextColor(Color.parseColor("#757575"));
        }
        nameCol.addView(nameView);

        // Show id/class info if present
        if (!node.id.isEmpty() || !node.cssClass.isEmpty()) {
            TextView infoView = new TextView(context);
            StringBuilder info = new StringBuilder();
            if (!node.id.isEmpty()) info.append("#").append(node.id);
            if (!node.cssClass.isEmpty()) {
                if (info.length() > 0) info.append(" ");
                info.append(".").append(node.cssClass);
            }
            infoView.setText(info.toString());
            infoView.setTextSize(12);
            infoView.setTextColor(Color.parseColor("#78909C"));
            infoView.setSingleLine(true);
            infoView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nameCol.addView(infoView);
        }

        layout.addView(nameCol);

        // Click to select widget
        layout.setOnClickListener(v -> {
            if (clickListener != null && node.depth > 0) {
                clickListener.onItemClick(node.view);
            }
        });

        // Long click for context actions
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

    private int getTagColor(String tag) {
        switch (tag) {
            case "div": case "section": return Color.parseColor("#42A5F5");
            case "header": case "footer": case "nav": return Color.parseColor("#26A69A");
            case "p": case "h1": case "h2": case "h3": case "span": return Color.parseColor("#FFCA28");
            case "button": return Color.parseColor("#FFA726");
            case "img": return Color.parseColor("#AB47BC");
            case "input": case "textarea": case "select": return Color.parseColor("#66BB6A");
            case "a": return Color.parseColor("#42A5F5");
            case "form": return Color.parseColor("#26C6DA");
            case "ul": case "ol": case "li": return Color.parseColor("#78909C");
            case "video": case "audio": return Color.parseColor("#EF5350");
            case "table": case "tr": case "td": case "th": return Color.parseColor("#8D6E63");
            case "label": return Color.parseColor("#FFCA28");
            case "hr": return Color.parseColor("#90A4AE");
            case "iframe": return Color.parseColor("#7E57C2");
            case "canvas": case "svg": return Color.parseColor("#EC407A");
            default: return Color.parseColor("#90A4AE");
        }
    }

    static class TreeNode {
        View view;
        int depth;
        String tag;
        String name;
        String id = "";
        String cssClass = "";
        boolean isContainer;
        int childCount;
        int nodeId;
        boolean isLocked;
        boolean isHidden;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View v) { super(v); }
    }
}
