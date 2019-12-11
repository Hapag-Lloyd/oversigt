package com.hlag.oversigt.sources.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.properties.Color;

import edu.umd.cs.findbugs.annotations.Nullable;

public class MapEvent extends OversigtEvent {
	private final List<Point> points = new ArrayList<>();

	public MapEvent(final Collection<Point> points) {
		this.points.addAll(points);
	}

	public static class Point {
		private final String id;

		private final double lon;

		private final double lat;

		@Nullable
		@javax.annotation.Nullable
		private final String fill;

		@Nullable
		@javax.annotation.Nullable
		private final String stroke;

		@Nullable
		@javax.annotation.Nullable
		private final Double size;

		public Point(final String id, final double longitude, final double latitue) {
			this.id = id;
			fill = null;
			stroke = null;
			lon = longitude;
			lat = latitue;
			size = null;
		}

		public Point(final String id, final double longitude, final double latitue, final double size) {
			this.id = id;
			fill = null;
			stroke = null;
			lon = longitude;
			lat = latitue;
			this.size = size;
		}

		public Point(final String id,
				final double longitude,
				final double latitue,
				final Color fill,
				final Color stroke) {
			this.id = id;
			this.fill = getColorString(fill);
			this.stroke = getColorString(stroke);
			lon = longitude;
			lat = latitue;
			size = null;
		}

		public Point(final String id,
				final double longitude,
				final double latitue,
				final Color fill,
				final Color stroke,
				final double size) {
			this.id = id;
			this.fill = getColorString(fill);
			this.stroke = getColorString(stroke);
			lon = longitude;
			lat = latitue;
			this.size = size;
		}

		private static String getColorString(final Color color) {
			return Objects.requireNonNull(color).getHexColor();
		}

		public String getId() {
			return id;
		}

		public double getLatitue() {
			return lat;
		}

		public double getLongitude() {
			return lon;
		}

		@Nullable
		public String getFill() {
			return fill;
		}

		@Nullable
		public String getStroke() {
			return stroke;
		}

		@Nullable
		public Double getSize() {
			return size;
		}

		@Override
		public String toString() {
			return String.format("Point [%s: %s;%s (%s) %s/%s]", id, lon, lat, size, fill, stroke);
		}
	}
}
