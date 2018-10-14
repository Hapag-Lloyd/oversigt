package com.hlag.oversigt.util;

import io.undertow.server.HttpServerExchange;
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

	/**
	 * Convert a byte array to a URL encoded string
	 *
	 * @param in byte[]
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
