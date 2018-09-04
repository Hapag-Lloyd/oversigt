package com.hlag.oversigt.core;

public class JsonEvent extends OversigtEvent {
	private final String json;

	public JsonEvent(String id, String json) {
		setId(id);
		this.json = json;
	}

	public String getJson() {
		return json;
	}
}
