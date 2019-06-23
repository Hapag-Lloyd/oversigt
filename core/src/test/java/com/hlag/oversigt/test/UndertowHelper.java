package com.hlag.oversigt.test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.BiConsumer;

import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.protocol.http.HttpServerConnection;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;

public final class UndertowHelper {

	public static FormData createFormData(final String... strings) {
		final FormData formData = new FormData(2000);
		addPairs(formData::add, strings);
		return formData;
	}

	@SuppressWarnings("resource")
	public static HttpServerExchange createHttpExchangeWithQueryParameters(final String... queryParameters) {
		final HeaderMap headerMap = new HeaderMap();
		final StreamConnection streamConnection = createStreamConnection();
		final OptionMap options = OptionMap.EMPTY;
		final ServerConnection connection = new HttpServerConnection(streamConnection, null, null, options, 0, null);

		return createHttpExchange(connection, headerMap, queryParameters);
	}

	@SuppressWarnings("resource")
	public static HttpServerExchange createHttpExchange() {
		final HeaderMap headerMap = new HeaderMap();
		final StreamConnection streamConnection = createStreamConnection();
		final OptionMap options = OptionMap.EMPTY;
		final ServerConnection connection = new HttpServerConnection(streamConnection, null, null, options, 0, null);
		return createHttpExchange(connection, headerMap, null);
	}

	private static HttpServerExchange createHttpExchange(final ServerConnection connection,
			final HeaderMap headerMap,
			final String[] queryParameters) {
		final HttpServerExchange httpServerExchange = new HttpServerExchange(connection, null, headerMap, 200);
		httpServerExchange.setRequestMethod(new HttpString("GET"));
		httpServerExchange.setProtocol(Protocols.HTTP_1_1);
		addPairs(httpServerExchange::addQueryParam, queryParameters);
		return httpServerExchange;
	}

	@SuppressWarnings("resource")
	private static StreamConnection createStreamConnection() {
		final StreamConnection streamConnection = mock(StreamConnection.class);
		final ConduitStreamSinkChannel sinkChannel;
		sinkChannel = createSinkChannel();
		when(streamConnection.getSinkChannel()).thenReturn(sinkChannel);
		final ConduitStreamSourceChannel sourceChannel = createSourceChannel();
		when(streamConnection.getSourceChannel()).thenReturn(sourceChannel);
		// XnioIoThread ioThread = mock(XnioIoThread.class);
		// when(streamConnection.getIoThread()).thenReturn(ioThread);
		return streamConnection;
	}

	private static ConduitStreamSinkChannel createSinkChannel() {
		final StreamSinkConduit sinkConduit = mock(StreamSinkConduit.class);
		// when(sinkConduit.write(any(ByteBuffer.class))).thenReturn(1);
		final ConduitStreamSinkChannel sinkChannel = new ConduitStreamSinkChannel(null, sinkConduit);
		return sinkChannel;
	}

	private static ConduitStreamSourceChannel createSourceChannel() {
		final StreamSourceConduit sourceConduit = mock(StreamSourceConduit.class);
		final ConduitStreamSourceChannel sourceChannel = new ConduitStreamSourceChannel(null, sourceConduit);
		return sourceChannel;
	}

	private static void addPairs(final BiConsumer<String, String> consumer, final String[] strings) {
		if (strings != null) {
			for (int i = 0; i < strings.length; i += 2) {
				consumer.accept(strings[i], strings[i + 1]);
			}
		}
	}

	private UndertowHelper() {
		throw new UnsupportedOperationException();
	}
}
