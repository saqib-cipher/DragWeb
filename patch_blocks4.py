import re

with open('source/app/src/main/java/sketchweb/gl/LogicBlockManager.java', 'r') as f:
    content = f.read()

# Replace block action keys mapping logic and execution JS logic
old_generate_action_js_start = """    private String generateActionJs(LogicBlock block, String elVar) {
        switch (block.action) {"""

new_generate_action_js_start = """    private String generateActionJs(LogicBlock block, String elVar) {
        switch (block.action) {
            case "setHref":
                return elVar + ".setAttribute('href', '" + escapeJs(block.params) + "');\\n";
            case "createElement":
                return "var _newEl = document.createElement('" + escapeJs(block.params) + "'); " + elVar + ".appendChild(_newEl);\\n";
            case "appendElement":
                return elVar + ".insertAdjacentHTML('beforeend', '" + escapeJs(block.params) + "');\\n";
            case "prependElement":
                return elVar + ".insertAdjacentHTML('afterbegin', '" + escapeJs(block.params) + "');\\n";"""

content = content.replace(old_generate_action_js_start, new_generate_action_js_start)

with open('source/app/src/main/java/sketchweb/gl/LogicBlockManager.java', 'w') as f:
    f.write(content)
