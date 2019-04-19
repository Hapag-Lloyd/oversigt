package com.hlag.oversigt.core.eventsource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.AbstractScheduledService;
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
public class NightlyReloaderService extends AbstractScheduledService {
	private static final Logger LOGGER = LoggerFactory.getLogger(NightlyReloaderService.class);

	/**
	 * EventBus to send events
	 */
	@Inject
	private EventBus eventBus;

	@Inject
	private DashboardController dashboardController;

	public static String getEventId() {
		return "reload";
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

	protected final void sendEvent(final OversigtEvent event) {
		if (null != event) {
			event.setId(getEventId());
			eventBus.post(event);
		}
	}

	@Override
	protected final Scheduler scheduler() {
		final LocalDateTime tomorrowMidnight
				= LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1);
		final Duration durationUntilMidnight = Duration.between(LocalDateTime.now(), tomorrowMidnight).abs();
		return Scheduler.newFixedDelaySchedule(//
				durationUntilMidnight.getSeconds(), //
				Duration.ofDays(1).getSeconds(), //
				TimeUnit.SECONDS);
	}

	@Override
	protected final String serviceName() {
		return getClass().getSimpleName() + "[eventID=" + getEventId() + "]";
	}
}
