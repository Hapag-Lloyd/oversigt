package com.hlag.oversigt.sources;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import com.hlag.oversigt.connect.exchange.Meeting;
import com.hlag.oversigt.connect.exchange.Room;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.sources.ExchangeRoomAvailabilityEventSource.RoomAvailabilityListEvent;

/**
 * @author Olaf Neumann
 */
@EventSource(view = "MeetingRooms",
		displayName = "Microsoft Exchange Meeting Room Availability",
		description = "Shows the room availability from Microsoft Exchange for a configurable collection of rooms.",
		hiddenDataItems = { "moreinfo" })
public class ExchangeRoomAvailabilityEventSource extends AbstractExchangeEventSource<RoomAvailabilityListEvent> {

	@Override
	protected RoomAvailabilityListEvent produceExchangeEvent() throws Exception {
		final ZonedDateTime now = ZonedDateTime.now(getZoneId());
		final Map<Room, List<Meeting>> meetings
				= getExchangeClient().getMeetings(Arrays.asList(getRooms()), now.toLocalDate(), getZoneId());

		final List<RoomAvailabilityItem> unsortedItems = meetings.entrySet()
				.stream()
				.map(e -> checkRoomAvailability(now, e.getKey(), e.getValue()))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		final List<RoomAvailabilityItem> sortedItems;
		if (isAvailableRoomsToTop()) {
			final Comparator<RoomAvailabilityItem> compareByFree = (a, b) -> Boolean.compare(b.free, a.free);
			final Comparator<RoomAvailabilityItem> compareByUntil = (a, b) -> (a.free ? -1 : 1)
					* a.until.orElse(LocalTime.MAX).compareTo(b.until.orElse(LocalTime.MAX));
			final Comparator<RoomAvailabilityItem> compareByIndex
					= (a, b) -> unsortedItems.indexOf(a) - unsortedItems.indexOf(b);

			sortedItems = unsortedItems.stream()
					.sorted(compareByFree.thenComparing(compareByUntil).thenComparing(compareByIndex))
					.collect(Collectors.toList());
		} else {
			sortedItems = unsortedItems;
		}

		return new RoomAvailabilityListEvent(sortedItems);
	}

	private RoomAvailabilityItem checkRoomAvailability(final ZonedDateTime when,
			final Room room,
			final Collection<Meeting> meetings) {
		final BusyState bs = new BusyState();
		meetings.forEach(meeting -> bs.add(meeting.getStart(), meeting.getEnd()));
		final DateTimeFormatter formatter = getDateFormatter();
		return new RoomAvailabilityItem(room,
				!bs.isBusy(when),
				bs.currentStateUntil(when)
						.filter(when.toLocalDate().plusDays(1).atStartOfDay(getZoneId())::isAfter)
						.map(ZonedDateTime::toLocalTime)
						.orElse(null),
				formatter);
	}

	private Room[] rooms = new Room[] { new Room() };

	private boolean availableRoomsToTop = false;

	private String dateFormat = "HH:mm";

	@Property(name = "Rooms", description = "The rooms to be checked and to be displayed in the widget.")
	public Room[] getRooms() {
		return rooms;
	}

	public void setRooms(final Room[] rooms) {
		this.rooms = rooms;
	}

	public boolean isAvailableRoomsToTop() {
		return availableRoomsToTop;
	}

	@Property(name = "Available Rooms To Top",
			description = "If selected the available rooms will be moved to the top of the room list.")
	public void setAvailableRoomsToTop(final boolean availableRoomsToTop) {
		this.availableRoomsToTop = availableRoomsToTop;
	}

	@Property(name = "Date Format", description = "How to display the date in the widget")
	public String getDateFormatString() {
		return Strings.isNullOrEmpty(dateFormat) ? "HH:mm" : dateFormat;
	}

	public DateTimeFormatter getDateFormatter() {
		return DateTimeFormatter.ofPattern(getDateFormatString());
	}

	public void setDateFormatString(final String dateFormat) {
		this.dateFormat = dateFormat;
	}

	public static final class RoomAvailabilityListEvent extends OversigtEvent {

		private final List<RoomAvailabilityItem> items;

		public RoomAvailabilityListEvent(final List<RoomAvailabilityItem> items) {
			this.items = items;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (items == null ? 0 : items.hashCode());
			return result;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final RoomAvailabilityListEvent other = (RoomAvailabilityListEvent) obj;
			if (items == null) {
				if (other.items != null) {
					return false;
				}
			} else if (!items.equals(other.items)) {
				return false;
			}
			return true;
		}
	}

	public static final class RoomAvailabilityItem {

		private final String clazz;

		private final String name;

		private final String number;

		private final String status;

		private final boolean free;

		private final Optional<LocalTime> until;

		public RoomAvailabilityItem(final Room room,
				final boolean free,
				final LocalTime until,
				final DateTimeFormatter formatter) {
			clazz = free ? "free" : "occupied";
			name = room.getName();
			number = room.getRoomNumber();
			this.free = free;
			this.until = Optional.ofNullable(until);
			status = (free ? "Free" : "Busy") + (until == null ? " Today" : " until " + formatter.format(until)); // TODO
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (clazz == null ? 0 : clazz.hashCode());
			result = prime * result + (name == null ? 0 : name.hashCode());
			result = prime * result + (number == null ? 0 : number.hashCode());
			result = prime * result + (status == null ? 0 : status.hashCode());
			return result;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final RoomAvailabilityItem other = (RoomAvailabilityItem) obj;
			if (clazz == null) {
				if (other.clazz != null) {
					return false;
				}
			} else if (!clazz.equals(other.clazz)) {
				return false;
			}
			if (name == null) {
				if (other.name != null) {
					return false;
				}
			} else if (!name.equals(other.name)) {
				return false;
			}
			if (number == null) {
				if (other.number != null) {
					return false;
				}
			} else if (!number.equals(other.number)) {
				return false;
			}
			if (status == null) {
				if (other.status != null) {
					return false;
				}
			} else if (!status.equals(other.status)) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			return String.format("RoomAvailabilityItem [%s, %s, %s, %s]", clazz, name, number, status);
		}
	}

	private static class BusyState {
		private SortedSet<TimeSlice> slices = new TreeSet<>();

		synchronized boolean isBusy(final ZonedDateTime dt) {
			return slices.stream().anyMatch(s -> s.contains(dt));
		}

		synchronized Optional<ZonedDateTime> currentStateUntil(final ZonedDateTime dt) {
			return slices.stream()
					.filter(s -> s.isBefore(dt))
					.findFirst()
					.map(s -> s.start.isBefore(dt) ? s.end : s.start);
		}

		void add(final ZonedDateTime start, final ZonedDateTime end) {
			add(new TimeSlice(start, end));
		}

		synchronized void add(final TimeSlice nts) {
			// is the new slice completely within an existing?
			for (final TimeSlice slice : slices) {
				if (slice.containsCompletely(nts)) {
					return;
				}
			}

			// does the new slice contain another completely?
			final Iterator<TimeSlice> it = slices.iterator();
			while (it.hasNext()) {
				if (nts.containsCompletely(it.next())) {
					it.remove();
				}
			}

			// does the new slice interfere with something?
			boolean expandedSomething = false;

			// does the new slice interfere with the previous slice?
			final List<TimeSlice> befores = getTimeSlicesCompletlyBefore(nts.end);
			TimeSlice prev = null;
			if (!befores.isEmpty()) {
				prev = befores.get(befores.size() - 1);
				if (prev.end.isAfter(nts.start) || prev.end.isEqual(nts.start)) {
					prev.end = nts.end;
					expandedSomething = true;
				}
			}

			// does the new slice interfere with the next slice?
			final List<TimeSlice> afters = getTimeSlicesCompletlyAfter(nts.start);
			TimeSlice next = null;
			if (!afters.isEmpty()) {
				next = afters.get(0);
				if (next.start.isBefore(nts.end) || next.start.isEqual(nts.end)) {
					next.start = nts.start;
					expandedSomething = true;
				}
			}

			// maybe add new slice
			if (!expandedSomething) {
				slices.add(nts);
			} else if (prev != null && next != null && (prev.end.isEqual(next.start) || prev.end.isAfter(next.start))) {
				// combine both slices
				slices.remove(prev);
				slices.remove(next);
				slices.add(new TimeSlice(prev.start, next.end));
			}
		}

		List<TimeSlice> getTimeSlicesCompletlyBefore(final ZonedDateTime point) {
			return getFilteredTimeSlices(s -> s.end.isBefore(point));
		}

		List<TimeSlice> getTimeSlicesCompletlyAfter(final ZonedDateTime point) {
			return getFilteredTimeSlices(s -> s.start.isAfter(point));
		}

		private List<TimeSlice> getFilteredTimeSlices(final Predicate<? super TimeSlice> predicate) {
			return slices.stream().filter(predicate).collect(Collectors.toList());
		}

		@Override
		public String toString() {
			return slices.toString();
		}
	}

	private static class TimeSlice implements Comparable<TimeSlice> {
		private ZonedDateTime start;

		private ZonedDateTime end;

		TimeSlice(final ZonedDateTime start, final ZonedDateTime end) {
			this.start = Objects.requireNonNull(start);
			this.end = Objects.requireNonNull(end);
		}

		boolean containsCompletely(final TimeSlice that) {
			return contains(that.start) && contains(that.end);
		}

		boolean contains(final ZonedDateTime that) {
			return start.isBefore(that) && end.isAfter(that);
		}

		boolean isBefore(final ZonedDateTime dt) {
			return end.isAfter(dt);
		}

		@Override
		public String toString() {
			final StringBuilder builder = new StringBuilder();
			builder.append("<<");
			builder.append(start);
			builder.append(" - ");
			builder.append(end);
			builder.append(">>");
			return builder.toString();
		}

		@Override
		public int compareTo(final TimeSlice that) {
			return start.compareTo(that.start);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (end == null ? 0 : end.hashCode());
			result = prime * result + (start == null ? 0 : start.hashCode());
			return result;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final TimeSlice other = (TimeSlice) obj;
			if (end == null) {
				if (other.end != null) {
					return false;
				}
			} else if (!end.equals(other.end)) {
				return false;
			}
			if (start == null) {
				if (other.start != null) {
					return false;
				}
			} else if (!start.equals(other.start)) {
				return false;
			}
			return true;
		}
	}

}
