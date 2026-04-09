import re

with open('source/app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'r') as f:
    content = f.read()

# Update mapActionKey
old_map_action_key = """    private String mapActionKey(String id) {
        switch (id) {
            case "setDisplay": case "setColor": case "setBackground":
            case "setWidth": case "setHeight": case "setMargin":
            case "setPadding": case "setBorder": case "setRadius":
            case "setOpacity": case "setFontSize":
                return "changeStyle";
            case "addClass": return "addClass";
            case "removeClass": return "removeClass";
            case "toggleClass": return "toggleClass";
            case "setText": return "setText";
            case "setHTML": return "setHTML";
            case "showElement": case "hideElement": case "toggleElement": return "showHide";
            case "navigate": return "navigate";
            case "goToPage": return "goToPage";
            case "alert": return "alert";
            case "scrollTo": return "scrollTo";
            case "focusInput": return "focusInput";
            case "setAttribute": return "setAttribute";
            case "removeElement": return "removeElement";
            default: return id;
        }
    }"""

new_map_action_key = """    private String mapActionKey(String id) {
        switch (id) {
            case "setDisplay": case "setColor": case "setBackground":
            case "setWidth": case "setHeight": case "setMargin":
            case "setPadding": case "setBorder": case "setRadius":
            case "setOpacity": case "setFontSize":
                return "changeStyle";
            case "addClass": return "addClass";
            case "removeClass": return "removeClass";
            case "toggleClass": return "toggleClass";
            case "setText": return "setText";
            case "setHTML": return "setHTML";
            case "showElement": case "hideElement": case "toggleElement": return "showHide";
            case "navigate": return "navigate";
            case "goToPage": return "goToPage";
            case "alert": return "alert";
            case "scrollTo": return "scrollTo";
            case "focusInput": return "focusInput";
            case "setAttribute": return "setAttribute";
            case "removeElement": return "removeElement";
            case "setHref": return "setHref";
            case "createElement": return "createElement";
            case "appendElement": return "appendElement";
            case "prependElement": return "prependElement";
            default: return id;
        }
    }"""

content = content.replace(old_map_action_key, new_map_action_key)

with open('source/app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'w') as f:
    f.write(content)
