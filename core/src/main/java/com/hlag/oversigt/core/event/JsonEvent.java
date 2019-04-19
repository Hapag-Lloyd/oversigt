package com.hlag.oversigt.core.event;

public class JsonEvent extends OversigtEvent {
	private final String json;

	public JsonEvent(final String id, final String json) {
		setId(id);
		this.json = json;
	}

	public String getJson() {
		return json;
	}
}
