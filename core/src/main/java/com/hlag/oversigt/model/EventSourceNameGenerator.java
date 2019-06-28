package com.hlag.oversigt.model;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.storage.Storage;

@Singleton
public class EventSourceNameGenerator {
	@Inject
	private Storage storage;

	public EventSourceNameGenerator() {
		// no fields to be initialized manually, some will be injected
	}

	public String createEventSourceInstanceName(final EventSourceDescriptor descriptor) {
		// create base name
		String basename = descriptor.getView();
		if (descriptor.getServiceClass() != null) {
			basename = descriptor.getServiceClass().getSimpleName();
			if (basename.toLowerCase().endsWith("eventsource")) {
				basename = basename.substring(0, basename.length() - "eventsource".length());
			}
		}
		basename = basename.chars()
				.mapToObj(c -> Character.isUpperCase(c) ? " " + (char) c : Character.toString((char) c))
				.collect(Collectors.joining())
				.trim();

		// look if there is another source with the same name
		final Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		names.addAll(storage.getEventSourceNames());
		String name = basename;
		int i = 1;
		while (names.contains(name)) {
			name = basename + " " + i;
			i += 1;
		}

		return name;
	}
}
