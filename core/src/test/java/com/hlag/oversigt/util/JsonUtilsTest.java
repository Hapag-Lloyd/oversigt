package com.hlag.oversigt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.hlag.oversigt.core.configuration.JsonModule;
import com.hlag.oversigt.storage.Storage;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import edu.umd.cs.findbugs.annotations.Nullable;

@ExtendWith(MockitoExtension.class)
public class JsonUtilsTest {
	public static Module[] createModulesForJsonTesting() {
		return new Module[] {
				new JsonModule(),
				binder -> binder.bind(Storage.class).toInstance(Mockito.mock(Storage.class)) };
	}

	@Mock
	@Nullable
	private ObjectMapper allFieldObjectMapper;

	@Mock
	@Nullable
	private Configuration jsonpathConfiguration;

	@BeforeAll
	public static void initJsonUtils() {
		// needed to inject static values
		Guice.createInjector(createModulesForJsonTesting());
	}

	// TODO build tests

	@Test
	public void testToJson_1() {
		final Object given = new String[0];
		final String expected = "[]";

		final String actual = JsonUtils.toJson(given);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void testToJson_2() {
		final Object given = Utils.map("abc", "def", "ghi", Utils.map("jkl", "mno"));
		final String expected = "{\"abc\":\"def\",\"ghi\":{\"jkl\":\"mno\"}}";

		final String actual = JsonUtils.toJson(given);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void testFromJson() {
		final String given = "\r\n"
				+ "{\r\n"
				+ "		\"hostname\": \"smtp.server.com\",\r\n"
				+ "		\"port\": 587,\r\n"
				+ "		\"startTls\": true,\r\n"
				+ "		\"username\": \"\",\r\n"
				+ "		\"senderAddress\": \"noreply@oversigt.com\"\r\n"
				+ "	}";

		final TestClass actual = JsonUtils.fromJson(given, TestClass.class);

		assertThat(actual).isNotNull();
		assertThat(actual).extracting(TestClass::getHostname).isEqualTo(Optional.of("smtp.server.com"));
		assertThat(actual).extracting(TestClass::getPort).isEqualTo(587);
		assertThat(actual).extracting(TestClass::isStartTls).isEqualTo(true);
		assertThrows(NoSuchElementException.class, () -> Objects.requireNonNull(actual).getPassword().get());
	}

	@Test
	public void testExtractValueUsingJsonPath() {
		final String given = "[\r\n"
				+ "  {\r\n"
				+ "    \"userId\": 1,\r\n"
				+ "    \"id\": 1,\r\n"
				+ "    \"title\": \"delectus aut autem\",\r\n"
				+ "    \"completed\": false\r\n"
				+ "  },\r\n"
				+ "  {\r\n"
				+ "    \"userId\": 1,\r\n"
				+ "    \"id\": 2,\r\n"
				+ "    \"title\": \"quis ut nam facilis et officia qui\",\r\n"
				+ "    \"completed\": false\r\n"
				+ "  },\r\n"
				+ "  {\r\n"
				+ "    \"userId\": 1,\r\n"
				+ "    \"id\": 3,\r\n"
				+ "    \"title\": \"fugiat veniam minus\",\r\n"
				+ "    \"completed\": false\r\n"
				+ "  }\r\n"
				+ "]";
		final String expected = "delectus aut autem";
		final JsonPath jsonPath = JsonPath.compile("$[0].title");

		final Object actual = JsonUtils.extractValueUsingJsonPath(jsonPath, given);

		assertThat(actual).isEqualTo(expected);
	}

	// @Test
	// public void testRemoveKeysFromJson() throws Exception {
	// throw new RuntimeException("not yet implemented");
	// }
	//
	@Test
	public void testRemovePasswords() {
		final Map<String, Object> input = Utils.map("password", "jo", "anderes", "ebenfalls");
		final Map<String, Object> expected = Utils.map("password", null, "anderes", "ebenfalls");

		final Map<String, Object> actual = JsonUtils.removePasswords(input);

		assertThat(actual).containsExactlyEntriesOf(expected);
	}

	@Test
	public void testRemovePasswordsFromJson() {
		final String json = "{\"password\":\"jo\",\"anderes\":\"ebenfalls\"}";
		final String expected = "{\"anderes\":\"ebenfalls\"}";

		final String actual = JsonUtils.removePasswordsFromJson(json);

		assertThat(actual).isEqualTo(expected);
	}

	private static final class TestClass {
		private Optional<String> hostname = Optional.empty();

		private int port = 0;

		private boolean startTls = false;

		private Optional<String> username = Optional.empty();

		private Optional<String> password = Optional.empty();

		private Optional<String> senderAddress = Optional.empty();

		private TestClass() {
			// no fields to be initialized
		}

		public Optional<String> getHostname() {
			return hostname;
		}

		public int getPort() {
			return port;
		}

		public boolean isStartTls() {
			return startTls;
		}

		@SuppressWarnings("unused")
		public Optional<String> getUsername() {
			return username;
		}

		public Optional<String> getPassword() {
			return password;
		}

		@SuppressWarnings("unused")
		public String getSenderAddress() {
			return senderAddress.orElseThrow(() -> new RuntimeException("No sender address configured"));
		}
	}
}
