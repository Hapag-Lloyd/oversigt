package com.hlag.oversigt.sources.event;

import com.hlag.oversigt.core.OversigtEvent;

public class NumberEvent extends OversigtEvent {
	private final int current;
	private final int last;

	public NumberEvent(int current, int last) {
		this.current = current;
		this.last = last;
	}

	public int getCurrent() {
		return this.current;
	}

	public int getLast() {
		return this.last;
	}
}