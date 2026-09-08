# Implementation Plan - DragWeb Refinement

This plan details the implementation of requested improvements across the DragWeb application: Home Activity, Website Import System, Main Activity (Canvas Editor & Theme settings), and Logic Block Activity.

## User Review Required

> [!IMPORTANT]
> - **Font Settings Move**: We are removing Font Family configuration from the primary Theme Settings dialog and moving it to a standalone popup dialog selectable from the Theme options popup.
> - **Inline Styles Toggle**: We are adding an "Inline Styles" toggle in the Theme Settings. When turned off, it removes all auto-generated inline style attributes from exported files completely (and does not write them as `.el-tag-xxxx` rules in the external CSS), ensuring a completely clean and stylesheet-driven export.

## Proposed Changes

---

### Home Activity & Website Import

#### [MODIFY] [dialog_new_project.xml](file:///c:/Users/sddrk/AndroidStudioProjects/DragWeb/app/src/main/res/layout/dialog_new_project.xml)
- Remove the logo selector container (containing the "Select Logo" button and path label).

#### [MODIFY] [HomeActivity.java](file:///c:/Users/sddrk/AndroidStudioProjects/DragWeb/app/src/main/java/sketchweb/gl/HomeActivity.java)
- Remove fields, registration launchers, and click listeners associated with the logo selection.
- Update `generateProjectId()` to scan existing projects in `getFilesDir()/projects/` and return the next formatted number ID: `project_01`, `project_02`, etc.
- In `createProjectFromImport()`, save imported logic blocks into the page's `.logic` file (`projectId_index.logic`).

#### [MODIFY] [HtmlCssImporter.java](file:///c:/Users/sddrk/AndroidStudioProjects/DragWeb/app/src/main/java/sketchweb/gl/HtmlCssImporter.java)
- Extract inline `<style>` stylesheet blocks from the HTML file (and/or external CSS content) and parse them.
- Map parsed CSS rules to the equivalent visual `changeStyle` logic blocks of type `immediate` and category `style`.
- Add a new field `logicBlocks` to `ImportResult` to carry these blocks.
- Prevent applying stylesheet/selector CSS properties as inline styles in the widgets' style map (leaving only actual `style="..."` attributes on the elements).

---

### Canvas Editor & Theme Settings

#### [MODIFY] [WidgetBuilderEngine.java](file:///c:/Users/sddrk/AndroidStudioProjects/DragWeb/app/src/main/java/sketchweb/gl/WidgetBuilderEngine.java)
- Define a `isBlockTag(String tag)` helper to identify block-level elements (`p`, `h1`-`h6`, `div`, `section`, etc.).
- Ensure block elements get `MATCH_PARENT` (width: 100%) by default when dropped, behaving like actual web HTML block elements.
- In `applyStyles`, set default border and background colors depending on the tag (e.g. `button` gets `#EFEFEF` background and border width 1; `input`/`textarea` get `#FFFFFF` background and border width 1; other views get transparent backgrounds and border width 0 by default) to render a clean, blank HTML layout style instead of default grey borders.

#### [MODIFY] [dialog_theme_settings.xml](file:///c:/Users/sddrk/AndroidStudioProjects/DragWeb/app/src/main/res/layout/dialog_theme_settings.xml)
- Remove `tilFontFamily` layout field.
- Add a Switch/Toggle for "Inline Styles".
- Add a "Reset Theme to Default" button at the bottom.

#### [MODIFY] [ThemeManager.java](file:///c:/Users/sddrk/AndroidStudioProjects/DragWeb/app/src/main/java/sketchweb/gl/ThemeManager.java)
- Manage a new boolean parameter `useInlineStyles` (defaulting to `true`).
- Save and load this parameter in `toJson()` and `fromJson()`.
- Add a `resetToDefaults()` method to restore default light/dark themes and clear custom CSS variables.

#### [MODIFY] [MainActivity.java](file:///c:/Users/sddrk/AndroidStudioProjects/DragWeb/app/src/main/java/sketchweb/gl/MainActivity.java)
- In `showThemeDialog()`, wire the new inline styles toggle and the Reset button. Clicking the Reset button will restore default settings and refresh the fields.
- Add "Font Settings" as a separate popup option in the Theme button menu. Selecting it will show a dedicated dialog to set the font family.

#### [MODIFY] [PageCodeGenerator.java](file:///c:/Users/sddrk/AndroidStudioProjects/DragWeb/app/src/main/java/sketchweb/gl/PageCodeGenerator.java)
- If `useInlineStyles` is disabled in `themeManager`, omit printing the `style="..."` attribute inside `generateHtmlForNode` and `generateHtmlForView`.

#### [MODIFY] [ExportManager.java](file:///c:/Users/sddrk/AndroidStudioProjects/DragWeb/app/src/main/java/sketchweb/gl/ExportManager.java)
- If `useInlineStyles` is disabled in `themeManager`, do not write element-specific CSS classes/rules to `style.css` and do not add the generated class attribute (`el-tag-hashCode`) to HTML tags in `generateElementHtml`.

---

### Logic Block Activity

#### [MODIFY] [BlockChipFactory.java](file:///c:/Users/sddrk/AndroidStudioProjects/DragWeb/app/src/main/java/sketchweb/gl/BlockChipFactory.java)
- Keep a static `lastValueMap` to save the last entered/selected values for each parameter/chip input ID (`input.id`).
- When building a chip, if `currentValue` is empty or default, pre-fill it with the last saved value from `lastValueMap`.
- When any value changes, update `lastValueMap` with the new value.

#### [MODIFY] [LogicBlockActivity.java](file:///c:/Users/sddrk/AndroidStudioProjects/DragWeb/app/src/main/java/sketchweb/gl/LogicBlockActivity.java)
- Implement `updateUndoRedoMenuState()` to dynamically update the enablement and tint of the Undo/Redo toolbar menu items.
- Enabled icons will use the theme's `colorOnSurface` color; disabled icons will use `colorOnSurface` with a 30% alpha for high-fidelity Material 3 design styling.
- Call `updateUndoRedoMenuState()` during `onCreate` and after every undo, redo, or workspace mutation.

---

## Verification Plan

### Automated Build Check
- Run compilation checks using Gradle commands to ensure there are no compilation errors:
  `./gradlew assembleDebug`

### Manual Verification
1. **Home Activity**: Create a new project. Verify that no logo selector option appears and the generated project ID is sequentially numbered (e.g. `project_01`, `project_02`).
2. **Website Import**: Import a single HTML file containing inline `<style>` tags. Open the project and check that those CSS styles are converted into blocks in the Logic Block activity.
3. **Canvas Editor**: Drag and drop block elements (like `p`, `h1`) and verify that they stretch to full width of the parent (width 100%) and render without gray borders.
4. **Theme Settings**: Toggle "Inline Styles" off and export/preview the project. Verify that the output HTML does not contain inline style attributes or auto-generated classes.
5. **Logic Block Activity**: Edit a parameter chip, then select a different block or new block, and verify that the dialog pre-fills with the last selected value/selection. Also verify that the undo/redo menu icons tint appropriately when active/inactive.
