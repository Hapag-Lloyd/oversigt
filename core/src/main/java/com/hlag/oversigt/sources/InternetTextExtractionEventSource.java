package com.hlag.oversigt.sources;

import static com.hlag.oversigt.util.Utils.logDebug;
import static com.hlag.oversigt.util.Utils.logError;
import static com.hlag.oversigt.util.Utils.logTrace;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.sources.event.TwoColumnListEvent;
import com.hlag.oversigt.sources.event.TwoColumnListEvent.ListEventItem;
import com.hlag.oversigt.util.text.TextProcessor;

@EventSource(displayName = "Internet Extraction Text",
		description = "Shows text extracted from (one or more) URL",
		view = "List",
		hiddenDataItems = "updated-at-message")
public class InternetTextExtractionEventSource extends AbstractDownloadEventSource<TwoColumnListEvent<String>> {
	private ValueExtraction[] valueExtractions = new ValueExtraction[] { new ValueExtraction("", "$[*].name") };

	private Summarization summarization = Summarization.ConcatenationWithLineBreak;

	private String defaultValue = "";

	@Override
	protected TwoColumnListEvent<String> produceEvent() {
		logTrace(getLogger(), "Starting event creation");

		final String body = downloadText();
		String output = processValueExtractions(body);
		if (Strings.isNullOrEmpty(output)) {
			output = getDefaultValue();
		}

		final List<ListEventItem<String>> items = Splitter.on("\n")
				.splitToList(output)
				.stream()
				.map(l -> new ListEventItem<>(l, ""))
				.collect(Collectors.toList());
		return new TwoColumnListEvent<>(items);
	}

	private String downloadText() {
		try {
			final String body = downloadString(createConfiguredConnection());
			logDebug(getLogger(), "Downloaded body");
			logTrace(getLogger(), "Body content %s", body);
			return body;
		} catch (final IOException e) {
			logError(getLogger(), "Unable to download content: %s", e.getMessage());
			throw new RuntimeException(e);
		}
	}

	private String processValueExtractions(final String downloadedContent) {
		Object value = Arrays.stream(getValueExtractions())
				.filter(ve -> ve.filter(downloadedContent))
				.map(ve -> ve.process(downloadedContent))
				.collect(getSummarization().getCollector());
		if (value instanceof Optional) {
			final Optional<?> optional = (Optional<?>) value;
			if (!optional.isPresent()) {
				return "";
			}
			value = optional.get();
		}
		return Objects.toString(value);
	}

	@Property(name = "Default Value", description = "The default value to show if the JSONPath does not match")
	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(final String defaultValue) {
		this.defaultValue = defaultValue;
	}

	@Property(name = "Value Extraction", description = "Specify the values to extract and how to extract them.")
	public ValueExtraction[] getValueExtractions() {
		return valueExtractions;
	}

	public void setValueExtractions(final ValueExtraction[] valueExtractions) {
		this.valueExtractions = valueExtractions;
	}

	@Property(name = "Summary Method", description = "How to summarize multiple extraction values.")
	public Summarization getSummarization() {
		return summarization;
	}

	public void setSummarization(final Summarization summaization) {
		summarization = summaization;
	}

	public static class ValueExtraction {
		private final String condition;

		private final String format;

		public ValueExtraction(final String condition, final String format) {
			super();
			this.condition = condition;
			this.format = format;
		}

		public String getCondition() {
			return condition;
		}

		public String getFormat() {
			return format;
		}

		boolean filter(final String downloadedContent) {
			final String result = TextProcessor//
					.create()
					.registerDatetimeFunctions()
					.registerJsonPathFunction(downloadedContent)
					.registerRegularExpressionFunction(downloadedContent)
					.process(condition)
					.trim();
			try {
				if (Boolean.parseBoolean(result)) {
					return true;
				}
			} catch (@SuppressWarnings("unused") final Exception ignore) {/* ignore */}
			try {
				if (Long.parseLong(result) > 0L) {
					return true;
				}
			} catch (@SuppressWarnings("unused") final Exception ignore) {/* ignore */}
			return false;
		}

		String process(final String downloadedContent) {
			return TextProcessor//
					.create()
					.registerDatetimeFunctions()
					.registerJsonPathFunction(downloadedContent)
					.registerRegularExpressionFunction(downloadedContent)
					.process(format);
		}
	}

	public enum Summarization {
		Concatenation(Collectors.joining()),
		ConcatenationWithLineBreak(Collectors.joining("\n")),
		Sum(Collectors.summingInt(cs -> Integer.parseInt(cs.toString()))),
		Average(Collectors.averagingInt(cs -> Integer.parseInt(cs.toString()))),
		Min(Collectors
				.minBy((a, b) -> Integer.compare(Integer.parseInt(a.toString()), Integer.parseInt(b.toString())))),
		Max(Collectors
				.maxBy((a, b) -> Integer.compare(Integer.parseInt(a.toString()), Integer.parseInt(b.toString()))));

		private final Collector<CharSequence, ?, ?> collector;

		Summarization(final Collector<CharSequence, ?, ?> collector) {
			this.collector = collector;
		}

		public Collector<CharSequence, ?, ?> getCollector() {
			return collector;
		}
	}
}
