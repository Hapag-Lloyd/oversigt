package com.hlag.oversigt.sources.event;

import com.hlag.oversigt.core.event.OversigtEvent;

public class TextEvent extends OversigtEvent {
	private final String text;

	public TextEvent(final String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}
}
