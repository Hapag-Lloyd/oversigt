package com.hlag.oversigt.storage;

public class DatabaseException extends RuntimeException {
	private final String sql;

	DatabaseException(final String message, final String sql, final Throwable cause) {
		super(message, cause);
		this.sql = sql;
	}

	DatabaseException(final String message, final String sql) {
		super(message);
		this.sql = sql;
	}

	public String getSql() {
		return sql;
	}

	@Override
	public String getMessage() {
		return super.getMessage() + System.lineSeparator() + getSql();
	}

	@Override
	public String getLocalizedMessage() {
		return super.getLocalizedMessage() + System.lineSeparator() + getSql();
	}
}
