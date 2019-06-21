package com.hlag.oversigt.sources;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.User;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.hlag.oversigt.connect.jira.JiraClientException;
import com.hlag.oversigt.connect.jira.JiraClientFactory;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;
import com.hlag.oversigt.sources.data.DisplayOption;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;

import de.larssh.utils.Nullables;

public abstract class AbstractJiraEventSource<T extends OversigtEvent> extends ScheduledEventSource<T> {
	private static final Pattern HANDLE_AUTHENTICATION_EXCEPTION_PATTERN = Pattern.compile("[\r\n]+");

	private ServerConnection jiraConnection = ServerConnection.EMPTY;

	private Credentials jiraCredentials = Credentials.EMPTY;

	private String query = "";

	private final Set<AggregationType> aggregationTypes
			= Collections.synchronizedSet(new TreeSet<>(Arrays.asList(AggregationType.STATUS)));

	private DisplayOption[] displayOptions = null;

	private DisplayOption defaultDisplayOption = null;

	private boolean showEmptyCategories = false;

	private ShowUnknownCategories showUnknownCategories = ShowUnknownCategories.ALL;

	@Override
	protected void shutDown() throws Exception {
		super.shutDown();
	}

	protected Map<DisplayOption, Set<Issue>> getJiraTickets() {
		// List all issues as defined by JQL query
		final List<Issue> issues;
		try {
			issues = JiraClientFactory.createJiraClient(getJiraConnection(), getJiraCredentials()).search(getQuery());
		} catch (JiraClientException | TimeoutException e) {
			return handleException(e);
		}

		// Initialize entries per user-defined category to keep the user-defined order
		Map<DisplayOption, Set<Issue>> issuesPerCategory = new LinkedHashMap<>();
		for (final DisplayOption displayOption : getDisplayOptions()) {
			issuesPerCategory.put(displayOption, new HashSet<>());
		}

		// Find known (categories) and unknown DisplayOptions per issue
		final Map<DisplayOption, Set<Issue>> issuesUnknown = new TreeMap<>();
		for (final Issue issue : issues) {
			boolean containedKnownDislayOption = false;

			// Add issues to all known (categories) DisplayOptions,
			// following the user-defined option "showUnknownDisplayOptions" for unknown
			// DisplayOptions
			for (final DisplayOption displayOption : getDisplayOptions(issue)) {
				if (issuesPerCategory.containsKey(displayOption) && !Strings.isNullOrEmpty(displayOption.getValue())) {
					issuesPerCategory.get(displayOption).add(issue);
					containedKnownDislayOption = true;
				} else if (getShowUnknownCategories() == ShowUnknownCategories.ALL
						|| getShowUnknownCategories() == ShowUnknownCategories.UNKNOWN_ONLY
								&& !containedKnownDislayOption) {
									if (!issuesUnknown.containsKey(displayOption)) {
										issuesUnknown.put(displayOption, new HashSet<>());
									}
									issuesUnknown.get(displayOption).add(issue);
								}
			}
		}

		// Add unknown categories afterwards to have them sorted first (using TreeMap)
		issuesPerCategory.putAll(issuesUnknown);

		// If not getShowEmptyCategories then remove empty DisplayOption entries
		if (!getShowEmptyCategories()) {
			issuesPerCategory = issuesPerCategory.entrySet()
					.stream()
					.filter(e -> !e.getValue().isEmpty())
					.collect(de.larssh.utils.Collectors.toLinkedHashMap());
		}

		return issuesPerCategory;
	}

	private DisplayOption getUnknownDisplayOptions(final AggregationType aggregationType,
			final Issue issue,
			final String value) {
		final DisplayOption defaultDisplayOption = getDefaultDisplayOption();
		if (defaultDisplayOption != null && !Strings.isNullOrEmpty(defaultDisplayOption.getDisplayValue())) {
			return defaultDisplayOption;
		}
		return new DisplayOption(aggregationType.getDisplayValue(issue, value));
	}

	@Property(name = "Jira URL")
	public ServerConnection getJiraConnection() {
		return jiraConnection;
	}

	public void setJiraConnection(final ServerConnection jiraConnection) {
		this.jiraConnection = jiraConnection;
	}

	@Property(name = "Jira Credentials", description = "The credentials to be used for Jira log in.")
	public Credentials getJiraCredentials() {
		return jiraCredentials;
	}

	public void setJiraCredentials(final Credentials jiraCredentials) {
		this.jiraCredentials = jiraCredentials;
	}

	@Property(name = "Search Query")
	public String getQuery() {
		return query;
	}

	public void setQuery(final String query) {
		this.query = query;
	}

	@Property(name = "Aggregate values by")
	public AggregationType[] getAggregationTypes() {
		return aggregationTypes.toArray(new AggregationType[0]);
	}

	public void setAggregationTypes(final AggregationType[] aggregationTypes) {
		synchronized (this.aggregationTypes) {
			this.aggregationTypes.clear();
			this.aggregationTypes.addAll(Arrays.asList(aggregationTypes));
		}
	}

	@Property(name = "Display Options",
			description = "Optional mapping of original display values to originated display options, such as value and color. Values are handled as regular expression.")
	public DisplayOption[] getDisplayOptions() {
		if (displayOptions == null) {
			return new DisplayOption[0];
		}
		return displayOptions;
	}

	/**
	 * Returns all matching DisplayOptions of an issue The returned set is ordered
	 * by known categories first and unknown DisplayOptions last. For no further
	 * ordering is guaranteed.
	 */
	private Set<DisplayOption> getDisplayOptions(final Issue issue) {
		// Collect attribute values to match
		final Map<String, AggregationType> values = new TreeMap<>();
		for (final AggregationType aggregationType : getAggregationTypes()) {
			for (final String value : aggregationType.getAttributeValues(issue)) {
				values.put(value, aggregationType);
			}
		}

		// Collect DisplayOptions per value
		final Set<DisplayOption> displayOptions = new LinkedHashSet<>();
		final Set<DisplayOption> displayOptionsUnknown = new TreeSet<>();
		for (final String value : values.keySet()) {
			boolean foundDisplayOption = false;

			for (final DisplayOption displayOption : getDisplayOptions()) {
				if (AggregationType.matches(displayOption, value)) {
					displayOptions.add(displayOption);
					foundDisplayOption = true;
				}
			}

			if (!foundDisplayOption) {
				displayOptionsUnknown.add(getUnknownDisplayOptions(values.get(value), issue, value));
			}
		}

		displayOptions.addAll(displayOptionsUnknown);
		return displayOptions;
	}

	public void setDisplayOptions(final DisplayOption[] displayOptions) {
		this.displayOptions = displayOptions;
	}

	@Property(name = "Default Display Option",
			description = "Optionally all unmapped display values can be displayed by this color. By defining a Default Display Option display value, all unmapped display values are mapped to just one entry with that specified display value.")
	public DisplayOption getDefaultDisplayOption() {
		return defaultDisplayOption;
	}

	public void setDefaultDisplayOption(final DisplayOption defaultDisplayOption) {
		this.defaultDisplayOption = defaultDisplayOption;
	}

	// TODO this property should be a property of the Widget, not of the EventSource
	@Property(name = "Show Empty Categories",
			description = "If enabled the event of this event source will contain all categories, even those containing any data. Otherwise they will be excluded.")
	public boolean getShowEmptyCategories() {
		return showEmptyCategories;
	}

	public void setShowEmptyCategories(final boolean showEmptyCategories) {
		this.showEmptyCategories = showEmptyCategories;
	}

	@Property(name = "Show Unknown Categories",
			description = "Handles how values without a matching Display Option are handled.\n'ALL' shows them anyway, 'UNKNOWN_ONLY' shows them only if no Display Option matched the issue at all and 'NONE' ignores unknown values at all.")
	public ShowUnknownCategories getShowUnknownCategories() {
		return showUnknownCategories;
	}

	public void setShowUnknownCategories(final ShowUnknownCategories showUnknownCategories) {
		this.showUnknownCategories = showUnknownCategories;
	}

	private <X> X handleException(final Exception original) {
		Throwable previous = null;
		Throwable throwable = original;

		while (throwable != null && throwable != previous) {
			if (throwable instanceof TimeoutException) {
				failure("JIRA request timed out while waiting for a free connection.");
			} else if (throwable instanceof JiraClientException) {
				return failure(throwable.getMessage(), throwable.getCause());
			} else if (throwable instanceof RestClientException) {
				final RestClientException rce = (RestClientException) throwable;
				if (rce.getStatusCode().isPresent()
						&& rce.getStatusCode().get() == 500
						&& rce.getCause() instanceof JSONException) {
					final JSONException je = (JSONException) rce.getCause();
					final String[] messageParts = HANDLE_AUTHENTICATION_EXCEPTION_PATTERN.split(je.getMessage(), 0);
					final List<String> auths = Arrays.stream(messageParts)
							.filter(ste -> ste.toLowerCase().contains("authenticator"))
							.collect(Collectors.toList());

					if (contains(auths, "getUserFromBasicAuthentication") && contains(auths, "checkAuthenticated")) {
						if (getJiraCredentials() == null || getJiraCredentials() == Credentials.EMPTY) {
							return failure("No credentials configured.");
						}
						return failure("Unable to log in to JIRA. Username: " + getJiraCredentials().getUsername());
					}
				}
			}
			if (Throwables.getRootCause(throwable) instanceof SocketTimeoutException) {
				return failure("JIRA not available: Timeout");
			}
			if (Throwables.getRootCause(throwable) instanceof JSONException) {
				return failure("JIRA not available: Invalid JSON returned from server");
			}
			previous = throwable;
			throwable = throwable.getCause();
		}

		throw new RuntimeException(original);
	}

	private static boolean contains(final Collection<String> haystack, final String needle) {
		return haystack.stream().filter(s -> s.contains(needle)).findAny().isPresent();
	}

	@JsonHint(arrayStyle = ArrayStyle.TABLE)
	public enum AggregationType {
		ASSIGNEE("Assignee",
				i -> i.getAssignee() == null ? null : i.getAssignee().getName(),
				i -> i.getAssignee() == null ? null : i.getAssignee().getDisplayName()),
		COMPONENTS("Components",
				i -> StreamSupport.stream(i.getComponents().spliterator(), false)
						.map(BasicComponent::getName)
						.collect(Collectors.toSet()),
				(i, v) -> v),
		ISSUE_TYPE("Issue Type", i -> i.getIssueType().getName()),
		PRIORITY("Priority", i -> i.getPriority().getName()),
		PROJECT("Project", i -> i.getProject().getName()),
		REPORTER("Reporter", i -> i.getReporter().getName(), i -> i.getReporter().getDisplayName()),
		RESPONSIBLE_PERSONS("Responsible Persons",
				i -> getUserNamesOfResponsiblePersons(i.getFieldByName("Responsible Persons")),
				(i, v) -> getDisplayNameOfResponsiblePerson(i.getFieldByName("Responsible Persons"), v)),
		STATUS("Status", i -> i.getStatus().getName()),
		WORKER("Worker",
				i -> i.getFieldByName("Worker").getValue() == null
						? null
						: ((User) i.getFieldByName("Worker").getValue()).getName(),
				i -> i.getFieldByName("Worker").getValue() == null
						? null
						: ((User) i.getFieldByName("Worker").getValue()).getDisplayName());

		private static String getDisplayNameOfResponsiblePerson(final IssueField field, final String name) {
			try {
				final JSONArray responsiblePersons = (JSONArray) field.getValue();
				for (int i = 0; i < responsiblePersons.length(); i += 1) {
					final JSONObject person = responsiblePersons.getJSONObject(i);
					if (person.getString("name").equals(name)) {
						return person.getString("displayName");
					}
				}
				return name;
			} catch (final JSONException e) {
				throw new RuntimeException("Unable to extract responsible person.", e);
			}
		}

		private static List<String> getUserNamesOfResponsiblePersons(final IssueField field) {
			try {
				final List<String> names = new ArrayList<>();
				final JSONArray responsiblePersons = (JSONArray) field.getValue();
				if (responsiblePersons != null) {
					for (int i = 0; i < responsiblePersons.length(); i += 1) {
						names.add(responsiblePersons.getJSONObject(i).getString("name"));
					}
				}
				return names;
			} catch (final JSONException e) {
				throw new RuntimeException("Unable to extract responsible persons.", e);
			}
		}

		public static boolean matches(final DisplayOption displayOption, final String attributeValue) {
			final Pattern pattern = Pattern.compile(displayOption.getValue());
			return de.larssh.utils.text.Strings.matches(Nullables.orElse(attributeValue, ""), pattern);
		}

		private final Function<Issue, Collection<String>> attributeValuesProducer;

		private final String displayValue;

		private final BiFunction<Issue, String, String> displayValueProducer;

		AggregationType(final String displayValue, final Function<Issue, String> attributeValueProducer) {
			this(displayValue, i -> {
				final String value = attributeValueProducer.apply(i);
				return value == null ? Collections.emptyList() : Arrays.asList(value);
			}, (i, v) -> v);
		}

		AggregationType(final String displayValue,
				final Function<Issue, String> attributeValueProducer,
				final Function<Issue, String> displayValueProducer) {
			this(displayValue, i -> {
				final String value = attributeValueProducer.apply(i);
				return value == null ? Collections.emptyList() : Arrays.asList(value);
			}, (i, v) -> displayValueProducer.apply(i));
		}

		AggregationType(final String displayValue,
				final Function<Issue, Collection<String>> attributeValuesProducer,
				final BiFunction<Issue, String, String> displayValueProducer) {
			this.attributeValuesProducer = attributeValuesProducer;
			this.displayValue = displayValue;
			this.displayValueProducer = displayValueProducer;
		}

		public Collection<String> getAttributeValues(final Issue issue) {
			return attributeValuesProducer.apply(issue);
		}

		public String getDisplayValue() {
			return displayValue;
		}

		public String getDisplayValue(final Issue issue, final String value) {
			return displayValueProducer.apply(issue, value);
		}

		@Override
		public String toString() {
			return displayValue;
		}
	}

	private enum ShowUnknownCategories {
		ALL,
		UNKNOWN_ONLY,
		NONE;
	}
}
