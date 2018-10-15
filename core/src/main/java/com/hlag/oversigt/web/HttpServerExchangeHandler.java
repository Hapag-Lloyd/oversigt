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
	void doNonBlocking(HttpHandler handler, HttpServerExchange exchange) throws Exception {
		if (exchange.isInIoThread()) {
			exchange.dispatch(handler);
		} else {
			handler.handleRequest(exchange);
		}
	}

	<T> T query(HttpServerExchange exchange, String name, Function<String, T> converter, T defaultIfNotPresent) {
		return query(exchange, name).map(converter).orElse(defaultIfNotPresent);
	}

	Optional<String> query(HttpServerExchange exchange, String name) {
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
	String param(FormData formData, String name) {
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
	Optional<String> maybeParam(FormData formData, String name) {
		return Optional.of(formData).map(fd -> fd.get(name)).map(Deque::getFirst).map(FormValue::getValue);
	}

	FormData getFormData(HttpServerExchange exchange) throws IOException {
		Builder builder = FormParserFactory.builder();
		builder.setDefaultCharset("UTF-8");
		FormDataParser parser = builder.build().createParser(exchange);
		return parser.parseBlocking();
	}

	public Optional<Session> getSession(HttpServerExchange exchange) {
		// To retrive the SessionManager use the attachmentKey
		SessionManager sm = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
		// same goes to SessionConfig
		SessionConfig sessionConfig = exchange.getAttachment(SessionConfig.ATTACHMENT_KEY);

		if (sm != null) {
			return Optional.ofNullable(sm.getSession(exchange, sessionConfig));
		} else {
			return Optional.empty();
		}
	}

	public Optional<Principal> getPrincipal(HttpServerExchange exchange) {
		return getSession(exchange).map(session -> (Principal) session.getAttribute("PRINCIPAL"));
	}

	public boolean hasSession(HttpServerExchange exchange) {
		return getSession(exchange).isPresent();
	}

	Session getOrCreateSession(HttpServerExchange exchange) {
		// To retrieve the SessionManager use the attachmentKey
		SessionManager sm = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
		// same goes to SessionConfig
		SessionConfig sessionConfig = exchange.getAttachment(SessionConfig.ATTACHMENT_KEY);

		Session session = sm.getSession(exchange, sessionConfig);
		if (session == null) {
			session = sm.createSession(exchange, sessionConfig);
		}
		return session;
	}
}
