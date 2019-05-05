package com.hlag.oversigt.connect.jira;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

/**
 * Factory for creating {@link JiraClient}s without knowing the implementation
 *
 * @author Olaf Neumann
 *
 */
public class JiraClientFactory {
	private JiraClientFactory() {
		throw new RuntimeException("Do not instantiate this class.");
	}

	/**
	 * Create a new {@link JiraClient} for the given parameters.
	 *
	 * @param connection  the server details to be used to create the server
	 *                    connection
	 * @param credentials the credentials to be used for authenticating the JIRA
	 *                    requests
	 * @return
	 * @throws JiraClientException in case of error while creating the client
	 */
	public static JiraClient createJiraClient(final ServerConnection connection, final Credentials credentials)
			throws JiraClientException {
		return new ThreadedJiraClient(connection, credentials);
	}

	private static final Table<String, String, UnlimitedJiraClient> CLIENT_CACHE = HashBasedTable.create();

	/**
	 * Create a new jira client for the givon connection and credentials. Only one
	 * client per connection and credentials will be created and then cached.
	 *
	 * @param connection  where to connect the jira client to
	 * @param credentials the credentials to use for the jira connection
	 * @return the newly create jira client
	 * @throws JiraClientException if something fails while creating the connection
	 */
	static synchronized JiraClient createUnlimitedJiraClient(final ServerConnection connection,
			final Credentials credentials) throws JiraClientException {
		if (!CLIENT_CACHE.contains(connection.getUrl(), credentials.getUsername())) {
			CLIENT_CACHE.put(connection.getUrl(),
					credentials.getUsername(),
					new UnlimitedJiraClient(connection, credentials));
		}
		return CLIENT_CACHE.get(connection.getUrl(), credentials.getUsername());
	}
}
