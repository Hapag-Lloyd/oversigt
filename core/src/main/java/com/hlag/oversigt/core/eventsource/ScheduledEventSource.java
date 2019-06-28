package com.hlag.oversigt.core.eventsource;

import static com.hlag.oversigt.util.Utils.logDebug;
import static com.hlag.oversigt.util.Utils.logError;
import static com.hlag.oversigt.util.Utils.logTrace;
import static com.hlag.oversigt.util.Utils.logWarn;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.inject.Inject;
import com.hlag.oversigt.core.event.ErrorEvent;
import com.hlag.oversigt.core.event.OversigtEvent;

/**
 * Scheduled EventSource - produces events with specified time period.
 * Basically, based on Guava's
 * {@link com.google.common.util.concurrent.AbstractScheduledService}
 *
 * @author avarabyeu
 */
public abstract class ScheduledEventSource<T extends OversigtEvent> extends AbstractScheduledService {
	private static final int ALLOWED_NUMBER_OF_FAILED_CALLS = 5;

	private static final Map<String, Logger> LOGGERS = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Time period between sending events
	 */
	private Duration frequency = Duration.ofMinutes(1);

	/**
	 * ID of event this event source bound to
	 */
	@Inject
	@EventId
	private String eventId;

	/**
	 * EventBus to send events
	 */
	@Inject
	private EventBus eventBus;

	private AtomicBoolean immediateExecution = new AtomicBoolean(false);

	private ZonedDateTime lastRun = null;

	private ZonedDateTime lastSuccessfulRun = null;

	private final AtomicInteger numberOfFailedRuns = new AtomicInteger(0);

	private ZonedDateTime lastFailureDateTime = null;

	private String lastFailureDescription = null;

	private Throwable lastFailureException = null;

	protected String getEventId() {
		return eventId;
	}

	/**
	 * Executes one iteration of
	 * {@link com.google.common.util.concurrent.AbstractScheduledService} Sends
	 * event to event bus
	 */
	@Override
	@SuppressWarnings("checkstyle:XIllegalCatchDefault")
	protected final void runOneIteration() {
		logTrace(getLogger(), "Run one iteration");
		// if the last iteration (if any) is too short in the past, return immediately
		// so no action will be done.
		if (!isTimeToRunNextIteration()) {
			logTrace(getLogger(), "Not the time to run one iteration");
			return;
		}

		try {
			logTrace(getLogger(), "Starting to produce event");
			if (sendEvent(produceEvent())) {
				resetFailure();
			} else {
				sendEvent(new ErrorEvent(getLastFailureDescription()));
			}
			logTrace(getLogger(), "Done producing event");
			setLastRunNow(true);
		} catch (final Throwable e) {
			sendEvent(new ErrorEvent(e));
			logError(getLogger(), "Cannot produce event with id %s. Deleting last event.", eventId);
			failure("Event source threw an exception", e);
			setLastRunNow(false);
			removeLastEvent();
			if (numberOfFailedRuns.incrementAndGet() > ALLOWED_NUMBER_OF_FAILED_CALLS) {
				logWarn(getLogger(),
						"Running the event source resulted in %s errors in a row. Maximum allowed errors in a row is %s. Stopping service.",
						numberOfFailedRuns.get(),
						ALLOWED_NUMBER_OF_FAILED_CALLS);
				stopAsync();
			} else if (!(e instanceof Exception)) /* Throwable is some kind of Error or similar - stop immediately */ {
				logError(getLogger(), "Error occurred. Stopping service immediately.");
				stopAsync();
			}
		}
	}

	protected final boolean sendEvent(final OversigtEvent event) {
		if (event != null) {
			event.setId(eventId);
			try {
				event.setLifetime(getEventLifetime());
			} catch (final Exception e) {
				logWarn(getLogger(), "Unable to compute event life time", e);
			}
			this.eventBus.post(event);
			return true;
		}
		return false;
	}

	protected final void removeLastEvent() {
		this.eventBus.post(eventId);
	}

	@Override
	protected final Scheduler scheduler() {
		return Scheduler.newFixedDelaySchedule(1, 1, TimeUnit.SECONDS);
	}

	@Override
	protected final String serviceName() {
		return getClass().getSimpleName() + "[eventID=" + eventId + "]";
	}

	protected abstract T produceEvent() throws Exception;

	protected final Logger getLogger() {
		return LOGGERS.computeIfAbsent(getClass().getName(), LoggerFactory::getLogger);
	}

	protected Logger getSpecialLogger() {
		return LOGGERS.computeIfAbsent("special." + eventId, LoggerFactory::getLogger);
	}

	protected Duration getEventLifetime() {
		return getFrequency().multipliedBy(3);
	}

	public final Duration getFrequency() {
		return frequency;
	}

	public final void setFrequency(final Duration frequency) {
		this.frequency = frequency;
		scheduleImmediateExecution();
	}

	/**
	 * Clears the timestamp of the last iteration of this service resulting in an
	 * immediate execution of this event source within the next second.
	 */
	public final void scheduleImmediateExecution() {
		immediateExecution.set(true);
	}

	private void setLastRunNow(final boolean success) {
		setLastRun(success, ZonedDateTime.now());
	}

	private synchronized void setLastRun(final boolean success, final ZonedDateTime lastRun) {
		logDebug(getLogger(), "Setting last run timestamp to %s", lastRun);
		this.lastRun = lastRun;
		if (success) {
			logDebug(getLogger(), "Setting last successful run timestamp, too");
			lastSuccessfulRun = ZonedDateTime.from(lastRun);
			numberOfFailedRuns.set(0);
		}
	}

	private synchronized boolean isTimeToRunNextIteration() {
		logTrace(getLogger(),
				"immediateExexcution=%s ; lastRun=%s ; frequency=%s ; now()=%s ; duration.between=%s ; diff=%s",
				immediateExecution,
				lastRun,
				frequency,
				(Supplier<ZonedDateTime>) ZonedDateTime::now,
				(Supplier<Duration>) () -> Duration.between(lastRun, ZonedDateTime.now()),
				(Supplier<Duration>) () -> frequency.minus(Duration.between(lastRun, ZonedDateTime.now())));
		return immediateExecution.getAndSet(false)
				|| lastRun == null
				|| frequency.minus(Duration.between(lastRun, ZonedDateTime.now())).isNegative();
		// || Duration.between(lastRun, LocalDateTime.now()).compareTo(frequency) > 1;
	}

	public synchronized ZonedDateTime getLastRun() {
		return lastRun;
	}

	public synchronized ZonedDateTime getLastSuccessfulRun() {
		return lastSuccessfulRun;
	}

	private synchronized void resetFailure() {
		this.lastFailureDateTime = null;
		this.lastFailureDescription = null;
		this.lastFailureException = null;
	}

	protected synchronized <X> X failure(final String message) {
		return failure(message, null);
	}

	protected synchronized <X> X failure(final String message, final Throwable exception) {
		getLogger().error(message, exception);
		this.lastFailureDateTime = ZonedDateTime.now();
		this.lastFailureDescription = message;
		this.lastFailureException = exception;
		return null;
	}

	public synchronized ZonedDateTime getLastFailureDateTime() {
		return lastFailureDateTime;
	}

	public synchronized String getLastFailureDescription() {
		return lastFailureDescription;
	}

	public synchronized String getLastFailureException() {
		return lastFailureException != null ? Throwables.getStackTraceAsString(lastFailureException) : null;
	}
}
