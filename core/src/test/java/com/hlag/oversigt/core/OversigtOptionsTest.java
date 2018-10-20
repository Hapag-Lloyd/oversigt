package com.hlag.oversigt.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class OversigtOptionsTest {
	@Test
	public void shouldCreateObject_whenParsingStringArray_givenValidCommandLine() throws Exception {
		// given
		String[] givenCommandLine = new String[] { //
				"--debug", //
				"--startEventSources", //
				"--ldapBindPassword", //
				"idefix", //
		};

		// when
		OversigtOptions actualStartOptions = OversigtOptions.parse(givenCommandLine);

		// then
		assertThat(actualStartOptions.isDebugFallback()).isTrue();
		assertThat(actualStartOptions.isDeleteNonExistingEventSourceFromDatabase()).isFalse();
		assertThat(actualStartOptions.isStartEventSources()).isTrue();
		assertThat(actualStartOptions.getLdapBindPasswordFallback()).isEqualTo("idefix");
	}

	@Test
	public void shouldCreateFilledMap_whenParsingStringArray_givenValidCommandLine() throws Exception {
		// given
		String[] givenCommandLine = new String[] { //
				"--debug", //
				"--ldapBindPassword", //
				"idefix", //
		};
		Map<String, String> expectedProperties = new HashMap<>();
		expectedProperties.put("debugFallback", "true");
		expectedProperties.put("ldapBindPasswordFallback", "idefix");
		expectedProperties.put("deleteNonExistingEventSourceFromDatabase", "false");
		expectedProperties.put("startEventSources", "false");

		// when
		OversigtOptions actualStartOptions = OversigtOptions.parse(givenCommandLine);
		Map<String, String> actualProperties = actualStartOptions.getProperties();

		// then
		assertThat(actualProperties).isEqualTo(expectedProperties);
	}
}
