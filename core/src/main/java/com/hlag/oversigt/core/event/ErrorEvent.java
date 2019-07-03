package com.hlag.oversigt.core.event;

import java.util.Optional;

import com.google.common.base.Throwables;
import com.hlag.oversigt.core.eventsource.EventSourceException;

public class ErrorEvent extends OversigtEvent {
	private final Optional<String> errorMessage;

	private final boolean error = true;

	public ErrorEvent(final Throwable t) {
		if (t instanceof EventSourceException) {
			errorMessage = Optional.of(t.getMessage()); // the EventSourceException always has a message!
		} else {
			errorMessage = Optional.of(Throwables.getStackTraceAsString(t));
		}
	}

	public ErrorEvent(final Optional<String> errorMessage) {
		this.errorMessage = errorMessage;
	}

	public Optional<String> getErrorMessage() {
		return errorMessage;
	}

	public boolean isError() {
		return error;
	}
}
