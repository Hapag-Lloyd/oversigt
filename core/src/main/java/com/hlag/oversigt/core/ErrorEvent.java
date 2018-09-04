package com.hlag.oversigt.core;

import com.google.common.base.Throwables;

public class ErrorEvent extends OversigtEvent {
	private final String errorMessage;
	private final boolean error = true;

	public ErrorEvent(Throwable t) {
		this(Throwables.getStackTraceAsString(t));
	}

	public ErrorEvent(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public boolean isError() {
		return error;
	}
}
