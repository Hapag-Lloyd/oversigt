package com.hlag.oversigt.storage;

public class DatabaseException extends RuntimeException {
	private static final long serialVersionUID = -5096449312578429802L;

	private final String sql;

	@Deprecated
	DatabaseException(final String message, final Throwable cause) {
		this(message, (String) null, cause);
	}

	@Deprecated
	DatabaseException(final String message) {
		this(message, (String) null);
	}

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
