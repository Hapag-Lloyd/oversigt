package com.hlag.oversigt.sources;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.google.common.base.Strings;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.properties.JsonBasedData;
import com.hlag.oversigt.sources.data.Birthday;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;
import com.hlag.oversigt.sources.event.TimelineEvent;

import de.jollyday.Holiday;
import de.jollyday.HolidayCalendar;
import de.jollyday.HolidayManager;
import de.jollyday.ManagerParameters;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.enumeration.property.LegacyFreeBusyStatus;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.exception.service.local.ServiceLocalException;
import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.search.CalendarView;
import microsoft.exchange.webservices.data.search.FindItemsResults;

/**
 * @author Constantin Pagenkopp
 */
@EventSource(displayName = "Calendar Timeline View", view = "Timeline")
public class TimelineEventSource extends AbstractExchangeEventSource<TimelineEvent> {
	// TODO internationalize these strings
	private static final String UNTIL = "bis ";
	private static final String TODAY = "Today";
	private static final String ALL_DAY_UNTIL = "ganztägig, bis ";
	private static final String ALL_DAY = "ganztägig";

	private Period maximumPointInFuture = Period.ofMonths(3);
	private int minimumEventCount = 0;

	private Color colorToday = Color.Green;//"#008000"; // green
	private Color colorAppointment = Color.Blue;//"#0000FF"; // blue
	private Color colorBirthday = Color.Yellow;//"#FFFF00"; // yellow
	private Color colorHoliday = Color.Grey;//"#808080"; // grey
	private Color colorOutOfOffice = Color.Blue;//"#808080"; // blue
	private Color colorMailbox = Color.Blue;//"#808080"; // blue

	private HolidayCalendar holidayCalendar = HolidayCalendar.GERMANY;
	private String holidayArea = "hh";

	private Locale locale = Locale.getDefault();

	private Birthday[] birthdays = new Birthday[0];

	private HolidayNameCorrection[] corrections = new HolidayNameCorrection[] { //
			new HolidayNameCorrection("Weihnachten", "1. Weihnachtstag"),
			new HolidayNameCorrection("Stephanstag", "2. Weihnachtstag"),
			new HolidayNameCorrection("Tag der Wiedervereinigung", "Tag der Deutschen Einheit") };

	@Override
	protected TimelineEvent produceExchangeEvent() throws Exception {
		TimelineEvent event = new TimelineEvent(maximumPointInFuture);
		LocalDate now = LocalDate.now();

		fillTimelineEvent(now, event);

		event.removeEvents(getMaximumPointInFuture(), getMinimumEventCount());

		// maybe add today
		if (!event.hasEventAt(now)) {
			event.addEvent(TODAY, now, now, false, colorToday);
		}

		return event;
	}

	protected void fillTimelineEvent(LocalDate now, TimelineEvent event) {
		addBirthdays(event, now);
		addHolidays(event, now);
		addExchangeCalendar(event, now);
	}

	private void addExchangeCalendar(TimelineEvent event, LocalDate now) {
		Optional<ExchangeService> service = createExchangeService();
		if (service.isPresent()) {
			/*
			 * start search two month in the past to fetch all running all day appointments, 5 month to handle a maternity leave for up to 2.5 months.
			 * running all day events are also shown if they started in the past and continue to now or the future.
			 */
			CalendarView calendarView = getCalendarView(now.minusMonths(5).atStartOfDay(getZoneId()),
					now.plus(getMaximumPointInFuture()).plus(getMaximumPointInFuture()).plusDays(1).atStartOfDay(
							getZoneId()));

			FindItemsResults<Appointment> findResults;
			try {
				findResults = service.get().findAppointments(WellKnownFolderName.Calendar, calendarView);
			} catch (Exception e) {
				throw new RuntimeException("Unable to find appointments", e);
			}
			findResults.getItems().forEach(a -> addAppointment(event, a));
		}
	}

	private void addAppointment(TimelineEvent event, Appointment appointment) {
		try {
			ZonedDateTime start = appointment.getStart().toInstant().atZone(getZoneId());
			ZonedDateTime end = appointment.getEnd().toInstant().atZone(getZoneId());

			//for all day appointments, write "ganztägig"
			String duration;

			if (appointment.getIsAllDayEvent()) {
				LocalDate _endDate = end.toLocalDate().minus(1, ChronoUnit.DAYS);
				boolean endSameDay = _endDate.isEqual(start.toLocalDate());
				boolean endsToday = _endDate.isEqual(LocalDate.now());

				if (endSameDay || endsToday) {
					duration = ALL_DAY;
				} else {
					duration = ALL_DAY_UNTIL + _endDate.format(DateTimeFormatter.ofPattern("d. MMM", getLocale()));
				}
			} else {
				//not an all day event
				boolean startInPast = start.toLocalDate().isBefore(LocalDate.now());

				if (startInPast) {
					duration = UNTIL + end.format(DateTimeFormatter.ofPattern("d. MMM, HH:mm", getLocale()));
				} else {
					//begins today or in future: show start time (date is always shown)
					duration = start.format(DateTimeFormatter.ofPattern("HH:mm", getLocale()));
				}
			}

			String title = String.format("%s (%s)", appointment.getSubject(), duration);

			//for out of office and mailbox handling use special colors
			Color color = colorAppointment;

			if (LegacyFreeBusyStatus.OOF.equals(appointment.getLegacyFreeBusyStatus())) {
				color = colorOutOfOffice;
			} else if (appointment.getSubject() != null && appointment.getSubject().startsWith("Mailbox")) {
				// TODO Farben auf regel-basis erstellen, nicht hart-kodiert
				color = colorMailbox;
			}

			event.addEvent(title,
					appointment.getStart().toInstant().atZone(getZoneId()).toLocalDate(),
					appointment.getEnd().toInstant().atZone(getZoneId()).toLocalDate(),
					appointment.getIsAllDayEvent(),
					color);
		} catch (ServiceLocalException ignore) {
		}
	}

	private void addBirthdays(TimelineEvent event, LocalDate now) {
		for (Birthday birthday : getBirthdays()) {
			if (birthday.getDate() != null) {
				LocalDate date = birthday.getDate().withYear(now.getYear());
				if (date.isBefore(now)) {
					date = date.plusYears(1);
				}
				event.addEvent(birthday.getName() + " (" + birthday.getDate().until(date).getYears()
						+ ")", date, date, true, colorBirthday);
			}
		}
	}

	private void addHolidays(TimelineEvent event, LocalDate now) {
		Color fontColor = Color.LightGrey;

		HolidayManager m = HolidayManager.getInstance(ManagerParameters.create(getHolidayCalendar()));
		String area = Strings.emptyToNull(getHolidayArea());
		Set<Holiday> holidays;
		if (area != null) {
			holidays = m.getHolidays(now,
					now.plus(getMaximumPointInFuture()).plus(getMaximumPointInFuture()),
					new String[] { area });
		} else {
			holidays = m.getHolidays(now, now.plus(getMaximumPointInFuture()).plus(getMaximumPointInFuture()));
		}
		holidays.forEach(h -> event.addEvent(getCorrectHolidayName(
				h.getDescription(getLocale())), h.getDate(), h.getDate(), true, colorHoliday, fontColor));
	}

	private String getCorrectHolidayName(final String apiName) {
		return Arrays.stream(getHolidayNameCorrections())
				.filter(c -> c.apiName.equals(apiName))
				.findAny()
				.map(c -> c.correctName)
				.orElse(apiName);
	}

	public HolidayNameCorrection[] getHolidayNameCorrections() {
		return corrections;
	}

	@Property(name = "Holiday Name Correction", description = "Some holiday names are not the common used names. If you wish to change that name displayd in the UI please change it here.", json = true)
	public void setHolidayNameCorrections(HolidayNameCorrection[] corrections) {
		this.corrections = corrections;
	}

	public Period getMaximumPointInFuture() {
		return maximumPointInFuture;
	}

	public int getMinimumEventCount() {
		return minimumEventCount;
	}

	@Property(name = "Maximum Point of Time in Future", description = "How far should this event source look into the future at maximum")
	public void setMaximumPointInFuture(Period maximumPointInFuture) {
		this.maximumPointInFuture = maximumPointInFuture;
	}

	@Property(name = "Minimum events count", description = "Set to zero to disable")
	public void setMinimumEventCount(int minimumEventCount) {
		this.minimumEventCount = minimumEventCount;
	}

	@Property(name = "Color for Mailbox Handling", type = "color")
	public Color getColorMailbox() {
		return colorMailbox;
	}

	@Property(name = "Color for \"Today\"", type = "color")
	public Color getColorToday() {
		return colorToday;
	}

	@Property(name = "Color for Out Of Office", type = "color")
	public Color getColorOutOfOffice() {
		return colorOutOfOffice;
	}

	@Property(name = "Color for Appointments", type = "color")
	public Color getColorAppointment() {
		return colorAppointment;
	}

	@Property(name = "Color for Birthdays", type = "color")
	public Color getColorBirthday() {
		return colorBirthday;
	}

	@Property(name = "Color for national holiday", type = "color")
	public Color getColorHoliday() {
		return colorHoliday;
	}

	@Property(name = "Calendar Locale", description = "Internationalization to be used for holiday names")
	public Locale getLocale() {
		return locale;
	}

	public void setLocale(Locale locale) {
		this.locale = locale;
	}

	public void setColorToday(Color colorToday) {
		this.colorToday = colorToday;
	}

	public void setColorMailbox(Color colorMailbox) {
		this.colorMailbox = colorMailbox;
	}

	public void setColorOutOfOffice(Color colorOutOfOffice) {
		this.colorOutOfOffice = colorOutOfOffice;
	}

	public void setColorAppointment(Color colorAppointment) {
		this.colorAppointment = colorAppointment;
	}

	public void setColorBirthday(Color colorBirthday) {
		this.colorBirthday = colorBirthday;
	}

	public void setColorHoliday(Color colorHoliday) {
		this.colorHoliday = colorHoliday;
	}

	@Property(name = "Holiday Calendar", description = "The calendar that shall be used to determine holidays")
	public HolidayCalendar getHolidayCalendar() {
		return holidayCalendar;
	}

	public void setHolidayCalendar(HolidayCalendar holidayCalendar) {
		this.holidayCalendar = holidayCalendar;
	}

	@Property(name = "Holiday Area", description = "The area within the Holiday Calendar to be used to determine local holidays")
	public String getHolidayArea() {
		return holidayArea;
	}

	public void setHolidayArea(String holidayArea) {
		this.holidayArea = holidayArea;
	}

	@Property(name = "Birthdays", description = "Add any birthdays to be displayed in the timeline view", json = true)
	public Birthday[] getBirthdays() {
		return birthdays;
	}

	public void setBirthdays(Birthday[] birthdays) {
		this.birthdays = birthdays;
	}

	@JsonHint(arrayStyle = ArrayStyle.TABLE, headerTemplate = "{{ self.correctName }}")
	private static class HolidayNameCorrection implements JsonBasedData {
		@NotNull
		private String apiName = "API Name";
		@NotNull
		private String correctName = "Correct Name";

		private HolidayNameCorrection(String apiName, String correctName) {
			this.apiName = apiName;
			this.correctName = correctName;
		}
	}
}
