package com.hlag.oversigt.properties;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hlag.oversigt.properties.SerializableProperty.Description;

/**
 * This class stores the credentials for a given use case. Credentials will be
 * stored separately within the dashboard and can be changed without the need to
 * change all event source configurations.
 *
 * <p>
 * This class is also useful because when configuring an event source you don't
 * need to know passwords because they can be entered somewhere else.
 *
 * @author neumaol
 */
@Description("Store credentials to access a resource using them. The password will be stored in a way that others can use it without being able to read it.")
public class Credentials extends SerializableProperty {
	public static final Credentials EMPTY = new Credentials(0, "", "", "", "");

	@Member(icon = "user", size = 3, mayBeEmpty = true)
	private String username;

	@Member(icon = "lock", size = 3)
	private String password;

	@Member(icon = "cloud", size = 2, mayBeEmpty = true)
	private String domain;

	@JsonCreator
	public Credentials(@JsonProperty("id") final int id,
			@JsonProperty("name") final String name,
			@JsonProperty("username") final String username,
			@JsonProperty("password") final String password,
			@JsonProperty("domain") final String domain) {
		super(id, name);
		this.domain = domain;
		this.username = username;
		this.password = password;
	}

	/**
	 * The domain of these credentials
	 *
	 * @return The domain of these credentials
	 */
	public String getDomain() {
		return domain;
	}

	/**
	 * The password belonging to these credentials
	 *
	 * @return The password belonging to these credentials
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * The username belonging to these credentials
	 *
	 * @return The username belonging to these credentials
	 */
	public String getUsername() {
		return username;
	}

	public void setDomain(final String domain) {
		this.domain = domain;
	}

	public void setPassword(final String password) {
		this.password = password;
	}

	public void setUsername(final String username) {
		this.username = username;
	}
}
