package com.hlag.oversigt.storage;

import static com.hlag.oversigt.util.Utils.map;
import static java.util.stream.Collectors.toList;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.inject.Singleton;

@Singleton
public abstract class AbstractJdbcConnector implements Closeable {

	protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractJdbcConnector.class);

	protected abstract Connection getConnection();

	protected abstract SqlDialect getDialect();

	protected AbstractJdbcConnector() {
	}

	@Override
	public void close() throws IOException {
		try {
			getConnection().close();
		} catch (SQLException e) {
			throw new IOException("Unable to close dastabase", e);
		}
	}

	private PreparedStatement prepare(String sql, Object... values) throws SQLException {
		return prepare(sql, false, values);
	}

	private PreparedStatement prepare(String sql, boolean returnGeneratedKeys, Object... values) throws SQLException {
		PreparedStatement stmt = null;
		try {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Preparing statement: {}", sql);
			}
			stmt = getConnection().prepareStatement(sql,
					returnGeneratedKeys ? Statement.RETURN_GENERATED_KEYS : 0);
			if (values != null) {
				for (int i = 0; i < values.length; ++i) {
					stmt.setObject(i + 1, getDialect().convertValue(values[i]));
				}
			}
			return stmt;
		} catch (SQLException e) {
			try {
				if (stmt != null) {
					stmt.close();
				}
			} catch (SQLException e1) {
			}
			throw e;
		}
	}

	protected Map<String, Object> readColumnValues(ResultSet rs, String[] columnNames) throws SQLException {
		Map<String, Object> map = new HashMap<>();
		for (String columnName : columnNames) {
			try {
				map.put(columnName, readColumnValue(rs, columnName));
			} catch (SQLException e) {
				throw new SQLException("Unable to read column '" + columnName + "'", e);
			}
		}
		return map;
	}

	@SuppressWarnings("unchecked")
	protected static <T> T readColumnValue(ResultSet rs, String columnName) throws SQLException {
		return (T) rs.getObject(columnName);
	}

	protected <T> T insert(String table, ThrowingFunction<ResultSet, T> getGeneratedKey, Object... parameters) {
		return insert(table, getGeneratedKey, map(parameters));
	}

	protected <T> T insert(String table, ThrowingFunction<ResultSet, T> getGeneratedKey, Map<String, ?> values) {
		String sql = getDialect().insert(table, values.keySet());
		try {
			return executeInsert(sql, getGeneratedKey, values.values().toArray());
		} catch (SQLException e) {
			throw new DatabaseException("Unable to execute INSERT", sql, e);
		}
	}

	protected int updateByOneKey(String table, String mainName, Object mainValue, Object... whereValues) {
		return update(table, map(mainName, mainValue), map(whereValues));
	}

	protected int updateByTwoKey(String table,
			String mainName,
			Object mainValue,
			String subName,
			Object subValue,
			Object... whereValues) {
		return update(table, map(mainName, mainValue, subName, subValue), map(whereValues));
	}

	protected int updateByThreeKey(String table,
			String oneName,
			Object oneValue,
			String twoName,
			Object twoValue,
			String threeName,
			Object threeValue,
			Object... whereValues) {
		return update(table, map(oneName, oneValue, twoName, twoValue, threeName, threeValue), map(whereValues));
	}

	protected int update(String table, Map<String, Object> where, Map<String, Object> values) {
		String sql = getDialect().update(table, values.keySet(), where.keySet());
		try {
			return executeUpdate(sql,
					Stream.concat(values.values().stream(), where.values().stream()).collect(toList()));
		} catch (SQLException e) {
			throw new DatabaseException("Unable to execute UPDATE\nWHERE: " + where + "\nVALUES: " + values, sql, e);
		}
	}

	protected int delete(String table, Object... keysAndValues) {
		return delete(table, map(keysAndValues));
	}

	protected int delete(String table, Map<String, Object> keysAndValues) {
		String sql = getDialect().delete(table, keysAndValues.keySet());
		try {
			int count = executeUpdate(sql, keysAndValues.values());
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Deleted {} rows", count);
			}
			return count;
		} catch (SQLException e) {
			throw new DatabaseException("Unable to execute DELETE", sql);
		}
	}

	protected int executeUpdate(String sql, Collection<Object> values) throws SQLException {
		return executeUpdate(sql, values.toArray());
	}

	protected int executeUpdate(String sql, Object... values) throws SQLException {
		try (PreparedStatement stmt = prepare(sql, values)) {
			int count = stmt.executeUpdate();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Executed UPDATE ({} lines)", count);
			}
			return count;
		}
	}

	protected <T> T executeInsert(String sql, ThrowingFunction<ResultSet, T> getGeneratedKey, Object... values)
			throws SQLException {
		try (PreparedStatement stmt = prepare(sql, true, values)) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Executing INSERT");
			}
			stmt.executeUpdate();
			if (getGeneratedKey != null) {
				try (ResultSet gkrs = stmt.getGeneratedKeys()) {
					if (gkrs.next()) {
						return getGeneratedKey.apply(gkrs);
					} else {
						throw new DatabaseException("Unable to get generated keys. ResultSet is empty.", sql);
					}
				}
			} else {
				return null;
			}
		}
	}

	protected static <T> T first(Collection<T> objects) {
		return objects.stream().findFirst().orElse(null);
	}

	@SuppressWarnings("unchecked")
	protected <T> List<T> load(String table, String columnToRead, String columnToCheck, Object value) {
		return load(table, columnToRead, columnToCheck, value, rs -> (T) rs.getObject(1));
	}

	@SuppressWarnings("unchecked")
	protected <T> List<T> loadWithOneLike(String table,
			String columnToRead,
			String[] columnsToCheck,
			Object[] valuesToCheck,
			String columnToCheckWithLike,
			String likeValue) {
		return load(getDialect().selectithOneLike(table,
				Arrays.asList(columnToRead),
				Arrays.asList(columnsToCheck),
				columnToCheckWithLike), rs -> (T) rs.getObject(1), simplify(valuesToCheck, likeValue));
	}

	protected List<Map<String, Object>> loadValues(String table,
			String columnToRead,
			String columnToCheck,
			Object value,
			String[] columnNames) {
		return loadValues(table, columnToRead, columnToCheck, new Object[] { value }, columnNames);
	}

	protected List<Map<String, Object>> loadValues(String table,
			String columnToRead,
			String columnToCheck,
			Collection<Object> values,
			String[] columnNames) {
		return loadValues(table, columnToRead, columnToCheck, !values.isEmpty() ? values.toArray() : null, columnNames);
	}

	protected List<Map<String, Object>> loadValues(String table,
			String columnToRead,
			String columnToCheck,
			Object[] values,
			String[] columnNames) {
		if (values != null && values.length == 1) {
			return load(table, columnToRead, columnToCheck, values[0], rs -> readColumnValues(rs, columnNames));
		} else {
			String sql = getDialect()
					.select(table, Lists.newArrayList("*"), columnToCheck, values != null ? values.length : -1);
			return load(sql, rs -> readColumnValues(rs, columnNames), values);
		}
	}

	protected <T> List<T> load(String table,
			String columnToRead,
			String columnToCheckA,
			Object valueA,
			String columnToCheckB,
			Object valueB,
			ThrowingFunction<ResultSet, T> converter) {
		String sql = getDialect().select(table,
				Arrays.asList(columnToRead),
				Arrays.asList(Objects.requireNonNull(columnToCheckA), Objects.requireNonNull(columnToCheckB)));
		return load(sql, converter, new Object[] { valueA, valueB });
	}

	protected <T> List<T> load(String table,
			String columnToRead,
			String columnToCheck,
			Object value,
			ThrowingFunction<ResultSet, T> converter) {
		String sql = getDialect().select(table,
				Arrays.asList(columnToRead),
				columnToCheck != null ? Arrays.asList(columnToCheck) : null);
		return load(sql, converter, columnToCheck != null ? new Object[] { value } : new Object[0]);
	}

	protected <T> List<T> load(String sql, ThrowingFunction<ResultSet, T> converter, Object... values) {
		try (PreparedStatement stmt = prepare(sql, values)) {
			return load(stmt, converter);
		} catch (Exception e) {
			throw new DatabaseException("Unable to read", sql, e);
		}
	}

	private Object[] simplify(Object... objects) {
		List<Object> out = new ArrayList<>();
		for (Object object : objects) {
			if (object instanceof Object[]) {
				out.addAll(Arrays.asList((Object[]) object));
			} else {
				out.add(object);
			}
		}
		return out.toArray();
	}

	protected <T> List<T> getList(ResultSet rs, ThrowingFunction<ResultSet, T> converter) throws SQLException {
		List<T> list = new ArrayList<>();
		while (rs.next()) {
			list.add(converter.apply(rs));
		}
		return list;
	}

	protected <T> List<T> load(String sql, ThrowingFunction<ResultSet, T> converter) {
		try (PreparedStatement stmt = prepare(sql)) {
			return load(stmt, converter);
		} catch (Exception e) {
			throw new DatabaseException("Unable to read data", sql, e);
		}
	}

	protected <T> List<T> load(PreparedStatement stmt, ThrowingFunction<ResultSet, T> converter) throws SQLException {
		try (ResultSet resultSet = stmt.executeQuery()) {
			return load(resultSet, converter);
		}
	}

	protected <T> List<T> load(ResultSet resultSet, ThrowingFunction<ResultSet, T> converter) throws SQLException {
		List<T> items = new ArrayList<>();
		while (resultSet.next()) {
			T item = converter.apply(resultSet);
			items.add(item);
		}
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Executed SELECT ({} lines)", items.size());
		}
		return items;
	}

	protected static Timestamp now() {
		return new Timestamp(ZonedDateTime.now().toInstant().toEpochMilli());
	}

	@FunctionalInterface
	protected static interface ThrowingRunnable {
		void run() throws SQLException;
	}

	@FunctionalInterface
	protected static interface ThrowingFunction<I, O> {
		O apply(I argument) throws SQLException;
	}
}
