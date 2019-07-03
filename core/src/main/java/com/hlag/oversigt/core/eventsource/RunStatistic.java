package com.hlag.oversigt.core.eventsource;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RunStatistic {
	public static StatisticsCollector createCollector() {
		return new StatisticsCollector();
	}

	private final ZonedDateTime startTime;

	private final Duration duration;

	private final boolean success;

	private final Optional<String> message;

	private final Optional<Throwable> throwable;

	private final List<Action> actions;

	private RunStatistic(final ZonedDateTime startTime,
			final Duration duration,
			final boolean success,
			final Optional<String> message,
			final Optional<Throwable> throwable,
			final List<Action> actions) {
		this.startTime = startTime;
		this.duration = duration;
		this.success = success;
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

	public Optional<Throwable> getThrowable() {
		return throwable;
	}

	public boolean isSuccess() {
		return success;
	}

	public List<Action> getActions() {
		return actions;
	}

	public static final class Action {
		private final String name;

		private final Duration duration;

		private Action(final String name, final Duration duration) {
			this.name = name;
			this.duration = duration;
		}

		public Duration getDuration() {
			return duration;
		}

		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			return String.format("Action[%s: %s]", name, duration);
		}

	}

	public static final class StatisticsCollector {
		private final ZonedDateTime startTime;

		private final List<Action> actions = new ArrayList<>();

		private StatisticsCollector() {
			startTime = ZonedDateTime.now();
		}

		public void addAction(final String name, final Duration duration) {
			actions.add(new Action(name, duration));
		}

		RunStatistic success() {
			return build(true, Optional.empty(), Optional.empty());
		}

		RunStatistic failure(final String message) {
			return build(false, Optional.of(message), Optional.empty());
		}

		RunStatistic failure(final String message, final Throwable throwable) {
			return build(false, Optional.of(message), Optional.of(throwable));
		}

		RunStatistic failure(final String message, final Optional<Throwable> throwable) {
			return build(false, Optional.of(message), throwable);
		}

		private RunStatistic build(final boolean success,
				final Optional<String> message,
				final Optional<Throwable> throwable) {
			final Duration duration = Duration.between(startTime, ZonedDateTime.now());
			return new RunStatistic(startTime, duration, success, message, throwable, actions);
		}
	}
}
