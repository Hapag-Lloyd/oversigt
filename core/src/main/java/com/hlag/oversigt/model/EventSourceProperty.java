package com.hlag.oversigt.model;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.apache.commons.text.WordUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hlag.oversigt.sources.data.JsonHint;

import de.larssh.utils.Optionals;
import edu.umd.cs.findbugs.annotations.Nullable;

public class EventSourceProperty implements Comparable<EventSourceProperty> {
	@NotBlank
	private String name;

	@NotBlank
	private final String displayName;

	@Nullable
	private final String description;

	@NotBlank
	private final String inputType;

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

	EventSourceProperty(@NotBlank final String name,
			@NotBlank final String displayName,
			@Nullable final String description,
			final String inputType,
			final boolean customValuesAllowed,
			final Method getter,
			final Method setter,
			final Class<?> clazz,
			final JsonHint hint,
			final boolean json,
			final String jsonSchema) {
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

	void addAllowedValue(@NotNull final String value, final String title) {
		allowedValues.put(value,
				Optionals.ofNonEmpty(title).orElseGet(() -> WordUtils.capitalizeFully(value.replace('_', ' '))));
	}

	public String getName() {
		return name;
	}

	public String getDisplayName() {
		return displayName;
	}

	@Nullable
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
		return getter == null ? "DATA" : "PROPERTY";
	}

	@Override
	public int compareTo(@Nullable final EventSourceProperty that) {
		return getDisplayName()
				.compareToIgnoreCase(Optional.ofNullable(that).map(EventSourceProperty::getDisplayName).orElse(""));
	}

	@Override
	public String toString() {
		return getName() + (getClazz() != null ? " (" + getClazz().getSimpleName() + ")" : "");
	}
}
