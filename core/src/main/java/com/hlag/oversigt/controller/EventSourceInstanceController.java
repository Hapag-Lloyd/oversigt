package com.hlag.oversigt.controller;

import static com.hlag.oversigt.util.Utils.copyProperties;
import static java.util.stream.Collectors.toSet;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Consumer;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.Listener;
import com.google.common.util.concurrent.Service.State;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.hlag.oversigt.core.eventsource.EventId;
import com.hlag.oversigt.core.eventsource.EventSource.NOP;
import com.hlag.oversigt.core.eventsource.EventSourceStatisticsManager;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;
import com.hlag.oversigt.model.EventSourceDescriptor;
import com.hlag.oversigt.model.EventSourceInstance;
import com.hlag.oversigt.model.EventSourceProperty;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.storage.Storage;
import com.hlag.oversigt.util.SimpleReadWriteLock;
import com.hlag.oversigt.util.TypeUtils;

import edu.umd.cs.findbugs.annotations.Nullable;

@Singleton
public class EventSourceInstanceController {
	private static final Logger LOGGER = LoggerFactory.getLogger(EventSourceInstanceController.class);

	@Inject
	private Storage storage;

	@Inject
	private EventSourceNameGenerator nameGenerator;

	@Inject
	private Injector injector;

	@Inject
	private EventSourceDescriptorController descriptorController;

	@Inject
	private EventSourceStatisticsManager statisticsManager;

	// access to this map is synchronized through the lock object
	private final SimpleReadWriteLock eventSourceInstancesLock = new SimpleReadWriteLock();

	private volatile Map<EventSourceInstance, Service> eventSourceInstancesInternal = new HashMap<>();

	private List<Consumer<EventSourceInstance>> eventSourceInstanceUpdatedEventHandlers = new ArrayList<>();

	public EventSourceInstanceController() {
		// nothing to do
	}

	public void registerEventSourceInstanceUpdatedEventHandler(final Consumer<EventSourceInstance> eventHandler) {
		eventSourceInstanceUpdatedEventHandlers.add(eventHandler);
	}

	private void fireEventSourceInstanceUpdatedEvent(final EventSourceInstance instance) {
		eventSourceInstanceUpdatedEventHandlers.forEach(consumer -> consumer.accept(instance));
	}

	@SuppressWarnings("unchecked")
	// TODO make private
	<S extends Service> Optional<S> getService(final EventSourceInstance instance) {
		return eventSourceInstancesLock.read(() -> Optional.ofNullable((S) eventSourceInstancesInternal.get(instance)));
	}

	// TODO make private
	void setService(final EventSourceInstance instance, final Service service) {
		eventSourceInstancesLock.write(() -> eventSourceInstancesInternal.put(instance, service));
	}

	// TODO make private
	void unsetService(final EventSourceInstance instance) {
		eventSourceInstancesLock.write(() -> eventSourceInstancesInternal.put(instance, null));
	}

	// TODO make private
	void removeEventSourceInstance(final EventSourceInstance instance) {
		eventSourceInstancesLock.write(() -> {
			/*
			 * This method is a work around because (why ever)
			 * <code>eventSourceInstances_internal.remove(instance);</code> didn't work and
			 * did not remove the instance from the map.
			 */
			final Map<EventSourceInstance, Service> newMap = eventSourceInstancesInternal.entrySet()
					.stream()
					.filter(e -> !e.getKey().equals(instance))
					.collect(LinkedHashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), LinkedHashMap::putAll);
			eventSourceInstancesInternal.clear();
			eventSourceInstancesInternal.putAll(newMap);
		});
	}

	public Collection<EventSourceInstance> getEventSourceInstances() {
		return eventSourceInstancesLock.read(() -> new HashSet<>(eventSourceInstancesInternal.keySet()));
	}

	public EventSourceInstance getEventSourceInstance(final String id) {
		// TODO replace by optional
		return eventSourceInstancesLock.read(
				() -> eventSourceInstancesInternal.keySet().stream().filter(i -> id.equals(i.getId())).findAny().get());
	}

	public void updateEventSourceInstance(final EventSourceInstance updatedInstance) {
		final EventSourceInstance origInstance = getEventSourceInstance(updatedInstance.getId());

		if (origInstance != updatedInstance) {
			copyProperties(updatedInstance, origInstance);
			for (final EventSourceProperty p : origInstance.getDescriptor().getProperties()) {
				origInstance.setProperty(p, updatedInstance.getPropertyValue(p));
			}
			for (final EventSourceProperty p : origInstance.getDescriptor().getDataItems()) {
				if (updatedInstance.hasPropertyValue(p)) {
					origInstance.setProperty(p, updatedInstance.getPropertyValue(p));
				} else {
					origInstance.removeProperty(p);
				}
			}
		}

		storage.persistEventSourceInstance(origInstance);
		final boolean running = isRunning(origInstance);
		boolean hasStopped = false;
		if (!origInstance.isEnabled() && running) {
			stopInstance(origInstance.getId());
			hasStopped = true;
		}
		if (running && origInstance.isEnabled()) {
			if (!hasStopped) {
				stopInstance(origInstance.getId());
			}
			startInstance(origInstance.getId(), false);
			fireEventSourceInstanceUpdatedEvent(origInstance);
		}
	}

	public void initialize() {
		descriptorController.initialize();
		LOGGER.info("Loading event source instances");
		storage.getEventSourcesIds().stream().map(this::loadEventSourceInstance).forEach(this::unsetService);
	}

	private String createUniqueId(final EventSourceDescriptor descriptor) {
		final Set<String> existingIds = Collections
				.unmodifiableSet(getEventSourceInstances().stream().map(EventSourceInstance::getId).collect(toSet()));

		String id;
		do {
			id = descriptor.getServiceClass().map(c -> c.getSimpleName()).orElse(descriptor.getView())
					+ "__"
					+ UUID.randomUUID().toString().replace("-", "_");
		} while (existingIds.contains(id));
		return id;
	}

	private EventSourceInstance loadEventSourceInstance(@NotBlank final String id) {
		try {
			final EventSourceInstance instance = storage.loadEventSourceInstance(id, (c, v) -> {
				final EventSourceKey key
						= EventSourceKey.fromClassOrView(c, Strings.isNullOrEmpty(v) ? getView(id) : v);
				return descriptorController.getEventSourceDescriptor(key);
			});
			final Map<String, String> propertyStrings = storage.getEventSourceInstanceProperties(id);
			final Map<String, String> dataItemStrings = storage.getEventSourceInstanceDataItems(id);

			if (instance.getDescriptor().getServiceClass().isPresent()) {
				adoptDefaultEventSourceInstanceProperties(instance, instance.getDescriptor());
			}
			instance.getDescriptor()
					.getProperties()
					.stream()
					.filter(p -> propertyStrings.containsKey(p.getName()))
					.forEach(p -> instance.setPropertyString(p, propertyStrings.get(p.getName())));
			instance.getDescriptor()
					.getDataItems()
					.stream()
					.filter(p -> dataItemStrings.containsKey(p.getName()))
					.forEach(p -> instance.setPropertyString(p, dataItemStrings.get(p.getName())));

			return instance;
		} catch (final Exception e) {
			throw new RuntimeException("Unable to load event source instance: " + id, e);
		}
	}

	private static String getView(final String id) {
		return id.substring(0, id.indexOf(id.contains(":") ? ":" : "__"));
	}

	@SuppressWarnings("checkstyle:XIllegalCatchCustom")
	private void adoptDefaultEventSourceInstanceProperties(final EventSourceInstance instance,
			final EventSourceDescriptor descriptor) {
		// Create a new object of the source to retrieve the default values
		final Service service = createServiceInstance(
				descriptor.getServiceClass()
						.orElseThrow(() -> new NullPointerException("Service class must not be null")),
				descriptor.getModuleClass(),
				"dummy");
		for (final EventSourceProperty property : instance.getDescriptor().getProperties()) {
			try {
				final Object defaultValue = Objects.requireNonNull(property.getGetter().get().invoke(service),
						"The default value of a property must not be null.");
				instance.setProperty(property, defaultValue);
			} catch (final IllegalAccessException
					| IllegalArgumentException
					| InvocationTargetException
					| NullPointerException e) {
				throw new RuntimeException("Unable to set property: " + property.getName(), e);
			}
		}
	}

	private <T extends Service> T createServiceInstance(final Class<T> serviceClass,
			final Class<? extends Module> moduleClass,
			final String id) {
		final Injector childInjector = injector.createChildInjector(binder -> {
			binder.bind(String.class).annotatedWith(EventId.class).toInstance(id);
			binder.bind(serviceClass);
		}, createChildModule(moduleClass));
		return childInjector.getInstance(serviceClass);
	}

	private static Module createChildModule(final Class<? extends Module> moduleClass) {
		if (moduleClass == NOP.class) {
			return binder -> { /* empty by design */ };
		}
		return TypeUtils.createInstance(moduleClass);
	}

	public void startAllInstances() {
		getEventSourceInstances().stream()
				.filter(i -> i.getDescriptor().getServiceClass().isPresent())
				.filter(EventSourceInstance::isEnabled)
				.map(EventSourceInstance::getId)
				.forEach(id -> startInstance(id, false));
	}

	public void startInstance(final String id, final boolean automaticallyStarted) {
		final EventSourceInstance instance = getEventSourceInstance(id);

		if (!instance.isEnabled()) {
			throw new RuntimeException("Instance " + id + " is not enabled.");
		}

		synchronized (instance) {
			// check everything
			if (getService(instance).isPresent()) {
				throw new RuntimeException("Instance " + id + " is already started.");
			}
			if (!instance.getDescriptor().getServiceClass().isPresent()) {
				throw new RuntimeException("Instance " + id + " does not have a service class.");
			}
			if (!instance.isEnabled()) {
				throw new RuntimeException("Instance " + id + " is not enabled.");
			}

			final Optional<Service> service = createAndConfigureServiceInstance(instance);

			if (service.isPresent()) {
				// start service
				LOGGER.info("Starting event source: "
						+ id
						+ " ("
						+ instance.getName()
						+ "). Automatically: "
						+ automaticallyStarted);
				// TODO better error handling for IllegalStateExceptions
				service.get().startAsync();
				service.get().awaitRunning();
				setService(instance, service.get());
				service.get().addListener(new Listener() {
					@Override
					public void stopping(@SuppressWarnings("unused") @Nullable final State from) {
						if (service.get() instanceof ScheduledEventSource) {
							if (((ScheduledEventSource<?>) service.get()).isStoppedBecauseOfError()) {
								unsetService(instance);
							}
						}
					}
				}, MoreExecutors.directExecutor());
				statisticsManager.getEventSourceStatistics(id).setAutomaticallyStarted(automaticallyStarted);
				LOGGER.info("Started event source: " + id + " (" + instance.getName() + ")");
			} else {
				throw new RuntimeException("Service could not be started."); // TODO
			}
		}
	}

	public EventSourceInstance createEventSourceInstance(@NotNull final EventSourceDescriptor descriptor,
			final Principal createdBy) {
		final String id = createUniqueId(descriptor);
		final String name = nameGenerator.createEventSourceInstanceName(descriptor);
		final boolean enabled = false;
		final Duration frequency = Duration.ofMinutes(15);

		final EventSourceInstance instance = new EventSourceInstance(descriptor,
				id,
				name,
				enabled,
				frequency,
				createdBy.getUsername(),
				createdBy.getUsername());

		if (descriptor.getServiceClass().isPresent()) {
			adoptDefaultEventSourceInstanceProperties(instance, descriptor);
		}

		unsetService(instance);
		storage.persistEventSourceInstance(instance);

		return instance;
	}

	private Optional<Service> createAndConfigureServiceInstance(final EventSourceInstance instance) {
		// create class instance
		final Service service = createServiceInstance(instance.getDescriptor().getServiceClass().get(),
				instance.getDescriptor().getModuleClass(),
				instance.getId());
		if (service instanceof ScheduledEventSource) {
			((ScheduledEventSource<?>) service).setFrequency(instance.getFrequency());
		}

		// set property values
		instance//
				.getDescriptor()
				.getProperties()
				.stream()
				.filter(instance::hasPropertyValue)
				.filter(property -> property.getSetter().isPresent())
				.forEach(property -> {
					try {
						property.getSetter().get().invoke(service, instance.getPropertyValue(property));
					} catch (final IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
						throw new RuntimeException("Unable to set property: " + property.getName(), e);
					}
				});

		return Optional.of(service);
	}

	public void stopAllInstances() {
		getEventSourceInstances().stream()
				.filter(i -> i.getDescriptor().getServiceClass().isPresent())
				.filter(this::isRunning)
				.map(EventSourceInstance::getId)
				.forEach(this::stopInstance);
	}

	public void stopInstance(final String id) {
		final EventSourceInstance instance = getEventSourceInstance(id);

		synchronized (instance) {
			final Service service = getService(instance)
					.orElseThrow(() -> new RuntimeException("Instance " + id + " is not running"));

			// stop service
			if (service.state() != State.TERMINATED && service.state() != State.STOPPING) {
				LOGGER.info("Stopping event source: " + id + " (" + instance.getName() + ")");
				service.stopAsync();
			}
			if (service.state() != State.TERMINATED) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Awaiting termination: " + id + " (" + instance.getName() + ")");
				}
				service.awaitTerminated();
			}
			unsetService(instance);
			LOGGER.info("Stopped event source: " + id + " (" + instance.getName() + ")");
		}
	}

	public void restartInstance(final String id, final boolean wait) {
		final ForkJoinTask<?> task = ForkJoinPool.commonPool().submit(() -> {
			stopInstance(id);
			startInstance(id, false);
		});
		if (wait) {
			task.join();
		}
	}

	public void restartInstancesUsingSerializableProperty(final SerializableProperty changedProperty) {
		getEventSourceInstances().stream()
				.filter(this::isRunning)
				.filter(instance -> instance.getDescriptor()
						.getProperties()
						.stream()
						.filter(property -> property.getClazz().isPresent())
						.anyMatch(property -> property.getClazz().get() == changedProperty.getClass()
								&& ((SerializableProperty) instance.getPropertyValue(property))
										.getId() == changedProperty.getId()))
				.map(EventSourceInstance::getId)
				.forEach(id -> restartInstance(id, false));
	}

	public boolean isRunning(final EventSourceInstance instance) {
		return getService(instance).isPresent();
	}

	public void disableEventSourceInstance(final String id) {
		final EventSourceInstance instance = getEventSourceInstance(id);
		if (isRunning(instance)) {
			stopInstance(id);
		}
		instance.setEnabled(false);
		updateEventSourceInstance(instance);
	}

	public void enableEventSourceInstance(final String id) {
		final EventSourceInstance instance = getEventSourceInstance(id);
		instance.setEnabled(true);
		updateEventSourceInstance(instance);
	}

	void deleteEventSourceInstance(final String eventSourceId) {
		final EventSourceInstance instance = getEventSourceInstance(eventSourceId);

		if (isRunning(instance)) {
			stopInstance(eventSourceId);
		}

		storage.deleteEventSourceInstance(eventSourceId);
		removeEventSourceInstance(instance);
	}
}
