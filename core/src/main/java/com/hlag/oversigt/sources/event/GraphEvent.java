package com.hlag.oversigt.sources.event;

import java.util.Collection;

import com.hlag.oversigt.core.event.OversigtEvent;

public class GraphEvent extends OversigtEvent {
	private Collection<Point> points;

	public GraphEvent(final Collection<Point> points) {
		this.points = points;
	}

	public Collection<Point> getPoints() {
		return points;
	}

	public void setPoints(final Collection<Point> points) {
		this.points = points;
	}

	public static class Point {
		private Integer x;

		private Integer y;

		public Point(final Integer x, final Integer y) {
			this.x = x;
			this.y = y;
		}

		public Integer getX() {
			return x;
		}

		public void setX(final Integer x) {
			this.x = x;
		}

		public Integer getY() {
			return y;
		}

		public void setY(final Integer y) {
			this.y = y;
		}
	}
}
