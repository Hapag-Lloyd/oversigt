package com.hlag.oversigt.sources;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import com.hlag.oversigt.connect.ssh.SshConnection;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;
import com.hlag.oversigt.properties.JsonBasedData;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;
import com.hlag.oversigt.sources.event.ComplexGraphEvent;
import com.hlag.oversigt.sources.event.ComplexGraphEvent.Point;
import com.hlag.oversigt.sources.event.ComplexGraphEvent.Series;
import com.hlag.oversigt.util.Utils;

import de.larssh.utils.Nullables;

@EventSource(view = "Rickshawgraph", displayName = "CPU Usage")
public class CpuUsageGraphEventSource extends ScheduledEventSource<ComplexGraphEvent> {
	private final Map<ZonedDateTime, Map<Server, Integer>> values = new TreeMap<>();

	private Server[] servers = new Server[] { new Server() };

	private int historyLength = 10;

	public CpuUsageGraphEventSource() {}

	@Override
	protected ComplexGraphEvent produceEvent() {
		final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

		// get usages from hosts
		final Map<Server, Integer> usages = Arrays//
				.stream(getServers())//
				.parallel()//
				.collect(Collectors.toMap(Function.identity(), this::getCpuUsage));
		values.put(now, usages);

		// remove old values
		values.keySet().removeIf(now.minusMinutes(getHistoryLength())::isAfter);

		// graphen berechnen
		final long secondsOffset = now.withHour(0).withMinute(0).withSecond(0).toEpochSecond();
		final Map<String, Series> series = new TreeMap<>(String.CASE_INSENSITIVE_ORDER.reversed());
		for (final Server server : getServers()) {
			final List<Point> points = values//
					.entrySet()
					.stream()
					.map(e -> new Point(e.getKey().toEpochSecond() - secondsOffset,
							e.getValue().get(server).longValue()))
					.collect(Collectors.toList());
			series.put(server.hostname, new Series(server.getDisplayName(), points));
		}

		return new ComplexGraphEvent(new ArrayList<>(series.values()));
	}

	private int getCpuUsage(final Server server) {
		final int usage;
		switch (server.operatingSystem) {
		case Linux:
		case Aix:
			usage = getUnixCpuUsage(server);
			break;
		// case Windows:
		// usage = getWindowsCpuUsage(server);
		// break;
		default:
			throw new RuntimeException("Unknown operating system: " + server.operatingSystem.name());
		}
		Utils.logInfo(getSpecialLogger(), "%s: %s", server.hostname, usage);
		return usage;
	}

	private int getUnixCpuUsage(final Server server) {
		final double usage = SshConnection.getConnection(server.hostname, server.port, server.username, server.password)
				.getCpuUsage();
		if (Double.isNaN(usage)) {
			return 0;
		}
		int percent = (int) (100 * usage + 0.5);
		if (percent < 0) {
			percent = 0;
		}
		if (percent > 100) {
			percent = 100;
		}
		return percent;
	}

	@Property(name = "Servers", description = "The servers to check.")
	public Server[] getServers() {
		return Nullables.orElseGet(servers, () -> new Server[0]);
	}

	public void setServers(final Server[] servers) {
		this.servers = servers;
	}

	@Property(name = "History Length", description = "How long should data from the past be displayed (in minutes)?")
	public int getHistoryLength() {
		return historyLength;
	}

	public void setHistoryLength(final int historyLength) {
		this.historyLength = historyLength;
	}

	@JsonHint(headerTemplate = "{{ self.name }}", arrayStyle = ArrayStyle.TABS)
	private static class Server implements JsonBasedData {
		private String name = "DisplayName";

		@NotNull
		private String hostname = "hostname";

		@NotNull
		private int port = 22;

		@NotNull
		private String username = "username";

		@NotNull
		private String password = "password";

		@NotNull
		private OperatingSystem operatingSystem = OperatingSystem.Aix;

		private String getDisplayName() {
			return name != null ? name : hostname + ":" + port;
		}

		@Override
		public String toString() {
			return String.format("Server [name=%s, hostname=%s, port=%s]", name, hostname, port);
		}
	}

	private enum OperatingSystem {
		Aix,
		Linux,
		// Windows
	}
}
