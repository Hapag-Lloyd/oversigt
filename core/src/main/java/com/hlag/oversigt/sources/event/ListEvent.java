package com.hlag.oversigt.sources.event;

import java.util.List;

import com.hlag.oversigt.core.OversigtEvent;

public class ListEvent<T> extends OversigtEvent {
	private final List<T> items;

	public ListEvent(List<T> items) {
		this.items = items;
	}

	public List<T> getItems() {
		return items;
	}
}
