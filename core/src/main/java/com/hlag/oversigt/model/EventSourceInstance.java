package com.hlag.oversigt.model;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

	public EventSourceInstance(EventSourceDescriptor descriptor,
			String id,
			String name,
			boolean enabled,
			Duration frequency,
			String createdBy,
			String lastChangeBy) {
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

	Object getPropertyValue(EventSourceProperty property) {
		return propertyValues.get(property);
	}

	public String getPropertyValueString(EventSourceProperty property) {
		if (property.getClazz() != null) {
			return Optional.ofNullable(propertyValues.get(property))
					.map(v -> DashboardController.getInstance().getValueString(property, v))
					.orElse("");
		} else {
			return Optional.ofNullable((String) propertyValues.get(property)).orElse("");
		}
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Duration getFrequency() {
		return frequency;
	}

	public void setFrequency(Duration frequency) {
		this.frequency = frequency;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	void setProperty(EventSourceProperty property, Object value) {
		if (property.getClazz() == null && !property.isCustomValuesAllowed() && !property.getAllowedValues().isEmpty()
				&& !property.getAllowedValues().contains(value)) {
			throw new RuntimeException("The value '" + value + "' is not allowed for property " + property.getName());
		}
		this.propertyValues.put(property, value);
	}

	public void setPropertyString(EventSourceProperty property, String stringValue) {
		setProperty(property, DashboardController.getInstance().createObjectFromString(property, stringValue));
	}

	public boolean hasPropertyValue(EventSourceProperty property) {
		return propertyValues.containsKey(property);
	}

	public void removeProperty(EventSourceProperty property) {
		propertyValues.remove(property);
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public String getLastChangeBy() {
		return lastChangeBy;
	}

	public void setLastChangeBy(String lastChangeBy) {
		this.lastChangeBy = lastChangeBy;
	}

	@Override
	public int compareTo(EventSourceInstance that) {
		return COMPARATOR.compare(this, that);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (id == null ? 0 : id.hashCode());
		result = prime * result + (name == null ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		EventSourceInstance other = (EventSourceInstance) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return id + " \"" + name + "\""
				+ (descriptor.getServiceClass() != null
						? " (" + descriptor.getServiceClass().getSimpleName() + ")"
						: "");
	}
}
