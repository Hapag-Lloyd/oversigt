package com.hlag.oversigt.controller;

import static com.hlag.oversigt.util.ClassRenameDetector.detectComplexRename;
import static com.hlag.oversigt.util.ClassRenameDetector.detectPackageMove;
import static com.hlag.oversigt.util.ClassRenameDetector.detectSimpleRename;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.validation.constraints.NotNull;

import com.google.common.base.Strings;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.util.Utils;

import de.larssh.utils.Finals;
import edu.umd.cs.findbugs.annotations.Nullable;

public final class EventSourceKey implements Comparable<EventSourceKey> {
	private static final Comparator<EventSourceKey> COMPARATOR_BY_DISPLAY_NAME
			= Utils.caseInsensitiveComparator(EventSourceKey::getDisplayName);

	private static final Comparator<EventSourceKey> COMPARATOR_BY_KEY
			= Utils.caseSensitiveComparator(EventSourceKey::getKey);

	public static final Comparator<EventSourceKey> COMPARATOR
			= COMPARATOR_BY_DISPLAY_NAME.thenComparing(COMPARATOR_BY_KEY);

	private static AtomicReference<EventSourceRenamer> eventSourceRenamer = new AtomicReference<>();

	static final String PREFIX_CLASS = Finals.constant("class:");

	static final String PREFIX_WIDGET = Finals.constant("widget:");

	private static final Map<String, EventSourceKey> KEYS = Collections.synchronizedMap(new HashMap<>());

	static void setEventSourceRenamer(final EventSourceRenamer eventSourceRenamer) {
		EventSourceKey.eventSourceRenamer.set(eventSourceRenamer);
	}

	private static EventSourceKey findKeyFromClass(final String className) {
		Optional<EventSourceKey> key = detectPackageMove(KEYS, className);
		if (!key.isPresent()) {
			key = detectSimpleRename(KEYS, className);
		}
		if (!key.isPresent()) {
			key = detectComplexRename(KEYS, className);
		}
		return key.orElseThrow(() -> new RuntimeException("Unable to find matching class for: " + className));
	}

	private static EventSourceKey findKeyFromWidget(final String widget) {
		// TODO detect widget renamings
		throw new RuntimeException("Unknown widget id: " + widget);
	}

	public static EventSourceKey fromKeyString(final String key) {
		if (!KEYS.containsKey(key)) {
			final String type = getType(key);
			final String subKey = getSubKey(key);
			switch (type) {
			case "class":
				final EventSourceKey newKey = findKeyFromClass(subKey);
				Optional.ofNullable(eventSourceRenamer.get())
						.ifPresent(r -> r.changeEventSourceName(subKey, getSubKey(newKey.getKey())));
				return newKey;
			case "widget":
				return findKeyFromWidget(subKey);
			default:
				throw new InvalidKeyException("Unknown EventSourceKey type: " + type);
			}
		}
		return KEYS.get(key);
	}

	public static EventSourceKey fromClassOrView(final Optional<String> className, final String viewName) {
		final String key = className.isPresent()
				? EventSourceKey.PREFIX_CLASS + className.get().toString()
				: EventSourceKey.PREFIX_WIDGET + viewName;
		return fromKeyString(key);
	}

	static EventSourceKey createKeyFromClass(final Class<?> clazz) {
		Optional<String> displayName = Optional.empty();
		if (clazz.isAnnotationPresent(EventSource.class)) {
			displayName = Optional.ofNullable(clazz.getAnnotation(EventSource.class).displayName());
		}
		return addKey(new EventSourceKey("class:" + clazz.getName(), displayName.orElse(clazz.getSimpleName())));
	}

	static EventSourceKey createKeyFromWidget(final String widgetName, final String displayName) {
		return addKey(new EventSourceKey("widget:" + widgetName, Strings.nullToEmpty(displayName)));
	}

	private static EventSourceKey addKey(final EventSourceKey key) {
		if (KEYS.containsKey(key.getKey())) {
			throw new RuntimeException("Duplicate key: " + key);
		}
		KEYS.put(key.getKey(), key);
		return key;
	}

	private final String key;

	private final String displayName;

	private EventSourceKey(final String key, final String displayName) {
		this.key = key;
		this.displayName = displayName;
	}

	@NotNull
	public String getDisplayName() {
		return displayName;
	}

	@NotNull
	public String getKey() {
		return key;
	}

	private static String getType(final String key) {
		return key.substring(0, key.indexOf(":"));
	}

	private static String getSubKey(final String key) {
		return key.substring(key.indexOf(":") + 1);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + key.hashCode();
		return result;
	}

	@Override
	public boolean equals(@Nullable final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (obj instanceof String) {
			return key.equals(obj);
		} else if (getClass() != obj.getClass()) {
			return false;
		}
		final EventSourceKey other = (EventSourceKey) obj;
		if (!key.equals(other.key)) {
			return false;
		}
		return true;
	}

	@Override
	public int compareTo(@Nullable final EventSourceKey that) {
		return COMPARATOR_BY_KEY.compare(this, that);
	}

	@Override
	public String toString() {
		return key;
	}

	@FunctionalInterface
	interface EventSourceRenamer {
		void changeEventSourceName(String oldName, String newName);
	}
}
