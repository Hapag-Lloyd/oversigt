package com.hlag.oversigt.web.api;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Ascii;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import de.larssh.utils.SneakyException;
import edu.umd.cs.findbugs.annotations.Nullable;

@Provider
public class ApiExceptionHandler implements ExceptionMapper<Exception> {
	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private final boolean debug;

	@Inject
	public ApiExceptionHandler(@Named("debug") final boolean debug) {
		this.debug = debug;
	}

	@Nullable
	@Context
	private HttpServletRequest injectedRequest;

	private static final Pattern PATTERN_COMMA = Pattern.compile(",");

	private static final Pattern PATTERN_SEMICOLON = Pattern.compile(";");

	/** {@inheritDoc} */
	@Override
	public Response toResponse(@Nullable final Exception nullableException) {
		final Exception exception = Objects.requireNonNull(nullableException);

		final UUID uuid = UUID.randomUUID();
		LOGGER.error("Error while calling API method - " + uuid.toString(), exception);
		if (exception instanceof ApiValidationException) {
			final ApiValidationException ave = (ApiValidationException) exception;
			return ErrorResponse.badRequest(uuid,
					"Unable to validate input parameters.",
					ave.getViolations()
							.stream()
							.map(v -> v.getPropertyPath() + " " + v.getMessage())
							.collect(Collectors.toList()));
		}
		if (!debug) {
			return ErrorResponse.internalServerError(uuid,
					"Internal server error. More details can be found in the server log file.");
		}
		final String[] accepts = PATTERN_COMMA.split(Objects.requireNonNull(injectedRequest).getHeader("accept"), 0);
		for (final String accept : accepts) {
			final String[] parts = PATTERN_SEMICOLON.split(accept, 0);
			switch (Ascii.toLowerCase(parts[0].trim())) {
			case "application/json":
			case "application/xml":
				final StringWriter sw = new StringWriter();
				final PrintWriter pw = new PrintWriter(sw);
				pw.println("ID: " + uuid.toString());
				pw.println();
				exception.printStackTrace(pw);
				return Response.status(Status.INTERNAL_SERVER_ERROR)
						.entity(sw.toString())
						.type(MediaType.TEXT_PLAIN_TYPE)
						.build();
			default:
			}
		}
		throw new SneakyException(exception);
	}
}
