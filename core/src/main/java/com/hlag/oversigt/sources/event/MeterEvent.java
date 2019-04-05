package com.hlag.oversigt.sources.event;

import com.hlag.oversigt.core.event.OversigtEvent;

public class MeterEvent extends OversigtEvent {
	private final byte value;

	public MeterEvent(final byte value) {
		this.value = value;
	}

	public MeterEvent(final String title, final byte value) {
		this.value = value;
		setTitle(title);
	}

	public byte getValue() {
		return value;
	}
}
