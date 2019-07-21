package com.hlag.oversigt.model;

import static java.util.Optional.empty;

import java.util.Collections;
import java.util.LinkedHashSet;
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
	private final Set<@NotNull EventSourceProperty> properties;

	@NotNull
	private final Set<@NotNull EventSourceProperty> dataItems;

	@JsonIgnore
	private final Set<@NotBlank String> dataItemsToHide;

	private EventSourceDescriptor(final EventSourceKey key,
			final String displayName,
			final Optional<String> description,
			final String view,
			final Optional<Class<? extends Service>> serviceClass,
			final Optional<Class<? extends OversigtEvent>> eventClass,
			final Class<? extends Module> moduleClass,
			final boolean standAlone,
			final Set<EventSourceProperty> properties,
			final Set<EventSourceProperty> dataItems,
			final Set<String> dataItemsToHide) {
		this.key = key;
		this.displayName = displayName;
		this.description = description;
		this.view = view;
		this.serviceClass = serviceClass;
		this.eventClass = eventClass;
		this.moduleClass = moduleClass;
		this.standAlone = standAlone;
		this.properties = Collections.unmodifiableSet(new LinkedHashSet<>(properties));
		this.dataItems = Collections.unmodifiableSet(new LinkedHashSet<>(dataItems));
		this.dataItemsToHide = Collections.unmodifiableSet(new LinkedHashSet<>(dataItemsToHide));
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

	EventSourceProperty getProperty(final String name) {
		return properties.stream().filter(p -> name.equals(p.getName())).findAny().get();
	}

	EventSourceProperty getDataItem(final String name) {
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

	public static final class Builder {
		private final EventSourceKey key;

		private final String displayName;

		private final Optional<String> description;

		private final String view;

		private final Optional<Class<? extends Service>> serviceClass;

		private final Optional<Class<? extends OversigtEvent>> eventClass;

		private final Class<? extends Module> moduleClass;

		private final boolean standAlone;

		private final Set<EventSourceProperty> properties = new TreeSet<>();

		private final Set<EventSourceProperty> dataItems = new TreeSet<>();

		private final Set<String> dataItemsToHide = new TreeSet<>();

		public Builder(final EventSourceKey key,
				final String displayName,
				final Optional<String> description,
				final String view,
				final boolean standAlone) {
			this.key = key;
			this.displayName = displayName;
			this.description = description;
			this.view = view;
			serviceClass = empty();
			eventClass = empty();
			moduleClass = NOP.class;
			this.standAlone = standAlone;
		}

		public Builder(final EventSourceKey key,
				final String displayName,
				final String description,
				final String view,
				final Class<? extends Service> serviceClass,
				final Class<? extends OversigtEvent> eventClass,
				final Class<? extends Module> moduleClass) {
			this.key = key;
			this.displayName = displayName;
			this.description = Optional.of(description);
			this.view = view;
			this.serviceClass = Optional.of(serviceClass);
			this.eventClass = Optional.of(eventClass);
			this.moduleClass = moduleClass;
			standAlone = false;
		}

		public String getView() {
			return view;
		}

		public Set<String> getDataItemsToHide() {
			return dataItemsToHide;
		}

		public void addProperty(final EventSourceProperty property) {
			properties.add(Objects.requireNonNull(property, "Property must not be null"));
		}

		public void addDataItem(final EventSourceProperty dataItem) {
			dataItems.add(Objects.requireNonNull(dataItem, "Data item must not be null"));
		}

		public void addDataItemToHide(final String itemName) {
			dataItemsToHide
					.add(Objects.requireNonNull(Strings.emptyToNull(itemName), "Item name must not be null or empty"));
		}

		public EventSourceDescriptor build() {
			return new EventSourceDescriptor(key,
					displayName,
					description,
					view,
					serviceClass,
					eventClass,
					moduleClass,
					standAlone,
					properties,
					dataItems,
					dataItemsToHide);
		}
	}
}
