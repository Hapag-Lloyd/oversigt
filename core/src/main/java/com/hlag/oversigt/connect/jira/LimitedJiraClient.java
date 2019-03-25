package com.hlag.oversigt.connect.jira;

import java.util.List;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.common.util.concurrent.RateLimiter;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

public class LimitedJiraClient extends JiraClientFilter {
	private static final int ALLOWED_CALLS_PER_SECOND = 1;
	private static final RateLimiter RATE_LIMITER = RateLimiter.create(ALLOWED_CALLS_PER_SECOND);

	public LimitedJiraClient(final ServerConnection connection, final Credentials credentials)
			throws JiraClientException {
		super(connection, credentials);
	}

	/**
	 * Perform the Jira search but limit the number of calls to one per second
	 * 
	 * @see com.hlag.oversigt.connect.jira.JiraClient#search(java.lang.String, int, int)
	 */
	@Override
	public List<Issue> search(String jql, int maxResults, int startAt) throws JiraClientException {
		// wait until we may proceed
		RATE_LIMITER.acquire();

		// let the JIRA client do its job
		return getJiraClient().search(jql, maxResults, startAt);
	}
}
