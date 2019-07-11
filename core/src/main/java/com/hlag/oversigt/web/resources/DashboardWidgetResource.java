package com.hlag.oversigt.web.resources;

import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.ok;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.model.EventSourceProperty;
import com.hlag.oversigt.model.Widget;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.web.api.ApiAuthenticationFilter;
import com.hlag.oversigt.web.api.ErrorResponse;
import com.hlag.oversigt.web.api.JwtSecured;
import com.hlag.oversigt.web.api.NoChangeLog;
import com.hlag.oversigt.web.resources.EventSourceInstanceResource.FullEventSourceInstanceInfo;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

@Api(tags = { "Dashboard-Widget" })
@Path("/dashboards/{dashboardId}/widgets")
@Singleton
public class DashboardWidgetResource {
	@Inject
	private DashboardController controller;

	@Inject
	private EventSourceInstanceResource eventSourceInstanceResource;

	@Nullable
	@Context
	private UriInfo injectedUriInfo;

	private UriInfo getUriInfo() {
		return Objects.requireNonNull(injectedUriInfo);
	}

	public DashboardWidgetResource() {
		// no fields to be initialized manually, some will be injected
	}

	@GET
	@Path("/")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "Returning a list of all widgets",
					response = WidgetShortInfo.class,
					responseContainer = "List"),
			@ApiResponse(code = 404, message = "The dashboard does not exist") })
	@JwtSecured(mustBeAuthenticated = false)
	@ApiOperation(value = "List widgets of a dashboard",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@PermitAll
	@NoChangeLog
	public Response listWidgets(@Context final SecurityContext secu,
			@PathParam("dashboardId") @NotNull final String dashboardId,
			@QueryParam("containing") @ApiParam(required = false,
					value = "Only show widgets containing this text") final String containing) {
		final Optional<Dashboard> dashboard = controller.getDashboard(dashboardId);
		if (!dashboard.isPresent()) {
			return ErrorResponse.notFound("The dashboard does not exist");
		}

		final Predicate<Widget> filterName;
		if (Strings.isNullOrEmpty(containing)) {
			filterName = w -> true;
		} else {
			final String lower = containing.trim().toLowerCase();
			filterName = w -> w.getName().toLowerCase().contains(lower)
					|| w.getTitle().toLowerCase().contains(lower)
					|| w.getBackgroundColor().getHexColor().toLowerCase().contains(lower)
					|| w.getStyle().toLowerCase().contains(lower)
					|| w.getType().toLowerCase().contains(lower)
					|| w.getView().toLowerCase().contains(lower)
					|| w.getEventSourceInstance()
							.getDescriptor()
							.getDataItems()
							.stream()
							.filter(w::hasWidgetData)
							.map(w::getWidgetData)
							.map(String::toLowerCase)
							.anyMatch(s -> s.contains(lower));
		}

		final Predicate<Widget> filterSecu;
		if (secu.getUserPrincipal() != null) {
			filterSecu = w -> true;
		} else {
			filterSecu = Widget::isEnabled;
		}

		return ok(dashboard.get()
				.getWidgets()
				.stream()
				.filter(filterName)
				.filter(filterSecu)
				.map(WidgetShortInfo::new)
				.toArray()).build();
	}

	@POST
	@Path("/")
	@ApiResponses({
			@ApiResponse(code = 201, message = "Widget created", response = WidgetDetails.class),
			@ApiResponse(code = 400,
					message = "The data provided by the client is invalid",
					response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Required data does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Create widget",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed("dashboard.{dashboardId}.editor")
	public Response createWidget(@PathParam("dashboardId") @NotNull final String dashboardId,
			@QueryParam("eventSource") @ApiParam(required = true) @NotBlank final String eventSourceInstanceId) {
		final Optional<Dashboard> dashboard = controller.getDashboard(dashboardId);
		if (!dashboard.isPresent()) {
			return ErrorResponse.notFound("The dashboard does not exist");
		}

		final Widget widget;
		try {
			widget = controller.createWidgetForDashboard(dashboard.get(), eventSourceInstanceId);
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("The event source instance does not exist");
		}

		return created(URI.create(getUriInfo().getAbsolutePath() + "/" + widget.getId()))
				.entity(new WidgetDetails(widget, false))
				.build();
	}

	@GET
	@Path("/{id}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returning a list of all widgets", response = WidgetDetails.class),
			@ApiResponse(code = 404, message = "The data does not exist", response = ErrorResponse.class) })
	@JwtSecured(mustBeAuthenticated = false)
	@ApiOperation(value = "Read widget",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@PermitAll
	@NoChangeLog
	public Response readWidget(@PathParam("dashboardId") @NotNull final String dashboardId,
			@PathParam("id") @Positive final int widgetId,
			@QueryParam("showAllProperties") @DefaultValue("true") @ApiParam(defaultValue = "true",
					value = "false to show only properties defined for this specific widget. true to additionally show all properties inherited from the underlaying event source.") final boolean all) {
		final Optional<Dashboard> dashboard = controller.getDashboard(dashboardId);
		if (!dashboard.isPresent()) {
			return ErrorResponse.notFound("The dashboard does not exist");
		}

		try {
			return ok(new WidgetDetails(dashboard.get().getWidget(widgetId), all)).build();
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("The widget does not exist in this dashboard");
		}
	}

	@GET
	@Path("/{id}/event-source-instance")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "The event source instance information for the specified widget",
					response = FullEventSourceInstanceInfo.class),
			@ApiResponse(code = 404, message = "Data does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Read event source instance for widget",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed("dashboard.{dashboardId}.editor")
	public Response readEventSourceInstanceForWidget(@PathParam("dashboardId") @NotNull final String dashboardId,
			@PathParam("id") @Positive final int widgetId) {
		final Optional<Dashboard> dashboard = controller.getDashboard(dashboardId);
		final Widget widget = dashboard.get().getWidget(widgetId);
		final FullEventSourceInstanceInfo info
				= eventSourceInstanceResource.getInstanceInfo(widget.getEventSourceInstance().getId());
		return ok(info).build();
	}

	@PUT
	@Path("/{id}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Widget has been updated", response = WidgetDetails.class),
			@ApiResponse(code = 404, message = "Data does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Update widget",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed("dashboard.{dashboardId}.editor")
	public Response updateWidget(@PathParam("dashboardId") @NotNull final String dashboardId,
			@PathParam("id") @Positive final int widgetId,
			@NotNull final WidgetDetails details) {

		final Optional<Dashboard> dashboard = controller.getDashboard(dashboardId);
		if (!dashboard.isPresent()) {
			return ErrorResponse.notFound("The dashboard does not exist");
		}

		final Widget widget;
		try {
			widget = dashboard.get().getWidget(widgetId);
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("The widget does not exist");
		}

		if (widget.getId() != details.getId() || details.getId() != widgetId) {
			return ErrorResponse.badRequest("The ID does not match");
		}
		if (!widget.getEventSourceInstance().getId().equals(details.getEventSourceInstanceId())) {
			return ErrorResponse.badRequest("The event source instance id does not match");
		}
		final Set<String> unnessaccaryDataItems = new HashSet<>(details.getData().keySet());
		unnessaccaryDataItems.removeAll(widget.getEventSourceInstance()
				.getDescriptor()
				.getDataItems()
				.stream()
				.map(EventSourceProperty::getName)
				.collect(Collectors.toSet()));
		if (!unnessaccaryDataItems.isEmpty()) {
			return ErrorResponse.badRequest("Some data items are unknown", unnessaccaryDataItems);
		}

		final Widget newWidget;
		try {
			newWidget = new Widget(details.getId(),
					widget.getEventSourceInstance(),
					details.getTitle(),
					details.getName(),
					details.isEnabled(),
					details.getPosX(),
					details.getPosY(),
					details.getSizeX(),
					details.getSizeY(),
					details.getBackgroundColor(),
					details.getStyle());
			newWidget.getEventSourceInstance().getDescriptor().getDataItems().forEach(p -> {
				if (details.getData().containsKey(p.getName())) {
					newWidget.setWidgetData(p, details.getData().get(p.getName()));
				} else {
					newWidget.removeWidgetData(p);
				}
			});
		} catch (final Exception e) {
			return ErrorResponse.badRequest("Invalid event source instance values", e);
		}

		controller.updateWidget(newWidget);
		return ok(new WidgetDetails(newWidget, false)).build();
	}

	@DELETE
	@Path("/{id}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "The widget has been deleted"),
			@ApiResponse(code = 404, message = "The widget does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Delete widget",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed("dashboard.{dashboardId}.editor")
	public Response deleteWidget(@PathParam("dashboardId") @NotNull final String dashboardId,
			@PathParam("id") @Positive final int widgetId) {
		final Optional<Dashboard> dashboard = controller.getDashboard(dashboardId);
		if (!dashboard.isPresent()) {
			return ErrorResponse.notFound("The dashboard does not exist");
		}

		final Widget widget;
		try {
			widget = dashboard.get().getWidget(widgetId);
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("The widget does not exist");
		}

		controller.deleteWidget(widget);
		return ok().build();
	}

	public static class WidgetShortInfo {
		public WidgetShortInfo(final Widget widget) {
			this(widget.getId(),
					widget.getName(),
					widget.getView(),
					widget.isEnabled(),
					widget.getPosX(),
					widget.getPosY(),
					widget.getSizeX(),
					widget.getSizeY());
		}

		public WidgetShortInfo(@NotNull @Positive final int id,
				@NotBlank @NotNull final String name,
				final String view,
				final boolean enabled,
				@NotNull @PositiveOrZero final int posX,
				@NotNull @PositiveOrZero final int posY,
				@NotNull @Min(1) @Positive final int sizeX,
				@NotNull @Min(1) @Positive final int sizeY) {
			super();
			this.id = id;
			this.name = name;
			this.view = view;
			this.enabled = enabled;
			this.posX = posX;
			this.posY = posY;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
		}

		@NotNull
		@Positive
		private final int id;

		@NotBlank
		@NotNull
		private final String name;

		private final String view;

		private final boolean enabled;

		@NotNull
		@PositiveOrZero
		private final int posX;

		@NotNull
		@PositiveOrZero
		private final int posY;

		@NotNull
		@Min(1)
		@Positive
		private final int sizeX;

		@NotNull
		@Min(1)
		@Positive
		private final int sizeY;

		/**
		 * @return the id
		 */
		public int getId() {
			return id;
		}

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * @return the view
		 */
		public String getView() {
			return view;
		}

		/**
		 * @return the enabled
		 */
		public boolean isEnabled() {
			return enabled;
		}

		/**
		 * @return the posX
		 */
		public int getPosX() {
			return posX;
		}

		/**
		 * @return the posY
		 */
		public int getPosY() {
			return posY;
		}

		/**
		 * @return the sizeX
		 */
		public int getSizeX() {
			return sizeX;
		}

		/**
		 * @return the sizeY
		 */
		public int getSizeY() {
			return sizeY;
		}
	}

	public static class WidgetDetails {
		@NotNull
		@Positive
		private int id = 0;

		@NotBlank
		@NotNull
		private String eventSourceInstanceId = "";

		@NotNull
		private String type = "";

		@NotBlank
		@NotNull
		private String title = "";

		@NotBlank
		@NotNull
		private String name = "";

		@NotNull
		@NotBlank
		private String view = "";

		@NotNull
		private boolean enabled = false;

		@NotNull
		@PositiveOrZero
		private int posX = 0;

		@NotNull
		@PositiveOrZero
		private int posY = 0;

		@NotNull
		@Min(1)
		@Positive
		private int sizeX = 1;

		@NotNull
		@Min(1)
		@Positive
		private int sizeY = 1;

		@NotNull
		private Color backgroundColor = Color.BLACK;

		@NotNull
		private String style = "";

		@NotNull
		private Map<@NotBlank String, @NotBlank String> data = new HashMap<>();

		WidgetDetails() {}

		WidgetDetails(final Widget widget, final boolean showAllDatas) {
			this(widget.getId(),
					widget.getEventSourceInstance().getId(),
					widget.getType(),
					widget.getTitle(),
					widget.getName(),
					widget.getView(),
					widget.isEnabled(),
					widget.getPosX(),
					widget.getPosY(),
					widget.getSizeX(),
					widget.getSizeY(),
					widget.getBackgroundColor(),
					widget.getStyle(),
					EventSourceInstanceResource.getValueMap(
							widget.getEventSourceInstance().getDescriptor().getDataItems().stream(),
							showAllDatas ? widget::getWidgetDataForDashboard : widget::getWidgetData,
							showAllDatas ? widget::hasWidgetDataForDashboard : widget::hasWidgetData,
							true));
		}

		WidgetDetails(final int id,
				final String eventSourceInstanceId,
				final String type,
				final String title,
				final String name,
				final String view,
				final boolean enabled,
				final int posX,
				final int posY,
				final int sizeX,
				final int sizeY,
				final Color backgroundColor,
				final String style,
				final Map<String, String> data) {
			this.id = id;
			this.eventSourceInstanceId = eventSourceInstanceId;
			this.type = type;
			this.title = title;
			this.name = name;
			this.view = view;
			this.enabled = enabled;
			this.posX = posX;
			this.posY = posY;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			this.backgroundColor = backgroundColor;
			this.style = style;
			this.data = new LinkedHashMap<>(data);
		}

		/**
		 * @return the id
		 */
		public int getId() {
			return id;
		}

		/**
		 * @return the eventSourceInstanceId
		 */
		public String getEventSourceInstanceId() {
			return eventSourceInstanceId;
		}

		/**
		 * @return the type
		 */
		public String getType() {
			return type;
		}

		/**
		 * @return the title
		 */
		public String getTitle() {
			return title;
		}

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * @return the view
		 */
		public String getView() {
			return view;
		}

		/**
		 * @return the enabled
		 */
		public boolean isEnabled() {
			return enabled;
		}

		/**
		 * @return the posX
		 */
		public int getPosX() {
			return posX;
		}

		/**
		 * @return the posY
		 */
		public int getPosY() {
			return posY;
		}

		/**
		 * @return the sizeX
		 */
		public int getSizeX() {
			return sizeX;
		}

		/**
		 * @return the sizeY
		 */
		public int getSizeY() {
			return sizeY;
		}

		/**
		 * @return the backgroundColor
		 */
		public Color getBackgroundColor() {
			return backgroundColor;
		}

		/**
		 * @return the style
		 */
		public String getStyle() {
			return style;
		}

		/**
		 * @return the data
		 */
		public Map<String, String> getData() {
			return data;
		}
	}
}
