package com.hlag.oversigt.util;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

public final class HttpUtils {

	public static void redirect(final HttpServerExchange exchange,
			final String location,
			final boolean permanent,
			final boolean changeToGet) {
		final int code;
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
	}

	public static void notFound(final HttpServerExchange exchange) {
		notFound(exchange, "Not found");
	}

	public static void notFound(final HttpServerExchange exchange, final String message) {
		exchange.setStatusCode(StatusCodes.NOT_FOUND);
		exchange.getResponseSender().send("404 - " + message);
		exchange.endExchange();
	}

	public static void forbidden(final HttpServerExchange exchange) {
		exchange.setStatusCode(StatusCodes.FORBIDDEN);
		exchange.getResponseSender().send("403 - Forbidden");
		exchange.endExchange();
	}

	public static void badRequest(final HttpServerExchange exchange) {
		exchange.setStatusCode(StatusCodes.BAD_REQUEST);
		exchange.getResponseSender().send("400 - Bad Request");
		exchange.endExchange();
	}

	/**
	 * Convert a byte array to a URL encoded string
	 *
	 * @param in byte[]
	 * @return String
	 */
	@SuppressWarnings("checkstyle:IllegalToken")
	public static String encodeByteArrayToUrlString(final byte[] in) {
		int i = 0;
		if (in.length == 0) {
			return "";
		}

		final String[] pseudo = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F" };
		final StringBuilder out = new StringBuilder(2 * in.length);

		while (i < in.length) {
			// First check to see if we need ASCII or HEX
			if (in[i] >= '0' && in[i] <= '9'
					|| in[i] >= 'a' && in[i] <= 'z'
					|| in[i] >= 'A' && in[i] <= 'Z'
					|| in[i] == '$'
					|| in[i] == '-'
					|| in[i] == '_'
					|| in[i] == '.'
					|| in[i] == '!') {
				out.append((char) in[i]);
				i += 1;
			} else {
				out.append('%');
				byte ch = (byte) (in[i] & 0xF0); // Strip off high nibble
				ch = (byte) (ch >>> 4); // shift the bits down
				ch = (byte) (ch & 0x0F); // must do this is high order bit is
				// on!
				out.append(pseudo[ch]); // convert the nibble to a
				// String Character
				ch = (byte) (in[i] & 0x0F); // Strip off low nibble
				out.append(pseudo[ch]); // convert the nibble to a
				// String Character
				i += 1;
			}
		}

		return out.toString();
	}

	private HttpUtils() {
		throw new UnsupportedOperationException();
	}
}
