package com.hlag.oversigt.connect.exchange;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public final class Meeting {
	private final Room room;

	private final ZonedDateTime startTime;

	private final ZonedDateTime endTime;

	Meeting(final Room room, final Date startTime, final Date endTime, final ZoneId zoneId) {
		this(room, startTime.toInstant().atZone(zoneId), endTime.toInstant().atZone(zoneId));
	}

	private Meeting(final Room room, final ZonedDateTime startTime, final ZonedDateTime endTime) {
		this.room = room;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public Room getRoom() {
		return room;
	}

	public ZonedDateTime getStart() {
		return startTime;
	}

	public ZonedDateTime getEnd() {
		return endTime;
	}

	@Override
	public String toString() {
		return String.format("Meeting [%s, %s - %s, %s]", room.getName(), startTime, endTime);
	}
}
