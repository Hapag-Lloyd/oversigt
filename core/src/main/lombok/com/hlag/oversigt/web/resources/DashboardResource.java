package com.hlag.oversigt.web.resources;

import static com.hlag.oversigt.web.api.ErrorResponse.badRequest;
import static com.hlag.oversigt.web.api.ErrorResponse.forbidden;
import static com.hlag.oversigt.web.api.ErrorResponse.internalServerError;
import static com.hlag.oversigt.web.api.ErrorResponse.notFound;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.ok;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.model.Widget;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.security.Role;
import com.hlag.oversigt.util.TypeUtils;
import com.hlag.oversigt.validate.UserId;
import com.hlag.oversigt.web.api.ApiAuthenticationFilter;
import com.hlag.oversigt.web.api.ErrorResponse;
import com.hlag.oversigt.web.api.JwtSecured;
import com.hlag.oversigt.web.api.NoChangeLog;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import lombok.Builder;
import lombok.Getter;

@Api(tags = { "Dashboard" })
@Path("/dashboards")
@Singleton
public class DashboardResource {
	private static final Logger LOGGER = LoggerFactory.getLogger(DashboardResource.class);

	@Inject
	private DashboardController dashboardController;

	@Inject
	private Authenticator authenticator;

	@Nullable
	@Context
	private UriInfo injectedUriInfo;

	private UriInfo getUriInfo() {
		return Objects.requireNonNull(injectedUriInfo);
	}

	public DashboardResource() {
		// no fields to be initialized manually, some will be injected
	}

	@GET
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "Returning a list of all dashboards",
					response = DashboardInfo.class,
					responseContainer = "List") })
	@ApiOperation(value = "List existing dashboards ids")
	@NoChangeLog
	public Response listDashboardIds() {
		return ok(dashboardController.getDashboardIds()
				.stream()
				.map(dashboardController::getDashboard)
				.map(Optional::get)
				.map(DashboardInfo::fromDashboard)
				.collect(toList())).build();
	}

	@PUT
	@ApiResponses({
			@ApiResponse(code = 201,
					message = "A new dashboard has been created. The body contains a detailed representation of the newly created dashboard",
					response = Dashboard.class),
			@ApiResponse(code = 303,
					message = "The dashboard already exists. Sending redirect to dashboard information.") })
	@JwtSecured
	@ApiOperation(value = "Create new dashboard",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	public Response createDashboard(@Context final SecurityContext securityContext,
			@QueryParam("dashboardId") @NotNull final String id,
			@QueryParam("owner") @NotNull @UserId final String ownerUserId,
			@QueryParam("enabled") @ApiParam(defaultValue = "false") final boolean enabled) {
		final Optional<Dashboard> existingDashboard = dashboardController.getDashboard(id);
		if (existingDashboard.isPresent()) {
			return Response.seeOther(URI.create(getUriInfo().getAbsolutePath() + "/" + id)).build();
		}

		final Optional<Dashboard> createdDashboard = dashboardController.createDashboard(id,
				Principal.loadPrincipal(authenticator, ownerUserId)
						.orElseThrow(() -> new RuntimeException("Unknown principal for: " + ownerUserId)),
				enabled && securityContext.isUserInRole(Role.ROLE_NAME_SERVER_ADMIN));
		if (!createdDashboard.isPresent()) {
			final UUID uuid = UUID.randomUUID();
			LOGGER.error("Dashboard {} does not exist, but creation failed, too.", id);
			return internalServerError(uuid, "Unable to create dashboard. Details are written to log file.");
		}
		return created(URI.create(getUriInfo().getAbsolutePath() + "/" + createdDashboard.get().getId()))
				.entity(createdDashboard.get())
				.type(MediaType.APPLICATION_JSON_TYPE)
				.build();
	}

	@GET
	@Path("/{dashboardId}")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "A detailed representation of the requested dashboard",
					response = Dashboard.class),
			@ApiResponse(code = 404,
					message = "The requested dashboard does not exist",
					response = ErrorResponse.class) })
	@JwtSecured(mustBeAuthenticated = false)
	@ApiOperation(value = "Read dashboard details",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@NoChangeLog
	@PermitAll
	public Response readDashboard(@Context final SecurityContext secu, @PathParam("dashboardId") final String id) {
		final Optional<Dashboard> dashboard = dashboardController.getDashboard(id);
		if (!dashboard.isPresent()) {
			return notFound("A dashboard with id '" + id + "' does not exist.");
		}

		final Map<String, Object> dashboardMap = dashboard.map(TypeUtils::toMemberMap)
				.orElseThrow(() -> new RuntimeException("The dashboard is not present."));
		if (secu.getUserPrincipal() == null) {
			dashboardMap.remove("owner");
			dashboardMap.remove("editors");
		}
		return ok(dashboardMap).build();
	}

	@PATCH
	@Path("/{dashboardId}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "The dashboard has been updated", response = Dashboard.class),
			@ApiResponse(code = 400, message = "The provided information are invalid", response = ErrorResponse.class),
			@ApiResponse(code = 403,
					message = "The user is either not permitted to edit this dashboard or he wants to perform a change that he is not allowed to",
					response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "The dashboard does not exist") })
	@ApiOperation(value = "Partially update dashboard details",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@JwtSecured
	@RolesAllowed("dashboard.{dashboardId}.editor")
	public Response updateDashboardPartially(@Context final SecurityContext securityContext,
			@PathParam("dashboardId") @NotNull final String id,
			final Map<String, Object> newDashboardData) {
		// load current dashboard from storage
		final Optional<Dashboard> dashboard = dashboardController.getDashboard(id);
		if (!dashboard.isPresent()) {
			return notFound("A dashboard with id '" + id + "' does not exist.");
		}

		// clone it to work on it
		final Dashboard newDashboard = dashboard.get().copy();

		// check dashboard id
		if (newDashboardData.containsKey("id")
				&& (!id.equals(newDashboardData.get("id")) || !id.equals(newDashboard.getId()))) {
			return badRequest("The dashboard ID does not match");
		}

		// check if the change is allowed
		final List<String> allowedChangeElements = Arrays.asList("id", "enabled");
		if (newDashboardData.keySet().stream().anyMatch(key -> !allowedChangeElements.contains(key))) {
			return badRequest("Currently the only allowed attribute for change is: 'enabled'");
		}

		// Check if the user changed the enabled state of the dashboard. Only server
		// admins may perform that action
		if (newDashboardData.containsKey("enabled")
				&& Boolean.parseBoolean(newDashboardData.get("enabled").toString()) != newDashboard.isEnabled()
				&& !securityContext.isUserInRole(Role.ROLE_NAME_SERVER_ADMIN)) {
			return forbidden(
					"Only server admins are allowed to change a dashboard's enabled state. Please contact a server admin.");
		}

		// // if the owner has changed the user needs to be at least dashboard owner
		// if (!newDashboardData.getOwners().equals(dashboard.getOwners())
		// &&
		// !securityContext.isUserInRole(Role.getDashboardOwnerRole(dashboard.getId()).getName()))
		// {
		// return forbidden("To change the owner of a dashboard you need to be at least
		// the owner of the dashboard.");
		// }

		// update dashboard data
		if (newDashboardData.containsKey("enabled")) {
			newDashboard.setEnabled(Boolean.parseBoolean(newDashboardData.get("enabled").toString()));
		}
		if (!dashboardController.updateDashboard(newDashboard)) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		return ok(dashboardController.getDashboard(id)).build();
	}

	@PUT
	@Path("/{dashboardId}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "The dashboard has been updated", response = Dashboard.class),
			@ApiResponse(code = 400, message = "The provided information are invalid", response = ErrorResponse.class),
			@ApiResponse(code = 403,
					message = "The user is either not permitted to edit this dashboard or he wants to perform a change that he is not allowed to",
					response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "The dashboard does not exist") })
	@ApiOperation(value = "Update dashboard details",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@JwtSecured
	@RolesAllowed("dashboard.{dashboardId}.editor")
	public Response updateDashboard(@Context final SecurityContext securityContext,
			@PathParam("dashboardId") @NotNull final String id,
			final Dashboard newDashboardData) {
		// load current dashboard from storage
		final Optional<Dashboard> maybeOriginalDashboard = dashboardController.getDashboard(id);

		// Checks
		// ======

		// does it exist?
		if (!maybeOriginalDashboard.isPresent()) {
			return notFound("A dashboard with id '" + id + "' does not exist.");
		}
		final Dashboard originalDashboard = maybeOriginalDashboard.get();

		// check dashboard id
		if (!id.equals(newDashboardData.getId()) || !id.equals(originalDashboard.getId())) {
			return badRequest("The dashboard ID does not match");
		}

		// Check if the user changed the enabled state of the dashboard. Only server
		// admins may perform that action
		if (newDashboardData.isEnabled() != originalDashboard.isEnabled()
				&& !securityContext.isUserInRole(Role.ROLE_NAME_SERVER_ADMIN)) {
			return forbidden(
					"Only server admins are allowed to change a dashboard's enabled state. Please contact a server admin.");
		}

		// if the owner has changed the user needs to be at least dashboard owner
		if (!newDashboardData.getOwners().equals(originalDashboard.getOwners())
				&& !securityContext.isUserInRole(Role.getDashboardOwnerRole(originalDashboard.getId()).getName())) {
			return forbidden("To change the owner of a dashboard you need to be at least the owner of the dashboard.");
		}

		// check usernames
		newDashboardData.setOwners(
				newDashboardData.getOwners().stream().filter(authenticator::isUsernameValid).collect(toList()));
		newDashboardData.setEditors(
				newDashboardData.getEditors().stream().filter(authenticator::isUsernameValid).collect(toList()));
		// TODO notify changed users

		// Prepare changes
		// ===============
		final Set<String> users
				= Stream.concat(originalDashboard.getOwners().stream(), originalDashboard.getEditors().stream())
						.collect(toSet());

		// update dashboard data
		// =====================
		if (!dashboardController.updateDashboard(newDashboardData)) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		// return dashboard info
		// =====================
		final Optional<Dashboard> changedDashboard = dashboardController.getDashboard(id);

		// reload user rights
		users.addAll(
				Stream.concat(changedDashboard.get().getOwners().stream(), changedDashboard.get().getEditors().stream())
						.collect(toSet()));
		users.forEach(authenticator::reloadRoles);

		return ok(changedDashboard).build();
	}

	@PUT
	@Path("/{dashboardId}/positions")
	@ApiResponses({
			@ApiResponse(code = 200, message = "The widget positions has been updated"),
			@ApiResponse(code = 400, message = "The provided information are invalid", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "The dashboard does not exist") })
	@ApiOperation(value = "Update dashboard widget positions",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@JwtSecured
	@RolesAllowed("dashboard.{dashboardId}.editor")
	public Response updateWidgetPositions(@PathParam("dashboardId") @NotNull final String id,
			final List<WidgetPosition> widgetPositions) {
		// load current dashboard from storage
		final Optional<Dashboard> dashboard = dashboardController.getDashboard(id);

		// does it exist?
		if (!dashboard.isPresent()) {
			return notFound("Dashboard does not exist.");
		}

		// Do all given widget positions match a dashboard widget?
		final Set<String> unmatchedWidgetIds = widgetPositions.stream()
				.mapToInt(WidgetPosition::getWidgetId)
				.filter(i -> !dashboard.get().getWidgets().stream().mapToInt(Widget::getId).anyMatch(w -> i == w))
				.mapToObj(Integer::toString)
				.collect(Collectors.toSet());
		if (!unmatchedWidgetIds.isEmpty()) {
			return badRequest("Some widgets ids do not exist in the dashboard", unmatchedWidgetIds);
		}

		// Update widget positions
		widgetPositions.forEach(t -> {
			final Widget widget = dashboard.get().getWidget(t.getWidgetId());
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
	@ApiResponses({
			@ApiResponse(code = 200, message = "The dashboard has been deleted"),
			@ApiResponse(code = 404, message = "The dashboard does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Delete dashboard",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_SERVER_ADMIN)
	public Response deleteDashboard(@PathParam("dashboardId") final String id) {
		if (!dashboardController.deleteDashboard(id)) {
			return notFound("A dashboard with id '" + id + "' does not exist.");
		}
		return ok().build();
	}

	@Getter
	@Builder
	public static class DashboardInfo {
		public static DashboardInfo fromDashboard(final Dashboard dashboard) {
			return builder().id(dashboard.getId()).title(dashboard.getTitle()).build();
		}

		public static DashboardInfo fromDashboard(final Optional<Dashboard> dashboard) {
			return fromDashboard(dashboard.get());
		}

		@NotNull
		@NotEmpty
		@NotBlank
		private String id;

		@NotNull
		@NotEmpty
		@NotBlank
		private String title;
	}

	public static class WidgetPosition {
		public WidgetPosition() {
			// no fields to be initialized
		}

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

		public void setWidgetId(final int widgetId) {
			this.widgetId = widgetId;
		}

		public void setPosX(final int posX) {
			this.posX = posX;
		}

		public void setPosY(final int posY) {
			this.posY = posY;
		}

		public void setSizeX(final int sizeX) {
			this.sizeX = sizeX;
		}

		public void setSizeY(final int sizeY) {
			this.sizeY = sizeY;
		}
	}
}
