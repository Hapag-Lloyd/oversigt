package com.hlag.oversigt.connect.exchange;

import com.hlag.oversigt.properties.JsonBasedData;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;

@JsonHint(headerTemplate = "{{self.name}}", arrayStyle = ArrayStyle.TABLE)
public class Room implements JsonBasedData, Comparable<Room> {
	private String name = "RoomName";

	private String smtpAddress = "roomname@exchange.com";

	private String roomNumber = "123";

	public Room(final String name, final String smtpAddress, final String roomNumber) {
		this.name = name;
		this.smtpAddress = smtpAddress;
		this.roomNumber = roomNumber;
	}

	public String getName() {
		return name;
	}

	public String getRoomNumber() {
		return roomNumber;
	}

	public String getSmtpAddress() {
		return smtpAddress;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public void setRoomNumber(final String roomNumber) {
		this.roomNumber = roomNumber;
	}

	public void setSmtpAddress(final String smtpAddress) {
		this.smtpAddress = smtpAddress;
	}

	@Override
	public int compareTo(final Room that) {
		return String.CASE_INSENSITIVE_ORDER.compare(name, that.name);
	}

	@Override
	public String toString() {
		return String.format("Room [name=%s, smtpAddress=%s, roomNumber=%s]", name, smtpAddress, roomNumber);
	}
}
