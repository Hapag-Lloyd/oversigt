package com.hlag.oversigt.connect.jira;

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
}
