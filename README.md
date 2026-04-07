# DragWeb - No-Code Android Website Builder

A visual drag-and-drop website builder for Android, inspired by Sketchware. Create websites by dragging UI components onto a canvas, editing their properties in real-time, previewing results, and exporting clean HTML/CSS/JS code.

## Features

### Project System
- Create, save, and load projects (JSON-based)
- Project listing on home screen with metadata
- Delete projects with confirmation

### Drag & Drop Builder
- Widget palette with 15+ components loaded from JSON registry
- Center canvas for visual editing
- Drag from palette to canvas to add elements
- Long-press drag to reorder elements within canvas
- Nested layout support (containers accept child elements)

### Supported Elements
- **Basic**: Text, Heading (H1-H3), Button, Image, Input, Link, List, Divider
- **Layout**: Container, Row, Column, Card, Spacer
- Each element includes default properties, editable attributes, and optional children

### Dynamic Property Editor
- 19 editable properties: text, color, font, background, borders, padding, margin, elevation, gravity, opacity, rotation, scale, dimensions, orientation
- Material Design dialogs for property input
- Changes instantly update the canvas preview

### Real-Time Preview
- Native Android preview on canvas
- WebView HTML preview via bottom sheet
- Copy HTML to clipboard

### Code Generation & Export
- Export as separate HTML, CSS, and JS files
- Export as ZIP archive
- Copy single-file HTML to clipboard
- Clean, readable output with proper indentation

### Undo / Redo System
- Full action history (up to 50 states)
- Undo/redo buttons in toolbar

### Theme System
- Light and dark website themes
- Configurable primary color, font family, body background
- CSS variable generation for exported themes

## Architecture

- **Widget Registry**: JSON-based element definitions (`assets/widgets.json`)
- **Widget Tree**: `ArrayList<HashMap<String, Object>>` for flexible structure
- **Rendering Pipeline**: JSON -> Widget Tree -> Native View / HTML
- **Persistence**: JSON files in internal storage

## Tech Stack

- **Language**: Java (Android)
- **UI**: XML layouts with Material Design 3
- **Preview**: WebView + Native Views
- **JSON**: Gson
- **Storage**: Internal storage (JSON files)

## Project Structure

```
source/app/src/main/
├── assets/
│   └── widgets.json          # Widget registry
├── java/sketchweb/gl/
│   ├── MainActivity.java      # Main editor with drag-and-drop
│   ├── HomeActivity.java      # Project management
│   ├── SplashActivity.java    # Splash screen
│   ├── WidgetBuilderEngine.java   # Creates views from tags
│   ├── WidgetRegistry.java    # Loads widgets from JSON
│   ├── WidgetSelector.java    # Selection/highlighting
│   ├── WidgetUpdater.java     # Property updates
│   ├── DropZoneManager.java   # Nested drop support
│   ├── PageCodeGenerator.java # HTML code generation
│   ├── ExportManager.java     # HTML/CSS/JS/ZIP export
│   ├── UndoRedoManager.java   # Undo/redo history
│   ├── ThemeManager.java      # Theme/global styles
│   ├── ProjectDataManager.java # Save/load projects
│   └── ...utilities
└── res/
    ├── layout/                # 12 XML layouts
    ├── drawable/              # 36 vector icons
    └── values/                # Colors, strings, styles
```
