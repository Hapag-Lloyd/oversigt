package com.hlag.oversigt.connect.jira;

/**
 * Filter allowing extending the {@link JiraClient} with needing to implement
 * details.
 *
 * @author neumaol
 */
abstract class JiraClientFilter implements JiraClient {
	private final JiraClient jiraClient;

	/**
	 * Create a jira client wrapping around the given jira client
	 *
	 * @param jiraClient the jira client to wrap
	 */
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
