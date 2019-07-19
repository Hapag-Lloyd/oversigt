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
import com.google.common.base.Strings;
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
	private final Optional<Method> getter;

	@JsonIgnore
	private final Optional<Method> setter;

	@JsonIgnore
	private final Optional<Class<?>> clazz;

	@JsonIgnore
	private final Optional<JsonHint> hint;

	private final boolean json;

	private final Optional<String> jsonSchema;

	// TODO make non-public
	public EventSourceProperty(@NotBlank final String name,
			@NotBlank final String displayName,
			@Nullable final String description,
			final String inputType,
			final boolean customValuesAllowed) {
		this.name = name;
		this.displayName = displayName;
		this.description = Strings.emptyToNull(description);
		this.inputType = inputType;
		this.customValuesAllowed = customValuesAllowed;
		getter = Optional.empty();
		setter = Optional.empty();
		clazz = Optional.empty();
		hint = Optional.empty();
		json = false;
		jsonSchema = Optional.empty();
	}

	// TODO make non-public
	public EventSourceProperty(@NotBlank final String name,
			@NotBlank final String displayName,
			@Nullable final String description,
			final String inputType,
			final boolean customValuesAllowed,
			final Method getter,
			final Method setter,
			final Class<?> clazz,
			@Nullable final JsonHint hint,
			final boolean json,
			@Nullable final String jsonSchema) {
		this.name = name;
		this.displayName = displayName;
		this.description = Strings.emptyToNull(description);
		this.inputType = inputType;
		this.customValuesAllowed = customValuesAllowed;
		this.getter = Optional.of(getter);
		this.setter = Optional.of(setter);
		this.clazz = Optional.of(clazz);
		this.hint = Optional.ofNullable(hint);
		this.json = json;
		this.jsonSchema = Optional.ofNullable(jsonSchema);
	}

	// TODO make non-public
	public void addAllowedValue(@NotNull final String value, final String title) {
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

	public Optional<Method> getGetter() {
		return getter;
	}

	public Optional<Method> getSetter() {
		return setter;
	}

	public Optional<Class<?>> getClazz() {
		return clazz;
	}

	public Optional<JsonHint> getHint() {
		return hint;
	}

	public boolean isJson() {
		return json;
	}

	public Optional<String> getJsonSchema() {
		return jsonSchema;
	}

	@JsonIgnore
	public String getType() {
		return getter.isPresent() ? "PROPERTY" : "DATA";
	}

	@Override
	public int compareTo(@Nullable final EventSourceProperty that) {
		return getDisplayName()
				.compareToIgnoreCase(Optional.ofNullable(that).map(EventSourceProperty::getDisplayName).orElse(""));
	}

	@Override
	public String toString() {
		return getName() + getClazz().map(Class::getSimpleName).map(s -> " (" + s + ")").orElse("");
	}
}
