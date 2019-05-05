package com.hlag.oversigt.connect.jira;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;
import com.hlag.oversigt.util.CountingThreadFactory;
import com.hlag.oversigt.util.GrowingExecutor;

/**
 * A jira client using multiple threads to execute jira queries simultaneously.
 * The number of threads changes dynamically depending on the current workload.
 *
 * @author Olaf Neumann
 *
 */
class ThreadedJiraClient extends JiraClientFilter {
	private static final int TIMEOUT = 3;

	private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MINUTES;

	private static final GrowingExecutor EXECUTOR = new GrowingExecutor(1,
			50,
			Duration.ofSeconds(2),
			Duration.ofMinutes(4),
			CountingThreadFactory.createDaemonThreadFactory("ThreadJiraClient-executor-"));

	/**
	 * Create a new threaded jira client
	 *
	 * @param connection  the server where to connect to
	 * @param credentials the credentials to be used
	 * @throws JiraClientException if something fails while creating the client
	 */
	ThreadedJiraClient(final ServerConnection connection, final Credentials credentials) throws JiraClientException {
		super(JiraClientFactory.createUnlimitedJiraClient(connection, credentials));
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
			final Callable<List<Issue>> callable = () -> getJiraClient().search(jql, maxResults, startAt);
			return EXECUTOR.execute(callable).get(TIMEOUT, TIMEOUT_UNIT);
		} catch (final InterruptedException | ExecutionException | TimeoutException e) {
			if (e.getCause() != null && e.getCause() instanceof JiraClientException) {
				throw (JiraClientException) e.getCause();
			}
			throw new JiraClientException("Unable to execute JIRA query", e);
		}
	}
}
