package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
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
        View oldSelected = this.selectedWidgetView;
        this.selectedWidgetView = view;

        // Only rebind items whose selection state actually changed (avoids full refresh)
        for (int i = 0; i < flatList.size(); i++) {
            View nodeView = flatList.get(i).view;
            if (nodeView == oldSelected || nodeView == view) {
                notifyItemChanged(i);
            }
        }
    }

    public void setFilter(String query) {
        this.filterQuery = query != null ? query.toLowerCase() : "";
        if (rootScreen != null) {
            buildTree(rootScreen);
        }
    }

    public void buildTree(ViewGroup screen) {
        this.rootScreen = screen;

        // Capture current list before rebuild for DiffUtil comparison
        List<TreeNode> oldList = new ArrayList<>(flatList);

        flatList.clear();
        if (screen != null) {
            addNode(screen, 0, "body");
        }

        // Diff old vs new and animate changes. Empty-tree edge cases (initial
        // load with no widgets) still need dispatchUpdatesTo so the adapter
        // notices itemCount went 0 -> N when widgets land later.
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new TreeDiffCallback(oldList, flatList, selectedWidgetView), true);
        diffResult.dispatchUpdatesTo(this);
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
                if (fromPos < 0 || toPos < 0) return false;
                if (fromPos >= flatList.size() || toPos >= flatList.size()) return false;

                TreeNode fromNode = flatList.get(fromPos);
                TreeNode toNode = flatList.get(toPos);

                // Don't move locked nodes
                if (fromNode.isLocked) return false;

                // Perform the actual view reparenting/reorder
                if (fromNode.view != null && fromNode.view.getParent() instanceof ViewGroup) {
                    ViewGroup fromParent = (ViewGroup) fromNode.view.getParent();

                    // Determine target parent and index
                    ViewGroup targetParent;
                    int targetIndex;

                    if (toNode.isContainer && toNode.depth >= fromNode.depth) {
                        // Moving INTO a container - add as first child
                        targetParent = (ViewGroup) toNode.view;
                        targetIndex = 0;
                    } else if (toNode.view.getParent() instanceof ViewGroup) {
                        // Moving BESIDE a sibling
                        targetParent = (ViewGroup) toNode.view.getParent();
                        targetIndex = targetParent.indexOfChild(toNode.view);
                    } else {
                        return false;
                    }

                    // Prevent dropping into own children
                    if (isDescendantOf(targetParent, fromNode.view)) {
                        return false;
                    }

                    fromParent.removeView(fromNode.view);

                    // Recalculate index after removal
                    targetIndex = Math.min(targetIndex, targetParent.getChildCount());
                    targetParent.addView(fromNode.view, Math.max(0, targetIndex));

                    if (reorderListener != null) {
                        reorderListener.onReorder(fromNode.view, targetParent, targetIndex);
                    }
                }

                // Animate the swap in the flat list without a full rebind
                if (fromPos >= 0 && toPos >= 0 && fromPos < flatList.size() && toPos < flatList.size()) {
                    Collections.swap(flatList, fromPos, toPos);
                    notifyItemMoved(fromPos, toPos);
                    // Full rebuild (with DiffUtil) happens in clearView after drag ends
                }
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // Rebuild tree with DiffUtil after drag ends to sync depths/structure
                if (rootScreen != null) {
                    buildTree(rootScreen);
                }
            }
        });
        touchHelper.attachToRecyclerView(rv);
    }

    private boolean isDescendantOf(View parent, View potentialAncestor) {
        View current = parent;
        while (current != null) {
            if (current == potentialAncestor) return true;
            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
        }
        return false;
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
        String textPreview = "";
        boolean isContainer = view instanceof ViewGroup;
        boolean isLocked = false;
        boolean isHidden = false;

        if (view.getTag() instanceof Map) {
            @SuppressWarnings("unchecked")
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
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
            if (function != null) {
                if (function.containsKey("id") && id.isEmpty()) {
                    id = function.get("id").toString();
                }
                if (function.containsKey("class")) {
                    cssClass = function.get("class").toString();
                }
                if (function.containsKey("text")) {
                    textPreview = function.get("text").toString();
                    if (textPreview.length() > 20) textPreview = textPreview.substring(0, 20) + "...";
                }
            }
            if (name == null) {
                name = tag;
            }
        }

        // Apply filter
        if (!filterQuery.isEmpty() && depth > 0) {
            String searchable = (name + " " + tag + " " + id + " " + cssClass + " " + textPreview).toLowerCase();
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
        node.textPreview = textPreview;
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
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(12, 6, 12, 6);
        layout.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new ViewHolder(layout);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        TreeNode node = flatList.get(position);
        LinearLayout layout = (LinearLayout) holder.itemView;
        layout.removeAllViews();

        boolean isSelected = node.view == selectedWidgetView;
        boolean isRoot = node.depth == 0;

        int indentPx = node.depth * 24;

        LinearLayout cardRow = new LinearLayout(context);
        cardRow.setOrientation(LinearLayout.HORIZONTAL);
        cardRow.setGravity(Gravity.CENTER_VERTICAL);
        cardRow.setPadding(12, 10, 12, 10);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(indentPx, 2, 4, 2);
        cardRow.setLayoutParams(cardParams);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(16);

        if (isSelected) {
            cardBg.setColor(Color.parseColor("#1A2196F3"));
            cardBg.setStroke(2, Color.parseColor("#2196F3"));
        } else if (isRoot) {
            cardBg.setColor(Color.parseColor("#1A6750A4"));
            cardBg.setStroke(2, Color.parseColor("#6750A4"));
        } else if (node.isContainer) {
            cardBg.setColor(Color.parseColor("#0D37474F"));
            cardBg.setStroke(1, Color.parseColor("#49454F"));
        } else {
            cardBg.setColor(Color.TRANSPARENT);
            cardBg.setStroke(1, Color.parseColor("#36343B"));
        }
        cardRow.setBackground(cardBg);

        // Collapse/expand arrow for containers
        if (node.isContainer && node.childCount > 0) {
            TextView arrow = new TextView(context);
            arrow.setTextSize(14);
            boolean collapsed = collapsedNodes.contains(node.nodeId);
            arrow.setText(collapsed ? "\u25B6" : "\u25BC");
            arrow.setTextColor(getTagColor(node.tag));
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(
                28, ViewGroup.LayoutParams.WRAP_CONTENT);
            arrowParams.setMargins(0, 0, 6, 0);
            arrow.setLayoutParams(arrowParams);
            arrow.setGravity(Gravity.CENTER);
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
            cardRow.addView(arrow);
        } else {
            View spacer = new View(context);
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(28, 1);
            spacerParams.setMargins(0, 0, 6, 0);
            spacer.setLayoutParams(spacerParams);
            cardRow.addView(spacer);
        }

        // Tag color indicator (rounded pill)
        TextView tagBadge = new TextView(context);
        tagBadge.setTextSize(10);
        tagBadge.setTypeface(null, Typeface.BOLD);
        tagBadge.setTextColor(Color.WHITE);
        tagBadge.setGravity(Gravity.CENTER);
        tagBadge.setPadding(10, 2, 10, 2);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(12);
        badgeBg.setColor(getTagColor(node.tag));
        tagBadge.setBackground(badgeBg);

        String badgeText = isRoot ? "body" : node.tag;
        tagBadge.setText(badgeText);

        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.setMargins(0, 0, 8, 0);
        tagBadge.setLayoutParams(badgeParams);
        cardRow.addView(tagBadge);

        // Name + info column
        LinearLayout nameCol = new LinearLayout(context);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameColParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameCol.setLayoutParams(nameColParams);

        TextView nameView = new TextView(context);
        StringBuilder displayName = new StringBuilder();

        if (node.isLocked) displayName.append("\uD83D\uDD12 ");
        if (node.isHidden) displayName.append("\uD83D\uDC41 ");

        if (!isRoot) {
            displayName.append("<").append(node.tag).append(">");
            if (!node.id.isEmpty()) {
                displayName.append(" #").append(node.id);
            }
            if (!node.cssClass.isEmpty()) {
                String shortClass = node.cssClass.length() > 15 ? node.cssClass.substring(0, 15) + ".." : node.cssClass;
                displayName.append(" .").append(shortClass);
            }
        } else {
            displayName.append("body");
        }

        if (node.isContainer && node.childCount > 0) {
            displayName.append("  (").append(node.childCount).append(")");
        }

        nameView.setText(displayName.toString());
        nameView.setTextSize(14);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameView.setTypeface(null, Typeface.BOLD);

        if (isSelected) {
            nameView.setTextColor(Color.parseColor("#2196F3"));
        } else if (isRoot) {
            nameView.setTextColor(Color.parseColor("#D0BCFF"));
        } else if (node.isContainer) {
            nameView.setTextColor(Color.parseColor("#E6E1E5"));
        } else {
            nameView.setTextColor(Color.parseColor("#CAC4D0"));
        }
        nameCol.addView(nameView);

        if (!node.textPreview.isEmpty()) {
            TextView previewView = new TextView(context);
            previewView.setText("\"" + node.textPreview + "\"");
            previewView.setTextSize(11);
            previewView.setTextColor(Color.parseColor("#938F99"));
            previewView.setSingleLine(true);
            previewView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nameCol.addView(previewView);
        }

        cardRow.addView(nameCol);

        // More options button
        if (!isRoot) {
            TextView moreBtn = new TextView(context);
            moreBtn.setText("\u22EE");
            moreBtn.setTextSize(18);
            moreBtn.setTextColor(Color.parseColor("#A8A4AE"));
            moreBtn.setPadding(10, 2, 10, 2);
            moreBtn.setGravity(Gravity.CENTER);
            GradientDrawable moreBg = new GradientDrawable();
            moreBg.setColor(Color.parseColor("#14363A40"));
            moreBg.setCornerRadius(10);
            moreBtn.setBackground(moreBg);
            moreBtn.setOnClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onItemLongClick(node.view);
                }
            });
            cardRow.addView(moreBtn);
        }

        layout.setPadding(0, 0, 0, 0);
        layout.addView(cardRow);

        cardRow.setOnClickListener(v -> {
            if (clickListener != null && !isRoot) {
                clickListener.onItemClick(node.view);
            }
        });

        cardRow.setOnLongClickListener(v -> {
            if (longClickListener != null && !isRoot) {
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
            case "main": case "article": case "aside": return Color.parseColor("#5C6BC0");
            case "p": case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
            case "span": return Color.parseColor("#FFCA28");
            case "button": return Color.parseColor("#FFA726");
            case "img": return Color.parseColor("#AB47BC");
            case "input": case "textarea": case "select": return Color.parseColor("#66BB6A");
            case "a": return Color.parseColor("#42A5F5");
            case "form": return Color.parseColor("#26C6DA");
            case "ul": case "ol": case "li": return Color.parseColor("#78909C");
            case "video": case "audio": return Color.parseColor("#EF5350");
            case "table": case "tr": case "td": case "th": return Color.parseColor("#8D6E63");
            case "label": return Color.parseColor("#FFCA28");
            case "hr": case "br": return Color.parseColor("#90A4AE");
            case "iframe": return Color.parseColor("#7E57C2");
            case "canvas": case "svg": return Color.parseColor("#EC407A");
            case "pre": case "blockquote": return Color.parseColor("#78909C");
            case "body": case "unknown": return Color.parseColor("#6750A4");
            default: return Color.parseColor("#90A4AE");
        }
    }

    // -------------------------------------------------------------------------
    // DiffUtil Callback
    // -------------------------------------------------------------------------

    /**
     * Compares two snapshots of the flat hierarchy list so RecyclerView can
     * animate inserts, removes, and moves without a full rebind.
     */
    static class TreeDiffCallback extends DiffUtil.Callback {

        private final List<TreeNode> oldList;
        private final List<TreeNode> newList;
        @Nullable private final View selectedView;

        TreeDiffCallback(List<TreeNode> oldList, List<TreeNode> newList, @Nullable View selectedView) {
            this.oldList = oldList;
            this.newList = newList;
            this.selectedView = selectedView;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        /** Two nodes represent the same widget if they wrap the same View instance. */
        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            return oldList.get(oldPos).nodeId == newList.get(newPos).nodeId;
        }

        /**
         * Content equality: checks all visible properties including selection state,
         * depth, tag, id, class, text preview, and structural flags.
         */
        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            TreeNode o = oldList.get(oldPos);
            TreeNode n = newList.get(newPos);
            boolean oSel = (o.view == selectedView);
            boolean nSel = (n.view == selectedView);
            return oSel == nSel
                    && o.depth == n.depth
                    && o.tag.equals(n.tag)
                    && o.name.equals(n.name)
                    && o.id.equals(n.id)
                    && o.cssClass.equals(n.cssClass)
                    && o.textPreview.equals(n.textPreview)
                    && o.isContainer == n.isContainer
                    && o.childCount == n.childCount
                    && o.isLocked == n.isLocked
                    && o.isHidden == n.isHidden;
        }
    }

    // -------------------------------------------------------------------------
    // Data classes
    // -------------------------------------------------------------------------

    static class TreeNode {
        View view;
        int depth;
        String tag;
        String name;
        String id = "";
        String cssClass = "";
        String textPreview = "";
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
