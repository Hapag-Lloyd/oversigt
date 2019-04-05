package com.hlag.oversigt.util.text;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.datatype.DatatypeFactory;

// public modifier for Guice-Injection
class DatetimeFunction implements Function<String, LocalDateTime> {
	private static final String PATTERN_DATETIME_STRING
			= "\\s*([+-])?\\s*([a-z]+|(?:[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:.[0-9]{3})?|[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}:[0-9]{2}:[0-9]{2}(?:.[0-9]{3})?)|P(?=\\d|T\\d)(?:\\d+Y)?(?:\\d+M)?(?:\\d+[DW])?(?:T(?:\\d+H)?(?:\\d+M)?(?:\\d+(?:\\.\\d+)?S)?)?)\\s*";

	private static final Pattern PATTERN_DATETIME = Pattern.compile(PATTERN_DATETIME_STRING);

	private static final Pattern PATTERN_DATETIME_COMPLETE = Pattern.compile("^(" + PATTERN_DATETIME_STRING + ")+$");

	private final DatatypeFactory datatypeFactory;

	DatetimeFunction(final DatatypeFactory datatypeFactory) {
		this.datatypeFactory = datatypeFactory;
	}

	@Override
	public LocalDateTime apply(final String input) {
		if (!PATTERN_DATETIME_COMPLETE.matcher(input).matches()) {
			throw new RuntimeException("Expression '" + input + "' cannot be parsed for dates and times.");
		}
		final Matcher dateMatcher = PATTERN_DATETIME.matcher(input);
		final LocalDateTime nullDateTime = LocalDateTime.of(0, 1, 1, 0, 0, 0, 0);
		LocalDateTime datetime = LocalDateTime.from(nullDateTime);
		while (dateMatcher.find()) {
			char op = 0;
			if (dateMatcher.group(1) != null) {
				op = dateMatcher.group(1).charAt(0);
			}

			TemporalAmount change;
			final String temporalString = dateMatcher.group(2).toUpperCase();
			if (temporalString.equals("NOW")) {
				change = Duration.between(nullDateTime, LocalDateTime.now());
			} else if (temporalString.equals("MIDNIGHT")) {
				change = Duration.between(nullDateTime,
						LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0));
			} else if (temporalString.equals("NOON")) {
				change = Duration.between(nullDateTime,
						LocalDateTime.now().withHour(12).withMinute(0).withSecond(0).withNano(0));
			} else if (temporalString.startsWith("P")) {
				javax.xml.datatype.Duration duration = datatypeFactory.newDuration(temporalString);
				if (op == '-') {
					duration = duration.negate();
					op = '+';
				}
				final long millis
						= duration.getTimeInMillis(Date.from(datetime.atZone(ZoneId.systemDefault()).toInstant()));
				change = Duration.ofMillis(millis);
			} else if (temporalString.contains("T")) {
				change = Duration.between(nullDateTime,
						LocalDateTime.parse(temporalString, DateTimeFormatter.ISO_DATE_TIME));
			} else if (temporalString.contains("-")) {
				change = Period.between(nullDateTime.toLocalDate(),
						LocalDate.parse(temporalString, DateTimeFormatter.ISO_DATE));
			} else {
				change = Duration.between(nullDateTime.toLocalTime(),
						LocalTime.parse(temporalString, DateTimeFormatter.ISO_TIME));
			}

			if (op == 0) {
				datetime = datetime.plus(change);
			} else {
				switch (op) {
				case '+':
					datetime = datetime.plus(change);
					break;
				case '-':
					datetime = datetime.minus(change);
					break;
				default:
					throw new RuntimeException("Unknown operator: " + op);
				}
			}
		}
		return datetime;
	}
}
