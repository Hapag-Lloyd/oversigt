package com.hlag.oversigt.sources;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import com.hlag.oversigt.core.EventSource;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;
import com.hlag.oversigt.core.eventsource.annotation.DataItemDefault;
import com.hlag.oversigt.sources.event.MeterEvent;

@EventSource(view = "Meter", displayName = "Motivation", description = "Compute semi-random numbers depending on time of day and other things.", hiddenDataItems = {
		"height", "moreinfo", "updated-at-message", "width" }, dataItemDefaults = {
				@DataItemDefault(name = "min", value = "0"), @DataItemDefault(name = "max", value = "100") })
public class MotivationEventSource extends ScheduledEventSource<MeterEvent> {

	private static final ZoneId HERE = ZoneId.of("Europe/Berlin");
	private static final LocalTime START_ZEIT = LocalTime.of(7, 0);
	private static final LocalTime STOP_ZEIT = LocalTime.of(17, 15);

	@Override
	protected MeterEvent produceEvent() {
		ZonedDateTime now = ZonedDateTime.now(HERE);
		ZonedDateTime start = getStartingTime();
		ZonedDateTime stop = getQuittingTime();

		double motivation;

		// Consider time of day
		if (start.isBefore(now) && stop.isAfter(now)) {
			// Motivation goes down...
			double minutesToWork = ChronoUnit.MINUTES.between(start, stop);
			double minutesLeft = ChronoUnit.MINUTES.between(now, stop);

			motivation = minutesLeft / minutesToWork;
		} else {
			// Motivation goes up...
			start = start.plusDays(1);
			stop = stop.plusDays(1);
			double minutesToWork = ChronoUnit.MINUTES.between(stop, start);
			double minutesLeft = ChronoUnit.MINUTES.between(now, start);

			motivation = (minutesToWork - minutesLeft) / minutesToWork;
		}

		// consider day of week
		{
			DayOfWeek dow = now.getDayOfWeek();
			double offset = dow.ordinal() * 0.05;
			double factor = 1.0 - DayOfWeek.values().length * 0.05;
			motivation = offset + motivation * factor;
		}

		return new MeterEvent((byte) (motivation * 100 + 0.5));
	}

	private static ZonedDateTime getStartingTime() {
		return START_ZEIT.atDate(LocalDate.now()).atZone(HERE);
	}

	private static ZonedDateTime getQuittingTime() {
		return STOP_ZEIT.atDate(LocalDate.now()).atZone(HERE);
	}
}
