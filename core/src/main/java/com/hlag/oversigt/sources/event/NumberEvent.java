package com.hlag.oversigt.sources.event;

import com.hlag.oversigt.core.event.OversigtEvent;

public class NumberEvent extends OversigtEvent {
	private final int current;

	private final int last;

	public NumberEvent(final int current, final int last) {
		this.current = current;
		this.last = last;
	}

	public int getCurrent() {
		return current;
	}

	public int getLast() {
		return last;
	}
}
