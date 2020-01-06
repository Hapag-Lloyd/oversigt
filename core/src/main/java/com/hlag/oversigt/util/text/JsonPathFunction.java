package com.hlag.oversigt.util.text;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import com.hlag.oversigt.util.JsonUtils;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;

import edu.umd.cs.findbugs.annotations.Nullable;

class JsonPathFunction implements Function<String, String> {
	private final String probablyJson;

	JsonPathFunction(final String probablyJson) {
		this.probablyJson = probablyJson;
	}

	@Nullable
	@Override
	public String apply(@Nullable final String jsonPathString) {
		Objects.requireNonNull(jsonPathString, "Input for a JsonPath must be non null");
		final JsonPath jsonPath = JsonPath.compile(jsonPathString);

		final Object result;
		try {
			result = JsonUtils.extractValueUsingJsonPath(jsonPath, probablyJson);
		} catch (@SuppressWarnings("unused") final InvalidJsonException e) {
			return null;
		}

		if (result == null) {
			return "";
		} else if (result instanceof Collection && ((Collection<?>) result).size() == 1) {
			return ((Collection<?>) result).iterator().next().toString();
		} else {
			return result.toString();
		}
	}
}
