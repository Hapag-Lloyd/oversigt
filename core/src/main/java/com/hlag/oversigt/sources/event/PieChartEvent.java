package com.hlag.oversigt.sources.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.hlag.oversigt.core.event.OversigtEvent;

public class PieChartEvent extends OversigtEvent {
	private Collection<String> labels = new ArrayList<>();

	private final List<Dataset> datasets = Arrays.asList(new Dataset());

	public PieChartEvent() {
		super();
	}

	public void addData(final String label, final double value, final String color) {
		addData(label, value, color, color);
	}

	public void addData(final String label,
			final double value,
			final String backgroundColor,
			final String hoverBackgroundColor) {
		labels.add(label);
		getDataset().add(value, backgroundColor, hoverBackgroundColor);
	}

	private Dataset getDataset() {
		return datasets.get(0);
	}

	public static final class Dataset {
		private final Collection<Double> data = new ArrayList<>();

		private final Collection<String> backgroundColor = new ArrayList<>();

		private final Collection<String> hoverBackgroundColor = new ArrayList<>();

		private Dataset() {
			// empty by design
		}

		private void add(final double value, final String backgroundColor, final String hoverBackgroundColor) {
			data.add(value);
			this.backgroundColor.add(backgroundColor);
			this.hoverBackgroundColor.add(hoverBackgroundColor);
		}
	}
}
