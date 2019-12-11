package com.hlag.oversigt.sources;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.HttpClientBuilder;

import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.EventSourceException;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.HttpProxy;
import com.hlag.oversigt.properties.ServerConnection;
import com.hlag.oversigt.sources.event.JenkinsPipelineStatusEvent;
import com.hlag.oversigt.sources.event.JenkinsPipelineStatusEvent.JenkinsPipelineEventStatusItem;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.client.JenkinsHttpClient;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;

@EventSource(view = "JenkinsPipelineStatus", displayName = "Jenkins Pipeline Status", hiddenDataItems = { "moreinfo" })
public class JenkinsPipelineStatusEventSource extends ScheduledEventSource<JenkinsPipelineStatusEvent> {

	private Credentials credentials = Credentials.EMPTY;

	private HttpProxy proxy = HttpProxy.EMPTY;

	private ServerConnection jenkinsUrl = ServerConnection.EMPTY;

	@Property(name = "HTTP Proxy", description = "The proxy server to be used to contact the internet.")
	public HttpProxy getHttpProxy() {
		return proxy;
	}

	public void setHttpProxy(final HttpProxy proxy) {
		this.proxy = proxy;
	}

	private String regEx = "";

	private Pipeline[] pipelines = {};

	public JenkinsPipelineStatusEventSource() {
		// no fields to be initialized
	}

	@Override
	protected Optional<JenkinsPipelineStatusEvent> produceEvent() throws EventSourceException {
		final List<JenkinsPipelineEventStatusItem> list = new ArrayList<>();

		// Requests pipeline status
		for (final Pipeline pipeline : pipelines) {
			for (final Branch branch : pipeline.branch) {
				list.addAll(getJenkinsDataByID(pipeline, branch, branch.times));
			}
		}

		// Compare Pipelines
		final Comparator<JenkinsPipelineEventStatusItem> comparator
				= Comparator.comparing(JenkinsPipelineEventStatusItem::getStatus)
						.thenComparing(JenkinsPipelineEventStatusItem::getPipeline);
		list.sort(comparator);

		return Optional.of(new JenkinsPipelineStatusEvent(list));
	}

	public List<JenkinsPipelineEventStatusItem> getJenkinsDataByID(final Pipeline pipe,
			final Branch branch,
			final int times) throws EventSourceException {
		try (JenkinsServer jenkins = new JenkinsServer(new JenkinsHttpClient(getJenkinsUri(),
				createHttpClientBuilder(),
				credentials.getUsername(),
				credentials.getPassword()))) {
			final Map<String, Job> jobs = jenkins.getJobs();
			// request Pipeline information
			final JobWithDetails job = jobs.get(pipe.pipelineName).details();
			final List<JenkinsPipelineEventStatusItem> returns = new ArrayList<>();
			for (final Build buildfromjob : job.getBuilds()) {
				if (buildfromjob.details().getDescription() != null
						&& buildfromjob.details().getDescription().contains(branch.branchName)) {
					final BuildResult status = buildfromjob.details().getResult();
					if (status != null) {
						String userID = "";
						String ticket = "";
						final Matcher m = Pattern.compile(regEx).matcher(buildfromjob.details().getDescription());
						m.reset(buildfromjob.details().getDescription());
						if (m.find()) {
							// Use group 2 and 3 from RegEx
							userID = m.group(2);
							ticket = m.group(3);
						}
						if (status.equals(BuildResult.UNKNOWN)) {
							returns.add(
									new JenkinsPipelineEventStatusItem(pipe.pipelineName.replace("_", " ").substring(4),
											branch.branchName,
											ticket,
											userID,
											Integer.toString(buildfromjob.getNumber()),
											"No Build available",
											"font-weight: bold",
											"",
											"font-style: italic",
											"font-style: italic",
											"font-size: 80%",
											""));
						} else if (status.equals(BuildResult.SUCCESS)) {
							returns.add(
									new JenkinsPipelineEventStatusItem(pipe.pipelineName.replace("_", " ").substring(4),
											branch.branchName,
											ticket,
											userID,
											Integer.toString(buildfromjob.getNumber()),
											"Build successful",
											"font-weight: bold",
											"",
											"font-style: italic",
											"font-style: italic",
											"font-size: 80%",
											""));
						} else if (status.equals(BuildResult.UNSTABLE)) {
							returns.add(
									new JenkinsPipelineEventStatusItem(pipe.pipelineName.replace("_", " ").substring(4),
											branch.branchName,
											ticket,
											userID,
											Integer.toString(buildfromjob.getNumber()),
											"Build unstable",
											"font-weight: bold",
											"",
											"font-style: italic",
											"font-style: italic",
											"font-size: 80%",
											""));
						} else if (status.equals(BuildResult.FAILURE)) {
							returns.add(
									new JenkinsPipelineEventStatusItem(pipe.pipelineName.replace("_", " ").substring(4),
											branch.branchName,
											ticket,
											userID,
											Integer.toString(buildfromjob.getNumber()),
											"Build failed",
											"font-weight: bold",
											"",
											"font-style: italic",
											"font-style: italic",
											"font-size: 80%",
											"color:red"));
						} else if (status.equals(BuildResult.NOT_BUILT)) {
							returns.add(
									new JenkinsPipelineEventStatusItem(pipe.pipelineName.replace("_", " ").substring(4),
											branch.branchName,
											ticket,
											userID,
											Integer.toString(buildfromjob.getNumber()),
											"Has never run",
											"font-weight: bold",
											"",
											"font-style: italic",
											"font-style: italic",
											"font-size: 80%",
											""));
						} else if (status.equals(BuildResult.BUILDING)) {
							returns.add(
									new JenkinsPipelineEventStatusItem(pipe.pipelineName.replace("_", " ").substring(4),
											branch.branchName,
											ticket,
											userID,
											Integer.toString(buildfromjob.getNumber()),
											"Is currently building",
											"font-weight: bold",
											"",
											"font-style: italic",
											"font-style: italic",
											"font-size: 80%",
											""));
						} else if (status.equals(BuildResult.ABORTED)) {
							returns.add(
									new JenkinsPipelineEventStatusItem(pipe.pipelineName.replace("_", " ").substring(4),
											branch.branchName,
											ticket,
											userID,
											Integer.toString(buildfromjob.getNumber()),
											"Build aborted",
											"font-weight: bold",
											"",
											"font-style: italic",
											"font-style: italic",
											"font-size: 80%",
											""));
						} else {
							returns.add(
									new JenkinsPipelineEventStatusItem(pipe.pipelineName.replace("_", " ").substring(4),
											branch.branchName,
											ticket,
											userID,
											Integer.toString(buildfromjob.getNumber()),
											"no status available",
											"font-weight: bold",
											"",
											"font-style: italic",
											"font-style: italic",
											"font-size: 80%",
											""));
						}
						if (returns.size() == times) {
							return returns;
						}
					}
				}
			}

			return returns;

		} catch (final IOException e) {
			throw new EventSourceException("Failed accessing Jenkins for " + pipe.pipelineName, e);
		}
	}

	private HttpClientBuilder createHttpClientBuilder() {
		final HttpClientBuilder builder = HttpClientBuilder.create();

		final HttpProxy proxy = getHttpProxy();
		if (proxy != HttpProxy.EMPTY) {
			builder.setProxy(new HttpHost(proxy.getHostname(), proxy.getPort()));
		}

		return builder;
	}

	private URI getJenkinsUri() throws EventSourceException {
		try {
			return URI.create(jenkinsUrl.getUrl());
		} catch (final IllegalArgumentException e) {
			throw new EventSourceException("Failed parsing Jenkins URL as URI", e);
		}
	}

	@Property(name = "Regular Expression",
			description = "The regular expression(Java) used to specify information from description.")
	public String getValue() {
		return regEx;
	}

	public void setValue(final String regEx) {
		this.regEx = regEx;
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

	public static class Pipeline {
		private String pipelineName = "";

		private Branch[] branch = {};

		public Pipeline() {
			// nothing to initialize here
		}
	}

	public static class Branch {
		private String branchName = "";

		private int times = 1;

		public Branch() {
			// nothing to initialize here
		}
	}
}
