package com.hlag.oversigt.sources;

import static com.hlag.oversigt.util.Utils.logDebug;
import static com.hlag.oversigt.util.Utils.logError;
import static com.hlag.oversigt.util.Utils.logTrace;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;
import com.hlag.oversigt.sources.event.TextEvent;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

/**
 * @deprecated Do not use this event source any longer!
 */
@EventSource(displayName = "Internet Text",
		description = "Shows a text from a JSON",
		view = "Text",
		hiddenDataItems = "updated-at-message")
@Deprecated
public class InternetTextEventSource extends AbstractDownloadEventSource<TextEvent> {
	private static final Configuration JSON_PATH_CONFIGURATION
			= Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();

	private static final String JSONPATH_EXTRACTION_PATTERN_STRING = "\\$\\{((?:[^\\}]|\\}[^$])*)\\}\\$?";

	private static final Pattern JSONPATH_EXTRACTION_PATTERN = Pattern.compile(JSONPATH_EXTRACTION_PATTERN_STRING);

	// private String pathExpression = "$[*].name";
	private String[] pathExpressions = new String[] { "$[*].name" };

	private String stringFormat = "";

	private String defaultValue = "";

	private MultiResultBehaviour multiResultBehaviour = MultiResultBehaviour.Random;

	@Inject
	private Gson gson;

	public InternetTextEventSource() {
		// no fields to be initialized manually, some will be injected
	}

	@Override
	protected Optional<TextEvent> produceEvent() {
		logTrace(getLogger(), "Starting event creation");

		final String body;
		try {
			body = downloadString(createConfiguredConnection());
			logDebug(getLogger(), "Downloaded body");
			logTrace(getLogger(), "Body content %s", body);
		} catch (final IOException e) {
			logError(getLogger(), "Unable to download content: %s", e.getMessage());
			throw new RuntimeException(e);
		}

		final List<?> elements = extractElements(body);
		logDebug(getLogger(), "Extracted text: %s", elements.toString());

		final String output;
		if (elements.isEmpty()) {
			output = getDefaultValue();
		} else if (hasStringFormat()) {
			output = elements.stream().map(gson::toJson).map(this::useStringFormat).collect(Collectors.joining("\n"));
		} else {
			output = getMultiResultBehaviour().extractElement(elements).toString();
		}

		return Optional.of(new TextEvent(output));
	}

	private List<?> extractElements(final String json) {
		return getJsonPaths().stream()
				.map(jp -> extractElement(jp, json))
				.flatMap(Collection::stream)
				.collect(Collectors.toList());
	}

	private Collection<?> extractElement(final JsonPath jsonPath, final String json) {
		final Object result = jsonPath.read(json, JSON_PATH_CONFIGURATION);
		if (getLogger().isDebugEnabled()) {
			getLogger().debug("Result: " + result);
		}

		if (result == null) {
			return Collections.emptyList();
		} else if (result instanceof Collection) {
			return (Collection<?>) result;
		} else {
			return Arrays.asList(result);
		}
	}

	private String extractSingleElement(final JsonPath jsonPath, final String json) {
		return extractElement(jsonPath, json).stream().findFirst().map(Object::toString).orElse("");
	}

	@Property(name = "Default Value", description = "The default value to show if the JSONPath does not match")
	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(final String defaultValue) {
		this.defaultValue = defaultValue;
	}
	// @Property(name = "JSONPath Expression", description = "The path to the values
	// to be shown. If more than one item is returned one single value will be
	// chosen randomly.", type = "text")
	// public String getPathExpression() {
	// return pathExpression;
	// }
	// public void setPathExpression(String pathExpression) {
	// this.pathExpression = pathExpression;
	// }

	private List<JsonPath> getJsonPaths() {
		// if (getPathExpressions().length == 0) {
		// return Arrays.asList(JsonPath.compile(getPathExpression()));
		// } else {
		return Arrays.stream(getPathExpressions()).map(JsonPath::compile).collect(Collectors.toList());
		// }
	}

	@Property(name = "JSONPath Expressions",
			description = "The path to the values to be shown. If more than one item is returned one single value will be chosen randomly.")
	@JsonHint(arrayStyle = ArrayStyle.TABLE)
	public String[] getPathExpressions() {
		return pathExpressions;
	}

	public void setPathExpressions(final String[] pathExpressions) {
		this.pathExpressions = pathExpressions;
	}

	@Property(name = "Multi Result Behaviour",
			description = "How should the event source behave if the JSONPath expression results in more than one result?")
	public MultiResultBehaviour getMultiResultBehaviour() {
		return multiResultBehaviour;
	}

	public void setMultiResultBehaviour(final MultiResultBehaviour multiResultBehaviour) {
		this.multiResultBehaviour = multiResultBehaviour;
	}

	public String getStringFormat() {
		return stringFormat;
	}

	@Property(name = "String Format",
			description = "Defines how the extracted content will be displayed. To access values from extracted JSON use JSONPath expressions encapsuled in ${ and }$. Example: This is ${$.value.child}$.")
	public void setStringFormat(final String stringFormat) {
		this.stringFormat = stringFormat;
	}

	private boolean hasStringFormat() {
		return !Strings.isNullOrEmpty(getStringFormat());
	}

	private String useStringFormat(final String json) {
		@SuppressWarnings({ "checkstyle:IllegalInstantiation", "checkstyle:XIllegalTypeCustom" })
		final StringBuffer builder = new StringBuffer();

		final Matcher matcher = JSONPATH_EXTRACTION_PATTERN.matcher(getStringFormat());
		while (matcher.find()) {
			final String pathString = matcher.group(1);
			final JsonPath path = JsonPath.compile(pathString);
			matcher.appendReplacement(builder, extractSingleElement(path, json));
		}
		matcher.appendTail(builder);
		return builder.toString();
	}

	public enum MultiResultBehaviour {
		First(c -> c.iterator().next()),
		Last(c -> c.stream().skip(c.size() - 1).findFirst().get()),
		Random(c -> c.stream().skip((long) (Math.random() * c.size())).findFirst().get());

		private final Function<Collection<?>, ?> extractor;

		MultiResultBehaviour(final Function<Collection<?>, ?> extractor) {
			this.extractor = extractor;
		}

		private Object extractElement(final Collection<?> objects) {
			return extractor.apply(objects);
		}
	}
}
