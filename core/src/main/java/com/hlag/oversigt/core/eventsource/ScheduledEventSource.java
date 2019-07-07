package com.hlag.oversigt.core.eventsource;

import static com.hlag.oversigt.util.Utils.logError;
import static com.hlag.oversigt.util.Utils.logTrace;
import static com.hlag.oversigt.util.Utils.logWarn;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.inject.Inject;
import com.hlag.oversigt.core.event.ErrorEvent;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.RunStatistic.StatisticsCollector;
import com.hlag.oversigt.util.Utils;

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

	private final List<RunStatistic> statistics = new ArrayList<>();

	private Optional<RunStatistic> lastSuccessfulRun = Optional.empty();

	private Optional<RunStatistic> lastFailedRun = Optional.empty();

	private final AtomicInteger numberOfFailedRuns = new AtomicInteger(0);

	protected String getEventId() {
		return eventId;
	}

	private Optional<StatisticsCollector> collector = Optional.empty();

	protected StatisticsCollector getStatisticsCollector() {
		return collector.orElseThrow(() -> new RuntimeException("The statistics collector has not been created."));
	}

	private synchronized void addStatistics(final RunStatistic runStatistic) {
		getLogger().info(String.format("Execution duration: %s %s",
				Utils.formatDuration(runStatistic.getDuration()),
				runStatistic.getActions()));
		// save newest entry
		statistics.add(runStatistic);
		// remove entries if too many are in the list
		while (statistics.size() > ALLOWED_NUMBER_OF_FAILED_CALLS + 1) {
			statistics.remove(statistics.size() - 1);
		}
		// handle special cases
		if (runStatistic.isSuccess()) {
			lastSuccessfulRun = Optional.of(runStatistic);
			numberOfFailedRuns.set(0);
		} else {
			lastFailedRun = Optional.of(runStatistic);
			numberOfFailedRuns.incrementAndGet();
		}
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

		try {
			logTrace(getLogger(), "Starting to produce event");
			collector = Optional.of(RunStatistic.createCollector());
			final Optional<T> event = produceEvent();
			if (sendEvent(event)) {
				addStatistics(getStatisticsCollector().success());
			} else {
				addStatistics(getStatisticsCollector()
						.failure(getLastFailureDescription().orElse("No error message supplied.")));
				sendEvent(Optional.of(new ErrorEvent(getLastFailureDescription())));
			}
			logTrace(getLogger(), "Done producing event");
		} catch (final Throwable e) {
			addStatistics(getStatisticsCollector().failure(Optional.ofNullable(e.getMessage()).orElse("No message"),
					Optional.ofNullable(e.getCause())));
			sendEvent(Optional.of(new ErrorEvent(e)));

			getLogger().error(String.format("Cannot produce event with id %s. Deleting last event.", eventId), e);
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

	protected abstract Optional<T> produceEvent() throws Exception;

	protected final boolean sendEvent(final Optional<? extends OversigtEvent> event) {
		if (event.isPresent()) {
			event.get().setId(eventId);
			try {
				event.get().setLifetime(getEventLifetime());
			} catch (final Exception e) {
				logWarn(getLogger(), "Unable to compute event life time", e);
			}
			this.eventBus.post(event.get());
			return true;
		}
		return false;
	}

	protected final void removeLastEvent() {
		this.eventBus.post(eventId);
	}

	@Override
	protected final Scheduler scheduler() {
		return Scheduler.newFixedDelaySchedule(1, getFrequency().toMillis(), TimeUnit.MILLISECONDS);
	}

	@Override
	protected final String serviceName() {
		return getClass().getSimpleName() + "[eventID=" + eventId + "]";
	}

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
	}

	public synchronized Optional<RunStatistic> getLastRun() {
		return statistics.stream().findFirst();
	}

	public synchronized Optional<RunStatistic> getLastSuccessfulRun() {
		return lastSuccessfulRun;
	}

	public synchronized List<RunStatistic> getRunStatistics() {
		return statistics;
	}

	protected synchronized <X> X failure(final String message) {
		return failure(message, Optional.empty());
	}

	protected synchronized <X> X failure(final String message, final Throwable exception) {
		return failure(message, Optional.of(exception));
	}

	protected synchronized <X> X failure(final String message, final Optional<Throwable> exception) {
		getLogger().error(message, exception.orElse(null));
		throw new EventSourceException(message, exception);
	}

	public synchronized Optional<String> getLastFailureDescription() {
		return lastFailedRun.flatMap(RunStatistic::getMessage);
	}

	public synchronized Optional<String> getLastFailureException() {
		return lastFailedRun.flatMap(RunStatistic::getThrowable).map(Throwables::getStackTraceAsString);
	}
}
