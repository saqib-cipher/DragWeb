1. **Hierarchy Tree Performance & UX (DiffUtil)**
   - Create `HierarchyTreeDiffCallback` class extending `DiffUtil.Callback`.
   - Update `HierarchyTreeAdapter.java`:
     - Change `buildTree` to create a new list of `TreeNode`s, use `DiffUtil.calculateDiff` to compare the old and new lists, and dispatch updates (`diffResult.dispatchUpdatesTo(this)`).
     - Update drag & drop logic to smoothly sync data using `DiffUtil` or `notifyItemMoved` avoiding full rebuilds.
     - Add necessary unique IDs to `TreeNode` to allow `DiffUtil` to track items.

2. **Logic System & Blocks.json Redesign**
   - Update `blocks.json` in `source/app/src/main/assets/`:
     - Remove JS-heavy blocks (`ifBlock`, `compareEqual`, `variable`, etc.).
     - Keep and enhance CSS blocks (`setDisplay`, `setPosition`, `setColor`, etc.).
     - Keep HTML attribute blocks (`setHref`, `setSrc`, etc.).
     - Change events from `onPageLoad`, `onClick` to CSS pseudo-classes `hover`, `focus`, `active`.
   - Update `ExportManager.java`:
     - Modify `generateJs` to not output old logic script but rely on pure HTML/CSS.
   - Update `LogicBlockManager.java` and `LogicBlockActivity.java` to handle the new blocks structure.

3. **Backup & Import Fixes**
   - Update `ProjectDataManager.java`:
     - Ensure `saveProject` writes cleanly.
     - Fix `exportSingleProjectAsZip` and `importProjectsFromZip` to cleanly use a ZIP system.
     - Ensure redundant `layout.json`/`index.json` are not duplicated. Ensure paths are correctly mapped on load to restore the full project state.
     - Remove duplication of single/multiple zips if needed, ensure it covers pages, styles, assets, widgets cleanly.

4. **Preview System Upgrade**
   - Update `PreviewActivity.java`:
     - Use a full project renderer (via WebView).
     - Ensure all HTML, CSS, images, and pages are loaded correctly.
     - Support multi-page navigation inside the WebView.

5. **Pre-commit Steps**
   - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
