package com.hlag.oversigt.storage;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = "IMC_IMMATURE_CLASS_BAD_SERIALVERSIONUID", justification = "false positive")
public class DatabaseException extends RuntimeException {
	private static final long serialVersionUID = 3801630513244550824L;

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
