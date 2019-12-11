package com.hlag.oversigt.sources.event;

import java.util.List;

import com.hlag.oversigt.core.event.OversigtEvent;

public class ListEvent<T> extends OversigtEvent {
	private final List<? extends T> items;

	public ListEvent(final List<? extends T> items) {
		this.items = items;
	}

	public List<? extends T> getItems() {
		return items;
	}
}
