# DragWeb Repository Structure

## Overview
DragWeb is an Android no-code website builder with dual storage (internal + external), Material Design 3 UI, and a block-based logic editor. This document maps the complete file structure for AI model context.

## Directory Tree

```
DragWeb/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/sketchweb/gl/
│   │   │   │   ├── Activities/
│   │   │   │   │   ├── MainActivity.java                    [Main editor with ViewPager2 tabs]
│   │   │   │   │   ├── HomeActivity.java                   [Project list + import/export]
│   │   │   │   │   ├── SplashActivity.java                 [App entry point]
│   │   │   │   │   ├── LogicBlockActivity.java             [Block-based code editor]
│   │   │   │   │   ├── PreviewActivity.java                [WebView preview with LocalHttpServer]
│   │   │   │   │   ├── ManagePageActivity.java             [Page rename/delete]
│   │   │   │   │   ├── ManageBlocksWidgetsActivity.java    [Custom block/widget manager]
│   │   │   │   │   ├── EditorSettingsActivity.java         [App settings]
│   │   │   │   │   ├── BlockParamManagerActivity.java      [Block input configuration]
│   │   │   │   │   ├── TextEditorActivity.java             [CodeMirror raw code editor]
│   │   │   │   │   └── DebugActivity.java                  [Debugging & diagnostics]
│   │   │   │   │
│   │   │   │   ├── Fragments/
│   │   │   │   │   ├── MainEditorFragment.java             [Visual canvas + property panel]
│   │   │   │   │   │                                       [Holds: WidgetRegistry, DropZoneManager, UndoRedoManager]
│   │   │   │   │   ├── EventsFragment.java                 [Logic blocks list per page]
│   │   │   │   │   ├── AssetsFragment.java                 [File browser for project assets]
│   │   │   │   │   └── DesignFragment.java                 [Theme & design settings]
│   │   │   │   │
│   │   │   │   ├── Engine & Builders/
│   │   │   │   │   ├── WidgetBuilderEngine.java            [Creates View from tag, applies styles]
│   │   │   │   │   ├── WidgetUpdater.java                  [Merges style/attr changes to View]
│   │   │   │   │   ├── WidgetRegistry.java                 [Manages custom + bundled widgets]
│   │   │   │   │   ├── WidgetSelector.java                 [Maintains selected widget reference]
│   │   │   │   │   └── WidgetDragDropManager.java          [Handles drag/drop logic]
│   │   │   │   │
│   │   │   │   ├── Canvas & UI/
│   │   │   │   │   ├── ViewLogicEditor.java                [Canvas ScrollView container]
│   │   │   │   │   ├── DropZoneManager.java                [Registers container drop targets]
│   │   │   │   │   ├── HierarchyTreeAdapter.java           [RecyclerView tree view adapter]
│   │   │   │   │   ├── BlockPane.java                      [Block stacking container for logic editor]
│   │   │   │   │   ├── ViewDummy.java                      [Dragging ghost element]
│   │   │   │   │   └── LocalHttpServer.java                [Nano-HTTP server for preview]
│   │   │   │   │
│   │   │   │   ├── Logic Blocks/
│   │   │   │   │   ├── LogicBlockManager.java              [Manages block tree, code generation]
│   │   │   │   │   ├── BlockCodeCompiler.java              [Compiles blocks → JS code]
│   │   │   │   │   ├── LogicBlock.java                     [Single block data model]
│   │   │   │   │   ├── BlockDef.java                       [Block definition from blocks.json]
│   │   │   │   │   ├── Block.java                          [Visual block UI (extends ViewGroup)]
│   │   │   │   │   ├── BlockBase.java                      [Base class for block components]
│   │   │   │   │   ├── BlockArg.java                       [Input field in a block]
│   │   │   │   │   ├── PaletteSelector.java                [Block category selector]
│   │   │   │   │   ├── PaletteBlock.java                   [Block palette panel]
│   │   │   │   │   ├── BlockDragDropManager.java           [Drag/drop for blocks]
│   │   │   │   │   ├── ManageBlocksWidgets.java            [Custom block/widget collection manager]
│   │   │   │   │   └── BlockBean.java                      [Serializable block data]
│   │   │   │   │
│   │   │   │   ├── Code Generation/
│   │   │   │   │   ├── PageCodeGenerator.java              [Generates self-contained HTML for preview]
│   │   │   │   │   ├── ProjectCodeGenerator.java           [Generates assets (CSS/JS) from logic]
│   │   │   │   │   ├── ExportManager.java                  [Multi-page export + ZIP]
│   │   │   │   │   └── HtmlCssImporter.java                [Reverse: HTML/CSS → widget tree + logic]
│   │   │   │   │
│   │   │   │   ├── Data Management/
│   │   │   │   │   ├── DesignDataManager.java              [In-memory cache for blocks, vars, lists]
│   │   │   │   │   ├── ProjectDataManager.java             [Project file I/O, ZIP import/export]
│   │   │   │   │   ├── PageManager.java                    [Multi-page state + serialization]
│   │   │   │   │   ├── ThemeManager.java                   [Light/dark theme + CSS vars]
│   │   │   │   │   ├── IconLibraryManager.java             [Font Awesome, Material Icons config]
│   │   │   │   │   └── AnimationLibraryManager.java        [Animation library registration]
│   │   │   │   │
│   │   │   │   ├── Utilities/
│   │   │   │   │   ├── FileUtil.java                       [File I/O helpers]
│   │   │   │   │   ├── LayoutUtil.java                     [DPI conversion utilities]
│   │   │   │   │   ├── UndoRedoManager.java                [Undo/redo state stack (max 30)]
│   │   │   │   │   ├── SharedPreferenceUtil.java           [SharedPreferences wrapper]
│   │   │   │   │   ├── StringUtil.java                     [String parsing utilities]
│   │   │   │   │   ├── ColorUtil.java                      [Color parsing + conversion]
│   │   │   │   │   ├── VariableNameValidator.java          [Validates var/list/function names]
│   │   │   │   │   ├── DefineSource.java                   [Reserved words + used symbol tracking]
│   │   │   │   │   ├── UniversalM3Dialog.java              [M3 dialog builder (text/color/choice)]
│   │   │   │   │   └── UniversalDialog.java                [Legacy dialog helpers]
│   │   │   │   │
│   │   │   │   ├── Adapters/
│   │   │   │   │   ├── Recyclerview1Adapter.java           [Widget palette chips]
│   │   │   │   │   ├── Recyclerview3Adapter.java           [Design/property list]
│   │   │   │   │   ├── FileExplorerAdapter.java            [Assets file browser]
│   │   │   │   │   ├── HierarchyTreeAdapter.java           [Widget tree view]
│   │   │   │   │   └── EventsAdapter.java                  [Logic blocks list]
│   │   │   │   │
│   │   │   │   └── Models/
│   │   │   │       ├── BlockBean.java                      [Serializable block]
│   │   │   │       ├── LogicBlock.java                     [Runtime block]
│   │   │   │       └── (other data classes)
│   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_main.xml                   [MainEditorFragment + ViewPager2]
│   │   │   │   │   ├── activity_home.xml                   [Project list RecyclerView]
│   │   │   │   │   ├── activity_logic_block.xml            [Block editor WorkspaceView]
│   │   │   │   │   ├── activity_preview.xml                [WebView for preview]
│   │   │   │   │   ├── fragment_main_editor.xml            [Canvas + panels]
│   │   │   │   │   ├── fragment_events.xml                 [Logic blocks list]
│   │   │   │   │   ├── fragment_assets.xml                 [File browser]
│   │   │   │   │   ├── dialog_universal_text_input.xml     [Text input dialog]
│   │   │   │   │   ├── dialog_universal_color.xml          [Color picker dialog]
│   │   │   │   │   ├── item_widget_chip.xml                [Widget palette chip]
│   │   │   │   │   ├── item_design_property.xml            [Property row]
│   │   │   │   │   ├── item_hierarchy_node.xml             [Tree view node]
│   │   │   │   │   ├── item_block_view.xml                 [Logic block UI]
│   │   │   │   │   └── ... (60+ layout XMLs)
│   │   │   │   │
│   │   │   │   ├── drawable/ & drawable-night/
│   │   │   │   │   ├── bg_block_shape_rect.xml             [Stack block bg]
│   │   │   │   │   ├── bg_block_shape_event.xml            [Event block bg]
│   │   │   │   │   ├── if_else.9.png                       [C-shape NinePatch]
│   │   │   │   │   ├── trash.png, trash_act.png            [Delete icon]
│   │   │   │   │   ├── copy.png, copy_act.png              [Duplicate icon]
│   │   │   │   │   ├── device_floppy.png                   [Save icon]
│   │   │   │   │   ├── ic_search.png, x.png                [Search icons]
│   │   │   │   │   └── ... (palette icons, category icons)
│   │   │   │   │
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml                         [All UI strings]
│   │   │   │   │   ├── colors.xml                          [Material 3 color palette]
│   │   │   │   │   ├── dimens.xml                          [Standard dimensions]
│   │   │   │   │   ├── styles.xml                          [M3 theme + widget styles]
│   │   │   │   │   └── attrs.xml                           [Custom view attributes]
│   │   │   │   │
│   │   │   │   ├── values-night/
│   │   │   │   │   ├── colors.xml                          [Dark theme colors]
│   │   │   │   │   └── styles.xml                          [Dark theme styles]
│   │   │   │   │
│   │   │   │   ├── menu/
│   │   │   │   │   ├── menu_main.xml                       [MainActivity toolbar menu]
│   │   │   │   │   ├── logic_menu.xml                      [LogicBlockActivity menu]
│   │   │   │   │   └── home_menu.xml                       [HomeActivity menu]
│   │   │   │   │
│   │   │   │   └── mipmap/ & mipmap-night/
│   │   │   │       └── ic_launcher.png                     [App icon variants]
│   │   │   │
│   │   │   ├── assets/
│   │   │   │   ├── blocks.json                             [Built-in block definitions]
│   │   │   │   ├── widgets.json                            [Built-in widget definitions]
│   │   │   │   ├── codemirror/
│   │   │   │   │   ├── codemirror.js                       [CodeMirror core]
│   │   │   │   │   ├── codemirror.css                      [CodeMirror styles]
│   │   │   │   │   ├── mode/
│   │   │   │   │   │   ├── htmlmixed/htmlmixed.js
│   │   │   │   │   │   ├── css/css.js
│   │   │   │   │   │   └── javascript/javascript.js
│   │   │   │   │   └── theme/
│   │   │   │   │       ├── eclipse.css                     [Light theme]
│   │   │   │   │       └── dracula.css                     [Dark theme]
│   │   │   │   └── code_viewer.html                        [CodeMirror wrapper for TextEditorActivity]
│   │   │   │
│   │   │   └── AndroidManifest.xml                         [App permissions + activity declarations]
│   │   │
│   │   └── test/ & androidTest/
│   │       └── (unit tests, not detailed here)
│   │
│   ├── build.gradle                                        [App-level Gradle config]
│   ├── proguard-rules.pro                                  [ProGuard obfuscation rules]
│   └── ...
│
├── .gemini/
│   ├── DragWeb.txt                                         [Comprehensive codebase reference]
│   ├── structure.md                                        [This file]
│   ├── design.md                                           [Material 3 UI design guidelines]
│   ├── components.md                                       [Reusable UI components]
│   ├── logic-blocks.md                                     [Block system documentation]
│   ├── code-generation.md                                  [HTML/CSS/JS generation]
│   ├── data-model.md                                       [Widget tree & serialization]
│   ├── file-system.md                                      [Storage layout & paths]
│   ├── api-reference.md                                    [Public API & method signatures]
│   └── troubleshooting.md                                  [Known issues & solutions]
│
├── project-level files
│   ├── build.gradle (root)                                 [Root Gradle config]
│   ├── settings.gradle                                     [Module configuration]
│   ├── gradle.properties                                   [Gradle properties]
│   └── local.properties                                    [Local SDK path (git-ignored)]
│
└── README.md                                               [Project overview]
```

## Key File Purposes

### Core Activities
| File | Purpose | Key Components |
|------|---------|-----------------|
| MainActivity | Main editor (3-tab ViewPager2) | MainEditorFragment, EventsFragment, AssetsFragment |
| HomeActivity | Project management | Project list RecyclerView, import/export dialogs |
| LogicBlockActivity | Block-based code editor | WorkspaceView, BlockPane, block palette |
| PreviewActivity | Live HTML preview | WebView + LocalHttpServer |

### Core Fragments
| File | Purpose | Key Components |
|------|---------|-----------------|
| MainEditorFragment | Visual canvas + design panel | Screen (LinearLayout), property RecyclerView, hierarchy tree |
| EventsFragment | Logic blocks list | LogicBlockManager, events list RecyclerView |
| AssetsFragment | File browser | FileExplorerAdapter, asset upload/delete |

### Engine Classes
| File | Purpose | Key Methods |
|------|---------|-------------|
| WidgetBuilderEngine | Creates & styles Views | createWidget(), applyStyles(), parseDimension() |
| WidgetRegistry | Widget definitions | getAllWidgets(), importCustomWidgets(), saveCustomWidgets() |
| PageManager | Multi-page state | loadPageLayout(), savePageLayout(), addPage(), removePage() |
| LogicBlockManager | Block code generation | generateBaseCssRules(), generateJavaScript(), generateAsdSource() |

### Code Generation
| File | Purpose | Output |
|------|---------|--------|
| PageCodeGenerator | Self-contained HTML (preview) | Single HTML file with inline CSS/JS |
| ExportManager | Multi-page export | HTML files + css/style.css + js/script.js |
| ProjectCodeGenerator | Live asset compilation | assets/css/style.css + assets/js/script.js |
| HtmlCssImporter | Reverse import | Widget tree + logic blocks from HTML/CSS |

### Data Management
| File | Purpose | Storage |
|------|---------|---------|
| DesignDataManager | In-memory cache | Blocks, variables, lists per page |
| ProjectDataManager | File I/O | Internal {filesDir}/projects/ |
| ThemeManager | Theme configuration | {id}.theme JSON |
| IconLibraryManager | Icon library config | {id}.icons JSON |

### Asset Files
| File | Purpose | Format |
|------|---------|--------|
| blocks.json | Block definitions | JSON array of block schemas |
| widgets.json | Widget definitions | JSON array of widget schemas |
| colors.xml | M3 color palette | Android color resources |
| styles.xml | M3 theme | Android style resources |

## Data Flow Overview

```
User Action (drag widget, edit style, add logic)
    ↓
MainEditorFragment / LogicBlockActivity
    ↓
WidgetUpdater / LogicBlockManager.updateBlock()
    ↓
View.setTag() updated / LogicBlock data modified
    ↓
saveUndoState() → UndoRedoManager
    ↓
saveProject() → ProjectDataManager (internal JSON)
    ↓
generateExportFiles() → ExportManager
    ↓
Output: css/style.css + js/script.js → assets/
```

## Storage Hierarchy

```
INTERNAL: context.getFilesDir()
  files/projects/
    ├── {id}.json                          [Index page widget tree]
    ├── {id}_{pageName}.json               [Per-page widget tree]
    ├── {id}.meta                          [Project metadata]
    ├── {id}.theme                         [Theme config]
    └── logic/
        ├── {id}_{page}.logic              [Logic blocks per page]
        ├── {id}_css_style_css.logic       [CSS file blocks]
        └── {id}_js_script_js.logic        [JS file blocks]

EXTERNAL: /.dragweb/projects/{id}/
  ├── project.config.json
  ├── pages.json                           [Page list]
  ├── assets/
  │   ├── css/style.css                    [Compiled CSS]
  │   ├── js/script.js                     [Compiled JS]
  │   └── (user images, fonts, etc.)
  └── pages/
      └── {pageName}.json                  [Per-page layout]

EXPORT: {filesDir}/exports/{projectName}/
  ├── {pageName}.html                      [Per-page HTML]
  ├── css/style.css
  ├── js/script.js
  └── (mirrored assets)
```

## File Dependencies

### Import Chain (HTML → App)
```
HomeActivity.showImportWebsiteDialog()
  ↓ HtmlCssImporter.importHtmlCss()
  ↓ Jsoup or string parsing
  ↓ createProjectFromImport()
  ↓ Save to {id}.json + {id}.logic files
```

### Export Chain (App → HTML/ZIP)
```
MainEditorFragment.performExport()
  ↓ ExportManager.generateExportFiles()
  ↓ PageManager.getPages()
  ↓ For each page: generateElementHtml() or generateHtmlFromNode()
  ↓ LogicBlockManager.generateBaseCssRules() + generateJavaScript()
  ↓ Write {page}.html + css/style.css + js/script.js
  ↓ ExportManager.exportAsZip()
  ↓ /.DragWeb/export/{name}_{id}.zip
```

### Preview Chain (App → Live HTML)
```
MainEditorFragment.showPreview()
  ↓ PageCodeGenerator.generateFullCode()
  ↓ Write to {cacheDir}/preview_{id}/{page}.html
  ↓ LocalHttpServer.start()
  ↓ PreviewActivity.loadUrl("http://localhost:8080/preview.html")
```

---

**Last Updated:** 2026-07-23  
**Format:** Markdown for AI model context  
**Related Docs:** design.md, components.md, logic-blocks.md, code-generation.md
