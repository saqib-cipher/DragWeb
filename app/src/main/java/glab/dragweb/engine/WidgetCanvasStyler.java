package glab.dragweb.engine;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import glab.dragweb.R;
import glab.dragweb.data.ThemeManager;

/**
 * WidgetCanvasStyler applies CSS-equivalent styling and layout behavior to Android Views
 * on the DragWeb editor canvas, providing accurate visual parity with the web preview.
 *
 * It accurately handles:
 * - CSS dimensions (px, rem, em, %, vw, vh, auto, fit-content, match_parent)
 * - CSS colors (Hex 3/4/6/8-digit, rgb/rgba, hsl/hsla, CSS named colors, CSS variables)
 * - Borders & corner radius (including multi-corner shorthand and 50% circular pills)
 * - Flexbox layout (flexDirection row/column, updating child LayoutParams accordingly so
 *   row items sit side-by-side instead of being stretched to MATCH_PARENT)
 * - Typography (font sizes, weights, alignments, decorations, transforms)
 * - Padding, margin, gap, opacity, elevation, and gradients.
 */
public class WidgetCanvasStyler {

    private static final String TAG = "WidgetCanvasStyler";
    private static WidgetCanvasStyler instance;

    private final Context context;
    private ThemeManager themeManager;
    private final float density;
    private final int screenWidthPx;
    private final int screenHeightPx;

    // Cache of CSS standard color names
    private static final Map<String, Integer> CSS_COLORS = new HashMap<>();

    static {
        CSS_COLORS.put("transparent", Color.TRANSPARENT);
        CSS_COLORS.put("black", 0xFF000000);
        CSS_COLORS.put("white", 0xFFFFFFFF);
        CSS_COLORS.put("red", 0xFFFF0000);
        CSS_COLORS.put("green", 0xFF008000);
        CSS_COLORS.put("blue", 0xFF0000FF);
        CSS_COLORS.put("yellow", 0xFFFFFF00);
        CSS_COLORS.put("cyan", 0xFF00FFFF);
        CSS_COLORS.put("magenta", 0xFFFF00FF);
        CSS_COLORS.put("gray", 0xFF808080);
        CSS_COLORS.put("grey", 0xFF808080);
        CSS_COLORS.put("lightgray", 0xFFD3D3D3);
        CSS_COLORS.put("lightgrey", 0xFFD3D3D3);
        CSS_COLORS.put("darkgray", 0xFFA9A9A9);
        CSS_COLORS.put("darkgrey", 0xFFA9A9A9);
        CSS_COLORS.put("dimgray", 0xFF696969);
        CSS_COLORS.put("dimgrey", 0xFF696969);
        CSS_COLORS.put("silver", 0xFFC0C0C0);
        CSS_COLORS.put("orange", 0xFFFFA500);
        CSS_COLORS.put("purple", 0xFF800080);
        CSS_COLORS.put("pink", 0xFFFFC0CB);
        CSS_COLORS.put("brown", 0xFFA52A2A);
        CSS_COLORS.put("coral", 0xFFFF7F50);
        CSS_COLORS.put("teal", 0xFF008080);
        CSS_COLORS.put("navy", 0xFF000080);
        CSS_COLORS.put("indigo", 0xFF4B0082);
        CSS_COLORS.put("violet", 0xFFEE82EE);
        CSS_COLORS.put("crimson", 0xFFDC143C);
        CSS_COLORS.put("gold", 0xFFFFD700);
        CSS_COLORS.put("lime", 0xFF00FF00);
        CSS_COLORS.put("olive", 0xFF808000);
        CSS_COLORS.put("maroon", 0xFF800000);
        CSS_COLORS.put("aqua", 0xFF00FFFF);
        CSS_COLORS.put("fuchsia", 0xFFFF00FF);
        CSS_COLORS.put("rebeccapurple", 0xFF663399);
        CSS_COLORS.put("skyblue", 0xFF87CEEB);
        CSS_COLORS.put("deepskyblue", 0xFF00BFFF);
        CSS_COLORS.put("dodgerblue", 0xFF1E90FF);
        CSS_COLORS.put("royalblue", 0xFF4169E1);
        CSS_COLORS.put("steelblue", 0xFF4682B4);
        CSS_COLORS.put("darkblue", 0xFF00008B);
        CSS_COLORS.put("mediumblue", 0xFF0000CD);
        CSS_COLORS.put("midnightblue", 0xFF191970);
        CSS_COLORS.put("cornflowerblue", 0xFF6495ED);
        CSS_COLORS.put("aliceblue", 0xFFF0F8FF);
        CSS_COLORS.put("aquamarine", 0xFF7FFFD4);
        CSS_COLORS.put("azure", 0xFFF0FFFF);
        CSS_COLORS.put("beige", 0xFFF5F5DC);
        CSS_COLORS.put("bisque", 0xFFFFE4C4);
        CSS_COLORS.put("blanchedalmond", 0xFFFFEBCD);
        CSS_COLORS.put("blueviolet", 0xFF8A2BE2);
        CSS_COLORS.put("burlywood", 0xFFDEB887);
        CSS_COLORS.put("cadetblue", 0xFF5F9EA0);
        CSS_COLORS.put("chartreuse", 0xFF7FFF00);
        CSS_COLORS.put("chocolate", 0xFFD2691E);
        CSS_COLORS.put("cornsilk", 0xFFFFF8DC);
        CSS_COLORS.put("darkcyan", 0xFF008B8B);
        CSS_COLORS.put("darkgoldenrod", 0xFFB8860B);
        CSS_COLORS.put("darkgreen", 0xFF006400);
        CSS_COLORS.put("darkkhaki", 0xFFBDB76B);
        CSS_COLORS.put("darkmagenta", 0xFF8B008B);
        CSS_COLORS.put("darkolivegreen", 0xFF556B2F);
        CSS_COLORS.put("darkorange", 0xFFFF8C00);
        CSS_COLORS.put("darkorchid", 0xFF9932CC);
        CSS_COLORS.put("darkred", 0xFF8B0000);
        CSS_COLORS.put("darksalmon", 0xFFE9967A);
        CSS_COLORS.put("darkseagreen", 0xFF8FBC8F);
        CSS_COLORS.put("darkslateblue", 0xFF483D8B);
        CSS_COLORS.put("darkslategray", 0xFF2F4F4F);
        CSS_COLORS.put("darkslategrey", 0xFF2F4F4F);
        CSS_COLORS.put("darkturquoise", 0xFF00CED1);
        CSS_COLORS.put("darkviolet", 0xFF9400D3);
        CSS_COLORS.put("deeppink", 0xFFFF1493);
        CSS_COLORS.put("firebrick", 0xFFB22222);
        CSS_COLORS.put("floralwhite", 0xFFFFFAF0);
        CSS_COLORS.put("forestgreen", 0xFF228B22);
        CSS_COLORS.put("gainsboro", 0xFFDCDCDC);
        CSS_COLORS.put("ghostwhite", 0xFFF8F8FF);
        CSS_COLORS.put("goldenrod", 0xFFDAA520);
        CSS_COLORS.put("greenyellow", 0xFFADFF2F);
        CSS_COLORS.put("honeydew", 0xFFF0FFF0);
        CSS_COLORS.put("hotpink", 0xFFFF69B4);
        CSS_COLORS.put("indianred", 0xFFCD5C5C);
        CSS_COLORS.put("ivory", 0xFFFFFFF0);
        CSS_COLORS.put("khaki", 0xFFF0E68C);
        CSS_COLORS.put("lavender", 0xFFE6E6FA);
        CSS_COLORS.put("lavenderblush", 0xFFFFF0F5);
        CSS_COLORS.put("lawngreen", 0xFF7CFC00);
        CSS_COLORS.put("lemonchiffon", 0xFFFFFACD);
        CSS_COLORS.put("lightblue", 0xFFADD8E6);
        CSS_COLORS.put("lightcoral", 0xFFF08080);
        CSS_COLORS.put("lightcyan", 0xFFE0FFFF);
        CSS_COLORS.put("lightgoldenrodyellow", 0xFFFAFAD2);
        CSS_COLORS.put("lightgreen", 0xFF90EE90);
        CSS_COLORS.put("lightpink", 0xFFFFB6C1);
        CSS_COLORS.put("lightsalmon", 0xFFFFA07A);
        CSS_COLORS.put("lightseagreen", 0xFF20B2AA);
        CSS_COLORS.put("lightskyblue", 0xFF87CEFA);
        CSS_COLORS.put("lightslategray", 0xFF778899);
        CSS_COLORS.put("lightslategrey", 0xFF778899);
        CSS_COLORS.put("lightsteelblue", 0xFFB0C4DE);
        CSS_COLORS.put("lightyellow", 0xFFFFFFE0);
        CSS_COLORS.put("limegreen", 0xFF32CD32);
        CSS_COLORS.put("linen", 0xFFFAF0E6);
        CSS_COLORS.put("mediumaquamarine", 0xFF66CDAA);
        CSS_COLORS.put("mediumorchid", 0xFFBA55D3);
        CSS_COLORS.put("mediumpurple", 0xFF9370DB);
        CSS_COLORS.put("mediumseagreen", 0xFF3CB371);
        CSS_COLORS.put("mediumslateblue", 0xFF7B68EE);
        CSS_COLORS.put("mediumspringgreen", 0xFF00FA9A);
        CSS_COLORS.put("mediumturquoise", 0xFF48D1CC);
        CSS_COLORS.put("mediumvioletred", 0xFFC71585);
        CSS_COLORS.put("mintcream", 0xFFF5FFFA);
        CSS_COLORS.put("mistyrose", 0xFFFFE4E1);
        CSS_COLORS.put("moccasin", 0xFFFFE4B5);
        CSS_COLORS.put("navajowhite", 0xFFFFDEAD);
        CSS_COLORS.put("oldlace", 0xFFFDF5E6);
        CSS_COLORS.put("olivedrab", 0xFF6B8E23);
        CSS_COLORS.put("orangered", 0xFFFF4500);
        CSS_COLORS.put("orchid", 0xFFDA70D6);
        CSS_COLORS.put("palegoldenrod", 0xFFEEE8AA);
        CSS_COLORS.put("palegreen", 0xFF98FB98);
        CSS_COLORS.put("paleturquoise", 0xFFAFEEEE);
        CSS_COLORS.put("palevioletred", 0xFFDB7093);
        CSS_COLORS.put("papayawhip", 0xFFFFEFD5);
        CSS_COLORS.put("peachpuff", 0xFFFFDAB9);
        CSS_COLORS.put("peru", 0xFFCD853F);
        CSS_COLORS.put("plum", 0xFFDDA0DD);
        CSS_COLORS.put("powderblue", 0xFFB0E0E6);
        CSS_COLORS.put("rosybrown", 0xFFBC8F8F);
        CSS_COLORS.put("saddlebrown", 0xFF8B4513);
        CSS_COLORS.put("salmon", 0xFFFA8072);
        CSS_COLORS.put("sandybrown", 0xFFF4A460);
        CSS_COLORS.put("seagreen", 0xFF2E8B57);
        CSS_COLORS.put("seashell", 0xFFFFF5EE);
        CSS_COLORS.put("sienna", 0xFFA0522D);
        CSS_COLORS.put("snow", 0xFFFFFAFA);
        CSS_COLORS.put("springgreen", 0xFF00FF7F);
        CSS_COLORS.put("tan", 0xFFD2B48C);
        CSS_COLORS.put("thistle", 0xFFD8BFD8);
        CSS_COLORS.put("tomato", 0xFFFF6347);
        CSS_COLORS.put("turquoise", 0xFF40E0D0);
        CSS_COLORS.put("wheat", 0xFFF5DEB3);
        CSS_COLORS.put("whitesmoke", 0xFFF5F5F5);
        CSS_COLORS.put("yellowgreen", 0xFF9ACD32);
    }

    public WidgetCanvasStyler(Context context) {
        this.context = context.getApplicationContext();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        this.density = metrics.density;
        this.screenWidthPx = metrics.widthPixels;
        this.screenHeightPx = metrics.heightPixels;
    }

    public static synchronized WidgetCanvasStyler getInstance(Context context) {
        if (instance == null) {
            instance = new WidgetCanvasStyler(context);
        }
        return instance;
    }

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    public int dpToPx(float dp) {
        return (int) (dp * density + 0.5f);
    }

    public float pxToDp(int px) {
        return px / density;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dimension & Unit Parsing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses a CSS dimension string into actual pixels.
     * Supports: px, rem, em, %, vw, vh, pt, auto, fit-content, match_parent.
     */
    public int parseDimension(String valStr, int parentDimension, int fallback) {
        if (valStr == null) return fallback;
        String s = valStr.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || "initial".equals(s) || "inherit".equals(s)) return fallback;

        // Special keywords
        if ("auto".equals(s) || "fit-content".equals(s) || "max-content".equals(s) || "min-content".equals(s)
                || "wrap_content".equals(s)) {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        if ("100%".equals(s) || "match_parent".equals(s)) {
            return ViewGroup.LayoutParams.MATCH_PARENT;
        }

        // Percentage (e.g. 50%)
        if (s.endsWith("%")) {
            try {
                float percent = Float.parseFloat(s.substring(0, s.length() - 1).trim()) / 100f;
                if (parentDimension > 0) {
                    return Math.max(1, (int) (parentDimension * percent));
                }
                // Fallback to canvas screen width for percentage calculation
                return Math.max(1, (int) (screenWidthPx * percent));
            } catch (Exception e) {
                return fallback;
            }
        }

        // Viewport Width (vw)
        if (s.endsWith("vw")) {
            try {
                float vw = Float.parseFloat(s.substring(0, s.length() - 2).trim()) / 100f;
                return Math.max(1, (int) (screenWidthPx * vw));
            } catch (Exception e) {
                return fallback;
            }
        }

        // Viewport Height (vh)
        if (s.endsWith("vh")) {
            try {
                float vh = Float.parseFloat(s.substring(0, s.length() - 2).trim()) / 100f;
                return Math.max(1, (int) (screenHeightPx * vh));
            } catch (Exception e) {
                return fallback;
            }
        }

        // REM / EM (1rem = 16dp)
        if (s.endsWith("rem") || s.endsWith("em")) {
            try {
                String numStr = s.replaceAll("[^0-9.-]", "");
                float rem = Float.parseFloat(numStr);
                return dpToPx(rem * 16f);
            } catch (Exception e) {
                return fallback;
            }
        }

        // Points (pt)
        if (s.endsWith("pt")) {
            try {
                String numStr = s.replaceAll("[^0-9.-]", "");
                float pt = Float.parseFloat(numStr);
                return dpToPx(pt * (4f / 3f));
            } catch (Exception e) {
                return fallback;
            }
        }

        // Default: px / dp
        try {
            String numStr = s.replaceAll("[^0-9.-]", "");
            if (numStr.isEmpty()) return fallback;
            float val = Float.parseFloat(numStr);
            return dpToPx(val);
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Parses CSS 1-4 value shorthand (e.g. "10px", "10px 20px", "10px 20px 30px 40px").
     * Returns int[4] in order: [top, right, bottom, left].
     */
    public int[] parseShorthandDimensions(String shStr, int parentDimension) {
        int[] result = new int[4];
        if (shStr == null || shStr.trim().isEmpty()) return result;

        String[] parts = shStr.trim().split("\\s+");
        if (parts.length == 1) {
            int val = parseDimension(parts[0], parentDimension, 0);
            result[0] = val; // top
            result[1] = val; // right
            result[2] = val; // bottom
            result[3] = val; // left
        } else if (parts.length == 2) {
            int valV = parseDimension(parts[0], parentDimension, 0); // top & bottom
            int valH = parseDimension(parts[1], parentDimension, 0); // left & right
            result[0] = valV;
            result[1] = valH;
            result[2] = valV;
            result[3] = valH;
        } else if (parts.length == 3) {
            result[0] = parseDimension(parts[0], parentDimension, 0); // top
            int lr = parseDimension(parts[1], parentDimension, 0);    // left & right
            result[1] = lr;
            result[3] = lr;
            result[2] = parseDimension(parts[2], parentDimension, 0); // bottom
        } else if (parts.length >= 4) {
            result[0] = parseDimension(parts[0], parentDimension, 0); // top
            result[1] = parseDimension(parts[1], parentDimension, 0); // right
            result[2] = parseDimension(parts[2], parentDimension, 0); // bottom
            result[3] = parseDimension(parts[3], parentDimension, 0); // left
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Color Parsing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses any CSS color string into an Android @ColorInt value.
     * Supports:
     * - #RGB, #RGBA, #RRGGBB, #RRGGBBAA
     * - rgb(r, g, b), rgba(r, g, b, a)
     * - hsl(h, s, l), hsla(h, s, l, a)
     * - CSS named colors (140+ colors)
     * - var(--...) CSS variables resolved via ThemeManager
     */
    public int parseColor(String colorStr, int fallback) {
        if (colorStr == null) return fallback;
        String s = colorStr.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || "none".equals(s)) return fallback;

        // Resolve CSS Variables
        if (s.startsWith("var(")) {
            String resolved = resolveCssVariable(s);
            if (resolved != null && !resolved.equals(s)) {
                return parseColor(resolved, fallback);
            }
        }

        // CSS named colors
        if (CSS_COLORS.containsKey(s)) {
            return CSS_COLORS.get(s);
        }

        // Hex Colors (#rgb, #rgba, #rrggbb, #rrggbbaa)
        if (s.startsWith("#")) {
            String hex = s.substring(1);
            if (hex.length() == 3) {
                // #rgb -> #rrggbb
                char r = hex.charAt(0);
                char g = hex.charAt(1);
                char b = hex.charAt(2);
                hex = "" + r + r + g + g + b + b;
                try {
                    return Color.parseColor("#" + hex);
                } catch (Exception ignored) {}
            } else if (hex.length() == 4) {
                // #rgba -> #aarrggbb
                char r = hex.charAt(0);
                char g = hex.charAt(1);
                char b = hex.charAt(2);
                char a = hex.charAt(3);
                hex = "" + a + a + r + r + g + g + b + b;
                try {
                    return Color.parseColor("#" + hex);
                } catch (Exception ignored) {}
            } else if (hex.length() == 6) {
                try {
                    return Color.parseColor("#" + hex);
                } catch (Exception ignored) {}
            } else if (hex.length() == 8) {
                // CSS #RRGGBBAA -> Android #AARRGGBB
                String rrggbb = hex.substring(0, 6);
                String aa = hex.substring(6, 8);
                try {
                    return Color.parseColor("#" + aa + rrggbb);
                } catch (Exception ignored) {}
            }
        }

        // rgb(...) / rgba(...)
        if (s.startsWith("rgb")) {
            try {
                int start = s.indexOf('(');
                int end = s.indexOf(')');
                if (start != -1 && end != -1) {
                    String[] parts = s.substring(start + 1, end).split("[,/\\s]+");
                    if (parts.length >= 3) {
                        int r = clampColor(parseColorComponent(parts[0], 255));
                        int g = clampColor(parseColorComponent(parts[1], 255));
                        int b = clampColor(parseColorComponent(parts[2], 255));
                        int a = 255;
                        if (parts.length >= 4) {
                            float alphaVal = Float.parseFloat(parts[3].replace("%", "").trim());
                            if (parts[3].contains("%")) {
                                a = clampColor(Math.round(alphaVal * 2.55f));
                            } else if (alphaVal <= 1.0f) {
                                a = clampColor(Math.round(alphaVal * 255f));
                            } else {
                                a = clampColor(Math.round(alphaVal));
                            }
                        }
                        return Color.argb(a, r, g, b);
                    }
                }
            } catch (Exception ignored) {}
        }

        // hsl(...) / hsla(...)
        if (s.startsWith("hsl")) {
            try {
                int start = s.indexOf('(');
                int end = s.indexOf(')');
                if (start != -1 && end != -1) {
                    String[] parts = s.substring(start + 1, end).split("[,/\\s]+");
                    if (parts.length >= 3) {
                        float h = Float.parseFloat(parts[0].replaceAll("[^0-9.]", ""));
                        float sat = Float.parseFloat(parts[1].replaceAll("[^0-9.]", "")) / 100f;
                        float lum = Float.parseFloat(parts[2].replaceAll("[^0-9.]", "")) / 100f;
                        int a = 255;
                        if (parts.length >= 4) {
                            float alphaVal = Float.parseFloat(parts[3].replace("%", "").trim());
                            if (parts[3].contains("%")) {
                                a = clampColor(Math.round(alphaVal * 2.55f));
                            } else if (alphaVal <= 1.0f) {
                                a = clampColor(Math.round(alphaVal * 255f));
                            } else {
                                a = clampColor(Math.round(alphaVal));
                            }
                        }
                        int rgb = hslToRgb(h, sat, lum);
                        return Color.argb(a, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
                    }
                }
            } catch (Exception ignored) {}
        }

        return fallback;
    }

    private int parseColorComponent(String s, int max) {
        s = s.trim();
        if (s.endsWith("%")) {
            float p = Float.parseFloat(s.substring(0, s.length() - 1));
            return Math.round((p / 100f) * max);
        }
        return Math.round(Float.parseFloat(s));
    }

    private int clampColor(int val) {
        return Math.max(0, Math.min(255, val));
    }

    private int hslToRgb(float h, float s, float l) {
        float r, g, b;
        if (s == 0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
            float p = 2f * l - q;
            r = hueToRgb(p, q, h / 360f + 1f / 3f);
            g = hueToRgb(p, q, h / 360f);
            b = hueToRgb(p, q, h / 360f - 1f / 3f);
        }
        return Color.rgb(clampColor(Math.round(r * 255f)),
                clampColor(Math.round(g * 255f)),
                clampColor(Math.round(b * 255f)));
    }

    private float hueToRgb(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }

    private String resolveCssVariable(String varStr) {
        if (themeManager == null) return null;
        Pattern p = Pattern.compile("var\\(\\s*--([a-zA-Z0-9_-]+)\\s*\\)");
        Matcher m = p.matcher(varStr);
        if (m.find()) {
            String varName = m.group(1);
            Map<String, String> customVars = themeManager.getCustomCssVars();
            if (customVars != null && customVars.containsKey("--" + varName)) {
                return customVars.get("--" + varName);
            }
            Map<String, String> styles = themeManager.getAllStyles();
            if (styles != null) {
                // Try camelCase equivalents (e.g. primary-color -> primaryColor)
                String camel = kebabToCamel(varName);
                if (styles.containsKey(camel)) return styles.get(camel);
                if (styles.containsKey(varName)) return styles.get(varName);
            }
        }
        return null;
    }

    private String kebabToCamel(String kebab) {
        if (kebab == null || !kebab.contains("-")) return kebab;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : kebab.toCharArray()) {
            if (c == '-') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTML / Tag Classification
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isLayoutContainer(String tag) {
        if (tag == null) return false;
        switch (tag.toLowerCase(Locale.ROOT)) {
            case "div":
            case "section":
            case "nav":
            case "header":
            case "footer":
            case "main":
            case "article":
            case "aside":
            case "form":
            case "ul":
            case "ol":
            case "li":
            case "table":
                return true;
            default:
                return false;
        }
    }

    public boolean isBlockTag(String tag) {
        if (tag == null) return false;
        switch (tag.toLowerCase(Locale.ROOT)) {
            case "p":
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
            case "blockquote":
            case "pre":
            case "hr":
            case "div":
            case "section":
            case "nav":
            case "header":
            case "footer":
            case "main":
            case "article":
            case "aside":
            case "form":
            case "ul":
            case "ol":
            case "li":
            case "table":
                return true;
            default:
                return false;
        }
    }

    public boolean isInlineTag(String tag) {
        if (tag == null) return true;
        switch (tag.toLowerCase(Locale.ROOT)) {
            case "span":
            case "a":
            case "button":
            case "input":
            case "img":
            case "label":
            case "i":
            case "b":
            case "strong":
            case "em":
            case "textarea":
            case "select":
            case "br":
            case "svg":
            case "canvas":
                return true;
            default:
                return !isBlockTag(tag);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core Style & Property Application
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies both attributes and CSS styles from widgetMap to the target Android View.
     */
    public void applyProperties(View view, Map<String, Object> widgetMap) {
        if (view == null || widgetMap == null) return;

        String tag = widgetMap.containsKey("tag") && widgetMap.get("tag") != null
                ? widgetMap.get("tag").toString() : "";

        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
        if (function == null) return;

        // Base Tag Content
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (function.containsKey("text") && function.get("text") != null) {
                tv.setText(function.get("text").toString());
            } else if ("i".equalsIgnoreCase(tag) && function.containsKey("class") && function.get("class") != null) {
                tv.setText("[" + function.get("class").toString() + "]");
            }
        }

        if (view instanceof TextInputEditText) {
            TextInputEditText et = (TextInputEditText) view;
            if (function.containsKey("placeholder") && function.get("placeholder") != null) {
                et.setHint(function.get("placeholder").toString());
            }
            if (function.containsKey("value") && function.get("value") != null) {
                et.setText(function.get("value").toString());
            }
        }

        if (view instanceof ImageView) {
            ImageView iv = (ImageView) view;
            if (function.containsKey("src") && function.get("src") != null) {
                loadImageViewSource(iv, function.get("src").toString());
            } else {
                iv.setImageResource(R.drawable.photo);
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> style = (Map<String, Object>) function.get("style");
        if (style != null) {
            applyStyles(view, tag, style);
        }
    }

    /**
     * Applies full CSS styles to the view, updating layout parameters, borders,
     * colors, typography, and flex layout.
     */
    public void applyStyles(View view, String tag, Map<String, Object> rawStyle) {
        if (view == null || rawStyle == null) return;

        // Clean out !important suffix
        Map<String, Object> style = new HashMap<>();
        for (Map.Entry<String, Object> entry : rawStyle.entrySet()) {
            if (entry.getValue() != null) {
                String valStr = entry.getValue().toString().replaceAll("(?i)\\s*!\\s*important\\s*$", "").trim();
                style.put(entry.getKey(), valStr);
            }
        }

        ViewGroup.LayoutParams existingParams = view.getLayoutParams();
        LinearLayout.LayoutParams params;
        if (existingParams instanceof LinearLayout.LayoutParams) {
            params = (LinearLayout.LayoutParams) existingParams;
        } else if (existingParams != null) {
            params = new LinearLayout.LayoutParams(existingParams.width, existingParams.height);
        } else {
            params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        // Determine parent orientation if view is attached
        boolean isInsideHorizontalParent = false;
        ViewGroup parentVg = null;
        if (view.getParent() instanceof ViewGroup) {
            parentVg = (ViewGroup) view.getParent();
            if (parentVg instanceof LinearLayout) {
                isInsideHorizontalParent = ((LinearLayout) parentVg).getOrientation() == LinearLayout.HORIZONTAL;
            }
        }

        // 1. Width Calculation
        if (style.containsKey("width")) {
            String wStr = style.get("width").toString();
            params.width = parseDimension(wStr, screenWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            // Default width rule:
            // - Inside a horizontal flex row: always WRAP_CONTENT so children don't hog the row!
            // - In vertical layout: block tags take MATCH_PARENT, inline tags (buttons, inputs) take WRAP_CONTENT!
            if (isInsideHorizontalParent) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else if (isBlockTag(tag)) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            } else {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
        }

        // 2. Height Calculation
        if (style.containsKey("height")) {
            String hStr = style.get("height").toString();
            params.height = parseDimension(hStr, screenHeightPx, ViewGroup.LayoutParams.WRAP_CONTENT);
            // If explicit height is given, remove any artificial minimum height
            view.setMinimumHeight(0);
        } else {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            if (isLayoutContainer(tag)) {
                // If it's a container and has no children yet, keep small minHeight so user can drop widgets
                if (view instanceof ViewGroup && ((ViewGroup) view).getChildCount() == 0) {
                    view.setMinimumHeight(dpToPx(48));
                } else {
                    view.setMinimumHeight(0);
                }
            } else {
                view.setMinimumHeight(0);
            }
        }

        // 3. Flex Weight & Flex-Grow
        if (style.containsKey("flexGrow") || style.containsKey("flex")) {
            try {
                String flexStr = style.containsKey("flexGrow") ? style.get("flexGrow").toString() : style.get("flex").toString();
                float weight = Float.parseFloat(flexStr.replaceAll("[^0-9.]", ""));
                if (weight > 0) {
                    params.weight = weight;
                    if (isInsideHorizontalParent) {
                        params.width = 0;
                    } else {
                        params.height = 0;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4. Margins
        if (style.containsKey("margin")) {
            int[] margins = parseShorthandDimensions(style.get("margin").toString(), screenWidthPx);
            params.setMargins(margins[3], margins[0], margins[1], margins[2]);
        } else {
            int mt = style.containsKey("marginTop") ? parseDimension(style.get("marginTop").toString(), screenWidthPx, 0) : params.topMargin;
            int mb = style.containsKey("marginBottom") ? parseDimension(style.get("marginBottom").toString(), screenWidthPx, 0) : params.bottomMargin;
            int ml = style.containsKey("marginLeft") ? parseDimension(style.get("marginLeft").toString(), screenWidthPx, 0) : params.leftMargin;
            int mr = style.containsKey("marginRight") ? parseDimension(style.get("marginRight").toString(), screenWidthPx, 0) : params.rightMargin;
            params.setMargins(ml, mt, mr, mb);
        }

        view.setLayoutParams(params);

        // 5. Background, Borders, Radius
        applyBackgroundAndBorders(view, tag, style);

        // 6. Padding
        if (style.containsKey("padding")) {
            int[] paddings = parseShorthandDimensions(style.get("padding").toString(), screenWidthPx);
            view.setPadding(paddings[3], paddings[0], paddings[1], paddings[2]);
        } else {
            if ("button".equalsIgnoreCase(tag)) {
                view.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
            } else if ("input".equalsIgnoreCase(tag)) {
                view.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
            } else if ("textarea".equalsIgnoreCase(tag)) {
                view.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            }
        }

        // 7. Typography (TextView / Buttons / Inputs)
        if (view instanceof TextView) {
            applyTypography((TextView) view, style);
        }

        // 8. Opacity & Visibility
        if (style.containsKey("display")) {
            String disp = style.get("display").toString().trim().toLowerCase(Locale.ROOT);
            if ("none".equals(disp)) {
                view.setVisibility(View.GONE);
            } else {
                view.setVisibility(View.VISIBLE);
            }
        }
        if (style.containsKey("opacity")) {
            try {
                float op = Float.parseFloat(style.get("opacity").toString().trim());
                view.setAlpha(Math.max(0f, Math.min(1f, op)));
            } catch (Exception ignored) {}
        }
        if (style.containsKey("elevation")) {
            try {
                float elev = parseDimension(style.get("elevation").toString(), 0, 0);
                view.setElevation(elev);
            } catch (Exception ignored) {}
        }
        if (style.containsKey("transform")) {
            String transform = style.get("transform").toString();
            if (transform.contains("rotate(")) {
                try {
                    String deg = transform.replaceAll("[^0-9.-]", "");
                    view.setRotation(Float.parseFloat(deg));
                } catch (Exception ignored) {}
            }
        }
        if (view instanceof ImageView && style.containsKey("objectFit")) {
            String fit = style.get("objectFit").toString().toLowerCase(Locale.ROOT);
            switch (fit) {
                case "cover": ((ImageView) view).setScaleType(ImageView.ScaleType.CENTER_CROP); break;
                case "contain": ((ImageView) view).setScaleType(ImageView.ScaleType.FIT_CENTER); break;
                case "fill": ((ImageView) view).setScaleType(ImageView.ScaleType.FIT_XY); break;
                case "none": ((ImageView) view).setScaleType(ImageView.ScaleType.CENTER); break;
                case "scale-down": ((ImageView) view).setScaleType(ImageView.ScaleType.CENTER_INSIDE); break;
                default: ((ImageView) view).setScaleType(ImageView.ScaleType.FIT_CENTER); break;
            }
        }

        // 9. Flex Container Layout (Orientation, Alignment, Children adjustment)
        if (view instanceof ViewGroup) {
            applyFlexContainerStyles((ViewGroup) view, style);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Backgrounds, Borders & Corner Radius
    // ─────────────────────────────────────────────────────────────────────────

    private void applyBackgroundAndBorders(View view, String tag, Map<String, Object> style) {
        GradientDrawable shape = new GradientDrawable();

        // Tag default backgrounds & borders
        int bgColor = Color.TRANSPARENT;
        int borderColor = Color.TRANSPARENT;
        int borderWidth = 0;
        float cornerRadius = 0;

        boolean isContainer = isLayoutContainer(tag);

        if ("button".equalsIgnoreCase(tag)) {
            bgColor = Color.parseColor("#EEEEEE");
            borderColor = Color.parseColor("#B0BEC5");
            borderWidth = dpToPx(1);
            cornerRadius = dpToPx(4);
        } else if ("input".equalsIgnoreCase(tag) || "textarea".equalsIgnoreCase(tag)) {
            bgColor = Color.WHITE;
            borderColor = Color.parseColor("#B0BEC5");
            borderWidth = dpToPx(1);
            cornerRadius = dpToPx(4);
        } else if (isContainer) {
            // Subtle dashed guideline border for canvas containers when no border is set
            borderColor = Color.parseColor("#BDBDBD");
            borderWidth = dpToPx(1);
        }

        // Custom Background Color
        if (style.containsKey("backgroundColor")) {
            bgColor = parseColor(style.get("backgroundColor").toString(), bgColor);
        } else if (style.containsKey("background")) {
            String bg = style.get("background").toString().trim();
            if (!bg.contains("url(") && !bg.contains("linear-gradient")) {
                bgColor = parseColor(bg, bgColor);
            }
        }
        shape.setColor(bgColor);

        // Border Width & Color
        if (style.containsKey("border")) {
            String b = style.get("border").toString().trim();
            // Parse e.g. "2px solid #ff0000"
            String[] parts = b.split("\\s+");
            if (parts.length >= 1) borderWidth = parseDimension(parts[0], 0, borderWidth);
            if (parts.length >= 3) borderColor = parseColor(parts[2], borderColor);
        } else {
            if (style.containsKey("borderWidth")) {
                borderWidth = parseDimension(style.get("borderWidth").toString(), 0, borderWidth);
            }
            if (style.containsKey("borderColor")) {
                borderColor = parseColor(style.get("borderColor").toString(), borderColor);
            }
        }

        // Stroke style (dashed/dotted for containers without custom border)
        String borderStyle = style.containsKey("borderStyle") ? style.get("borderStyle").toString().toLowerCase(Locale.ROOT) : "";
        if ("dashed".equals(borderStyle) || (isContainer && !style.containsKey("border") && !style.containsKey("borderWidth"))) {
            shape.setStroke(borderWidth, borderColor, dpToPx(6), dpToPx(4));
        } else if ("dotted".equals(borderStyle)) {
            shape.setStroke(borderWidth, borderColor, dpToPx(2), dpToPx(2));
        } else {
            shape.setStroke(borderWidth, borderColor);
        }

        // Corner Radius
        if (style.containsKey("borderRadius")) {
            String rStr = style.get("borderRadius").toString().trim();
            if ("50%".equals(rStr)) {
                // Circular pill (e.g. rounded avatars, pills)
                cornerRadius = dpToPx(1000);
                shape.setCornerRadius(cornerRadius);
            } else {
                int[] radii = parseShorthandDimensions(rStr, 0);
                if (radii[0] == radii[1] && radii[1] == radii[2] && radii[2] == radii[3]) {
                    cornerRadius = radii[0];
                    shape.setCornerRadius(cornerRadius);
                } else {
                    float tl = radii[0];
                    float tr = radii[1];
                    float br = radii[2];
                    float bl = radii[3];
                    shape.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
                }
            }
        } else {
            // Per-corner properties
            float tl = style.containsKey("borderTopLeftRadius") ? parseDimension(style.get("borderTopLeftRadius").toString(), 0, 0) : cornerRadius;
            float tr = style.containsKey("borderTopRightRadius") ? parseDimension(style.get("borderTopRightRadius").toString(), 0, 0) : cornerRadius;
            float br = style.containsKey("borderBottomRightRadius") ? parseDimension(style.get("borderBottomRightRadius").toString(), 0, 0) : cornerRadius;
            float bl = style.containsKey("borderBottomLeftRadius") ? parseDimension(style.get("borderBottomLeftRadius").toString(), 0, 0) : cornerRadius;
            if (tl > 0 || tr > 0 || br > 0 || bl > 0) {
                shape.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
            } else if (cornerRadius > 0) {
                shape.setCornerRadius(cornerRadius);
            }
        }

        view.setBackground(shape);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Typography
    // ─────────────────────────────────────────────────────────────────────────

    private void applyTypography(TextView tv, Map<String, Object> style) {
        // Text Color
        if (style.containsKey("color")) {
            int tc = parseColor(style.get("color").toString(), Color.parseColor("#37474F"));
            tv.setTextColor(tc);
        }

        // Font Size
        if (style.containsKey("fontSize")) {
            int sizePx = parseDimension(style.get("fontSize").toString(), 0, dpToPx(13));
            if (sizePx > 0) {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
            }
        }

        // Font Weight
        if (style.containsKey("fontWeight")) {
            String weight = style.get("fontWeight").toString().toLowerCase(Locale.ROOT);
            if ("bold".equals(weight) || "700".equals(weight) || "800".equals(weight) || "900".equals(weight)) {
                tv.setTypeface(null, Typeface.BOLD);
            } else if ("italic".equals(weight)) {
                tv.setTypeface(null, Typeface.ITALIC);
            } else {
                tv.setTypeface(null, Typeface.NORMAL);
            }
        }

        // Text Align
        if (style.containsKey("textAlign")) {
            String align = style.get("textAlign").toString().toLowerCase(Locale.ROOT);
            switch (align) {
                case "center":
                    tv.setGravity(Gravity.CENTER);
                    break;
                case "right":
                case "end":
                    tv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                    break;
                case "left":
                case "start":
                default:
                    tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    break;
            }
        }

        // Text Transform
        if (style.containsKey("textTransform")) {
            String transform = style.get("textTransform").toString().toLowerCase(Locale.ROOT);
            CharSequence text = tv.getText();
            if (text != null && text.length() > 0) {
                if ("uppercase".equals(transform)) {
                    tv.setText(text.toString().toUpperCase(Locale.ROOT));
                } else if ("lowercase".equals(transform)) {
                    tv.setText(text.toString().toLowerCase(Locale.ROOT));
                }
            }
        }

        // Text Decoration
        if (style.containsKey("textDecoration")) {
            String dec = style.get("textDecoration").toString().toLowerCase(Locale.ROOT);
            if (dec.contains("underline")) {
                tv.setPaintFlags(tv.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            } else if (dec.contains("line-through")) {
                tv.setPaintFlags(tv.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Flexbox Layout & Orientation Control
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Configures a container's flexbox behavior.
     * When flexDirection is "row", orientation switches to HORIZONTAL, and all children
     * are automatically adapted so they do NOT stretch to MATCH_PARENT width,
     * allowing them to render side-by-side naturally as in a web browser.
     */
    public void applyFlexContainerStyles(ViewGroup container, Map<String, Object> style) {
        if (!(container instanceof LinearLayout)) return;
        LinearLayout layout = (LinearLayout) container;

        boolean isRow = false;

        // Check explicit flexDirection
        if (style.containsKey("flexDirection") || style.containsKey("flex-direction")) {
            String dir = (style.containsKey("flexDirection")
                    ? style.get("flexDirection") : style.get("flex-direction")).toString().trim().toLowerCase(Locale.ROOT);
            isRow = "row".equals(dir) || "row-reverse".equals(dir);
        } else if (style.containsKey("display")) {
            String disp = style.get("display").toString().trim().toLowerCase(Locale.ROOT);
            // In CSS flexbox, default flex-direction is "row"!
            if ("flex".equals(disp) || "inline-flex".equals(disp)) {
                isRow = true;
            }
        }

        // 1. Update Container Orientation
        layout.setOrientation(isRow ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        // 2. Alignment & Justify Content
        int gravity = Gravity.NO_GRAVITY;
        if (style.containsKey("justifyContent")) {
            String jc = style.get("justifyContent").toString().toLowerCase(Locale.ROOT);
            switch (jc) {
                case "center":
                    gravity |= isRow ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL;
                    break;
                case "flex-end":
                case "end":
                    gravity |= isRow ? Gravity.END : Gravity.BOTTOM;
                    break;
                case "flex-start":
                case "start":
                default:
                    gravity |= isRow ? Gravity.START : Gravity.TOP;
                    break;
            }
        }
        if (style.containsKey("alignItems")) {
            String ai = style.get("alignItems").toString().toLowerCase(Locale.ROOT);
            switch (ai) {
                case "center":
                    gravity |= isRow ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL;
                    break;
                case "flex-end":
                case "end":
                    gravity |= isRow ? Gravity.BOTTOM : Gravity.END;
                    break;
                case "flex-start":
                case "start":
                default:
                    gravity |= isRow ? Gravity.TOP : Gravity.START;
                    break;
            }
        }
        if (gravity != Gravity.NO_GRAVITY) {
            layout.setGravity(gravity);
        }

        // 3. Gap (spacing between children)
        int gapPx = 0;
        if (style.containsKey("gap")) {
            gapPx = parseDimension(style.get("gap").toString(), 0, 0);
        }

        // 4. CRITICAL: Normalize Children LayoutParams so row items sit side-by-side!
        normalizeChildren(layout, isRow, gapPx);
    }

    /**
     * Re-evaluates container orientation and normalizes all direct children.
     * Call this whenever children are added, removed, or their dimensions/styles change.
     */
    public void normalizeFlexContainer(ViewGroup container) {
        if (!(container instanceof LinearLayout)) return;
        LinearLayout layout = (LinearLayout) container;

        boolean isRow = false;
        int gapPx = 0;

        if (layout.getTag() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) layout.getTag();
            if (map.containsKey("function") && map.get("function") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fn = (Map<String, Object>) map.get("function");
                if (fn.containsKey("style") && fn.get("style") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> st = (Map<String, Object>) fn.get("style");

                    if (st.containsKey("flexDirection") || st.containsKey("flex-direction")) {
                        String dir = (st.containsKey("flexDirection")
                                ? st.get("flexDirection") : st.get("flex-direction")).toString().trim().toLowerCase(Locale.ROOT);
                        isRow = "row".equals(dir) || "row-reverse".equals(dir);
                    } else if (st.containsKey("display")) {
                        String disp = st.get("display").toString().trim().toLowerCase(Locale.ROOT);
                        if ("flex".equals(disp) || "inline-flex".equals(disp)) {
                            isRow = true;
                        }
                    } else {
                        isRow = layout.getOrientation() == LinearLayout.HORIZONTAL;
                    }

                    if (st.containsKey("gap")) {
                        gapPx = parseDimension(st.get("gap").toString(), 0, 0);
                    }
                }
            }
        } else {
            isRow = layout.getOrientation() == LinearLayout.HORIZONTAL;
        }

        layout.setOrientation(isRow ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        normalizeChildren(layout, isRow, gapPx);
    }

    /**
     * Loops through all direct children of container, adjusting width, height, weights, and margins
     * according to the container's orientation.
     */
    public void normalizeChildren(LinearLayout container, boolean isRow, int gapPx) {
        int childCount = container.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = container.getChildAt(i);
            ViewGroup.LayoutParams existing = child.getLayoutParams();
            LinearLayout.LayoutParams lp;
            if (existing instanceof LinearLayout.LayoutParams) {
                lp = (LinearLayout.LayoutParams) existing;
            } else if (existing != null) {
                lp = new LinearLayout.LayoutParams(existing.width, existing.height);
            } else {
                lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            // Inspect child's own style map
            Map<String, Object> childStyle = null;
            String childTag = "";
            if (child.getTag() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> childMap = (Map<String, Object>) child.getTag();
                if (childMap.containsKey("tag") && childMap.get("tag") != null) {
                    childTag = childMap.get("tag").toString();
                }
                if (childMap.containsKey("function") && childMap.get("function") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fn = (Map<String, Object>) childMap.get("function");
                    if (fn.containsKey("style") && fn.get("style") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> st = (Map<String, Object>) fn.get("style");
                        childStyle = st;
                    }
                }
            }

            boolean hasExplicitWidth = childStyle != null && childStyle.containsKey("width");
            boolean hasExplicitHeight = childStyle != null && childStyle.containsKey("height");

            if (isRow) {
                // HORIZONTAL FLEX ROW
                // If child does not have an explicit width, it MUST NOT be MATCH_PARENT!
                if (!hasExplicitWidth) {
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                } else {
                    lp.width = parseDimension(childStyle.get("width").toString(), screenWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
                }
                if (!hasExplicitHeight) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                } else {
                    lp.height = parseDimension(childStyle.get("height").toString(), screenHeightPx, ViewGroup.LayoutParams.WRAP_CONTENT);
                }

                // Check flex / flexGrow on child
                if (childStyle != null && (childStyle.containsKey("flexGrow") || childStyle.containsKey("flex"))) {
                    try {
                        String flexStr = childStyle.containsKey("flexGrow") ? childStyle.get("flexGrow").toString() : childStyle.get("flex").toString();
                        float weight = Float.parseFloat(flexStr.replaceAll("[^0-9.]", ""));
                        if (weight > 0) {
                            lp.weight = weight;
                            lp.width = 0;
                        } else {
                            lp.weight = 0;
                        }
                    } catch (Exception ignored) {
                        lp.weight = 0;
                    }
                } else {
                    lp.weight = 0;
                }

                // Apply margins and gap
                if (childStyle != null && childStyle.containsKey("margin")) {
                    int[] margins = parseShorthandDimensions(childStyle.get("margin").toString(), screenWidthPx);
                    int lm = margins[3] + (gapPx > 0 && i > 0 ? gapPx : 0);
                    lp.setMargins(lm, margins[0], margins[1], margins[2]);
                } else {
                    int leftMargin = (gapPx > 0 && i > 0) ? gapPx : 0;
                    lp.setMargins(leftMargin, 0, 0, 0);
                }

                // If container child is empty, ensure it has minimum dimensions so it's visible & droppable
                if (isLayoutContainer(childTag)) {
                    if (child instanceof ViewGroup && ((ViewGroup) child).getChildCount() == 0) {
                        child.setMinimumWidth(dpToPx(48));
                        child.setMinimumHeight(dpToPx(48));
                    } else {
                        child.setMinimumWidth(0);
                    }
                }
            } else {
                // VERTICAL CONTAINER
                if (!hasExplicitWidth) {
                    if (isBlockTag(childTag)) {
                        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    } else {
                        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    }
                } else {
                    lp.width = parseDimension(childStyle.get("width").toString(), screenWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
                }
                if (!hasExplicitHeight) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                } else {
                    lp.height = parseDimension(childStyle.get("height").toString(), screenHeightPx, ViewGroup.LayoutParams.WRAP_CONTENT);
                }

                // Check flex / flexGrow on child in vertical
                if (childStyle != null && (childStyle.containsKey("flexGrow") || childStyle.containsKey("flex"))) {
                    try {
                        String flexStr = childStyle.containsKey("flexGrow") ? childStyle.get("flexGrow").toString() : childStyle.get("flex").toString();
                        float weight = Float.parseFloat(flexStr.replaceAll("[^0-9.]", ""));
                        if (weight > 0) {
                            lp.weight = weight;
                            lp.height = 0;
                        } else {
                            lp.weight = 0;
                        }
                    } catch (Exception ignored) {
                        lp.weight = 0;
                    }
                } else {
                    lp.weight = 0;
                }

                // Apply margins and gap
                if (childStyle != null && childStyle.containsKey("margin")) {
                    int[] margins = parseShorthandDimensions(childStyle.get("margin").toString(), screenWidthPx);
                    int tm = margins[0] + (gapPx > 0 && i > 0 ? gapPx : 0);
                    lp.setMargins(margins[3], tm, margins[1], margins[2]);
                } else {
                    int topMargin = (gapPx > 0 && i > 0) ? gapPx : 0;
                    lp.setMargins(0, topMargin, 0, 0);
                }

                if (isLayoutContainer(childTag)) {
                    if (child instanceof ViewGroup && ((ViewGroup) child).getChildCount() == 0) {
                        child.setMinimumHeight(dpToPx(48));
                    }
                }
            }

            child.setLayoutParams(lp);
        }
        container.requestLayout();
    }

    /**
     * Recursively traverses and synchronizes the entire canvas hierarchy.
     */
    public void synchronizeCanvas(View root) {
        if (root != null && root.getId() == R.id.screen) {
            String bg = (themeManager != null) ? themeManager.getGlobalStyle("bodyBackground") : "#FFFFFF";
            if (bg == null || bg.isEmpty()) bg = "#FFFFFF";
            try {
                root.setBackgroundColor(parseColor(bg, Color.WHITE));
            } catch (Exception ignored) {}
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            if (vg.getTag() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) vg.getTag();
                applyProperties(vg, map);
            }
            normalizeFlexContainer(vg);
            for (int i = 0; i < vg.getChildCount(); i++) {
                synchronizeCanvas(vg.getChildAt(i));
            }
        } else if (root != null && root.getTag() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) root.getTag();
            applyProperties(root, map);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Image Loading Helpers
    // ─────────────────────────────────────────────────────────────────────────

    public void loadImageViewSource(ImageView iv, String src) {
        if (src == null || src.isEmpty()) {
            iv.setImageResource(R.drawable.photo);
            return;
        }
        if (src.startsWith("data:")) {
            try {
                String base64 = src.substring(src.indexOf(",") + 1);
                byte[] decoded = Base64.decode(base64, Base64.DEFAULT);
                Bitmap bmp = decodeSampledBitmapFromByteArray(decoded, 1024, 1024);
                if (bmp != null) {
                    iv.setImageBitmap(bmp);
                } else {
                    iv.setImageResource(R.drawable.photo);
                }
            } catch (Exception e) {
                iv.setImageResource(R.drawable.photo);
            }
        } else {
            try {
                Bitmap bmp = decodeSampledBitmapFromFile(src, 1024, 1024);
                if (bmp != null) {
                    iv.setImageBitmap(bmp);
                } else {
                    iv.setImageResource(R.drawable.photo);
                }
            } catch (Exception e) {
                iv.setImageResource(R.drawable.photo);
            }
        }
    }

    private static Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    private static Bitmap decodeSampledBitmapFromByteArray(byte[] data, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
