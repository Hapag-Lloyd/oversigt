package com.hlag.oversigt.model;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.storage.Storage;

@Singleton
public class EventSourceNameGenerator {
	private static final String EVENTSOURCE = "eventsource";

	@Inject
	private Storage storage;

	public EventSourceNameGenerator() {
		// no fields to be initialized manually, some will be injected
	}

	public String createEventSourceInstanceName(final EventSourceDescriptor descriptor) {
		// create base name
		String basename = descriptor//
				.getServiceClass()
				.map(c -> c.getSimpleName())
				.map(name -> {
					if (name.toLowerCase().endsWith(EVENTSOURCE)) {
						return name.substring(0, name.length() - EVENTSOURCE.length());
					}
					return name;
				})
				.orElse(descriptor.getView());
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
