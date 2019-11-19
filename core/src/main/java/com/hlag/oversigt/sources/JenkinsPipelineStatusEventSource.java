package com.hlag.oversigt.sources;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.EventSourceException;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;
import com.hlag.oversigt.sources.event.TwoColumnListEvent;
import com.hlag.oversigt.sources.event.TwoColumnListEvent.ListEventItem;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;

@EventSource(view = "List", displayName = "Jenkins Pipeline Status", hiddenDataItems = { "moreinfo" })
public class JenkinsPipelineStatusEventSource extends ScheduledEventSource<TwoColumnListEvent<String>> {

	private Credentials credentials = Credentials.EMPTY;

	private ServerConnection jenkinsUrl = ServerConnection.EMPTY;

	private Pipeline[] pipelines = {};

	@Override
	protected Optional<TwoColumnListEvent<String>> produceEvent() throws EventSourceException {
		final List<ListEventItem<String>> list = new ArrayList<>();

		// Requests pipeline status
		for (final Pipeline pipeline : pipelines) {
			list.add(new ListEventItem<>(pipeline.pipelineName.replace("_", " ").substring(4),
					getJenkinsDataByID(pipeline.pipelineName),
					"color: red;", // TODO: test style
					""));
		}

		// Compare Pipelines
		final Comparator<ListEventItem<String>> compareValue = Comparator.comparing(ListEventItem::getValue);
		final Comparator<ListEventItem<String>> compareValueAndLabel
				= compareValue.thenComparing(ListEventItem::getLabel);
		list.sort(compareValueAndLabel);

		return Optional.of(new TwoColumnListEvent<>(list));
	}

	public String getJenkinsDataByID(final String build) throws EventSourceException {
		try (final JenkinsServer jenkins
				= new JenkinsServer(getJenkinsUri(), credentials.getUsername(), credentials.getPassword())) {
			final Map<String, Job> jobs = jenkins.getJobs();

			// request Pipeline information
			final JobWithDetails job = jobs.get(build).details();
			final Build last = job.getLastBuild();
			final Build lateststable = job.getLastStableBuild();
			final Build latestunstable = job.getLastUnstableBuild();
			final Build latestfailed = job.getLastFailedBuild();

			// compares build numbers to get build status
			if (last.equals(latestfailed) && last.equals(latestunstable) && last.equals(lateststable)) {
				return "No Build available";
			}
			if (last.equals(lateststable)) {
				return "Build succesful";
			}
			if (last.equals(latestunstable)) {
				return "Build unstable";
			}
			if (last.equals(latestfailed)) {
				return "Build failed";
			}
			if (last.equals(Build.BUILD_HAS_NEVER_RUN)) {
				return "Has never run";
			}
			return "Newest Build not finished";
		} catch (final IOException e) {
			throw new EventSourceException("Failed accessing Jenkins for " + build, e);
		}
	}

	private URI getJenkinsUri() throws EventSourceException {
		try {
			return URI.create(jenkinsUrl.getUrl());
		} catch (final IllegalArgumentException e) {
			throw new EventSourceException("Failed parsing Jenkins URL as URI", e);
		}
	}

	@Property(name = "Credentials", description = "The credentials to be used when connecting to Jenkins.")
	public Credentials getCredentials() {
		return credentials;
	}

	public void setCredentials(final Credentials credentials) {
		this.credentials = credentials;
	}

	@Property(name = "Jenkins URL", description = "URL to the Jenkins server")
	public ServerConnection getJenkinsUrl() {
		return jenkinsUrl;
	}

	public void setJenkinsUrl(final ServerConnection jenkinsUrl) {
		this.jenkinsUrl = jenkinsUrl;
	}

	@Property(name = "BuilsIDs", description = "Build IDs from Jenkins Build Pipelines")
	public Pipeline[] getPipelines() {
		return pipelines;
	}

	public void setPipelines(final Pipeline[] pipelines) {
		this.pipelines = pipelines;
	}

	private static class Pipeline {
		private String pipelineName = "";

		private String branch = "";

		public Pipeline() {
			// nothing to initialize here
		}
	}
}
