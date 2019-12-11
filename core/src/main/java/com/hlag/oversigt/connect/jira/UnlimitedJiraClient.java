package com.hlag.oversigt.connect.jira;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.common.collect.Lists;
import com.hlag.oversigt.connect.jira.config.JiraConfigurationProvider;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

/**
 * JIRA client with no restrictions. This class is not intended to be used
 * directly by consuming code. This class should always be an invisible
 * implementation detail.
 */
final class UnlimitedJiraClient implements JiraClient {
	private final ServerConnection connection;

	private final Credentials credentials;

	private Optional<JiraRestClient> jiraClient;

	/**
	 * Create a new JIRA client without limitations. This constructor should NOT be
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
		jiraClient = Optional.empty();
	}

	private JiraRestClient getJiraRestClient() throws JiraClientException {
		if (!jiraClient.isPresent()) {
			try {
				jiraClient = Optional.of(JiraConfigurationProvider.createClientFactory()
						.createWithBasicHttpAuthentication(new URI(connection.getUrl()),
								credentials.getUsername(),
								credentials.getPassword()));
			} catch (final URISyntaxException e) {
				throw new JiraClientException("Jira URI is invalid.", e);
			}
		}
		return jiraClient.get();
	}

	private void resetJiraRestClient() {
		try {
			if (jiraClient.isPresent()) {
				jiraClient.get().close();
			}
		} catch (@SuppressWarnings("unused") final IOException ignore) {
			// ignore exception while closing the connection
		}
		jiraClient = Optional.empty();
	}

	/** {@inheritDoc} */
	@Override
	public List<Issue> search(final String jql, final int maxResults, final int startAt) throws JiraClientException {
		try {
			return Lists.newArrayList(//
					getJiraRestClient()//
							.getSearchClient()
							.searchJql(jql, maxResults, startAt, null)
							.get()
							.getIssues());
		} catch (final Exception e) {
			resetJiraRestClient();
			throw new JiraClientException("Failed searching Jira.", e);
		}
	}
}
