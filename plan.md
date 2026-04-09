1. **Refactor Block and Event Categories in `LogicBlockActivity.java` and `LogicBlockManager.java`**
   - Keep current Sketchware-style vertical connection design (puzzle logic blocks).
   - Update `BlockDef` categories in `LogicBlockActivity.java` to strongly focus on Web, Events, CSS, HTML, Logic, Variables as described. Add specific missing blocks like `onVisible`, `onHidden`, `onDestroy`.
   - Update `LogicBlockManager.java` code generation: `generateJavaScript()` to handle the page-level event scopes and DOM manipulation logic seamlessly.

2. **Add Missing HTML/CSS Actions in `LogicBlockManager.java`**
   - Ensure mapping of setHref, setHTML, show/hide, createElement, append/prepend, and all CSS manipulation.
   - Refactor generateActionJs to use direct style apply or attribute edits.

3. **Improve Preview & Undo/Redo System**
   - Replaced bottom sheet preview with a Full Activity (`PreviewFullscreenActivity.java`). (Done)
   - Ensure Undo/Redo mechanism correctly handles block insertion/deletion tracking in `LogicBlockActivity.java`.

4. **Enhance Variables & Logic Actions**
   - Check and fix variable blocks mapping (create/set/get with types).
   - Ensure basic loops and conditionals generate standard JS correctly.

5. **Add Pre Commit checks**
   - Run tests and verifications before pushing.
