package com.hlag.oversigt.core.event;

import com.google.common.base.Throwables;

import edu.umd.cs.findbugs.annotations.Nullable;

public class ErrorEvent extends OversigtEvent {
	@Nullable
	private final String errorMessage;

	private final boolean error = true;

	public ErrorEvent(final Throwable t) {
		this(Throwables.getStackTraceAsString(t));
	}

	public ErrorEvent(@Nullable final String errorMessage) {
		this.errorMessage = errorMessage;
	}

	@Nullable
	public String getErrorMessage() {
		return errorMessage;
	}

	public boolean isError() {
		return error;
	}
}
