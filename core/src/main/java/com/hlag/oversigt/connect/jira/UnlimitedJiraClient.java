package com.hlag.oversigt.connect.jira;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

/**
 * Jira client with no restrictions
 */
final class UnlimitedJiraClient implements JiraClient {

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
	static synchronized JiraClient getInstance(final ServerConnection connection, final Credentials credentials)
			throws JiraClientException {
		if (!CLIENT_CACHE.contains(connection.getUrl(), credentials.getUsername())) {
			CLIENT_CACHE.put(connection.getUrl(),
					credentials.getUsername(),
					new UnlimitedJiraClient(connection, credentials));
		}
		return CLIENT_CACHE.get(connection.getUrl(), credentials.getUsername());
	}

	private final ServerConnection connection;

	private final Credentials credentials;

	private volatile JiraRestClient jiraClient;

	private UnlimitedJiraClient(final ServerConnection connection, final Credentials credentials)
			throws JiraClientException {
		if (connection == ServerConnection.EMPTY) {
			throw new JiraClientException("No Jira hostname configured.");
		}
		if (credentials == Credentials.EMPTY) {
			throw new JiraClientException("No Jira credentials configured.");
		}

		this.connection = connection;
		this.credentials = credentials;
		jiraClient = null;
	}

	private JiraRestClient getJiraRestClient() throws JiraClientException {
		if (jiraClient == null) {
			try {
				jiraClient = new AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(
						new URI(connection.getUrl()),
						credentials.getUsername(),
						credentials.getPassword());
			} catch (final URISyntaxException e) {
				throw new JiraClientException("Jira URI is invalid.", e);
			}
		}
		return jiraClient;
	}

	private void resetJiraRestClient() {
		try {
			jiraClient.close();
		} catch (@SuppressWarnings("unused") final Exception ignore) {
			// ignore exception while closing the connection
		}
		jiraClient = null;
	}

	/** {@inheritDoc} */
	@Override
	public List<Issue> search(final String jql, final int maxResults, final int startAt) throws JiraClientException {
		try {
			return Lists.newArrayList(
					getJiraRestClient().getSearchClient().searchJql(jql, maxResults, startAt, null).get().getIssues());
		} catch (final Exception e) {
			resetJiraRestClient();
			throw new JiraClientException("Failed searching Jira.", e);
		}
	}
}
