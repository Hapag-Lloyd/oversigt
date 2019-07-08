package com.hlag.oversigt.sources;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSourceException;
import com.hlag.oversigt.core.eventsource.EventSourceStatisticsManager.StatisticsCollector.StartedAction;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.DatabaseConnection;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * @author Olaf Neumann
 */
public abstract class AbstractJdbcEventSource<T extends OversigtEvent> extends ScheduledEventSource<T> {
	protected static final Logger DB_LOGGER = LoggerFactory.getLogger("db");

	private DatabaseConnection databaseConnection = DatabaseConnection.EMPTY;

	private Credentials credentials = Credentials.EMPTY;

	private Duration databaseQueryInterval = Duration.ofHours(1);

	@Property(name = "Database Connection", description = "The connection details to be used for the SQL connection.")
	public DatabaseConnection getDatabaseConnection() {
		return databaseConnection;
	}

	public void setDatabaseConnection(final DatabaseConnection databaseConnection) {
		this.databaseConnection = databaseConnection;
	}

	@Property(name = "Credentials", description = "The credentials to be used to connect to the database.")
	public Credentials getCredentials() {
		return credentials;
	}

	public void setCredentials(final Credentials credentials) {
		this.credentials = credentials;
	}

	@Property(name = "Query interval", description = "How often should this event source call the database?")
	public Duration getDatabaseQueryInterval() {
		return databaseQueryInterval;
	}

	public void setDatabaseQueryInterval(final Duration databaseQueryInterval) {
		this.databaseQueryInterval = databaseQueryInterval;
	}

	private Connection wrapConnection(final Connection connection) {
		return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { Connection.class },
				new UnpreparedStatementPreventingInvocationHandler(connection));
	}

	@SuppressWarnings("resource")
	private Connection getConnection() throws EventSourceException {
		if (getDatabaseConnection() == DatabaseConnection.EMPTY) {
			throw new EventSourceException("Database connection is not configured.");
		}
		try {
			// Load the driver
			getDatabaseConnection().loadDriverClass();
			getLogger().info("Loaded JDBC driver.");

			// Create the connection using the IBM Data Server Driver for JDBC and SQLJ
			final Connection con = DriverManager.getConnection(getDatabaseConnection().getJdbcUrl(),
					getCredentials().getUsername(),
					getCredentials().getPassword());
			// Commit changes manually
			con.setAutoCommit(false);
			con.setReadOnly(true);
			con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
			getLogger().info("Created JDBC connection to the data source.");
			return wrapConnection(con);
		} catch (final ClassNotFoundException e) {
			throw new EventSourceException("Could not load JDBC driver.", e);
		} catch (final SQLException e) {
			throw new EventSourceException("Failed connecting to data base.", e);
		}
	}

	@SuppressWarnings("resource")
	private void withConnection(final DBConnectionConsumer consumer) throws SQLException, EventSourceException {
		final Connection connection = getConnection();
		try {
			consumer.apply(connection);
		} finally {
			try {
				connection.rollback();
				connection.close();
			} catch (final SQLException e) {
				getLogger().warn("Unable to rollback and close DB connection", e);
			}
		}
	}

	private Optional<LocalDateTime> lastDbAccessDateTime = Optional.empty();

	@Override
	protected final Optional<T> produceEvent() throws EventSourceException {
		if (lastDbAccessDateTime.map(LocalDateTime.now().minus(getDatabaseQueryInterval())::isAfter).orElse(false)) {
			try {
				withConnection(this::gatherDatabaseInfo);
				lastDbAccessDateTime = Optional.of(LocalDateTime.now());
			} catch (final SQLException e) {
				throw new EventSourceException("Unable to gather database info", e);
			}
		}
		return produceEventFromData();
	}

	protected abstract void gatherDatabaseInfo(Connection connection) throws SQLException;

	protected abstract Optional<T> produceEventFromData();

	protected <X> List<X> readFromDatabase(final Connection connection,
			final ResultSetFunction<X> readOneLine,
			final String sql,
			final Object... parameters) throws SQLException {

		final long time = System.currentTimeMillis();
		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; i += 1) {
				stmt.setObject(i + 1, parameters[i]);
			}
			final StartedAction action = getStatisticsCollector().startAction("SQL-Query", sql);
			try {
				return readFromDatabase(stmt, readOneLine);
			} finally {
				action.done();
			}
		} catch (final SQLException e) {
			DB_LOGGER.error("Query failed", e);
			throw e;
		} finally {
			DB_LOGGER.info("Finished query. Duration: " + (System.currentTimeMillis() - time));
			DB_LOGGER.trace(sql);
		}
	}

	private static <T> List<T> readFromDatabase(final PreparedStatement statement,
			final ResultSetFunction<T> readOneLine) throws SQLException {
		try (ResultSet rs = statement.executeQuery()) {
			return readFromDatabase(rs, readOneLine);
		}
	}

	private static <T> List<T> readFromDatabase(final ResultSet rs, final ResultSetFunction<T> readOneLine)
			throws SQLException {
		final List<T> list = new ArrayList<>();
		while (rs.next()) {
			final T item = readOneLine.readLine(rs);
			list.add(item);
		}
		return list;
	}

	/**
	 * Executes the given query
	 *
	 * @param <X>         the type of the return value
	 * @param connection  the DB connection to use
	 * @param readOneLine the function converting one line of the result set into a
	 *                    result row
	 * @param sql         the query to execute
	 * @param parameters  parameters to be inserted in the prepared statement
	 * @return the read rows
	 * @throws SQLException if something fails
	 * @deprecated use
	 *             {@link #readFromDatabase(Connection, ResultSetFunction, String, Object...)}
	 *             instead
	 */
	@Deprecated
	public static <X> List<X> readFromDatabaseStatic(final Connection connection,
			final ResultSetFunction<X> readOneLine,
			final String sql,
			final Object... parameters) throws SQLException {

		final long time = System.currentTimeMillis();
		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; i += 1) {
				stmt.setObject(i + 1, parameters[i]);
			}
			return readFromDatabase(stmt, readOneLine);
		} catch (final SQLException e) {
			DB_LOGGER.error("Query failed", e);
			throw e;
		} finally {
			DB_LOGGER.info("Finished query. Duration: " + (System.currentTimeMillis() - time));
			DB_LOGGER.trace(sql);
		}
	}

	@FunctionalInterface
	private interface DBConnectionConsumer {
		void apply(Connection connection) throws SQLException;
	}

	@FunctionalInterface
	public interface ResultSetFunction<T> {
		T readLine(ResultSet resultSet) throws SQLException;
	}

	private static final class UnpreparedStatementPreventingInvocationHandler implements InvocationHandler {
		private final Connection connection;

		private UnpreparedStatementPreventingInvocationHandler(final Connection connection) {
			this.connection = connection;
		}

		@Override
		public Object invoke(@SuppressWarnings("unused") @Nullable final Object proxy,
				@Nullable final Method method,
				@Nullable final Object[] args) throws Throwable {
			if (Objects.requireNonNull(method).getDeclaringClass() == Connection.class
					&& "createStatement".equals(method.getName())) {
				throw new RuntimeException(
						"Oversigt does not allow unprepared statements. Please use #prepareStatement instead.");
			}
			return method.invoke(connection, args);
		}
	}
}
