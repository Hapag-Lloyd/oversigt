package com.hlag.oversigt.connect.jira;

import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

import lombok.experimental.UtilityClass;

@UtilityClass
public class JiraClientFactory {
	public JiraClient createJiraClient(ServerConnection serverConnection, Credentials credentials)
			throws JiraClientException {
		return new ThreadedJiraClient(serverConnection, credentials);
	}
}
