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
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hlag.oversigt.model.EventSourceKey;

import info.debatty.java.stringsimilarity.Levenshtein;
import info.debatty.java.stringsimilarity.interfaces.MetricStringDistance;

public class ClassRenameDetector {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClassRenameDetector.class);
	private static final String RENAME_FILENAME = "data/classRenames.properties";
	private static final Properties RENAME_PROPERTIES = new Properties();

	static {
		Path path = Paths.get(RENAME_FILENAME);
		if (Files.exists(path)) {
			LOGGER.info("Class renaming properties exist. Loading them...");
			try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
				RENAME_PROPERTIES.load(in);
				LOGGER.info("Found {} class renaming instructions.", RENAME_PROPERTIES.size());
				LOGGER.debug("Class renaming properties: {}", RENAME_PROPERTIES);
			} catch (IOException e) {
				LOGGER.error("Unable to load class renaming properties", e);
			}
		}
	}

	private static String getSimpleName(String className) {
		return className.contains(".") ? className.substring(className.lastIndexOf(".") + 1) : className;
	}

	public static EventSourceKey detectPackageMove(Map<String, EventSourceKey> KEYS, String className) {
		final String simpleClassNameWithDot = "." + getSimpleName(className);
		Set<Entry<String, EventSourceKey>> candidates = KEYS.entrySet()//
				.stream()
				.filter(s -> s.getKey().startsWith("class:"))
				.filter(s -> s.getKey().endsWith(simpleClassNameWithDot))
				.collect(toSet());
		if (candidates.size() == 1) {
			return candidates.iterator().next().getValue();
		} else {
			if (candidates.isEmpty()) {
				LOGGER.warn("No moved class found for name: {}", className);
			} else if (candidates.size() > 1) {
				LOGGER.warn("Multiple moved candidates found for class [{}]: {}",
						className,
						candidates.stream().map(Entry::getKey).collect(joining(", ")));
			}
			return null;
		}
	}

	private static final MetricStringDistance DISTANCE = new Levenshtein();

	public static EventSourceKey detectSimpleRename(Map<String, EventSourceKey> KEYS, String className) {
		final String simpleClassName = getSimpleName(className);
		List<Tuple<EventSourceKey, Double>> candidates = KEYS.entrySet()//
				.stream()
				.filter(s -> s.getKey().startsWith("class:"))
				.map(s -> new Tuple<>(s.getValue(), DISTANCE.distance(simpleClassName, getSimpleName(s.getKey()))))
				.filter(t -> t.getSecond() <= 2.0)
				.sorted((a, b) -> Double.compare(a.getSecond(), b.getSecond()))
				.collect(toList());
		if (candidates.size() == 1) {
			return candidates.iterator().next().getFirst();
		} else {
			if (candidates.isEmpty()) {
				LOGGER.warn("No renamed class found for name: {}", className);
			} else if (candidates.size() > 1) {
				LOGGER.warn("Multiple renamed candidates found for class [{}]: {}",
						className,
						candidates.stream().map(Tuple::getFirst).map(EventSourceKey::getKey).collect(joining(", ")));
			}
			return null;
		}
	}

	public static EventSourceKey detectComplexRename(Map<String, EventSourceKey> KEYS, String className) {
		String newClassName = RENAME_PROPERTIES.getProperty(className);
		if (newClassName != null) {
			try {
				Class.forName(newClassName);
				LOGGER.info("Class {} replaced by {}", className, newClassName);
				return KEYS.get("class:" + newClassName);
			} catch (Exception e) {
				LOGGER.error("Unable to load class {} in order to replace class {}", newClassName, className, e);
			}
		}
		return null;
	}
}
