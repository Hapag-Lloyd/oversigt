package com.hlag.oversigt.connect.jira;

import java.util.List;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

/**
 * Jira client implementation that allows one query at a time only
 *
 * @author knickla
 * @deprecated Use {@link LimitedJiraClient} instead
 */
@Deprecated
class SynchronizedJiraClient extends JiraClientFilter {

	private SynchronizedJiraClient(JiraClient jiraClient) {
		super(jiraClient);
	}

	public SynchronizedJiraClient(ServerConnection connection, Credentials credentials) throws JiraClientException {
		super(connection, credentials);
	}

	/**
	 * Perform the jira search but only allow one search per time. Calls to this method while
	 * another search is performed will be blocked until the first search has finished.
	 *
	 * @see com.hlag.oversigt.connect.jira.JiraClient#search(java.lang.String, int, int)
	 */
	@Override
	public synchronized List<Issue> search(String jql, int maxResults, int startAt) throws JiraClientException {
		return getJiraClient().search(jql, maxResults, startAt);
	}
}
