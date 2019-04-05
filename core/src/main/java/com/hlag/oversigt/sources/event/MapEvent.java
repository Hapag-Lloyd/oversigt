package com.hlag.oversigt.sources.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.properties.Color;

public class MapEvent extends OversigtEvent {
	private final List<Point> points = new ArrayList<>();

	public MapEvent(final Collection<Point> points) {
		this.points.addAll(points);
	}

	public static class Point {
		private final String id;

		private final double lon;

		private final double lat;

		private final String fill;

		private final String stroke;

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
			return color != null ? color.getHexColor() : null;
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

		public String getFill() {
			return fill;
		}

		public String getStroke() {
			return stroke;
		}

		public Double getSize() {
			return size;
		}

		@Override
		public String toString() {
			return String.format("Point [%s: %s;%s (%s) %s/%s]", id, lon, lat, size, fill, stroke);
		}
	}
}
