package com.hlag.oversigt.properties;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * This class stores the credentials for a given use case. Credentials will be stored separately within the
 * dashboard and can be changed without the need to change all event source configurations.
 * <p>
 * This class is also useful because when configuring an event source you don't need to know passwords
 * because they can be entered somewhere else.
 *
 * @author neumaol
 *
 */
public class Credentials extends SerializableProperty {
	public static final Credentials EMPTY = new Credentials(0, "", "", "", "");

	@Member(icon = "user", size = 3, mayBeEmpty = true)
	private String username;
	@Member(icon = "lock", size = 3)
	private String password;
	@Member(icon = "cloud", size = 2, mayBeEmpty = true)
	private String domain;

	public Credentials(int id, String name, String username, String password, String domain) {
		super(id, name);
		this.domain = domain;
		this.username = username;
		this.password = password;
	}

	/**The domain of these credentials
	 * @return The domain of these credentials
	 */
	public String getDomain() {
		return domain;
	}

	/**The password belonging to these credentials
	 * @return The password belonging to these credentials
	 */
	public String getPassword() {
		return password;
	}

	/**The username belonging to these credentials
	 * @return The username belonging to these credentials
	 */
	public String getUsername() {
		return username;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(new Credentials(getId(), getName(), username, "********", domain),
				ToStringStyle.JSON_STYLE);
	}
}
