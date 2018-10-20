package com.hlag.oversigt.sources.event;

import com.hlag.oversigt.core.event.OversigtEvent;

public class MeterEvent extends OversigtEvent {
	private final byte value;

	public MeterEvent(byte value) {
		this.value = value;
	}

	public MeterEvent(String title, byte value) {
		this.value = value;
		setTitle(title);
	}

	public byte getValue() {
		return this.value;
	}
}