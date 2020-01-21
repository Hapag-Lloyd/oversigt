package com.hlag.oversigt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
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
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.hlag.oversigt.core.configuration.JsonModule;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardColorScheme;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;
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

		final TestClass1 actual = JsonUtils.fromJson(given, TestClass1.class);

		assertThat(actual).isNotNull();
		assertThat(actual).extracting(TestClass1::getHostname).isEqualTo(Optional.of("smtp.server.com"));
		assertThat(actual).extracting(TestClass1::getPort).isEqualTo(587);
		assertThat(actual).extracting(TestClass1::isStartTls).isEqualTo(true);
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

	@Test
	public void testJsonSchema() throws IOException {
		final String expected
				= Resources.toString(Resources.getResource("com.hlag.oversigt.util.JsonUtilsTest.TestClass2.json"),
						StandardCharsets.UTF_8);

		final String actual = JsonUtils.toJsonSchema(TestClass2.class);

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void testJsonSchemaFromProperty() {
		final String expected
				= "{\"$schema\":\"http://json-schema.org/schema#\",\"$id\":\"http://schema.hlag.com/oversigt/property/com.hlag.oversigt.properties.Credentials\",\"title\":\"Credentials\",\"type\":\"number\",\"uniqueItems\":true,\"enum\":[0],\"oversigt-ids\":[0],\"enumSource\":[{\"title\":\"{{item.title}}\",\"value\":\"{{item.value}}\",\"source\":[{\"value\":0,\"title\":\" \"}]}]}";

		final String actual = JsonUtils.toJsonSchema(Credentials.class);

		assertThat(actual).isEqualTo(expected);
	}

	private static final class TestClass1 {
		private Optional<String> hostname = Optional.empty();

		private int port = 0;

		private boolean startTls = false;

		private Optional<String> username = Optional.empty();

		private Optional<String> password = Optional.empty();

		private Optional<String> senderAddress = Optional.empty();

		private TestClass1() {
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

	public static final class TestClass2 {
		@SuppressWarnings("checkstyle:VisibilityModifier")
		public Optional<String> hostname = Optional.empty();

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public Optional<?> otherName = Optional.empty();

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public int port = 0;

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public char c = 'c';

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public byte b = 1;

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public short s = 2;

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public long l = 3;

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public boolean startTls = false;

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public String username = "user";

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public Optional<String[]> usernames = Optional.empty();

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public DashboardColorScheme enumeration = DashboardColorScheme.COLORED;

		@SuppressWarnings("checkstyle:VisibilityModifier")
		@JsonHint(arrayStyle = ArrayStyle.TABLE, headerTemplate = "abc$1")
		public List<DashboardColorScheme> list = Collections.emptyList();

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public Map<DashboardColorScheme, Dashboard[]> map = Collections.emptyMap();

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public Color color = Color.AQUA;

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public Credentials credentials = Credentials.EMPTY;

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public LocalDate localDate = LocalDate.now();

		@SuppressWarnings("checkstyle:VisibilityModifier")
		public LocalTime localTime = LocalTime.NOON;
	}
}
