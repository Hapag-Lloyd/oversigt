package com.hlag.oversigt.sources;

import static java.util.stream.Collectors.toList;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;
import com.hlag.oversigt.sources.event.ComplexGraphEvent;
import com.hlag.oversigt.sources.event.ComplexGraphEvent.Point;
import com.hlag.oversigt.sources.event.ComplexGraphEvent.Series;
import com.hlag.oversigt.util.Utils;
import com.hlag.oversigt.util.function.ThrowingIntFunction;

@EventSource(displayName = "SQL Graph", view = "Rickshawgraph")
public class SqlGraphEventSource extends AbstractJdbcEventSource<ComplexGraphEvent> {
	private int historyLength = 10;

	private String labelFormat = "";

	private String sqlStatement = "";

	private String[] titles = new String[0];

	private int columnCount = 0;

	private final SortedMap<ZonedDateTime, List<Long>> values = new TreeMap<>();

	public SqlGraphEventSource() {
		// no fields to be initialized
	}

	@Override
	protected Optional<ComplexGraphEvent> produceEventFromData() {
		final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

		// Remove too old history
		values.keySet().removeIf(now.minusMinutes(getHistoryLength())::isAfter);

		// Calculate graph values
		final long secondsOffset = now.withHour(0).withMinute(0).withSecond(0).toEpochSecond();
		final List<Series> series = new ArrayList<>();
		IntStream.range(0, columnCount).forEach(column -> {
			final List<Point> points = new ArrayList<>();

			if (!values.isEmpty()) {
				// Get maximum value of each series to normalize
				final OptionalLong optionalMaxValue = values.values().stream().mapToLong(v -> v.get(column)).max();
				final long maxValue = optionalMaxValue.getAsLong() == 0 ? 1 : optionalMaxValue.getAsLong();

				points.addAll(values.entrySet()
						.stream()
						.map(e -> new Point(e.getKey().toEpochSecond() - secondsOffset,
								100 * e.getValue().get(column) / maxValue))
						.collect(Collectors.toList()));
			}

			series.add(new Series(titles.length > column ? titles[column] : "", points));
		});

		Collections.reverse(series);

		final List<Long> displayValues = new ArrayList<>();
		if (!values.isEmpty()) {
			displayValues.addAll(values.get(values.lastKey()));
		}
		String text = "";
		try {
			text = String.format(labelFormat, displayValues.toArray());
		} catch (final IllegalFormatException e) {
			getLogger().warn("Unable to fill format: " + getLabelFormat(), e);
		}
		return Optional.of(new ComplexGraphEvent(series, text));
	}

	@Override
	protected void gatherDatabaseInfo(final Connection connection) throws SQLException {
		final List<List<Long>> data = readFromDatabase(connection,
				rs -> IntStream.range(1, rs.getMetaData().getColumnCount() + 1)
						.mapToObj(ThrowingIntFunction.sneakyInt(rs::getLong))
						.collect(toList()),
				getSqlStatement());

		if (!data.isEmpty()) {
			values.put(ZonedDateTime.now(ZoneOffset.UTC), data.get(0));
			columnCount = data.get(0).size();
			Utils.logInfo(getSpecialLogger(),
					"%s",
					(Supplier<String>) () -> IntStream.range(0, columnCount)
							.mapToObj((final int column) -> String
									.format("%s: %d", titles[column], data.get(0).get(column)))
							.collect(Collectors.joining(", ")));
		} else {
			getLogger().error("Failed with empty result set.");
		}
	}

	@Property(name = "History length", description = "in minutes")
	public int getHistoryLength() {
		return historyLength;
	}

	public void setHistoryLength(final int historyLength) {
		this.historyLength = historyLength;
	}

	@Property(name = "Label format",
			description = "Values of the latest SQL execution can be used as String.format parameters (type: long)")
	public String getLabelFormat() {
		return labelFormat;
	}

	public void setLabelFormat(final String labelFormat) {
		this.labelFormat = labelFormat;
	}

	@Property(name = "SQL", description = "SQL Statement returning one row", type = "sql")
	public String getSqlStatement() {
		return sqlStatement;
	}

	public void setSqlStatement(final String sqlStatement) {
		this.sqlStatement = sqlStatement;
	}

	@Property(name = "Titles", description = "Names of the values to display")
	public String[] getTitles() {
		return titles;
	}

	@JsonHint(arrayStyle = ArrayStyle.TABLE)
	public void setTitles(final String[] titles) {
		this.titles = titles;
	}
}
