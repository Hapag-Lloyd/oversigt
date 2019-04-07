package com.hlag.oversigt.web;

import java.io.IOException;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Function;

import com.hlag.oversigt.security.Principal;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormData.FormValue;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.FormParserFactory.Builder;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;

public class HttpServerExchangeHandler {
	void doNonBlocking(final HttpHandler handler, final HttpServerExchange exchange) throws Exception {
		if (exchange.isInIoThread()) {
			exchange.dispatch(handler);
		} else {
			handler.handleRequest(exchange);
		}
	}

	<T> T query(final HttpServerExchange exchange,
			final String name,
			final Function<String, T> converter,
			final T defaultIfNotPresent) {
		return query(exchange, name).map(converter).orElse(defaultIfNotPresent);
	}

	Optional<String> query(final HttpServerExchange exchange, final String name) {
		return Optional//
				.ofNullable(exchange.getQueryParameters())//
				.map(qp -> qp.get(name))//
				.map(l -> l.peek());
	}

	/**
	 * This method tries to extract a parameter of the given name from the given
	 * {@link FormData}. If the parameter cannot be found a {@link RuntimeException}
	 * will be thrown.
	 *
	 * @param formData the data from which to extract the parameter
	 * @param name     the name of the parameter to extract
	 * @return the string value of the extracted parameter
	 * @throws RuntimeException if the named parameter cannot be found
	 */
	String param(final FormData formData, final String name) {
		return maybeParam(formData, name).orElseThrow(
				() -> new RuntimeException(String.format("Unable to extract parameter '%s' from form data.", name)));
	}

	/**
	 * Tries to extract the a parameter of the given name from the given
	 * {@link FormData}.
	 *
	 * @param formData the data from which to extract the parameter
	 * @param name     the name of the parameter to extract
	 * @return an {@link Optional} that may hold the extracted value
	 */
	Optional<String> maybeParam(final FormData formData, final String name) {
		return Optional.of(formData).map(fd -> fd.get(name)).map(Deque::getFirst).map(FormValue::getValue);
	}

	FormData getFormData(final HttpServerExchange exchange) throws IOException {
		final Builder builder = FormParserFactory.builder();
		builder.setDefaultCharset("UTF-8");
		try (FormDataParser parser = builder.build().createParser(exchange)) {
			return parser.parseBlocking();
		}
	}

	public Optional<Session> getSession(final HttpServerExchange exchange) {
		// To retrive the SessionManager use the attachmentKey
		final SessionManager sessionManager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
		// same goes to SessionConfig
		final SessionConfig sessionConfig = exchange.getAttachment(SessionConfig.ATTACHMENT_KEY);

		return Optional.ofNullable(sessionManager).map(s -> s.getSession(exchange, sessionConfig));
	}

	public Optional<Principal> getPrincipal(final HttpServerExchange exchange) {
		return getSession(exchange).map(session -> (Principal) session.getAttribute("PRINCIPAL"));
	}

	public boolean hasSession(final HttpServerExchange exchange) {
		return getSession(exchange).isPresent();
	}

	Session getOrCreateSession(final HttpServerExchange exchange) {
		// To retrieve the SessionManager use the attachmentKey
		final SessionManager sm = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
		// same goes to SessionConfig
		final SessionConfig sessionConfig = exchange.getAttachment(SessionConfig.ATTACHMENT_KEY);

		Session session = sm.getSession(exchange, sessionConfig);
		if (session == null) {
			session = sm.createSession(exchange, sessionConfig);
		}
		return session;
	}
}
