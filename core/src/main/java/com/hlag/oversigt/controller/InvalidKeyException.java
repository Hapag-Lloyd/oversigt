package com.hlag.oversigt.controller;

public class InvalidKeyException extends RuntimeException {
	private static final long serialVersionUID = 5201395112005052141L;

	InvalidKeyException(final String message) {
		super(message);
	}

	InvalidKeyException(final String message, final Exception cause) {
		super(message, cause);
	}
}
