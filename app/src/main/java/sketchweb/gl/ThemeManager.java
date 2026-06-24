package sketchweb.gl;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ThemeManager {

    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    // Separate style maps for each theme
    private Map<String, String> lightStyles = new LinkedHashMap<>();
    private Map<String, String> darkStyles = new LinkedHashMap<>();
    private Map<String, String> customCssVars = new LinkedHashMap<>();
    private String currentTheme = THEME_LIGHT;

    public ThemeManager() {
        initLightDefaults();
        initDarkDefaults();
    }

    private void initLightDefaults() {
        lightStyles.clear();
        lightStyles.put("bodyBackground", "#FFFFFF");
        lightStyles.put("bodyColor", "#333333");
        lightStyles.put("fontFamily", "sans-serif");
        lightStyles.put("primaryColor", "#2196F3");
        lightStyles.put("secondaryColor", "#FF9800");
        lightStyles.put("accentColor", "#4CAF50");
        lightStyles.put("linkColor", "#1976D2");
        lightStyles.put("borderColor", "#E0E0E0");
        lightStyles.put("cardBackground", "#FFFFFF");
        lightStyles.put("cardShadow", "0 2px 8px rgba(0,0,0,0.1)");
    }

    private void initDarkDefaults() {
        darkStyles.clear();
        darkStyles.put("bodyBackground", "#121212");
        darkStyles.put("bodyColor", "#E0E0E0");
        darkStyles.put("fontFamily", "sans-serif");
        darkStyles.put("primaryColor", "#BB86FC");
        darkStyles.put("secondaryColor", "#03DAC6");
        darkStyles.put("accentColor", "#CF6679");
        darkStyles.put("linkColor", "#BB86FC");
        darkStyles.put("borderColor", "#333333");
        darkStyles.put("cardBackground", "#1E1E1E");
        darkStyles.put("cardShadow", "0 2px 8px rgba(0,0,0,0.3)");
    }

    public void setTheme(String theme) {
        currentTheme = theme;
    }

    public String getCurrentTheme() {
        return currentTheme;
    }

    /** Get the active styles map (for the current theme) */
    private Map<String, String> getActiveStyles() {
        return THEME_DARK.equals(currentTheme) ? darkStyles : lightStyles;
    }

    /** Get the light theme styles */
    public Map<String, String> getLightStyles() {
        return new LinkedHashMap<>(lightStyles);
    }

    /** Get the dark theme styles */
    public Map<String, String> getDarkStyles() {
        return new LinkedHashMap<>(darkStyles);
    }

    public void setGlobalStyle(String key, String value) {
        getActiveStyles().put(key, value);
    }

    /** Set a style value for a specific theme */
    public void setStyleForTheme(String theme, String key, String value) {
        if (THEME_DARK.equals(theme)) {
            darkStyles.put(key, value);
        } else {
            lightStyles.put(key, value);
        }
    }

    public String getGlobalStyle(String key) {
        return getActiveStyles().getOrDefault(key, "");
    }

    /** Get a style from a specific theme */
    public String getStyleForTheme(String theme, String key) {
        if (THEME_DARK.equals(theme)) {
            return darkStyles.getOrDefault(key, "");
        }
        return lightStyles.getOrDefault(key, "");
    }

    public Map<String, String> getAllStyles() {
        return new LinkedHashMap<>(getActiveStyles());
    }

    // Custom CSS variables management
    public void addCustomVar(String name, String value) {
        customCssVars.put(name, value);
    }

    public void removeCustomVar(String name) {
        customCssVars.remove(name);
    }

    public Map<String, String> getCustomCssVars() {
        return new LinkedHashMap<>(customCssVars);
    }

    public void setCustomCssVars(Map<String, String> vars) {
        customCssVars.clear();
        if (vars != null) {
            customCssVars.putAll(vars);
        }
    }

    private String generateVarsBlock(Map<String, String> styles) {
        StringBuilder css = new StringBuilder();
        for (Map.Entry<String, String> entry : styles.entrySet()) {
            String cssVar = "--" + camelToKebab(entry.getKey());
            css.append("  ").append(cssVar).append(": ").append(entry.getValue()).append(";\n");
        }
        // Include custom CSS variables
        for (Map.Entry<String, String> entry : customCssVars.entrySet()) {
            String varName = entry.getKey();
            if (!varName.startsWith("--")) {
                varName = "--" + varName;
            }
            css.append("  ").append(varName).append(": ").append(entry.getValue()).append(";\n");
        }
        return css.toString();
    }

    public String generateCssVariables() {
        StringBuilder css = new StringBuilder();
        // Light theme as default :root
        css.append(":root {\n");
        css.append(generateVarsBlock(lightStyles));
        css.append("}\n\n");

        // Dark theme via prefers-color-scheme AND a .dark-theme class
        css.append("@media (prefers-color-scheme: dark) {\n");
        css.append("  :root {\n");
        for (Map.Entry<String, String> entry : darkStyles.entrySet()) {
            String lightVal = lightStyles.get(entry.getKey());
            // Only output if different from light
            if (lightVal == null || !lightVal.equals(entry.getValue())) {
                String cssVar = "--" + camelToKebab(entry.getKey());
                css.append("    ").append(cssVar).append(": ").append(entry.getValue()).append(";\n");
            }
        }
        css.append("  }\n");
        css.append("}\n\n");

        // Also support explicit .dark-theme class on body/html
        css.append(".dark-theme {\n");
        css.append(generateVarsBlock(darkStyles));
        css.append("}\n\n");

        css.append(".light-theme {\n");
        css.append(generateVarsBlock(lightStyles));
        css.append("}\n");

        return css.toString();
    }

    public String generateGlobalCss() {
        if (disableDefaultStyles) {
            return "    .hidden { display: none !important; }\n";
        }
        StringBuilder css = new StringBuilder();
        css.append(generateCssVariables());
        css.append("\n* {\n  margin: 0;\n  padding: 0;\n  box-sizing: border-box;\n}\n\n");
        css.append("body {\n");
        css.append("  background-color: var(--body-background);\n");
        css.append("  color: var(--body-color);\n");
        css.append("  font-family: var(--font-family);\n");
        css.append("  line-height: 1.6;\n");
        css.append("}\n\n");
        css.append("a {\n  color: var(--link-color);\n  text-decoration: none;\n}\n\n");
        css.append("a:hover {\n  text-decoration: underline;\n}\n\n");
        css.append("button {\n  cursor: pointer;\n  font-family: inherit;\n}\n\n");
        css.append("input, textarea, select {\n  font-family: inherit;\n}\n\n");
        css.append(".hidden { display: none !important; }\n");
        css.append(".flex { display: flex; }\n");
        css.append(".flex-col { flex-direction: column; }\n");
        css.append(".flex-row { flex-direction: row; }\n");
        css.append(".items-center { align-items: center; }\n");
        css.append(".justify-center { justify-content: center; }\n");
        css.append(".justify-between { justify-content: space-between; }\n");
        css.append(".text-center { text-align: center; }\n");
        css.append(".w-full { width: 100%; }\n");
        css.append(".h-full { height: 100%; }\n");
        return css.toString();
    }

    private boolean useInlineStyles = true;
    private boolean disableDefaultStyles = false;

    public boolean isUseInlineStyles() {
        return useInlineStyles;
    }

    public void setUseInlineStyles(boolean useInlineStyles) {
        this.useInlineStyles = useInlineStyles;
    }

    public boolean isDisableDefaultStyles() {
        return disableDefaultStyles;
    }

    public void setDisableDefaultStyles(boolean disableDefaultStyles) {
        this.disableDefaultStyles = disableDefaultStyles;
    }

    public void resetToDefaults() {
        initLightDefaults();
        initDarkDefaults();
        customCssVars.clear();
        useInlineStyles = true;
        disableDefaultStyles = false;
        currentTheme = THEME_LIGHT;
    }

    public String toJson() {
        Map<String, Object> data = new HashMap<>();
        data.put("theme", currentTheme);
        data.put("lightStyles", lightStyles);
        data.put("darkStyles", darkStyles);
        data.put("customVars", customCssVars);
        data.put("useInlineStyles", useInlineStyles);
        data.put("disableDefaultStyles", disableDefaultStyles);
        // Backwards compat: also write "styles" as active theme
        data.put("styles", getActiveStyles());
        return new Gson().toJson(data);
    }

    public void fromJson(String json) {
        try {
            Map<String, Object> data = new Gson().fromJson(json, Map.class);
            if (data.containsKey("theme")) {
                currentTheme = data.get("theme").toString();
            }
            if (data.containsKey("disableDefaultStyles")) {
                Object val = data.get("disableDefaultStyles");
                if (val instanceof Boolean) {
                    disableDefaultStyles = (Boolean) val;
                } else if (val instanceof String) {
                    disableDefaultStyles = Boolean.parseBoolean((String) val);
                } else if (val instanceof Number) {
                    disableDefaultStyles = ((Number) val).intValue() != 0;
                }
            } else {
                disableDefaultStyles = false;
            }
            if (data.containsKey("useInlineStyles")) {
                Object val = data.get("useInlineStyles");
                if (val instanceof Boolean) {
                    useInlineStyles = (Boolean) val;
                } else if (val instanceof String) {
                    useInlineStyles = Boolean.parseBoolean((String) val);
                } else if (val instanceof Number) {
                    useInlineStyles = ((Number) val).intValue() != 0;
                }
            } else {
                useInlineStyles = true;
            }
            // Load separate light/dark styles if available
            if (data.containsKey("lightStyles")) {
                Map<String, String> ls = (Map<String, String>) data.get("lightStyles");
                if (ls != null) {
                    lightStyles.clear();
                    lightStyles.putAll(ls);
                }
            }
            if (data.containsKey("darkStyles")) {
                Map<String, String> ds = (Map<String, String>) data.get("darkStyles");
                if (ds != null) {
                    darkStyles.clear();
                    darkStyles.putAll(ds);
                }
            }
            // Backwards compat: if no separate maps, load from "styles"
            if (!data.containsKey("lightStyles") && !data.containsKey("darkStyles")) {
                if (data.containsKey("styles")) {
                    Map<String, String> styles = (Map<String, String>) data.get("styles");
                    if (styles != null) {
                        if (THEME_DARK.equals(currentTheme)) {
                            darkStyles.putAll(styles);
                        } else {
                            lightStyles.putAll(styles);
                        }
                    }
                }
            }
            if (data.containsKey("customVars")) {
                Map<String, String> vars = (Map<String, String>) data.get("customVars");
                if (vars != null) {
                    customCssVars.putAll(vars);
                }
            }
        } catch (Exception e) {
            resetToDefaults();
        }
    }

    public static String camelToKebab(String str) {
        return str.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
