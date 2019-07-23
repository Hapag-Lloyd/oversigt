package com.hlag.oversigt.util;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import com.google.common.base.CaseFormat;
import com.hlag.oversigt.security.Principal;

import de.larssh.utils.Nullables;
import edu.umd.cs.findbugs.annotations.Nullable;

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
		return lines.flatMap(s -> find(pattern, s, "name"))
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

	public static String formatDuration(final Duration duration) {
		// https://stackoverflow.com/questions/266825/how-to-format-a-duration-in-java-e-g-format-hmmss
		final long seconds = duration.getSeconds();
		final long absSeconds = Math.abs(seconds);
		final String positive
				= String.format("%d:%02d:%02d", absSeconds / 3600, absSeconds % 3600 / 60, absSeconds % 60);
		return seconds < 0 ? "-" + positive : positive;
	}

	public static Map<String, Object> map(final Object... parameters) {
		if (parameters.length % 2 != 0) {
			throw new RuntimeException("keysAndValues length is not even");
		}
		final Map<String, Object> values = new LinkedHashMap<>();
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

	public static Set<String> sortedAndSynchronizedSet(final String... items) {
		return sortedAndSynchronizedSet(Arrays.asList(items));
	}

	public static Set<String> sortedAndSynchronizedSet(final Collection<String> items) {
		final Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		set.addAll(items);
		return Collections.synchronizedSet(set);
	}

	public static String notNullOrEmpty(@Nullable final String stringToCheck, final String errorMessage) {
		if (requireNonNull(stringToCheck, errorMessage).isEmpty()) {
			throw new IllegalArgumentException(errorMessage);
		}
		return stringToCheck;
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

	public static int computeHashCode(final Object... objects) {
		return Stream.of(objects)//
				.filter(notNull())
				.map(Object::hashCode)
				.collect(hashing())
				.intValue();
	}

	public static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException e) {
			throw new RuntimeException("Cannot sleep anymore", e);
		}
	}

	/**
	 * Collector that allows parallelized calculation of a jobs hash code based upon
	 * {@link Objects#hashCode()}.
	 *
	 * @param <T> the type of input elements to the reduction operation
	 * @return collector that allows parallelized calculation of a jobs hash code
	 * @see <a href="https://stackoverflow.com/questions/39385860">StackOverflow</a>
	 */
	private static <T> Collector<T, ?, Integer> hashing() {
		return Collector.of(() -> new int[2], (a, o) -> {
			a[0] = a[0] * 31 + Objects.hashCode(o);
			a[1] += 1;
		}, (a1, a2) -> {
			a1[0] = a1[0] * iPow(31, a2[1]) + a2[0];
			a1[1] += a2[1];
			return a1;
		}, a -> iPow(31, a[1]) + a[0]);
	}

	/**
	 * @see derived from
	 *      <a href="http://stackoverflow.com/questions/101439">StackOverflow</a>
	 */
	@SuppressWarnings("all")
	private static int iPow(int base, int exp) {
		int result = 1;
		for (; exp > 0; exp >>= 1, base *= base) {
			if ((exp & 1) != 0) {
				result *= base;
			}
		}
		return result;
	}

	/**
	 * types of Operating Systems
	 */
	public enum OperatingSystemType {
		Windows,
		MacOS,
		Linux,
		Other
	}

	/**
	 * detect the operating system from the os.name System property and cache the
	 * result
	 *
	 * @return the operating system detected
	 */
	public static OperatingSystemType getOperatingSystemType() {
		final String osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
		if (osName.indexOf("mac") >= 0 || osName.indexOf("darwin") >= 0) {
			return OperatingSystemType.MacOS;
		} else if (osName.indexOf("win") >= 0) {
			return OperatingSystemType.Windows;
		} else if (osName.indexOf("nux") >= 0) {
			return OperatingSystemType.Linux;
		} else {
			return OperatingSystemType.Other;
		}
	}

	public static <T> void copyProperties(final T source, final T target, final String... ignoreProperties) {
		BeanUtils.copyProperties(source, target, ignoreProperties);
	}

	private Utils() {
		throw new UnsupportedOperationException();
	}
}
