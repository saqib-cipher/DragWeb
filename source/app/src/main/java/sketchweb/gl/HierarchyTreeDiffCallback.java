package sketchweb.gl;

import androidx.recyclerview.widget.DiffUtil;
import java.util.List;

public class HierarchyTreeDiffCallback extends DiffUtil.Callback {
    private final List<HierarchyTreeAdapter.TreeNode> oldList;
    private final List<HierarchyTreeAdapter.TreeNode> newList;

    public HierarchyTreeDiffCallback(List<HierarchyTreeAdapter.TreeNode> oldList, List<HierarchyTreeAdapter.TreeNode> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() { return oldList.size(); }

    @Override
    public int getNewListSize() { return newList.size(); }

    @Override
    public boolean areItemsTheSame(int oldPos, int newPos) {
        return oldList.get(oldPos).nodeId == newList.get(newPos).nodeId;
    }

    @Override
    public boolean areContentsTheSame(int oldPos, int newPos) {
        HierarchyTreeAdapter.TreeNode oldItem = oldList.get(oldPos);
        HierarchyTreeAdapter.TreeNode newItem = newList.get(newPos);
        return oldItem.depth == newItem.depth &&
               oldItem.tag.equals(newItem.tag) &&
               oldItem.name.equals(newItem.name) &&
               oldItem.id.equals(newItem.id) &&
               oldItem.cssClass.equals(newItem.cssClass) &&
               oldItem.textPreview.equals(newItem.textPreview) &&
               oldItem.isContainer == newItem.isContainer &&
               oldItem.childCount == newItem.childCount &&
               oldItem.isLocked == newItem.isLocked &&
               oldItem.isHidden == newItem.isHidden;
    }
}
