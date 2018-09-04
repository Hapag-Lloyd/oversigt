package com.oversigt.restarter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ForkJoinPool;

import org.apache.commons.io.IOUtils;

import com.atlassian.util.concurrent.atomic.AtomicReference;
import com.google.common.base.Charsets;

public class Restarter implements Runnable {
	public static void main(String[] args) throws Exception {
		try (ServerSocket server = new ServerSocket(80)) {
			for (;;) {
				Socket socket = server.accept();
				ForkJoinPool.commonPool().execute(new Restarter(socket));
			}
		}
	}

	private final Socket socket;

	private Restarter(Socket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		try (Socket socketToClose = socket;
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(socket.getInputStream(), Charsets.US_ASCII));
				BufferedWriter writer = new BufferedWriter(
						new OutputStreamWriter(socket.getOutputStream(), Charsets.US_ASCII))) {

			String first = reader.readLine();
			String[] parts = first.split(" ", 3);
			IOUtils.readLines(reader);
			if ("/restart".equals(parts[1])) {
				ForkJoinPool.commonPool().execute(Restarter::restartApplication);
				sendResponse(writer, 200, "OK");
			} else {
				sendResponse(writer, 404, "Not Found");
			}

			writer.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendResponse(Writer writer, int code, String reason) throws IOException {
		writer.append("HTTP/1.0 " + code + " " + reason + "\r\n");
		writer.append("Connection: Close\r\n");
		writer.append("Content-length: 0\r\n");
		writer.append("\r\n");
	}

	private static AtomicReference<Process> processRef = new AtomicReference<>(null);

	private static synchronized void restartApplication() {
		if (processRef.get() != null) {
			Process process = processRef.get();
			process.destroy();
			// XXX macht der das richtig aus?
		}
		// TODO neu starten...
	}
}
