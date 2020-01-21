package com.hlag.oversigt.util;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.common.io.Resources;
import com.google.inject.Binder;
import com.hlag.oversigt.connect.exchange.Room;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.JsonBasedData;
import com.hlag.oversigt.sources.InternetImageEventSource;
import com.hlag.oversigt.sources.data.DisplayOption;
import com.hlag.oversigt.util.TypeUtils.SerializablePropertyMember;
import com.hlag.oversigt.util.bindtest.ClassA;

public class TypeUtilsTest {
	@Test
	public void testGetClassForName_positive() {
		final Optional<Class<?>> actual
				= TypeUtils.getClassForName("com.hlag.oversigt.sources.event.TwoColumnListEvent");

		assertThat(actual).isPresent();
		assertThat(actual).get().isEqualTo(com.hlag.oversigt.sources.event.TwoColumnListEvent.class);
	}

	@Test
	public void testGetClassForName_negative() {
		final Optional<Class<?>> actual = TypeUtils.getClassForName("this.class.does.not.exist");

		assertThat(actual).isEmpty();
	}

	@Test
	public void testIsOfType_1() {
		final Object bla = "abcde";

		final boolean actual = TypeUtils.isOfType(bla.getClass(), CharSequence.class);

		assertThat(actual).isTrue();
	}

	@Test
	public void testIsOfType_2() {
		final DisplayOption option = new DisplayOption();

		final boolean actual = TypeUtils.isOfType(option.getClass(), JsonBasedData.class);

		assertThat(actual).isTrue();
	}

	@Test
	public void testIsOfType_3() {
		final Object something = new InternetImageEventSource();

		final boolean actual = TypeUtils.isOfType(something.getClass(), JsonBasedData.class);

		assertThat(actual).isFalse();
	}

	@Test
	public void testIsOfType_4() {
		final Object something = new Room[0];

		final boolean actual = TypeUtils.isOfType(something.getClass(), JsonBasedData.class);

		assertThat(actual).isTrue();
	}

	@Test
	public void testBindClasses() throws ClassNotFoundException {
		final Binder binder = mock(Binder.class);
		final Package pack = ClassA.class.getPackage();
		final ArgumentCaptor<Class<?>> captor = ArgumentCaptor.forClass(Class.class);

		TypeUtils.bindClasses(pack, x -> true, binder);

		verify(binder, times(2)).bind(captor.capture());
		final List<Class<?>> actual = captor.getAllValues();
		assertThat(actual).size().isEqualTo(2);
		// the second class is loaded via Class.forName() to avoid the test code load
		// the class instead of the code that is actually being tested.
		assertThat(actual).contains(ClassA.class, Class.forName("com.hlag.oversigt.util.bindtest.ClassB"));
	}

	@Test
	public void testListClassesInJarFiles() throws IOException {
		final URL given = Resources.getResource("utils/b.jar");
		final String expected = "com.hlag.oversigt.web.ui.OversigtUiHelper";

		final List<String> actual = TypeUtils.listClassesInJarFiles(new URL[] { given });

		assertThat(actual).size().isEqualTo(1);
		assertThat(actual).first().isEqualTo(expected);
	}

	public void testGetMembers() {
		// TODO implement
	}

	@Test
	public void testToMemberMap_1() {
		final Map<String, Object> expected
				= Utils.map("id", 1, "name", "name", "username", "username", "password", "password", "domain", "ww");

		final Map<String, Object> actual
				= TypeUtils.toMemberMap(new Credentials(1, "name", "username", "password", "ww"));

		assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
	}

	@Test
	public void testToMemberMap_2() {
		final Map<String, Object> expected
				= Utils.map("id", 1, "name", "name", "username", "username", "password", "password", "domain", "ww");

		final Map<String, Object> actual
				= TypeUtils.toMemberMap(Optional.of(new Credentials(1, "name", "username", "password", "ww")));

		assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
	}

	@Test
	public void testToMemberMap_3() {
		final Map<String, Object> actual = TypeUtils.toMemberMap(Optional.empty());

		assertThat(actual).isEmpty();
	}

	@Test
	public void testGetSerializablePropertyMembers() {
		final String[] expectedNames = new String[] { "name", "username", "password", "domain" };
		final String[] expectedIcons = new String[] { "tag", "user", "lock", "cloud" };

		final Collection<SerializablePropertyMember> members
				= TypeUtils.getSerializablePropertyMembers(Credentials.class);
		final List<String> actualNames = members.stream().map(SerializablePropertyMember::getName).collect(toList());
		final List<String> actualIcons = members.stream().map(SerializablePropertyMember::getIcon).collect(toList());

		assertThat(actualNames).containsExactlyInAnyOrder(expectedNames);
		assertThat(actualIcons).containsExactlyInAnyOrder(expectedIcons);
	}

	@Test
	public void testStreamFields() {
		final String[] exptected = new String[] {
				"DEFAULT_LIFETIME",
				"applicationId",
				"id",
				"updatedAt",
				"moreinfo",
				"createdOn",
				"lifetime",
				"title" };

		final List<String> actual = TypeUtils.streamFields(OversigtEvent.class).map(Field::getName).collect(toList());

		assertThat(actual).contains(exptected);
	}

	public void testGetAppropriateConstructor() {
		// TODO implement
	}

	@Test
	public void testTryToCreateInstance_Duration() {
		final Optional<TemporalAmount> actual
				= TypeUtils.<TemporalAmount>tryToCreateInstance("PT360H12M27S", Duration.class, Period.class);

		assertThat(actual).isPresent();
		assertThat(actual).get().isInstanceOf(Duration.class);
		assertThat(actual).get()
				.asInstanceOf(InstanceOfAssertFactories.type(Duration.class))
				.extracting(Duration::getSeconds)
				.isEqualTo(1_296_747L);
	}

	@Test
	public void testTryToCreateInstance_Period() {
		final Optional<TemporalAmount> actual
				= TypeUtils.<TemporalAmount>tryToCreateInstance("P200Y11M14D", Duration.class, Period.class);

		assertThat(actual).isPresent();
		assertThat(actual).get().isInstanceOf(Period.class);
		assertThat(actual).get()
				.asInstanceOf(InstanceOfAssertFactories.type(Period.class))
				.extracting(Period::getDays)
				.isEqualTo(14);
		assertThat(actual).get()
				.asInstanceOf(InstanceOfAssertFactories.type(Period.class))
				.extracting(Period::getYears)
				.isEqualTo(200);
	}

	@Test
	public void testTryToCreateInstance_negative() {
		final Optional<Object> actual = TypeUtils.<Object>tryToCreateInstance("abc");

		assertThat(actual).isEmpty();
	}

	@Test
	public void testCreateInstance_int() {
		final int exptected = 42;

		final Integer actual = TypeUtils.createInstance(int.class, "42");

		assertThat(actual).isEqualTo(exptected);
	}

	@Test
	public void testCreateInstance_Pattern() {
		final Pattern exptected = Pattern.compile("abcdef");

		final Pattern actual = TypeUtils.createInstance(Pattern.class, "abcdef");

		assertThat(actual.pattern()).isEqualTo(exptected.pattern());
	}

	@Test
	public void testCreateInstance_Color() {
		final Color exptected = Color.WHITE;

		final Color actual = TypeUtils.createInstance(Color.class, "#ffffff");

		assertThat(actual).isEqualTo(exptected);
	}

	@Test
	public void testGetMethod() {
		final Optional<Method> actualMethod = TypeUtils.getMethod(InternetImageEventSource.class,
				Arrays.asList("produceEventFromData", "produceCachedEvent", "produceEvent"),
				new Class<?>[0]);

		assertThat(actualMethod).isPresent();

		final String actualName = actualMethod.get().getName();

		assertThat(actualName).isEqualTo("produceEvent");
	}

	@Test
	public void testCreateArray() {
		final Object actual = TypeUtils.createArray(Short.class, 5);

		assertThat(actual).isInstanceOf(Short[].class);
		assertThat((Short[]) actual).hasSize(5);
	}
}
