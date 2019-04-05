package com.hlag.oversigt.connect.jira;

import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

/**
 * Filter allowing extending the {@link JiraClient} with needing to implement
 * details.
 *
 * @author neumaol
 */
abstract class JiraClientFilter implements JiraClient {

	private final JiraClient jiraClient;

	protected JiraClientFilter(final ServerConnection connection, final Credentials credentials)
			throws JiraClientException {
		jiraClient = UnlimitedJiraClient.getInstance(connection, credentials);
	}

	protected JiraClientFilter(final JiraClient jiraClient) {
		this.jiraClient = jiraClient;
	}

	/**
	 * Return the actual {@link JiraClient} that is used to perform
	 *
	 * @return the {@link JiraClient} used to perform Jira queries
	 */
	protected JiraClient getJiraClient() {
		return jiraClient;
	}
}
