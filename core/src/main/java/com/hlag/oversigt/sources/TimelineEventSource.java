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
import microsoft.exchange.webservices.data.core.enumeration.property.LegacyFreeBusyStatus;
import microsoft.exchange.webservices.data.core.exception.service.local.ServiceLocalException;
import microsoft.exchange.webservices.data.core.service.item.Appointment;

/**
 * @author Constantin Pagenkopp
 */
@EventSource(displayName = "Calendar Timeline View", view = "Timeline")
public class TimelineEventSource extends AbstractExchangeEventSource<TimelineEvent> {
	// TODO internationalize these strings
	private static final String UNTIL = "until ";

	private static final String TODAY = "Today";

	private static final String ALL_DAY = "all day";

	private static final String ALL_DAY_UNTIL = ALL_DAY + ", " + UNTIL;

	private Period maximumPointInFuture = Period.ofMonths(3);

	private int minimumEventCount = 0;

	private Color colorToday = Color.GREEN;

	private Color colorAppointment = Color.BLUE;

	private Color colorBirthday = Color.YELLOW;

	private Color colorHoliday = Color.GRAY;

	private Color colorOutOfOffice = Color.BLUE;

	private Color colorMailbox = Color.BLUE;

	private HolidayCalendar holidayCalendar = HolidayCalendar.GERMANY;

	private String holidayArea = "hh";

	private Locale locale = Locale.getDefault();

	private Birthday[] birthdays = new Birthday[0];

	private HolidayNameCorrection[] corrections = new HolidayNameCorrection[] { //
			new HolidayNameCorrection("Weihnachten", "1. Weihnachtstag"),
			new HolidayNameCorrection("Stephanstag", "2. Weihnachtstag"),
			new HolidayNameCorrection("Tag der Wiedervereinigung", "Tag der Deutschen Einheit") };

	@Override
	protected Optional<TimelineEvent> produceExchangeEvent() throws Exception {
		final TimelineEvent event = new TimelineEvent(maximumPointInFuture, getZoneId(), getLocale());
		final LocalDate now = LocalDate.now(getZoneId());

		fillTimelineEvent(now, event);

		event.removeEvents(getMaximumPointInFuture(), getMinimumEventCount());

		// maybe add today
		if (!event.hasEventAt(now)) {
			event.addEvent(TODAY, now, now, false, colorToday);
		}

		return Optional.of(event);
	}

	protected void fillTimelineEvent(final LocalDate now, final TimelineEvent event) {
		addBirthdays(event, now);
		addHolidays(event, now);
		addExchangeCalendar(event, now);
	}

	private void addExchangeCalendar(final TimelineEvent event, final LocalDate now) {
		getExchangeClient()
				.loadAppointments(now.minusMonths(5).atStartOfDay(getZoneId()),
						now.plus(getMaximumPointInFuture())
								.plus(getMaximumPointInFuture())
								.plusDays(1)
								.atStartOfDay(getZoneId()))
				.forEach(appointment -> addAppointment(event, appointment));
	}

	private void addAppointment(final TimelineEvent event, final Appointment appointment) {
		try {
			addAppointment_unsafe(event, appointment);
		} catch (@SuppressWarnings("unused") final ServiceLocalException ignore) {
			/* ignore */
		}
	}

	private void addAppointment_unsafe(final TimelineEvent event, final Appointment appointment)
			throws ServiceLocalException {
		final ZonedDateTime start = appointment.getStart().toInstant().atZone(getZoneId());
		final ZonedDateTime end = appointment.getEnd().toInstant().atZone(getZoneId());
		final LocalDate now = LocalDate.now(getZoneId());

		// for all day appointments, write "ganztägig"
		final String duration;

		if (appointment.getIsAllDayEvent()) {
			final LocalDate endDate = end.toLocalDate().minus(1, ChronoUnit.DAYS);
			final boolean endSameDay = endDate.isEqual(start.toLocalDate());
			final boolean endsToday = endDate.isEqual(now);

			if (endSameDay || endsToday) {
				duration = ALL_DAY;
			} else {
				duration = ALL_DAY_UNTIL + endDate.format(DateTimeFormatter.ofPattern("d. MMM", getLocale()));
			}
		} else {
			// not an all day event
			final boolean startInPast = start.toLocalDate().isBefore(now);

			if (startInPast) {
				duration = UNTIL + end.format(DateTimeFormatter.ofPattern("d. MMM, HH:mm", getLocale()));
			} else {
				// begins today or in future: show start time (date is always shown)
				duration = start.format(DateTimeFormatter.ofPattern("HH:mm", getLocale()));
			}
		}

		final String title = String.format("%s (%s)", appointment.getSubject(), duration);

		// for out of office and mailbox handling use special colors
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
	}

	private void addBirthdays(final TimelineEvent event, final LocalDate now) {
		for (final Birthday birthday : getBirthdays()) {
			LocalDate date = birthday.getDate().withYear(now.getYear());
			if (date.isBefore(now)) {
				date = date.plusYears(1);
			}
			event.addEvent(birthday.getName() + " (" + birthday.getDate().until(date).getYears() + ")",
					date,
					date,
					true,
					colorBirthday);
		}
	}

	private void addHolidays(final TimelineEvent event, final LocalDate now) {
		final Color fontColor = Color.LIGHT_GRAY;

		final HolidayManager m = HolidayManager.getInstance(ManagerParameters.create(getHolidayCalendar()));
		final String area = Strings.emptyToNull(getHolidayArea());
		final Set<Holiday> holidays;
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

	@Property(name = "Holiday Name Correction",
			description = "Some holiday names are not the common used names. If you wish to change that name displayd in the UI please change it here.")
	public HolidayNameCorrection[] getHolidayNameCorrections() {
		return corrections;
	}

	public void setHolidayNameCorrections(final HolidayNameCorrection[] corrections) {
		this.corrections = corrections;
	}

	public Period getMaximumPointInFuture() {
		return maximumPointInFuture;
	}

	public int getMinimumEventCount() {
		return minimumEventCount;
	}

	@Property(name = "Maximum Point of Time in Future",
			description = "How far should this event source look into the future at maximum")
	public void setMaximumPointInFuture(final Period maximumPointInFuture) {
		this.maximumPointInFuture = maximumPointInFuture;
	}

	@Property(name = "Minimum events count", description = "Set to zero to disable")
	public void setMinimumEventCount(final int minimumEventCount) {
		this.minimumEventCount = minimumEventCount;
	}

	@Property(name = "Color for Mailbox Handling")
	public Color getColorMailbox() {
		return colorMailbox;
	}

	@Property(name = "Color for \"Today\"")
	public Color getColorToday() {
		return colorToday;
	}

	@Property(name = "Color for Out Of Office")
	public Color getColorOutOfOffice() {
		return colorOutOfOffice;
	}

	@Property(name = "Color for Appointments")
	public Color getColorAppointment() {
		return colorAppointment;
	}

	@Property(name = "Color for Birthdays")
	public Color getColorBirthday() {
		return colorBirthday;
	}

	@Property(name = "Color for national holiday")
	public Color getColorHoliday() {
		return colorHoliday;
	}

	@Property(name = "Calendar Locale", description = "Internationalization to be used for holiday names")
	public Locale getLocale() {
		return locale;
	}

	public void setLocale(final Locale locale) {
		this.locale = locale;
	}

	public void setColorToday(final Color colorToday) {
		this.colorToday = colorToday;
	}

	public void setColorMailbox(final Color colorMailbox) {
		this.colorMailbox = colorMailbox;
	}

	public void setColorOutOfOffice(final Color colorOutOfOffice) {
		this.colorOutOfOffice = colorOutOfOffice;
	}

	public void setColorAppointment(final Color colorAppointment) {
		this.colorAppointment = colorAppointment;
	}

	public void setColorBirthday(final Color colorBirthday) {
		this.colorBirthday = colorBirthday;
	}

	public void setColorHoliday(final Color colorHoliday) {
		this.colorHoliday = colorHoliday;
	}

	@Property(name = "Holiday Calendar", description = "The calendar that shall be used to determine holidays")
	public HolidayCalendar getHolidayCalendar() {
		return holidayCalendar;
	}

	public void setHolidayCalendar(final HolidayCalendar holidayCalendar) {
		this.holidayCalendar = holidayCalendar;
	}

	@Property(name = "Holiday Area",
			description = "The area within the Holiday Calendar to be used to determine local holidays")
	public String getHolidayArea() {
		return holidayArea;
	}

	public void setHolidayArea(final String holidayArea) {
		this.holidayArea = holidayArea;
	}

	@Property(name = "Birthdays", description = "Add any birthdays to be displayed in the timeline view")
	public Birthday[] getBirthdays() {
		return birthdays;
	}

	public void setBirthdays(final Birthday[] birthdays) {
		this.birthdays = birthdays;
	}

	@JsonHint(arrayStyle = ArrayStyle.TABLE, headerTemplate = "{{ self.correctName }}")
	private static final class HolidayNameCorrection implements JsonBasedData {
		@NotNull
		private String apiName = "API Name";

		@NotNull
		private String correctName = "Correct Name";

		private HolidayNameCorrection(final String apiName, final String correctName) {
			this.apiName = apiName;
			this.correctName = correctName;
		}
	}
}
