package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HierarchyTreeAdapter extends RecyclerView.Adapter<HierarchyTreeAdapter.ViewHolder> {

    private static final int BG = 0xFF1B1C1E;
    private static final int SURFACE = 0xFF25262D;
    private static final int SURFACE_VARIANT = 0xFF2F3038;
    private static final int OUTLINE = 0x14FFFFFF;
    private static final int PRIMARY = 0xFFC79743;
    private static final int TEXT_PRIMARY = 0xFFF5F5F5;
    private static final int TEXT_SECONDARY = 0xFFB0B0B0;
    private static final int RIPPLE_COLOR = 0x1AF5F5F5;

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
    private float density;

    public void setLoading(boolean loading) {
        this.isLoading = loading;
        if (loading) {
            flatList.clear();
            notifyDataSetChanged();
        } else if (rootScreen != null) {
            buildTree(rootScreen);
        }
    }

    public HierarchyTreeAdapter(Context context) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
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
        View prev = this.selectedWidgetView;
        this.selectedWidgetView = view;
        int prevIdx = findNodeIndex(prev);
        int newIdx = findNodeIndex(view);
        if (prevIdx >= 0) notifyItemChanged(prevIdx);
        if (newIdx >= 0 && newIdx != prevIdx) notifyItemChanged(newIdx);
    }

    public void setFilter(String query) {
        this.filterQuery = query != null ? query.toLowerCase() : "";
        if (rootScreen != null) buildTree(rootScreen);
    }

    public void buildTree(ViewGroup screen) {
        this.rootScreen = screen;
        if (isLoading) return;
        flatList.clear();
        for (int i = 0; i < screen.getChildCount(); i++) {
            addNode(screen.getChildAt(i), 0, null);
        }
        notifyDataSetChanged();
    }

    public void attachToRecyclerView(RecyclerView rv) {
        rv.setItemAnimator(new MaterialItemAnimator());
        rv.setBackgroundColor(BG);

        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();

                if (fromPos <= 0 || toPos <= 0) return false;
                if (fromPos < 0 || toPos < 0) return false;
                if (fromPos >= flatList.size() || toPos >= flatList.size()) return false;

                TreeNode fromNode = flatList.get(fromPos);
                TreeNode toNode = flatList.get(toPos);

                if (fromNode.isLocked || fromNode.view == null) return false;
                if (!(fromNode.view.getParent() instanceof ViewGroup)) return false;

                ViewGroup fromParent = (ViewGroup) fromNode.view.getParent();
                ViewGroup targetParent;
                int targetIndex;

                boolean movingDown = toPos > fromPos;
                if (toNode.isContainer && toNode.view != fromNode.view) {
                    targetParent = (ViewGroup) toNode.view;
                    targetIndex = movingDown ? 0 : targetParent.getChildCount();
                } else if (toNode.view.getParent() instanceof ViewGroup) {
                    targetParent = (ViewGroup) toNode.view.getParent();
                    targetIndex = targetParent.indexOfChild(toNode.view);
                    if (movingDown) targetIndex++;
                } else {
                    return false;
                }

                if (isDescendantOf(targetParent, fromNode.view)) return false;

                int prevIdx = (fromParent == targetParent) ? fromParent.indexOfChild(fromNode.view) : -1;
                fromParent.removeView(fromNode.view);
                if (prevIdx >= 0 && prevIdx < targetIndex) targetIndex--;
                targetIndex = Math.max(0, Math.min(targetIndex, targetParent.getChildCount()));
                targetParent.addView(fromNode.view, targetIndex);

                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public boolean isLongPressDragEnabled() { return true; }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                if (reorderListener != null && flatList.size() > vh.getAdapterPosition() && vh.getAdapterPosition() >= 0) {
                    TreeNode node = flatList.get(vh.getAdapterPosition());
                    if (node.view != null && node.view.getParent() instanceof ViewGroup) {
                        ViewGroup p = (ViewGroup) node.view.getParent();
                        reorderListener.onReorder(node.view, p, p.indexOfChild(node.view));
                    }
                }
                if (rootScreen != null) buildTree(rootScreen);
            }
        });
        touchHelper.attachToRecyclerView(rv);
    }

    private boolean isDescendantOf(View parent, View potentialAncestor) {
        View current = parent;
        while (current != null) {
            if (current == potentialAncestor) return true;
            current = (current.getParent() instanceof View) ? (View) current.getParent() : null;
        }
        return false;
    }

    public void startDrag(RecyclerView.ViewHolder holder) {
        if (touchHelper != null) touchHelper.startDrag(holder);
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
            if (widgetMap.containsKey("tag")) tag = widgetMap.get("tag").toString();
            if (widgetMap.containsKey("id")) id = widgetMap.get("id").toString();
            if (widgetMap.containsKey("locked")) isLocked = Boolean.TRUE.equals(widgetMap.get("locked"));
            if (widgetMap.containsKey("hidden")) isHidden = Boolean.TRUE.equals(widgetMap.get("hidden"));
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
            if (function != null) {
                if (function.containsKey("id") && id.isEmpty()) id = function.get("id").toString();
                if (function.containsKey("class")) cssClass = function.get("class").toString();
                if (function.containsKey("text")) {
                    textPreview = function.get("text").toString();
                    if (textPreview.length() > 20) textPreview = textPreview.substring(0, 20) + "...";
                }
            }
            if (name == null) name = tag;
        }

        if (!filterQuery.isEmpty() && depth > 0) {
            String searchable = (name + " " + tag + " " + id + " " + cssClass + " " + textPreview).toLowerCase();
            if (!searchable.contains(filterQuery)) {
                if (isContainer) {
                    ViewGroup vg = (ViewGroup) view;
                    for (int i = 0; i < vg.getChildCount(); i++) addNode(vg.getChildAt(i), depth + 1, null);
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
        node.firstChildIdx = -1;
        node.treeChildCount = 0;

        int insertPos = flatList.size();
        flatList.add(node);

        if (isContainer && !collapsedNodes.contains(node.nodeId)) {
            node.firstChildIdx = flatList.size();
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                addNode(vg.getChildAt(i), depth + 1, null);
            }
            node.treeChildCount = flatList.size() - node.firstChildIdx;
        }
    }

    private int findNodeIndex(View view) {
        if (view == null) return -1;
        for (int i = 0; i < flatList.size(); i++) {
            if (flatList.get(i).view == view) return i;
        }
        return -1;
    }

    private void toggleCollapse(int position) {
        TreeNode node = flatList.get(position);
        if (!node.isContainer || node.childCount == 0) return;

        boolean isCollapsed = collapsedNodes.contains(node.nodeId);
        if (isCollapsed) {
            collapsedNodes.remove(node.nodeId);
            int insertAt = position + 1;
            List<TreeNode> children = new ArrayList<>();
            collectChildren((ViewGroup) node.view, node.depth + 1, children);
            node.firstChildIdx = insertAt;
            node.treeChildCount = children.size();
            flatList.addAll(insertAt, children);
            notifyItemRangeInserted(insertAt, children.size());
        } else {
            collapsedNodes.add(node.nodeId);
            int count = node.treeChildCount;
            if (count <= 0) {
                count = 0;
                for (int i = position + 1; i < flatList.size() && flatList.get(i).depth > node.depth; i++) count++;
            }
            int start = position + 1;
            int end = start + count;
            if (end > flatList.size()) end = flatList.size();
            int actualCount = end - start;
            if (actualCount > 0) {
                flatList.subList(start, end).clear();
                notifyItemRangeRemoved(start, actualCount);
            }
        }
    }

    private void collectChildren(ViewGroup parent, int depth, List<TreeNode> out) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int childIdx = out.size();
            TreeNode node = buildTreeNode(child, depth, null);
            out.add(node);
            if (node.isContainer && !collapsedNodes.contains(node.nodeId)) {
                node.firstChildIdx = childIdx + 1;
                int before = out.size();
                collectChildren((ViewGroup) child, depth + 1, out);
                node.treeChildCount = out.size() - before;
            }
        }
    }

    private TreeNode buildTreeNode(View view, int depth, String forceName) {
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
            if (widgetMap.containsKey("tag")) tag = widgetMap.get("tag").toString();
            if (widgetMap.containsKey("id")) id = widgetMap.get("id").toString();
            if (widgetMap.containsKey("locked")) isLocked = Boolean.TRUE.equals(widgetMap.get("locked"));
            if (widgetMap.containsKey("hidden")) isHidden = Boolean.TRUE.equals(widgetMap.get("hidden"));
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
            if (function != null) {
                if (function.containsKey("id") && id.isEmpty()) id = function.get("id").toString();
                if (function.containsKey("class")) cssClass = function.get("class").toString();
                if (function.containsKey("text")) {
                    textPreview = function.get("text").toString();
                    if (textPreview.length() > 20) textPreview = textPreview.substring(0, 20) + "...";
                }
            }
            if (name == null) name = tag;
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
        return node;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        MaterialCardView card = new MaterialCardView(context);
        card.setMinimumWidth((int)(280 * density));
        card.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        float r12 = 12 * density;
        card.setRadius(r12);
        card.setCardElevation(0);
        card.setStrokeWidth((int)(1 * density));
        card.setStrokeColor(ColorStateList.valueOf(OUTLINE));
        card.setCardBackgroundColor(SURFACE_VARIANT);
        card.setUseCompatPadding(false);
        card.setContentPadding(0, 0, 0, 0);
        card.setMinimumHeight((int)(48 * density));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(r12);
        mask.setColor(Color.WHITE);
        RippleDrawable ripple = new RippleDrawable(
            ColorStateList.valueOf(RIPPLE_COLOR), null, mask);
        card.setForeground(ripple);

        // Accent bar for containers (inserted at index 0 so row draws on top)
        View accentBar = new View(context);
        int abW = (int)(3 * density);
        FrameLayout.LayoutParams ablp = new FrameLayout.LayoutParams(
            abW, ViewGroup.LayoutParams.MATCH_PARENT);
        ablp.gravity = Gravity.LEFT;
        accentBar.setLayoutParams(ablp);
        accentBar.setBackgroundColor(PRIMARY);
        accentBar.setVisibility(View.GONE);
        card.addView(accentBar, 0);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.setPadding((int)(10 * density), 0, (int)(10 * density), 0);
        card.addView(row);

        // Chevron
        ImageView chevron = new ImageView(context);
        chevron.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            (int)(20 * density), (int)(20 * density));
        cp.setMargins(0, 0, (int)(6 * density), 0);
        chevron.setLayoutParams(cp);
        chevron.setImageResource(R.drawable.rounded_arrow_drop_down_24);
        chevron.setColorFilter(TEXT_PRIMARY);
        row.addView(chevron);

        // Chevron spacer
        View chevronSpacer = new View(context);
        chevronSpacer.setLayoutParams(new LinearLayout.LayoutParams(
            (int)(26 * density), 1));
        chevronSpacer.setVisibility(View.GONE);
        row.addView(chevronSpacer);

        // Icon badge
        TextView iconBadge = new TextView(context);
        int iconSize = (int)(22 * density);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(iconSize, iconSize);
        ip.setMargins(0, 0, (int)(8 * density), 0);
        iconBadge.setLayoutParams(ip);
        iconBadge.setGravity(Gravity.CENTER);
        iconBadge.setTextSize(10);
        iconBadge.setTypeface(null, Typeface.BOLD);
        iconBadge.setTextColor(Color.WHITE);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setSize(iconSize, iconSize);
        iconBadge.setBackground(iconBg);
        row.addView(iconBadge);

        // Content column
        LinearLayout contentCol = new LinearLayout(context);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        contentCol.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(contentCol);

        // Name row
        LinearLayout nameRow = new LinearLayout(context);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentCol.addView(nameRow);

        TextView nameText = new TextView(context);
        nameText.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nameText.setTextSize(15);
        nameText.setTypeface(null, Typeface.NORMAL);
        nameText.setSingleLine(true);
        nameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameText.setTextColor(TEXT_PRIMARY);
        nameRow.addView(nameText);

        // Info suffix
        TextView infoSuffix = new TextView(context);
        infoSuffix.setTextSize(13);
        infoSuffix.setTypeface(null, Typeface.NORMAL);
        infoSuffix.setTextColor(TEXT_SECONDARY);
        infoSuffix.setPadding((int)(4 * density), 0, 0, 0);
        nameRow.addView(infoSuffix);

        // Secondary info line
        TextView infoText = new TextView(context);
        infoText.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        infoText.setTextSize(11);
        infoText.setSingleLine(true);
        infoText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        infoText.setTextColor(TEXT_SECONDARY);
        infoText.setVisibility(View.GONE);
        contentCol.addView(infoText);

        // Status icons container
        LinearLayout statusCol = new LinearLayout(context);
        statusCol.setOrientation(LinearLayout.HORIZONTAL);
        statusCol.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scp.setMargins(0, 0, (int)(4 * density), 0);
        statusCol.setLayoutParams(scp);
        row.addView(statusCol);

        // Lock icon
        ImageView lockIcon = new ImageView(context);
        int si = (int)(16 * density);
        LinearLayout.LayoutParams sip = new LinearLayout.LayoutParams(si, si);
        sip.setMargins(0, 0, (int)(4 * density), 0);
        lockIcon.setLayoutParams(sip);
        lockIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        lockIcon.setImageResource(R.drawable.lock);
        lockIcon.setColorFilter(TEXT_SECONDARY);
        lockIcon.setVisibility(View.GONE);
        statusCol.addView(lockIcon);

        // Hide icon
        ImageView hideIcon = new ImageView(context);
        hideIcon.setLayoutParams(new LinearLayout.LayoutParams(si, si));
        hideIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        hideIcon.setImageResource(R.drawable.eye_closed);
        hideIcon.setColorFilter(TEXT_SECONDARY);
        hideIcon.setVisibility(View.GONE);
        statusCol.addView(hideIcon);

        // More button
        ImageView moreBtn = new ImageView(context);
        int mbSize = (int)(28 * density);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(mbSize, mbSize);
        mp.setMargins((int)(4 * density), 0, 0, 0);
        moreBtn.setLayoutParams(mp);
        moreBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        moreBtn.setImageResource(R.drawable.dots_vertical);
        moreBtn.setColorFilter(TEXT_SECONDARY);
        moreBtn.setPadding((int)(4 * density), (int)(4 * density), (int)(4 * density), (int)(4 * density));
        GradientDrawable moreBg = new GradientDrawable();
        moreBg.setCornerRadius((int)(8 * density));
        moreBg.setColor(SURFACE);
        moreBtn.setBackground(moreBg);
        row.addView(moreBtn);

        return new ViewHolder(card, row, accentBar, chevron, chevronSpacer, iconBadge, iconBg,
            nameText, infoSuffix, infoText, statusCol, lockIcon, hideIcon, moreBtn);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (isLoading || position < 0 || position >= flatList.size()) return;

        TreeNode node = flatList.get(position);
        if (node.view == null) return;

        boolean isSelected = node.view == selectedWidgetView;
        boolean isCollapsed = collapsedNodes.contains(node.nodeId);
        boolean isContainer = node.isContainer && node.childCount > 0;

        // Card styling — containers get PRIMARY accent, leaves get subtle outline
        if (isSelected) {
            holder.card.setCardBackgroundColor(0x33C79743);
            holder.card.setStrokeColor(ColorStateList.valueOf(PRIMARY));
            holder.card.setStrokeWidth((int)(1.5f * density));
        } else if (isContainer) {
            holder.card.setCardBackgroundColor(SURFACE_VARIANT);
            holder.card.setStrokeColor(ColorStateList.valueOf(PRIMARY));
            holder.card.setStrokeWidth((int)(1 * density));
        } else {
            holder.card.setCardBackgroundColor(SURFACE_VARIANT);
            holder.card.setStrokeColor(ColorStateList.valueOf(OUTLINE));
            holder.card.setStrokeWidth((int)(1 * density));
        }
        holder.card.setMinimumHeight(isContainer ? (int)(52 * density) : (int)(44 * density));

        // Accent bar — visible only for containers
        holder.accentBar.setVisibility(isContainer ? View.VISIBLE : View.GONE);

        // Indentation — extra left offset for leaf items so they sit inside the parent card
        int indent = (int)(24 * node.depth * density);
        int leftPad = (int)(12 * density) + indent;
        if (node.depth > 0) leftPad += (int)(8 * density);
        int vertPad = isContainer ? (int)(10 * density) : (int)(6 * density);
        holder.row.setPadding(
            leftPad,
            vertPad,
            (int)(10 * density),
            vertPad);

        // Chevron
        boolean showChevron = node.isContainer && node.childCount > 0;
        holder.chevron.setVisibility(showChevron ? View.VISIBLE : View.GONE);
        holder.chevronSpacer.setVisibility(showChevron ? View.GONE : View.VISIBLE);
        if (showChevron) {
            float rotation = isCollapsed ? -90f : 0f;
            if (holder.chevron.getRotation() != rotation) {
                holder.chevron.animate()
                    .rotation(rotation)
                    .setDuration(200)
                    .setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f))
                    .start();
            }
        }

        // Icon badge
        int tagColor = getTagColor(node.tag);
        String firstLetter = node.tag.isEmpty() ? "?" : node.tag.substring(0, 1).toUpperCase();
        holder.iconBadge.setText(firstLetter);
        holder.iconBg.setColor(tagColor);

        // Name
        holder.nameText.setText(node.tag);
        if (!node.id.isEmpty()) {
            holder.infoSuffix.setText("#" + node.id);
            holder.infoSuffix.setVisibility(View.VISIBLE);
        } else {
            holder.infoSuffix.setVisibility(View.GONE);
        }

        // Info line
        StringBuilder info = new StringBuilder();
        if (node.isContainer && node.childCount > 0) {
            info.append(node.childCount).append(node.childCount == 1 ? " child" : " children");
        }
        if (!node.textPreview.isEmpty()) {
            if (info.length() > 0) info.append("  ");
            info.append("\"").append(node.textPreview).append("\"");
        }
        if (!node.cssClass.isEmpty()) {
            if (info.length() > 0) info.append("  ");
            info.append(".").append(node.cssClass);
        }
        if (info.length() > 0) {
            holder.infoText.setText(info.toString());
            holder.infoText.setVisibility(View.VISIBLE);
        } else {
            holder.infoText.setVisibility(View.GONE);
        }

        // Vertical margin between items
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) holder.card.getLayoutParams();
        mlp.bottomMargin = (int)(6 * density);
        mlp.leftMargin = 0;
        mlp.rightMargin = 0;

        // Lock icon
        holder.lockIcon.setVisibility(node.isLocked ? View.VISIBLE : View.GONE);

        // Hide icon
        holder.hideIcon.setVisibility(node.isHidden ? View.VISIBLE : View.GONE);

        // More button
        holder.moreBtn.setOnClickListener(v -> {
            if (longClickListener != null) longClickListener.onItemLongClick(node.view);
        });

        // Click: select + toggle collapse
        holder.card.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(node.view);
            if (node.isContainer && node.childCount > 0) {
                toggleCollapse(findNodeIndex(node.view));
            }
        });
    }

    @Override
    public int getItemCount() {
        return isLoading ? 1 : flatList.size();
    }

    private int getTagColor(String tag) {
        switch (tag) {
            case "div": case "section": return 0xFF42A5F5;
            case "header": case "footer": case "nav": return 0xFF26A69A;
            case "main": case "article": case "aside": return 0xFF5C6BC0;
            case "p": case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
            case "span": return 0xFFFFCA28;
            case "button": return 0xFFFFA726;
            case "img": return 0xFFAB47BC;
            case "input": case "textarea": case "select": return 0xFF66BB6A;
            case "a": return 0xFF42A5F5;
            case "form": return 0xFF26C6DA;
            case "ul": case "ol": case "li": return 0xFF78909C;
            case "video": case "audio": return 0xFFEF5350;
            case "table": case "tr": case "td": case "th": return 0xFF8D6E63;
            case "label": return 0xFFFFCA28;
            case "hr": case "br": return 0xFF90A4AE;
            case "iframe": return 0xFF7E57C2;
            case "canvas": case "svg": return 0xFFEC407A;
            case "pre": case "blockquote": return 0xFF78909C;
            case "body": case "unknown": return 0xFF6750A4;
            default: return 0xFF90A4AE;
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
        int firstChildIdx = -1;
        int treeChildCount;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        LinearLayout row;
        View accentBar;
        ImageView chevron;
        View chevronSpacer;
        TextView iconBadge;
        GradientDrawable iconBg;
        TextView nameText;
        TextView infoSuffix;
        TextView infoText;
        ViewGroup statusCol;
        ImageView lockIcon;
        ImageView hideIcon;
        ImageView moreBtn;

        ViewHolder(MaterialCardView c, LinearLayout r, View ab, ImageView ch, View cs,
                   TextView ib, GradientDrawable ibg, TextView nt, TextView isuf,
                   TextView it, ViewGroup sc, ImageView li, ImageView hi, ImageView mb) {
            super(c);
            card = c; row = r; accentBar = ab; chevron = ch; chevronSpacer = cs;
            iconBadge = ib; iconBg = ibg; nameText = nt; infoSuffix = isuf;
            infoText = it; statusCol = sc; lockIcon = li; hideIcon = hi; moreBtn = mb;
        }
    }

    private static class MaterialItemAnimator extends DefaultItemAnimator {
        MaterialItemAnimator() {
            setAddDuration(250);
            setRemoveDuration(250);
            setMoveDuration(250);
            setChangeDuration(250);
        }
    }
}
