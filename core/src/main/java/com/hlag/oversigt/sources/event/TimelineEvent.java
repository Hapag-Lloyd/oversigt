package com.hlag.oversigt.sources.event;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAmount;
import java.util.Comparator;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import com.hlag.oversigt.core.OversigtEvent;
import com.hlag.oversigt.properties.Color;

public class TimelineEvent extends OversigtEvent {

	private static final Comparator<Event> BY_DATE = (e1, e2) -> e1.getLocalDate().compareTo(e2.getLocalDate());
	private static final Comparator<Event> BY_NAME = (e1, e2) -> e1.getName().compareTo(e2.getName());

	private SortedSet<Event> events = new TreeSet<>();

	private final TemporalAmount maxAge;

	public TimelineEvent(TemporalAmount maxAge) {
		this.maxAge = maxAge;
	}

	public TemporalAmount getMaxAge() {
		return maxAge;
	}

	public Optional<LocalDate> getDateOfFirstEvent() {
		return events.stream().findFirst().map(Event::getLocalDate);
	}

	public boolean hasEventAt(LocalDate date) {
		return events.stream().filter(event -> event.getLocalDate().equals(date)).findAny().isPresent();
	}

	public void addEvent(String name, LocalDate startDate, LocalDate endDate, boolean allDay, Color background) {
		addEvent(name, startDate, endDate, allDay, background.getHexColor());
	}

	public void addEvent(String name, LocalDate startDate, LocalDate endDate, boolean allDay, String background) {
		addEvent(name, startDate, endDate, allDay, background, null);
	}

	public void addEvent(String name,
			LocalDate startDate,
			LocalDate endDate,
			boolean allDay,
			Color background,
			Color fontColor) {
		addEvent(name,
				startDate,
				endDate,
				allDay,
				background.getHexColor(),
				fontColor != null ? fontColor.getHexColor() : null);
	}

	public void addEvent(String name,
			LocalDate startDate,
			LocalDate endDate,
			boolean allDay,
			String background,
			String fontColor) {
		final LocalDate now = LocalDate.now();

		// filter event in the past
		if (endDate.isBefore(now)) {
			return;
		}
		// filter all day events ending yesterday --> the end date is one day later at 00:00
		// but if the start and end date are the same -> keep the event
		if (allDay && endDate.isEqual(now) && !startDate.equals(endDate)) {
			return;
		}
		// filter events that are too far in the future
		if (maxAge != null && endDate.minus(maxAge).plusDays(1).isAfter(now)) {
			return;
		}

		events.add(new Event(name, startDate, background, fontColor));
	}

	/**
	 * Removes events older than maxAge if more than minCount of events are inside this event.
	 */
	public void removeEvents(TemporalAmount maxAge, int minCount) {
		if (events.size() > minCount) {
			int count = 0;
			for (Event event : events) {
				if (count > minCount
						&& (maxAge == null || event.getLocalDate().minus(maxAge).isAfter(LocalDate.now()))) {
					events = new TreeSet<>(events.headSet(event));
					return;
				}
				count += 1;
			}
		}
	}

	public static class Event implements Comparable<Event> {

		private final String name;
		private final String date;
		private final String background;
		private final String fontColor;
		private final LocalDate localDate;

		private Event(String name, LocalDate date, String background) {
			this(name, date, background, "white");
		}

		private Event(String name, LocalDate date, String background, String fontColor) {
			this.name = name;
			this.date = date.format(DateTimeFormatter.ISO_DATE);
			this.background = background;
			this.fontColor = fontColor == null ? "white" : fontColor;
			this.localDate = date;
		}

		public String getName() {
			return name;
		}

		public String getDate() {
			return date;
		}

		public String getBackground() {
			return background;
		}

		public String getFontColor() {
			return fontColor;
		}

		public LocalDate getLocalDate() {
			return localDate;
		}

		@Override
		public int compareTo(Event that) {
			return BY_DATE.thenComparing(BY_NAME).compare(this, that);
		}

		@Override
		public String toString() {
			return String.format("Event [name=%s, date=%s]", name, localDate);
		}
	}
}
