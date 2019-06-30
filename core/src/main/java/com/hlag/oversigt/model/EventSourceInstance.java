package com.hlag.oversigt.model;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import edu.umd.cs.findbugs.annotations.Nullable;

public class EventSourceInstance implements Comparable<EventSourceInstance> {
	public static final Comparator<EventSourceInstance> COMPARATOR = Comparator.comparing(EventSourceInstance::getName)
			.thenComparing(Comparator.comparing(EventSourceInstance::getId));

	@JsonIgnore
	private final EventSourceDescriptor descriptor;

	private String id;

	private String name;

	private boolean enabled;

	private Duration frequency;

	private final Map<EventSourceProperty, Object> propertyValues = new HashMap<>();

	private final String createdBy;

	private String lastChangeBy;

	public EventSourceInstance(final EventSourceDescriptor descriptor,
			final String id,
			final String name,
			final boolean enabled,
			final Duration frequency,
			final String createdBy,
			final String lastChangeBy) {
		this.descriptor = descriptor;
		this.id = id;
		this.name = name;
		this.enabled = enabled;
		this.frequency = frequency;

		this.createdBy = Objects.requireNonNull(createdBy, "The creator of an event source must not be null");
		this.lastChangeBy = Objects.requireNonNull(lastChangeBy);
	}

	public EventSourceDescriptor getDescriptor() {
		return descriptor;
	}

	Object getPropertyValue(final EventSourceProperty property) {
		return propertyValues.get(property);
	}

	public String getPropertyValueString(final EventSourceProperty property) {
		final Optional<Object> value = Optional.ofNullable(propertyValues.get(property));
		if (!property.getClazz().isPresent()) {
			return value.map(v -> (String) v).orElse("");
		}
		return value.map(v -> DashboardController.getInstance().getValueString(property, v)).orElse("");
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public Duration getFrequency() {
		return frequency;
	}

	public void setFrequency(final Duration frequency) {
		this.frequency = frequency;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
	}

	void setProperty(final EventSourceProperty property, final Object value) {
		Objects.requireNonNull(value, "The value of a property must not be null");
		if (!property.getClazz().isPresent()
				&& !property.isCustomValuesAllowed()
				&& !property.getAllowedValues().isEmpty()
				&& !property.getAllowedValues().contains(value)) {
			throw new RuntimeException("The value '" + value + "' is not allowed for property " + property.getName());
		}
		propertyValues.put(property, value);
	}

	public void setPropertyString(final EventSourceProperty property, final String stringValue) {
		setProperty(property, DashboardController.getInstance().createObjectFromString(property, stringValue));
	}

	public boolean hasPropertyValue(final EventSourceProperty property) {
		return propertyValues.containsKey(property);
	}

	public void removeProperty(final EventSourceProperty property) {
		propertyValues.remove(property);
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public String getLastChangeBy() {
		return lastChangeBy;
	}

	public void setLastChangeBy(final String lastChangeBy) {
		this.lastChangeBy = lastChangeBy;
	}

	@Override
	public int compareTo(@Nullable final EventSourceInstance that) {
		return COMPARATOR.compare(this, that);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id.hashCode();
		result = prime * result + name.hashCode();
		return result;
	}

	@Override
	public boolean equals(@Nullable final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			if (obj instanceof String) {
				return obj.equals(getId());
			}
			return false;
		}
		final EventSourceInstance other = (EventSourceInstance) obj;
		if (!id.equals(other.id)) {
			return false;
		}
		if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return id
				+ " \""
				+ name
				+ "\""
				+ descriptor.getServiceClass().map(c -> " (" + c.getSimpleName() + ")").orElse("");
	}

	public static Predicate<EventSourceInstance> createFilter(final String filter) {
		return i -> i.getName().toLowerCase().contains(filter)
				|| i.getId().toLowerCase().contains(filter)
				|| Optional.ofNullable(i.getFrequency()).map(Object::toString).orElse("").toLowerCase().contains(filter)
				|| i.getDescriptor()
						.getProperties()
						.stream()
						.filter(i::hasPropertyValue)
						.anyMatch(p -> i.getPropertyValueString(p).toLowerCase().contains(filter))
				|| i.getDescriptor()
						.getDataItems()
						.stream()
						.filter(i::hasPropertyValue)
						.anyMatch(p -> i.getPropertyValueString(p).toLowerCase().contains(filter));
	}
}
