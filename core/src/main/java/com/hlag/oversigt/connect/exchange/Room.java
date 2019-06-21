package com.hlag.oversigt.connect.exchange;

import java.util.Optional;

import com.hlag.oversigt.properties.JsonBasedData;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Representation of a room
 *
 * @author neumaol
 *
 */
@JsonHint(headerTemplate = "{{self.name}}", arrayStyle = ArrayStyle.TABLE)
public class Room implements JsonBasedData, Comparable<Room> {
	private String name = "RoomName";

	private String smtpAddress = "roomname@exchange.com";

	private String roomNumber = "123";

	/** Create a room with default values. */
	public Room() {
		// leave default values
	}

	/**
	 * Create a room with the given details
	 *
	 * @param name        the name of the room
	 * @param smtpAddress the SMTP address of the room
	 * @param roomNumber  the number of the room
	 */
	public Room(final String name, final String smtpAddress, final String roomNumber) {
		this.name = name;
		this.smtpAddress = smtpAddress;
		this.roomNumber = roomNumber;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the smtpAddress
	 */
	public String getSmtpAddress() {
		return smtpAddress;
	}

	/**
	 * @return the roomNumber
	 */
	public String getRoomNumber() {
		return roomNumber;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(final String name) {
		this.name = name;
	}

	/**
	 * @param smtpAddress the smtpAddress to set
	 */
	public void setSmtpAddress(final String smtpAddress) {
		this.smtpAddress = smtpAddress;
	}

	/**
	 * @param roomNumber the roomNumber to set
	 */
	public void setRoomNumber(final String roomNumber) {
		this.roomNumber = roomNumber;
	}

	/** {@inheritDoc} */
	@Override
	public int compareTo(@Nullable final Room that) {
		return String.CASE_INSENSITIVE_ORDER.compare(name, Optional.ofNullable(that).map(Room::getName).orElse(""));
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("Room [name=%s, smtpAddress=%s, roomNumber=%s]", name, smtpAddress, roomNumber);
	}
}
