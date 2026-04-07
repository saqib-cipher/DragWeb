package sketchweb.gl;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ThemeManager {

    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private Map<String, String> globalStyles = new LinkedHashMap<>();
    private Map<String, String> customCssVars = new LinkedHashMap<>();
    private String currentTheme = THEME_LIGHT;

    public ThemeManager() {
        applyLightTheme();
    }

    public void setTheme(String theme) {
        currentTheme = theme;
        if (THEME_DARK.equals(theme)) {
            applyDarkTheme();
        } else {
            applyLightTheme();
        }
    }

    public String getCurrentTheme() {
        return currentTheme;
    }

    private void applyLightTheme() {
        globalStyles.clear();
        globalStyles.put("bodyBackground", "#FFFFFF");
        globalStyles.put("bodyColor", "#333333");
        globalStyles.put("fontFamily", "sans-serif");
        globalStyles.put("primaryColor", "#2196F3");
        globalStyles.put("secondaryColor", "#FF9800");
        globalStyles.put("accentColor", "#4CAF50");
        globalStyles.put("linkColor", "#1976D2");
        globalStyles.put("borderColor", "#E0E0E0");
        globalStyles.put("cardBackground", "#FFFFFF");
        globalStyles.put("cardShadow", "0 2px 8px rgba(0,0,0,0.1)");
    }

    private void applyDarkTheme() {
        globalStyles.clear();
        globalStyles.put("bodyBackground", "#121212");
        globalStyles.put("bodyColor", "#E0E0E0");
        globalStyles.put("fontFamily", "sans-serif");
        globalStyles.put("primaryColor", "#BB86FC");
        globalStyles.put("secondaryColor", "#03DAC6");
        globalStyles.put("accentColor", "#CF6679");
        globalStyles.put("linkColor", "#BB86FC");
        globalStyles.put("borderColor", "#333333");
        globalStyles.put("cardBackground", "#1E1E1E");
        globalStyles.put("cardShadow", "0 2px 8px rgba(0,0,0,0.3)");
    }

    public void setGlobalStyle(String key, String value) {
        globalStyles.put(key, value);
    }

    public String getGlobalStyle(String key) {
        return globalStyles.getOrDefault(key, "");
    }

    public Map<String, String> getAllStyles() {
        return new LinkedHashMap<>(globalStyles);
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

    public String generateCssVariables() {
        StringBuilder css = new StringBuilder();
        css.append(":root {\n");
        for (Map.Entry<String, String> entry : globalStyles.entrySet()) {
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
        data.put("styles", globalStyles);
        data.put("customVars", customCssVars);
        return new Gson().toJson(data);
    }

    public void fromJson(String json) {
        try {
            Map<String, Object> data = new Gson().fromJson(json, Map.class);
            if (data.containsKey("theme")) {
                currentTheme = data.get("theme").toString();
            }
            if (data.containsKey("styles")) {
                Map<String, String> styles = (Map<String, String>) data.get("styles");
                globalStyles.putAll(styles);
            }
            if (data.containsKey("customVars")) {
                Map<String, String> vars = (Map<String, String>) data.get("customVars");
                if (vars != null) {
                    customCssVars.putAll(vars);
                }
            }
        } catch (Exception e) {
            applyLightTheme();
        }
    }

    private String camelToKebab(String str) {
        return str.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
