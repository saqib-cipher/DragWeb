cat << 'PATCH' > LogicBlockManager.java.patch2
<<<<<<< SEARCH
    // CSS Pseudo-class events
    public static final String EVENT_HOVER = "hover";
    public static final String EVENT_FOCUS = "focus";
    public static final String EVENT_ACTIVE = "active";
=======
    // Page-based events (Sketchware pattern)
    public static final String EVENT_PAGE_LOAD = "pageLoad";
    public static final String EVENT_VISIBLE = "visible";
    public static final String EVENT_HIDDEN = "hidden";
    public static final String EVENT_DESTROY = "destroy";
    public static final String EVENT_PAGE_SCROLL = "pageScroll";
    public static final String EVENT_PAGE_INPUT = "pageInput";

    // CSS Pseudo-class events
    public static final String EVENT_HOVER = "hover";
    public static final String EVENT_FOCUS = "focus";
    public static final String EVENT_ACTIVE = "active";

    // Legacy element events for backwards compatibility with old project logic
    public static final String EVENT_CLICK = "click";
    public static final String EVENT_INPUT = "input";
    public static final String EVENT_LOAD = "load";
    public static final String EVENT_SUBMIT = "submit";
    public static final String EVENT_SCROLL = "scroll";
    public static final String EVENT_KEYDOWN = "keydown";
    public static final String EVENT_CHANGE = "change";
>>>>>>> REPLACE
<<<<<<< SEARCH
    // HTML Attributes actions
    public static final String ACTION_SET_HREF = "setHref";
    public static final String ACTION_SET_SRC = "setSrc";
    public static final String ACTION_SET_ATTRIBUTE = "setAttribute";
    public static final String ACTION_SET_ID = "setId";
    public static final String ACTION_SET_CLASS = "setClass";

    public static final String ACTION_CHANGE_STYLE = "changeStyle";
=======
    // HTML Attributes actions
    public static final String ACTION_SET_HREF = "setHref";
    public static final String ACTION_SET_SRC = "setSrc";
    public static final String ACTION_SET_ATTRIBUTE = "setAttribute";
    public static final String ACTION_SET_ID = "setId";
    public static final String ACTION_SET_CLASS = "setClass";

    // Legacy actions for compatibility with old save files
    public static final String ACTION_CHANGE_STYLE = "changeStyle";
>>>>>>> REPLACE
<<<<<<< SEARCH
    public static String getEventDisplayName(String eventType) {
        switch (eventType) {
            case EVENT_PAGE_LOAD: return "On Page Load";
            case EVENT_VISIBLE: return "On Visible";
            case EVENT_HIDDEN: return "On Hidden";
            case EVENT_DESTROY: return "On Destroy";
            case EVENT_PAGE_SCROLL: return "On Scroll";
            case EVENT_PAGE_INPUT: return "On Input";
            case EVENT_CLICK: return "On Click";
            case EVENT_HOVER: return "On Hover";
            case EVENT_INPUT: return "On Input";
            case EVENT_LOAD: return "On Load";
            case EVENT_SUBMIT: return "On Submit";
            case EVENT_SCROLL: return "On Scroll";
            case EVENT_KEYDOWN: return "On Key Down";
            case EVENT_CHANGE: return "On Change";
            case "immediate": return "Immediate";
            default: return eventType;
        }
    }
=======
    public static String getEventDisplayName(String eventType) {
        switch (eventType) {
            case EVENT_PAGE_LOAD: return "On Page Load";
            case EVENT_VISIBLE: return "On Visible";
            case EVENT_HIDDEN: return "On Hidden";
            case EVENT_DESTROY: return "On Destroy";
            case EVENT_PAGE_SCROLL: return "On Scroll";
            case EVENT_PAGE_INPUT: return "On Input";
            case EVENT_CLICK: return "On Click";
            case EVENT_HOVER: return "On Hover";
            case EVENT_FOCUS: return "On Focus";
            case EVENT_ACTIVE: return "On Active";
            case EVENT_INPUT: return "On Input";
            case EVENT_LOAD: return "On Load";
            case EVENT_SUBMIT: return "On Submit";
            case EVENT_SCROLL: return "On Scroll";
            case EVENT_KEYDOWN: return "On Key Down";
            case EVENT_CHANGE: return "On Change";
            case "immediate": return "Immediate";
            default: return eventType;
        }
    }
>>>>>>> REPLACE
PATCH
patch source/app/src/main/java/sketchweb/gl/LogicBlockManager.java LogicBlockManager.java.patch2
