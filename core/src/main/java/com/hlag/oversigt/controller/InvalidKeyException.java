package com.hlag.oversigt.controller;

public class InvalidKeyException extends RuntimeException {
	InvalidKeyException(final String message) {
		super(message);
	}

	InvalidKeyException(final String message, final Exception cause) {
		super(message, cause);
	}
}
