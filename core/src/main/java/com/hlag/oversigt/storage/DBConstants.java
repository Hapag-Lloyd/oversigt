package com.hlag.oversigt.storage;

import static java.util.stream.Collectors.toList;

import java.util.stream.Stream;

import com.hlag.oversigt.storage.SqlDialect.ColumnOptions;
import com.hlag.oversigt.storage.SqlDialect.ColumnType;

interface DBConstants {
	String TABLE_EVENT_SOURCE = "EVENT_SOURCE";

	String TABLE_EVENT_SOURCE_PROPERTY = "EVENT_SOURCE_PROPERTY";

	String TABLE_DASHBOARD = "DASHBOARD";

	String TABLE_WIDGET = "WIDGET";

	String TABLE_WIDGET_DATA = "WIDGET_DATA";

	String TABLE_VALUES = "OVERSIGT_PROPERTY";

	ColumnOptions[] COLUMN_OPTIONS_EVENT_SOURCE = new ColumnOptions[] {
			new ColumnOptions("ID", ColumnType.Text, null, false, true),
			new ColumnOptions("NAME", ColumnType.Text, null, false, false),
			new ColumnOptions("ENABLED", ColumnType.Boolean, true, false, false),
			new ColumnOptions("EVENT_SOURCE_CLASS", ColumnType.Text, null, true, false),
			new ColumnOptions("FREQUENCY", ColumnType.BigInteger, 60, true, false),
			new ColumnOptions("VIEW", ColumnType.Text, "", false, false),
			new ColumnOptions("CREATED_ON", ColumnType.Timestamp, null, false, false),
			new ColumnOptions("CREATED_BY", ColumnType.Text, "", false, false),
			new ColumnOptions("LAST_CHANGE", ColumnType.Timestamp, null, false, false),
			new ColumnOptions("LAST_CHANGE_BY", ColumnType.Text, "", false, false) };

	ColumnOptions[] COLUMN_OPTIONS_EVENT_SOURCE_PROPERTY = new ColumnOptions[] {
			new ColumnOptions("EVENT_SOURCE_ID", ColumnType.Text, null, false, true),
			new ColumnOptions("TYPE", ColumnType.Text, null, false, true),
			new ColumnOptions("NAME", ColumnType.Text, null, false, true),
			new ColumnOptions("VALUE", ColumnType.Text, null, false, false) };

	ColumnOptions[] COLUMN_OPTIONS_DASHBOARD = new ColumnOptions[] {
			new ColumnOptions("ID", ColumnType.Text, null, false, true),
			new ColumnOptions("TITLE", ColumnType.Text, null, false, false),
			new ColumnOptions("WIDTH", ColumnType.Integer, 1920, false, false),
			new ColumnOptions("HEIGHT", ColumnType.Integer, 1080, false, false),
			new ColumnOptions("COLUMNS", ColumnType.Integer, 15, false, false),
			new ColumnOptions("BACKGROUND_COLOR", ColumnType.Text, "#222222", false, false),
			new ColumnOptions("COLOR_SCHEME", ColumnType.Text, "default", false, false),
			new ColumnOptions("FOREGROUND_COLOR_START", ColumnType.Text, "#888888", false, false),
			new ColumnOptions("FOREGROUND_COLOR_END", ColumnType.Text, "#AAAAAA", false, false),
			new ColumnOptions("OWNER", ColumnType.Text, "", false, false),
			new ColumnOptions("EDITOR", ColumnType.Text, "", false, false),
			new ColumnOptions("ENABLED", ColumnType.Boolean, true, false, false),
			new ColumnOptions("LAST_CHANGE", ColumnType.Timestamp, null, false, false) };

	ColumnOptions[] COLUMN_OPTIONS_WIDGET = new ColumnOptions[] {
			new ColumnOptions("ID", ColumnType.Integer, null, false, true, true, false),
			new ColumnOptions("DASHBOARD_ID", ColumnType.Text, null, false, false),
			new ColumnOptions("EVENT_SOURCE_ID", ColumnType.Text, null, false, false),
			new ColumnOptions("TITLE", ColumnType.Text, null, false, false),
			new ColumnOptions("NAME", ColumnType.Text, null, false, false),
			new ColumnOptions("ENABLED", ColumnType.Boolean, true, false, false),
			new ColumnOptions("POS_X", ColumnType.Integer, 1, false, false), // 1
			new ColumnOptions("POS_Y", ColumnType.Integer, 1, false, false), // 1
			new ColumnOptions("SIZE_X", ColumnType.Integer, 3, false, false), // 3
			new ColumnOptions("SIZE_Y", ColumnType.Integer, 3, false, false), // 3
			new ColumnOptions("BACKGROUND_COLOR", ColumnType.Text, "#999", false, false),
			new ColumnOptions("STYLE", ColumnType.Text, "", false, false),
			new ColumnOptions("LAST_CHANGE", ColumnType.Timestamp, null, false, false) };

	ColumnOptions[] COLUMN_OPTIONS_WIDGET_DATA = new ColumnOptions[] {
			new ColumnOptions("WIDGET_ID", ColumnType.Integer, null, false, true),
			new ColumnOptions("NAME", ColumnType.Text, null, false, true),
			new ColumnOptions("VALUE", ColumnType.Text, null, false, false) };

	ColumnOptions[] COLUMN_OPTIONS_VALUES = new ColumnOptions[] {
			new ColumnOptions("ID", ColumnType.Integer, null, false, true, true, true),
			new ColumnOptions("CLASS", ColumnType.Text, null, false, false, false, true),
			new ColumnOptions("NAME", ColumnType.Text, null, false, false),
			new ColumnOptions("JSON", ColumnType.Text, null, false, false) };

	String[] COLUMNS_EVENT_SOURCE = Stream.of(COLUMN_OPTIONS_EVENT_SOURCE)
			.map(ColumnOptions::getName)
			.collect(toList())
			.toArray(new String[0]);

	String[] COLUMNS_EVENT_SOURCE_PROPERTY = Stream.of(COLUMN_OPTIONS_EVENT_SOURCE_PROPERTY)
			.map(ColumnOptions::getName)
			.collect(toList())
			.toArray(new String[0]);

	String[] COLUMNS_DASHBOARD
			= Stream.of(COLUMN_OPTIONS_DASHBOARD).map(ColumnOptions::getName).collect(toList()).toArray(new String[0]);

	String[] COLUMNS_WIDGET
			= Stream.of(COLUMN_OPTIONS_WIDGET).map(ColumnOptions::getName).collect(toList()).toArray(new String[0]);

	String[] COLUMNS_WIDGET_DATA = Stream.of(COLUMN_OPTIONS_WIDGET_DATA)
			.map(ColumnOptions::getName)
			.collect(toList())
			.toArray(new String[0]);

	String[] COLUMNS_VALUES
			= Stream.of(COLUMN_OPTIONS_VALUES).map(ColumnOptions::getName).collect(toList()).toArray(new String[0]);
}
