package com.hlag.oversigt.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
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
import com.hlag.oversigt.controller.EventSourceKey;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSource.NOP;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;

import edu.umd.cs.findbugs.annotations.Nullable;

public class EventSourceDescriptor implements Comparable<EventSourceDescriptor> {
	@NotNull
	private final EventSourceKey key;

	@NotBlank
	@NotNull
	private final String displayName;

	private final Optional<String> description;

	@NotBlank
	@JsonProperty(access = Access.READ_ONLY, required = false)
	private final String view;

	private final Optional<Class<? extends Service>> serviceClass;

	private final Optional<Class<? extends OversigtEvent>> eventClass;

	@JsonIgnore
	private final Class<? extends Module> moduleClass;

	private final boolean standAlone;

	@NotNull
	private final Set<@NotNull EventSourceProperty> properties = new TreeSet<>();

	@NotNull
	private final Set<@NotNull EventSourceProperty> dataItems = new TreeSet<>();

	@JsonIgnore
	private final Set<@NotBlank String> dataItemsToHide = new TreeSet<>();

	// TODO make non-public
	public EventSourceDescriptor(@NotNull final EventSourceKey key,
			@NotNull final String displayName,
			@Nullable final String description,
			@NotBlank final String view,
			final boolean standAlone) {
		this(key,
				displayName,
				Optional.ofNullable(Strings.emptyToNull(description)),
				view,
				Optional.empty(),
				Optional.empty(),
				NOP.class,
				standAlone);
	}

	// TODO make non-public
	public EventSourceDescriptor(@NotNull final EventSourceKey key,
			@NotBlank final String displayName,
			@Nullable final String description,
			@NotBlank final String view,
			@Nullable final Class<? extends Service> serviceClass,
			@Nullable final Class<? extends OversigtEvent> eventClass,
			final Class<? extends Module> moduleClass) {
		this(key,
				displayName,
				Optional.ofNullable(Strings.emptyToNull(description)),
				view,
				Optional.ofNullable(serviceClass),
				Optional.ofNullable(eventClass),
				moduleClass,
				false);
	}

	private EventSourceDescriptor(@NotNull final EventSourceKey key,
			@NotBlank @NotNull final String displayName,
			final Optional<String> description,
			@NotBlank final String view,
			final Optional<Class<? extends Service>> serviceClass,
			final Optional<Class<? extends OversigtEvent>> eventClass,
			final Class<? extends Module> moduleClass,
			final boolean standAlone) {
		super();
		this.key = key;
		this.displayName = displayName;
		this.description = description;
		this.view = view;
		this.serviceClass = serviceClass;
		this.eventClass = eventClass;
		this.moduleClass = moduleClass;
		this.standAlone = standAlone;
	}

	public EventSourceKey getKey() {
		return key;
	}

	public String getView() {
		return view;
	}

	public Optional<Class<? extends Service>> getServiceClass() {
		return serviceClass;
	}

	@JsonProperty(access = Access.READ_ONLY, required = false)
	@Nullable
	public String getServiceClassName() {
		return serviceClass.map(Class::getName).orElse(null);
	}

	@JsonProperty(access = Access.READ_ONLY, required = false)
	@Nullable
	public String getEventClassName() {
		return eventClass.map(Class::getName).orElse(null);
	}

	@JsonIgnore
	public boolean isScheduledService() {
		return serviceClass.map(ScheduledEventSource.class::isAssignableFrom).orElse(false);
	}

	public boolean isDeprecated() {
		return serviceClass.map(clazz -> clazz.isAnnotationPresent(Deprecated.class)).orElse(false);
	}

	@Nullable
	public Class<? extends OversigtEvent> getEventClass() {
		return eventClass.orElse(null);
	}

	public Class<? extends Module> getModuleClass() {
		return moduleClass;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Optional<String> getDescription() {
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

	// TODO make non-public
	public void addProperty(@NotNull final EventSourceProperty property) {
		properties.add(Objects.requireNonNull(property, "Property must not be null"));
	}

	// TODO make non-public
	public void addDataItem(@NotNull final EventSourceProperty dataItem) {
		dataItems.add(Objects.requireNonNull(dataItem, "Data item must not be null"));
	}

	// TODO make non-public
	public void addDataItemToHide(@NotBlank final String itemName) {
		dataItemsToHide
				.add(Objects.requireNonNull(Strings.emptyToNull(itemName), "Item name must not be null or empty"));
	}

	EventSourceProperty getProperty(@NotBlank final String name) {
		return properties.stream().filter(p -> name.equals(p.getName())).findAny().get();
	}

	EventSourceProperty getDataItem(@NotBlank final String name) {
		return dataItems.stream().filter(p -> name.equals(p.getName())).findAny().get();
	}

	// TODO make non-public
	public boolean isStandAlone() {
		return standAlone;
	}

	@Override
	public int compareTo(@Nullable final EventSourceDescriptor that) {
		return getDisplayName()
				.compareToIgnoreCase(Optional.ofNullable(that).map(EventSourceDescriptor::getDisplayName).orElse(""));
	}

	@Override
	public String toString() {
		return key + " (" + displayName + " / " + view + ")";
	}
}
