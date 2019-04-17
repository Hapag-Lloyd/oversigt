package com.hlag.oversigt.connect.jira;

import java.util.List;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.common.util.concurrent.RateLimiter;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

import io.atlassian.util.concurrent.atomic.AtomicInteger;

/**
 * JIRA client that limits the number of simultaneous calls.
 *
 * @author neumaol
 * @deprecated Use {@link ThreadedJiraClient} instead
 */
@Deprecated
class LimitedJiraClient extends JiraClientFilter {
	private static final RateLimiter RATE_LIMITER = RateLimiter.create(1.0);

	private static final AtomicInteger runningSearches = new AtomicInteger(0);

	public LimitedJiraClient(final ServerConnection connection, final Credentials credentials)
			throws JiraClientException {
		super(connection, credentials);
	}

	/**
	 * Perform the Jira search but limit the number of calls per second to the JIRA server. The
	 * limit is dynamically adjusted with every incoming request
	 *
	 * @see com.hlag.oversigt.connect.jira.JiraClient#search(java.lang.String, int, int)
	 */
	@Override
	public List<Issue> search(String jql, int maxResults, int startAt) throws JiraClientException {
		// change the number of allowed searches
		int limit = runningSearches.incrementAndGet();
		RATE_LIMITER.setRate(limit);

		// wait until we may proceed
		RATE_LIMITER.acquire();

		// let the JIRA client do its job
		try {
			return getJiraClient().search(jql, maxResults, startAt);
		} finally {
			runningSearches.decrementAndGet();
		}
	}
}
