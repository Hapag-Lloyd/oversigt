package com.hlag.oversigt.web.resources;

import static com.hlag.oversigt.util.Utils.removePasswords;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.ok;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.core.eventsource.EventSourceStatisticsManager;
import com.hlag.oversigt.core.eventsource.EventSourceStatisticsManager.RunStatistic;
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

@Api(tags = { "EventSource" },
		authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
@Path("/event-source/instances")
@Singleton
public class EventSourceInstanceResource {
	private static final Logger LOGGER = LoggerFactory.getLogger(EventSourceInstanceResource.class);

	@Inject
	@edu.umd.cs.findbugs.annotations.Nullable
	private static JsonUtils json;

	private static JsonUtils getJson() {
		return Objects.requireNonNull(json);
	}

	@Inject
	private DashboardController controller;

	@Inject
	private EventSourceStatisticsManager statisticsManager;

	@edu.umd.cs.findbugs.annotations.Nullable
	@Context
	private UriInfo injectedUriInfo;

	@edu.umd.cs.findbugs.annotations.Nullable
	@Context
	private SecurityContext injectedSecurityContext;

	public EventSourceInstanceResource() {
		// required by checkstyle
	}

	private SecurityContext getSecurityContext() {
		return Objects.requireNonNull(injectedSecurityContext);
	}

	private UriInfo getUriInfo() {
		return Objects.requireNonNull(injectedUriInfo);
	}

	@GET
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "Returns a list of existing event source instances",
					response = EventSourceInstanceInfo.class,
					responseContainer = "List") })
	@JwtSecured
	@ApiOperation(value = "List existing event source instances")
	@NoChangeLog
	public Response listInstances(@QueryParam("containing") @ApiParam(required = false,
			value = "Filter to reduce the number of listed instances") @Nullable @edu.umd.cs.findbugs.annotations.Nullable final String containing,
			@QueryParam("limit") @ApiParam(required = false,
					value = "Maximum number of instances to be returned") @Nullable @edu.umd.cs.findbugs.annotations.Nullable final Integer limit,
			@QueryParam("onlyStartable") @ApiParam(required = false,
					value = "Only return instances that can be started") @Nullable @edu.umd.cs.findbugs.annotations.Nullable final Boolean onlyStartable) {

		Predicate<EventSourceInstance> containingFilter = i -> true;
		Predicate<EventSourceInstance> startableFilter = i -> true;

		if (!Strings.isNullOrEmpty(containing)) {
			final String searchedContent = Strings.nullToEmpty(containing).toLowerCase();
			containingFilter = EventSourceInstance.createFilter(searchedContent);
		}

		if (onlyStartable != null && onlyStartable) {
			startableFilter = i -> i.getDescriptor().getEventClass() != null;
		}

		Stream<EventSourceInstanceInfo> stream = controller.getEventSourceInstances()
				.stream()
				.filter(containingFilter)
				.filter(startableFilter)
				.map(instance -> new EventSourceInstanceInfo(controller, statisticsManager, instance));
		if (limit != null && limit > 0) {
			stream = stream.limit(limit);
		}
		final Set<EventSourceInstanceInfo> infos
				= new TreeSet<>((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
		infos.addAll(stream.collect(toList()));
		return ok(infos).build();
	}

	@POST
	@Path("/")
	@ApiResponses({
			@ApiResponse(code = 201,
					message = "Event source instance created",
					response = EventSourceInstanceDetails.class),
			@ApiResponse(code = 400,
					message = "The data provided by the client is invalid",
					response = ErrorResponse.class),
			@ApiResponse(code = 404,
					message = "There is no event source descriptor named by the given key",
					response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Create event source instance")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response createInstance(@QueryParam("key") @ApiParam(
			value = "The key of the event source descriptor to be used") @NotBlank final String keyString) {
		final EventSourceKey key;
		try {
			key = EventSourceKey.getKey(keyString);
		} catch (@SuppressWarnings("unused") final InvalidKeyException e) {
			return ErrorResponse.badRequest("The key '" + keyString + "' is invalid.");
		}

		final EventSourceDescriptor descriptor;
		try {
			descriptor = controller.getEventSourceDescriptor(key);
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("No descriptor found for key: " + keyString);
		}

		final EventSourceInstance instance
				= controller.createEventSourceInstance(descriptor, (Principal) getSecurityContext().getUserPrincipal());
		return created(URI.create(getUriInfo().getAbsolutePath() + "/" + instance.getId()))
				.entity(new EventSourceInstanceDetails(instance))
				.build();
	}

	@GET
	@Path("/{id}")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "Returns details of the requested event source instance",
					response = FullEventSourceInstanceInfo.class),
			@ApiResponse(code = 404,
					message = "The event source instance with the given id does not exist",
					response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Read event source instance")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	@NoChangeLog
	public Response readInstance(@PathParam("id") @NotBlank final String instanceId) {
		try {
			// read object
			final FullEventSourceInstanceInfo info = getInstanceInfo(instanceId);
			return ok(info).build();
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("Event source instance '" + instanceId + "' does not exist.");
		}
	}

	@GET
	@Path("/{id}/usage")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "Returns the usage of the event source instance",
					response = DashboardInfo.class,
					responseContainer = "List")
			// , @ApiResponse(code = 404, message = "The event source instance with the
			// given id does not exist", response = ErrorResponse.class)
	})
	@JwtSecured
	@ApiOperation(value = "Read event source instance usage")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_EDITOR)
	@NoChangeLog
	public Response readInstanceUsage(@PathParam("id") @NotBlank final String instanceId) {
		try {
			return ok(controller.getEventSourceInstanceUsage(instanceId)
					.stream()
					.map(controller::getDashboard)
					.map(Optional::get)
					.map(DashboardInfo::new)
					.collect(toList())).build();
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("Event source instance '" + instanceId + "' does not exist.");
		}
	}

	@PUT
	@Path("/{id}")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "The event source instance has been updated",
					response = FullEventSourceInstanceInfo.class),
			@ApiResponse(code = 400, message = "The data is invalid", response = ErrorResponse.class),
			@ApiResponse(code = 404,
					message = "The event source instance with the given id does not exist",
					response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Update event source instance")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response updateInstance(@PathParam("id") @NotBlank final String instanceId,
			final EventSourceInstanceDetails details) {
		final EventSourceInstance instance;
		try {
			instance = controller.getEventSourceInstance(instanceId);
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("Event source instance does not exist.");
		}

		if (!instance.getId().equals(details.getId()) || !details.getId().equals(instanceId)) {
			return ErrorResponse.badRequest("The ID does not match");
		}
		if (!instance.getDescriptor().getKey().getKey().equals(details.getEventSourceDescriptor())) {
			return ErrorResponse.badRequest("The event source descriptor key does not match");
		}
		// TODO this needs to be checked when the UI is fixed
		// if (!instance.getDescriptor().isScheduledService()&& details.frequency !=
		// Duration.ZERO) {
		// return ErrorResponse.badRequest("The event source does not take a
		// frequency");
		// }
		final Set<String> unnessaccaryDataItems = new HashSet<>(details.dataItems.keySet());
		unnessaccaryDataItems.removeAll(instance.getDescriptor()
				.getDataItems()
				.stream()
				.map(EventSourceProperty::getName)
				.collect(Collectors.toSet()));
		if (!unnessaccaryDataItems.isEmpty()) {
			return ErrorResponse.badRequest("Some data items are unknown", unnessaccaryDataItems);
		}

		final EventSourceInstance newInstance;
		try {
			newInstance = new EventSourceInstance(instance.getDescriptor(),
					instance.getId(),
					details.getName(),
					details.isEnabled(),
					details.getFrequency(),
					instance.getCreatedBy(),
					((Principal) getSecurityContext().getUserPrincipal()).getUsername());
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
		} catch (final Exception e) {
			LOGGER.error("Unable to update event source instance", e);
			return ErrorResponse.badRequest("Invalid event source instance values", e);
		}

		controller.updateEventSourceInstance(newInstance);
		return ok(getInstanceInfo(instanceId)).build();
	}

	@DELETE
	@Path("/{id}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "The event source instance has been deleted"),
			@ApiResponse(code = 404,
					message = "The event source instance with the given id does not exist",
					response = ErrorResponse.class),
			@ApiResponse(code = 422,
					message = "There widgets using this event source instance and the force parameter has not been set to true",
					response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Delete event source instance")
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response deleteInstance(@PathParam("id") @NotBlank final String instanceId,
			@QueryParam("force") @ApiParam(required = false,
					defaultValue = "false",
					value = "true to also remove all widgets using this event source") final boolean force) {
		try {
			final Set<String> dashboardsPreventingDeletion = controller.deleteEventSourceInstance(instanceId, force);
			if (dashboardsPreventingDeletion.isEmpty()) {
				return ok().build();
			}
			return ErrorResponse.unprocessableEntity("Unable to delete event source instance",
					dashboardsPreventingDeletion);
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("Event source instance does not exist.");
		}
	}

	FullEventSourceInstanceInfo getInstanceInfo(final String instanceId) {
		final EventSourceInstance instance = controller.getEventSourceInstance(instanceId);
		return new FullEventSourceInstanceInfo(new EventSourceInstanceDetails(instance),
				EventSourceInstanceState.fromInstance(controller, statisticsManager, instance));
	}

	static Map<String, String> getValueMap(final Stream<EventSourceProperty> propertyStream,
			final Function<EventSourceProperty, String> getValue,
			final Predicate<EventSourceProperty> hasValue,
			final boolean removeEmpty) {
		final Map<String, String> map = propertyStream.filter(p -> !(removeEmpty && !hasValue.test(p)))
				.collect(Collectors.toMap(EventSourceProperty::getName, p -> {
					String string = getValue.apply(p);
					if (p.isJson()) {
						string = getJson().removePasswordsFromJson(string);
					}
					return string;
				}));
		return removePasswords(map, "");
	}

	private static Map<String, String> getPropertyMap(final EventSourceInstance instance) {
		return getValueMap(instance.getDescriptor().getProperties().stream(),
				instance::getPropertyValueString,
				instance::hasPropertyValue,
				false);
	}

	private static Map<String, String> getDataItemMap(final EventSourceInstance instance) {
		return getValueMap(instance.getDescriptor().getDataItems().stream(),
				instance::getPropertyValueString,
				instance::hasPropertyValue,
				true);
	}

	public static class EventSourceInstanceInfo {
		@NotBlank
		private String id = "";

		@NotBlank
		private String name = "";

		private boolean isService = false;

		private boolean enabled = false;

		private boolean running = false;

		private boolean hasError = false;

		@NotNull
		private List<@NotNull DashboardInfo> usedBy = new ArrayList<>();

		public EventSourceInstanceInfo() {
			// nothing to initialize
		}

		public EventSourceInstanceInfo(final DashboardController controller,
				final EventSourceStatisticsManager statisticsManager,
				final EventSourceInstance instance) {
			id = instance.getId();
			name = instance.getName();
			isService = instance.getDescriptor().getServiceClass().isPresent();
			enabled = instance.isEnabled();
			running = controller.isRunning(instance);
			hasError = statisticsManager.getEventSourceStatistics(instance.getId())
					.getLastRun()
					.flatMap(RunStatistic::getThrowable)
					.map(x -> true)
					.orElse(false);
			usedBy = new ArrayList<>(
					controller.getDashboardsWhereEventSourceIsUsed(instance).map(DashboardInfo::new).collect(toList()));
		}

		public String getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public boolean isService() {
			return isService;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public boolean isRunning() {
			return running;
		}

		public boolean isHasError() {
			return hasError;
		}

		public List<DashboardInfo> getUsedBy() {
			return usedBy;
		}
	}

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

		public EventSourceInstanceDetails() {
			eventSourceDescriptor = "";
			id = "";
			name = "";
			enabled = false;
			frequency = Duration.ZERO;
			properties = new HashMap<>();
			dataItems = new HashMap<>();
		}

		public EventSourceInstanceDetails(@NotBlank final String eventSourceDescriptor,
				@NotBlank final String id,
				@NotBlank final String name,
				final boolean enabled,
				final Duration frequency,
				@NotNull final Map<@NotBlank String, @NotNull String> properties,
				@NotNull final Map<@NotBlank String, @NotNull String> dataItems) {
			this.eventSourceDescriptor = eventSourceDescriptor;
			this.id = id;
			this.name = name;
			this.enabled = enabled;
			this.frequency = frequency;
			this.properties = properties;
			this.dataItems = dataItems;
		}

		public EventSourceInstanceDetails(final EventSourceInstance instance) {
			this(instance.getDescriptor().getKey().getKey(),
					instance.getId(),
					instance.getName(),
					instance.isEnabled(),
					instance.getFrequency(),
					getPropertyMap(instance),
					getDataItemMap(instance));
		}

		public String getEventSourceDescriptor() {
			return eventSourceDescriptor;
		}

		public String getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public Duration getFrequency() {
			return frequency;
		}

		public Map<String, String> getProperties() {
			return properties;
		}

		public Map<String, String> getDataItems() {
			return dataItems;
		}
	}

	public static class FullEventSourceInstanceInfo {
		private final EventSourceInstanceDetails instanceDetails;

		private final EventSourceInstanceState instanceState;

		public FullEventSourceInstanceInfo(final EventSourceInstanceDetails instanceDetails,
				final EventSourceInstanceState instanceState) {
			this.instanceDetails = instanceDetails;
			this.instanceState = instanceState;
		}

		public EventSourceInstanceDetails getInstanceDetails() {
			return instanceDetails;
		}

		public EventSourceInstanceState getInstanceState() {
			return instanceState;
		}
	}
}
