package com.hlag.oversigt.connect.exchange;

import static java.util.stream.Collectors.toList;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.enumeration.availability.AvailabilityData;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.search.FolderTraversal;
import microsoft.exchange.webservices.data.core.enumeration.search.SortDirection;
import microsoft.exchange.webservices.data.core.exception.service.remote.ServiceRequestException;
import microsoft.exchange.webservices.data.core.exception.service.remote.ServiceResponseException;
import microsoft.exchange.webservices.data.core.response.AttendeeAvailability;
import microsoft.exchange.webservices.data.core.service.folder.CalendarFolder;
import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.core.service.item.EmailMessage;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.item.Task;
import microsoft.exchange.webservices.data.core.service.schema.FolderSchema;
import microsoft.exchange.webservices.data.core.service.schema.ItemSchema;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import microsoft.exchange.webservices.data.misc.availability.AttendeeInfo;
import microsoft.exchange.webservices.data.misc.availability.GetUserAvailabilityResults;
import microsoft.exchange.webservices.data.misc.availability.TimeWindow;
import microsoft.exchange.webservices.data.property.complex.FolderId;
import microsoft.exchange.webservices.data.property.complex.Mailbox;
import microsoft.exchange.webservices.data.search.CalendarView;
import microsoft.exchange.webservices.data.search.FindFoldersResults;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.FolderView;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.search.filter.SearchFilter;

public class SimpleExchangeClient implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(SimpleExchangeClient.class);

	private static final int NUMBER_OF_ITEMS_TO_LOAD_IN_ONE_CHUNK = 50;

	private static final boolean FAIL_SAFE = true;

	private final ExchangeService service;

	public SimpleExchangeClient(final URI exchangeServerUri, final String username, final String password) {
		service = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
		service.setCredentials(new WebCredentials(username, password));
		service.setUrl(exchangeServerUri);
	}

	public ExchangeService getService() {
		return service;
	}

	@Override
	public void close() throws IOException {
		getService().close();
	}

	private FolderId getFolderId(final WellKnownFolderName searchRoot, final String folderName) throws Exception {
		final SearchFilter searchFilter = new SearchFilter.IsEqualTo(FolderSchema.DisplayName, folderName);
		final FolderView folderView = new FolderView(2);
		folderView.setTraversal(FolderTraversal.Deep);
		final FindFoldersResults folders = getService().findFolders(searchRoot, searchFilter, folderView);
		if (folders.getTotalCount() != 1) {
			throw new RuntimeException(String.format("Folder '%s' wasn't found. Found %s folders matching the name.",
					folderName,
					folders.getTotalCount()));
		}
		return folders.getFolders().get(0).getId();
	}

	public List<Mail> loadMails(final String folderName) throws Exception {
		return loadItems(EmailMessage.class, WellKnownFolderName.MsgFolderRoot, folderName, SortDirection.Ascending)//
				.stream()
				.map(Mail::create)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(toList());
	}

	public List<Task> loadTasks() throws Exception {
		return loadItems(Task.class, WellKnownFolderName.Root, "Dashboard", SortDirection.Descending)//
				.stream()
				.filter(t -> {
					try {
						return !t.getIsComplete();
					} catch (final Exception e) {
						throw new RuntimeException("Unable to read complete status of task", e);
					}
				})
				.collect(toList());
	}

	private <T> List<T> loadItems(final Class<T> clazz,
			final WellKnownFolderName searchRoot,
			final String folderName,
			final SortDirection sortDirection) throws Exception {
		final List<T> result = new ArrayList<>();

		final ItemView view = new ItemView(NUMBER_OF_ITEMS_TO_LOAD_IN_ONE_CHUNK);
		view.getOrderBy().add(ItemSchema.DateTimeCreated, sortDirection);
		FindItemsResults<Item> findResults;
		do {
			final FolderId folderId = getFolderId(searchRoot, folderName);
			findResults = getService().findItems(folderId, view);
			result.addAll(findResults//
					.getItems()
					.stream()
					.filter(item -> item != null && clazz.isAssignableFrom(item.getClass()))
					.map(clazz::cast)
					.collect(toList()));
			view.setOffset(view.getOffset() + NUMBER_OF_ITEMS_TO_LOAD_IN_ONE_CHUNK);
		} while (findResults.isMoreAvailable());

		return result;
	}

	public List<Appointment> loadAppointments(final ZonedDateTime from, final ZonedDateTime until) {
		final CalendarView calendarView = getCalendarView(from, until);

		final FindItemsResults<Appointment> findResults;
		try {
			findResults = getService().findAppointments(WellKnownFolderName.Calendar, calendarView);
		} catch (final Exception e) {
			throw new RuntimeException("Unable to find appointments", e);
		}
		return new ArrayList<>(findResults.getItems());
	}

	public Map<Room, List<Meeting>> getMeetings(final List<Room> rooms, final LocalDate day, final ZoneId zoneId)
			throws Exception {
		if (FAIL_SAFE) {
			return getMeetingsViaAttendeeInfo(rooms,
					day.atStartOfDay(zoneId),
					day.plusDays(1).atStartOfDay(zoneId),
					zoneId);
		}
		return rooms.stream()
				.collect(Collectors.toMap(Function.identity(), room -> getMeetingsViaAppointment(room, day, zoneId)));
	}

	private List<Meeting> getMeetingsViaAppointment(final Room room, final LocalDate day, final ZoneId zoneId) {
		try {
			final CalendarFolder cf = CalendarFolder.bind(getService(),
					new FolderId(WellKnownFolderName.Calendar, new Mailbox(room.getSmtpAddress())));
			final CalendarView cv = getCalendarView(day.atStartOfDay(zoneId), day.atStartOfDay(zoneId).plusDays(1));

			final FindItemsResults<Appointment> apps = cf.findAppointments(cv);
			final List<Meeting> meetings = new ArrayList<>();
			for (final Appointment app : apps) {
				meetings.add(new Meeting(room, app, app.getStart(), app.getEnd(), app.getSubject(), zoneId));
			}
			return meetings;
		} catch (final Exception e) {
			if (e instanceof ServiceRequestException && "The request failed. 40".equals(e.getMessage())) {
				// Happens sometimes. Ignore it
				return null;
			} else if (e instanceof ServiceResponseException
					&& "The specified folder could not be found in the store.".equals(e.getMessage())) {
						LOGGER.warn("Unable to get Meetings for " + room.getName() + ". " + e.getMessage(), e);
						return null;
					}
			throw new RuntimeException("Unable to get appointments", e);
		}
	}

	private Map<Room, List<Meeting>> getMeetingsViaAttendeeInfo(final List<Room> rooms,
			final ZonedDateTime from,
			final ZonedDateTime to,
			final ZoneId zoneId) throws Exception {
		final Instant fromInstant = from.toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant();
		final Instant toInstant = to.toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant();

		final List<AttendeeInfo> attendees
				= rooms.stream().map(r -> new AttendeeInfo(r.getSmtpAddress())).collect(Collectors.toList());

		final GetUserAvailabilityResults result = getService()
				.getUserAvailability(attendees, getTimeWindow(from, to, zoneId), AvailabilityData.FreeBusy);

		int index = 0;
		final Map<Room, List<Meeting>> meetings = new LinkedHashMap<>();
		for (final AttendeeAvailability availability : result.getAttendeesAvailability()) {
			final Room room = rooms.get(index);
			meetings.put(room,
					availability.getCalendarEvents()
							.stream()
							.filter(e -> e.getStartTime().toInstant().isBefore(toInstant))
							.filter(e -> e.getEndTime().toInstant().isAfter(fromInstant))
							.map(e -> new Meeting(room, e, zoneId))
							.collect(Collectors.toList()));
			index += 1;
		}

		return meetings;
	}

	private static CalendarView getCalendarView(final ZonedDateTime start, final ZonedDateTime end) {
		return new CalendarView(Date.from(start.toInstant()), Date.from(end.toInstant()));
	}

	private static TimeWindow getTimeWindow(final ZonedDateTime fromTime,
			final ZonedDateTime toTime,
			final ZoneId zoneId) {
		if (!fromTime.isBefore(toTime)) {
			throw new RuntimeException();
		}

		final LocalDate fromDate = fromTime.toLocalDate();
		LocalDate toDate = toTime.toLocalDate();
		if (toTime.isAfter(toDate.atStartOfDay(zoneId)) || fromDate.isEqual(toDate)) {
			toDate = toDate.plusDays(1);
		}

		// Following the UTC conversion at
		// TimeWindow.writeToXmlUnscopedDatesOnly(EwsServiceXmlWriter, String)
		final Date start = Date.from(fromDate.atStartOfDay(ZoneOffset.UTC).toInstant());
		final Date end = Date.from(toDate.atStartOfDay(ZoneOffset.UTC).toInstant());

		return new TimeWindow(start, end);
	}
}
