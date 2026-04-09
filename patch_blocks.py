import re

with open('source/app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'r') as f:
    content = f.read()

# Replace event definitions
event_blocks_old = """        case CAT_EVENT: return new BlockDef[]{
            new BlockDef("onClick", "On Click", "When element is clicked", CAT_EVENT),
            new BlockDef("onHover", "On Hover", "When mouse hovers", CAT_EVENT),
            new BlockDef("onLoad", "On Load", "When page loads", CAT_EVENT),
            new BlockDef("onInput", "On Input", "When input changes", CAT_EVENT),
            new BlockDef("onSubmit", "On Submit", "When form submits", CAT_EVENT),
            new BlockDef("onScroll", "On Scroll", "When user scrolls", CAT_EVENT),
            new BlockDef("onKeyDown", "On Key Down", "When key pressed", CAT_EVENT),
            new BlockDef("onChange", "On Change", "When value changes", CAT_EVENT),
        };"""

event_blocks_new = """        case CAT_EVENT: return new BlockDef[]{
            new BlockDef("onPageLoad", "onPageLoad", "When page loads", CAT_EVENT),
            new BlockDef("onVisible", "onVisible", "When element is visible", CAT_EVENT),
            new BlockDef("onHidden", "onHidden", "When element is hidden", CAT_EVENT),
            new BlockDef("onDestroy", "onDestroy", "When element is destroyed", CAT_EVENT),
            new BlockDef("onScroll", "onScroll", "When user scrolls", CAT_EVENT),
            new BlockDef("onInput", "onInput", "When input changes", CAT_EVENT),
            new BlockDef("onClick", "onClick", "When element is clicked", CAT_EVENT),
            new BlockDef("onHover", "onHover", "When mouse hovers", CAT_EVENT),
            new BlockDef("onSubmit", "onSubmit", "When form submits", CAT_EVENT),
        };"""

content = content.replace(event_blocks_old, event_blocks_new)

with open('source/app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'w') as f:
    f.write(content)
