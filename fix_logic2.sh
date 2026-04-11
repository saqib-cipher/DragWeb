cat << 'PATCH' > LogicBlockManager.java.patch3
<<<<<<< SEARCH
    public static final String ACTION_SET_ATTRIBUTE = "setAttribute";
    public static final String ACTION_REMOVE_ATTRIBUTE = "removeAttribute";
    public static final String ACTION_SET_VALUE = "setValue";
    public static final String ACTION_APPEND_CHILD = "appendChild";
    public static final String ACTION_PREPEND_CHILD = "prependChild";
    public static final String ACTION_CREATE_ELEMENT = "createElement";
    public static final String ACTION_REMOVE_ELEMENT = "removeElement";
    public static final String ACTION_CUSTOM_JS = "customJs";
    public static final String ACTION_FETCH_API = "fetchApi";
    public static final String ACTION_LOCAL_STORAGE = "localStorage";
    public static final String ACTION_SCROLL_TO = "scrollTo";
    public static final String ACTION_COPY_CLIPBOARD = "copyClipboard";
    public static final String ACTION_DELAY = "delay";
    public static final String ACTION_GO_TO_PAGE = "goToPage";
    public static final String ACTION_OPEN_PAGE = "openPage";
    public static final String ACTION_SET_HTML = "setHTML";
    public static final String ACTION_FOCUS_INPUT = "focusInput";
    public static final String ACTION_BLUR_INPUT = "blurInput";
    public static final String ACTION_SET_HREF = "setHref";
    public static final String ACTION_SET_TRANSFORM = "setTransform";
    public static final String ACTION_SET_TRANSITION = "setTransition";
=======
    public static final String ACTION_REMOVE_ATTRIBUTE = "removeAttribute";
    public static final String ACTION_SET_VALUE = "setValue";
    public static final String ACTION_APPEND_CHILD = "appendChild";
    public static final String ACTION_PREPEND_CHILD = "prependChild";
    public static final String ACTION_CREATE_ELEMENT = "createElement";
    public static final String ACTION_REMOVE_ELEMENT = "removeElement";
    public static final String ACTION_CUSTOM_JS = "customJs";
    public static final String ACTION_FETCH_API = "fetchApi";
    public static final String ACTION_LOCAL_STORAGE = "localStorage";
    public static final String ACTION_SCROLL_TO = "scrollTo";
    public static final String ACTION_COPY_CLIPBOARD = "copyClipboard";
    public static final String ACTION_DELAY = "delay";
    public static final String ACTION_GO_TO_PAGE = "goToPage";
    public static final String ACTION_OPEN_PAGE = "openPage";
    public static final String ACTION_SET_HTML = "setHTML";
    public static final String ACTION_FOCUS_INPUT = "focusInput";
    public static final String ACTION_BLUR_INPUT = "blurInput";
>>>>>>> REPLACE
PATCH
patch source/app/src/main/java/sketchweb/gl/LogicBlockManager.java LogicBlockManager.java.patch3
