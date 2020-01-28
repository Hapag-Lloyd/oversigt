package com.hlag.oversigt.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class CommandLineOptionsTest {
	public CommandLineOptionsTest() {
		// no fields to be initialized
	}

	@Test
	public void shouldCreateObject_whenParsingStringArray_givenValidCommandLine() {
		// given
		final String[] givenCommandLine
				= new String[] { "--debug", "--startEventSources", "--ldapBindPassword", "idefix" };

		// when
		final CommandLineOptions actualStartOptions = CommandLineOptions.parse(givenCommandLine).get();

		// then
		assertThat(actualStartOptions.isDebugFallback()).isTrue();
		assertThat(actualStartOptions.isDeleteNonExistingEventSourceFromDatabase()).isFalse();
		assertThat(actualStartOptions.isStartEventSources()).isTrue();
		assertThat(actualStartOptions.getLdapBindPasswordFallback()).isEqualTo("idefix");
	}

	@Test
	public void shouldCreateFilledMap_whenParsingStringArray_givenValidCommandLine() {
		// given
		final String[] givenCommandLine = new String[] { "--debug", "--ldapBindPassword", "idefix" };
		final Map<String, String> expectedProperties = new HashMap<>();
		expectedProperties.put("debugFallback", "true");
		expectedProperties.put("ldapBindPasswordFallback", "idefix");
		expectedProperties.put("deleteNonExistingEventSourceFromDatabase", "false");
		expectedProperties.put("startEventSources", "false");

		// when
		final CommandLineOptions actualStartOptions = CommandLineOptions.parse(givenCommandLine).get();
		final Map<String, String> actualProperties = actualStartOptions.getProperties();

		// then
		assertThat(actualProperties).isEqualTo(expectedProperties);
	}
}
