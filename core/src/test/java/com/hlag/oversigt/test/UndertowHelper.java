package com.hlag.oversigt.test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.function.BiConsumer;

import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import com.hlag.oversigt.util.SneakyException;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.protocol.http.HttpServerConnection;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;

public class UndertowHelper {

	public static FormData createFormData(String... strings) {
		FormData formData = new FormData(2000);
		addPairs(formData::add, strings);
		return formData;
	}

	public static HttpServerExchange createHttpExchangeWithQueryParameters(String... queryParameters) {
		final HeaderMap headerMap = new HeaderMap();
		final StreamConnection streamConnection = createStreamConnection();
		final OptionMap options = OptionMap.EMPTY;
		final ServerConnection connection = new HttpServerConnection(streamConnection, null, null, options, 0, null);

		return createHttpExchange(connection, headerMap, queryParameters);
	}

	public static HttpServerExchange createHttpExchange() {
		final HeaderMap headerMap = new HeaderMap();
		final StreamConnection streamConnection = createStreamConnection();
		final OptionMap options = OptionMap.EMPTY;
		final ServerConnection connection = new HttpServerConnection(streamConnection, null, null, options, 0, null);
		return createHttpExchange(connection, headerMap, null);
	}

	private static StreamConnection createStreamConnection() {
		final StreamConnection streamConnection = mock(StreamConnection.class);
		ConduitStreamSinkChannel sinkChannel;
		try {
			sinkChannel = createSinkChannel();
		} catch (IOException e) {
			throw new SneakyException(e);
		}
		when(streamConnection.getSinkChannel()).thenReturn(sinkChannel);
		ConduitStreamSourceChannel sourceChannel = createSourceChannel();
		when(streamConnection.getSourceChannel()).thenReturn(sourceChannel);
		// XnioIoThread ioThread = mock(XnioIoThread.class);
		// when(streamConnection.getIoThread()).thenReturn(ioThread);
		return streamConnection;
	}

	private static ConduitStreamSinkChannel createSinkChannel() throws IOException {
		StreamSinkConduit sinkConduit = mock(StreamSinkConduit.class);
		// when(sinkConduit.write(any(ByteBuffer.class))).thenReturn(1);
		ConduitStreamSinkChannel sinkChannel = new ConduitStreamSinkChannel(null, sinkConduit);
		return sinkChannel;
	}

	private static ConduitStreamSourceChannel createSourceChannel() {
		StreamSourceConduit sourceConduit = mock(StreamSourceConduit.class);
		ConduitStreamSourceChannel sourceChannel = new ConduitStreamSourceChannel(null, sourceConduit);
		return sourceChannel;
	}

	private static HttpServerExchange createHttpExchange(ServerConnection connection,
			HeaderMap headerMap,
			String[] queryParameters) {
		HttpServerExchange httpServerExchange = new HttpServerExchange(connection, null, headerMap, 200);
		httpServerExchange.setRequestMethod(new HttpString("GET"));
		httpServerExchange.setProtocol(Protocols.HTTP_1_1);
		addPairs(httpServerExchange::addQueryParam, queryParameters);
		return httpServerExchange;
	}

	private static void addPairs(BiConsumer<String, String> consumer, String[] strings) {
		if (strings != null) {
			for (int i = 0; i < strings.length; i += 2) {
				consumer.accept(strings[i], strings[i + 1]);
			}
		}
	}
}