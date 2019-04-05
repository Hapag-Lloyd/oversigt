package com.hlag.oversigt.connect.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hlag.oversigt.util.ThrowingFunction;

public abstract class DatabaseCache<T> {
	public static <X> DatabaseCache<X> createCache(final ThrowingFunction<Connection, Collection<X>> readFunction) {
		return new DatabaseCache<X>() {
			@Override
			protected Collection<X> readItems(final Connection connection) throws SQLException {
				return readFunction.apply(connection);
			}
		};
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseCache.class);

	protected DatabaseCache() {}

	private final AtomicReference<List<T>> items = new AtomicReference<>(new ArrayList<>());

	private final AtomicReference<LocalDateTime> lastAccess = new AtomicReference<>();

	private Duration durationBetweenReads = Duration.ofHours(1);

	public Duration getDurationBetweenReads() {
		return durationBetweenReads;
	}

	public void setDurationBetweenReads(final Duration durationBetweenReads) {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Set durationBetweenReads=" + durationBetweenReads);
		}
		this.durationBetweenReads = durationBetweenReads;
	}

	public Stream<T> stream() {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("stream called");
		}
		return getItems().stream();
	}

	public List<T> getItems() {
		final List<T> items = this.items.get();
		if (items.isEmpty()) {
			LOGGER.warn("Items have not been loaded yet.");
		}
		return items;
	}

	public List<T> getItems(final Connection connection) throws SQLException {
		if (shouldReadItems()) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Reading items");
			}
			final List<T> items = new ArrayList<>(readItems(connection));
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Found " + items.size() + " items");
			}
			this.items.set(items);
		}
		return items.get();
	}

	private synchronized boolean shouldReadItems() {
		final LocalDateTime now = LocalDateTime.now();
		final boolean yes = lastAccess.get() == null || lastAccess.get().plus(getDurationBetweenReads()).isBefore(now);
		if (yes) {
			lastAccess.set(now);
		}
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Should read items: " + yes);
		}
		return yes;
	}

	protected abstract Collection<T> readItems(Connection connection) throws SQLException;
}
