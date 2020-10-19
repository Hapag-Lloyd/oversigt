package com.hlag.oversigt.core.eventsource;

/**
 * Excetion used to abort event source excecution due to some error.
 *
 * @author neumaol
 *
 */
public final class EventSourceException extends Exception {
	public EventSourceException(final String message) {
		super(message);
	}

	public EventSourceException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
