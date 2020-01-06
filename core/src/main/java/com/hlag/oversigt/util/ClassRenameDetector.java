package com.hlag.oversigt.util;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hlag.oversigt.controller.EventSourceKey;

import info.debatty.java.stringsimilarity.Levenshtein;
import info.debatty.java.stringsimilarity.interfaces.MetricStringDistance;

public final class ClassRenameDetector {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClassRenameDetector.class);

	private static final String RENAME_FILENAME = "data/classRenames.properties";

	private static final Properties RENAME_PROPERTIES = new Properties();

	static {
		final Path path = Paths.get(RENAME_FILENAME);
		if (Files.exists(path)) {
			LOGGER.info("Class renaming properties exist. Loading them...");
			try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
				RENAME_PROPERTIES.load(in);
				LOGGER.info("Found {} class renaming instructions.", RENAME_PROPERTIES.size());
				LOGGER.debug("Class renaming properties: {}", RENAME_PROPERTIES);
			} catch (final IOException e) {
				LOGGER.error("Unable to load class renaming properties", e);
			}
		}
	}

	private static String getSimpleName(final String className) {
		return className.contains(".") ? className.substring(className.lastIndexOf(".") + 1) : className;
	}

	public static Optional<EventSourceKey> detectPackageMove(final Map<String, EventSourceKey> keys,
			final String className) {
		final String simpleClassNameWithDot = "." + getSimpleName(className);
		final Set<Entry<String, EventSourceKey>> candidates = keys.entrySet()
				.stream()
				.filter(s -> s.getKey().startsWith("class:"))
				.filter(s -> s.getKey().endsWith(simpleClassNameWithDot))
				.collect(toSet());
		if (candidates.size() == 1) {
			return Optional.of(candidates.iterator().next().getValue());
		}
		if (candidates.isEmpty()) {
			LOGGER.warn("No moved class found for name: {}", className);
		} else if (candidates.size() > 1) {
			LOGGER.warn("Multiple moved candidates found for class [{}]: {}",
					className,
					candidates.stream().map(Entry::getKey).collect(joining(", ")));
		}
		return Optional.empty();
	}

	private static final MetricStringDistance DISTANCE = new Levenshtein();

	public static Optional<EventSourceKey> detectSimpleRename(final Map<String, EventSourceKey> keys,
			final String className) {
		final String simpleClassName = getSimpleName(className);
		final List<KeyDistance> candidates = keys.entrySet()//
				.stream()
				.filter(s -> s.getKey().startsWith("class:"))
				.map(s -> new KeyDistance(s.getValue(), DISTANCE.distance(simpleClassName, getSimpleName(s.getKey()))))
				.filter(t -> t.getDistance() <= 2.0)
				.sorted((a, b) -> Double.compare(a.getDistance(), b.getDistance()))
				.collect(toList());
		if (candidates.size() == 1) {
			return Optional.of(candidates.iterator().next().getKey());
		}
		if (candidates.isEmpty()) {
			LOGGER.warn("No renamed class found for name: {}", className);
		} else if (candidates.size() > 1) {
			LOGGER.warn("Multiple renamed candidates found for class [{}]: {}",
					className,
					candidates.stream().map(KeyDistance::getKey).map(EventSourceKey::getKey).collect(joining(", ")));
		}
		return Optional.empty();
	}

	public static Optional<EventSourceKey> detectComplexRename(final Map<String, EventSourceKey> keys,
			final String className) {
		final String newClassName = RENAME_PROPERTIES.getProperty(className);
		if (newClassName != null) {
			try {
				Class.forName(newClassName);
				LOGGER.info("Class {} replaced by {}", className, newClassName);
				return Optional.of(keys.get("class:" + newClassName));
			} catch (final Exception e) {
				LOGGER.error("Unable to load class {} in order to replace class {}", newClassName, className, e);
			}
		}
		return Optional.empty();
	}

	private ClassRenameDetector() {
		throw new UnsupportedOperationException();
	}

	private static final class KeyDistance {
		private final EventSourceKey key;

		private final Double distance;

		private KeyDistance(final EventSourceKey key, final Double distance) {
			this.key = key;
			this.distance = distance;
		}

		public EventSourceKey getKey() {
			return key;
		}

		public Double getDistance() {
			return distance;
		}
	}
}
