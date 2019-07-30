package com.hlag.oversigt.core.configuration;

import java.util.UUID;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.hlag.oversigt.controller.DashboardController;
import com.hlag.oversigt.controller.EventSourceDescriptorController;
import com.hlag.oversigt.controller.EventSourceInstanceController;
import com.hlag.oversigt.controller.EventSourceNameGenerator;
import com.hlag.oversigt.core.HttpHandlers;
import com.hlag.oversigt.core.Oversigt;
import com.hlag.oversigt.core.OversigtServer;
import com.hlag.oversigt.core.event.EventSender;
import com.hlag.oversigt.core.eventsource.EventSourceStatisticsManager;
import com.hlag.oversigt.core.eventsource.NightlyDashboardReloaderService;
import com.hlag.oversigt.core.eventsource.NightlyEventSourceRestarterService;
import com.hlag.oversigt.properties.SerializablePropertyController;
import com.hlag.oversigt.security.RoleProvider;
import com.hlag.oversigt.storage.JdbcDatabase;
import com.hlag.oversigt.storage.Storage;
import com.hlag.oversigt.util.MailSender;

/**
 * Main application configuration module. Configures server and all necessary
 * stuff
 *
 * @author avarabyeu
 * @author noxfireone
 */
public class OversigtModule extends AbstractModule {

	/**
	 * Create a module to configure Guice for Oversigt creation
	 */
	public OversigtModule() {
		// nothing to do
	}

	/** {@inheritDoc} */
	@Override
	protected void configure() {
		// some interesting values
		binder().bind(String.class)
				.annotatedWith(Names.named("application-id"))
				.toInstance(UUID.randomUUID().toString());

		// Add default constructors for explicit bindings
		binder().bind(OversigtServer.class);
		binder().bind(HttpHandlers.class);
		binder().bind(EventSourceStatisticsManager.class);
		binder().bind(EventSender.class);
		binder().bind(RoleProvider.class);
		binder().bind(MailSender.class);

		// model
		binder().bind(EventSourceDescriptorController.class);
		binder().bind(EventSourceInstanceController.class);
		binder().bind(DashboardController.class);
		binder().bind(EventSourceNameGenerator.class);
		binder().bind(SerializablePropertyController.class);

		// database
		binder().bind(Storage.class).to(JdbcDatabase.class);

		// Bind needed variables
		binder().bind(Service.class)
				.annotatedWith(Names.named("NightlyDashboardReloader"))
				.to(NightlyDashboardReloaderService.class);
		binder().bind(Service.class)
				.annotatedWith(Names.named("NightlyEventSourceRestarter"))
				.to(NightlyEventSourceRestarterService.class);
	}

	/**
	 * Create and configure the event bus used to pass events within this
	 * application
	 *
	 * @param sender the sender used to send events to the clients
	 * @return the event bus
	 */
	@Singleton
	@Provides
	@Inject
	EventBus provideEventBus(final EventSender sender) {
		final EventBus eventBus = new EventBus(Oversigt::handleEventBusException);
		eventBus.register(sender);
		return eventBus;
	}

}
