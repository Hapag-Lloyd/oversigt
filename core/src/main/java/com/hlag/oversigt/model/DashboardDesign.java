package com.hlag.oversigt.model;

import static com.hlag.oversigt.properties.Color.smooth;

import com.hlag.oversigt.properties.Color;

public class DashboardDesign {
	static final int TILE_DISTANCE = 6;

	static int getRows(Dashboard dashboard) {
		return dashboard.getScreenHeight() / dashboard.getComputedTileHeight();
	}

	public static boolean isAddColorCssToWidgets(Dashboard dashboard) {
		return dashboard.getColorScheme() != DashboardColorScheme.COLORED;
	}

	static StyleAddon getStyleAddon(Dashboard dashboard, int px, int py, int sx, int sy) {
		switch (dashboard.getColorScheme()) {
			case COLORED:
				return new StyleAddon("", null);
			case SINGLE_COLOR:
				return new StyleAddon(getCssSolidBackgroundColor(dashboard.getForegroundColorStart()),
						dashboard.getForegroundColorStart());
			case TILED_VERTICAL_GRADIENT:
				Color color = computeColor(dashboard, py, sy, getRows(dashboard));
				return new StyleAddon(getCssSolidBackgroundColor(color), color);
			case TILED_HORIZONTAL_GRADIENT:
				color = computeColor(dashboard, px, sx, dashboard.getColumns());
				return new StyleAddon(getCssSolidBackgroundColor(color), color);
			case VERTICAL_GRADIENT:
				Color[] colors = computeColors(dashboard, py, sy, getRows(dashboard));
				return new StyleAddon(getCssGradientBackgroundColor("bottom", colors[0], colors[1]),
						computeColor(dashboard, py, sy, getRows(dashboard)));
			case HORIZONTAL_GRADIENT:
				colors = computeColors(dashboard, px, sx, dashboard.getColumns());
				return new StyleAddon(getCssGradientBackgroundColor("right", colors[0], colors[1]),
						computeColor(dashboard, px, sx, dashboard.getColumns()));
			default:
				throw new RuntimeException("Unknown color scheme: " + dashboard.getColorScheme());
		}
	}

	private static String getCssSolidBackgroundColor(Color color) {
		return ";background-color:" + color + ";";
	}

	private static String getCssGradientBackgroundColor(String direction, Color start, Color end) {
		return ";background:" + start + ";background:linear-gradient(to " + direction + ", " + start + ", " + end
				+ ");";
	}

	static String getDisplayClass(Widget widget) {
		Dashboard dashboard = DashboardController.getInstance().getDashboard(widget);
		if (isAddColorCssToWidgets(dashboard)) {
			StyleAddon addon = getStyleAddon(dashboard,
					widget.getPosX(),
					widget.getPosY(),
					widget.getSizeX(),
					widget.getSizeY());
			if (addon.getBackgroundColor() != null) {
				if (addon.getBackgroundColor().shouldUseWhiteFontColor()) {
					return "light-foreground";
				} else {
					return "dark-foreground";
				}
			} else {
				if (widget.getBackgroundColor().shouldUseWhiteFontColor()) {
					return "light-foreground";
				} else {
					return "dark-foreground";
				}
			}
		} else {
			return "";
		}
	}

	static String getDisplayStyle(Widget widget) {
		Dashboard dashboard = DashboardController.getInstance().getDashboard(widget);
		StringBuilder sb = new StringBuilder();
		sb.append(widget.getStyle());
		if (isAddColorCssToWidgets(dashboard)) {
			StyleAddon addon = getStyleAddon(dashboard,
					widget.getPosX(),
					widget.getPosY(),
					widget.getSizeX(),
					widget.getSizeY());
			sb.append(";background-color:").append(widget.getBackgroundColor().getHexColor());
			sb.append(addon.getCss());
		}
		return sb.toString();
	}

	private static Color computeColor(Dashboard dashboard, int position, int size, int total) {
		double dt = total - 1.0;
		double point = position - 1 + (size - 1.0) * 0.5;
		return smooth(dashboard.getForegroundColorStart(), dashboard.getForegroundColorEnd(), point / dt);
	}

	private static Color[] computeColors(Dashboard dashboard, int position, int size, int total) {
		return new Color[] {
				smooth(dashboard.getForegroundColorStart(),
						dashboard.getForegroundColorEnd(),
						(position - 1) / (double) total),
				smooth(dashboard.getForegroundColorStart(),
						dashboard.getForegroundColorEnd(),
						(position - 1 + size) / (double) total) };
	}

	public static class StyleAddon {
		private final String css;
		private final Color backgroundColor;

		public StyleAddon(String css, Color backgroundColor) {
			this.css = css;
			this.backgroundColor = backgroundColor;
		}

		public Color getBackgroundColor() {
			return backgroundColor;
		}

		public String getCss() {
			return css;
		}
	}
}
