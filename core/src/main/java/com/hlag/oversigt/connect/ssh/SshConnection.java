package com.hlag.oversigt.connect.ssh;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public abstract class SshConnection {
	private static final Logger LOGGER = LoggerFactory.getLogger(SshConnection.class);

	private static final Pattern PARSE_CPU_USAGE_LINES_PATTERN = Pattern.compile("[\\r\\n]+");

	static {
		JSch.setLogger(new JSchLogger(LoggerFactory.getLogger(JSch.class)));
		Runtime.getRuntime().addShutdownHook(new Thread(SshConnection::shutdown));
	}

	private static void shutdown() {
		LOGGER.info("Disconnecting SSH sessions...");
		for (final SshConnection connection : CONNECTIONS.values()) {
			try {
				connection.disconnect();
			} catch (final Exception e) {
				LOGGER.warn("Failed shutting down SSH connection.", e);
			}
		}
		LOGGER.info("SSH sessions disconnected...");
	}

	private static final JSch JSCH = new JSch();

	private static final Map<SshConnectionKey, SshConnection> CONNECTIONS
			= Collections.synchronizedMap(new HashMap<>());

	public static SshConnection getConnection(final String hostname,
			final int port,
			final String username,
			final String password) {
		return getConnection(new SshConnectionKey(hostname, port, username),
				() -> new SshConnectionWithPassword(hostname, port, username, password));
	}

	private static SshConnection getConnection(final SshConnectionKey key,
			final Supplier<SshConnection> connectionSupplier) {
		synchronized (CONNECTIONS) {
			return CONNECTIONS.computeIfAbsent(key, x -> connectionSupplier.get());
		}
	}

	private final String hostname;

	private final int port;

	private final String username;

	private final AtomicReference<Session> sessionReference = new AtomicReference<>(null);

	protected SshConnection(final String hostname, final int port, final String username) {
		this.hostname = hostname;
		this.port = port;
		this.username = username;
	}

	protected String getUsername() {
		return username;
	}

	protected String getHostname() {
		return hostname;
	}

	protected int getPort() {
		return port;
	}

	private boolean isSessionAlive(final Session session) {
		try {
			final ChannelExec testChannel = (ChannelExec) sessionReference.get().openChannel("exec");
			testChannel.setCommand("true");
			testChannel.connect();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Session erfolgreich getestet, verwende sie erneut");
			}
			testChannel.disconnect();
			return true;
		} catch (final JSchException e) {
			try {
				session.disconnect();
			} catch (final Exception ignore) {}
			return false;
		}
	}

	private Session getSession() {
		synchronized (sessionReference) {
			if (sessionReference.get() != null) {
				if (isSessionAlive(sessionReference.get())) {
					return sessionReference.get();
				}
				sessionReference.set(null);
			}

			try {
				sessionReference.set(createSession(JSCH));
				return sessionReference.get();
			} catch (final JSchException e) {
				throw new RuntimeException("Unable to create session.", e);
			}
		}
	}

	protected abstract Session createSession(JSch jsch) throws JSchException;

	private void disconnect() {
		synchronized (sessionReference) {
			if (sessionReference.get() != null) {
				sessionReference.get().disconnect();
			}
		}
	}

	public double getCpuUsage() {
		final String result = runSshCommand("lparstat 1 1");
		if (result == null) {
			return Double.NaN;
		}

		double percent = parseCpuUsage(result);
		if (percent < 0.0) {
			percent = 0.0;
		}
		if (percent > 1.0) {
			percent = 1.0;
		}
		return percent;
	}

	public String getTopas() {
		final String filename = "hl_topas_" + UUID.randomUUID().toString() + ".tmp";
		final String[] commands = new String[] { //
				"cd /tmp", //
				"(sleep 3; echo q)|topas > " + filename, //
				"cat " + filename, //
				"rm " + filename };
		// String string = runSshCommand(session, "(sleep 3; echo q)|topas");
		final String string = runShellCommands(commands);
		return string;
	}

	private static double parseCpuUsage(final String string) {
		if (string.contains("Linux")) {
			return Double.NaN;
		}

		final String[] lines = PARSE_CPU_USAGE_LINES_PATTERN.split(string, 0);
		final int pos = lines[lines.length - 3].indexOf("%idle");
		final int len = lines[lines.length - 2].indexOf(' ', pos);
		return 1.0 - Double.parseDouble(lines[lines.length - 1].substring(pos, len)) / 100.0;
	}

	private String runSshCommand(final String command) {
		try {
			final Session session = getSession();

			final ChannelExec channel = (ChannelExec) session.openChannel("exec");
			channel.setCommand(command);
			channel.setInputStream(null);
			channel.setErrStream(System.err);
			final InputStream in = channel.getInputStream();
			channel.connect();

			try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
				final byte[] buffer = new byte[1024];
				while (true) {
					while (in.available() > 0) {
						final int len = in.read(buffer, 0, 1024);
						if (len < 0) {
							break;
						}
						bos.write(buffer, 0, len);
					}
					if (channel.isClosed()) {
						if (in.available() > 0) {
							continue;
						}
						break;
					}
					try {
						Thread.sleep(100);
					} catch (final Exception ignore) {}
				}
				channel.disconnect();

				return bos.toString();
			}
		} catch (final Exception e) {
			LOGGER.error("Unknown exception while running SSH command.", e);
			return null;
		}
	}

	private String runShellCommands(final String... commands) {
		try {
			final Session session = getSession();

			final ChannelShell channel = (ChannelShell) session.openChannel("shell");
			// channel.setCommand(command);
			// channel.setInputStream(null);
			// channel.setErrStream(System.err);
			// InputStream in = channel.getInputStream();
			channel.connect();
			final PrintStream out = new PrintStream(channel.getOutputStream());

			out.println("#!/bin/bash");
			for (final String command : commands) {
				out.println(command);
			}
			out.print("exit");
			out.flush();

			try (InputStream in = channel.getInputStream();
					ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
				final byte[] buffer = new byte[1024];
				while (true) {
					while (in.available() > 0) {
						final int len = in.read(buffer, 0, 1024);
						if (len < 0) {
							break;
						}
						bos.write(buffer, 0, len);
					}
					if (channel.isClosed()) {
						if (in.available() > 0) {
							continue;
						}
						break;
					}
					if ((bos.size() > 10 ? bos.toString().substring(bos.size() - 10) : bos.toString())
							.contains("exit")) {
						break;
					}
					try {
						Thread.sleep(100);
					} catch (final Exception ignore) {}
				}
				channel.disconnect();

				return bos.toString();
			}
		} catch (final Exception e) {
			LOGGER.error("Unknown exception while running SSH command.", e);
			return null;
		}
	}
}
