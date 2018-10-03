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

public class SynchronizedJiraClient implements JiraClient {

	private static final Table<String, String, SynchronizedJiraClient> CLIENT_CACHE = HashBasedTable.create();

	public synchronized static JiraClient getInstance(final ServerConnection connection, final Credentials credentials)
			throws JiraClientException {
		if (!CLIENT_CACHE.contains(connection.getUrl(), credentials.getUsername())) {
			CLIENT_CACHE.put(connection.getUrl(),
					credentials.getUsername(),
					new SynchronizedJiraClient(connection, credentials));
		}
		return CLIENT_CACHE.get(connection.getUrl(), credentials.getUsername());
	}

	final ServerConnection connection;
	final Credentials credentials;
	volatile JiraRestClient jiraClient;

	private SynchronizedJiraClient(final ServerConnection connection, final Credentials credentials)
			throws JiraClientException {
		if (connection == ServerConnection.EMPTY) {
			throw new JiraClientException("No Jira hostname configured.");
		}
		if (credentials == Credentials.EMPTY) {
			throw new JiraClientException("No Jira credentials configured.");
		}

		this.connection = connection;
		this.credentials = credentials;
		this.jiraClient = null;
	}

	private JiraRestClient getJiraRestClient() throws JiraClientException {
		if (jiraClient == null) {
			try {
				jiraClient = new AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(
						new URI(connection.getUrl()),
						credentials.getUsername(),
						credentials.getPassword());
			} catch (URISyntaxException e) {
				throw new JiraClientException("Jira URI is invalid.", e);
			}
		}
		return jiraClient;
	}

	private void resetJiraRestClient() {
		try {
			jiraClient.close();
		} catch (Exception ignore) {
		}
		jiraClient = null;
	}

	@Override
	public synchronized List<Issue> search(String jql, int maxResults, int startAt) throws JiraClientException {
		try {
			return Lists.newArrayList(
					getJiraRestClient().getSearchClient().searchJql(jql, maxResults, startAt, null).get().getIssues());
		} catch (Exception e) {
			resetJiraRestClient();
			throw new JiraClientException("Failed searching Jira.", e);
		}
	}
}
