package com.hlag.oversigt.storage;

import static com.hlag.oversigt.storage.DBConstants.COLUMNS_DASHBOARD;
import static com.hlag.oversigt.storage.DBConstants.COLUMNS_EVENT_SOURCE;
import static com.hlag.oversigt.storage.DBConstants.COLUMNS_EVENT_SOURCE_PROPERTY;
import static com.hlag.oversigt.storage.DBConstants.COLUMNS_VALUES;
import static com.hlag.oversigt.storage.DBConstants.COLUMNS_WIDGET;
import static com.hlag.oversigt.storage.DBConstants.COLUMNS_WIDGET_DATA;
import static com.hlag.oversigt.storage.DBConstants.COLUMN_OPTIONS_DASHBOARD;
import static com.hlag.oversigt.storage.DBConstants.COLUMN_OPTIONS_EVENT_SOURCE;
import static com.hlag.oversigt.storage.DBConstants.COLUMN_OPTIONS_EVENT_SOURCE_PROPERTY;
import static com.hlag.oversigt.storage.DBConstants.COLUMN_OPTIONS_VALUES;
import static com.hlag.oversigt.storage.DBConstants.COLUMN_OPTIONS_WIDGET;
import static com.hlag.oversigt.storage.DBConstants.COLUMN_OPTIONS_WIDGET_DATA;
import static com.hlag.oversigt.storage.DBConstants.TABLE_DASHBOARD;
import static com.hlag.oversigt.storage.DBConstants.TABLE_EVENT_SOURCE;
import static com.hlag.oversigt.storage.DBConstants.TABLE_EVENT_SOURCE_PROPERTY;
import static com.hlag.oversigt.storage.DBConstants.TABLE_VALUES;
import static com.hlag.oversigt.storage.DBConstants.TABLE_WIDGET;
import static com.hlag.oversigt.storage.DBConstants.TABLE_WIDGET_DATA;
import static com.hlag.oversigt.util.StringUtils.list;
import static com.hlag.oversigt.util.Utils.is;
import static com.hlag.oversigt.util.Utils.map;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardColorScheme;
import com.hlag.oversigt.model.EventSourceDescriptor;
import com.hlag.oversigt.model.EventSourceInstance;
import com.hlag.oversigt.model.EventSourceProperty;
import com.hlag.oversigt.model.Widget;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.storage.SqlDialect.ColumnOptions;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.TypeUtils;

@Singleton
public class JdbcDatabase extends AbstractJdbcConnector implements Storage {
	private static final Logger LOGGER = LoggerFactory.getLogger(JdbcDatabase.class);

	private static final String TYPE_PROPERTY = "PROPERTY";

	private static final String TYPE_DATA = "DATA";

	private static final Optional<ThrowingFunction<ResultSet, Integer>> RETRIEVE_ID = Optional.of(rs -> rs.getInt(1));

	private final SqlDialect sqlDialect;

	private final Connection connection;

	private final JsonUtils json;

	@Inject
	public JdbcDatabase(final SqlDialect dialect,
			@Named("databaseLocation") final String databaseLocation,
			@Named("databaseName") final String schema,
			@Named("databaseUsername") final String username,
			@Named("databasePassword") final String password,
			final JsonUtils json) {
		sqlDialect = dialect;
		this.json = json;
		try {
			LOGGER.info("Loading JDBC driver class: " + dialect.getDriverClassName());
			Class.forName(dialect.getDriverClassName());
			LOGGER.info("Creating JDBC connection for location '{}' and schema '{}'", databaseLocation, schema);
			connection = DriverManager
					.getConnection(dialect.getJdbcConnectionUrl(databaseLocation, schema, username, password));
			checkTable(TABLE_EVENT_SOURCE, COLUMN_OPTIONS_EVENT_SOURCE);
			checkTable(TABLE_EVENT_SOURCE_PROPERTY, COLUMN_OPTIONS_EVENT_SOURCE_PROPERTY);
			checkTable(TABLE_DASHBOARD, COLUMN_OPTIONS_DASHBOARD);
			checkTable(TABLE_WIDGET, COLUMN_OPTIONS_WIDGET);
			checkTable(TABLE_WIDGET_DATA, COLUMN_OPTIONS_WIDGET_DATA);
			checkTable(TABLE_VALUES, COLUMN_OPTIONS_VALUES);
		} catch (final SQLException | ClassNotFoundException e) {
			throw new RuntimeException("Unable to create database connection", e);
		}
	}

	@Override
	protected SqlDialect getDialect() {
		return sqlDialect;
	}

	private void checkTable(final String tableName, final ColumnOptions[] options) throws SQLException {
		LOGGER.debug("Checking table " + tableName);
		final DatabaseMetaData databaseMetaData = getConnection().getMetaData();
		try (ResultSet tables = databaseMetaData.getTables(null, null, tableName, new String[] { "TABLE" })) {
			final List<Object> tableList = getList(tables, rs -> rs.getString("TABLE_NAME"));
			if (tableList.isEmpty()) {
				// Table does not exist. we need to create it
				LOGGER.info("Creating table " + tableName);
				final String sql = getDialect().createTable(tableName, options);
				executeUpdate(sql);
			} else {
				final List<Map<String, Object>> columnProperties;
				try (ResultSet columns = databaseMetaData.getColumns(null, null, tableName, "%")) {
					columnProperties = load(columns,
							rs -> readColumnValues(rs,
									new String[] {
											"COLUMN_NAME",
											"TYPE_NAME",
											"COLUMN_SIZE",
											"IS_NULLABLE",
											"IS_AUTOINCREMENT" }));
				}
				for (final ColumnOptions option : options) {
					final Optional<Map<String, Object>> foundColumnProperties = columnProperties.stream()
							.filter(cp -> option.getName().equals(cp.get("COLUMN_NAME")))
							.findFirst();
					if (!foundColumnProperties.isPresent()) {
						LOGGER.info("Altering table " + tableName + ". Adding column " + option.getName());
						final String sql = getDialect().alterTableAddColumn(tableName, option);
						executeUpdate(sql);
					} else {
						columnProperties.remove(foundColumnProperties.get());
					}
				}
				final List<String> columnsToDrop = columnProperties.stream()
						.map(m -> (String) m.get("COLUMN_NAME"))
						.collect(Collectors.toList());
				for (final String columnToDrop : columnsToDrop) {
					LOGGER.info("Altering table " + tableName + ". Dropping column " + columnToDrop);
					final String sql = getDialect().alterTableDropColumn(tableName, columnToDrop);
					executeUpdate(sql);
				}
			}
		} catch (final Exception e) {
			throw new RuntimeException("Unable to check table: " + tableName, e);
		}
	}

	@Override
	public void close() throws IOException {
		try {
			connection.close();
			LOGGER.info("Database connection closed.");
		} catch (final SQLException e) {
			throw new IOException("Unable to close dastabase", e);
		}
	}

	@Override
	protected Connection getConnection() {
		return connection;
	}

	@Override
	public List<String> getEventSourceNames() {
		return load(TABLE_EVENT_SOURCE, "NAME");
	}

	@Override
	public Collection<String> getEventSourcesIds() {
		return load(TABLE_EVENT_SOURCE, "ID");
	}

	@Override
	public EventSourceInstance loadEventSourceInstance(final String eventSourceId,
			final BiFunction<Optional<String>, String, EventSourceDescriptor> descriptorSupplier) {
		final Map<String, Object> infos = load(TABLE_EVENT_SOURCE,
				"*",
				Optional.of("ID"),
				eventSourceId,
				rs -> readColumnValues(rs, COLUMNS_EVENT_SOURCE)).get(0);

		final String id = (String) infos.get("ID");
		final String view = (String) infos.get("VIEW");
		final String name = (String) infos.get("NAME");
		final boolean enabled = is(infos.get("ENABLED"));
		final Optional<String> className = Optional.ofNullable((String) infos.get("EVENT_SOURCE_CLASS"));
		final Duration frequency = Optional.ofNullable((Number) infos.get("FREQUENCY"))
				.map(Number::longValue)
				.map(Duration::ofSeconds)
				.orElse(null);
		final String createdBy = (String) infos.get("CREATED_BY");
		final String lastChangeBy = (String) infos.get("LAST_CHANGE_BY");

		return new EventSourceInstance(descriptorSupplier.apply(className,
				view), id, name, enabled, frequency, createdBy, lastChangeBy);
	}

	@Override
	public Map<String, String> getEventSourceInstanceProperties(final String id) {
		return getEventSourceInstance(id, TYPE_PROPERTY);
	}

	@Override
	public Map<String, String> getEventSourceInstanceDataItems(final String id) {
		return getEventSourceInstance(id, TYPE_DATA);
	}

	private Map<String, String> getEventSourceInstance(final String id, final String type) {
		return load(TABLE_EVENT_SOURCE_PROPERTY,
				"*",
				"EVENT_SOURCE_ID",
				id,
				"TYPE",
				type,
				rs -> readColumnValues(rs, new String[] { "NAME", "VALUE" })).stream()
						.collect(Collectors.toMap(m -> (String) m.get("NAME"), m -> (String) m.get("VALUE")));
	}

	@Override
	public void persistEventSourceInstance(final EventSourceInstance instance) {
		final Map<String, Object> values = map("NAME",
				instance.getName(),
				"ENABLED",
				instance.isEnabled(),
				"EVENT_SOURCE_CLASS",
				instance.getDescriptor().getServiceClass().map(c -> c.getName()).orElse(null), //
				"FREQUENCY",
				instance.getFrequency(),
				"VIEW",
				instance.getDescriptor().getView(),
				"LAST_CHANGE",
				now(),
				"LAST_CHANGE_BY",
				instance.getLastChangeBy());
		persist(TABLE_EVENT_SOURCE,
				"ID",
				instance,
				EventSourceInstance::getId,
				values,
				map("CREATED_ON", now(), "CREATED_BY", instance.getCreatedBy()));
		final List<Map<String, Object>> existingProperties = load(TABLE_EVENT_SOURCE_PROPERTY,
				"*",
				Optional.of("EVENT_SOURCE_ID"),
				instance.getId(),
				rs -> readColumnValues(rs, COLUMNS_EVENT_SOURCE_PROPERTY));
		createOrUpdateEventSourceProperties(instance,
				instance.getDescriptor().getProperties().stream(),
				"PROPERTY",
				existingProperties);
		delete(TABLE_EVENT_SOURCE_PROPERTY, map("EVENT_SOURCE_ID", instance.getId(), "TYPE", "DATA"));
		createOrUpdateEventSourceProperties(instance,
				instance.getDescriptor().getDataItems().stream().filter(instance::hasPropertyValue),
				"DATA",
				Collections.emptyList());
	}

	private void createOrUpdateEventSourceProperties(final EventSourceInstance instance,
			final Stream<EventSourceProperty> propertyStream,
			final String type,
			final List<Map<String, Object>> existingProperties) {
		createOrUpdateEventSourceProperties(instance,
				propertyStream.collect(Collectors.toMap(Function.identity(),
						p -> Strings.nullToEmpty(instance.getPropertyValueString(p)))),
				existingProperties.stream()
						.filter(m -> type.equals(m.get("TYPE")))
						.map(m -> (String) m.get("NAME"))
						.collect(Collectors.toSet()));
	}

	private void createOrUpdateEventSourceProperties(final EventSourceInstance instance,
			final Map<EventSourceProperty, String> properties,
			final Set<String> existingNames) {
		for (final Entry<EventSourceProperty, String> e : properties.entrySet()) {
			if (existingNames.contains(e.getKey().getName())) {
				// update
				updateByThreeKey(TABLE_EVENT_SOURCE_PROPERTY,
						"EVENT_SOURCE_ID",
						instance.getId(),
						"TYPE",
						e.getKey().getType(),
						"NAME",
						e.getKey().getName(),
						"VALUE",
						e.getValue());
			} else {
				// create
				insert(TABLE_EVENT_SOURCE_PROPERTY,
						Optional.empty(),
						"EVENT_SOURCE_ID",
						instance.getId(),
						"TYPE",
						e.getKey().getType(),
						"NAME",
						e.getKey().getName(),
						"VALUE",
						e.getValue());
			}
		}
	}

	@Override
	public void deleteEventSourceInstance(final String eventSourceId) {
		delete(TABLE_EVENT_SOURCE, "ID", eventSourceId);
		delete(TABLE_EVENT_SOURCE_PROPERTY, "EVENT_SOURCE_ID", eventSourceId);
	}

	@Override
	public void updateEventSourceClasses(final String oldClassName, final String newClassName) {
		update(TABLE_EVENT_SOURCE, map("EVENT_SOURCE_CLASS", oldClassName), map("EVENT_SOURCE_CLASS", newClassName));
	}

	@Override
	public List<String> getDashboardIds() {
		return load(TABLE_DASHBOARD, "ID");
	}

	@Override
	public void persistDashboard(final Dashboard dashboard) {
		final Map<String, Object> values = map("TITLE",
				dashboard.getTitle(),
				"WIDTH",
				dashboard.getScreenWidth(),
				"HEIGHT",
				dashboard.getScreenHeight(),
				"COLUMNS",
				dashboard.getColumns(),
				"BACKGROUND_COLOR",
				dashboard.getBackgroundColor().getHexColor(),
				"COLOR_SCHEME",
				dashboard.getColorScheme().name(),
				"FOREGROUND_COLOR_START",
				dashboard.getForegroundColorStart().getHexColor(),
				"FOREGROUND_COLOR_END",
				dashboard.getForegroundColorEnd().getHexColor(),
				"OWNER",
				dashboard.getOwners().stream().collect(Collectors.joining(",", ",", ",")),
				"EDITOR",
				dashboard.getEditors().stream().collect(Collectors.joining(",", ",", ",")),
				"ENABLED",
				dashboard.isEnabled(),
				"LAST_CHANGE",
				now());
		persist(TABLE_DASHBOARD, "ID", dashboard, Dashboard::getId, values);
	}

	@Override
	public List<Dashboard> loadDashboards() {
		return load(TABLE_DASHBOARD, "*", Optional.empty(), null, rs -> readColumnValues(rs, COLUMNS_DASHBOARD))//
				.stream()
				.map(this::loadDashboard)
				.collect(Collectors.toList());
	}

	@Override
	public Optional<Dashboard> loadDashboard(final String id) {
		final List<Map<String, Object>> maybeData
				= load(TABLE_DASHBOARD, "*", Optional.of("ID"), id, rs -> readColumnValues(rs, COLUMNS_DASHBOARD));

		if (maybeData.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(loadDashboard(maybeData.get(0)));
	}

	public Dashboard loadDashboard(final Map<String, Object> data) {
		final String id = (String) data.get("ID");
		final String title = (String) data.get("TITLE");
		final boolean enabled = is(data.get("ENABLED"));
		final int screenWidth = (int) data.get("WIDTH");
		final int screenHeight = (int) data.get("HEIGHT");
		final int columns = (int) data.get("COLUMNS");
		final Color backgroundColor = Color.parse((String) data.get("BACKGROUND_COLOR"));
		final DashboardColorScheme colorScheme = DashboardColorScheme.fromString((String) data.get("COLOR_SCHEME"));
		final Color foregroundColorStart = Color.parse((String) data.get("FOREGROUND_COLOR_START"));
		final Color foregroundColorEnd = Color.parse((String) data.get("FOREGROUND_COLOR_END"));
		final Collection<String> owners = list((String) data.get("OWNER"));
		final Collection<String> editors = list((String) data.get("EDITOR"));

		return new Dashboard(id,
				title,
				enabled,
				screenWidth,
				screenHeight,
				columns,
				backgroundColor,
				colorScheme,
				foregroundColorStart,
				foregroundColorEnd,
				owners,
				editors);
	}

	@Override
	public void createWidget(final Dashboard dashboard, final Widget widget) {
		final OptionalInt id = insert(TABLE_WIDGET,
				RETRIEVE_ID,
				"DASHBOARD_ID",
				dashboard.getId(),
				"EVENT_SOURCE_ID",
				widget.getEventSourceInstance().getId(),
				"TITLE",
				widget.getTitle(),
				"NAME",
				widget.getName(),
				"POS_X",
				widget.getPosX(),
				"POS_Y",
				widget.getPosY(),
				"SIZE_X",
				widget.getSizeX(),
				"SIZE_Y",
				widget.getSizeY(),
				"ENABLED",
				widget.isEnabled(),
				"BACKGROUND_COLOR",
				widget.getBackgroundColor().getHexColor(),
				"STYLE",
				widget.getStyle(),
				"LAST_CHANGE",
				now()).map(OptionalInt::of).orElse(OptionalInt.empty());
		if (!id.isPresent()) {
			throw new RuntimeException(String
					.format("In dashboard [%s]: unable to create widget: %s", dashboard.getId(), widget.toString()));
		}
		widget.setId(id.getAsInt());
		createOrUpdateWidgetProperties(widget);
	}

	private void deleteWidgetDatas(final Widget widget) {
		final String sql = getDialect().delete(TABLE_WIDGET_DATA,
				Arrays.asList("WIDGET_ID"),
				Optional.of("NAME"),
				true,
				widget.getEventSourceInstance().getDescriptor().getDataItems().size());
		final List<Object> items = new ArrayList<>();
		items.add(widget.getId());
		items.addAll(widget.getEventSourceInstance()
				.getDescriptor()
				.getDataItems()
				.stream()
				.map(EventSourceProperty::getName)
				.collect(Collectors.toList()));
		try {
			final int amount = executeUpdate(sql, items);
			LOGGER.info("Deleted " + amount + " data rows.");
		} catch (final SQLException e) {
			throw new RuntimeException("Unable to delete unnesseccary widget data items", e);
		}
	}

	private void createOrUpdateWidgetProperties(final Widget widget) {
		// determine values to persist
		final Map<String, String> values = widget.getEventSourceInstance()
				.getDescriptor()
				.getDataItems()
				.stream()
				.filter(p -> !widget.getEventSourceInstance().hasPropertyValue(p))
				.collect(Collectors.toMap(EventSourceProperty::getName,
						p -> Strings.nullToEmpty(widget.getWidgetData(p))));

		// find names of already existing values
		deleteWidgetDatas(widget);
		final List<String> exisitingNames = load(TABLE_WIDGET_DATA, "NAME", Optional.of("WIDGET_ID"), widget.getId());
		// TODO we can omit the "OrUpdate"
		createOrUpdateWidgetProperties(widget.getId(), values, exisitingNames);
	}

	private void createOrUpdateWidgetProperties(final int widgetId,
			final Map<String, String> entries,
			final Collection<String> exisitingNames) {
		for (final Entry<String, String> e : entries.entrySet()) {
			if (exisitingNames.contains(e.getKey())) {
				// update
				updateByTwoKey(TABLE_WIDGET_DATA, "WIDGET_ID", widgetId, "NAME", e.getKey(), "VALUE", e.getValue());
			} else {
				// create
				insert(TABLE_WIDGET_DATA,
						Optional.empty(),
						"WIDGET_ID",
						widgetId,
						"NAME",
						e.getKey(),
						"VALUE",
						e.getValue());
			}
		}
	}

	@Override
	public List<Widget> loadWidgetDatas(final Dashboard dashboard,
			final Function<String, EventSourceInstance> instanceProvider) {
		final List<Map<String, Object>> widgetInfoList = load(TABLE_WIDGET,
				"*",
				Optional.of("DASHBOARD_ID"),
				dashboard.getId(),
				rs -> readColumnValues(rs, COLUMNS_WIDGET));
		final List<Map<String, Object>> widgetDataMapList = loadValues(TABLE_WIDGET_DATA,
				"*",
				"WIDGET_ID",
				widgetInfoList.stream().map(m -> (Integer) m.get("ID")).collect(Collectors.toSet()),
				COLUMNS_WIDGET_DATA);

		final List<Widget> widgets = widgetInfoList.stream()
				.map(m -> loadWidget(m, widgetDataMapList, instanceProvider))
				.collect(Collectors.toList());
		return widgets;
	}

	private Widget loadWidget(final Map<String, Object> map,
			final List<Map<String, Object>> widgetDataMapList,
			final Function<String, EventSourceInstance> instanceProvider) {
		final int id = (int) map.get("ID");
		final EventSourceInstance eventSource = instanceProvider.apply((String) map.get("EVENT_SOURCE_ID"));
		final String title = (String) map.get("TITLE");
		final String name = (String) map.get("NAME");
		final boolean enabled = is(map.get("ENABLED"));
		final int posX = (int) map.get("POS_X");
		final int posY = (int) map.get("POS_Y");
		final int sizeX = (int) map.get("SIZE_X");
		final int sizeY = (int) map.get("SIZE_Y");
		final Color backgroundColor = Color.parse((String) map.get("BACKGROUND_COLOR"));
		final String style = (String) map.get("STYLE");

		final Widget widget
				= new Widget(id, eventSource, title, name, enabled, posX, posY, sizeX, sizeY, backgroundColor, style);

		final Map<String, String> datas = widgetDataMapList.stream()
				.filter(d -> (int) d.get("WIDGET_ID") == widget.getId())
				.collect(Collectors.toMap(d -> (String) d.get("NAME"), d -> (String) d.get("VALUE")));

		eventSource.getDescriptor()
				.getDataItems()
				.stream()
				.filter(p -> datas.containsKey(p.getName()))
				.filter(p -> !Strings.isNullOrEmpty(datas.get(p.getName())))
				.forEach(p -> widget.setWidgetData(p, datas.get(p.getName())));

		return widget;
	}

	@Override
	public List<String> getOwnedDashboardIds(final String userid) {
		return loadWithOneLike(TABLE_DASHBOARD,
				"ID",
				new String[] { /* "ENABLED" */ },
				new Object[] { /* true */ },
				"OWNER",
				"%," + userid + ",%");
	}

	@Override
	public List<String> getEditableDashboardIds(final String userid) {
		return loadWithOneLike(TABLE_DASHBOARD,
				"ID",
				new String[] { /* "ENABLED" */ },
				new Object[] { /* true */ },
				"EDITOR",
				"%," + userid + ",%");
	}

	@Override
	public boolean deleteDashboard(final Dashboard dashboard) {
		dashboard.getWidgets().stream().forEach(this::deleteWidget);
		return delete(TABLE_DASHBOARD, "ID", dashboard.getId()) == 1;
	}

	@Override
	public void updateWidget(final Widget widget) {
		updateByOneKey(TABLE_WIDGET,
				"ID",
				widget.getId(),
				"TITLE",
				widget.getTitle(),
				"NAME",
				widget.getName(),
				"ENABLED",
				widget.isEnabled(),
				"POS_X",
				widget.getPosX(),
				"POS_Y",
				widget.getPosY(),
				"SIZE_X",
				widget.getSizeX(),
				"SIZE_Y",
				widget.getSizeY(),
				"BACKGROUND_COLOR",
				widget.getBackgroundColor().getHexColor(),
				"STYLE",
				widget.getStyle());

		createOrUpdateWidgetProperties(widget);
	}

	@Override
	public void deleteWidget(final Widget widget) {
		deleteWidget(widget.getId());
	}

	private void deleteWidget(final int id) {
		delete(TABLE_WIDGET_DATA, "WIDGET_ID", id);
		delete(TABLE_WIDGET, "ID", id);
	}

	@Override
	public <T extends SerializableProperty> Optional<T> getProperty(final Class<T> clazz, final int id) {
		final List<Map<String, Object>> credentialsMap = load(TABLE_VALUES,
				"*",
				Optional.of("ID"),
				id,
				// XXX Column CLASS should be checked, too
				rs -> readColumnValues(rs, COLUMNS_VALUES));
		return credentialsMap.stream().findFirst().map(m -> mapToValue(clazz, m));
	}

	private <T extends SerializableProperty> T mapToValue(final Class<T> clazz, final Map<String, Object> map) {
		return Objects.requireNonNull(json.fromJson((String) map.get("JSON"), clazz), "Unable to create object");
	}

	@Override
	public <T extends SerializableProperty> List<T> listProperties(final Class<T> clazz) {
		return load(TABLE_VALUES,
				"*",
				Optional.of("CLASS"),
				clazz.getName(),
				rs -> readColumnValues(rs, COLUMNS_VALUES))//
						.stream()//
						.map(m -> mapToValue(clazz, m))//
						.sorted()//
						.collect(toList());
	}

	@Override
	public void updateProperty(final SerializableProperty value) {
		update(TABLE_VALUES,
				map("ID", value.getId(), "CLASS", value.getClass().getName()),
				map("NAME", value.getName(), "JSON", json.toJson(value)));
	}

	@Override
	public void deleteProperty(final Class<? extends SerializableProperty> clazz, final int id) {
		delete(TABLE_VALUES, "ID", id, "CLASS", clazz.getName());
	}

	@Override
	public <T extends SerializableProperty> T createProperty(final Class<T> clazz,
			final String name,
			final Object... parameters) {
		final OptionalInt id = insert(TABLE_VALUES, RETRIEVE_ID, "CLASS", clazz.getName(), "NAME", name, "JSON", "{}")
				.map(OptionalInt::of)
				.orElse(OptionalInt.empty());
		if (!id.isPresent()) {
			throw new RuntimeException("Unable to create property: " + name);
		}
		final Object[] params = new Object[2 + parameters.length];
		params[0] = id.getAsInt();
		params[1] = name;
		System.arraycopy(parameters, 0, params, 2, parameters.length);
		final T value;
		try {
			value = TypeUtils.getAppropriateConstructor(clazz, params).newInstance(params);
		} catch (final InstantiationException
				| IllegalAccessException
				| IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException(
					"Class " + clazz.getName() + " does not contain a constructor to be used in the database class",
					e);
		}
		updateProperty(value);
		return value;
	}

	private <T, I> void persist(final String table,
			final String idColumn,
			final T item,
			final Function<T, I> getId,
			final Map<String, Object> values) {
		persist(table, idColumn, item, getId, values, new HashMap<>());
	}

	private <T, I> void persist(final String table,
			final String idColumn,
			final T item,
			final Function<T, I> getId,
			final Map<String, Object> values,
			final Map<String, Object> creationValues) {
		final List<String> found
				= load(table, idColumn, Optional.of(idColumn), getId.apply(item), rs -> rs.getString(idColumn));
		if (found.isEmpty()) {
			values.put(idColumn, getId.apply(item));
			values.putAll(creationValues);
			insert(table, Optional.empty(), values);
		} else if (found.size() == 1) {
			// update existing entry
			update(table, map(idColumn, getId.apply(item)), values);
		} else {
			throw new RuntimeException("More than one line found for ID: " + getId.apply(item));
		}
	}
}
