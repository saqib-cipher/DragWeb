import re

with open('source/app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'r') as f:
    content = f.read()

# Replace HTML definitions
html_blocks_old = """        case CAT_HTML: return new BlockDef[]{
            new BlockDef("setText", "Set Text", "Change text content", CAT_HTML),
            new BlockDef("setHTML", "Set HTML", "Set inner HTML", CAT_HTML),
            new BlockDef("showElement", "Show", "Show element", CAT_HTML),
            new BlockDef("hideElement", "Hide", "Hide element", CAT_HTML),
            new BlockDef("toggleElement", "Toggle", "Toggle visibility", CAT_HTML),
            new BlockDef("navigate", "Navigate", "Go to URL", CAT_HTML),
            new BlockDef("goToPage", "Go To Page", "Navigate to page", CAT_HTML),
            new BlockDef("alert", "Alert", "Show alert dialog", CAT_HTML),
            new BlockDef("scrollTo", "Scroll To", "Scroll to position", CAT_HTML),
            new BlockDef("focusInput", "Focus Input", "Focus input field", CAT_HTML),
            new BlockDef("setAttribute", "Set Attribute", "Set HTML attribute", CAT_HTML),
            new BlockDef("removeElement", "Remove", "Remove element", CAT_HTML),
        };"""

html_blocks_new = """        case CAT_HTML: return new BlockDef[]{
            new BlockDef("setHref", "setHref", "Set link href", CAT_HTML),
            new BlockDef("scrollTo", "scrollTo", "Scroll to id/class", CAT_HTML),
            new BlockDef("setText", "setText", "Set text content", CAT_HTML),
            new BlockDef("setHTML", "setHTML", "Set inner HTML", CAT_HTML),
            new BlockDef("showElement", "show", "Show element", CAT_HTML),
            new BlockDef("hideElement", "hide", "Hide element", CAT_HTML),
            new BlockDef("createElement", "createElement", "Create element", CAT_HTML),
            new BlockDef("removeElement", "removeElement", "Remove element", CAT_HTML),
            new BlockDef("appendElement", "append", "Append child", CAT_HTML),
            new BlockDef("prependElement", "prepend", "Prepend child", CAT_HTML),
        };"""

content = content.replace(html_blocks_old, html_blocks_new)

with open('source/app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'w') as f:
    f.write(content)
