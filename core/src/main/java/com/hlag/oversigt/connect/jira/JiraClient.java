package com.hlag.oversigt.connect.jira;

import java.util.List;

import com.atlassian.jira.rest.client.api.domain.Issue;

public interface JiraClient {

	public static final int MAX_RESULTS_DEFAULT = 500;

	default List<Issue> search(String jql) throws JiraClientException {
		return search(jql, MAX_RESULTS_DEFAULT, 0);
	}

	List<Issue> search(String jql, int maxResults, int startAt) throws JiraClientException;
}
