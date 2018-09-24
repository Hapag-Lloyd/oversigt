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

public class TextProcessor {
	private static final Pattern PATTERN_DATA_REPLACEMENT = Pattern.compile("\\$\\{([a-z]+)(:[^\\}]+)?\\}");

	@Inject
	private static Configuration JSON_PATH_CONFIGURATION;
	@Inject
	private static DatatypeFactory DATATYPE_FACTORY;

	public static TextProcessor create() {
		return new TextProcessor();
	}

	private final Map<String, Function<String, String>> processors = new HashMap<>();

	private TextProcessor() {
	}

	public TextProcessor registerFunction(String name, Function<String, String> function) {
		processors.put(name, function);
		return this;
	}

	public TextProcessor registerDatetimeFunctions() {
		DatetimeFunction datetimeFunction = new DatetimeFunction(DATATYPE_FACTORY);
		registerFunction("datetime", s -> datetimeFunction.apply(s).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		registerFunction("date", s -> datetimeFunction.apply(s).format(DateTimeFormatter.ISO_LOCAL_DATE));
		registerFunction("time", s -> datetimeFunction.apply(s).format(DateTimeFormatter.ISO_LOCAL_TIME));
		return this;
	}

	public TextProcessor registerJsonPathFunction(String json) {
		return registerFunction("jsonpath",
				jsonpath -> new JsonPathFunction(JSON_PATH_CONFIGURATION, json).apply(jsonpath));
	}

	public TextProcessor registerRegularExpressionFunction(final String value) {
		return registerFunction("regex", regex -> new RegularExpressionFunction(value).apply(regex));
	}

	public String process(String string) {
		Matcher mainMatcher = PATTERN_DATA_REPLACEMENT.matcher(string);
		while (mainMatcher.find()) {
			String target = mainMatcher.group();
			String replacement;

			String processorName = mainMatcher.group(1);
			if (processors.containsKey(processorName)) {
				String input = mainMatcher.group(2);
				if (input != null) {
					input = input.substring(1);
				}
				replacement = processors.get(processorName).apply(input);
			} else {
				throw new RuntimeException("Data replacement '" + mainMatcher.group(1) + "' is unknown.");
			}

			string = string.replace(target, replacement);
		}
		return string;
	}
}
