package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.RotateAnimation;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
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
    private boolean isLoading = false;

    public void setLoading(boolean isLoading) {
        this.isLoading = isLoading;
        if (isLoading) {
            flatList.clear();
            notifyDataSetChanged();
        } else if (rootScreen != null) {
            buildTree(rootScreen);
        }
    }

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
        if (isLoading) {
            return;
        }
        flatList.clear();
        for (int i = 0; i < screen.getChildCount(); i++) {
            addNode(screen.getChildAt(i), 0, null);
        }
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

                if (fromPos == 0 || toPos == 0) return false;
                if (fromPos < 0 || toPos < 0) return false;
                if (fromPos >= flatList.size() || toPos >= flatList.size()) return false;

                TreeNode fromNode = flatList.get(fromPos);
                TreeNode toNode = flatList.get(toPos);

                if (fromNode.isLocked) return false;
                if (fromNode.view == null || !(fromNode.view.getParent() instanceof ViewGroup)) {
                    return false;
                }

                ViewGroup fromParent = (ViewGroup) fromNode.view.getParent();
                ViewGroup targetParent;
                int targetIndex;

                boolean movingDown = toPos > fromPos;
                if (toNode.isContainer && toNode.view != fromNode.view) {
                    targetParent = (ViewGroup) toNode.view;
                    targetIndex = movingDown ? 0 : targetParent.getChildCount();
                } else if (toNode.view.getParent() instanceof ViewGroup) {
                    targetParent = (ViewGroup) toNode.view.getParent();
                    int siblingIdx = targetParent.indexOfChild(toNode.view);
                    targetIndex = movingDown ? siblingIdx + 1 : siblingIdx;
                } else {
                    return false;
                }

                if (isDescendantOf(targetParent, fromNode.view)) {
                    return false;
                }

                // Reparent the view.
                int prevIndexInSameParent = -1;
                if (fromParent == targetParent) {
                    prevIndexInSameParent = fromParent.indexOfChild(fromNode.view);
                }
                fromParent.removeView(fromNode.view);
                if (prevIndexInSameParent != -1 && prevIndexInSameParent < targetIndex) {
                    targetIndex--;
                }
                targetIndex = Math.max(0, Math.min(targetIndex, targetParent.getChildCount()));
                targetParent.addView(fromNode.view, targetIndex);

                if (reorderListener != null) {
                    reorderListener.onReorder(fromNode.view, targetParent, targetIndex);
                }

                // Rebuild immediately so depth, child counts, and arrows refresh
                // in real time during the drag.
                if (rootScreen != null) {
                    buildTree(rootScreen);
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
        // Wrapper LinearLayout so we can indent the M3 card via margins.
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(20);
        card.setCardElevation(0);
        card.setStrokeWidth(1);
        card.setUseCompatPadding(false);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(8, 4, 8, 4);
        card.setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(12, 10, 12, 10);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(row);
        wrapper.addView(card);

        ViewHolder vh = new ViewHolder(wrapper);
        vh.card = card;
        vh.row = row;
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        LinearLayout row = holder.row;
        MaterialCardView card = holder.card;
        row.removeAllViews();

        float density = context.getResources().getDisplayMetrics().density;

        if (isLoading) {
            int dp16 = (int) (16 * density);
            int dp8 = (int) (8 * density);
            int dp12 = (int) (12 * density);

            ((LinearLayout.LayoutParams) card.getLayoutParams()).setMargins(dp8, dp8, dp8, dp8);

            card.setCardBackgroundColor(Color.parseColor("#1437474F"));
            card.setStrokeColor(Color.parseColor("#33FFFFFF"));
            card.setClickable(false);
            card.setFocusable(false);

            TextView tv = new TextView(context);
            tv.setText("Loading layout hierarchy...");
            tv.setTextColor(Color.parseColor("#A8A4AE"));
            tv.setTextSize(14);
            tv.setTypeface(null, Typeface.ITALIC);
            row.addView(tv);
            return;
        }

        if (position < 0 || position >= flatList.size()) return;

        TreeNode node = flatList.get(position);

        boolean isSelected = node.view == selectedWidgetView;
        boolean isRoot = false; // body is excluded, so all cards represent editable child views
        int indentPx = node.depth * 20; // 20dp indentation per level

        // Keep card width constant to match the hierarchy panel
        ((LinearLayout.LayoutParams) card.getLayoutParams()).setMargins(8, 4, 8, 4);

        // Apply indentation using row padding instead of card margins
        row.setPadding(
            (int)((16 + indentPx) * density), 
            (int)(14 * density), 
            (int)(16 * density), 
            (int)(14 * density)
        );

        // Material 3 card surface — tonal fill + outline + ripple.
        int fill, stroke, textColor;
        if (isSelected) {
            fill = Color.parseColor("#332196F3");
            stroke = Color.parseColor("#2196F3");
            textColor = Color.parseColor("#2196F3");
        } else if (node.isContainer) {
            fill = Color.parseColor("#1437474F");
            stroke = Color.parseColor("#49454F");
            textColor = Color.parseColor("#E6E1E5");
        } else {
            fill = Color.parseColor("#0C1F1B24");
            stroke = Color.parseColor("#36343B");
            textColor = Color.parseColor("#CAC4D0");
        }
        card.setCardBackgroundColor(fill);
        card.setStrokeColor(stroke);
        card.setRippleColor(ColorStateList.valueOf(Color.parseColor("#332196F3")));
        card.setClickable(true);
        card.setFocusable(true);

        // Collapse/expand arrow using the custom vector icon
        if (node.isContainer && node.childCount > 0) {
            ImageView arrow = new ImageView(context);
            arrow.setImageResource(R.drawable.rounded_arrow_drop_down_24);
            arrow.setImageTintList(ColorStateList.valueOf(textColor));
            
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(
                (int)(24 * density), (int)(24 * density));
            arrowParams.setMargins(0, 0, (int)(6 * density), 0);
            arrow.setLayoutParams(arrowParams);

            boolean collapsed = collapsedNodes.contains(node.nodeId);
            arrow.setRotation(collapsed ? -90f : 0f);
            row.addView(arrow);
        } else {
            View spacer = new View(context);
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                (int)(24 * density), 1);
            spacerParams.setMargins(0, 0, (int)(6 * density), 0);
            spacer.setLayoutParams(spacerParams);
            row.addView(spacer);
        }

        // Tag badge
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
        tagBadge.setText(node.tag);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.setMargins(0, 0, 8, 0);
        tagBadge.setLayoutParams(badgeParams);
        row.addView(tagBadge);

        // Name + preview column
        LinearLayout nameCol = new LinearLayout(context);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameColParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameCol.setLayoutParams(nameColParams);

        TextView nameView = new TextView(context);
        StringBuilder displayName = new StringBuilder();
        displayName.append("<").append(node.tag).append(">");
        if (!node.id.isEmpty()) displayName.append(" #").append(node.id);
        if (!node.cssClass.isEmpty()) {
            String shortClass = node.cssClass.length() > 15
                ? node.cssClass.substring(0, 15) + ".." : node.cssClass;
            displayName.append(" .").append(shortClass);
        }
        if (node.isContainer && node.childCount > 0) {
            displayName.append("  (").append(node.childCount).append(")");
        }
        nameView.setText(displayName.toString());
        nameView.setTextSize(14);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameView.setTypeface(null, Typeface.BOLD);
        nameView.setTextColor(textColor);
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

        row.addView(nameCol);

        // Lock / Hidden status indicators
        if (node.isLocked) {
            ImageView lockIcon = new ImageView(context);
            lockIcon.setImageResource(R.drawable.lock);
            lockIcon.setImageTintList(ColorStateList.valueOf(textColor));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int)(16 * density), (int)(16 * density));
            lp.setMarginEnd((int)(8 * density));
            lockIcon.setLayoutParams(lp);
            row.addView(lockIcon);
        }
        if (node.isHidden) {
            ImageView eyeIcon = new ImageView(context);
            eyeIcon.setImageResource(R.drawable.eye_closed);
            eyeIcon.setImageTintList(ColorStateList.valueOf(textColor));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int)(16 * density), (int)(16 * density));
            lp.setMarginEnd((int)(8 * density));
            eyeIcon.setLayoutParams(lp);
            row.addView(eyeIcon);
        }

        // More options
        TextView moreBtn = new TextView(context);
        moreBtn.setText("⋮");
        moreBtn.setTextSize(18);
        moreBtn.setTextColor(Color.parseColor("#A8A4AE"));
        moreBtn.setPadding(10, 2, 10, 2);
        moreBtn.setGravity(Gravity.CENTER);
        GradientDrawable moreBg = new GradientDrawable();
        moreBg.setColor(Color.parseColor("#14363A40"));
        moreBg.setCornerRadius(10);
        moreBtn.setBackground(moreBg);
        moreBtn.setOnClickListener(v -> {
            if (longClickListener != null) longClickListener.onItemLongClick(node.view);
        });
        row.addView(moreBtn);

        card.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onItemClick(node.view);
            }
            if (node.isContainer && node.childCount > 0) {
                boolean nowCollapsed = !collapsedNodes.contains(node.nodeId);
                if (nowCollapsed) {
                    collapsedNodes.add(node.nodeId);
                } else {
                    collapsedNodes.remove(node.nodeId);
                }
                if (rootScreen != null) {
                    buildTree(rootScreen);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        if (isLoading) return 1;
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
        MaterialCardView card;
        LinearLayout row;
        ViewHolder(View v) { super(v); }
    }
}
