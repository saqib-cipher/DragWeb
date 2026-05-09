package sketchweb.gl;

import android.graphics.Color;

/**
 * Single source of truth for category colors used across the vertical block
 * editor. Mirrors the Sketchware palette (warm event blocks, blue CSS, green
 * HTML, rose logic, teal variables, purple values, slate ASD) so block bodies,
 * palette pills and code-side highlights all match visually.
 */
final class BlockCategoryPalette {

    private BlockCategoryPalette() {}

    static String colorForCategory(String category) {
        if (category == null) return "#607D8B";
        switch (category) {
            case "event":     return "#FF9800";
            case "css":       return "#2196F3";
            case "html":      return "#4CAF50";
            case "logic":     return "#E91E63";
            case "variable":  return "#00BCD4";
            case "animation": return "#9C27B0";
            case "asd":       return "#455A64";
            case "value":     return "#7E57C2";
            default:          return "#607D8B";
        }
    }

    static int colorIntForCategory(String category) {
        return Color.parseColor(colorForCategory(category));
    }

    static int darken(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.78f;
        return Color.HSVToColor(hsv);
    }

    static int lighten(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] / Math.max(0.01f, factor));
        return Color.HSVToColor(hsv);
    }
}
