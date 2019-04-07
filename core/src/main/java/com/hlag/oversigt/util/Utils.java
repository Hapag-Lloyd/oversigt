package com.hlag.oversigt.util;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CaseFormat;
import com.hlag.oversigt.security.Principal;

import de.larssh.utils.Nullables;

public final class Utils {
	private static final Logger CHANGE_LOGGER = LoggerFactory.getLogger("change");

	public static void logDebug(final Logger logger, final String format, final Object... objects) {
		if (logger.isDebugEnabled()) {
			logger.debug(format(format, objects));
		}
	}

	public static void logTrace(final Logger logger, final String format, final Object... objects) {
		if (logger.isTraceEnabled()) {
			logger.trace(format(format, objects));
		}
	}

	public static void logWarn(final Logger logger, final String format, final Object... objects) {
		if (logger.isWarnEnabled()) {
			logger.warn(format(format, objects));
		}
	}

	public static void logInfo(final Logger logger, final String format, final Object... objects) {
		if (logger.isInfoEnabled()) {
			logger.info(format(format, objects));
		}
	}

	public static void logError(final Logger logger, final String format, final Object... objects) {
		if (logger.isErrorEnabled()) {
			logger.error(format(format, objects));
		}
	}

	public static void logChange(final Principal who, final String didWhat, final Object... withWhat) {
		logChange(who.getUsername(), didWhat, withWhat);
	}

	public static void logChange(final String who, final String didWhat, final Object... withWhat) {
		CHANGE_LOGGER.info(who + " - " + String.format(didWhat, withWhat));
	}

	private static String format(final String string, final Object... objects) {
		return String.format(string,
				Nullables.map(objects,
						objs -> Arrays.stream(objs)
								.map(o -> o instanceof Supplier ? ((Supplier<?>) o).get() : o)
								.collect(Collectors.toList())
								.toArray()));
	}

	public static Set<String> findGets(final Collection<String> lines) {
		return findPattern(GET_PATTERN, lines.stream());
	}

	public static Set<String> findSets(final Collection<String> lines) {
		return findPattern(SET_PATTERN, lines.stream());
	}

	public static Set<String> findDataBindings(final Stream<String> lines) {
		return findPattern(DB_PATTERN, lines);
	}

	private static Set<String> findPattern(final Pattern pattern, final Stream<String> lines) {
		return lines//
				.flatMap(s -> find(pattern, s, "name"))//
				.map(s -> CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, s))
				.collect(Collectors.toSet());
	}

	private static final Pattern GET_PATTERN = Pattern.compile("@get\\s*\\(\\s*(['\"])(?<name>[^'\"]+)\\1\\s*\\)");

	private static final Pattern SET_PATTERN = Pattern.compile("@set\\s*\\(\\s*(['\"])(?<name>[^'\"]+)\\1\\s*,");

	private static final Pattern DB_PATTERN = Pattern
			.compile("data-bind(?:-[-_a-zA-Z0-9]+)?=\"(?<name>[-_a-zA-Z0-9]*+)(\"|[^\"\\.][^\"]*|[^\"\\.]*)\"");

	private static Stream<String> find(final Pattern pattern, final String line, final String groupName) {
		final Builder<String> builder = Stream.builder();
		final Matcher matcher = pattern.matcher(line);
		while (matcher.find()) {
			builder.accept(matcher.group(groupName));
		}
		return builder.build();
	}

	public static <I, T extends Comparable<T>> Comparator<I> caseSensitiveComparator(final Function<I, T> function) {
		return (a, b) -> function.apply(a).compareTo(function.apply(b));
	}

	public static <T> Comparator<T> caseInsensitiveComparator(final Function<T, String> function) {
		return (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(function.apply(a), function.apply(b));
	}

	public static <T> Predicate<T> not(final Predicate<T> predicate) {
		return x -> !predicate.test(x);
	}

	public static <T> Predicate<T> notNull() {
		return x -> x != null;
	}

	public static boolean is(final Object object) {
		if (object instanceof Boolean) {
			return ((Boolean) object).booleanValue();
		} else if (object instanceof Number) {
			return ((Number) object).intValue() > 0;
		} else if (object instanceof String) {
			return Boolean.parseBoolean((String) object);
		} else {
			throw new RuntimeException("Unknown class: " + object.getClass().getName());
		}
	}

	@SafeVarargs
	public static <T> Stream<T> concat(final Stream<T>... streams) {
		return Arrays.stream(streams).flatMap(identity());
	}

	public static Map<String, Object> map(final Object... parameters) {
		if (parameters.length % 2 != 0) {
			throw new RuntimeException("keysAndValues length is not even");
		}
		final LinkedHashMap<String, Object> values = new LinkedHashMap<>();
		for (int i = 0; i < parameters.length; i += 2) {
			if (parameters[i] instanceof String) {
				values.put((String) parameters[i], parameters[i + 1]);
			} else {
				throw new ClassCastException("Unable to convert array to map. Parameter "
						+ i
						+ " ("
						+ (parameters[i] != null ? parameters[i].toString() : "null")
						+ ") is not of type String.");
			}
		}
		return values;
	}

	public static String notNullOrEmpty(final String string, final String message) {
		if (requireNonNull(string, message).isEmpty()) {
			throw new IllegalArgumentException(message);
		}
		return string;
	}

	public static <T> Map<String, T> removePasswords(final Map<String, T> map, final T empty) {
		final Iterator<Entry<String, T>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			final Entry<String, T> e = it.next();
			if (e.getKey().toLowerCase().contains("password")) {
				e.setValue(empty);
			}
		}
		return map;
	}

	private Utils() {
		throw new UnsupportedOperationException();
	}
}
