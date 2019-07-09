package com.hlag.oversigt.model;

import static com.hlag.oversigt.properties.Color.smooth;

import java.util.Optional;

import com.hlag.oversigt.properties.Color;

import de.larssh.utils.Finals;

public final class DashboardDesign {
	static final int TILE_DISTANCE = Finals.constant(6);

	static int getRows(final Dashboard dashboard) {
		return dashboard.getScreenHeight() / dashboard.getComputedTileHeight();
	}

	public static boolean isAddColorCssToWidgets(final Dashboard dashboard) {
		return dashboard.getColorScheme() != DashboardColorScheme.COLORED;
	}

	static StyleAddon getStyleAddon(final Dashboard dashboard, final int px, final int py, final int sx, final int sy) {
		switch (dashboard.getColorScheme()) {
		case COLORED:
			return new StyleAddon("", Optional.empty());
		case SINGLE_COLOR:
			return new StyleAddon(getCssSolidBackgroundColor(dashboard.getForegroundColorStart()),
					Optional.of(dashboard.getForegroundColorStart()));
		case TILED_VERTICAL_GRADIENT:
			Color color = computeColor(dashboard, py, sy, getRows(dashboard));
			return new StyleAddon(getCssSolidBackgroundColor(color), Optional.of(color));
		case TILED_HORIZONTAL_GRADIENT:
			color = computeColor(dashboard, px, sx, dashboard.getColumns());
			return new StyleAddon(getCssSolidBackgroundColor(color), Optional.of(color));
		case VERTICAL_GRADIENT:
			Color[] colors = computeColors(dashboard, py, sy, getRows(dashboard));
			return new StyleAddon(getCssGradientBackgroundColor("bottom", colors[0], colors[1]),
					Optional.of(computeColor(dashboard, py, sy, getRows(dashboard))));
		case HORIZONTAL_GRADIENT:
			colors = computeColors(dashboard, px, sx, dashboard.getColumns());
			return new StyleAddon(getCssGradientBackgroundColor("right", colors[0], colors[1]),
					Optional.of(computeColor(dashboard, px, sx, dashboard.getColumns())));
		default:
			throw new RuntimeException("Unknown color scheme: " + dashboard.getColorScheme());
		}
	}

	private static String getCssSolidBackgroundColor(final Color color) {
		return ";background-color:" + color + ";";
	}

	private static String getCssGradientBackgroundColor(final String direction, final Color start, final Color end) {
		return ";background:"
				+ start
				+ ";background:linear-gradient(to "
				+ direction
				+ ", "
				+ start
				+ ", "
				+ end
				+ ");";
	}

	static String getDisplayClass(final Widget widget) {
		final Dashboard dashboard = DashboardController.getInstance().getDashboard(widget);
		if (!isAddColorCssToWidgets(dashboard)) {
			return "";
		}

		final StyleAddon addon
				= getStyleAddon(dashboard, widget.getPosX(), widget.getPosY(), widget.getSizeX(), widget.getSizeY());
		if (addon.getBackgroundColor()
				.map(Color::shouldUseWhiteFontColor)
				.orElseGet(() -> widget.getBackgroundColor().shouldUseWhiteFontColor())) {
			return "light-foreground";
		}
		return "dark-foreground";
	}

	static String getDisplayStyle(final Widget widget) {
		final Dashboard dashboard = DashboardController.getInstance().getDashboard(widget);
		final StringBuilder sb = new StringBuilder();
		sb.append(widget.getStyle());
		if (isAddColorCssToWidgets(dashboard)) {
			final StyleAddon addon = getStyleAddon(dashboard,
					widget.getPosX(),
					widget.getPosY(),
					widget.getSizeX(),
					widget.getSizeY());
			sb.append(";background-color:").append(widget.getBackgroundColor().getHexColor());
			sb.append(addon.getCss());
		}
		return sb.toString();
	}

	private static Color computeColor(final Dashboard dashboard, final int position, final int size, final int total) {
		final double dt = total - 1.0;
		final double point = position - 1 + (size - 1.0) * 0.5;
		return smooth(dashboard.getForegroundColorStart(), dashboard.getForegroundColorEnd(), point / dt);
	}

	private static Color[] computeColors(final Dashboard dashboard,
			final int position,
			final int size,
			final int total) {
		return new Color[] {
				smooth(dashboard.getForegroundColorStart(),
						dashboard.getForegroundColorEnd(),
						(position - 1) / (double) total),
				smooth(dashboard.getForegroundColorStart(),
						dashboard.getForegroundColorEnd(),
						(position - 1 + size) / (double) total) };
	}

	private DashboardDesign() {
		throw new UnsupportedOperationException();
	}

	public static class StyleAddon {
		private final String css;

		private final Optional<Color> backgroundColor;

		public StyleAddon(final String css, final Optional<Color> backgroundColor) {
			this.css = css;
			this.backgroundColor = backgroundColor;
		}

		public Optional<Color> getBackgroundColor() {
			return backgroundColor;
		}

		public String getCss() {
			return css;
		}
	}
}
