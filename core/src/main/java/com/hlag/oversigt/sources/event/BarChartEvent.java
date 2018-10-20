package com.hlag.oversigt.sources.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hlag.oversigt.core.event.OversigtEvent;

public class BarChartEvent extends OversigtEvent {
	private Collection<String> labels = new ArrayList<>();
	private List<Dataset> datasets = new ArrayList<>();

	public void addDataset(String name, String color, int borderWidth) {
		addDataset(name, color, color, borderWidth);
	}

	public void addDataset(String name, String backgroundColor, String borderColor, int borderWidth) {
		datasets.add(new Dataset(name, backgroundColor, borderColor, borderWidth));
	}

	public void addData(String label, double... values) {
		if (values.length != datasets.size()) {
			throw new RuntimeException("Number of values doesn't match the number of datasets");
		}
		this.labels.add(label);
		for (int i = 0; i < values.length; ++i) {
			datasets.get(i).add(values[i]);
		}
	}

	public Collection<Dataset> getDatasets() {
		return datasets;
	}

	public Collection<String> getLabels() {
		return labels;
	}

	public static class Dataset {
		private final String label;
		private final Collection<Double> data = new ArrayList<>();
		private final Collection<String> backgroundColor = new ArrayList<>();
		private final Collection<String> borderColor = new ArrayList<>();
		private final int borderWidth;

		private final transient String defaultBackgroundColor;
		private final transient String defaultBorderColor;

		private Dataset(String label, String backgroundColor, String borderColor, int borderWidth) {
			this.label = label;
			this.borderWidth = borderWidth;
			this.defaultBackgroundColor = backgroundColor;
			this.defaultBorderColor = borderColor;
		}

		private void add(double value) {
			data.add(value);
			backgroundColor.add(defaultBackgroundColor);
			borderColor.add(defaultBorderColor);
		}

		public Collection<String> getBackgroundColor() {
			return backgroundColor;
		}

		public Collection<String> getBorderColor() {
			return borderColor;
		}

		public int getBorderWidth() {
			return borderWidth;
		}

		public String getLabel() {
			return label;
		}

		public Collection<Double> getData() {
			return data;
		}
	}
}
