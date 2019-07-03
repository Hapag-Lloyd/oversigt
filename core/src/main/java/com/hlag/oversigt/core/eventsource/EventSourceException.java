package com.hlag.oversigt.core.eventsource;

import java.util.Optional;

public final class EventSourceException extends RuntimeException {
	private static final long serialVersionUID = -3175800380445076693L;

	EventSourceException(final String message, final Optional<Throwable> cause) {
		super(message, cause.orElse(null));
	}
}
