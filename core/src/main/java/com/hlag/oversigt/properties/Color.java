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

public class Color {
	private static final Map<String, Color> CSS_COLORS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	static {
		CSS_COLORS.put("AliceBlue", parse("#F0F8FF"));
		CSS_COLORS.put("AntiqueWhite", parse("#FAEBD7"));
		CSS_COLORS.put("Aqua", parse("#00FFFF"));
		CSS_COLORS.put("Aquamarine", parse("#7FFFD4"));
		CSS_COLORS.put("Azure", parse("#F0FFFF"));
		CSS_COLORS.put("Beige", parse("#F5F5DC"));
		CSS_COLORS.put("Bisque", parse("#FFE4C4"));
		CSS_COLORS.put("Black", parse("#000000"));
		CSS_COLORS.put("BlanchedAlmond", parse("#FFEBCD"));
		CSS_COLORS.put("Blue", parse("#0000FF"));
		CSS_COLORS.put("BlueViolet", parse("#8A2BE2"));
		CSS_COLORS.put("Brown", parse("#A52A2A"));
		CSS_COLORS.put("BurlyWood", parse("#DEB887"));
		CSS_COLORS.put("CadetBlue", parse("#5F9EA0"));
		CSS_COLORS.put("Chartreuse", parse("#7FFF00"));
		CSS_COLORS.put("Chocolate", parse("#D2691E"));
		CSS_COLORS.put("Coral", parse("#FF7F50"));
		CSS_COLORS.put("CornflowerBlue", parse("#6495ED"));
		CSS_COLORS.put("Cornsilk", parse("#FFF8DC"));
		CSS_COLORS.put("Crimson", parse("#DC143C"));
		CSS_COLORS.put("Cyan", parse("#00FFFF"));
		CSS_COLORS.put("DarkBlue", parse("#00008B"));
		CSS_COLORS.put("DarkCyan", parse("#008B8B"));
		CSS_COLORS.put("DarkGoldenRod", parse("#B8860B"));
		CSS_COLORS.put("DarkGray", parse("#A9A9A9"));
		CSS_COLORS.put("DarkGrey", parse("#A9A9A9"));
		CSS_COLORS.put("DarkGreen", parse("#006400"));
		CSS_COLORS.put("DarkKhaki", parse("#BDB76B"));
		CSS_COLORS.put("DarkMagenta", parse("#8B008B"));
		CSS_COLORS.put("DarkOliveGreen", parse("#556B2F"));
		CSS_COLORS.put("DarkOrange", parse("#FF8C00"));
		CSS_COLORS.put("DarkOrchid", parse("#9932CC"));
		CSS_COLORS.put("DarkRed", parse("#8B0000"));
		CSS_COLORS.put("DarkSalmon", parse("#E9967A"));
		CSS_COLORS.put("DarkSeaGreen", parse("#8FBC8F"));
		CSS_COLORS.put("DarkSlateBlue", parse("#483D8B"));
		CSS_COLORS.put("DarkSlateGray", parse("#2F4F4F"));
		CSS_COLORS.put("DarkSlateGrey", parse("#2F4F4F"));
		CSS_COLORS.put("DarkTurquoise", parse("#00CED1"));
		CSS_COLORS.put("DarkViolet", parse("#9400D3"));
		CSS_COLORS.put("DeepPink", parse("#FF1493"));
		CSS_COLORS.put("DeepSkyBlue", parse("#00BFFF"));
		CSS_COLORS.put("DimGray", parse("#696969"));
		CSS_COLORS.put("DimGrey", parse("#696969"));
		CSS_COLORS.put("DodgerBlue", parse("#1E90FF"));
		CSS_COLORS.put("FireBrick", parse("#B22222"));
		CSS_COLORS.put("FloralWhite", parse("#FFFAF0"));
		CSS_COLORS.put("ForestGreen", parse("#228B22"));
		CSS_COLORS.put("Fuchsia", parse("#FF00FF"));
		CSS_COLORS.put("Gainsboro", parse("#DCDCDC"));
		CSS_COLORS.put("GhostWhite", parse("#F8F8FF"));
		CSS_COLORS.put("Gold", parse("#FFD700"));
		CSS_COLORS.put("GoldenRod", parse("#DAA520"));
		CSS_COLORS.put("Gray", parse("#808080"));
		CSS_COLORS.put("Grey", parse("#808080"));
		CSS_COLORS.put("Green", parse("#008000"));
		CSS_COLORS.put("GreenYellow", parse("#ADFF2F"));
		CSS_COLORS.put("HoneyDew", parse("#F0FFF0"));
		CSS_COLORS.put("HotPink", parse("#FF69B4"));
		CSS_COLORS.put("IndianRed", parse("#CD5C5C"));
		CSS_COLORS.put("Indigo", parse("#4B0082"));
		CSS_COLORS.put("Ivory", parse("#FFFFF0"));
		CSS_COLORS.put("Khaki", parse("#F0E68C"));
		CSS_COLORS.put("Lavender", parse("#E6E6FA"));
		CSS_COLORS.put("LavenderBlush", parse("#FFF0F5"));
		CSS_COLORS.put("LawnGreen", parse("#7CFC00"));
		CSS_COLORS.put("LemonChiffon", parse("#FFFACD"));
		CSS_COLORS.put("LightBlue", parse("#ADD8E6"));
		CSS_COLORS.put("LightCoral", parse("#F08080"));
		CSS_COLORS.put("LightCyan", parse("#E0FFFF"));
		CSS_COLORS.put("LightGoldenRodYellow", parse("#FAFAD2"));
		CSS_COLORS.put("LightGray", parse("#D3D3D3"));
		CSS_COLORS.put("LightGrey", parse("#D3D3D3"));
		CSS_COLORS.put("LightGreen", parse("#90EE90"));
		CSS_COLORS.put("LightPink", parse("#FFB6C1"));
		CSS_COLORS.put("LightSalmon", parse("#FFA07A"));
		CSS_COLORS.put("LightSeaGreen", parse("#20B2AA"));
		CSS_COLORS.put("LightSkyBlue", parse("#87CEFA"));
		CSS_COLORS.put("LightSlateGray", parse("#778899"));
		CSS_COLORS.put("LightSlateGrey", parse("#778899"));
		CSS_COLORS.put("LightSteelBlue", parse("#B0C4DE"));
		CSS_COLORS.put("LightYellow", parse("#FFFFE0"));
		CSS_COLORS.put("Lime", parse("#00FF00"));
		CSS_COLORS.put("LimeGreen", parse("#32CD32"));
		CSS_COLORS.put("Linen", parse("#FAF0E6"));
		CSS_COLORS.put("Magenta", parse("#FF00FF"));
		CSS_COLORS.put("Maroon", parse("#800000"));
		CSS_COLORS.put("MediumAquaMarine", parse("#66CDAA"));
		CSS_COLORS.put("MediumBlue", parse("#0000CD"));
		CSS_COLORS.put("MediumOrchid", parse("#BA55D3"));
		CSS_COLORS.put("MediumPurple", parse("#9370DB"));
		CSS_COLORS.put("MediumSeaGreen", parse("#3CB371"));
		CSS_COLORS.put("MediumSlateBlue", parse("#7B68EE"));
		CSS_COLORS.put("MediumSpringGreen", parse("#00FA9A"));
		CSS_COLORS.put("MediumTurquoise", parse("#48D1CC"));
		CSS_COLORS.put("MediumVioletRed", parse("#C71585"));
		CSS_COLORS.put("MidnightBlue", parse("#191970"));
		CSS_COLORS.put("MintCream", parse("#F5FFFA"));
		CSS_COLORS.put("MistyRose", parse("#FFE4E1"));
		CSS_COLORS.put("Moccasin", parse("#FFE4B5"));
		CSS_COLORS.put("NavajoWhite", parse("#FFDEAD"));
		CSS_COLORS.put("Navy", parse("#000080"));
		CSS_COLORS.put("OldLace", parse("#FDF5E6"));
		CSS_COLORS.put("Olive", parse("#808000"));
		CSS_COLORS.put("OliveDrab", parse("#6B8E23"));
		CSS_COLORS.put("Orange", parse("#FFA500"));
		CSS_COLORS.put("OrangeRed", parse("#FF4500"));
		CSS_COLORS.put("Orchid", parse("#DA70D6"));
		CSS_COLORS.put("PaleGoldenRod", parse("#EEE8AA"));
		CSS_COLORS.put("PaleGreen", parse("#98FB98"));
		CSS_COLORS.put("PaleTurquoise", parse("#AFEEEE"));
		CSS_COLORS.put("PaleVioletRed", parse("#DB7093"));
		CSS_COLORS.put("PapayaWhip", parse("#FFEFD5"));
		CSS_COLORS.put("PeachPuff", parse("#FFDAB9"));
		CSS_COLORS.put("Peru", parse("#CD853F"));
		CSS_COLORS.put("Pink", parse("#FFC0CB"));
		CSS_COLORS.put("Plum", parse("#DDA0DD"));
		CSS_COLORS.put("PowderBlue", parse("#B0E0E6"));
		CSS_COLORS.put("Purple", parse("#800080"));
		CSS_COLORS.put("RebeccaPurple", parse("#663399"));
		CSS_COLORS.put("Red", parse("#FF0000"));
		CSS_COLORS.put("RosyBrown", parse("#BC8F8F"));
		CSS_COLORS.put("RoyalBlue", parse("#4169E1"));
		CSS_COLORS.put("SaddleBrown", parse("#8B4513"));
		CSS_COLORS.put("Salmon", parse("#FA8072"));
		CSS_COLORS.put("SandyBrown", parse("#F4A460"));
		CSS_COLORS.put("SeaGreen", parse("#2E8B57"));
		CSS_COLORS.put("SeaShell", parse("#FFF5EE"));
		CSS_COLORS.put("Sienna", parse("#A0522D"));
		CSS_COLORS.put("Silver", parse("#C0C0C0"));
		CSS_COLORS.put("SkyBlue", parse("#87CEEB"));
		CSS_COLORS.put("SlateBlue", parse("#6A5ACD"));
		CSS_COLORS.put("SlateGray", parse("#708090"));
		CSS_COLORS.put("SlateGrey", parse("#708090"));
		CSS_COLORS.put("Snow", parse("#FFFAFA"));
		CSS_COLORS.put("SpringGreen", parse("#00FF7F"));
		CSS_COLORS.put("SteelBlue", parse("#4682B4"));
		CSS_COLORS.put("Tan", parse("#D2B48C"));
		CSS_COLORS.put("Teal", parse("#008080"));
		CSS_COLORS.put("Thistle", parse("#D8BFD8"));
		CSS_COLORS.put("Tomato", parse("#FF6347"));
		CSS_COLORS.put("Turquoise", parse("#40E0D0"));
		CSS_COLORS.put("Violet", parse("#EE82EE"));
		CSS_COLORS.put("Wheat", parse("#F5DEB3"));
		CSS_COLORS.put("White", parse("#FFFFFF"));
		CSS_COLORS.put("WhiteSmoke", parse("#F5F5F5"));
		CSS_COLORS.put("Yellow", parse("#FFFF00"));
		CSS_COLORS.put("YellowGreen", parse("#9ACD32"));
	}

	public static Color random() {
		return CSS_COLORS//
				.values()//
				.stream()//
				.skip((long) (Math.random() * CSS_COLORS.size()))//
				.findAny()//
				.get();
	}

	public static Color parse(final String string) {
		return CSS_COLORS.computeIfAbsent(string, s -> parseString(string));
	}

	private static Color parseString(final String string) {
		if (string.length() == 0) {
			return Black;
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

		if (s == 0f) {
			r = g = b = l; // achromatic
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

	public static final Color AliceBlue = parse("#F0F8FF");

	public static final Color AntiqueWhite = parse("#FAEBD7");

	public static final Color Aqua = parse("#00FFFF");

	public static final Color Aquamarine = parse("#7FFFD4");

	public static final Color Azure = parse("#F0FFFF");

	public static final Color Beige = parse("#F5F5DC");

	public static final Color Bisque = parse("#FFE4C4");

	public static final Color Black = parse("#000000");

	public static final Color BlanchedAlmond = parse("#FFEBCD");

	public static final Color Blue = parse("#0000FF");

	public static final Color BlueViolet = parse("#8A2BE2");

	public static final Color Brown = parse("#A52A2A");

	public static final Color BurlyWood = parse("#DEB887");

	public static final Color CadetBlue = parse("#5F9EA0");

	public static final Color Chartreuse = parse("#7FFF00");

	public static final Color Chocolate = parse("#D2691E");

	public static final Color Coral = parse("#FF7F50");

	public static final Color CornflowerBlue = parse("#6495ED");

	public static final Color Cornsilk = parse("#FFF8DC");

	public static final Color Crimson = parse("#DC143C");

	public static final Color Cyan = parse("#00FFFF");

	public static final Color DarkBlue = parse("#00008B");

	public static final Color DarkCyan = parse("#008B8B");

	public static final Color DarkGoldenRod = parse("#B8860B");

	public static final Color DarkGray = parse("#A9A9A9");

	public static final Color DarkGrey = parse("#A9A9A9");

	public static final Color DarkGreen = parse("#006400");

	public static final Color DarkKhaki = parse("#BDB76B");

	public static final Color DarkMagenta = parse("#8B008B");

	public static final Color DarkOliveGreen = parse("#556B2F");

	public static final Color DarkOrange = parse("#FF8C00");

	public static final Color DarkOrchid = parse("#9932CC");

	public static final Color DarkRed = parse("#8B0000");

	public static final Color DarkSalmon = parse("#E9967A");

	public static final Color DarkSeaGreen = parse("#8FBC8F");

	public static final Color DarkSlateBlue = parse("#483D8B");

	public static final Color DarkSlateGray = parse("#2F4F4F");

	public static final Color DarkSlateGrey = parse("#2F4F4F");

	public static final Color DarkTurquoise = parse("#00CED1");

	public static final Color DarkViolet = parse("#9400D3");

	public static final Color DeepPink = parse("#FF1493");

	public static final Color DeepSkyBlue = parse("#00BFFF");

	public static final Color DimGray = parse("#696969");

	public static final Color DimGrey = parse("#696969");

	public static final Color DodgerBlue = parse("#1E90FF");

	public static final Color FireBrick = parse("#B22222");

	public static final Color FloralWhite = parse("#FFFAF0");

	public static final Color ForestGreen = parse("#228B22");

	public static final Color Fuchsia = parse("#FF00FF");

	public static final Color Gainsboro = parse("#DCDCDC");

	public static final Color GhostWhite = parse("#F8F8FF");

	public static final Color Gold = parse("#FFD700");

	public static final Color GoldenRod = parse("#DAA520");

	public static final Color Gray = parse("#808080");

	public static final Color Grey = parse("#808080");

	public static final Color Green = parse("#008000");

	public static final Color GreenYellow = parse("#ADFF2F");

	public static final Color HoneyDew = parse("#F0FFF0");

	public static final Color HotPink = parse("#FF69B4");

	public static final Color IndianRed = parse("#CD5C5C");

	public static final Color Indigo = parse("#4B0082");

	public static final Color Ivory = parse("#FFFFF0");

	public static final Color Khaki = parse("#F0E68C");

	public static final Color Lavender = parse("#E6E6FA");

	public static final Color LavenderBlush = parse("#FFF0F5");

	public static final Color LawnGreen = parse("#7CFC00");

	public static final Color LemonChiffon = parse("#FFFACD");

	public static final Color LightBlue = parse("#ADD8E6");

	public static final Color LightCoral = parse("#F08080");

	public static final Color LightCyan = parse("#E0FFFF");

	public static final Color LightGoldenRodYellow = parse("#FAFAD2");

	public static final Color LightGray = parse("#D3D3D3");

	public static final Color LightGrey = parse("#D3D3D3");

	public static final Color LightGreen = parse("#90EE90");

	public static final Color LightPink = parse("#FFB6C1");

	public static final Color LightSalmon = parse("#FFA07A");

	public static final Color LightSeaGreen = parse("#20B2AA");

	public static final Color LightSkyBlue = parse("#87CEFA");

	public static final Color LightSlateGray = parse("#778899");

	public static final Color LightSlateGrey = parse("#778899");

	public static final Color LightSteelBlue = parse("#B0C4DE");

	public static final Color LightYellow = parse("#FFFFE0");

	public static final Color Lime = parse("#00FF00");

	public static final Color LimeGreen = parse("#32CD32");

	public static final Color Linen = parse("#FAF0E6");

	public static final Color Magenta = parse("#FF00FF");

	public static final Color Maroon = parse("#800000");

	public static final Color MediumAquaMarine = parse("#66CDAA");

	public static final Color MediumBlue = parse("#0000CD");

	public static final Color MediumOrchid = parse("#BA55D3");

	public static final Color MediumPurple = parse("#9370DB");

	public static final Color MediumSeaGreen = parse("#3CB371");

	public static final Color MediumSlateBlue = parse("#7B68EE");

	public static final Color MediumSpringGreen = parse("#00FA9A");

	public static final Color MediumTurquoise = parse("#48D1CC");

	public static final Color MediumVioletRed = parse("#C71585");

	public static final Color MidnightBlue = parse("#191970");

	public static final Color MintCream = parse("#F5FFFA");

	public static final Color MistyRose = parse("#FFE4E1");

	public static final Color Moccasin = parse("#FFE4B5");

	public static final Color NavajoWhite = parse("#FFDEAD");

	public static final Color Navy = parse("#000080");

	public static final Color OldLace = parse("#FDF5E6");

	public static final Color Olive = parse("#808000");

	public static final Color OliveDrab = parse("#6B8E23");

	public static final Color Orange = parse("#FFA500");

	public static final Color OrangeRed = parse("#FF4500");

	public static final Color Orchid = parse("#DA70D6");

	public static final Color PaleGoldenRod = parse("#EEE8AA");

	public static final Color PaleGreen = parse("#98FB98");

	public static final Color PaleTurquoise = parse("#AFEEEE");

	public static final Color PaleVioletRed = parse("#DB7093");

	public static final Color PapayaWhip = parse("#FFEFD5");

	public static final Color PeachPuff = parse("#FFDAB9");

	public static final Color Peru = parse("#CD853F");

	public static final Color Pink = parse("#FFC0CB");

	public static final Color Plum = parse("#DDA0DD");

	public static final Color PowderBlue = parse("#B0E0E6");

	public static final Color Purple = parse("#800080");

	public static final Color RebeccaPurple = parse("#663399");

	public static final Color Red = parse("#FF0000");

	public static final Color RosyBrown = parse("#BC8F8F");

	public static final Color RoyalBlue = parse("#4169E1");

	public static final Color SaddleBrown = parse("#8B4513");

	public static final Color Salmon = parse("#FA8072");

	public static final Color SandyBrown = parse("#F4A460");

	public static final Color SeaGreen = parse("#2E8B57");

	public static final Color SeaShell = parse("#FFF5EE");

	public static final Color Sienna = parse("#A0522D");

	public static final Color Silver = parse("#C0C0C0");

	public static final Color SkyBlue = parse("#87CEEB");

	public static final Color SlateBlue = parse("#6A5ACD");

	public static final Color SlateGray = parse("#708090");

	public static final Color SlateGrey = parse("#708090");

	public static final Color Snow = parse("#FFFAFA");

	public static final Color SpringGreen = parse("#00FF7F");

	public static final Color SteelBlue = parse("#4682B4");

	public static final Color Tan = parse("#D2B48C");

	public static final Color Teal = parse("#008080");

	public static final Color Thistle = parse("#D8BFD8");

	public static final Color Tomato = parse("#FF6347");

	public static final Color Turquoise = parse("#40E0D0");

	public static final Color Violet = parse("#EE82EE");

	public static final Color Wheat = parse("#F5DEB3");

	public static final Color White = parse("#FFFFFF");

	public static final Color WhiteSmoke = parse("#F5F5F5");

	public static final Color Yellow = parse("#FFFF00");

	public static final Color YellowGreen = parse("#9ACD32");

	private final int red;

	private final int green;

	private final int blue;

	private final double alpha;

	public Color(final int red, final int green, final int blue) {
		this(red, green, blue, 1.0);
	}

	public Color(final int red, final int green, final int blue, final double alpha) {
		super();
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
