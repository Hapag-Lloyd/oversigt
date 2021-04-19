package com.hlag.oversigt.properties;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import de.larssh.utils.collection.Maps;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class Color {
	private static final Map<String, Color> CSS_COLORS
			= Maps.builder(new TreeMap<String, Color>(String.CASE_INSENSITIVE_ORDER))
					.put("AliceBlue", parseString("#F0F8FF"))
					.put("AntiqueWhite", parseString("#FAEBD7"))
					.put("Aqua", parseString("#00FFFF"))
					.put("AquaMarine", parseString("#7FFFD4"))
					.put("Azure", parseString("#F0FFFF"))
					.put("Beige", parseString("#F5F5DC"))
					.put("Bisque", parseString("#FFE4C4"))
					.put("Black", parseString("#000000"))
					.put("BlanchedAlmond", parseString("#FFEBCD"))
					.put("Blue", parseString("#0000FF"))
					.put("BlueViolet", parseString("#8A2BE2"))
					.put("Brown", parseString("#A52A2A"))
					.put("BurlyWood", parseString("#DEB887"))
					.put("CadetBlue", parseString("#5F9EA0"))
					.put("Chartreuse", parseString("#7FFF00"))
					.put("Chocolate", parseString("#D2691E"))
					.put("Coral", parseString("#FF7F50"))
					.put("CornflowerBlue", parseString("#6495ED"))
					.put("Cornsilk", parseString("#FFF8DC"))
					.put("Crimson", parseString("#DC143C"))
					.put("Cyan", parseString("#00FFFF"))
					.put("DarkBlue", parseString("#00008B"))
					.put("DarkCyan", parseString("#008B8B"))
					.put("DarkGoldenRod", parseString("#B8860B"))
					.put("DarkGray", parseString("#A9A9A9"))
					.put("DarkGrey", parseString("#A9A9A9"))
					.put("DarkGreen", parseString("#006400"))
					.put("DarkKhaki", parseString("#BDB76B"))
					.put("DarkMagenta", parseString("#8B008B"))
					.put("DarkOliveGreen", parseString("#556B2F"))
					.put("DarkOrange", parseString("#FF8C00"))
					.put("DarkOrchid", parseString("#9932CC"))
					.put("DarkRed", parseString("#8B0000"))
					.put("DarkSalmon", parseString("#E9967A"))
					.put("DarkSeaGreen", parseString("#8FBC8F"))
					.put("DarkSlateBlue", parseString("#483D8B"))
					.put("DarkSlateGray", parseString("#2F4F4F"))
					.put("DarkSlateGrey", parseString("#2F4F4F"))
					.put("DarkTurquoise", parseString("#00CED1"))
					.put("DarkViolet", parseString("#9400D3"))
					.put("DeepPink", parseString("#FF1493"))
					.put("DeepSkyBlue", parseString("#00BFFF"))
					.put("DimGray", parseString("#696969"))
					.put("DimGrey", parseString("#696969"))
					.put("DodgerBlue", parseString("#1E90FF"))
					.put("FireBrick", parseString("#B22222"))
					.put("FloralWhite", parseString("#FFFAF0"))
					.put("ForestGreen", parseString("#228B22"))
					.put("Fuchsia", parseString("#FF00FF"))
					.put("Gainsboro", parseString("#DCDCDC"))
					.put("GhostWhite", parseString("#F8F8FF"))
					.put("Gold", parseString("#FFD700"))
					.put("GoldenRod", parseString("#DAA520"))
					.put("Gray", parseString("#808080"))
					.put("Grey", parseString("#808080"))
					.put("Green", parseString("#008000"))
					.put("GreenYellow", parseString("#ADFF2F"))
					.put("HoneyDew", parseString("#F0FFF0"))
					.put("HotPink", parseString("#FF69B4"))
					.put("IndianRed", parseString("#CD5C5C"))
					.put("Indigo", parseString("#4B0082"))
					.put("Ivory", parseString("#FFFFF0"))
					.put("Khaki", parseString("#F0E68C"))
					.put("Lavender", parseString("#E6E6FA"))
					.put("LavenderBlush", parseString("#FFF0F5"))
					.put("LawnGreen", parseString("#7CFC00"))
					.put("LemonChiffon", parseString("#FFFACD"))
					.put("LightBlue", parseString("#ADD8E6"))
					.put("LightCoral", parseString("#F08080"))
					.put("LightCyan", parseString("#E0FFFF"))
					.put("LightGoldenRodYellow", parseString("#FAFAD2"))
					.put("LightGray", parseString("#D3D3D3"))
					.put("LightGrey", parseString("#D3D3D3"))
					.put("LightGreen", parseString("#90EE90"))
					.put("LightPink", parseString("#FFB6C1"))
					.put("LightSalmon", parseString("#FFA07A"))
					.put("LightSeaGreen", parseString("#20B2AA"))
					.put("LightSkyBlue", parseString("#87CEFA"))
					.put("LightSlateGray", parseString("#778899"))
					.put("LightSlateGrey", parseString("#778899"))
					.put("LightSteelBlue", parseString("#B0C4DE"))
					.put("LightYellow", parseString("#FFFFE0"))
					.put("Lime", parseString("#00FF00"))
					.put("LimeGreen", parseString("#32CD32"))
					.put("Linen", parseString("#FAF0E6"))
					.put("Magenta", parseString("#FF00FF"))
					.put("Maroon", parseString("#800000"))
					.put("MediumAquaMarine", parseString("#66CDAA"))
					.put("MediumBlue", parseString("#0000CD"))
					.put("MediumOrchid", parseString("#BA55D3"))
					.put("MediumPurple", parseString("#9370DB"))
					.put("MediumSeaGreen", parseString("#3CB371"))
					.put("MediumSlateBlue", parseString("#7B68EE"))
					.put("MediumSpringGreen", parseString("#00FA9A"))
					.put("MediumTurquoise", parseString("#48D1CC"))
					.put("MediumVioletRed", parseString("#C71585"))
					.put("MidnightBlue", parseString("#191970"))
					.put("MintCream", parseString("#F5FFFA"))
					.put("MistyRose", parseString("#FFE4E1"))
					.put("Moccasin", parseString("#FFE4B5"))
					.put("NavajoWhite", parseString("#FFDEAD"))
					.put("Navy", parseString("#000080"))
					.put("OldLace", parseString("#FDF5E6"))
					.put("Olive", parseString("#808000"))
					.put("OliveDrab", parseString("#6B8E23"))
					.put("Orange", parseString("#FFA500"))
					.put("OrangeRed", parseString("#FF4500"))
					.put("Orchid", parseString("#DA70D6"))
					.put("PaleGoldenRod", parseString("#EEE8AA"))
					.put("PaleGreen", parseString("#98FB98"))
					.put("PaleTurquoise", parseString("#AFEEEE"))
					.put("PaleVioletRed", parseString("#DB7093"))
					.put("PapayaWhip", parseString("#FFEFD5"))
					.put("PeachPuff", parseString("#FFDAB9"))
					.put("Peru", parseString("#CD853F"))
					.put("Pink", parseString("#FFC0CB"))
					.put("Plum", parseString("#DDA0DD"))
					.put("PowderBlue", parseString("#B0E0E6"))
					.put("Purple", parseString("#800080"))
					.put("RebeccaPurple", parseString("#663399"))
					.put("Red", parseString("#FF0000"))
					.put("RosyBrown", parseString("#BC8F8F"))
					.put("RoyalBlue", parseString("#4169E1"))
					.put("SaddleBrown", parseString("#8B4513"))
					.put("Salmon", parseString("#FA8072"))
					.put("SandyBrown", parseString("#F4A460"))
					.put("SeaGreen", parseString("#2E8B57"))
					.put("SeaShell", parseString("#FFF5EE"))
					.put("Sienna", parseString("#A0522D"))
					.put("Silver", parseString("#C0C0C0"))
					.put("SkyBlue", parseString("#87CEEB"))
					.put("SlateBlue", parseString("#6A5ACD"))
					.put("SlateGray", parseString("#708090"))
					.put("SlateGrey", parseString("#708090"))
					.put("Snow", parseString("#FFFAFA"))
					.put("SpringGreen", parseString("#00FF7F"))
					.put("SteelBlue", parseString("#4682B4"))
					.put("Tan", parseString("#D2B48C"))
					.put("Teal", parseString("#008080"))
					.put("Thistle", parseString("#D8BFD8"))
					.put("Tomato", parseString("#FF6347"))
					.put("Turquoise", parseString("#40E0D0"))
					.put("Violet", parseString("#EE82EE"))
					.put("Wheat", parseString("#F5DEB3"))
					.put("White", parseString("#FFFFFF"))
					.put("WhiteSmoke", parseString("#F5F5F5"))
					.put("Yellow", parseString("#FFFF00"))
					.put("YellowGreen", parseString("#9ACD32"))
					.get();

	public static Color random() {
		return CSS_COLORS.values().stream().skip((long) (Math.random() * CSS_COLORS.size())).findAny().get();
	}

	public static Color parse(final String string) {
		return CSS_COLORS.computeIfAbsent(string, s -> parseString(string));
	}

	private static Color parseString(final String string) {
		if (string.length() == 0) {
			return BLACK;
		}
		switch (string.charAt(0)) {
		case '#':
			return parseHexColor(string.substring(1));
		case 'r':
		case 'h':
			return parseFunctionColor(string);
		default:
			throw new RuntimeException("Unable to parse color: " + string);
		}
	}

	private static Color parseHexColor(final String string) {
		if (string.length() == 3) {
			final String r = string.substring(0, 1);
			final String g = string.substring(1, 2);
			final String b = string.substring(2, 3);
			return new Color(Integer.parseInt(r + r, 16),
					Integer.parseInt(g + g, 16),
					Integer.parseInt(b + b, 16),
					1.0);
		} else if (string.length() == 6) {
			final String r = string.substring(0, 2);
			final String g = string.substring(2, 4);
			final String b = string.substring(4, 6);
			return new Color(Integer.parseInt(r, 16), Integer.parseInt(g, 16), Integer.parseInt(b, 16), 1.0);
		} else {
			throw new RuntimeException("Unable to parse color: " + string);
		}
	}

	private static final Pattern FUNCTION_PATTERN = Pattern.compile(
			"([a-z]+)\\s*\\(\\s*([0-9]+)\\s*,\\s*([0-9]+)\\s*%?\\s*,\\s*([0-9]+)\\s*%?\\s*(?:,\\s*([0-9\\.]+)\\s*%?\\s*)?\\)",
			Pattern.CASE_INSENSITIVE);

	@SuppressFBWarnings(value = "IMPROPER_UNICODE",
			justification = "comparing lower cased string with ASCII characters only")
	private static Color parseFunctionColor(final String string) {
		final Matcher matcher = FUNCTION_PATTERN.matcher(string);
		if (matcher.find()) {
			final String function = matcher.group(1);
			final String r = matcher.group(2);
			final String g = matcher.group(3);
			final String b = matcher.group(4);
			final String a = matcher.group(5);

			switch (function.toLowerCase()) {
			case "rgb":
				return new Color(Integer.parseInt(r), Integer.parseInt(g), Integer.parseInt(b), 1.0);
			case "rgba":
				return new Color(Integer.parseInt(r), Integer.parseInt(g), Integer.parseInt(b), Double.parseDouble(a));
			case "hsl":
			case "hsla":
				final int[] rgb = hslToRgb(Integer.parseInt(r), Integer.parseInt(r), Integer.parseInt(b));
				return new Color(rgb[0], rgb[1], rgb[2], a != null ? Double.parseDouble(a) : 1.0);
			default:
				// throw below exception
			}
		}
		throw new RuntimeException("Unable to parse color: " + string);
	}

	public static int[] hslToRgb(final int h, final int s, final int l) {
		return hslToRgb(h / 255f, s / 255f, l / 255f);
	}

	/**
	 * Converts an HSL color value to RGB. Conversion formula adapted from
	 * http://en.wikipedia.org/wiki/HSL_color_space. Assumes h, s, and l are
	 * contained in the set [0, 1] and returns r, g, and b in the set [0, 255].
	 *
	 * @param h The hue
	 * @param s The saturation
	 * @param l The lightness
	 * @return int array, the RGB representation
	 */
	public static int[] hslToRgb(final float h, final float s, final float l) {
		final float r;
		final float g;
		final float b;

		// achromatic
		if (s == 0f) {
			r = l;
			g = l;
			b = l;
		} else {
			final float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
			final float p = 2 * l - q;
			r = hueToRgb(p, q, h + 1f / 3f);
			g = hueToRgb(p, q, h);
			b = hueToRgb(p, q, h - 1f / 3f);
		}
		return new int[] { (int) (r * 255), (int) (g * 255), (int) (b * 255) };
	}

	private static float hueToRgb(final float p, final float q, final float t) {
		float tt = t;
		if (tt < 0f) {
			tt += 1f;
		} else if (tt > 1f) {
			tt -= 1f;
		}
		if (tt < 1f / 6f) {
			return p + (q - p) * 6f * tt;
		}
		if (tt < 1f / 2f) {
			return q;
		}
		if (tt < 2f / 3f) {
			return p + (q - p) * (2f / 3f - tt) * 6f;
		}
		return p;
	}

	public static Color smooth(final Color start, final Color end, final double progress) {
		final int r = smooth(start.getRed(), end.getRed(), progress);
		final int g = smooth(start.getGreen(), end.getGreen(), progress);
		final int b = smooth(start.getBlue(), end.getBlue(), progress);
		return new Color(r, g, b);
	}

	private static int smooth(final int start, final int end, final double progress) {
		return (int) (start + (end - start) * Math.max(0.0, Math.min(1.0, progress)));
	}

	public static final Color ALICE_BLUE = parse("#F0F8FF");

	public static final Color ANTIQUE_WHITE = parse("#FAEBD7");

	public static final Color AQUA = parse("#00FFFF");

	public static final Color AQUA_MARINE = parse("#7FFFD4");

	public static final Color AZURE = parse("#F0FFFF");

	public static final Color BEIGE = parse("#F5F5DC");

	public static final Color BISQUE = parse("#FFE4C4");

	public static final Color BLACK = parse("#000000");

	public static final Color BLANCHED_ALMOND = parse("#FFEBCD");

	public static final Color BLUE = parse("#0000FF");

	public static final Color BLUE_VIOLET = parse("#8A2BE2");

	public static final Color BROWN = parse("#A52A2A");

	public static final Color BURLY_WOOD = parse("#DEB887");

	public static final Color CADET_BLUE = parse("#5F9EA0");

	public static final Color CHARTREUSE = parse("#7FFF00");

	public static final Color CHOCOLATE = parse("#D2691E");

	public static final Color CORAL = parse("#FF7F50");

	public static final Color CORNFLOWER_BLUE = parse("#6495ED");

	public static final Color CORNSILK = parse("#FFF8DC");

	public static final Color CRIMSON = parse("#DC143C");

	public static final Color CYAN = parse("#00FFFF");

	public static final Color DARK_BLUE = parse("#00008B");

	public static final Color DARK_CYAN = parse("#008B8B");

	public static final Color DARK_GOLDEN_ROD = parse("#B8860B");

	public static final Color DARK_GRAY = parse("#A9A9A9");

	public static final Color DARK_GREY = parse("#A9A9A9");

	public static final Color DARK_GREEN = parse("#006400");

	public static final Color DARK_KHAKI = parse("#BDB76B");

	public static final Color DARK_MAGENTA = parse("#8B008B");

	public static final Color DARK_OLIVE_GREEN = parse("#556B2F");

	public static final Color DARK_ORANGE = parse("#FF8C00");

	public static final Color DARK_ORCHID = parse("#9932CC");

	public static final Color DARK_RED = parse("#8B0000");

	public static final Color DARK_SALMON = parse("#E9967A");

	public static final Color DARK_SEA_GREEN = parse("#8FBC8F");

	public static final Color DARK_SLATE_BLUE = parse("#483D8B");

	public static final Color DARK_SLATE_GRAY = parse("#2F4F4F");

	public static final Color DARK_SLATE_GREY = parse("#2F4F4F");

	public static final Color DARK_TURQUOISE = parse("#00CED1");

	public static final Color DARK_VIOLET = parse("#9400D3");

	public static final Color DEEP_PINK = parse("#FF1493");

	public static final Color DEEP_SKY_BLUE = parse("#00BFFF");

	public static final Color DIM_GRAY = parse("#696969");

	public static final Color DIM_GREY = parse("#696969");

	public static final Color DODGER_BLUE = parse("#1E90FF");

	public static final Color FIRE_BRICK = parse("#B22222");

	public static final Color FLORAL_WHITE = parse("#FFFAF0");

	public static final Color FOREST_GREEN = parse("#228B22");

	public static final Color FUCHSIA = parse("#FF00FF");

	public static final Color GAINSBORO = parse("#DCDCDC");

	public static final Color GHOST_WHITE = parse("#F8F8FF");

	public static final Color GOLD = parse("#FFD700");

	public static final Color GOLDEN_ROD = parse("#DAA520");

	public static final Color GRAY = parse("#808080");

	public static final Color GREY = parse("#808080");

	public static final Color GREEN = parse("#008000");

	public static final Color GREEN_YELLOW = parse("#ADFF2F");

	public static final Color HONEY_DEW = parse("#F0FFF0");

	public static final Color HOT_PINK = parse("#FF69B4");

	public static final Color INDIAN_RED = parse("#CD5C5C");

	public static final Color INDIGO = parse("#4B0082");

	public static final Color IVORY = parse("#FFFFF0");

	public static final Color KHAKI = parse("#F0E68C");

	public static final Color LAVENDER = parse("#E6E6FA");

	public static final Color LAVENDER_BLUSH = parse("#FFF0F5");

	public static final Color LAWN_GREEN = parse("#7CFC00");

	public static final Color LEMON_CHIFFON = parse("#FFFACD");

	public static final Color LIGHT_BLUE = parse("#ADD8E6");

	public static final Color LIGHT_CORAL = parse("#F08080");

	public static final Color LIGHT_CYAN = parse("#E0FFFF");

	public static final Color LIGHT_GOLDEN_ROD_YELLOW = parse("#FAFAD2");

	public static final Color LIGHT_GRAY = parse("#D3D3D3");

	public static final Color LIGHT_GREY = parse("#D3D3D3");

	public static final Color LIGHT_GREEN = parse("#90EE90");

	public static final Color LIGHT_PINK = parse("#FFB6C1");

	public static final Color LIGHT_SALMON = parse("#FFA07A");

	public static final Color LIGHT_SEA_GREEN = parse("#20B2AA");

	public static final Color LIGHT_SKY_BLUE = parse("#87CEFA");

	public static final Color LIGHT_SLATE_GRAY = parse("#778899");

	public static final Color LIGHT_SLATE_GREY = parse("#778899");

	public static final Color LIGHT_STEEL_BLUE = parse("#B0C4DE");

	public static final Color LIGHT_YELLOW = parse("#FFFFE0");

	public static final Color LIME = parse("#00FF00");

	public static final Color LIME_GREEN = parse("#32CD32");

	public static final Color LINEN = parse("#FAF0E6");

	public static final Color MAGENTA = parse("#FF00FF");

	public static final Color MAROON = parse("#800000");

	public static final Color MEDIUM_AQUA_MARINE = parse("#66CDAA");

	public static final Color MEDIUM_BLUE = parse("#0000CD");

	public static final Color MEDIUM_ORCHID = parse("#BA55D3");

	public static final Color MEDIUM_PURPLE = parse("#9370DB");

	public static final Color MEDIUM_SEA_GREEN = parse("#3CB371");

	public static final Color MEDIUM_SLATE_BLUE = parse("#7B68EE");

	public static final Color MEDIUM_SPRING_GREEN = parse("#00FA9A");

	public static final Color MEDIUM_TURQUOISE = parse("#48D1CC");

	public static final Color MEDIUM_VIOLET_RED = parse("#C71585");

	public static final Color MIDNIGHT_BLUE = parse("#191970");

	public static final Color MINT_CREAM = parse("#F5FFFA");

	public static final Color MISTY_ROSE = parse("#FFE4E1");

	public static final Color MOCCASIN = parse("#FFE4B5");

	public static final Color NAVAJO_WHITE = parse("#FFDEAD");

	public static final Color NAVY = parse("#000080");

	public static final Color OLD_LACE = parse("#FDF5E6");

	public static final Color OLIVE = parse("#808000");

	public static final Color OLIVE_DRAB = parse("#6B8E23");

	public static final Color ORANGE = parse("#FFA500");

	public static final Color ORANGE_RED = parse("#FF4500");

	public static final Color ORCHID = parse("#DA70D6");

	public static final Color PALE_GOLDEN_ROD = parse("#EEE8AA");

	public static final Color PALE_GREEN = parse("#98FB98");

	public static final Color PALE_TURQUOISE = parse("#AFEEEE");

	public static final Color PALE_VIOLET_RED = parse("#DB7093");

	public static final Color PAPAYA_WHIP = parse("#FFEFD5");

	public static final Color PEACH_PUFF = parse("#FFDAB9");

	public static final Color PERU = parse("#CD853F");

	public static final Color PINK = parse("#FFC0CB");

	public static final Color PLUM = parse("#DDA0DD");

	public static final Color POWDER_BLUE = parse("#B0E0E6");

	public static final Color PURPLE = parse("#800080");

	public static final Color REBECCA_PURPLE = parse("#663399");

	public static final Color RED = parse("#FF0000");

	public static final Color ROSY_BROWN = parse("#BC8F8F");

	public static final Color ROYAL_BLUE = parse("#4169E1");

	public static final Color SADDLE_BROWN = parse("#8B4513");

	public static final Color SALMON = parse("#FA8072");

	public static final Color SANDY_BROWN = parse("#F4A460");

	public static final Color SEA_GREEN = parse("#2E8B57");

	public static final Color SEA_SHELL = parse("#FFF5EE");

	public static final Color SIENNA = parse("#A0522D");

	public static final Color SILVER = parse("#C0C0C0");

	public static final Color SKY_BLUE = parse("#87CEEB");

	public static final Color SLATE_BLUE = parse("#6A5ACD");

	public static final Color SLATE_GRAY = parse("#708090");

	public static final Color SLATE_GREY = parse("#708090");

	public static final Color SNOW = parse("#FFFAFA");

	public static final Color SPRING_GREEN = parse("#00FF7F");

	public static final Color STEEL_BLUE = parse("#4682B4");

	public static final Color TAN = parse("#D2B48C");

	public static final Color TEAL = parse("#008080");

	public static final Color THISTLE = parse("#D8BFD8");

	public static final Color TOMATO = parse("#FF6347");

	public static final Color TURQUOISE = parse("#40E0D0");

	public static final Color VIOLET = parse("#EE82EE");

	public static final Color WHEAT = parse("#F5DEB3");

	public static final Color WHITE = parse("#FFFFFF");

	public static final Color WHITE_SMOKE = parse("#F5F5F5");

	public static final Color YELLOW = parse("#FFFF00");

	public static final Color YELLOW_GREEN = parse("#9ACD32");

	private final int red;

	private final int green;

	private final int blue;

	private final double alpha;

	public Color(final int red, final int green, final int blue) {
		this(red, green, blue, 1.0);
	}

	public Color(final int red, final int green, final int blue, final double alpha) {
		this.red = red;
		this.green = green;
		this.blue = blue;
		this.alpha = alpha;
	}

	@JsonPropertyDescription("Determines how translucent the color will be. The higher the value the more opaque the color will be.")
	@DecimalMin(value = "0.0", inclusive = true)
	@DecimalMax(value = "1.0", inclusive = true)
	@NotNull
	public double getAlpha() {
		return alpha;
	}

	@JsonPropertyDescription("Determines the blue component of the current color.")
	@Min(value = 0)
	@Max(value = 255)
	@NotNull
	public int getBlue() {
		return blue;
	}

	@JsonPropertyDescription("Determines the green component of the current color.")
	@Min(value = 0)
	@Max(value = 255)
	@NotNull
	public int getGreen() {
		return green;
	}

	@JsonPropertyDescription("Determines the red component of the current color.")
	@Min(value = 0)
	@Max(value = 255)
	@NotNull
	public int getRed() {
		return red;
	}

	private static String hex(final int i) {
		final String h = Integer.toHexString(i);
		return h.length() > 1 ? h : "0" + h;
	}

	@JsonIgnore
	public String getHexRed() {
		return hex(getRed());
	}

	@JsonIgnore
	public String getHexGreen() {
		return hex(getGreen());
	}

	@JsonIgnore
	public String getHexBlue() {
		return hex(getBlue());
	}

	@JsonIgnore
	public String getHexColor() {
		return "#" + getHexRed() + getHexGreen() + getHexBlue();
	}

	@JsonIgnore
	public int getPerceivedBrightness() {
		return (int) (getRed() * 0.299 + getGreen() * 0.587 + getBlue() * 0.114);
	}

	public boolean shouldUseWhiteFontColor() {
		return getPerceivedBrightness() < 186;
	}

	@Override
	public String toString() {
		return getHexColor();
	}
}
