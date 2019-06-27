package com.hlag.oversigt.connect.ssh;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

	private final AtomicReference<Optional<Session>> sessionReference = new AtomicReference<>(Optional.empty());

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

	private boolean isSessionAlive() {
		if (!sessionReference.get().isPresent()) {
			return false;
		}

		try {
			final ChannelExec testChannel = (ChannelExec) sessionReference.get().get().openChannel("exec");
			testChannel.setCommand("true");
			testChannel.connect();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Session erfolgreich getestet, verwende sie erneut");
			}
			testChannel.disconnect();
			return true;
		} catch (@SuppressWarnings("unused") final JSchException e) {
			try {
				sessionReference.get().ifPresent(Session::disconnect);
			} catch (@SuppressWarnings("unused") final Exception ignore) {
				// empty by design
			}
			return false;
		}
	}

	private Session getSession() {
		synchronized (sessionReference) {
			if (isSessionAlive()) {
				return sessionReference.get().get();
			}

			sessionReference.set(Optional.empty());

			try {
				final Session session = createSession(JSCH);
				sessionReference.set(Optional.of(session));
				return session;
			} catch (final JSchException e) {
				throw new RuntimeException("Unable to create session.", e);
			}
		}
	}

	protected abstract Session createSession(JSch jsch) throws JSchException;

	private void disconnect() {
		synchronized (sessionReference) {
			sessionReference.get().ifPresent(Session::disconnect);
		}
	}

	public double getCpuUsage() {
		return runSshCommand("lparstat 1 1")//
				.map(SshConnection::parseCpuUsage)
				.map(SshConnection::capTo0And1)
				.orElse(Double.NaN);
	}

	public Optional<String> getTopas() {
		final String filename = "hl_topas_" + UUID.randomUUID().toString() + ".tmp";
		final String[] commands = new String[] {
				"cd /tmp",
				"(sleep 3; echo q)|topas > " + filename,
				"cat " + filename,
				"rm " + filename };
		return runShellCommands(commands);
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

	private static double capTo0And1(final double d) {
		if (d < 0.0) {
			return 0.0;
		} else if (d > 1.0) {
			return 1.0;
		} else {
			return d;
		}
	}

	private Optional<String> runSshCommand(final String command) {
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
					} catch (@SuppressWarnings("unused") final Exception ignore) {
						// empty by design
					}
				}
				channel.disconnect();

				return Optional.of(bos.toString());
			}
		} catch (final Exception e) {
			LOGGER.error("Unknown exception while running SSH command.", e);
			return Optional.empty();
		}
	}

	private Optional<String> runShellCommands(final String... commands) {
		try {
			final Session session = getSession();

			final ChannelShell channel = (ChannelShell) session.openChannel("shell");
			channel.connect();
			final PrintStream out = new PrintStream(channel.getOutputStream());

			out.println("#!/bin/bash");
			for (final String command : commands) {
				out.println(command);
			}
			out.print("exit");
			out.flush();

			try (InputStream in = channel.getInputStream(); //
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
					} catch (@SuppressWarnings("unused") final Exception ignore) {
						// empty by design
					}
				}
				channel.disconnect();

				return Optional.of(bos.toString());
			}
		} catch (final Exception e) {
			LOGGER.error("Unknown exception while running SSH command.", e);
			return Optional.empty();
		}
	}
}
