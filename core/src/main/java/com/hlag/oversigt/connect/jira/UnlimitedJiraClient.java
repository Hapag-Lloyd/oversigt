package com.hlag.oversigt.connect.jira;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.google.common.collect.Lists;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

/**
 * Jira client with no restrictions. This class is not inteded to be used
 * directly by consuming code. This class should always to an invisible
 * implementation detail.
 */
final class UnlimitedJiraClient implements JiraClient {
	private final ServerConnection connection;

	private final Credentials credentials;

	private volatile JiraRestClient jiraClient;

	/**
	 * Create a new jira client without limitations. This constructor should NOT be
	 * called from outside of the package of the declaring class.
	 *
	 * @param connection  the server details to connect to
	 * @param credentials the credentials to be used for the connection
	 * @throws JiraClientException if something fails while creating the connection
	 */
	UnlimitedJiraClient(final ServerConnection connection, final Credentials credentials) throws JiraClientException {
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
