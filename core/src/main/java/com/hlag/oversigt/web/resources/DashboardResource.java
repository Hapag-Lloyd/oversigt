package com.hlag.oversigt.web.resources;

import static com.hlag.oversigt.web.api.ErrorResponse.badRequest;
import static com.hlag.oversigt.web.api.ErrorResponse.forbidden;
import static com.hlag.oversigt.web.api.ErrorResponse.notFound;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.ok;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.inject.Inject;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.model.Widget;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.security.Role;
import com.hlag.oversigt.util.TypeUtils;
import com.hlag.oversigt.validate.UserId;
import com.hlag.oversigt.web.api.ApiAuthenticationFilter;
import com.hlag.oversigt.web.api.ApiValidationException;
import com.hlag.oversigt.web.api.ErrorResponse;
import com.hlag.oversigt.web.api.JwtSecured;
import com.hlag.oversigt.web.api.NoChangeLog;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

@Api(tags = { "Dashboard" })
@Path("/dashboards")
public class DashboardResource {
	@Inject
	private DashboardController dashboardController;
	@Inject
	private Authenticator authenticator;

	@Context
	private UriInfo uri;

	@GET
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returning a list of all dashboards", response = DashboardInfo.class, responseContainer = "List") })
	@ApiOperation(value = "List existing dashboards ids")
	@NoChangeLog
	public Response listDashboardIds() {
		return ok(dashboardController.getDashboardIds()
				.stream()
				.map(dashboardController::getDashboard)
				.map(d -> new DashboardInfo(d.getId(), d.getTitle()))
				.collect(toList())).build();
	}

	@PUT
	@ApiResponses({
			@ApiResponse(code = 201, message = "A new dashboard has been created. The body contains a detailed representation of the newly created dashboard", response = Dashboard.class), //
			@ApiResponse(code = 303, message = "The dashboard already exists. Sending redirect to dashboard information.") //
	})
	@JwtSecured
	@ApiOperation(value = "Create new dashboard", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	public Response createDashboard(@Context SecurityContext securityContext,
			@QueryParam("dashboardId") @NotNull String id,
			@QueryParam("owner") @NotNull @UserId String ownerUserId,
			@QueryParam("enabled") @ApiParam(defaultValue = "false") boolean enabled) {
		Dashboard dashboard = dashboardController.getDashboard(id);
		if (dashboard != null) {
			return Response.seeOther(URI.create(uri.getAbsolutePath() + "/" + id)).build();
		}

		enabled = enabled && securityContext.isUserInRole(Role.ROLE_NAME_SERVER_ADMIN);
		dashboard = dashboardController
				.createDashboard(id, Principal.loadPrincipal(authenticator, ownerUserId), enabled);
		return created(URI.create(uri.getAbsolutePath() + "/" + dashboard.getId()))//
				.entity(dashboard)
				.type(MediaType.APPLICATION_JSON_TYPE)
				.build();
	}

	@GET
	@Path("/{dashboardId}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "A detailed representation of the requested dashboard", response = Dashboard.class), //
			@ApiResponse(code = 404, message = "The requested dashboard does not exist", response = ErrorResponse.class) //
	})
	@JwtSecured(mustBeAuthenticated = false)
	@ApiOperation(value = "Read dashboard details", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@NoChangeLog
	@PermitAll
	public Response readDashboard(@Context SecurityContext secu, @PathParam("dashboardId") String id) {
		Optional<Map<String, Object>> dashboard = Optional.ofNullable(dashboardController.getDashboard(id))
				.map(TypeUtils::toMemberMap);
		if (dashboard.isPresent() && secu.getUserPrincipal() == null) {
			dashboard.get().remove("owner");
			dashboard.get().remove("editors");
		}
		if (dashboard.isPresent()) {
			return ok(dashboard.get()).build();
		} else {
			return notFound("A dashboard with id '" + id + "' does not exist.");
		}
	}

	@PUT
	@Path("/{dashboardId}")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "The dashboard has been updated", response = Dashboard.class), //
			@ApiResponse(code = 400, message = "The provided information are invalid", response = ErrorResponse.class), //
			@ApiResponse(code = 403, message = "The user is either not permitted to edit this dashboard or he wants to perform a change that he is not allowed to", response = ErrorResponse.class), //
			@ApiResponse(code = 404, message = "The dashboard does not exist") //
	})
	@ApiOperation(value = "Update dashboard details", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@JwtSecured
	@RolesAllowed("dashboard.{dashboardId}.editor")
	public Response updateDashboard(@Context SecurityContext securityContext,
			@PathParam("dashboardId") @NotNull String id,
			Dashboard newDashboardData) throws ApiValidationException {
		// load current dashboard from storage
		Dashboard dashboard = dashboardController.getDashboard(id);

		// does it exist?
		if (dashboard == null) {
			return notFound("A dashboard with id '" + id + "' does not exist.");
		}

		// check dashboard id
		if (!id.equals(newDashboardData.getId()) || !id.equals(dashboard.getId())) {
			return badRequest("The dashboard ID does not match");
		}

		// Check if the user changed the enabled state of the dashboard. Only server admins may perform that action
		if (newDashboardData.isEnabled() != dashboard.isEnabled()
				&& !securityContext.isUserInRole(Role.ROLE_NAME_SERVER_ADMIN)) {
			return forbidden(
					"Only server admins are allowed to change a dashboard's enabled state. Please contact a server admin.");
		}

		// if the owner has changed the user needs to be at least dashboard owner
		if (!newDashboardData.getOwner().equals(dashboard.getOwner())
				&& !securityContext.isUserInRole(Role.getDashboardOwnerRole(dashboard.getId()).getName())) {
			return forbidden("To change the owner of a dashboard you need to be at least the owner of the dashboard.");
		}

		// update dashboard data
		if (dashboardController.updateDashboard(newDashboardData)) {
			dashboard = dashboardController.getDashboard(id);
			return ok(dashboard).build();
		} else {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

	@PUT
	@Path("/{dashboardId}/positions")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "The widget positions has been updated"), //
			@ApiResponse(code = 400, message = "The provided information are invalid", response = ErrorResponse.class), //
			@ApiResponse(code = 404, message = "The dashboard does not exist") //
	})
	@ApiOperation(value = "Update dashboard widget positions", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@JwtSecured
	@RolesAllowed("dashboard.{dashboardId}.editor")
	public Response updateWidgetPositions(@PathParam("dashboardId") @NotNull String id,
			List<WidgetPosition> widgetPositions) throws ApiValidationException {
		// load current dashboard from storage
		Dashboard dashboard = dashboardController.getDashboard(id);

		// does it exist?
		if (dashboard == null) {
			return notFound("Dashboard does not exist.");
		}

		// Do all given widget positions match a dashboard widget?
		Set<String> unmatchedWidgetIds = widgetPositions.stream()//
				.mapToInt(WidgetPosition::getWidgetId)
				.filter(i -> !dashboard.getWidgets()//
						.stream()
						.mapToInt(Widget::getId)
						.anyMatch(w -> i == w))
				.mapToObj(Integer::toString)
				.collect(Collectors.toSet());
		if (!unmatchedWidgetIds.isEmpty()) {
			return badRequest("Some widgets ids do not exist in the dashboard", unmatchedWidgetIds);
		}

		// Update widget positions
		widgetPositions.forEach(t -> {
			Widget widget = dashboard.getWidget(t.getWidgetId());
			widget.setSizeX(t.getSizeX());
			widget.setSizeY(t.getSizeY());
			widget.setPosX(t.getPosX());
			widget.setPosY(t.getPosY());
			dashboardController.updateWidget(widget);
		});

		return ok().build();
	}

	@DELETE
	@Path("/{dashboardId}")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "The dashboard has been deleted"), //
			@ApiResponse(code = 404, message = "The dashboard does not exist", response = ErrorResponse.class) //
	})
	@JwtSecured
	@ApiOperation(value = "Delete dashboard", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_SERVER_ADMIN)
	public Response deleteDashboard(@PathParam("dashboardId") String id) {
		if (dashboardController.deleteDashboard(id)) {
			return ok().build();
		} else {
			return notFound("A dashboard with id '" + id + "' does not exist.");
		}
	}

	public static class DashboardInfo {
		@NotNull
		@NotEmpty
		@NotBlank
		private String id;
		@NotNull
		@NotEmpty
		@NotBlank
		private String title;

		public DashboardInfo(@NotNull @NotEmpty @NotBlank String id, String title) {
			this.id = id;
			this.title = title;
		}

		public String getId() {
			return id;
		}

		public String getTitle() {
			return title;
		}

	}

	public static class WidgetPosition {
		@NotNull
		@Min(1)
		@Positive
		@JsonPropertyDescription("The ID of the widget this position applys to")
		private int widgetId;
		@NotNull
		@Min(1)
		@Positive
		@JsonPropertyDescription("The widget's position on the X axis")
		private int posX;
		@NotNull
		@Min(1)
		@Positive
		@JsonPropertyDescription("The widget's position on the Y axis")
		private int posY;
		@NotNull
		@Min(1)
		@Positive
		@JsonPropertyDescription("The widget's width")
		private int sizeX;
		@NotNull
		@Min(1)
		@Positive
		@JsonPropertyDescription("The widget's height")
		private int sizeY;

		public int getWidgetId() {
			return widgetId;
		}

		public int getPosX() {
			return posX;
		}

		public int getPosY() {
			return posY;
		}

		public int getSizeX() {
			return sizeX;
		}

		public int getSizeY() {
			return sizeY;
		}

		public void setWidgetId(int widgetId) {
			this.widgetId = widgetId;
		}

		public void setPosX(int posX) {
			this.posX = posX;
		}

		public void setPosY(int posY) {
			this.posY = posY;
		}

		public void setSizeX(int sizeX) {
			this.sizeX = sizeX;
		}

		public void setSizeY(int sizeY) {
			this.sizeY = sizeY;
		}
	}
}
