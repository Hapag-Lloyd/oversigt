package com.hlag.oversigt.storage;

import java.util.Collection;
import java.util.Objects;

public interface SqlDialect {

	String getDriverClassName();

	String getJdbcConnectionUrl(String location, String schemaName, String username, String password);

	String createTable(String tableName, ColumnOptions... columns);

	String alterTableAddColumn(String tableName, ColumnOptions option);

	String alterTableDropColumn(String tableName, String columnName);

	default String select(String tableName, Collection<String> select) {
		return select(tableName, null, select);
	}

	default String select(String tableName, Collection<String> select, Collection<String> where) {
		return select(tableName, select, where, null, false, 0L);
	}

	default String select(String tableName, Collection<String> select, String columnIn, long inValues) {
		return select(tableName, select, null, columnIn, false, inValues);
	}

	String select(String tableName,
			Collection<String> select,
			Collection<String> where,
			String columnIn,
			boolean notIn,
			long inValues);

	String selectithOneLike(String tableName,
			Collection<String> select,
			Collection<String> columnsToCheck,
			String columnWithLike);

	String insert(String tableName, Collection<String> columns);

	String update(String tableName, Collection<String> valueNames, Collection<String> whereNames);

	String delete(String tableName, Collection<String> whereNames);

	String delete(String tableName, Collection<String> where, String columnIn, boolean notIn, long inValues);

	Object convertValue(Object object);

	public static enum ColumnType {
		Text,
		Integer,
		BigInteger,
		Float,
		Boolean,
		Timestamp,
		Date,
		Time
	}

	public static class ColumnOptions {

		final String name;
		final ColumnType type;
		final boolean nullable;
		final Object defaultValue;
		final boolean primaryKey;
		final boolean autoincrement;
		final boolean unique;
		final Integer length;
		final Integer precision;

		public ColumnOptions(String name, ColumnType type, Object defaultValue, boolean nullable, boolean primaryKey) {
			this(name, type, defaultValue, nullable, primaryKey, false, false);
		}

		public ColumnOptions(String name,
				ColumnType type,
				Object defaultValue,
				boolean nullable,
				boolean primaryKey,
				boolean autoincrement,
				boolean unique) {
			this.name = Objects.requireNonNull(name);
			this.type = Objects.requireNonNull(type);
			this.defaultValue = defaultValue;
			this.nullable = nullable;
			this.primaryKey = primaryKey;
			this.autoincrement = autoincrement;
			this.length = null;
			this.precision = null;
			this.unique = unique;
		}

		public String getName() {
			return name;
		}
	}
}
