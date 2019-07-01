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

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * @author Olaf Neumann
 */
@EventSource(view = "MeetingRooms",
		displayName = "Microsoft Exchange Meeting Room Availability",
		description = "Shows the room availability from Microsoft Exchange for a configurable collection of rooms.",
		hiddenDataItems = { "moreinfo" })
public class ExchangeRoomAvailabilityEventSource extends AbstractExchangeEventSource<RoomAvailabilityListEvent> {
	public ExchangeRoomAvailabilityEventSource() {
		// no fields to be initialized
	}

	@Override
	protected Optional<RoomAvailabilityListEvent> produceExchangeEvent() throws Exception {
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

		return Optional.of(new RoomAvailabilityListEvent(sortedItems));
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
						.filter(when.toLocalDate() //
								.plusDays(1)
								.atStartOfDay(getZoneId())::isAfter)
						.map(ZonedDateTime::toLocalTime),
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
			result = prime * result + items.hashCode();
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
			if (getClass() != obj.getClass()) {
				return false;
			}
			final RoomAvailabilityListEvent other = (RoomAvailabilityListEvent) obj;
			if (!items.equals(other.items)) {
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
				final Optional<LocalTime> until,
				final DateTimeFormatter formatter) {
			clazz = free ? "free" : "occupied";
			name = room.getName();
			number = room.getRoomNumber();
			this.free = free;
			this.until = until;
			status = (free ? "Free" : "Busy")
					+ (!until.isPresent() ? " Today" : " until " + formatter.format(until.get()));
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + clazz.hashCode();
			result = prime * result + name.hashCode();
			result = prime * result + number.hashCode();
			result = prime * result + status.hashCode();
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
			if (getClass() != obj.getClass()) {
				return false;
			}
			final RoomAvailabilityItem other = (RoomAvailabilityItem) obj;
			if (!clazz.equals(other.clazz)) {
				return false;
			}
			if (!name.equals(other.name)) {
				return false;
			}
			if (!number.equals(other.number)) {
				return false;
			}
			if (!status.equals(other.status)) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			return String.format("RoomAvailabilityItem [%s, %s, %s, %s]", clazz, name, number, status);
		}
	}

	private static final class BusyState {
		private SortedSet<TimeSlice> slices = new TreeSet<>();

		private BusyState() {
			// no fields to be initialized
		}

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

	private static final class TimeSlice implements Comparable<TimeSlice> {
		private ZonedDateTime start;

		private ZonedDateTime end;

		private TimeSlice(final ZonedDateTime start, final ZonedDateTime end) {
			this.start = start;
			this.end = end;
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
		public int compareTo(@Nullable final TimeSlice that) {
			return start.compareTo(Optional.ofNullable(that).map(ts -> ts.start).orElse(null));
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + end.hashCode();
			result = prime * result + start.hashCode();
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
			if (getClass() != obj.getClass()) {
				return false;
			}
			final TimeSlice other = (TimeSlice) obj;
			if (!end.equals(other.end)) {
				return false;
			}
			if (!start.equals(other.start)) {
				return false;
			}
			return true;
		}
	}

}
