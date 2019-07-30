package com.hlag.oversigt.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.jayway.jsonpath.Configuration;

import edu.umd.cs.findbugs.annotations.Nullable;

@ExtendWith(MockitoExtension.class)
public class JsonUtilsTest {
	@Mock
	@Nullable
	private ObjectMapper allFieldObjectMapper;

	@Mock
	@Nullable
	private Configuration jsonpathConfiguration;

	@BeforeAll
	public static void initJsonUtils() {
		final Field[] fields = JsonUtils.class.getDeclaredFields();
		for (final Field field : fields) {
			if (field.isAnnotationPresent(Inject.class)) {
				System.out.println(field);
			}
		}
	}

	@Test
	public void testToJson_1() {
		final String expected = "[]";
		final Object given = new String[0];

		final String actual = JsonUtils.toJson(given);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void testFromJson() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Test
	public void testExtractValueUsingJsonPath() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Test
	public void testRemoveKeysFromJson() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Test
	public void testRemovePasswordsFromJson() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Test
	public void testRemovePasswords() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

}
