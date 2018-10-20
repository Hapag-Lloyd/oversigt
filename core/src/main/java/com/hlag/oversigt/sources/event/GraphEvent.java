package com.hlag.oversigt.sources.event;

import java.util.Collection;

import com.hlag.oversigt.core.event.OversigtEvent;

public class GraphEvent extends OversigtEvent {
	private Collection<Point> points;

	public GraphEvent(Collection<Point> points) {
		this.points = points;
	}

	public Collection<Point> getPoints() {
		return this.points;
	}

	public void setPoints(Collection<Point> points) {
		this.points = points;
	}

	public static class Point {
		private Integer x;
		private Integer y;

		public Point(Integer x, Integer y) {
			this.x = x;
			this.y = y;
		}

		public Integer getX() {
			return this.x;
		}

		public void setX(Integer x) {
			this.x = x;
		}

		public Integer getY() {
			return this.y;
		}

		public void setY(Integer y) {
			this.y = y;
		}
	}
}