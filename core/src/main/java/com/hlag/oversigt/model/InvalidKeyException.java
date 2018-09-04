package com.hlag.oversigt.model;

public class InvalidKeyException extends RuntimeException {
	private static final long serialVersionUID = -2378306089298534601L;

	InvalidKeyException(String message) {
		super(message);
	}

	InvalidKeyException(String message, Exception cause) {
		super(message, cause);
	}
}
