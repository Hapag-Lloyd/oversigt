package com.hlag.oversigt.web.resources;

import static javax.ws.rs.core.Response.ok;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.NotBlank;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.core.eventsource.RunStatistic;
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

@Api(tags = { "EventSource" },
		authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
@Path("/event-source/state")
@Singleton
public class EventSourceStatusResource {
	private static final Logger LOGGER = LoggerFactory.getLogger(EventSourceStatusResource.class);

	@Inject
	private DashboardController controller;

	public EventSourceStatusResource() {
		// no fields to be initialized manually, some will be injected
	}

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
	@ApiResponses({
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
			LOGGER.error("Unable to set event source status", e);
			return ErrorResponse.preconditionFailed("Unable to start/ stop event source", e);
		}
	}

	public static class EventSourceInstanceState {
		public static EventSourceInstanceState fromInstance(final DashboardController controller,
				final EventSourceInstance instance) {
			return new EventSourceInstanceState(instance.getId(),
					instance.getDescriptor().getServiceClassName(),
					instance.getCreatedBy(),
					instance.getLastChangeBy(),
					controller.isRunning(instance),
					controller.getRunStatistics(instance),
					controller.getLastSuccessfulRun(instance),
					controller.getLastRun(instance));
		}

		@NotBlank
		private final String id;

		private final String serviceClass;

		private final String createdBy;

		private final String lastChangedBy;

		private final boolean running;

		private final List<RunStatistic> statistics;

		private final Optional<RunStatistic> lastSuccessfulRun;

		private final Optional<RunStatistic> lastRun;

		public EventSourceInstanceState(@NotBlank final String id,
				final String serviceClass,
				final String createdBy,
				final String lastChangedBy,
				final boolean running,
				final List<RunStatistic> statistics,
				final Optional<RunStatistic> lastSuccessfulRun,
				final Optional<RunStatistic> lastRun) {
			this.id = id;
			this.serviceClass = serviceClass;
			this.createdBy = createdBy;
			this.lastChangedBy = lastChangedBy;
			this.running = running;
			this.statistics = statistics;
			this.lastSuccessfulRun = lastSuccessfulRun;
			this.lastRun = lastRun;
		}

		public String getId() {
			return id;
		}

		public String getServiceClass() {
			return serviceClass;
		}

		public String getCreatedBy() {
			return createdBy;
		}

		public String getLastChangedBy() {
			return lastChangedBy;
		}

		public boolean isRunning() {
			return running;
		}

		public List<RunStatistic> getStatistics() {
			return statistics;
		}

		public Optional<RunStatistic> getLastSuccessfulRun() {
			return lastSuccessfulRun;
		}

		public Optional<RunStatistic> getLastRun() {
			return lastRun;
		}
	}
}
