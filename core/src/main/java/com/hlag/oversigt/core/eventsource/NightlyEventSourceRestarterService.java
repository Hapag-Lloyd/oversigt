package com.hlag.oversigt.core.eventsource;

import static com.hlag.oversigt.util.Utils.not;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.model.EventSourceInstance;

/**
 * Service restarting erroneous event sources once a day.
 *
 * @author neumaol
 */
@Singleton
public class NightlyEventSourceRestarterService extends AbstractScheduledService {
	@Inject
	private DashboardController dashboardController;

	public NightlyEventSourceRestarterService() {
		// no fields to be initialized manually, some will be injected
	}

	/**
	 * Executes one iteration of
	 * {@link com.google.common.util.concurrent.AbstractScheduledService} Sends
	 * event to event bus
	 */
	@Override
	protected final void runOneIteration() {
		dashboardController.getEventSourceInstances()
				.stream()
				.filter(not(dashboardController::isRunning))
				.filter(EventSourceInstance::isEnabled)
				.map(EventSourceInstance::getId)
				.forEach(id -> dashboardController.startInstance(id, true));
	}

	@Override
	protected final Scheduler scheduler() {
		final LocalDateTime tomorrowMidnight
				= LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1);
		final Duration durationUntilMidnight = Duration.between(LocalDateTime.now(), tomorrowMidnight).abs();
		return Scheduler.newFixedDelaySchedule(durationUntilMidnight.getSeconds(),
				Duration.ofDays(1).getSeconds(),
				TimeUnit.SECONDS);
	}

	@Override
	protected final String serviceName() {
		return getClass().getSimpleName();
	}
}
