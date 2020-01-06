package com.hlag.oversigt.properties;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hlag.oversigt.properties.SerializableProperty.Description;

@Description("A connection to a database. This defines which JDBC driver will be used and where the database is located.")
public class DatabaseConnection extends SerializableProperty {
	public static final DatabaseConnection EMPTY = new DatabaseConnection(0, "", "", "");

	@Member(icon = "file", size = 4)
	private String driverClassName;

	@Member(icon = "cloud", size = 4)
	private String jdbcUrl;

	@JsonCreator
	public DatabaseConnection(@JsonProperty("id") final int id,
			@JsonProperty("name") final String name,
			@JsonProperty("driverClassName") final String driverClassName,
			@JsonProperty("jdbcUrl") final String jdbcUrl) {
		super(id, name);
		this.driverClassName = driverClassName;
		this.jdbcUrl = jdbcUrl;
	}

	public String getDriverClassName() {
		return driverClassName;
	}

	public Class<?> loadDriverClass() throws ClassNotFoundException {
		return Class.forName(getDriverClassName());
	}

	public String getJdbcUrl() {
		return jdbcUrl;
	}
}
