package com.hlag.oversigt.core.event;

import static com.hlag.oversigt.util.Utils.logDebug;
import static com.hlag.oversigt.util.Utils.logInfo;
import static com.hlag.oversigt.util.Utils.logWarn;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.EventSourceInstance;
import com.hlag.oversigt.model.Widget;
import com.hlag.oversigt.util.JsonUtils;

import de.larssh.utils.OptionalLongs;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.util.AttachmentKey;

@Singleton
public class EventSender {
	private static final Logger LOGGER = LoggerFactory.getLogger(EventSender.class);

	private static final AttachmentKey<RateLimiter> RATE_LIMITER_KEY = AttachmentKey.create(RateLimiter.class);

	public static final AttachmentKey<Dashboard> DASHBOARD_KEY = AttachmentKey.create(Dashboard.class);

	@Named("application-id")
	@Inject
	private String applicationId;

	private final OptionalLong rateLimit;

	// Send events in another thread
	private final BlockingQueue<EventSendTask> eventsToSend = new LinkedBlockingQueue<>();

	private final Thread thread;

	private final Map<String, OversigtEvent> cachedEvents = Collections.synchronizedMap(new HashMap<>());

	private final Map<ServerSentEventConnection, Map<String, LocalDateTime>> sentEventTimestamps
			= Collections.synchronizedMap(new WeakHashMap<>());

	private final JsonUtils json;

	private final Duration defaultEventLifetime;

	@Inject
	public EventSender(final JsonUtils json,
			@Named("discardEventsAfter") final Duration discardEventsAfter,
			@Named("rateLimit") @Nullable final Long rateLimit) {
		this.json = json;
		defaultEventLifetime = discardEventsAfter;
		this.rateLimit = rateLimit == null ? OptionalLong.empty() : OptionalLong.of(rateLimit);

		thread = new Thread(this::sendQueuedTasks, "EventSender");
		thread.setDaemon(true);
		thread.setPriority(Thread.NORM_PRIORITY + 1);
		thread.start();
	}

	@Subscribe
	void cacheEvent(final OversigtEvent event) {
		if (shouldCacheEvent(event)) {
			if (event.getLifetime() == null) {
				event.setLifetime(defaultEventLifetime);
			}
			logDebug(LOGGER, "Caching event [%s]. Lifetime [%s].", event.getId(), event.getLifetime());
			if (!(event instanceof ErrorEvent)) {
				cachedEvents.put(event.getId(), event);
			} else {
				synchronized (cachedEvents) {
					if (!cachedEvents.containsKey(event.getId())
							|| shouldRemoveEvent(cachedEvents.get(event.getId()))) {
						cachedEvents.put(event.getId(), event);
					}
				}
			}
		}
	}

	@Subscribe
	void newConnectionAdded(final ServerSentEventConnection connection) {
		OptionalLongs.mapToObj(rateLimit, RateLimiter::create)
				.ifPresent(rateLimiter -> connection.putAttachment(RATE_LIMITER_KEY, rateLimiter));
		logInfo(LOGGER,
				"Starting new SSE connection. Dashboard filter: '%s'. Rate limit: %s",
				Optional.ofNullable(connection.getAttachment(DASHBOARD_KEY)).map(Dashboard::getId).orElse("*"),
				rateLimit.orElse(-1L));
		synchronized (cachedEvents) {
			cachedEvents.values().removeIf(EventSender::shouldRemoveEvent);
			cachedEvents.values().forEach(event -> sendEventToConnection(event, connection));
		}
	}

	@Subscribe
	void removeEventWithId(final String id) {
		synchronized (cachedEvents) {
			final boolean deleted
					= cachedEvents.values().removeIf(event -> Objects.requireNonNull(event.getId()).equals(id));
			if (deleted) {
				logWarn(LOGGER, "Deleted cached events for ID [%s]", id);
			}
		}
	}

	private boolean shouldSendEventToConnection(final OversigtEvent event, final ServerSentEventConnection connection) {
		// always send uncached events
		if (!shouldCacheEvent(event)) {
			return true;
		}

		// filter for current dashboard
		if (!Optional.ofNullable(connection.getAttachment(DASHBOARD_KEY))
				.map(d -> doesDashboardContainEventId(d, Objects.requireNonNull(event.getId())))
				.orElse(true)) {
			return false;
		}

		// special handling for error events
		if (event instanceof ErrorEvent) {
			final OversigtEvent cachedEvent = cachedEvents.get(event.getId());
			return cachedEvent == null || cachedEvent instanceof ErrorEvent;
		}

		return true;
	}

	public void sendEventToConnection(final OversigtEvent event, final ServerSentEventConnection connection) {
		if (shouldSendEventToConnection(event, connection)) {
			eventsToSend.add(new EventSendTask(connection, event));
		}
	}

	private void sendQueuedTasks() {
		while (true) {
			try {
				final EventSendTask task = eventsToSend.take();
				processTask(task);
				moveTasksForConnectionToBack(task.connection);
			} catch (final InterruptedException e) {
				LOGGER.error("Waiting for new events has been interrupted.", e);
			}
		}
	}

	private void processTask(final EventSendTask task) {
		try {
			processTask(task.connection, task.event);
		} catch (final Exception e) {
			LOGGER.error("Unable to send event.", e);
		}
	}

	private void processTask(final ServerSentEventConnection connection, final OversigtEvent event) {
		final boolean isErrorEvent = event instanceof ErrorEvent;
		final Map<String, LocalDateTime> timestampsForConnection
				= sentEventTimestamps.computeIfAbsent(connection, c -> Collections.synchronizedMap(new HashMap<>()));
		final LocalDateTime lastEventTimestamp = timestampsForConnection.get(event.getId());
		if (isErrorEvent
				|| lastEventTimestamp == null
				|| lastEventTimestamp.isEqual(event.getCreatedOn())
				|| lastEventTimestamp.isBefore(event.getCreatedOn())) {
			// wait if there is rate limiter
			Optional.ofNullable(connection.getAttachment(RATE_LIMITER_KEY)).ifPresent(RateLimiter::acquire);

			// put application ID into event
			event.setApplicationId(applicationId);

			// Send event
			logDebug(LOGGER, "Sending event [%s]", event.getId());
			final String json = toJson(event);
			connection.send(json);

			// note when this connection got the last event of this ID
			if (isErrorEvent) {
				timestampsForConnection.put(event.getId(), event.getCreatedOn());
			}
		}
	}

	private void moveTasksForConnectionToBack(final ServerSentEventConnection connection) {
		synchronized (eventsToSend) {
			// find tasks with the same connection
			final List<EventSendTask> tasksWithSameConnection = new LinkedList<>();
			for (final EventSendTask task : eventsToSend) {
				if (task.connection == connection) {
					tasksWithSameConnection.add(task);
				}
			}

			// move tasks to end of list
			if (!tasksWithSameConnection.isEmpty()) {
				eventsToSend.removeAll(tasksWithSameConnection);
				eventsToSend.addAll(tasksWithSameConnection);
			}
		}
	}

	public Collection<OversigtEvent> getCachedEvents() {
		synchronized (cachedEvents) {
			return new ArrayList<>(cachedEvents.values());
		}
	}

	private String toJson(final OversigtEvent event) {
		if (event instanceof JsonEvent) {
			return ((JsonEvent) event).getJson();
		}
		return json.toJson(event);
	}

	private static boolean doesDashboardContainEventId(final Dashboard dashboard, final String eventId) {
		return dashboard.getWidgets()
				.stream()
				.map(Widget::getEventSourceInstance)
				.map(EventSourceInstance::getId)
				.anyMatch(eventId::equals);
	}

	private static boolean shouldRemoveEvent(final OversigtEvent event) {
		final boolean remove = !(event instanceof ErrorEvent) && !event.isValid();
		if (remove) {
			logWarn(LOGGER, "Deleting cached event [%s]. Event lifetime was [%s]", event.getId(), event.getLifetime());
		}
		return remove;
	}

	private static boolean shouldCacheEvent(final OversigtEvent event) {
		return !event.getClass().isAnnotationPresent(NoCache.class);
	}

	private static final class EventSendTask {
		private ServerSentEventConnection connection;

		private OversigtEvent event;

		private EventSendTask(final ServerSentEventConnection connection, final OversigtEvent event) {
			this.connection = connection;
			this.event = event;
		}
	}
}
