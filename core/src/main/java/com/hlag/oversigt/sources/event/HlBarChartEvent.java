package com.hlag.oversigt.sources.event;

import java.util.List;

import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.properties.Color;

public class HlBarChartEvent extends OversigtEvent {

	private final List<Category> categories;
	private final String value;

	public HlBarChartEvent(List<Category> categories, String value) {
		this.categories = categories;
		this.value = value;
	}

	public List<Category> getCategories() {
		return categories;
	}

	public String getValue() {
		return value;
	}

	public static final class Category {

		private final String title;
		private final String description;
		private final List<Serie> series;

		public Category(String title, String description, List<Serie> series) {
			this.title = title;
			this.description = description;
			this.series = series;
		}

		public String getDescription() {
			return description;
		}

		public String getTitle() {
			return title;
		}

		public List<Serie> getSeries() {
			return series;
		}
	}

	public static final class Serie {

		private final String backgroundColor;
		private final String height;
		private final String style;

		public Serie(Color backgroundColor, double height) {
			this.backgroundColor = backgroundColor.getHexColor();
			this.height = height * 100 + "%";
			this.style = "background-color: " + this.backgroundColor + "; height: " + this.height + ";";
		}

		public String getBackgroundColor() {
			return backgroundColor;
		}

		public String getHeight() {
			return height;
		}

		public String getStyle() {
			return style;
		}
	}
}
