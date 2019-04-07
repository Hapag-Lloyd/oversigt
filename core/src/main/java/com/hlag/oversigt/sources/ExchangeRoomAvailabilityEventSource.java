package com.hlag.oversigt.sources;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Strings;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.properties.JsonBasedData;
import com.hlag.oversigt.sources.ExchangeRoomAvailabilityEventSource.RoomAvailabilityListEvent;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.enumeration.availability.AvailabilityData;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.exception.service.remote.ServiceRequestException;
import microsoft.exchange.webservices.data.core.exception.service.remote.ServiceResponseException;
import microsoft.exchange.webservices.data.core.response.AttendeeAvailability;
import microsoft.exchange.webservices.data.core.service.folder.CalendarFolder;
import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.misc.availability.AttendeeInfo;
import microsoft.exchange.webservices.data.misc.availability.GetUserAvailabilityResults;
import microsoft.exchange.webservices.data.misc.availability.TimeWindow;
import microsoft.exchange.webservices.data.property.complex.FolderId;
import microsoft.exchange.webservices.data.property.complex.Mailbox;
import microsoft.exchange.webservices.data.property.complex.availability.CalendarEvent;
import microsoft.exchange.webservices.data.search.CalendarView;
import microsoft.exchange.webservices.data.search.FindItemsResults;

/**
 * @author Olaf Neumann
 */
@EventSource(view = "MeetingRooms",
		displayName = "Microsoft Exchange Meeting Room Availability",
		description = "Shows the room availability from Microsoft Exchange for a configurable collection of rooms.",
		hiddenDataItems = { "moreinfo" })
public class ExchangeRoomAvailabilityEventSource extends AbstractExchangeEventSource<RoomAvailabilityListEvent> {
	private static final boolean FAIL_SAFE = true;

	@Override
	protected RoomAvailabilityListEvent produceExchangeEvent() throws Exception {
		final ZonedDateTime now = ZonedDateTime.now(getZoneId());
		final Map<Room, List<Meeting>> meetings
				= getMeetings(getRooms(), createExchangeService().get(), now.toLocalDate());
		if (meetings == null) {
			return null;
		}

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

	private Map<Room, List<Meeting>> getMeetings(final Room[] rooms, final ExchangeService service, final LocalDate day)
			throws Exception {
		if (FAIL_SAFE) {
			return getMeetingsViaAttendeeInfo(rooms,
					service,
					day.atStartOfDay(getZoneId()),
					day.plusDays(1).atStartOfDay(getZoneId()));
		}
		return Stream.of(rooms)
				.collect(Collectors.toMap(Function.identity(), room -> getMeetingsViaAppointment(room, service, day)));
	}

	private List<Meeting> getMeetingsViaAppointment(final Room room,
			final ExchangeService service,
			final LocalDate day) {
		try {
			final CalendarFolder cf = CalendarFolder.bind(service,
					new FolderId(WellKnownFolderName.Calendar, new Mailbox(room.smtpAddress)));
			final CalendarView cv
					= getCalendarView(day.atStartOfDay(getZoneId()), day.atStartOfDay(getZoneId()).plusDays(1));

			final FindItemsResults<Appointment> apps = cf.findAppointments(cv);
			final List<Meeting> meetings = new ArrayList<>();
			for (final Appointment app : apps) {
				meetings.add(new Meeting(room, app, app.getStart(), app.getEnd(), app.getSubject(), getZoneId()));
			}
			return meetings;
		} catch (final Exception e) {
			if (e instanceof ServiceRequestException && "The request failed. 40".equals(e.getMessage())) {
				// Happens sometimes. Ignore it
				return null;
			} else if (e instanceof ServiceResponseException
					&& "The specified folder could not be found in the store.".equals(e.getMessage())) {
						getLogger().warn("Unable to get Meetings for " + room.name + ". " + e.getMessage(), e);
						return null;
					}
			throw new RuntimeException("Unable to get appointments", e);
		}
	}

	private Map<Room, List<Meeting>> getMeetingsViaAttendeeInfo(final Room[] rooms,
			final ExchangeService service,
			final ZonedDateTime from,
			final ZonedDateTime to) throws Exception {
		final Instant fromInstant = from.toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant();
		final Instant toInstant = to.toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant();

		final List<AttendeeInfo> attendees
				= Arrays.asList(rooms).stream().map(r -> new AttendeeInfo(r.smtpAddress)).collect(Collectors.toList());

		final GetUserAvailabilityResults result
				= service.getUserAvailability(attendees, getTimeWindow(from, to), AvailabilityData.FreeBusy);

		int index = 0;
		final Map<Room, List<Meeting>> meetings = new LinkedHashMap<>();
		for (final AttendeeAvailability availability : result.getAttendeesAvailability()) {
			final Room room = rooms[index];
			meetings.put(room,
					availability.getCalendarEvents()
							.stream()
							.filter(e -> e.getStartTime().toInstant().isBefore(toInstant))
							.filter(e -> e.getEndTime().toInstant().isAfter(fromInstant))
							.map(e -> new Meeting(room, e, getZoneId()))
							.collect(Collectors.toList()));
			index += 1;
		}

		return meetings;
	}

	private TimeWindow getTimeWindow(final ZonedDateTime fromTime, final ZonedDateTime toTime) {
		if (!fromTime.isBefore(toTime)) {
			throw new RuntimeException();
		}

		final LocalDate fromDate = fromTime.toLocalDate();
		LocalDate toDate = toTime.toLocalDate();
		if (toTime.isAfter(toDate.atStartOfDay(getZoneId())) || fromDate.isEqual(toDate)) {
			toDate = toDate.plusDays(1);
		}

		// Following the UTC conversion at
		// TimeWindow.writeToXmlUnscopedDatesOnly(EwsServiceXmlWriter, String)
		final Date start = Date.from(fromDate.atStartOfDay(ZoneOffset.UTC).toInstant());
		final Date end = Date.from(toDate.atStartOfDay(ZoneOffset.UTC).toInstant());

		return new TimeWindow(start, end);
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

	@JsonHint(headerTemplate = "{{self.name}}", arrayStyle = ArrayStyle.TABLE)
	public static class Room implements JsonBasedData, Comparable<Room> {
		private String name = "RoomName";

		private String smtpAddress = "roomname@exchange.com";

		private String roomNumber = "123";

		@Override
		public int compareTo(final Room that) {
			return String.CASE_INSENSITIVE_ORDER.compare(name, that.name);
		}

		@Override
		public String toString() {
			return String.format("Room [name=%s, smtpAddress=%s, roomNumber=%s]", name, smtpAddress, roomNumber);
		}
	}

	public static final class Meeting {

		private final Room room;

		private final ZonedDateTime start;

		private final ZonedDateTime end;

		private final String organizer;

		private final Appointment appointment;

		private Meeting(final Room room, final CalendarEvent event, final ZoneId zone) {
			this(room,
					null,
					event.getStartTime(),
					event.getEndTime(),
					event.getDetails() != null ? event.getDetails().getSubject() : "Unknown",
					zone);
		}

		private Meeting(final Room room,
				final Appointment appointment,
				final Date start,
				final Date end,
				final String organizer,
				final ZoneId zone) {
			this(room, appointment, start.toInstant().atZone(zone), end.toInstant().atZone(zone), organizer);
		}

		private Meeting(final Room room,
				final Appointment appointment,
				final ZonedDateTime start,
				final ZonedDateTime end,
				final String organizer) {
			this.room = room;
			this.start = start;
			this.end = end;
			this.organizer = organizer;
			this.appointment = appointment;
		}

		public Room getRoom() {
			return room;
		}

		public ZonedDateTime getStart() {
			return start;
		}

		public String getOrganizer() {
			return organizer;
		}

		public ZonedDateTime getEnd() {
			return end;
		}

		Appointment getAppointment() {
			return appointment;
		}

		@Override
		public String toString() {
			return String.format("Meeting [%s, %s - %s, %s]", room.name, start, end, organizer);
		}
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
			name = room.name;
			number = room.roomNumber;
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
