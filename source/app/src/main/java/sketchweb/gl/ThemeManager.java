package sketchweb.gl;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ThemeManager {

    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private final Map<String, String> lightStyles = new LinkedHashMap<>();
    private final Map<String, String> darkStyles = new LinkedHashMap<>();
    private final Map<String, String> lightCustomCssVars = new LinkedHashMap<>();
    private final Map<String, String> darkCustomCssVars = new LinkedHashMap<>();
    private String currentTheme = THEME_LIGHT;

    public ThemeManager() {
        applyLightDefaults(lightStyles);
        applyDarkDefaults(darkStyles);
    }

    public void setTheme(String theme) {
        currentTheme = THEME_DARK.equals(theme) ? THEME_DARK : THEME_LIGHT;
    }

    public String getCurrentTheme() {
        return currentTheme;
    }

    public Map<String, String> getStylesForTheme(String theme) {
        return new LinkedHashMap<>(getStylesMap(theme));
    }

    public void setStylesForTheme(String theme, Map<String, String> styles) {
        Map<String, String> target = getStylesMap(theme);
        if (styles == null || styles.isEmpty()) return;
        target.putAll(styles);
    }

    public Map<String, String> getCustomVarsForTheme(String theme) {
        return new LinkedHashMap<>(getCustomVarsMap(theme));
    }

    public void setCustomVarsForTheme(String theme, Map<String, String> vars) {
        Map<String, String> target = getCustomVarsMap(theme);
        target.clear();
        if (vars != null) {
            target.putAll(vars);
        }
    }

    private Map<String, String> getStylesMap(String theme) {
        return THEME_DARK.equals(theme) ? darkStyles : lightStyles;
    }

    private Map<String, String> getCustomVarsMap(String theme) {
        return THEME_DARK.equals(theme) ? darkCustomCssVars : lightCustomCssVars;
    }

    private Map<String, String> getCurrentStylesMap() {
        return getStylesMap(currentTheme);
    }

    private Map<String, String> getCurrentCustomVarsMap() {
        return getCustomVarsMap(currentTheme);
    }

    private void applyLightDefaults(Map<String, String> target) {
        target.clear();
        target.put("bodyBackground", "#FFFFFF");
        target.put("bodyColor", "#333333");
        target.put("fontFamily", "sans-serif");
        target.put("primaryColor", "#2196F3");
        target.put("secondaryColor", "#FF9800");
        target.put("accentColor", "#4CAF50");
        target.put("linkColor", "#1976D2");
        target.put("borderColor", "#E0E0E0");
        target.put("cardBackground", "#FFFFFF");
        target.put("cardShadow", "0 2px 8px rgba(0,0,0,0.1)");
    }

    private void applyDarkDefaults(Map<String, String> target) {
        target.clear();
        target.put("bodyBackground", "#121212");
        target.put("bodyColor", "#E0E0E0");
        target.put("fontFamily", "sans-serif");
        target.put("primaryColor", "#BB86FC");
        target.put("secondaryColor", "#03DAC6");
        target.put("accentColor", "#CF6679");
        target.put("linkColor", "#BB86FC");
        target.put("borderColor", "#333333");
        target.put("cardBackground", "#1E1E1E");
        target.put("cardShadow", "0 2px 8px rgba(0,0,0,0.3)");
    }

    public void setGlobalStyle(String key, String value) {
        getCurrentStylesMap().put(key, value);
    }

    public String getGlobalStyle(String key) {
        return getCurrentStylesMap().getOrDefault(key, "");
    }

    public Map<String, String> getAllStyles() {
        return new LinkedHashMap<>(getCurrentStylesMap());
    }

    // Custom CSS variables management
    public void addCustomVar(String name, String value) {
        getCurrentCustomVarsMap().put(name, value);
    }

    public void removeCustomVar(String name) {
        getCurrentCustomVarsMap().remove(name);
    }

    public Map<String, String> getCustomCssVars() {
        return new LinkedHashMap<>(getCurrentCustomVarsMap());
    }

    public void setCustomCssVars(Map<String, String> vars) {
        Map<String, String> currentVars = getCurrentCustomVarsMap();
        currentVars.clear();
        if (vars != null) {
            currentVars.putAll(vars);
        }
    }

    public String generateCssVariables() {
        StringBuilder css = new StringBuilder();
        css.append(":root {\n");
        Map<String, String> currentStyles = getCurrentStylesMap();
        Map<String, String> currentVars = getCurrentCustomVarsMap();
        for (Map.Entry<String, String> entry : currentStyles.entrySet()) {
            String cssVar = "--" + camelToKebab(entry.getKey());
            css.append("  ").append(cssVar).append(": ").append(entry.getValue()).append(";\n");
        }
        for (Map.Entry<String, String> entry : currentVars.entrySet()) {
            String varName = entry.getKey();
            if (!varName.startsWith("--")) {
                varName = "--" + varName;
            }
            css.append("  ").append(varName).append(": ").append(entry.getValue()).append(";\n");
        }
        css.append("}\n");
        return css.toString();
    }

    public String generateGlobalCss() {
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

    public String toJson() {
        Map<String, Object> data = new HashMap<>();
        data.put("theme", currentTheme);
        data.put("lightStyles", lightStyles);
        data.put("darkStyles", darkStyles);
        data.put("lightCustomVars", lightCustomCssVars);
        data.put("darkCustomVars", darkCustomCssVars);
        return new Gson().toJson(data);
    }

    public void fromJson(String json) {
        try {
            Map<String, Object> data = new Gson().fromJson(json, Map.class);
            if (data == null) return;

            if (data.containsKey("theme")) {
                setTheme(String.valueOf(data.get("theme")));
            }

            if (data.containsKey("lightStyles") || data.containsKey("darkStyles")) {
                mergeMap(lightStyles, (Map<String, Object>) data.get("lightStyles"));
                mergeMap(darkStyles, (Map<String, Object>) data.get("darkStyles"));
                mergeMap(lightCustomCssVars, (Map<String, Object>) data.get("lightCustomVars"));
                mergeMap(darkCustomCssVars, (Map<String, Object>) data.get("darkCustomVars"));
                return;
            }

            // Backward compatibility with previous single-theme format
            if (data.containsKey("styles")) {
                mergeMap(getCurrentStylesMap(), (Map<String, Object>) data.get("styles"));
            }
            if (data.containsKey("customVars")) {
                mergeMap(getCurrentCustomVarsMap(), (Map<String, Object>) data.get("customVars"));
            }
        } catch (Exception e) {
            applyLightDefaults(lightStyles);
            applyDarkDefaults(darkStyles);
            lightCustomCssVars.clear();
            darkCustomCssVars.clear();
            currentTheme = THEME_LIGHT;
        }
    }

    private void mergeMap(Map<String, String> target, Map<String, Object> source) {
        if (target == null || source == null) return;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            target.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
    }

    private String camelToKebab(String str) {
        return str.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
