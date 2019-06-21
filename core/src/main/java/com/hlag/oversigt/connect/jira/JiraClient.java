package com.hlag.oversigt.connect.jira;

import java.util.List;
import java.util.concurrent.TimeoutException;

import com.atlassian.jira.rest.client.api.domain.Issue;

/**
 * A client enabling consumer code to retrieve information from a JIRA backend
 *
 * @author Lars Knickrehm
 * @author Olaf Neumann
 */
public interface JiraClient {

	/**
	 * The maximum number of issues to return from the JIRA backend
	 */
	int MAX_RESULTS_DEFAULT = 500;

	/**
	 * Search the JIRA backend using the given query
	 *
	 * @param jql the query to execute on the JIRA backend
	 * @return a possibly empty list of issues found in jira
	 * @throws JiraClientException if something fails while searching for issues
	 */
	default List<Issue> search(final String jql) throws JiraClientException, TimeoutException {
		return search(jql, MAX_RESULTS_DEFAULT, 0);
	}

	/**
	 * Perform the Jira search with the given parameters
	 *
	 * @param jql        the query to execute
	 * @param maxResults the maximum number of results to return
	 * @param startAt    <em>don't know</em>
	 * @return the list of found issues
	 * @throws JiraClientException if something fails
	 */
	List<Issue> search(String jql, int maxResults, int startAt) throws JiraClientException, TimeoutException;
}
