package com.hlag.oversigt.connect.jira.config;

import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.experimental.UtilityClass;

/**
 * Provide jira properties
 *
 * @author neumaol
 */
@UtilityClass
@SuppressWarnings("PMD.ClassNamingConventions")
public final class JiraConfigurationProvider {
	/**
	 * Socket Timeout
	 *
	 * <p>
	 * The value of this field will be injected using Google Guice and is named
	 * {@code jiraSocketTimeout}.
	 */
	@Inject
	@Named("jiraSocketTimeout")
	@SuppressWarnings("checkstyle:MagicNumber")
	private static int socketTimeout = 60;

	/**
	 * Return the timeout for a jira request
	 *
	 * @return the timeout for a jira request
	 */
	@SuppressFBWarnings(value = "MRC_METHOD_RETURNS_CONSTANT", justification = "socketTimeout is not really a constant")
	public static int getSocketTimeout() {
		return socketTimeout;
	}

	/**
	 * Create a new {@link JiraRestClientFactory}
	 *
	 * @return a new instanceof {@link JiraRestClientFactory}
	 */
	public static JiraRestClientFactory createClientFactory() {
		return new AsynchronousJiraRestClientFactory();
	}
}
