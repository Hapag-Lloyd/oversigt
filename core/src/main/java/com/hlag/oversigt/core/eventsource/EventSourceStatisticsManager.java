package com.hlag.oversigt.core.eventsource;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Throwables;
import com.google.inject.Singleton;
import com.hlag.oversigt.util.Utils;

@Singleton
public class EventSourceStatisticsManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(EventSourceStatisticsManager.class);

	private static final int MAX_RUN_HISTORY = 10;

	public StatisticsCollector createCollector(final String eventId) {
		return new StatisticsCollector(getEventSourceStatistics(eventId));
	}

	public EventSourceStatistics getEventSourceStatistics(final String eventId) {
		return idToStats.computeIfAbsent(eventId, x -> new EventSourceStatistics());
	}

	private final Map<String, EventSourceStatistics> idToStats = Collections.synchronizedMap(new HashMap<>());

	public EventSourceStatisticsManager() {
		// nothing to initialize
	}

	public static final class EventSourceStatistics {
		private Optional<RunStatistic> lastSuccessfulRun = Optional.empty();

		private Optional<RunStatistic> lastFailedRun = Optional.empty();

		private final List<RunStatistic> lastRuns = new ArrayList<>();

		private final AtomicBoolean automticallyStarted = new AtomicBoolean(false);

		private EventSourceStatistics() {
			// hide constructor and nothing to initialize
		}

		public boolean isAutomticallyStarted() {
			return automticallyStarted.get();
		}

		public void setAutomaticallyStarted(final boolean automaticallyStarted) {
			automticallyStarted.set(automaticallyStarted);
		}

		public synchronized Optional<RunStatistic> getLastFailedRun() {
			return lastFailedRun;
		}

		public synchronized List<RunStatistic> getLastRuns() {
			return new ArrayList<>(lastRuns);
		}

		public synchronized Optional<RunStatistic> getLastSuccessfulRun() {
			return lastSuccessfulRun;
		}

		public synchronized Optional<RunStatistic> getLastRun() {
			if (lastRuns.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(lastRuns.get(lastRuns.size() - 1));
		}

		public synchronized void addExecution(final RunStatistic statistics) {
			if (statistics.isSuccess()) {
				lastSuccessfulRun = Optional.of(statistics);
			} else {
				lastFailedRun = Optional.of(statistics);
			}

			lastRuns.add(statistics);
			while (lastRuns.size() > MAX_RUN_HISTORY) {
				lastRuns.remove(0);
			}
		}
	}

	public static final class RunStatistic {
		private final ZonedDateTime startTime;

		private final Duration duration;

		private final boolean success;

		private final boolean automaticallyStarted;

		private final Optional<String> message;

		private final Optional<Throwable> throwable;

		private final List<Action> actions;

		private RunStatistic(final ZonedDateTime startTime,
				final Duration duration,
				final boolean success,
				final boolean automaticallyStarted,
				final Optional<String> message,
				final Optional<Throwable> throwable,
				final List<Action> actions) {
			this.startTime = startTime;
			this.duration = duration;
			this.success = success;
			this.automaticallyStarted = automaticallyStarted;
			this.message = message;
			this.throwable = throwable;
			this.actions = Collections.unmodifiableList(actions);
		}

		public Duration getDuration() {
			return duration;
		}

		public Optional<String> getMessage() {
			return message;
		}

		public ZonedDateTime getStartTime() {
			return startTime;
		}

		@JsonIgnore
		public Optional<Throwable> getThrowable() {
			return throwable;
		}

		public Optional<String> getThrowableStackTrace() {
			return throwable.map(Throwables::getStackTraceAsString);
		}

		public boolean isSuccess() {
			return success;
		}

		public boolean isAutomaticallyStarted() {
			return automaticallyStarted;
		}

		public List<Action> getActions() {
			return actions;
		}

	}

	public static final class Action {
		private final String name;

		private final String detail;

		private final Duration duration;

		private Action(final String name, final String detail, final Duration duration) {
			this.name = name;
			this.detail = detail;
			this.duration = duration;
		}

		public Duration getDuration() {
			return duration;
		}

		public String getName() {
			return name;
		}

		public String getDetail() {
			return detail;
		}

		@Override
		public String toString() {
			return String.format("%s(%s): %s", name, detail, duration);
		}
	}

	public static final class StatisticsCollector {
		private final EventSourceStatistics eventSourceStatistics;

		private final boolean automaticallyStarted;

		private final ZonedDateTime startTime;

		private final List<Action> actions = new ArrayList<>();

		private StatisticsCollector(final EventSourceStatistics eventSourceStatistics) {
			this.eventSourceStatistics = eventSourceStatistics;
			automaticallyStarted = eventSourceStatistics.isAutomticallyStarted();
			startTime = ZonedDateTime.now();
		}

		public StartedAction startAction(final String name, final String detail) {
			return new StartedAction(name, detail);
		}

		public void addAction(final String name, final String detail, final Duration duration) {
			actions.add(new Action(name, detail, duration));
		}

		void success() {
			addRun(true, Optional.empty(), Optional.empty());
		}

		void failure(final String message) {
			addRun(false, Optional.of(message), Optional.empty());
		}

		void failure(final String message, final Throwable throwable) {
			addRun(false, Optional.of(message), Optional.of(throwable));
		}

		void failure(final String message, final Optional<Throwable> throwable) {
			addRun(false, Optional.of(message), throwable);
		}

		private void addRun(final boolean success,
				final Optional<String> message,
				final Optional<Throwable> throwable) {
			final Duration duration = Duration.between(startTime, ZonedDateTime.now());
			final RunStatistic stats
					= new RunStatistic(startTime, duration, success, automaticallyStarted, message, throwable, actions);
			eventSourceStatistics.addExecution(stats);
			LOGGER.info(String.format("Execution duration: %s %s",
					Utils.formatDuration(stats.getDuration()),
					stats.getActions()));
		}

		public final class StartedAction implements AutoCloseable {
			private final long actionStartTime;

			private final String name;

			private final String detail;

			private StartedAction(final String name, final String detail) {
				this.name = name;
				this.detail = detail;
				actionStartTime = System.currentTimeMillis();
			}

			public void done() {
				final long actionEndTime = System.currentTimeMillis();
				addAction(name, detail, Duration.ofMillis(actionEndTime - actionStartTime));
			}

			@Override
			public void close() {
				done();
			}
		}
	}
}
