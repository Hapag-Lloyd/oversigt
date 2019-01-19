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
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.properties.SerializablePropertyController;
import com.hlag.oversigt.security.Role;
import com.hlag.oversigt.util.SneakyException;
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
import lombok.Getter;

@Api(tags = { "SerializableValue" })
@Path("/serializable-values")
public class SerializablePropertyResource {
	@Inject
	private DashboardController dController;
	@Inject
	private SerializablePropertyController spController;

	@GET
	@Path("/type")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returns a list of available types", response = SerializablePropertyDescription.class, responseContainer = "List") })
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
			@ApiResponse(code = 200, message = "Returns details of the requested serializable property", response = SerializablePropertyMember.class, responseContainer = "List"),
			@ApiResponse(code = 404, message = "The requested serializable property type does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Read serializable property details", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@NoChangeLog
	public Response readMembers(@PathParam("name") @NotBlank String className) {
		try {
			return ok(spController.getMembers(spController.getClass(className))).build();
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property '" + className + "' does not exist.");
		}
	}

	@GET
	@Path("/value/{type}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returns a list of all values of the requested serializable property", response = Map.class, responseContainer = "List") })
	@JwtSecured
	@ApiOperation(value = "Read serializable property values", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@NoChangeLog
	public Response listProperties(@PathParam("type") @NotBlank String className) {
		try {
			return ok(spController.streamProperties(spController.getClass(className))//
					.map(SerializablePropertyResource::toMapWithoutPassword)
					.collect(Collectors.toList())).build();
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property type '" + className + "' does not exist.");
		}
	}

	@POST
	@Path("/value/{type}")
	@ApiResponses({ //
			@ApiResponse(code = 201, message = "Serializable property has been created", response = Map.class),
			@ApiResponse(code = 404, message = "Serializable property type does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Create serializable property values", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response createProperty(@Context UriInfo uri,
			@PathParam("type") @NotBlank String className,
			@NotEmpty Map<@NotBlank String, @NotNull Object> map) throws MemberMissingException {
		Class<? extends SerializableProperty> clazz;
		try {
			clazz = spController.getClass(className);
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property type '" + className + "' does not exist.");
		}

		List<String> errors = checkMembers(clazz, map);
		if (!errors.isEmpty()) {
			return ErrorResponse.badRequest("Cannot create serializable property", errors);
		}

		try {
			SerializableProperty prop = spController.createProperty(clazz, (String) map.get("name"), map);
			return status(Status.CREATED)// TODO change to proper Location-header returning response
					.entity(toMapWithoutPassword(prop))
					.type(MediaType.APPLICATION_JSON_TYPE)
					.link(uri.getAbsolutePath() + "/" + prop.getId(), "self")
					.build();
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property '" + className + "' does not exist.");
		}
	}

	@GET
	@Path("/value/{type}/{id}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returns a list of all values of the requested serializable property", response = Map.class),
			@ApiResponse(code = 404, message = "Property does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Read serializable property values", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@NoChangeLog
	public Response readProperty(@PathParam("type") @NotBlank String className, @PathParam("id") @Positive int id) {
		Class<? extends SerializableProperty> clazz;
		try {
			clazz = spController.getClass(className);
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property type '" + className + "' does not exist.");
		}
		SerializableProperty prop = spController.getProperty(clazz, id);
		if (prop.getId() == id) {
			return ok(toMapWithoutPassword(prop)).build();
		} else {
			return ErrorResponse
					.notFound("Serializable property of type '" + className + "' with id " + id + " does not exist.");
		}
	}

	@PUT
	@Path("/value/{type}/{id}")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returns a list of all values of the requested serializable property", response = Map.class),
			@ApiResponse(code = 404, message = "Property does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Update serializable property values", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response updateProperty(@Context UriInfo uri,
			@PathParam("type") @NotBlank String className,
			@PathParam("id") @Positive int id,
			@NotNull Map<@NotBlank String, @NotNull Object> map) {
		Class<? extends SerializableProperty> clazz;
		try {
			clazz = spController.getClass(className);
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property type '" + className + "' does not exist.");
		}
		SerializableProperty prop = spController.getProperty(clazz, id);
		if (prop.getId() != id) {
			return ErrorResponse
					.notFound("Serializable property of type '" + className + "' with id " + id + " does not exist.");
		}

		List<String> errors = checkMembers(clazz, map);
		if (!errors.isEmpty()) {
			return ErrorResponse.badRequest("Cannot update serializable property", errors);
		}

		spController.getMembers(clazz)
				.forEach(SneakyException.sneakc(m -> m.set(prop, map.get(m.getName()).toString())));
		spController.updateProperty(prop);
		dController.restartInstancesUsingSerializableProperty(prop);

		return ok(toMapWithoutPassword(prop)).link(uri.getAbsolutePath() + "/" + prop.getId(), "self").build();
	}

	@DELETE
	@Path("/value/{type}/{id}")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "Serializable property has been deleted"),
			@ApiResponse(code = 404, message = "Serializable property does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Delete serializable property values", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public Response deleteProperty(@PathParam("type") @NotBlank String className, @PathParam("id") @Positive int id) {
		Class<? extends SerializableProperty> clazz;
		try {
			clazz = spController.getClass(className);
		} catch (NoSuchElementException e) {
			return ErrorResponse.notFound("Serializable property type '" + className + "' does not exist.");
		}
		try {
			spController.getProperty(clazz, id);
			spController.deleteProperty(clazz, id);
			return Response.ok().build();
		} catch (NoSuchElementException e) {
			return ErrorResponse
					.notFound("Serializable property of type '" + className + "' with id " + id + " does not exist.");
		}
	}

	private List<String> checkMembers(Class<? extends SerializableProperty> clazz, Map<String, Object> map) {
		List<String> errors = new ArrayList<>();
		// Are all members in the map?
		Collection<SerializablePropertyMember> members = spController.getMembers(clazz);
		for (SerializablePropertyMember m : members) {
			if (!map.containsKey(m.getName())) {
				errors.add("Member '" + m.getName() + "' is missing");
			}
		}
		// Are only members in the map?
		if (map.size() != members.size()) {
			members.stream().map(SerializablePropertyMember::getName).forEach(map::remove);
			for (String n : map.keySet()) {
				errors.add("'" + n + "' is not a member of " + clazz.getSimpleName());
			}
		}
		//		// are all values strings?
		//		map.entrySet().stream().filter(e -> !(e.getValue() instanceof String)).forEach(
		//				e -> errors.add("Value of '" + e.getKey() + "' is not a String"));
		return errors;
	}

	static Map<String, Object> toMapWithoutPassword(SerializableProperty property) {
		return removePasswords(toMemberMap(property), "");
	}

	/**Class describing a {@link SerializableProperty} class.
	 * @author Olaf Neumann
	 *
	 */
	@Builder
	@Getter
	public static class SerializablePropertyDescription {
		private final String name;
		private final String description;
	}
}