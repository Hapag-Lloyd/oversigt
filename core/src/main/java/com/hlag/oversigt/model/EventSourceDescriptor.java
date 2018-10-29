package com.hlag.oversigt.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Service;
import com.google.inject.Module;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;

public class EventSourceDescriptor implements Comparable<EventSourceDescriptor> {
	@NotNull
	private final EventSourceKey key;

	@NotBlank
	@NotNull
	private final String displayName;
	private final String description;
	@NotNull
	private final Set<@NotNull EventSourceProperty> properties = new TreeSet<>();
	@NotNull
	private final Set<@NotNull EventSourceProperty> dataItems = new TreeSet<>();
	@JsonIgnore
	private final Set<@NotBlank String> dataItemsToHide = new TreeSet<>();

	@NotBlank
	@JsonProperty(access = Access.READ_ONLY, required = false)
	private final String view;
	private final Class<? extends Service> serviceClass;
	private final Class<? extends OversigtEvent> eventClass;
	@JsonIgnore
	private final Class<? extends Module> moduleClass;
	private boolean standAlone = false;

	EventSourceDescriptor(@NotNull EventSourceKey key,
			@NotBlank String displayName,
			String description,
			@NotBlank String view,
			boolean standAlone) {
		this(key, displayName, description, view, null, null, null);
		this.standAlone = standAlone;
	}

	EventSourceDescriptor(@NotNull EventSourceKey key,
			@NotBlank String displayName,
			String description,
			@NotBlank String view,
			Class<? extends Service> serviceClass,
			Class<? extends OversigtEvent> eventClass,
			Class<? extends Module> moduleClass) {
		this.key = key;
		this.displayName = displayName;
		this.description = description != null && description.trim().length() > 1 ? description : null;
		this.view = view;
		this.serviceClass = serviceClass;
		this.eventClass = eventClass;
		this.moduleClass = moduleClass;
	}

	public EventSourceKey getKey() {
		return key;
	}

	public String getView() {
		return view;
	}

	public Class<? extends Service> getServiceClass() {
		return serviceClass;
	}

	@JsonProperty(access = Access.READ_ONLY, required = false)
	public String getServiceClassName() {
		return serviceClass != null ? serviceClass.getName() : null;
	}

	@JsonProperty(access = Access.READ_ONLY, required = false)
	public String getEventClassName() {
		return eventClass != null ? eventClass.getName() : null;
	}

	@JsonIgnore
	public boolean isScheduledService() {
		return serviceClass != null && ScheduledEventSource.class.isAssignableFrom(serviceClass);
	}

	public Class<? extends OversigtEvent> getEventClass() {
		return eventClass;
	}

	public Class<? extends Module> getModuleClass() {
		return moduleClass;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getDescription() {
		return description;
	}

	public Set<EventSourceProperty> getProperties() {
		return Collections.unmodifiableSet(properties);
	}

	public Set<EventSourceProperty> getDataItems() {
		return Collections.unmodifiableSet(dataItems);
	}

	public Set<String> getDataItemsToHide() {
		return Collections.unmodifiableSet(dataItemsToHide);
	}

	void addProperty(@NotNull EventSourceProperty property) {
		this.properties.add(Objects.requireNonNull(property, "Property must not be null"));
	}

	void addDataItem(@NotNull EventSourceProperty dataItem) {
		this.dataItems.add(Objects.requireNonNull(dataItem, "Data item must not be null"));
	}

	void addDataItemToHide(@NotBlank String itemName) {
		this.dataItemsToHide
				.add(Objects.requireNonNull(Strings.emptyToNull(itemName), "Item name must not be null or empty"));
	}

	EventSourceProperty getProperty(@NotBlank String name) {
		return properties.stream().filter(p -> name.equals(p.getName())).findAny().get();
	}

	EventSourceProperty getDataItem(@NotBlank String name) {
		return dataItems.stream().filter(p -> name.equals(p.getName())).findAny().get();
	}

	boolean isStandAlone() {
		return standAlone;
	}

	@Override
	public int compareTo(EventSourceDescriptor that) {
		return this.getDisplayName().compareToIgnoreCase(that.getDisplayName());
	}

	@Override
	public String toString() {
		return key + " (" + displayName + " / " + view + ")";
	}
}
