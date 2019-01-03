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

@Api(tags = { "EventSource" }, //
		authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
@Path("/event-source/state")
public class EventSourceStatusResource {
	@Inject
	private DashboardController controller;

	@GET
	@Path("/{id}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returns a current event source instance state", response = EventSourceInstanceState.class),
			@ApiResponse(code = 404, message = "The event source instance does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Read an event source instance's current state")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	@NoChangeLog
	public Response isInstanceRunning(@PathParam("id") @NotBlank String instanceId) {
		EventSourceInstance instance;
		try {
			instance = controller.getEventSourceInstance(instanceId);
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("The event source instance does not exist");
		}

		EventSourceInstanceState state = new EventSourceInstanceState();
		state.id = instance.getId();
		state.running = controller.isRunning(instance);
		state.lastRun = controller.getLastRun(instance);
		state.lastSuccess = controller.getLastSuccessfulRun(instance);
		state.lastFailure = controller.getLastFailureDateTime(instance);
		state.lastException = controller.getLastFailureException(instance);
		state.lastReason = controller.getLastFailureDescription(instance);

		return ok(state).build();
	}

	@POST
	@Path("/{id}")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "Returns a current event source instance state after changing the state", response = EventSourceInstanceState.class),
			@ApiResponse(code = 404, message = "The event source instance does not exist", response = ErrorResponse.class),
			@ApiResponse(code = 412, message = "The event source was not in a suitable state", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Start or stop an event source instance")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response setInstanceRunning(@PathParam("id") @NotBlank String instanceId,
			@QueryParam("running") @ApiParam(required = true, value = "Whether the event source instance shall be running or not") boolean running) {
		try {
			if (running) {
				controller.startInstance(instanceId);
			} else {
				controller.stopInstance(instanceId);
			}
			return isInstanceRunning(instanceId);
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("The event source instance does not exist");
		} catch (Exception e) {
			return ErrorResponse.preconditionFailed("Unable to start/ stop event source", e);
		}
	}

	public static class EventSourceInstanceState {
		@NotBlank
		private String id;
		private boolean running;
		private ZonedDateTime lastRun;
		private ZonedDateTime lastSuccess;
		private ZonedDateTime lastFailure;
		private String lastException;
		private String lastReason;

		public String getId() {
			return id;
		}

		public boolean isRunning() {
			return running;
		}

		public ZonedDateTime getLastRun() {
			return lastRun;
		}

		public ZonedDateTime getLastSuccess() {
			return lastSuccess;
		}

		public ZonedDateTime getLastFailure() {
			return lastFailure;
		}

		public String getLastException() {
			return lastException;
		}

		public String getLastReason() {
			return lastReason;
		}
	}
}