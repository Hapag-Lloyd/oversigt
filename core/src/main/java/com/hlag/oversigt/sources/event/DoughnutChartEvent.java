package com.hlag.oversigt.sources.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.hlag.oversigt.core.OversigtEvent;

public class DoughnutChartEvent extends OversigtEvent {
	private Collection<String> labels = new ArrayList<>();
	private List<Dataset> datasets = Arrays.asList(new Dataset());
	
	public void add(String name, double value, String color){
		labels.add(name);
		datasets.get(0).data.add(value);
		datasets.get(0).backgroundColor.add(color);
		datasets.get(0).hoverBackgroundColor.add(color);
	}

	private static final class Dataset {
		private final Collection<Double> data = new ArrayList<>();
		private final Collection<String> backgroundColor = new ArrayList<>();
		private final Collection<String> hoverBackgroundColor = new ArrayList<>();
	}
}
