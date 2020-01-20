package com.hlag.oversigt.core.eventsource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.hlag.oversigt.core.event.ErrorEvent;
import com.hlag.oversigt.core.event.OversigtEvent;

import edu.umd.cs.findbugs.annotations.Nullable;

public final class EventSourceTestHelper {
	private static EventBus eventBus = new EventBus();

	public static <E extends ScheduledEventSource<?>> E createScheduledEventSource(final Class<E> clazz,
			final Module... modules) {
		final List<Module> modulesForInjector = new ArrayList<>();
		modulesForInjector.add(new EventSourceTestModule(clazz));
		modulesForInjector.addAll(Arrays.asList(modules));
		return Guice.createInjector(modulesForInjector).getInstance(clazz);
	}

	public static Optional<OversigtEvent> runEventSource(final ScheduledEventSource<?> eventSource) {
		final EventReceiver eventReceiver = new EventReceiver();
		try {
			eventBus.register(eventReceiver);
			eventSource.runOneIteration();
		} finally {
			eventBus.unregister(eventReceiver);
		}
		return eventReceiver.getEvent();
	}

	private EventSourceTestHelper() {
		// nothing to do here
	}

	public static boolean isErrorEvent(final OversigtEvent event) {
		return event instanceof ErrorEvent;
	}

	public static Class<? extends OversigtEvent> getErrorEventClass() {
		return ErrorEvent.class;
	}

	private static final class EventReceiver implements Consumer<OversigtEvent> {
		private Optional<OversigtEvent> event = Optional.empty();

		@Override
		@Subscribe
		public void accept(@Nullable final OversigtEvent event) {
			this.event = Optional.ofNullable(event);
		}

		public Optional<OversigtEvent> getEvent() {
			return event;
		}
	}

	private static final class EventSourceTestModule extends AbstractModule {
		private String id = UUID.randomUUID().toString();

		private final Class<?> serviceClass;

		private EventSourceTestModule(final Class<?> serviceClass) {
			this.serviceClass = serviceClass;
		}

		@Override
		protected void configure() {
			binder().requireExplicitBindings();
			bind(String.class).annotatedWith(EventId.class).toInstance(id);
			bind(serviceClass);
			bind(EventBus.class).toInstance(eventBus);
			bind(EventSourceStatisticsManager.class);
		}
	}
}
