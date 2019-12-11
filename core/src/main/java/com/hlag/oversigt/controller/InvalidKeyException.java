package com.hlag.oversigt.controller;

public class InvalidKeyException extends RuntimeException {
	private static final long serialVersionUID = -2378306089298534601L;

	InvalidKeyException(final String message) {
		super(message);
	}

	InvalidKeyException(final String message, final Exception cause) {
		super(message, cause);
	}
}
