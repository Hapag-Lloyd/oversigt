package com.hlag.oversigt.web.resources;

import static com.hlag.oversigt.util.TypeUtils.toMemberMap;
import static com.hlag.oversigt.util.Utils.removePasswords;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.properties.SerializablePropertyController;
import com.hlag.oversigt.security.Role;
import com.hlag.oversigt.util.ThrowingConsumer;
import com.hlag.oversigt.util.TypeUtils.SerializablePropertyMember;
import com.hlag.oversigt.util.TypeUtils.SerializablePropertyMember.MemberMissingException;
import com.hlag.oversigt.web.api.ApiAuthenticationFilter;
import com.hlag.oversigt.web.api.ErrorResponse;
import com.hlag.oversigt.web.api.JwtSecured;
import com.hlag.oversigt.web.api.NoChangeLog;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import lombok.Builder;

@Api(tags = { "SerializableValue" })
@Path("/serializable-values")
@Singleton
public class SerializablePropertyResource {
	@Inject
	private DashboardController dController;

	@Inject
	private SerializablePropertyController spController;

	public SerializablePropertyResource() {
		// no fields to be initialized manually, some will be injected
	}

	@GET
	@Path("/type")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "Returns a list of available types",
					response = SerializablePropertyDescription.class,
					responseContainer = "List") })
	@ApiOperation(value = "List available property types")
	@NoChangeLog
	public List<SerializablePropertyDescription> listPropertyTypes() {
		return spController.getClasses()
				.stream()
				.map(c -> SerializablePropertyDescription.builder()
						.name(c.getSimpleName())
						.description(spController.getDescription(c.getSimpleName()))
						.build())
				.collect(Collectors.toList());
	}

	@GET
	@Path("/type/{name}")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "Returns details of the requested serializable property",
					response = SerializablePropertyMember.class,
					responseContainer = "List"),
			@ApiResponse(code = 404,
					message = "The requested serializable property type does not exist",
					response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Read serializable property details",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@NoChangeLog
	public Response readMembers(@PathParam("name") @NotBlank final String className) {
		try {
			return ok(spController.getMembers(spController.getClass(className))).build();
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property '" + className + "' does not exist.");
		}
	}

	@GET
	@Path("/value/{type}")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "Returns a list of all values of the requested serializable property",
					response = Map.class,
					responseContainer = "List") })
	@JwtSecured
	@ApiOperation(value = "Read serializable property values",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@NoChangeLog
	public Response listProperties(@PathParam("type") @NotBlank final String className) {
		try {
			return ok(spController.streamProperties(spController.getClass(className))
					.map(SerializablePropertyResource::toMapWithoutPassword)
					.collect(Collectors.toList())).build();
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property type '" + className + "' does not exist.");
		}
	}

	@POST
	@Path("/value/{type}")
	@ApiResponses({
			@ApiResponse(code = 201, message = "Serializable property has been created", response = Map.class),
			@ApiResponse(code = 404,
					message = "Serializable property type does not exist",
					response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Create serializable property values",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response createProperty(@Context final UriInfo uri,
			@PathParam("type") @NotBlank final String className,
			@NotEmpty final Map<@NotBlank String, @NotNull Object> map) throws MemberMissingException {
		final Class<? extends SerializableProperty> clazz;
		try {
			clazz = spController.getClass(className);
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property type '" + className + "' does not exist.");
		}

		final List<String> errors = checkMembers(clazz, map);
		if (!errors.isEmpty()) {
			return ErrorResponse.badRequest("Cannot create serializable property", errors);
		}

		try {
			final SerializableProperty prop = spController.createProperty(clazz, (String) map.get("name"), map);
			// TODO change to proper Location-header returning response
			return status(Status.CREATED).entity(toMapWithoutPassword(prop))
					.type(MediaType.APPLICATION_JSON_TYPE)
					.link(uri.getAbsolutePath() + "/" + prop.getId(), "self")
					.build();
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property '" + className + "' does not exist.");
		}
	}

	@GET
	@Path("/value/{type}/{id}")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "Returns a list of all values of the requested serializable property",
					response = Map.class),
			@ApiResponse(code = 404, message = "Property does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Read serializable property values",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@NoChangeLog
	public Response readProperty(@PathParam("type") @NotBlank final String className,
			@PathParam("id") @Positive final int id) {
		final Class<? extends SerializableProperty> clazz;
		try {
			clazz = spController.getClass(className);
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property type '" + className + "' does not exist.");
		}
		final SerializableProperty prop = spController.getProperty(clazz, id);
		if (prop.getId() == id) {
			return ok(toMapWithoutPassword(prop)).build();
		}
		return ErrorResponse
				.notFound("Serializable property of type '" + className + "' with id " + id + " does not exist.");
	}

	@PUT
	@Path("/value/{type}/{id}")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "Returns a list of all values of the requested serializable property",
					response = Map.class),
			@ApiResponse(code = 404, message = "Property does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Update serializable property values",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response updateProperty(@Context final UriInfo uri,
			@PathParam("type") @NotBlank final String className,
			@PathParam("id") @Positive final int id,
			@NotNull final Map<@NotBlank String, @NotNull Object> map) {
		final Class<? extends SerializableProperty> clazz;
		try {
			clazz = spController.getClass(className);
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property type '" + className + "' does not exist.");
		}
		final SerializableProperty prop = spController.getProperty(clazz, id);
		if (prop.getId() != id) {
			return ErrorResponse
					.notFound("Serializable property of type '" + className + "' with id " + id + " does not exist.");
		}

		final List<String> errors = checkMembers(clazz, map);
		if (!errors.isEmpty()) {
			return ErrorResponse.badRequest("Cannot update serializable property", errors);
		}

		spController.getMembers(clazz)
				.forEach(ThrowingConsumer.sneakc(m -> m.set(prop, map.get(m.getName()).toString())));
		spController.updateProperty(prop);
		dController.restartInstancesUsingSerializableProperty(prop);

		return ok(toMapWithoutPassword(prop)).link(uri.getAbsolutePath() + "/" + prop.getId(), "self").build();
	}

	@DELETE
	@Path("/value/{type}/{id}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Serializable property has been deleted"),
			@ApiResponse(code = 404,
					message = "Serializable property does not exist",
					response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Delete serializable property values",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response deleteProperty(@PathParam("type") @NotBlank final String className,
			@PathParam("id") @Positive final int id) {
		final Class<? extends SerializableProperty> clazz;
		try {
			clazz = spController.getClass(className);
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property type '" + className + "' does not exist.");
		}
		try {
			spController.getProperty(clazz, id);
			spController.deleteProperty(clazz, id);
			return Response.ok().build();
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			return ErrorResponse
					.notFound("Serializable property of type '" + className + "' with id " + id + " does not exist.");
		}
	}

	private List<String> checkMembers(final Class<? extends SerializableProperty> clazz,
			final Map<String, Object> map) {
		final List<String> errors = new ArrayList<>();
		// Are all members in the map?
		final Collection<SerializablePropertyMember> members = spController.getMembers(clazz);
		for (final SerializablePropertyMember m : members) {
			if (!map.containsKey(m.getName())) {
				errors.add("Member '" + m.getName() + "' is missing");
			}
		}
		// Are only members in the map?
		if (map.size() != members.size()) {
			members.stream().map(SerializablePropertyMember::getName).forEach(map::remove);
			for (final String n : map.keySet()) {
				errors.add("'" + n + "' is not a member of " + clazz.getSimpleName());
			}
		}
		return errors;
	}

	static Map<String, Object> toMapWithoutPassword(final SerializableProperty property) {
		return removePasswords(toMemberMap(property), "");
	}

	/**
	 * Class describing a {@link SerializableProperty} class.
	 *
	 * @author Olaf Neumann
	 */
	@Builder
	public static class SerializablePropertyDescription {
		private final String name;

		private final String description;

		public String getName() {
			return name;
		}

		public String getDescription() {
			return description;
		}
	}
}
