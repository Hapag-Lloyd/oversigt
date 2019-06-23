package com.hlag.oversigt.web.resources;

import static javax.ws.rs.core.Response.ok;

import java.time.ZonedDateTime;
import java.util.NoSuchElementException;

import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.NotBlank;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.model.EventSourceInstance;
import com.hlag.oversigt.security.Role;
import com.hlag.oversigt.web.api.ApiAuthenticationFilter;
import com.hlag.oversigt.web.api.ErrorResponse;
import com.hlag.oversigt.web.api.JwtSecured;
import com.hlag.oversigt.web.api.NoChangeLog;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import lombok.Builder;
import lombok.Getter;

@Api(tags = { "EventSource" }, //
		authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
@Path("/event-source/state")
@Singleton
public class EventSourceStatusResource {
	@Inject
	private DashboardController controller;

	@GET
	@Path("/{id}")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "Returns a current event source instance state",
					response = EventSourceInstanceState.class),
			@ApiResponse(code = 404,
					message = "The event source instance does not exist",
					response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Read an event source instance's current state")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	@NoChangeLog
	public Response isInstanceRunning(@PathParam("id") @NotBlank final String instanceId) {
		try {
			return ok(EventSourceInstanceState.fromInstance(controller, controller.getEventSourceInstance(instanceId)))
					.build();
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("The event source instance does not exist");
		}
	}

	@POST
	@Path("/{id}")
	@ApiResponses({ //
			@ApiResponse(code = 200,
					message = "Returns a current event source instance state after changing the state",
					response = EventSourceInstanceState.class),
			@ApiResponse(code = 404,
					message = "The event source instance does not exist",
					response = ErrorResponse.class),
			@ApiResponse(code = 412,
					message = "The event source was not in a suitable state",
					response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Start or stop an event source instance")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response setInstanceRunning(@PathParam("id") @NotBlank final String instanceId,
			@QueryParam("running") @ApiParam(required = true,
					value = "Whether the event source instance shall be running or not") final boolean running) {
		try {
			if (running) {
				controller.startInstance(instanceId);
			} else {
				controller.stopInstance(instanceId);
			}
			return ok(EventSourceInstanceState.fromInstance(controller, controller.getEventSourceInstance(instanceId)))
					.build();
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("The event source instance does not exist");
		} catch (final Exception e) {
			return ErrorResponse.preconditionFailed("Unable to start/ stop event source", e);
		}
	}

	@Builder
	@Getter
	public static class EventSourceInstanceState {
		public static EventSourceInstanceState fromInstance(final DashboardController controller,
				final EventSourceInstance instance) {
			return EventSourceInstanceState.builder()
					.id(instance.getId())
					.serviceClass(instance.getDescriptor().getServiceClassName())
					.createdBy(instance.getCreatedBy())
					.lastChangedBy(instance.getLastChangeBy())
					.running(controller.isRunning(instance))
					.lastRun(controller.getLastRun(instance).orElse(null))
					.lastSuccess(controller.getLastSuccessfulRun(instance).orElse(null))
					.lastFailure(controller.getLastFailureDateTime(instance).orElse(null))
					.lastReason(controller.getLastFailureDescription(instance).orElse(null))
					.lastException(controller.getLastFailureException(instance).orElse(null))
					.build();
		}

		@NotBlank
		private final String id;

		private final String serviceClass;

		private final String createdBy;

		private final String lastChangedBy;

		private final boolean running;

		private final ZonedDateTime lastRun;

		private final ZonedDateTime lastSuccess;

		private final ZonedDateTime lastFailure;

		private final String lastException;

		private final String lastReason;
	}
}
