package com.hlag.oversigt.util;

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
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

public final class HttpUtils {

	private HttpUtils() {
		throw new RuntimeException();
	}

	public static <T> T reloadWithGet(HttpServerExchange exchange) {
		return redirect(exchange, exchange.getRequestURI(), false, true);
	}

	public static <T> T redirect(HttpServerExchange exchange, String location, boolean permanent, boolean changeToGet) {
		int code = StatusCodes.FOUND;
		if (permanent) {
			if (changeToGet) {
				code = StatusCodes.MOVED_PERMANENTLY;
			} else {
				code = StatusCodes.PERMANENT_REDIRECT;
			}
		} else {
			if (changeToGet) {
				code = StatusCodes.SEE_OTHER;
			} else {
				code = StatusCodes.TEMPORARY_REDIRECT;
			}
		}
		exchange.setStatusCode(code);
		exchange.getResponseHeaders().put(Headers.LOCATION, location);
		exchange.endExchange();
		return null;
	}

	public static <T> T notFound(HttpServerExchange exchange) {
		return notFound(exchange, "Not found");
	}

	public static <T> T notFound(HttpServerExchange exchange, String message) {
		exchange.setStatusCode(StatusCodes.NOT_FOUND);
		exchange.getResponseSender().send("404 - " + message);
		exchange.endExchange();
		return null;
	}

	public static <T> T forbidden(HttpServerExchange exchange) {
		exchange.setStatusCode(StatusCodes.FORBIDDEN);
		exchange.getResponseSender().send("403 - Forbidden");
		exchange.endExchange();
		return null;
	}

	public static <T> T badRequest(HttpServerExchange exchange) {
		return badRequest(exchange, null);
	}

	public static <T> T badRequest(HttpServerExchange exchange, String message) {
		exchange.setStatusCode(StatusCodes.BAD_REQUEST);
		exchange.getResponseSender().send("400 - Bad Request" + (message != null ? " - " + message : ""));
		exchange.endExchange();
		return null;
	}

	public static <T> T internalServerError(HttpServerExchange exchange) {
		exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
		exchange.getResponseSender().send("500 - Internal Server Error");
		exchange.endExchange();
		return null;
	}

	public static <T> T query(HttpServerExchange exchange,
			String name,
			Function<String, T> converter,
			T defaultIfNotPresent) {
		return query(exchange, name).map(converter).orElse(defaultIfNotPresent);
	}

	public static Optional<String> query(HttpServerExchange exchange, String name) {
		return Optional//
				.ofNullable(exchange.getQueryParameters())//
				.map(qp -> qp.get(name))//
				.map(l -> l.peek());
	}

	/**This method tries to extract a parameter of the given name from the given {@link FormData}. If the parameter cannot be found a {@link RuntimeException} will be thrown.
	 * @param formData the data from which to extract the parameter
	 * @param name the name of the parameter to extract
	 * @return the string value of the extracted parameter
	 * @throws RuntimeException if the named parameter cannot be found
	 */
	public static String param(FormData formData, String name) {
		return maybeParam(formData, name).orElseThrow(
				() -> new RuntimeException(String.format("Unable to extract parameter '%s' from form data.", name)));
	}

	/**Tries to extract the a parameter of the given name from the given {@link FormData}.
	 * @param formData the data from which to extract the parameter
	 * @param name the name of the parameter to extract
	 * @return an {@link Optional} that may hold the extracted value
	 */
	public static Optional<String> maybeParam(FormData formData, String name) {
		return Optional.of(formData).map(fd -> fd.get(name)).map(Deque::getFirst).map(FormValue::getValue);
	}

	public static FormData getFormData(HttpServerExchange exchange) throws IOException {
		Builder builder = FormParserFactory.builder();
		builder.setDefaultCharset("UTF-8");
		FormDataParser parser = builder.build().createParser(exchange);
		return parser.parseBlocking();
	}

	public static void doNonBlocking(HttpHandler handler, HttpServerExchange exchange) throws Exception {
		if (exchange.isInIoThread()) {
			exchange.dispatch(handler);
		} else {
			handler.handleRequest(exchange);
		}
	}

	public static Optional<Session> getSession(HttpServerExchange exchange) {
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

	public static Optional<Principal> getPrincipal(HttpServerExchange exchange) {
		return getSession(exchange).map(session -> (Principal) session.getAttribute("PRINCIPAL"));
	}

	public static boolean hasSession(HttpServerExchange exchange) {
		return getSession(exchange).isPresent();
	}

	public static Session getOrCreateSession(HttpServerExchange exchange) {
		// To retrive the SessionManager use the attachmentKey
		SessionManager sm = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
		// same goes to SessionConfig
		SessionConfig sessionConfig = exchange.getAttachment(SessionConfig.ATTACHMENT_KEY);

		Session session = sm.getSession(exchange, sessionConfig);
		if (session == null) {
			session = sm.createSession(exchange, sessionConfig);
		}
		return session;
	}

	/**
	   * Convert a byte array to a URL encoded string
	   *
	   * @param in
	   *            byte[]
	   * @return String
	   */
	public static String encodeByteArrayToUrlString(byte in[]) {
		byte ch = 0x00;
		int i = 0;
		if (in == null || in.length <= 0) {
			return null;
		}

		String pseudo[] = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F" };
		StringBuilder out = new StringBuilder(in.length * 2);

		while (i < in.length) {
			// First check to see if we need ASCII or HEX
			if (in[i] >= '0' && in[i] <= '9' || in[i] >= 'a' && in[i] <= 'z' || in[i] >= 'A' && in[i] <= 'Z'
					|| in[i] == '$' || in[i] == '-' || in[i] == '_' || in[i] == '.' || in[i] == '!') {
				out.append((char) in[i]);
				i++;
			} else {
				out.append('%');
				ch = (byte) (in[i] & 0xF0); // Strip off high nibble
				ch = (byte) (ch >>> 4); // shift the bits down
				ch = (byte) (ch & 0x0F); // must do this is high order bit is
				// on!
				out.append(pseudo[ch]); // convert the nibble to a
				// String Character
				ch = (byte) (in[i] & 0x0F); // Strip off low nibble
				out.append(pseudo[ch]); // convert the nibble to a
				// String Character
				i++;
			}
		}

		String rslt = new String(out);

		return rslt;

	}
}
