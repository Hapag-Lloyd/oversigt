package com.hlag.oversigt.web.api;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import edu.umd.cs.findbugs.annotations.Nullable;

@JsonInclude(Include.NON_NULL)
public final class ErrorResponse {
	public static Collection<String> getErrorMessages(final Exception e) {
		return Arrays.asList(e instanceof InvocationTargetException
				? ((InvocationTargetException) e).getTargetException().getMessage()
				: e.getMessage());
	}

	public static Response createErrorResponse(final Status status, final String message, final String... strings) {
		return createErrorResponse(status.getStatusCode(), message, strings);
	}

	public static Response createErrorResponse(final int statusCode, final String message, final String... strings) {
		return new ErrorResponse(message, Arrays.asList(strings)).forStatus(statusCode);
	}

	public static Response forbidden(final String message) {
		return createErrorResponse(Status.FORBIDDEN, message);
	}

	public static Response notFound(final String message, final String... strings) {
		return new ErrorResponse(message, Arrays.asList(strings)).forStatus(Status.NOT_FOUND);
	}

	public static Response badRequest(final String message) {
		return createErrorResponse(Status.BAD_REQUEST, message);
	}

	public static Response badRequest(final UUID uuid, final String message, final Collection<String> errors) {
		return new ErrorResponse(uuid, message, errors).forStatus(Status.BAD_REQUEST);
	}

	public static Response badRequest(final String message, final Collection<String> errors) {
		return new ErrorResponse(message, errors).forStatus(Status.BAD_REQUEST);
	}

	public static Response badRequest(final String message, final Exception e) {
		return badRequest(message, getErrorMessages(e));
	}

	public static Response unprocessableEntity(final String message, final Collection<String> errors) {
		return new ErrorResponse(message, errors).forStatus(422);
	}

	public static Response preconditionFailed(final String message, final Exception e) {
		return new ErrorResponse(message, getErrorMessages(e)).forStatus(Status.PRECONDITION_FAILED);
	}

	public static Response internalServerError(final UUID uuid, final String message) {
		return new ErrorResponse(uuid, message).forStatus(Status.INTERNAL_SERVER_ERROR);
	}

	@NotBlank
	@JsonProperty(required = true)
	@JsonPropertyDescription("The error message with which the API call failed")
	private String message;

	private final UUID uuid;

	@JsonProperty(required = false)
	@JsonPropertyDescription("Details to the error message")
	@Nullable
	private final Collection<@NotBlank @NotNull String> errors;

	private ErrorResponse(final UUID uuid, final String message) {
		this(uuid, message, Collections.emptyList());
	}

	private ErrorResponse(final String message) {
		this(message, Collections.emptyList());
	}

	private ErrorResponse(@NotBlank final String message, final Collection<String> errors) {
		uuid = UUID.randomUUID();
		this.message = message;
		this.errors = errors.isEmpty() ? null : new ArrayList<>(errors);
	}

	private ErrorResponse(final UUID uuid, @NotBlank final String message, final Collection<String> errors) {
		this.uuid = uuid;
		this.message = message;
		this.errors = errors.isEmpty() ? null : new ArrayList<>(errors);
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(final String message) {
		this.message = message;
	}

	public UUID getUuid() {
		return uuid;
	}

	@Nullable
	public Collection<String> getErrors() {
		return errors;
	}

	public Response forStatus(final Status status) {
		return forStatus(status.getStatusCode());
	}

	public Response forStatus(final int status) {
		return Response.status(status).entity(this).type(MediaType.APPLICATION_JSON_TYPE).build();
	}
}
