package com.hlag.oversigt.sources;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import com.hlag.oversigt.util.Utils;

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
@EventSource(view = "MeetingRooms", displayName = "Microsoft Exchange Meeting Room Availability", description = "Shows the room availability from Microsoft Exchange for a configurable collection of rooms.", hiddenDataItems = {
		"moreinfo" })
public class ExchangeRoomAvailabilityEventSource extends AbstractExchangeEventSource<RoomAvailabilityListEvent> {
	private static final boolean FAIL_SAFE = true;

	@Override
	protected RoomAvailabilityListEvent produceExchangeEvent() throws Exception {
		ZonedDateTime now = ZonedDateTime.now(getZoneId());
		Map<Room, List<Meeting>> meetings = getMeetings(getRooms(), createExchangeService().get(), now.toLocalDate());
		if (meetings == null) {
			return null;
		}

		List<RoomAvailabilityItem> avl = meetings.entrySet()
				.stream()
				.map(e -> checkRoomAvailability(now, e.getKey(), e.getValue()))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		if (isAvailableRoomsToTop()) {
			avl = Utils.concat(avl.stream().filter(a -> a.free), avl.stream().filter(a -> !a.free))
					.collect(Collectors.toList());
		}

		return new RoomAvailabilityListEvent(avl);
	}

	private RoomAvailabilityItem checkRoomAvailability(ZonedDateTime when, Room room, Collection<Meeting> meetings) {
		BusyState bs = new BusyState();
		meetings.forEach(meeting -> bs.add(meeting.getStart(), meeting.getEnd()));
		DateTimeFormatter formatter = getDateFormatter();
		return new RoomAvailabilityItem(room,
				!bs.isBusy(when),
				bs.currentStateUntil(when)
						.filter(LocalDate.now(getZoneId()).plusDays(1).atStartOfDay(getZoneId())::isAfter)
						.map(ZonedDateTime::toLocalTime)
						.map(formatter::format)
						.orElse(null));
	}

	private Map<Room, List<Meeting>> getMeetings(Room[] rooms, ExchangeService service, LocalDate day)
			throws Exception {
		if (FAIL_SAFE) {
			return getMeetings_viaAttendeeInfo(rooms,
					service,
					day.atStartOfDay(getZoneId()),
					day.plusDays(1).atStartOfDay(getZoneId()));
		} else {
			return Stream.of(rooms).collect(
					Collectors.toMap(Function.identity(), room -> getMeetings_viaAppointment(room, service, day)));
		}
	}

	private List<Meeting> getMeetings_viaAppointment(Room room, ExchangeService service, LocalDate day) {
		try {
			CalendarFolder cf = CalendarFolder.bind(service,
					new FolderId(WellKnownFolderName.Calendar, new Mailbox(room.smtpAddress)));
			CalendarView cv = getCalendarView(day.atStartOfDay(getZoneId()), day.atStartOfDay(getZoneId()).plusDays(1));

			FindItemsResults<Appointment> apps = cf.findAppointments(cv);
			List<Meeting> meetings = new ArrayList<>();
			for (Appointment app : apps) {
				meetings.add(new Meeting(room, app, app.getStart(), app.getEnd(), app.getSubject(), getZoneId()));
			}
			return meetings;
		} catch (Exception e) {
			if (e instanceof ServiceRequestException && "The request failed. 40".equals(e.getMessage())) {
				//Happens sometimes. Ignore it
				return null;
			} else if (e instanceof ServiceResponseException
					&& "The specified folder could not be found in the store.".equals(e.getMessage())) {
				getLogger().warn("Unable to get Meetings for " + room.name + ". " + e.getMessage(), e);
				return null;
			}
			throw new RuntimeException("Unable to get appointments", e);
		}
	}

	private Map<Room, List<Meeting>> getMeetings_viaAttendeeInfo(Room[] rooms,
			ExchangeService service,
			ZonedDateTime from,
			ZonedDateTime to) throws Exception {
		Instant fromInstant = from.toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant();
		Instant toInstant = to.toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant();

		List<AttendeeInfo> attendees = Arrays.asList(rooms).stream().map(r -> new AttendeeInfo(r.smtpAddress)).collect(
				Collectors.toList());

		GetUserAvailabilityResults result = service
				.getUserAvailability(attendees, getTimeWindow(from, to), AvailabilityData.FreeBusy);

		int index = 0;
		Map<Room, List<Meeting>> meetings = new LinkedHashMap<>();
		for (AttendeeAvailability availability : result.getAttendeesAvailability()) {
			Room room = rooms[index];
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

	private TimeWindow getTimeWindow(ZonedDateTime fromTime, ZonedDateTime toTime) {
		assert fromTime.isBefore(toTime);

		LocalDate fromDate = fromTime.toLocalDate();
		LocalDate toDate = toTime.toLocalDate();
		if (toTime.isAfter(toDate.atStartOfDay(getZoneId())) || fromDate.isEqual(toDate)) {
			toDate = toDate.plusDays(1);
		}

		// Following the UTC conversion at TimeWindow.writeToXmlUnscopedDatesOnly(EwsServiceXmlWriter, String)
		Date start = Date.from(fromDate.atStartOfDay(ZoneOffset.UTC).toInstant());
		Date end = Date.from(toDate.atStartOfDay(ZoneOffset.UTC).toInstant());

		return new TimeWindow(start, end);
	}

	private Room[] rooms = new Room[] { new Room() };
	private boolean availableRoomsToTop = false;
	private String dateFormat = "HH:mm";

	@Property(name = "Rooms", description = "The rooms to be checked and to be displayed in the widget.")
	public Room[] getRooms() {
		return rooms;
	}

	public void setRooms(Room[] rooms) {
		this.rooms = rooms;
	}

	public boolean isAvailableRoomsToTop() {
		return availableRoomsToTop;
	}

	@Property(name = "Available Rooms To Top", description = "If selected the available rooms will be moved to the top of the room list.")
	public void setAvailableRoomsToTop(boolean availableRoomsToTop) {
		this.availableRoomsToTop = availableRoomsToTop;
	}

	@Property(name = "Date Format", description = "How to display the date in the widget")
	public String getDateFormatString() {
		return Strings.isNullOrEmpty(dateFormat) ? "HH:mm" : dateFormat;
	}

	public DateTimeFormatter getDateFormatter() {
		return DateTimeFormatter.ofPattern(getDateFormatString());
	}

	public void setDateFormatString(String dateFormat) {
		this.dateFormat = dateFormat;
	}

	@JsonHint(headerTemplate = "{{self.name}}", arrayStyle = ArrayStyle.TABLE)
	public static class Room implements JsonBasedData, Comparable<Room> {
		public String name = "RoomName";
		public String smtpAddress = "roomname@exchange.com";
		public String roomNumber = "123";

		@Override
		public int compareTo(Room that) {
			return String.CASE_INSENSITIVE_ORDER.compare(this.name, that.name);
		}

		@Override
		public String toString() {
			return String.format("Room [name=%s, smtpAddress=%s, roomNumber=%s]", name, smtpAddress, roomNumber);
		}
	}

	public static class Meeting {

		private final Room room;
		private final ZonedDateTime start;
		private final ZonedDateTime end;
		private final String organizer;
		private final Appointment appointment;

		private Meeting(Room room, CalendarEvent event, ZoneId zone) {
			this(
				room,
				null,
				event.getStartTime(),
				event.getEndTime(),
				event.getDetails() != null ? event.getDetails().getSubject() : "Unknown",
				zone);
		}

		private Meeting(Room room, Appointment appointment, Date start, Date end, String organizer, ZoneId zone) {
			this(room, appointment, start.toInstant().atZone(zone), end.toInstant().atZone(zone), organizer);
		}

		private Meeting(Room room, Appointment appointment, ZonedDateTime start, ZonedDateTime end, String organizer) {
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

		public RoomAvailabilityListEvent(List<RoomAvailabilityItem> items) {
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
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			RoomAvailabilityListEvent other = (RoomAvailabilityListEvent) obj;
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

		private String clazz;
		private String name;
		private String number;
		private String status;
		private volatile boolean free;

		public RoomAvailabilityItem(Room room, Boolean free, String until) {
			clazz = free != null && free ? "free" : "occupied";
			name = room.name;
			number = room.roomNumber;
			this.free = free;

			if (free == null) {
				status = "";
			} else {
				status = free ? "Free" : "Busy"; // TODO internationalization
			}
			status += until == null ? " Today" : " until " + until; // TODO internationalization
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
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			RoomAvailabilityItem other = (RoomAvailabilityItem) obj;
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

		synchronized boolean isBusy(ZonedDateTime dt) {
			return slices.stream().filter(s -> s.contains(dt)).findAny().isPresent();
		}

		synchronized Optional<ZonedDateTime> currentStateUntil(ZonedDateTime dt) {
			return slices.stream().filter(s -> s.isBefore(dt)).findFirst().map(
					s -> s.start.isBefore(dt) ? s.end : s.start);
		}

		void add(ZonedDateTime start, ZonedDateTime end) {
			add(new TimeSlice(start, end));
		}

		synchronized void add(TimeSlice nts) {
			// is the new slice completely within an existing?
			for (TimeSlice slice : slices) {
				if (slice.containsCompletely(nts)) {
					return;
				}
			}

			// does the new slice contain another completely?
			Iterator<TimeSlice> it = slices.iterator();
			while (it.hasNext()) {
				if (nts.containsCompletely(it.next())) {
					it.remove();
				}
			}

			// does the new slice interfere with something?
			boolean expandedSomething = false;

			// does the new slice interfere with the previous slice?
			List<TimeSlice> befores = getTimeSlicesCompletlyBefore(nts.end);
			TimeSlice prev = null;
			if (!befores.isEmpty()) {
				prev = befores.get(befores.size() - 1);
				if (prev.end.isAfter(nts.start) || prev.end.isEqual(nts.start)) {
					prev.end = nts.end;
					expandedSomething = true;
				}
			}

			// does the new slice interfere with the next slice?
			List<TimeSlice> afters = getTimeSlicesCompletlyAfter(nts.start);
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
			} else if (prev != null && next != null) {
				if (prev.end.isEqual(next.start) || prev.end.isAfter(next.start)) {
					// combine both slices
					slices.remove(prev);
					slices.remove(next);
					slices.add(new TimeSlice(prev.start, next.end));
				}
			}
		}

		List<TimeSlice> getTimeSlicesCompletlyBefore(ZonedDateTime point) {
			return getFilteredTimeSlices(s -> s.end.isBefore(point));
		}

		List<TimeSlice> getTimeSlicesCompletlyAfter(ZonedDateTime point) {
			return getFilteredTimeSlices(s -> s.start.isAfter(point));
		}

		private List<TimeSlice> getFilteredTimeSlices(Predicate<? super TimeSlice> predicate) {
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

		public TimeSlice(ZonedDateTime start, ZonedDateTime end) {
			this.start = Objects.requireNonNull(start);
			this.end = Objects.requireNonNull(end);
		}

		boolean containsCompletely(TimeSlice that) {
			return contains(that.start) && contains(that.end);
		}

		boolean contains(ZonedDateTime that) {
			return start.isBefore(that) && end.isAfter(that);
		}

		boolean isBefore(ZonedDateTime dt) {
			return end.isAfter(dt);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("<<");
			builder.append(start);
			builder.append(" - ");
			builder.append(end);
			builder.append(">>");
			return builder.toString();
		}

		@Override
		public int compareTo(TimeSlice that) {
			return this.start.compareTo(that.start);
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
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			TimeSlice other = (TimeSlice) obj;
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
