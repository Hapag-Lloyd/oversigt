package com.hlag.oversigt.core.eventsource;

import javax.inject.Inject;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.hlag.oversigt.core.event.OversigtEvent;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * @author Andrei Varabyeu
 */
abstract class SimpleEventSource<T extends OversigtEvent> extends AbstractExecutionThreadService {

	/**
	 * EventBus to send events
	 */
	@Inject
	private EventBus eventBus;

	/**
	 * ID of event this event source bound to
	 */
	@EventId
	@Inject
	private String eventId;

	protected final void sendEvent(/* TODO remove nullable */@Nullable final T event) {
		if (event != null) {
			event.setId(eventId);
			this.eventBus.post(event);
		}
	}

	@Override
	protected void startUp() throws Exception {
		/* no any lifecycle-related logic */
	}

	@Override
	protected void shutDown() throws Exception {
		/* no any lifecycle-related logic */
	}

	@Override
	protected String serviceName() {
		return "SimpleEventSource[eventID=" + eventId + "]";
	}
}
