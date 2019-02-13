package com.hlag.oversigt.storage;

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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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
public class JDBCDatabase extends AbstractJdbcConnector implements Storage, DBConstants {
	private static final Logger LOGGER = LoggerFactory.getLogger(JDBCDatabase.class);
	private static final String TYPE_PROPERTY = "PROPERTY";
	private static final String TYPE_DATA = "DATA";

	private static final ThrowingFunction<ResultSet, Integer> RETRIEVE_ID = rs -> rs.getInt(1);

	private final SqlDialect sqlDialect;
	private final Connection connection;

	private final JsonUtils json;

	@Inject
	public JDBCDatabase(SqlDialect dialect,
			@Named("databaseLocation") String databaseLocation,
			@Named("databaseName") String schema,
			@Named("databaseUsername") String username,
			@Named("databasePassword") String password,
			JsonUtils json) {
		this.sqlDialect = dialect;
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
		} catch (SQLException | ClassNotFoundException e) {
			throw new RuntimeException("Unable to create database connection", e);
		}
	}

	@Override
	protected SqlDialect getDialect() {
		return sqlDialect;
	}

	private void checkTable(String tableName, ColumnOptions[] options) throws SQLException {
		LOGGER.debug("Checking table " + tableName);
		DatabaseMetaData databaseMetaData = getConnection().getMetaData();
		try (ResultSet tables = databaseMetaData.getTables(null, null, tableName, new String[] { "TABLE" })) {
			List<Object> tableList = getList(tables, rs -> rs.getString("TABLE_NAME"));
			if (tableList.isEmpty()) {
				// Table does not exist. we need to create it
				LOGGER.info("Creating table " + tableName);
				String sql = getDialect().createTable(tableName, options);
				executeUpdate(sql);
			} else {
				List<Map<String, Object>> columnProperties;
				try (ResultSet columns = databaseMetaData.getColumns(null, null, tableName, "%")) {
					columnProperties = load(columns,
							rs -> readColumnValues(rs,
									new String[] { "COLUMN_NAME", "TYPE_NAME", "COLUMN_SIZE", "IS_NULLABLE",
											"IS_AUTOINCREMENT" }));
				}
				for (ColumnOptions option : options) {
					Optional<Map<String, Object>> foundColumnProperties = columnProperties.stream()
							.filter(cp -> option.name.equals(cp.get("COLUMN_NAME")))
							.findFirst();
					if (!foundColumnProperties.isPresent()) {
						LOGGER.info("Altering table " + tableName + ". Adding column " + option.name);
						String sql = getDialect().alterTableAddColumn(tableName, option);
						executeUpdate(sql);
					} else {
						columnProperties.remove(foundColumnProperties.get());
					}
				}
				List<String> columnsToDrop = columnProperties.stream()
						.map(m -> (String) m.get("COLUMN_NAME"))
						.collect(Collectors.toList());
				for (String columnToDrop : columnsToDrop) {
					LOGGER.info("Altering table " + tableName + ". Dropping column " + columnToDrop);
					String sql = getDialect().alterTableDropColumn(tableName, columnToDrop);
					executeUpdate(sql);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to check table: " + tableName, e);
		}
	}

	@Override
	public void close() throws IOException {
		try {
			connection.close();
			LOGGER.info("Database connection closed.");
		} catch (SQLException e) {
			throw new IOException("Unable to close dastabase", e);
		}
	}

	@Override
	protected Connection getConnection() {
		return connection;
	}

	@Override
	public List<String> getEventSourceNames() {
		return load(TABLE_EVENT_SOURCE, "NAME", null, null);
	}

	@Override
	public Collection<String> getEventSourcesIds() {
		return load(TABLE_EVENT_SOURCE, "ID", null, null);
	}

	@Override
	public EventSourceInstance loadEventSourceInstance(String eventSourceId,
			BiFunction<String, String, EventSourceDescriptor> descriptorSupplier) {
		Map<String, Object> infos = load(TABLE_EVENT_SOURCE,
				"*",
				"ID",
				eventSourceId,
				rs -> readColumnValues(rs, COLUMNS_EVENT_SOURCE)).get(0);

		String id = (String) infos.get("ID");
		String view = (String) infos.get("VIEW");
		String name = (String) infos.get("NAME");
		boolean enabled = is(infos.get("ENABLED"));
		String className = (String) infos.get("EVENT_SOURCE_CLASS");
		Duration frequency = Optional.ofNullable((Number) infos.get("FREQUENCY"))
				.map(Number::longValue)
				.map(Duration::ofSeconds)
				.orElse(null);
		String createdBy = (String) infos.get("CREATED_BY");
		String lastChangeBy = (String) infos.get("LAST_CHANGE_BY");

		return new EventSourceInstance(descriptorSupplier.apply(className,
				view), id, name, enabled, frequency, createdBy, lastChangeBy);
	}

	@Override
	public Map<String, String> getEventSourceInstanceProperties(String id) {
		return getEventSourceInstance(id, TYPE_PROPERTY);
	}

	@Override
	public Map<String, String> getEventSourceInstanceDataItems(String id) {
		return getEventSourceInstance(id, TYPE_DATA);
	}

	private Map<String, String> getEventSourceInstance(String id, String type) {
		return load(TABLE_EVENT_SOURCE_PROPERTY,
				"*",
				"EVENT_SOURCE_ID",
				id,
				"TYPE",
				type,
				rs -> readColumnValues(rs, new String[] { "NAME", "VALUE" }))//
						.stream()
						.collect(Collectors.toMap(m -> (String) m.get("NAME"), m -> (String) m.get("VALUE")));
	}

	@Override
	public void persistEventSourceInstance(EventSourceInstance instance) {
		Map<String, Object> values = map("NAME",
				instance.getName(), //
				"ENABLED",
				instance.isEnabled(), //
				"EVENT_SOURCE_CLASS",
				instance.getDescriptor().getServiceClass() != null
						? instance.getDescriptor().getServiceClass().getName()
						: null, //
				"FREQUENCY",
				instance.getFrequency(), //
				"VIEW",
				instance.getDescriptor().getView(), //
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
		List<Map<String, Object>> existingProperties = load(TABLE_EVENT_SOURCE_PROPERTY,
				"*",
				"EVENT_SOURCE_ID",
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

	private void createOrUpdateEventSourceProperties(EventSourceInstance instance,
			Stream<EventSourceProperty> propertyStream,
			String type,
			List<Map<String, Object>> existingProperties) {
		createOrUpdateEventSourceProperties(instance,
				propertyStream.collect(Collectors.toMap(Function.identity(),
						p -> Strings.nullToEmpty(instance.getPropertyValueString(p)))),
				existingProperties.stream()
						.filter(m -> type.equals(m.get("TYPE")))
						.map(m -> (String) m.get("NAME"))
						.collect(Collectors.toSet()));
	}

	private void createOrUpdateEventSourceProperties(EventSourceInstance instance,
			Map<EventSourceProperty, String> properties,
			Set<String> existingNames) {
		for (Entry<EventSourceProperty, String> e : properties.entrySet()) {
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
						null,
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
	public void deleteEventSourceInstance(String eventSourceId) {
		delete(TABLE_EVENT_SOURCE, "ID", eventSourceId);
		delete(TABLE_EVENT_SOURCE_PROPERTY, "EVENT_SOURCE_ID", eventSourceId);
	}

	@Override
	public void updateEventSourceClasses(String oldClassName, String newClassName) {
		update(TABLE_EVENT_SOURCE, map("EVENT_SOURCE_CLASS", oldClassName), map("EVENT_SOURCE_CLASS", newClassName));
	}

	@Override
	public List<String> getDashboardIds() {
		return load(TABLE_DASHBOARD, "ID", null, null);
	}

	@Override
	public void persistDashboard(Dashboard dashboard) {
		Map<String, Object> values = map("TITLE",
				dashboard.getTitle(), //
				"WIDTH",
				dashboard.getScreenWidth(), //
				"HEIGHT",
				dashboard.getScreenHeight(), //
				"COLUMNS",
				dashboard.getColumns(), //
				"BACKGROUND_COLOR",
				dashboard.getBackgroundColor().getHexColor(), //
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
		return load(TABLE_DASHBOARD, "*", null, null, rs -> readColumnValues(rs, COLUMNS_DASHBOARD))//
				.stream()
				.map(this::loadDashboard)
				.collect(Collectors.toList());
	}

	@Override
	public Dashboard loadDashboard(String id) {
		List<Map<String, Object>> maybeData = load(TABLE_DASHBOARD,
				"*",
				"ID",
				id,
				rs -> readColumnValues(rs, COLUMNS_DASHBOARD));

		if (!maybeData.isEmpty()) {
			return loadDashboard(maybeData.get(0));
		} else {
			return null;
		}
	}

	public Dashboard loadDashboard(Map<String, Object> data) {
		String id = (String) data.get("ID");
		String title = (String) data.get("TITLE");
		boolean enabled = is(data.get("ENABLED"));
		int screenWidth = (int) data.get("WIDTH");
		int screenHeight = (int) data.get("HEIGHT");
		int columns = (int) data.get("COLUMNS");
		Color backgroundColor = Color.parse((String) data.get("BACKGROUND_COLOR"));
		DashboardColorScheme colorScheme = DashboardColorScheme.fromString((String) data.get("COLOR_SCHEME"));
		Color foregroundColorStart = Color.parse((String) data.get("FOREGROUND_COLOR_START"));
		Color foregroundColorEnd = Color.parse((String) data.get("FOREGROUND_COLOR_END"));
		Collection<String> owners = list((String) data.get("OWNER"));
		Collection<String> editors = list((String) data.get("EDITOR"));

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
	public void createWidget(Dashboard dashboard, Widget widget) {
		int id = insert(TABLE_WIDGET,
				RETRIEVE_ID,
				"DASHBOARD_ID",
				dashboard.getId(), //
				"EVENT_SOURCE_ID",
				widget.getEventSourceInstance().getId(), //
				"TITLE",
				widget.getTitle(), //
				"NAME",
				widget.getName(), //
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
				now());
		widget.setId(id);
		createOrUpdateWidgetProperties(widget);
	}

	private void deleteWidgetDatas(Widget widget) {
		String sql = getDialect().delete(TABLE_WIDGET_DATA,
				Arrays.asList("WIDGET_ID"),
				"NAME",
				true,
				widget.getEventSourceInstance().getDescriptor().getDataItems().size());
		List<Object> items = new ArrayList<>();
		items.add(widget.getId());
		items.addAll(widget.getEventSourceInstance()
				.getDescriptor()
				.getDataItems()
				.stream()
				.map(EventSourceProperty::getName)
				.collect(Collectors.toList()));
		try {
			int amount = executeUpdate(sql, items);
			LOGGER.info("Deleted " + amount + " data rows.");
		} catch (SQLException e) {
			throw new RuntimeException("Unable to delete unnesseccary widget data items", e);
		}
	}

	private void createOrUpdateWidgetProperties(Widget widget) {
		// determine values to persist
		Map<String, String> values = widget//
				.getEventSourceInstance()
				.getDescriptor()
				.getDataItems()
				.stream()
				.filter(p -> !widget.getEventSourceInstance().hasPropertyValue(p))
				.collect(Collectors.toMap(EventSourceProperty::getName,
						p -> Strings.nullToEmpty(widget.getWidgetData(p))));

		// find names of already existing values
		deleteWidgetDatas(widget);
		List<String> exisitingNames = load(TABLE_WIDGET_DATA, "NAME", "WIDGET_ID", widget.getId());
		// TODO we can omit the "OrUpdate"
		createOrUpdateWidgetProperties(widget.getId(), values, exisitingNames);
	}

	@Override
	public List<Widget> loadWidgetDatas(Dashboard dashboard, Function<String, EventSourceInstance> instanceProvider) {
		List<Map<String, Object>> widgetInfoList = load(TABLE_WIDGET,
				"*",
				"DASHBOARD_ID",
				dashboard.getId(),
				rs -> readColumnValues(rs, COLUMNS_WIDGET));
		List<Map<String, Object>> widgetDataMapList = loadValues(TABLE_WIDGET_DATA,
				"*",
				"WIDGET_ID",
				widgetInfoList.stream().map(m -> (Integer) m.get("ID")).collect(Collectors.toSet()),
				COLUMNS_WIDGET_DATA);

		List<Widget> widgets = widgetInfoList.stream()
				.map(m -> loadWidget(dashboard, m, widgetDataMapList, instanceProvider))
				.collect(Collectors.toList());
		return widgets;
	}

	private Widget loadWidget(Dashboard dashboard,
			Map<String, Object> map,
			List<Map<String, Object>> widgetDataMapList,
			Function<String, EventSourceInstance> instanceProvider) {
		int id = (int) map.get("ID");
		EventSourceInstance eventSource = instanceProvider.apply((String) map.get("EVENT_SOURCE_ID"));
		String title = (String) map.get("TITLE");
		String name = (String) map.get("NAME");
		boolean enabled = is(map.get("ENABLED"));
		int posX = (int) map.get("POS_X");
		int posY = (int) map.get("POS_Y");
		int sizeX = (int) map.get("SIZE_X");
		int sizeY = (int) map.get("SIZE_Y");
		Color backgroundColor = Color.parse((String) map.get("BACKGROUND_COLOR"));
		String style = (String) map.get("STYLE");

		Widget widget = new Widget(id,
				eventSource,
				title,
				name,
				enabled,
				posX,
				posY,
				sizeX,
				sizeY,
				backgroundColor,
				style);

		Map<String, String> datas = widgetDataMapList.stream()//
				.filter(d -> (int) d.get("WIDGET_ID") == widget.getId())//
				.collect(Collectors.toMap(d -> (String) d.get("NAME"), d -> (String) d.get("VALUE")));

		eventSource.getDescriptor()//
				.getDataItems()
				.stream()
				.filter(p -> datas.containsKey(p.getName()))
				.filter(p -> !Strings.isNullOrEmpty(datas.get(p.getName())))
				.forEach(p -> widget.setWidgetData(p, datas.get(p.getName())));

		return widget;
	}

	@Override
	public List<String> getOwnedDashboardIds(String userid) {
		return loadWithOneLike(TABLE_DASHBOARD,
				"ID",
				new String[] { "ENABLED" },
				new Object[] { true },
				"OWNER",
				"%," + userid + ",%");
	}

	@Override
	public List<String> getEditableDashboardIds(String userid) {
		return loadWithOneLike(TABLE_DASHBOARD,
				"ID",
				new String[] { "ENABLED" },
				new Object[] { true },
				"EDITOR",
				"%," + userid + ",%");
	}

	@Override
	public boolean deleteDashboard(Dashboard dashboard) {
		dashboard.getWidgets().stream().forEach(this::deleteWidget);
		return delete(TABLE_DASHBOARD, "ID", dashboard.getId()) == 1;
	}

	@Override
	public void updateWidget(Widget widget) {
		updateByOneKey(TABLE_WIDGET,
				"ID",
				widget.getId(), //
				"TITLE",
				widget.getTitle(), //
				"NAME",
				widget.getName(), //
				"ENABLED",
				widget.isEnabled(), //
				"POS_X",
				widget.getPosX(), //
				"POS_Y",
				widget.getPosY(), //
				"SIZE_X",
				widget.getSizeX(), //
				"SIZE_Y",
				widget.getSizeY(), //
				"BACKGROUND_COLOR",
				widget.getBackgroundColor().getHexColor(), //
				"STYLE",
				widget.getStyle()//
		);

		createOrUpdateWidgetProperties(widget);
	}

	private void createOrUpdateWidgetProperties(int widgetId,
			Map<String, String> entries,
			Collection<String> exisitingNames) {
		for (Entry<String, String> e : entries.entrySet()) {
			if (exisitingNames.contains(e.getKey())) {
				// update
				updateByTwoKey(TABLE_WIDGET_DATA, "WIDGET_ID", widgetId, "NAME", e.getKey(), "VALUE", e.getValue());
			} else {
				// create
				insert(TABLE_WIDGET_DATA, null, "WIDGET_ID", widgetId, "NAME", e.getKey(), "VALUE", e.getValue());
			}
		}
	}

	@Override
	public void deleteWidget(Widget widget) {
		deleteWidget(widget.getId());
	}

	private void deleteWidget(int id) {
		delete(TABLE_WIDGET_DATA, "WIDGET_ID", id);
		delete(TABLE_WIDGET, "ID", id);
	}

	@Override
	public <T extends SerializableProperty> Optional<T> getProperty(Class<T> clazz, int id) {
		List<Map<String, Object>> credentialsMap = load(TABLE_VALUES,
				"*",
				"ID",
				id,
				// XXX Column CLASS should be checked, too
				rs -> readColumnValues(rs, COLUMNS_VALUES));
		return credentialsMap.stream().findFirst().map(m -> mapToValue(clazz, m));
	}

	private <T extends SerializableProperty> T mapToValue(Class<T> clazz, Map<String, Object> map) {
		return json.fromJson((String) map.get("JSON"), clazz);
	}

	@Override
	public <T extends SerializableProperty> List<T> listProperties(Class<T> clazz) {
		return load(TABLE_VALUES, "*", "CLASS", clazz.getName(), rs -> readColumnValues(rs, COLUMNS_VALUES))//
				.stream()//
				.map(m -> mapToValue(clazz, m))//
				.sorted()//
				.collect(toList());
	}

	@Override
	public void updateProperty(SerializableProperty value) {
		update(TABLE_VALUES,
				map("ID", value.getId(), "CLASS", value.getClass().getName()),
				map("NAME", value.getName(), "JSON", json.toJson(value)));
	}

	@Override
	public void deleteProperty(Class<? extends SerializableProperty> clazz, int id) {
		delete(TABLE_VALUES, "ID", id, "CLASS", clazz.getName());
	}

	@Override
	public <T extends SerializableProperty> T createProperty(Class<T> clazz, String name, Object... parameters) {
		int id = insert(TABLE_VALUES, RETRIEVE_ID, "CLASS", clazz.getName(), "NAME", name, "JSON", "{}");
		Object[] params = new Object[2 + parameters.length];
		params[0] = id;
		params[1] = name;
		System.arraycopy(parameters, 0, params, 2, parameters.length);
		T value;
		try {
			value = TypeUtils.getAppropriateConstructor(clazz, params).newInstance(params);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException(
					"Class " + clazz.getName() + " does not contain a constructor to be used in the database class",
					e);
		}
		updateProperty(value);
		return value;
	}

	private <T, I> void persist(String table,
			String idColumn,
			T item,
			Function<T, I> getId,
			Map<String, Object> values) {
		persist(table, idColumn, item, getId, values, null);
	}

	private <T, I> void persist(String table,
			String idColumn,
			T item,
			Function<T, I> getId,
			Map<String, Object> values,
			Map<String, Object> creationValues) {
		List<String> found = load(table, idColumn, idColumn, getId.apply(item), rs -> rs.getString(idColumn));
		if (found.isEmpty()) {
			values.put(idColumn, getId.apply(item));
			if (creationValues != null) {
				values.putAll(creationValues);
			}
			insert(table, null, values);
		} else if (found.size() == 1) {
			// update existing entry
			update(table, map(idColumn, getId.apply(item)), values);
		} else {
			throw new RuntimeException("More than one line found for ID: " + getId.apply(item));
		}
	}
}
