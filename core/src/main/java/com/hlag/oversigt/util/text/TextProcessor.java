package com.hlag.oversigt.util.text;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.datatype.DatatypeFactory;

import com.google.inject.Inject;
import com.jayway.jsonpath.Configuration;

public final class TextProcessor {
	private static final Pattern PATTERN_DATA_REPLACEMENT
			= Pattern.compile("\\$\\{(?<processor>[a-z]+)(:(?<input>[^\\}]+))?\\}");

	@Inject
	private static Configuration jsonPathConfiguration;

	@Inject
	private static DatatypeFactory dataTypeFactory;

	public static TextProcessor create() {
		return new TextProcessor();
	}

	private final Map<String, Function<String, String>> processors = new HashMap<>();

	private TextProcessor() {}

	public TextProcessor registerFunction(final String name, final Function<String, String> function) {
		processors.put(name, function);
		return this;
	}

	public TextProcessor registerDatetimeFunctions() {
		final DatetimeFunction datetimeFunction = new DatetimeFunction(dataTypeFactory);
		registerFunction("datetime", s -> datetimeFunction.apply(s).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		registerFunction("date", s -> datetimeFunction.apply(s).format(DateTimeFormatter.ISO_LOCAL_DATE));
		registerFunction("time", s -> datetimeFunction.apply(s).format(DateTimeFormatter.ISO_LOCAL_TIME));
		return this;
	}

	public TextProcessor registerJsonPathFunction(final String json) {
		return registerFunction("jsonpath",
				jsonpath -> new JsonPathFunction(jsonPathConfiguration, json).apply(jsonpath));
	}

	public TextProcessor registerRegularExpressionFunction(final String value) {
		return registerFunction("regex", regex -> new RegularExpressionFunction(value).apply(regex));
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
