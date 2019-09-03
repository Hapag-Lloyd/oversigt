package com.hlag.oversigt.util.text;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;

public final class TextProcessor {
	private static final Pattern PATTERN_DATA_REPLACEMENT
			= Pattern.compile("\\$\\{(?<processor>[a-z]+)(:(?<input>[^\\}]+))?\\}");

	private final Map<String, Function<String, String>> processors = new HashMap<>();

	public TextProcessor() {
		// nothing to do
	}

	public TextProcessor registerFunction(final String name, final Function<String, String> function) {
		processors.put(name, function);
		return this;
	}

	public TextProcessor registerDatetimeFunctions() {
		final DatetimeFunction datetimeFunction = new DatetimeFunction();
		registerFunction("datetime", s -> datetimeFunction.apply(s).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		registerFunction("date", s -> datetimeFunction.apply(s).format(DateTimeFormatter.ISO_LOCAL_DATE));
		registerFunction("time", s -> datetimeFunction.apply(s).format(DateTimeFormatter.ISO_LOCAL_TIME));
		return this;
	}

	public TextProcessor registerJsonPathFunction(final String probablyJson) {
		return registerFunction("jsonpath", new JsonPathFunction(probablyJson)::apply);
	}

	public TextProcessor registerRegularExpressionFunction(final String value) {
		return registerFunction("regex", new RegularExpressionFunction(value)::apply);
	}

	public TextProcessor registerXPathFunction(final Optional<Document> document) {
		return registerFunction("xpath", new XPathFunction(document)::apply);
	}

	public TextProcessor registerXPathFunction(final String probablyJsonOrXml) {
		return registerFunction("xpath", new XPathFunction(probablyJsonOrXml)::apply);
	}

	public String process(final String value) {
		String string = value;
		final Matcher mainMatcher = PATTERN_DATA_REPLACEMENT.matcher(string);
		while (mainMatcher.find()) {
			final String processorName = mainMatcher.group("processor");
			if (!processors.containsKey(processorName)) {
				throw new RuntimeException("Data replacement '" + mainMatcher.group(1) + "' is unknown.");
			}

			string = string.replace(mainMatcher.group(),
					processors.get(processorName).apply(mainMatcher.group("input")));
		}
		return string;
	}
}
