package com.hlag.oversigt.web.resources;

import static com.hlag.oversigt.web.api.ErrorResponse.notFound;
import static javax.ws.rs.core.Response.ok;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.model.EventSourceDescriptor;
import com.hlag.oversigt.model.EventSourceKey;
import com.hlag.oversigt.model.InvalidKeyException;
import com.hlag.oversigt.util.ImageUtil;
import com.hlag.oversigt.web.api.ApiAuthenticationFilter;
import com.hlag.oversigt.web.api.ErrorResponse;
import com.hlag.oversigt.web.api.JwtSecured;
import com.hlag.oversigt.web.api.NoChangeLog;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

@Api(tags = { "EventSource" },
		authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
@Path("/event-source/descriptions")
@Singleton
public class EventSourceDescriptionResource {
	public EventSourceDescriptionResource() {
		// no fields to be initialized
	}

	@Inject
	private DashboardController dashboardController;

	@GET
	@Path("/")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "Returns a list of available event sources",
					response = EventSourceInfo.class,
					responseContainer = "List") })
	@JwtSecured
	@ApiOperation(value = "List available event sources")
	@NoChangeLog
	public Response listAvailableEventSources() {
		return ok(dashboardController//
				.getEventSourceKeys()
				.stream()
				.map(dashboardController::getEventSourceDescriptor)
				.map(EventSourceInfo::new)
				.collect(Collectors.toList()))//
						.build();
	}

	@GET
	@Path("/{key}")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "The details of the requested event source descriptor",
					response = EventSourceDescriptor.class),
			@ApiResponse(code = 404,
					message = "The requested event source descriptor does not exist",
					response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Read event source description")
	@NoChangeLog
	public Response getEventSourceDetails(@PathParam("key") final String key) {
		try {
			final EventSourceDescriptor descriptor
					= dashboardController.getEventSourceDescriptor(EventSourceKey.getKey(key));
			return ok(descriptor).build();
		} catch (final NoSuchElementException | InvalidKeyException e) {
			return notFound("The event source descriptor does not exist.");
		}
	}

	public static class EventSourceInfo {
		private final EventSourceDescriptor descriptor;

		EventSourceInfo(final EventSourceDescriptor descriptor) {
			this.descriptor = descriptor;
		}

		@NotNull
		public String getKey() {
			return descriptor.getKey().getKey();
		}

		@NotNull
		public String getName() {
			return descriptor.getDisplayName();
		}

		@NotNull
		public String getDescription() {
			return descriptor.getDescription();
		}

		@NotNull
		public String getImage() {
			return ImageUtil.getPreviewImageUrl(descriptor);
		}
	}
}
