package com.hlag.oversigt.connect.exchange;

import java.io.Closeable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.core.service.item.Task;

public interface ExchangeClient extends Closeable {

	List<Mail> loadMails(String folderName) throws Exception;

	List<Task> loadTasks() throws Exception;

	List<Appointment> loadAppointments(ZonedDateTime from, ZonedDateTime until);

	Map<Room, List<Meeting>> getMeetings(List<Room> rooms, LocalDate day, ZoneId zoneId) throws Exception;
}
