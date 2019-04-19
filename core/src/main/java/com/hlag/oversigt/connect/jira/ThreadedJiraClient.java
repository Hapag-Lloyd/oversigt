package com.hlag.oversigt.connect.jira;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

public class ThreadedJiraClient extends JiraClientFilter {
	private static final int NUMBER_OF_THREADS = 4;

	private static final int TIMEOUT = 3;

	private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MINUTES;

	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

	public ThreadedJiraClient(final ServerConnection connection, final Credentials credentials)
			throws JiraClientException {
		super(connection, credentials);
	}

	/**
	 * Perform the JIRA search using a separate thread. The executing thread is
	 * handled by an executor service.
	 *
	 * @see com.hlag.oversigt.connect.jira.JiraClient#search(java.lang.String, int,
	 *      int)
	 */
	@Override
	public List<Issue> search(final String jql, final int maxResults, final int startAt) throws JiraClientException {
		try {
			return EXECUTOR.invokeAny(Arrays.asList(() -> getJiraClient().search(jql, maxResults, startAt)),
					TIMEOUT,
					TIMEOUT_UNIT);
		} catch (final InterruptedException | ExecutionException | TimeoutException e) {
			if (e.getCause() != null && e.getCause() instanceof JiraClientException) {
				throw (JiraClientException) e.getCause();
			}
			throw new JiraClientException("Unable to execute JIRA query", e);
		}
	}
}
