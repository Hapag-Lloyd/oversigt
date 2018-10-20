package com.hlag.oversigt.model;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.apache.commons.text.WordUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Strings;
import com.hlag.oversigt.sources.data.JsonHint;

public class EventSourceProperty implements Comparable<EventSourceProperty> {
	@NotBlank
	private String name = null;
	@NotBlank
	private final String displayName;
	private final String description;
	@NotBlank
	private final String inputType;//= "text";
	private final Map<@NotNull String, @NotNull String> allowedValues = new LinkedHashMap<>();
	private final boolean customValuesAllowed;

	@JsonIgnore
	private final Method getter;
	@JsonIgnore
	private final Method setter;
	@JsonIgnore
	private final Class<?> clazz;
	@JsonIgnore
	private final JsonHint hint;
	private final boolean json;
	private final String jsonSchema;

	EventSourceProperty(@NotBlank String name,
			@NotBlank String displayName,
			String description,
			String inputType,
			boolean customValuesAllowed,
			Method getter,
			Method setter,
			Class<?> clazz,
			JsonHint hint,
			boolean json,
			String jsonSchema) {
		this.name = name;
		this.displayName = displayName;
		this.description = description != null && description.trim().length() > 1 ? description : null;
		this.inputType = inputType;
		this.customValuesAllowed = customValuesAllowed;
		this.getter = getter;
		this.setter = setter;
		this.clazz = clazz;
		this.hint = hint;
		this.json = json;
		this.jsonSchema = jsonSchema;
	}

	void addAllowedValue(@NotNull String value, String title) {
		if (Strings.isNullOrEmpty(title)) {
			title = WordUtils.capitalizeFully(value.replace('_', ' '));
		}
		allowedValues.put(value, title);
	}

	public String getName() {
		return name;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getDescription() {
		return description;
	}

	public String getInputType() {
		return inputType;
	}

	public Collection<String> getAllowedValues() {
		return allowedValues.keySet(); // TODO: Return map
	}

	public Map<String, String> getAllowedValuesMap() {
		return allowedValues;
	}

	public boolean isCustomValuesAllowed() {
		return customValuesAllowed;
	}

	public Method getGetter() {
		return getter;
	}

	public Method getSetter() {
		return setter;
	}

	public Class<?> getClazz() {
		return clazz;
	}

	public JsonHint getHint() {
		return hint;
	}

	public boolean isJson() {
		return json;
	}

	public String getJsonSchema() {
		return jsonSchema;
	}

	@JsonIgnore
	public String getType() {
		if (getter != null) {
			return "PROPERTY";
		} else {
			return "DATA";
		}
	}

	@Override
	public int compareTo(EventSourceProperty that) {
		return this.getDisplayName().compareToIgnoreCase(that.getDisplayName());
	}

	@Override
	public String toString() {
		return getName() + (getClazz() != null ? " (" + getClazz().getSimpleName() + ")" : "");
	}
}
