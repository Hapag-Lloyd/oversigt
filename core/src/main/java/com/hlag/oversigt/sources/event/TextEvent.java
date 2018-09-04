package com.hlag.oversigt.sources.event;

import com.hlag.oversigt.core.OversigtEvent;

public class TextEvent extends OversigtEvent {
	private final String text;

	public TextEvent(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}
}
