package com.hlag.oversigt.web.resources;

import static com.hlag.oversigt.util.Utils.removePasswords;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.ok;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
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
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.model.EventSourceDescriptor;
import com.hlag.oversigt.model.EventSourceInstance;
import com.hlag.oversigt.model.EventSourceKey;
import com.hlag.oversigt.model.EventSourceProperty;
import com.hlag.oversigt.model.InvalidKeyException;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.security.Role;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.web.api.ApiAuthenticationFilter;
import com.hlag.oversigt.web.api.ErrorResponse;
import com.hlag.oversigt.web.api.JwtSecured;
import com.hlag.oversigt.web.api.NoChangeLog;
import com.hlag.oversigt.web.resources.DashboardResource.DashboardInfo;
import com.hlag.oversigt.web.resources.EventSourceStatusResource.EventSourceInstanceState;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Api(tags = { "EventSource" }, authorizations = {
		@Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
@Path("/event-source/instances")
public class EventSourceInstanceResource {
	private static JsonUtils json = null;
	@Inject
	private DashboardController controller;

	@Context
	private UriInfo uri;
	@Context
	private SecurityContext securityContext;

	@Inject
	public EventSourceInstanceResource(JsonUtils jsonUtils) {
		json = jsonUtils;
	}

	@GET
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returns a list of existing event source instances", response = EventSourceInstanceInfo.class, responseContainer = "List") })
	@JwtSecured
	@ApiOperation(value = "List existing event source instances")
	@NoChangeLog
	public Response listInstances(
			@QueryParam("containing") @ApiParam(required = false, value = "Filter to reduce the number of listed instances") @Nullable String containing,
			@QueryParam("limit") @ApiParam(required = false, value = "Maximum number of instances to be returned") @Nullable Integer limit,
			@QueryParam("onlyStartable") @ApiParam(required = false, value = "Only return instances that can be started") @Nullable Boolean onlyStartable) {

		Predicate<EventSourceInstance> containingFilter = i -> true;
		Predicate<EventSourceInstance> startableFilter = i -> true;

		if (!Strings.isNullOrEmpty(containing)) {
			final String searchedContent = Strings.nullToEmpty(containing).toLowerCase();
			containingFilter = EventSourceInstance.createFilter(searchedContent);
		}

		if (onlyStartable != null && onlyStartable) {
			startableFilter = i -> i.getDescriptor().getEventClass() != null;
		}

		Stream<EventSourceInstanceInfo> stream = controller//
				.getEventSourceInstances()
				.stream()
				.filter(containingFilter)
				.filter(startableFilter)
				.map(instance -> new EventSourceInstanceInfo(instance, controller));
		if (limit != null && limit > 0) {
			stream = stream.limit(limit);
		}
		return ok(stream.collect(toList())).build();
	}

	@POST
	@Path("/")
	@ApiResponses({
			@ApiResponse(code = 201, message = "Event source instance created", response = EventSourceInstanceDetails.class),
			@ApiResponse(code = 400, message = "The data provided by the client is invalid", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "There is no event source descriptor named by the given key", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Create event source instance")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response createInstance(
			@QueryParam("key") @ApiParam(value = "The key of the event source descriptor to be used") @NotBlank String keyString) {
		EventSourceKey key;
		try {
			key = EventSourceKey.getKey(keyString);
		} catch (InvalidKeyException e) {
			return ErrorResponse.badRequest("The key '" + keyString + "' is invalid.");
		}

		EventSourceDescriptor descriptor;
		try {
			descriptor = controller.getEventSourceDescriptor(key);
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("No descriptor found for key: " + keyString);
		}

		EventSourceInstance instance = controller.createEventSourceInstance(descriptor,
				(Principal) securityContext.getUserPrincipal());
		return created(URI.create(uri.getAbsolutePath() + "/" + instance.getId()))
				.entity(new EventSourceInstanceDetails(instance))
				.build();
	}

	@GET
	@Path("/{id}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returns details of the requested event source instance", response = FullEventSourceInstanceInfo.class),
			@ApiResponse(code = 404, message = "The event source instance with the given id does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Read event source instance")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	@NoChangeLog
	public Response readInstance(@PathParam("id") @NotBlank String instanceId) {
		try {
			return ok(getInstanceInfo(instanceId)).build();
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("Event source instance '" + instanceId + "' does not exist.");
		}
	}

	@GET
	@Path("/{id}/usage")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returns the usage of the event source instance", response = DashboardInfo.class, responseContainer = "List")
			//, @ApiResponse(code = 404, message = "The event source instance with the given id does not exist", response = ErrorResponse.class)
	})
	@JwtSecured
	@ApiOperation(value = "Read event source instance usage")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	@NoChangeLog
	public Response readInstanceUsage(@PathParam("id") @NotBlank String instanceId) {
		try {
			return ok(controller.getEventSourceInstanceUsage(instanceId)
					.stream()
					.map(controller::getDashboard)
					.map(DashboardInfo::fromDashboard)
					.collect(toList())).build();
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("Event source instance '" + instanceId + "' does not exist.");
		}
	}

	@PUT
	@Path("/{id}")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "The event source instance has been updated", response = FullEventSourceInstanceInfo.class),
			@ApiResponse(code = 400, message = "The data is invalid", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "The event source instance with the given id does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Update event source instance")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response updateInstance(@PathParam("id") @NotBlank String instanceId, EventSourceInstanceDetails details) {
		EventSourceInstance instance;
		try {
			instance = controller.getEventSourceInstance(instanceId);
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("Event source instance does not exist.");
		}

		if (!instance.getId().equals(details.getId()) || !details.getId().equals(instanceId)) {
			return ErrorResponse.badRequest("The ID does not match");
		}
		if (!instance.getDescriptor().getKey().getKey().equals(details.getEventSourceDescriptor())) {
			return ErrorResponse.badRequest("The event source descriptor key does not match");
		}
		if (!instance.getDescriptor().isScheduledService() && details.frequency != null) {
			return ErrorResponse.badRequest("The event source does not take a frequency");
		}
		HashSet<String> unnessaccaryDataItems = new HashSet<>(details.dataItems.keySet());
		unnessaccaryDataItems.removeAll(instance.getDescriptor()//
				.getDataItems()
				.stream()
				.map(EventSourceProperty::getName)
				.collect(Collectors.toSet()));
		if (!unnessaccaryDataItems.isEmpty()) {
			return ErrorResponse.badRequest("Some data items are unknown", unnessaccaryDataItems);
		}

		EventSourceInstance newInstance;
		try {
			newInstance = new EventSourceInstance(instance.getDescriptor(),
					instance.getId(),
					details.getName(),
					details.isEnabled(),
					details.getFrequency(),
					instance.getCreatedBy(),
					((Principal) securityContext.getUserPrincipal()).getUsername());
			// TODO how to handle passwords?
			newInstance.setEnabled(details.isEnabled());
			newInstance.setName(details.getName());
			newInstance.setFrequency(details.getFrequency());
			newInstance.getDescriptor()
					.getProperties()
					.forEach(p -> newInstance.setPropertyString(p, details.properties.get(p.getName())));
			newInstance.getDescriptor().getDataItems().forEach(p -> {
				if (details.dataItems.containsKey(p.getName())) {
					newInstance.setPropertyString(p, details.dataItems.get(p.getName()));
				} else {
					newInstance.removeProperty(p);
				}
			});
		} catch (Exception e) {
			return ErrorResponse.badRequest("Invalid event source instance values", e);
		}

		controller.updateEventSourceInstance(newInstance);
		return ok(getInstanceInfo(instanceId)).build();
	}

	@DELETE
	@Path("/{id}")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "The event source instance has been deleted"),
			@ApiResponse(code = 404, message = "The event source instance with the given id does not exist", response = ErrorResponse.class),
			@ApiResponse(code = 422, message = "There widgets using this event source instance and the force parameter has not been set to true", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Delete event source instance")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response deleteInstance(@PathParam("id") @NotBlank String instanceId,
			@QueryParam("force") @ApiParam(required = false, defaultValue = "false", value = "true to also remove all widgets using this event source") boolean force) {
		try {
			Set<String> dashboards = controller.deleteEventSourceInstance(instanceId, force);
			if (dashboards == null) {
				return ok().build();
			} else {
				return ErrorResponse.unprocessableEntity("Unable to delete event source instance", dashboards);
			}
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("Event source instance does not exist.");
		}
	}

	private FullEventSourceInstanceInfo getInstanceInfo(String instanceId) {
		EventSourceInstance instance = controller.getEventSourceInstance(instanceId);
		return new FullEventSourceInstanceInfo(new EventSourceInstanceDetails(instance),
				EventSourceInstanceState.fromInstance(controller, instance));
	}

	static Map<String, String> getValueMap(Stream<EventSourceProperty> propertyStream,
			Function<EventSourceProperty, String> getValue,
			Predicate<EventSourceProperty> hasValue,
			boolean removeEmpty) {
		return removePasswords(propertyStream//
				.filter(p -> !(removeEmpty && !hasValue.test(p)))
				.collect(Collectors.toMap(EventSourceProperty::getName, p -> {
					String string = getValue.apply(p);
					if (p.isJson()) {
						string = json.removePasswordsFromJson(string);
					}
					return string;
				})), "");
	}

	private static Map<String, String> getPropertyMap(EventSourceInstance instance) {
		return getValueMap(instance.getDescriptor().getProperties().stream(),
				instance::getPropertyValueString,
				instance::hasPropertyValue,
				false);
	}

	private static Map<String, String> getDataItemMap(EventSourceInstance instance) {
		return getValueMap(instance.getDescriptor().getDataItems().stream(),
				instance::getPropertyValueString,
				instance::hasPropertyValue,
				true);
	}

	@Getter
	@NoArgsConstructor
	public static class EventSourceInstanceInfo {
		@NotBlank
		private String id;
		@NotBlank
		private String name;
		private boolean isService;
		private boolean running;
		private boolean hasError;
		@NotNull
		private List<@NotNull DashboardInfo> usedBy;

		public EventSourceInstanceInfo(EventSourceInstance instance, DashboardController controller) {
			this.id = instance.getId();
			this.name = instance.getName();
			this.isService = instance.getDescriptor().getServiceClass() != null;
			this.running = controller.isRunning(instance);
			this.hasError = controller.hasException(instance);
			this.usedBy = new ArrayList<>(controller//
					.getDashboardsWhereEventSourceIsUsed(instance)
					.map(DashboardInfo::fromDashboard)
					.collect(toList()));
		}
	}

	@Getter
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	public static class EventSourceInstanceDetails {
		@NotBlank
		private String eventSourceDescriptor;
		@NotBlank
		private String id;
		@NotBlank
		private String name;
		private boolean enabled;
		private Duration frequency;
		@NotNull
		private Map<@NotBlank String, @NotNull String> properties;
		@NotNull
		private Map<@NotBlank String, @NotNull String> dataItems;

		public EventSourceInstanceDetails(EventSourceInstance instance) {
			this(
				instance.getDescriptor().getKey().getKey(),
				instance.getId(),
				instance.getName(),
				instance.isEnabled(),
				instance.getFrequency(),
				getPropertyMap(instance),
				getDataItemMap(instance)/*,
										instance.getCreatedBy(),
										instance.getLastChangeBy()*/);
		}
	}

	@Getter
	@AllArgsConstructor
	@ToString
	public static class FullEventSourceInstanceInfo {
		private EventSourceInstanceDetails instanceDetails;
		private EventSourceInstanceState instanceState;
	}
}