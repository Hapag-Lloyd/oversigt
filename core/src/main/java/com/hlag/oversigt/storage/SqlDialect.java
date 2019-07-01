package com.hlag.oversigt.storage;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import edu.umd.cs.findbugs.annotations.Nullable;

public interface SqlDialect {

	String getDriverClassName();

	String getJdbcConnectionUrl(String location, String schemaName, String username, String password);

	String createTable(String tableName, ColumnOptions... columns);

	String alterTableAddColumn(String tableName, ColumnOptions option);

	String alterTableDropColumn(String tableName, String columnName);

	default String select(final String tableName, final Collection<String> where) {
		return select(tableName, Collections.emptyList(), where);
	}

	default String select(final String tableName, final Collection<String> select, final Collection<String> where) {
		return select(tableName, select, where, Optional.empty(), false, 0L);
	}

	default String select(final String tableName,
			final Collection<String> select,
			final Optional<String> columnIn,
			final long inValues) {
		return select(tableName, select, Collections.emptyList(), columnIn, false, inValues);
	}

	String select(String tableName,
			Collection<String> select,
			Collection<String> where,
			Optional<String> columnIn,
			boolean notIn,
			long inValues);

	String selectWithOneLike(String tableName,
			Collection<String> select,
			Collection<String> columnsToCheck,
			String columnWithLike);

	String insert(String tableName, Collection<String> columns);

	String update(String tableName, Collection<String> valueNames, Collection<String> whereNames);

	String delete(String tableName, Collection<String> whereNames);

	String delete(String tableName, Collection<String> where, Optional<String> columnIn, boolean notIn, long inValues);

	Object convertValue(Object object);

	enum ColumnType {
		Text,
		Integer,
		BigInteger,
		Float,
		Boolean,
		Timestamp,
		Date,
		Time
	}

	class ColumnOptions {

		private final String name;

		private final ColumnType type;

		private final boolean nullable;

		@Nullable
		private final Object defaultValue;

		private final boolean primaryKey;

		private final boolean autoincrement;

		private final boolean unique;

		private final OptionalInt length;

		private final OptionalInt precision;

		public ColumnOptions(final String name,
				final ColumnType type,
				@Nullable final Object defaultValue,
				final boolean nullable,
				final boolean primaryKey) {
			this(name, type, defaultValue, nullable, primaryKey, false, false);
		}

		public ColumnOptions(final String name,
				final ColumnType type,
				@Nullable final Object defaultValue,
				final boolean nullable,
				final boolean primaryKey,
				final boolean autoincrement,
				final boolean unique) {
			this.name = Objects.requireNonNull(name);
			this.type = Objects.requireNonNull(type);
			this.defaultValue = defaultValue;
			this.nullable = nullable;
			this.primaryKey = primaryKey;
			this.autoincrement = autoincrement;
			length = OptionalInt.empty();
			precision = OptionalInt.empty();
			this.unique = unique;
		}

		@Nullable
		public Object getDefaultValue() {
			return defaultValue;
		}

		public String getName() {
			return name;
		}

		public OptionalInt getLength() {
			return length;
		}

		public OptionalInt getPrecision() {
			return precision;
		}

		public ColumnType getType() {
			return type;
		}

		public boolean isAutoincrement() {
			return autoincrement;
		}

		public boolean isNullable() {
			return nullable;
		}

		public boolean isPrimaryKey() {
			return primaryKey;
		}

		public boolean isUnique() {
			return unique;
		}
	}
}
