package com.hlag.oversigt.core.event;

import java.util.Map;
import java.util.Objects;

import com.hlag.oversigt.util.JsonUtils;

public class JsonEvent extends OversigtEvent {
	private final Map<String, Object> map;

	@SuppressWarnings("unchecked")
	public JsonEvent(final String json) {
		map = Objects.requireNonNull(JsonUtils.fromJson(json, Map.class));
		setId(Objects.requireNonNull((String) map.get("id")));
	}

	@Override
	public String toJson() {
		return JsonUtils.toJson(map);
	}
}
