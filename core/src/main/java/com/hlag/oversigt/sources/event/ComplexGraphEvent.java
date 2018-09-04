package com.hlag.oversigt.sources.event;

import java.util.List;

import com.hlag.oversigt.core.OversigtEvent;

public class ComplexGraphEvent extends OversigtEvent {
	private List<Series> series;
	private String displayedValue;

	public ComplexGraphEvent(List<Series> series) {
		this.series = series;
	}

	public ComplexGraphEvent(List<Series> series, String displayedValue) {
		this.series = series;
		this.displayedValue = displayedValue;
	}

	public List<Series> getSeries() {
		return this.series;
	}

	public void setSeries(List<Series> series) {
		this.series = series;
	}

	public String getDisplayedValue() {
		return this.displayedValue;
	}

	public static class Point {
		private Long x;
		private Long y;

		public Point(Long x, Long y) {
			this.x = x;
			this.y = y;
		}

		public Long getX() {
			return this.x;
		}

		public void setX(Long x) {
			this.x = x;
		}

		public Long getY() {
			return this.y;
		}

		public void setY(Long y) {
			this.y = y;
		}
	}

	public static class Series {
		private String name;
		private List<ComplexGraphEvent.Point> data;

		public Series(String name, List<ComplexGraphEvent.Point> data) {
			this.name = name;
			this.data = data;
		}

		public String getName() {
			return this.name;
		}

		public List<ComplexGraphEvent.Point> getData() {
			return this.data;
		}
	}
}