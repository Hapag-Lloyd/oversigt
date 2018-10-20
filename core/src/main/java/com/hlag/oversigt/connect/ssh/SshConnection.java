package com.hlag.oversigt.connect.ssh;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public abstract class SshConnection {
	private static final Logger LOGGER = LoggerFactory.getLogger(SshConnection.class);

	static {
		JSch.setLogger(new JSchLogger(LoggerFactory.getLogger(JSch.class)));
		Runtime.getRuntime().addShutdownHook(new Thread(SshConnection::shutdown));
	}

	private static void shutdown() {
		LOGGER.info("Disconnecting SSH sessions...");
		for (SshConnection connection : connections.values()) {
			try {
				connection.disconnect();
			} catch (Exception e) {
				LOGGER.warn("Failed shutting down SSH connection.", e);
			}
		}
		LOGGER.info("SSH sessions disconnected...");
	}

	private static final JSch JSCH = new JSch();
	private static final Map<SshConnectionKey, SshConnection> connections = Collections
			.synchronizedMap(new HashMap<>());

	public static SshConnection getConnection(String hostname, int port, String username, String password) {
		return getConnection(new SshConnectionKey(hostname, port, username),
				() -> new SshConnectionWithPassword(hostname, port, username, password));
	}

	private static SshConnection getConnection(SshConnectionKey key, Supplier<SshConnection> connectionSupplier) {
		synchronized (connections) {
			return connections.computeIfAbsent(key, x -> connectionSupplier.get());
		}
	}

	private final String hostname;
	private final int port;
	private final String username;

	private final AtomicReference<Session> sessionReference = new AtomicReference<>(null);

	protected SshConnection(String hostname, int port, String username) {
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

	private boolean isSessionAlive(Session session) {
		try {
			ChannelExec testChannel = (ChannelExec) sessionReference.get().openChannel("exec");
			testChannel.setCommand("true");
			testChannel.connect();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Session erfolgreich getestet, verwende sie erneut");
			}
			testChannel.disconnect();
			return true;
		} catch (JSchException e) {
			try {
				session.disconnect();
			} catch (Exception ignore) {
			}
			return false;
		}
	}

	private Session getSession() {
		synchronized (sessionReference) {
			if (sessionReference.get() != null) {
				if (isSessionAlive(sessionReference.get())) {
					return sessionReference.get();
				} else {
					sessionReference.set(null);
				}
			}

			try {
				sessionReference.set(createSession(JSCH));
				return sessionReference.get();
			} catch (JSchException e) {
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
		String result = runSshCommand("lparstat 1 1");
		if (result != null) {
			double percent = parseCpuUsage(result);
			if (percent < 0.0) {
				percent = 0.0;
			}
			if (percent > 1.0) {
				percent = 1.0;
			}
			return percent;
		} else {
			return Double.NaN;
		}
	}

	public List<ProcessInfo> getTopProcesses(String... greps) {
		String command = "ps -e -o pcpu,args | /bin/sort -u -r";// | grep DataFlowEngine | grep -v ksh | head"
		for (String grep : greps) {
			command += " | grep " + grep;
		}
		command += " | head";
		String result = runSshCommand(command);
		String[] lines = result.split("[\\r\\n]+");

		List<ProcessInfo> processes = new ArrayList<>();
		for (String line : lines) {
			String[] process = line.trim().split("[\\s]+", 2);
			processes.add(new ProcessInfo(process[1], Double.parseDouble(process[0])));
		}
		return processes;
	}

	public String getTopas() {
		String filename = "hl_topas_" + UUID.randomUUID().toString() + ".tmp";
		String[] commands = new String[] { //
				"cd /tmp", //
				"(sleep 3; echo q)|topas > " + filename, //
				"cat " + filename, //
				"rm " + filename };
		//String string = runSshCommand(session, "(sleep 3; echo q)|topas");
		String string = runShellCommands(commands);
		return string;
	}

	public static final class ProcessInfo {

		public final String name;
		public final double usage;

		private ProcessInfo(String name, double usage) {
			this.name = name;
			this.usage = usage;
		}
	}

	private static double parseCpuUsage(String string) {
		if (string.contains("Linux")) {
			return Double.NaN;
			//			String[] parts = string.split("[\\s]+");
			//			return 1.0 - Double.parseDouble(parts[parts.length - 1]) / 100.0;
		} else {
			String[] lines = string.split("[\\r\\n]+");
			int pos = lines[lines.length - 3].indexOf("%idle");
			int len = lines[lines.length - 2].indexOf(" ", pos);
			return 1.0 - Double.parseDouble(lines[lines.length - 1].substring(pos, len)) / 100.0;
		}
	}

	private String runSshCommand(String command) {
		try {
			Session session = getSession();

			ChannelExec channel = (ChannelExec) session.openChannel("exec");
			channel.setCommand(command);
			channel.setInputStream(null);
			channel.setErrStream(System.err);
			InputStream in = channel.getInputStream();
			channel.connect();

			ByteArrayOutputStream bos = new ByteArrayOutputStream();

			byte[] buffer = new byte[1024];
			while (true) {
				while (in.available() > 0) {
					int len = in.read(buffer, 0, 1024);
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
				} catch (Exception ignore) {
				}
			}
			channel.disconnect();

			return bos.toString();
		} catch (Exception e) {
			LOGGER.error("Unknown exception while running SSH command.", e);
			return null;
		}
	}

	private String runShellCommands(String... commands) {
		try {
			Session session = getSession();

			ChannelShell channel = (ChannelShell) session.openChannel("shell");
			//			channel.setCommand(command);
			//			channel.setInputStream(null);
			//			channel.setErrStream(System.err);
			//			InputStream in = channel.getInputStream();
			channel.connect();
			PrintStream out = new PrintStream(channel.getOutputStream());

			out.println("#!/bin/bash");
			for (String command : commands) {
				out.println(command);
			}
			out.print("exit");
			out.flush();

			InputStream in = channel.getInputStream();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			while (true) {
				while (in.available() > 0) {
					int len = in.read(buffer, 0, 1024);
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
				if ((bos.size() > 10 ? bos.toString().substring(bos.size() - 10) : bos.toString()).contains("exit")) {
					break;
				}
				try {
					Thread.sleep(100);
				} catch (Exception ignore) {
				}
			}
			channel.disconnect();

			return bos.toString();
		} catch (Exception e) {
			LOGGER.error("Unknown exception while running SSH command.", e);
			return null;
		}
	}
}
