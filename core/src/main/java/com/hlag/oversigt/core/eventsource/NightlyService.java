package com.hlag.oversigt.core.eventsource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.AbstractScheduledService;

/**
 * Scheduled EventSource - produces events with specified time period.
 * Basically, based on Guava's
 * {@link com.google.common.util.concurrent.AbstractScheduledService}
 *
 * @author neumaol
 */
public abstract class NightlyService extends AbstractScheduledService {
	protected NightlyService() {
		// no fields to be initialized manually, some will be injected
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
}
