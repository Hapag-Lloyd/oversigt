package com.hlag.oversigt.connect.exchange;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.property.complex.availability.CalendarEvent;

public final class Meeting {

	private final Room room;

	private final ZonedDateTime start;

	private final ZonedDateTime end;

	private final String organizer;

	private final Appointment appointment;

	Meeting(final Room room, final CalendarEvent event, final ZoneId zone) {
		this(room,
				null,
				event.getStartTime(),
				event.getEndTime(),
				event.getDetails() != null ? event.getDetails().getSubject() : "Unknown",
				zone);
	}

	Meeting(final Room room,
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
		return String.format("Meeting [%s, %s - %s, %s]", room.getName(), start, end, organizer);
	}
}
