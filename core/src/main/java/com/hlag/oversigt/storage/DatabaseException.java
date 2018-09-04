package com.hlag.oversigt.storage;

public class DatabaseException extends RuntimeException {
	private static final long serialVersionUID = -5096449312578429802L;

	private final String sql;

	@Deprecated
	DatabaseException(String message, Throwable cause) {
		this(message, (String) null, cause);
	}

	@Deprecated
	DatabaseException(String message) {
		this(message, (String) null);
	}

	DatabaseException(String message, String sql, Throwable cause) {
		super(message, cause);
		this.sql = sql;
	}

	DatabaseException(String message, String sql) {
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
