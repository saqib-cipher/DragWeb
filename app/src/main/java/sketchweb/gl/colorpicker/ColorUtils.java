package sketchweb.gl.colorpicker;

import android.graphics.Color;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class ColorUtils {
	
	private static final Map<String, String> categoryColors = new HashMap<String, String>() {{
			put("custom", "808080");      // серый Color.GRAY в HEX #808080
			put("theme", "2196F3");
			put("red", "F44336");
			put("pink", "E91E63");
			put("purple", "9C27B0");
			put("deep purple", "673AB7");
			put("indigo", "3F51B5");
			put("blue", "2196F3");
			put("light blue", "03A9F4");
			put("cyan", "00BCD4");
			put("teal", "009688");
			put("green", "4CAF50");
			put("light green", "8BC34A");
			put("lime", "CDDC39");
			put("yellow", "FFEB3B");
			put("amber", "FFC107");
			put("orange", "FF9800");
			put("deep orange", "FF5722");
			put("brown", "795548");
			put("grey", "9E9E9E");
			put("blue gray", "607D8B");
			put("black", "000000");
			put("white", "FFFFFF");
		}};
	
	public static int getColorFromCategory(String category) {
		if (category == null || category.trim().isEmpty()) {
			return Color.LTGRAY;
		}
		
		String cat = category.trim().toLowerCase();
		
		// Если категория - HEX цвет с #
		try {
			if (cat.startsWith("#")) {
				return Color.parseColor(cat);
			}
		} catch (IllegalArgumentException ignored) {}
		
		// Ищем в карте
		String hex = categoryColors.get(cat);
		if (hex != null) {
			try {
				return Color.parseColor("#" + hex);
			} catch (IllegalArgumentException ignored) {}
		}
		
		// По умолчанию
		return Color.LTGRAY;
	}
	
	
	
	public static int getContrastingTextColor(int backgroundColor) {
		double r = Color.red(backgroundColor) / 255.0;
		double g = Color.green(backgroundColor) / 255.0;
		double b = Color.blue(backgroundColor) / 255.0;
		
		r = (r <= 0.03928) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);  
		g = (g <= 0.03928) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);  
		b = (b <= 0.03928) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);  
		
		double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;  
		
		return luminance > 0.5 ? Color.parseColor("#212121") : Color.WHITE;  
	}  
	
	
	public static int parseHexColorSafe(String input) {
		if (input == null || input.trim().isEmpty()) {
			return Color.LTGRAY;
		}
		
		String hex = input.trim().toUpperCase();
		
		if (!hex.startsWith("#")) {
			hex = "#" + hex;
		}
		
		if (!hex.matches("^#([A-F0-9]{3}|[A-F0-9]{4}|[A-F0-9]{6}|[A-F0-9]{8})$")) {
			return Color.LTGRAY;
		}
		
		if (hex.length() == 4) {
			hex = "#" + hex.charAt(1) + hex.charAt(1)
			+ hex.charAt(2) + hex.charAt(2)
			+ hex.charAt(3) + hex.charAt(3);
		} else if (hex.length() == 5) {
			hex = "#" + hex.charAt(1) + hex.charAt(1)
			+ hex.charAt(2) + hex.charAt(2)
			+ hex.charAt(3) + hex.charAt(3)
			+ hex.charAt(4) + hex.charAt(4);
		} else if (hex.length() == 7) {
			hex = "#FF" + hex.substring(1);
		}
		
		try {
			return Color.parseColor(hex);
		} catch (IllegalArgumentException e) {
			return Color.LTGRAY;
		}
	}
	
	public static boolean isValidHexColor(String hex) {
		if (hex == null) return false;
		hex = hex.trim().toUpperCase();
		if (!hex.startsWith("#")) {
			hex = "#" + hex;
		}
		return hex.matches("^#([A-F0-9]{3}|[A-F0-9]{4}|[A-F0-9]{6}|[A-F0-9]{8})$");
	}
	
	
	
	public static String formatColor(String hex, String format) {
		// Удаляем символ #
		String clean = hex.replace("#", "");
		
		int a = 255, r = 0, g = 0, b = 0;
		
		try {
			if (clean.length() == 8) {
				a = Integer.parseInt(clean.substring(0, 2), 16);
				r = Integer.parseInt(clean.substring(2, 4), 16);
				g = Integer.parseInt(clean.substring(4, 6), 16);
				b = Integer.parseInt(clean.substring(6, 8), 16);
			} else if (clean.length() == 6) {
				r = Integer.parseInt(clean.substring(0, 2), 16);
				g = Integer.parseInt(clean.substring(2, 4), 16);
				b = Integer.parseInt(clean.substring(4, 6), 16);
			}
		} catch (Exception e) {
			return hex;
		}
		
		switch (format) {
			case "hex":
			return String.format("#%02X%02X%02X", r, g, b);
			case "hexad":
			return String.format("0x%02X%02X%02X%02X", a, r, g, b);
			case "rgb":
			return String.format("rgb(%d, %d, %d);", r, g, b);
			case "rgba":
			float alpha = a / 255f;
			String alphaStr = (alpha == 1f) ? "1" : String.format(Locale.US, "%.2f", alpha).replaceAll("0+$", "").replaceAll("\\.$", "");
			return String.format("rgba(%d, %d, %d, %s);", r, g, b, alphaStr);
			default:
			return hex;
		}
	}
    
    public static String normalizeHexColor(String hex) {
        hex = hex.trim().toUpperCase();
        if (!hex.startsWith("#")) hex = "#" + hex;

        if (hex.length() == 4) {
            return "#FF" + hex.charAt(1) + hex.charAt(1) +
                         hex.charAt(2) + hex.charAt(2) +
                         hex.charAt(3) + hex.charAt(3);
        } else if (hex.length() == 5) {
            return "#" + hex.charAt(1) + hex.charAt(1) +
                         hex.charAt(2) + hex.charAt(2) +
                         hex.charAt(3) + hex.charAt(3) +
                         hex.charAt(4) + hex.charAt(4);
        } else if (hex.length() == 7) {
            return "#FF" + hex.substring(1);
        }
        return hex;
    }
	
}
