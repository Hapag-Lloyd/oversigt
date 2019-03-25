package com.hlag.oversigt.connect.jira;

import java.util.List;

import com.atlassian.jira.rest.client.api.domain.Issue;

public interface JiraClient {

	public static final int MAX_RESULTS_DEFAULT = 500;

	default List<Issue> search(String jql) throws JiraClientException {
		return search(jql, MAX_RESULTS_DEFAULT, 0);
	}

	/**
	 * Perform the Jira search with the given parameters
	 *
	 * @param jql
	 *            the query to execute
	 * @param maxResults
	 *            the maximum number of results to return
	 * @param startAt
	 *            <em>don't know</em>
	 * @return the list of found issues
	 * @throws JiraClientException
	 *             if something fails
	 */
	List<Issue> search(String jql, int maxResults, int startAt) throws JiraClientException;
}
