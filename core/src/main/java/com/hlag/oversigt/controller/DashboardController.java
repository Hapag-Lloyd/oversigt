package com.hlag.oversigt.controller;

import static com.hlag.oversigt.util.Utils.copyProperties;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.EventSourceInstance;
import com.hlag.oversigt.model.EventSourceProperty;
import com.hlag.oversigt.model.Widget;
import com.hlag.oversigt.properties.JsonBasedData;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.properties.SerializablePropertyController;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.sources.event.ReloadEvent;
import com.hlag.oversigt.storage.Storage;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.TypeUtils;

import edu.umd.cs.findbugs.annotations.Nullable;

@Singleton
public class DashboardController {
	private static final Logger LOGGER = LoggerFactory.getLogger(DashboardController.class);

	@Nullable
	private static DashboardController instance;

	{
		instance = this;
		EventSourceKey.setEventSourceRenamer(this::updateEventSourceClasses);
	}

	public static DashboardController getInstance() {
		final DashboardController checkedInstance = instance;
		if (checkedInstance == null) {
			throw new RuntimeException("Instance has not been initialized yet.");
		}
		return checkedInstance;
	}

	@Inject
	private Storage storage;

	@Inject
	private EventBus eventBus;

	@Inject
	private SerializablePropertyController spController;

	@Inject
	private final EventSourceInstanceController eventSourceInstanceController;

	// Dashboards
	private final Map<String, Dashboard> dashboards = Collections.synchronizedMap(new HashMap<>());

	@Inject
	DashboardController(final EventSourceInstanceController eventSourceInstanceController) {
		this.eventSourceInstanceController = eventSourceInstanceController;
		eventSourceInstanceController
				.registerEventSourceInstanceUpdatedEventHandler(this::reloadDashboardsWithEventSourceInstance);
	}

	public Collection<String> getDashboardIds() {
		return dashboards.keySet();
	}

	public Optional<Dashboard> getDashboard(final String id) {
		return Optional.ofNullable(dashboards.get(id));
	}

	public Dashboard getDashboard(final Widget widget) {
		return dashboards.values()
				.stream()
				.filter(d -> d.getWidgets().stream().mapToInt(Widget::getId).anyMatch(i -> i == widget.getId()))
				.findAny()
				.get();
	}

	public void loadDashboards() {
		dashboards.clear();
		dashboards.putAll(storage.loadDashboards()
				.stream()
				.peek(db -> LOGGER.info("Loading dashboard: {} ({})", db.getId(), db.getTitle()))
				.peek(db -> db.getModifiableWidgets()
						.addAll(storage.loadWidgetDatas(db, eventSourceInstanceController::getEventSourceInstance)))
				.collect(toMap(Dashboard::getId, Function.identity())));
	}

	/**
	 * Creates a new dashboard instance, persists it in the storage and makes it
	 * available for other users.
	 *
	 * @param id      the ID of the dashboard to be created
	 * @param owner   the owner of the dashboard to be created
	 * @param enabled <code>true</code> if the dashboard should be enabled by
	 *                default
	 * @return the newly created dashboard object or <code>null</code> if a
	 *         dashboard with the given ID already exists.
	 */
	public Optional<Dashboard> createDashboard(final String id, final Principal owner, final boolean enabled) {
		if (dashboards.containsKey(id)) {
			return Optional.empty();
		}
		final Dashboard dashboard = new Dashboard(id, owner.getUsername(), enabled);
		storage.persistDashboard(dashboard);
		dashboards.put(id, dashboard);
		// TODO if owner is not admin -> send mail to admins
		return Optional.of(dashboard);
	}

	public boolean updateDashboard(final Dashboard dashboard) {
		final Optional<Dashboard> originalDashboard = getDashboard(dashboard.getId());
		if (!originalDashboard.isPresent()) {
			return false;
		}
		copyProperties(dashboard, originalDashboard.get());
		storage.persistDashboard(originalDashboard.get());
		return true;
	}

	public boolean deleteDashboard(final String id) {
		final Optional<Dashboard> dashboard = getDashboard(id);
		if (!dashboard.isPresent()) {
			return false;
		}
		return deleteDashboard(dashboard.get());
	}

	public boolean deleteDashboard(final Dashboard dashboard) {
		dashboards.remove(dashboard.getId());
		return storage.deleteDashboard(dashboard);
	}

	public void reloadDashboards(final Dashboard... dashboards) {
		if (dashboards.length > 0) {
			eventBus.post(new ReloadEvent(stream(dashboards).map(Dashboard::getId).collect(toList())));
		}
	}

	public Stream<Dashboard> getDashboardsWhereEventSourceIsUsed(final EventSourceInstance instance) {
		return dashboards.values()
				.stream()
				.filter(d -> d.getWidgets().stream().anyMatch(w -> w.getEventSourceInstance() == instance));
	}

	private void reloadDashboardsWithEventSourceInstance(final EventSourceInstance instance) {
		reloadDashboards(dashboards.values()
				.stream()
				.filter(d -> d.getWidgets().stream().map(Widget::getEventSourceInstance).anyMatch(i -> i == instance))
				.toArray(Dashboard[]::new));
	}

	public Widget createWidgetForDashboard(final Dashboard dashboard, final String eventSourceInstanceId) {
		final Widget widget = new Widget(eventSourceInstanceController.getEventSourceInstance(eventSourceInstanceId));
		storage.createWidget(dashboard, widget);
		dashboard.getModifiableWidgets().add(widget);
		reloadDashboards(dashboard);
		return widget;
	}

	public void updateWidget(final Widget widget) {
		final Dashboard dashboard = getDashboard(widget);
		final Widget originalWidget = dashboard.getWidget(widget.getId());
		if (widget != originalWidget) {
			// adapt properties to original widget
			if (originalWidget.getEventSourceInstance() != widget.getEventSourceInstance()) {
				throw new RuntimeException("The widgets have different EventSourceInstances");
			}
			copyProperties(widget, originalWidget, "eventSourceInstance");
			// update data
			for (final EventSourceProperty property : widget.getEventSourceInstance().getDescriptor().getDataItems()) {
				final String value = widget.getWidgetData(property);
				if (value != null) {
					originalWidget.setWidgetData(property, value);
				} else {
					originalWidget.removeWidgetData(property);
				}
			}
		}
		storage.updateWidget(widget);
		reloadDashboards(dashboard);
	}

	public void deleteWidget(final Widget widget) {
		final Dashboard dashboard = getDashboard(widget);
		final Widget widgetReference = dashboard.getWidget(widget.getId());
		dashboard.getModifiableWidgets().remove(widgetReference);
		storage.deleteWidget(widgetReference);
		reloadDashboards(dashboard);
	}

	// TODO better handling for optionals in values!!
	// TODO make non-public
	public String getValueString(final EventSourceProperty property, final Object value) {
		try {
			if (TypeUtils.isOfType(property.getClazz().get(), SerializableProperty.class)) {
				return spController.toString((SerializableProperty) value);
			} else if (value.getClass().isArray()) {
				if (TypeUtils.isOfType(value.getClass().getComponentType(), SerializableProperty.class)) {
					throw new NotImplementedException(
							"Arrays of Objects of type SerializableProperty are not supported yet. Please refer to developers to implement this feature.");
				}
				// return Arrays.deepToString((Object[]) value);
				return JsonUtils.toJson(value);
			} else if (property.isJson() || TypeUtils.isOfType(property.getClazz().get(), JsonBasedData.class)) {
				return JsonUtils.toJson(value);
			} else {
				return value.toString();
			}
		} catch (final IllegalArgumentException | SecurityException e) {
			throw new RuntimeException("Unable to convert property '" + property.getName() + "' to string", e);
		}
	}

	private void updateEventSourceClasses(final String oldClassName, final String newClassName) {
		LOGGER.info("Rename event source from [{}] to [{}]", oldClassName, newClassName);
		storage.updateEventSourceClasses(oldClassName, newClassName);
	}

	@SuppressWarnings("unchecked")
	public <T extends Enum<T>> Object createObjectFromString(final EventSourceProperty property,
			final String inputString) {
		// TODO check all callers
		final Class<?> type = property.getClazz().orElse(String.class);
		final String string = Objects.requireNonNull(inputString, "The input string must not be null.");
		try {
			if (type == null || String.class == type) {
				return string;
			} else if (type.isPrimitive()) {
				if (type == boolean.class) {
					return string == null ? Boolean.FALSE : Boolean.parseBoolean(string);
				}
				if (type == byte.class) {
					return Byte.parseByte(string);
				}
				if (type == short.class) {
					return Short.parseShort(string);
				}
				if (type == int.class) {
					return Integer.parseInt(string);
				}
				if (type == long.class) {
					return Long.parseLong(string);
				}
				if (type == float.class) {
					return Float.parseFloat(string);
				}
				if (type == double.class) {
					return Double.parseDouble(string);
				}
				if (type == char.class) {
					return string.charAt(0);
				}
				throw new RuntimeException("Unknown primitive type: " + type);
			} else if (Enum.class.isAssignableFrom(type)) {
				final Enum<T> enumValue;
				if (string != null) {
					enumValue = Enum.valueOf((Class<T>) type, string);
				} else {
					enumValue = (Enum<T>) type.getEnumConstants()[0];
				}
				// method.invoke(eventSource, enumValue);
				return enumValue;
			} else if (SerializableProperty.class.isAssignableFrom(type)) {
				try {
					return spController.getProperty((Class<SerializableProperty>) type, Integer.parseInt(string));
				} catch (@SuppressWarnings("unused") final NumberFormatException ignore) {
					LOGGER.warn("Unable to find property type '{}' for id '{}'", type.getSimpleName(), string);
					return spController.getEmpty((Class<SerializableProperty>) type);
				}
			} else if (property.isJson() || TypeUtils.isOfType(type, JsonBasedData.class)) {
				final Object value = JsonUtils.fromJson(string, type);
				if (value != null) {
					return value;
				}
				return TypeUtils.createInstance(type);
			} else if (type.isArray()) {
				throw new RuntimeException("Unable to deserialize type " + type);
			} else if (Path.class == type) {
				return Paths.get(string);
			} else if (TemporalAmount.class == type) {
				// This is quite difficult. Java has many classes that are temporal amount.
				// We need to test a bit, which one can interpret the String.
				return TypeUtils.<TemporalAmount>tryToCreateInstance(string, Duration.class, Period.class)
						.orElseThrow(() -> new RuntimeException("Unable to create TemporalAmount from: " + string));
			} else if (type.isInterface()) {
				final String message = "Type "
						+ type.getName()
						+ " is an interface. Please use concrete classes for getters and setters.";
				LOGGER.error(message);
				throw new RuntimeException(message);
			} else {
				return TypeUtils.createInstance(type, string);
			}
		} catch (final Exception e) {
			throw new RuntimeException("Unable to set property value of type " + type + " from string '" + string + "'",
					e);
		}
	}

	public Set<Dashboard> findDashboardUsingEventSourceInstance(final String eventSourceId) {
		return dashboards.values()
				.stream()
				.filter(d -> d.getWidgets()
						.stream()
						.anyMatch(w -> w.getEventSourceInstance().getId().equals(eventSourceId)))
				.collect(toSet());
	}

	/**
	 * Delete an event source instance if the instance is not used any more
	 *
	 * @param eventSourceId the ID of the event source to remove
	 * @return <code>null</code> if the removal was successful or the names of all
	 *         dashboards containing widgets that use this event source instance
	 */
	public Set<String> deleteEventSourceInstance(final String eventSourceId) {
		// TODO rename method
		return deleteEventSourceInstance(eventSourceId, false);
	}

	public Set<String> deleteEventSourceInstance(final String eventSourceId, final boolean force) {
		// TODO rename method
		final List<Widget> widgetsToDelete = dashboards.values()
				.stream()
				.flatMap(d -> d.getWidgets().stream())
				.filter(w -> w.getEventSourceInstance().getId().equals(eventSourceId))
				.collect(toList());

		if (!force && !widgetsToDelete.isEmpty()) {
			return findDashboardUsingEventSourceInstance(eventSourceId)//
					.stream()
					.map(Dashboard::getId)
					.collect(toSet());
		} else if (force && !widgetsToDelete.isEmpty()) {
			widgetsToDelete.forEach(this::deleteWidget);
		}

		eventSourceInstanceController.deleteEventSourceInstance(eventSourceId);
		return Collections.emptySet();
	}
}
