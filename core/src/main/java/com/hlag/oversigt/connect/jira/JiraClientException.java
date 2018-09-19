package com.hlag.oversigt.connect.jira;

public class JiraClientException extends Exception {
	private static final long serialVersionUID = -5097282801844376304L;

	JiraClientException(String message) {
		super(message);
	}

	JiraClientException(String message, Throwable cause) {
		super(message, cause);
	}
}