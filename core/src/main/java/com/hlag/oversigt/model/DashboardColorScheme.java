package com.hlag.oversigt.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public enum DashboardColorScheme {
	COLORED("colored", "default"),
	SINGLE_COLOR("singleColor"),
	TILED_VERTICAL_GRADIENT("tiledVerticalGradient"),
	TILED_HORIZONTAL_GRADIENT("tiledHorizontalGradient"),
	VERTICAL_GRADIENT("verticalGradient"),
	HORIZONTAL_GRADIENT("horizontalGradient");

	private final Set<String> names;

	private DashboardColorScheme(final String... names) {
		this.names = new HashSet<>(Arrays.asList(names));
	}

	public static DashboardColorScheme fromString(final String string) {
		for (final DashboardColorScheme scheme : values()) {
			if (scheme.name().equals(string) || scheme.names.stream().anyMatch(string::equals)) {
				return scheme;
			}
		}
		throw new RuntimeException("No ColorScheme found for: " + string);
	}
}
