package com.hlag.oversigt.util.text;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import edu.umd.cs.findbugs.annotations.Nullable;

class JsonPathFunction implements Function<String, String> {
	private final Configuration configuration;

	private final String json;

	JsonPathFunction(final Configuration configuration, final String json) {
		this.configuration = configuration;
		this.json = json;
	}

	@Override
	public String apply(@Nullable final String jsonPathString) {
		Objects.requireNonNull(jsonPathString, "Input for a JsonPath must be non null");
		final JsonPath jsonPath = JsonPath.compile(jsonPathString);
		final Object result = jsonPath.read(json, configuration);

		if (result == null) {
			return "";
		} else if (result instanceof Collection && ((Collection<?>) result).size() == 1) {
			return ((Collection<?>) result).iterator().next().toString();
		} else {
			return result.toString();
		}
	}
}
