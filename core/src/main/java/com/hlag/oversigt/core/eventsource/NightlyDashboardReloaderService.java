package com.hlag.oversigt.core.eventsource;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.sources.event.ReloadEvent;

/**
 * Scheduled EventSource - produces events with specified time period.
 * Basically, based on Guava's
 * {@link com.google.common.util.concurrent.AbstractScheduledService}
 *
 * @author neumaol
 */
@Singleton
public class NightlyDashboardReloaderService extends NightlyService {
	private static final Logger LOGGER = LoggerFactory.getLogger(NightlyDashboardReloaderService.class);

	public static String getEventId() {
		return "reload";
	}

	/**
	 * EventBus to send events
	 */
	@Inject
	private EventBus eventBus;

	@Inject
	private DashboardController dashboardController;

	public NightlyDashboardReloaderService() {
		// no fields to be initialized manually, some will be injected
	}

	/**
	 * Executes one iteration of
	 * {@link com.google.common.util.concurrent.AbstractScheduledService} Sends
	 * event to event bus
	 */
	@Override
	protected final void runOneIteration() {
		final Collection<String> dashboards = dashboardController.getDashboardIds();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Sending reload event to " + dashboards);
		}
		sendEvent(new ReloadEvent(dashboards));
	}

	private void sendEvent(final OversigtEvent event) {
		event.setId(getEventId());
		eventBus.post(event);
	}

	@Override
	protected final String serviceName() {
		return getClass().getSimpleName() + "[eventID=" + getEventId() + "]";
	}
}
