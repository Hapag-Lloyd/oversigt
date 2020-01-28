package com.hlag.oversigt.connect.jira;

/**
 * Describing exceptions occurring while communicating with JIRA servers
 *
 * @author Lars Knickrehm
 */
public class JiraClientException extends Exception {
	private static final long serialVersionUID = 2923278956711719664L;

	/**
	 * Create a new exception with the given message
	 *
	 * @param message description of what has gone wrong
	 */
	JiraClientException(final String message) {
		super(message);
	}

	/**
	 * Create a new exception with the given message and cause
	 *
	 * @param message description of what has gone wrong
	 * @param cause   the actual cause of the problem
	 */
	JiraClientException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
