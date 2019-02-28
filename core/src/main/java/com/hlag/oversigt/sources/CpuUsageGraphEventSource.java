package com.hlag.oversigt.sources;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
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

@EventSource(view = "Rickshawgraph", displayName = "CPU Usage")
public class CpuUsageGraphEventSource extends ScheduledEventSource<ComplexGraphEvent> {
	private final Map<ZonedDateTime, Map<Server, Integer>> values = new TreeMap<>();

	private Server[] servers = new Server[] { new Server() };
	private int historyLength = 10;

	public CpuUsageGraphEventSource() {
	}

	@Override
	protected ComplexGraphEvent produceEvent() {
		ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

		// get usages from hosts
		Map<Server, Integer> usages = Arrays//
				.stream(getServers())//
				.parallel()//
				.collect(Collectors.toMap(Function.identity(), this::getCpuUsage));
		values.put(now, usages);

		// remove old values
		values.keySet().removeIf(now.minusMinutes(getHistoryLength())::isAfter);

		// graphen berechnen
		long secondsOffset = now.withHour(0).withMinute(0).withSecond(0).toEpochSecond();
		Map<String, Series> series = new TreeMap<>(String.CASE_INSENSITIVE_ORDER.reversed());
		for (Server server : getServers()) {
			List<Point> points = values//
					.entrySet()
					.stream()
					.map(e -> new Point(e.getKey().toEpochSecond() - secondsOffset,
							e.getValue().get(server).longValue()))
					.collect(Collectors.toList());
			series.put(server.hostname, new Series(server.getDisplayName(), points));
		}

		return new ComplexGraphEvent(new ArrayList<>(series.values()));
	}

	private int getCpuUsage(Server server) {
		int usage;
		switch (server.operatingSystem) {
			case Linux:
			case Aix:
				usage = getUnixCpuUsage(server);
				break;
			//			case Windows:
			//				usage = getWindowsCpuUsage(server);
			//				break;
			default:
				throw new RuntimeException("Unknown operating system: " + server.operatingSystem.name());
		}
		Utils.logInfo(getSpecialLogger(), "%s: %s", server.hostname, usage);
		return usage;
	}

	private int getUnixCpuUsage(Server server) {
		double usage = SshConnection.getConnection(server.hostname, server.port, server.username, server.password)
				.getCpuUsage();
		if (!Double.isNaN(usage)) {
			int percent = (int) (100 * usage + 0.5);
			if (percent < 0) {
				percent = 0;
			}
			if (percent > 100) {
				percent = 100;
			}
			return percent;
		} else {
			return 0;
		}
	}

	@SuppressWarnings("unused")
	@Deprecated
	private int getWindowsCpuUsage(Server server) {
		Path temp = null;
		Path batch = null;
		Path stdout = null;
		Path stderr = null;

		try {
			temp = Files.createTempDirectory("cpuUsage").toAbsolutePath();
			batch = temp.resolve("cpuUsage.bat");
			stdout = temp.resolve("stdout.txt");
			stderr = temp.resolve("stderr.txt");

			// Write batch file
			try (Writer writer = new FileWriter(batch.toFile())) {
				writer.append(String.format(
						"%s -accepteula -nobanner \"%s\" -u \"%s\" -p \"%s\" wmic cpu get LoadPercentage 1>\"%s\" 2>\"%s\"\r\nexit",
						"", //PSEXEC_PATH,
						server.hostname,
						server.username,
						server.password,
						stdout.toString(),
						stderr.toString()));
			}

			// Execute PsExec
			int exitCode = new ProcessBuilder("cmd", "/C", "start", "/MIN", "/WAIT", batch.toString()).start()
					.waitFor();
			if (exitCode != 0) {
				getLogger().error("Failed executing PsExec. Exit code: " + exitCode);
				return 0;
			}

			// Read stdout
			try (BufferedReader reader = Files.newBufferedReader(stdout)) {
				List<String> lines = reader.lines()
						.map(String::trim)
						.filter(l -> !l.isEmpty())
						.collect(Collectors.toList());

				if (lines.size() < 2) {
					getLogger().error("Expected at least 2 filled lines in stdout of PsExec, got: " + lines.size());
					return 0;
				}
				if (!lines.get(0).equals("LoadPercentage")) {
					getLogger().error("Expected \"LoadPercentage\" in first filled line of stdout of PsExec, got: "
							+ lines.get(0));
					return 0;
				}

				// Supporting multiple processors
				int percentage = 0;
				for (int i = lines.size() - 1; i > 0; i -= 1) {
					percentage += Integer.parseInt(lines.get(i));
				}
				return percentage / (lines.size() - 1);
			}
		} catch (InterruptedException | IOException e) {
			getLogger().error("Exception while getting CPU usage.", e);
			return 0;
		} finally {
			for (Path path : new Path[] { stderr, stdout, batch, temp }) {
				if (path != null) {
					try {
						path.toFile().delete();
					} catch (Exception ignore) {
					}
				}
			}
		}
	}

	@Property(name = "Servers", description = "The servers to check.")
	public Server[] getServers() {
		if (servers != null) {
			return servers;
		} else {
			return new Server[0];
		}
	}

	public void setServers(Server[] servers) {
		this.servers = servers;
	}

	@Property(name = "History Length", description = "How long should data from the past be displayed (in minutes)?")
	public int getHistoryLength() {
		return historyLength;
	}

	public void setHistoryLength(int historyLength) {
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

	private static enum OperatingSystem {
		Aix,
		Linux,
		//	Windows
	}
}
