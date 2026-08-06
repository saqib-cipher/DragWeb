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
			put("transparent", "00FFFFFF");
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
		String trimmed = input.trim();
		if ("transparent".equalsIgnoreCase(trimmed)) {
			return Color.TRANSPARENT;
		}
		
		if (trimmed.toLowerCase().startsWith("rgb")) {
			try {
				String content = trimmed.substring(trimmed.indexOf("(") + 1, trimmed.indexOf(")"));
				String[] parts = content.split(",");
				int r = Integer.parseInt(parts[0].trim());
				int g = Integer.parseInt(parts[1].trim());
				int b = Integer.parseInt(parts[2].trim());
				int a = 255;
				if (parts.length >= 4) {
					a = Math.round(Float.parseFloat(parts[3].trim()) * 255f);
				}
				return Color.argb(a, r, g, b);
			} catch (Exception e) {
				return Color.LTGRAY;
			}
		}

		String hex = trimmed.toUpperCase();
		boolean is0x = hex.startsWith("0X");
		if (is0x) {
			hex = hex.substring(2);
		} else if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}

		// 8-digit hex: 0xAARRGGBB vs #RRGGBBAA
		if (hex.length() == 8) {
			try {
				if (is0x) {
					// 0xAARRGGBB (Alpha at start)
					int a = Integer.parseInt(hex.substring(0, 2), 16);
					int r = Integer.parseInt(hex.substring(2, 4), 16);
					int g = Integer.parseInt(hex.substring(4, 6), 16);
					int b = Integer.parseInt(hex.substring(6, 8), 16);
					return Color.argb(a, r, g, b);
				} else {
					// #RRGGBBAA (Alpha at last, CSS standard)
					int r = Integer.parseInt(hex.substring(0, 2), 16);
					int g = Integer.parseInt(hex.substring(2, 4), 16);
					int b = Integer.parseInt(hex.substring(4, 6), 16);
					int a = Integer.parseInt(hex.substring(6, 8), 16);
					return Color.argb(a, r, g, b);
				}
			} catch (Exception ignored) {}
		}

		// 6-digit hex: RRGGBB
		if (hex.length() == 6) {
			try {
				int r = Integer.parseInt(hex.substring(0, 2), 16);
				int g = Integer.parseInt(hex.substring(2, 4), 16);
				int b = Integer.parseInt(hex.substring(4, 6), 16);
				return Color.rgb(r, g, b);
			} catch (Exception ignored) {}
		}

		try {
			return Color.parseColor("#" + hex);
		} catch (Exception e) {
			return Color.LTGRAY;
		}
	}
	
	public static boolean isValidHexColor(String hex) {
		if (hex == null) return false;
		hex = hex.trim().toUpperCase();
		if (hex.toLowerCase().startsWith("rgb")) return true;
		if (hex.startsWith("0X")) hex = "#" + hex.substring(2);
		if (!hex.startsWith("#")) {
			hex = "#" + hex;
		}
		return hex.matches("^#([A-F0-9]{3}|[A-F0-9]{4}|[A-F0-9]{6}|[A-F0-9]{8})$");
	}
	
	
	
	public static String formatColor(String hex, String format) {
		if (hex == null) return "";
		if ("transparent".equalsIgnoreCase(hex)) {
			return "transparent";
		}
		
		int a = 255, r = 0, g = 0, b = 0;
		String clean = hex.replace("#", "").trim();

		try {
			if (clean.toLowerCase().startsWith("rgb")) {
				String content = clean.substring(clean.indexOf("(") + 1, clean.indexOf(")"));
				String[] parts = content.split(",");
				r = Integer.parseInt(parts[0].trim());
				g = Integer.parseInt(parts[1].trim());
				b = Integer.parseInt(parts[2].trim());
				if (parts.length >= 4) {
					float alphaFloat = Float.parseFloat(parts[3].trim());
					a = Math.round(alphaFloat * 255f);
				} else {
					a = 255;
				}
			} else {
				boolean is0x = clean.toLowerCase().startsWith("0x");
				if (is0x) {
					clean = clean.substring(2);
				}
				if (clean.length() == 8) {
					if (is0x) {
						// 0xAARRGGBB format (Alpha at start)
						a = Integer.parseInt(clean.substring(0, 2), 16);
						r = Integer.parseInt(clean.substring(2, 4), 16);
						g = Integer.parseInt(clean.substring(4, 6), 16);
						b = Integer.parseInt(clean.substring(6, 8), 16);
					} else {
						// CSS #RRGGBBAA format (Alpha at last)
						r = Integer.parseInt(clean.substring(0, 2), 16);
						g = Integer.parseInt(clean.substring(2, 4), 16);
						b = Integer.parseInt(clean.substring(4, 6), 16);
						a = Integer.parseInt(clean.substring(6, 8), 16);
					}
				} else if (clean.length() == 6) {
					r = Integer.parseInt(clean.substring(0, 2), 16);
					g = Integer.parseInt(clean.substring(2, 4), 16);
					b = Integer.parseInt(clean.substring(4, 6), 16);
				}
			}
		} catch (Exception e) {
			return hex;
		}
		
		a = Math.max(0, Math.min(255, a));
		r = Math.max(0, Math.min(255, r));
		g = Math.max(0, Math.min(255, g));
		b = Math.max(0, Math.min(255, b));

		if (a == 0 && !"hexad".equals(format)) {
			return "transparent";
		}

		switch (format) {
			case "hex":
				if (a < 255) {
					return String.format("#%02X%02X%02X%02X", r, g, b, a);
				}
				return String.format("#%02X%02X%02X", r, g, b);
			case "hexad":
				return String.format("0x%02X%02X%02X%02X", a, r, g, b);
			case "rgb":
				return String.format("rgb(%d, %d, %d)", r, g, b);
			case "rgba":
				float alpha = a / 255f;
				String alphaStr = (alpha == 1f) ? "1" : String.format(Locale.US, "%.2f", alpha).replaceAll("0+$", "").replaceAll("\\.$", "");
				return String.format("rgba(%d, %d, %d, %s)", r, g, b, alphaStr);
			default:
				return hex;
		}
	}
    
    public static String normalizeHexColor(String hex) {
        if (hex == null || hex.trim().isEmpty()) return "";
        String trimmed = hex.trim();
        if ("transparent".equalsIgnoreCase(trimmed)) return "transparent";
        if (trimmed.toLowerCase().startsWith("rgb")) return trimmed;

        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("#") && !upper.startsWith("0X")) {
            upper = "#" + upper;
        }
        return upper;
    }
	
}
