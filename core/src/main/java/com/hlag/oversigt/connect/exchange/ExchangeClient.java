package com.hlag.oversigt.connect.exchange;

import java.io.Closeable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.core.service.item.Task;

/**
 * A client allowing to read simple information from Exchange client. This
 * client simply wraps a library from Microsoft for easier usage.
 *
 * @author neumaol
 *
 */
public interface ExchangeClient extends Closeable {

	/**
	 * Read all mails from the folder of the logged in user
	 *
	 * @param folderName the name of the folder to read
	 * @return a list of all {@link Mail}s from this folder
	 * @throws Exception if something fails
	 */
	List<Mail> loadMails(String folderName) throws Exception;

	/**
	 * Read all tasks from 'Dashboard' the folder of the logged in user
	 *
	 * @return all {@link Task}s from the dashboard folder
	 * @throws Exception if something fails
	 */
	List<Task> loadTasks() throws Exception;

	/**
	 * Read all appointments of the currently logged in user in the given time range
	 *
	 * @param from  when to start looking for appointments
	 * @param until when to stop looking for appointments
	 * @return a list of all appointments
	 */
	List<Appointment> loadAppointments(ZonedDateTime from, ZonedDateTime until);

	/**
	 * Read meetings of the given resource for the specified day.
	 *
	 * @param rooms  the resources for which to retreive the meetings
	 * @param day    the day when to look for meetings
	 * @param zoneId the zoneId of the server
	 * @return a map of room to meetings
	 * @throws Exception if something fails
	 */
	Map<Room, List<Meeting>> getMeetings(List<Room> rooms, LocalDate day, ZoneId zoneId) throws Exception;
}
