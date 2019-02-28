package com.hlag.oversigt.sources;

import static com.hlag.oversigt.util.Utils.logDebug;
import static com.hlag.oversigt.util.Utils.logError;
import static com.hlag.oversigt.util.Utils.logTrace;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
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

@EventSource(displayName = "Internet Extraction Text", description = "Shows text extracted from (one or more) URL", view = "List", hiddenDataItems = "updated-at-message")
public class InternetTextExtractionEventSource extends AbstractDownloadEventSource<TwoColumnListEvent<String>> {
	private ValueExtraction[] valueExtractions = new ValueExtraction[] { new ValueExtraction("", "$[*].name") };
	private Summarization summarization = Summarization.ConcatenationWithLineBreak;
	private String defaultValue = "";

	@Override
	protected TwoColumnListEvent<String> produceEvent() {
		logTrace(getLogger(), "Starting event creation");

		String body = downloadText();
		String output = processValueExtractions(body);
		if (Strings.isNullOrEmpty(output)) {
			output = getDefaultValue();
		}

		List<ListEventItem<String>> items = Splitter.on("\n")
				.splitToList(output)
				.stream()
				.map(l -> new ListEventItem<>(l, ""))
				.collect(Collectors.toList());
		return new TwoColumnListEvent<>(items);
	}

	private String downloadText() {
		try {
			String body = downloadString(createConfiguredConnection());
			logDebug(getLogger(), "Downloaded body");
			logTrace(getLogger(), "Body content %s", body);
			return body;
		} catch (IOException e) {
			logError(getLogger(), "Unable to download content: %s", e.getMessage());
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private String processValueExtractions(String downloadedContent) {
		Object value = Arrays//
				.stream(getValueExtractions())
				.filter(ve -> ve.filter(downloadedContent))
				.map(ve -> ve.process(downloadedContent))
				.collect(getSummarization().getCollector());
		if (value instanceof Optional) {
			value = ((Optional) value).orElse("");
		}
		return value.toString();
	}

	@Property(name = "Default Value", description = "The default value to show if the JSONPath does not match", type = "text")
	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	@Property(name = "Value Extraction", description = "Specify the values to extract and how to extract them.")
	public ValueExtraction[] getValueExtractions() {
		return valueExtractions;
	}

	public void setValueExtractions(ValueExtraction[] valueExtractions) {
		this.valueExtractions = valueExtractions;
	}

	@Property(name = "Summary Method", description = "How to summarize multiple extraction values.")
	public Summarization getSummarization() {
		return summarization;
	}

	public void setSummarization(Summarization summaization) {
		this.summarization = summaization;
	}

	public static class ValueExtraction {
		private final String condition;
		private final String format;

		public ValueExtraction(String condition, String format) {
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

		boolean filter(String downloadedContent) {
			String result = TextProcessor//
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
			} catch (Exception ignore) {
			}
			try {
				if (Long.parseLong(result) > 0L) {
					return true;
				}
			} catch (Exception ignore) {
			}
			return false;
		}

		String process(String downloadedContent) {
			return TextProcessor//
					.create()
					.registerDatetimeFunctions()
					.registerJsonPathFunction(downloadedContent)
					.registerRegularExpressionFunction(downloadedContent)
					.process(format);
		}
	}

	public static enum Summarization {
		Concatenation(Collectors.joining()),
		ConcatenationWithLineBreak(Collectors.joining("\n")),
		Sum(Collectors.summingInt(cs -> Integer.parseInt(cs.toString()))),
		Average(Collectors.averagingInt(cs -> Integer.parseInt(cs.toString()))),
		Min(Collectors
				.minBy((a, b) -> Integer.compare(Integer.parseInt(a.toString()), Integer.parseInt(b.toString())))),
		Max(Collectors
				.maxBy((a, b) -> Integer.compare(Integer.parseInt(a.toString()), Integer.parseInt(b.toString()))));

		private final Collector<CharSequence, ?, ?> collector;

		private Summarization(Collector<CharSequence, ?, ?> collector) {
			this.collector = collector;
		}

		public Collector<CharSequence, ?, ?> getCollector() {
			return collector;
		}
	}
}
