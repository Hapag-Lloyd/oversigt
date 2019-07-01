package com.hlag.oversigt.sources.event;

import java.util.List;

import com.hlag.oversigt.core.event.OversigtEvent;

public class ComplexGraphEvent extends OversigtEvent {
	private List<Series> series;

	private String displayedValue = "";

	public ComplexGraphEvent(final List<Series> series) {
		this.series = series;
	}

	public ComplexGraphEvent(final List<Series> series, final String displayedValue) {
		this.series = series;
		this.displayedValue = displayedValue;
	}

	public List<Series> getSeries() {
		return series;
	}

	public void setSeries(final List<Series> series) {
		this.series = series;
	}

	public String getDisplayedValue() {
		return displayedValue;
	}

	public static class Point {
		private Long x;

		private Long y;

		public Point(final Long x, final Long y) {
			this.x = x;
			this.y = y;
		}

		public Long getX() {
			return x;
		}

		public void setX(final Long x) {
			this.x = x;
		}

		public Long getY() {
			return y;
		}

		public void setY(final Long y) {
			this.y = y;
		}
	}

	public static class Series {
		private String name;

		private List<ComplexGraphEvent.Point> data;

		public Series(final String name, final List<ComplexGraphEvent.Point> data) {
			this.name = name;
			this.data = data;
		}

		public String getName() {
			return name;
		}

		public List<ComplexGraphEvent.Point> getData() {
			return data;
		}
	}
}
