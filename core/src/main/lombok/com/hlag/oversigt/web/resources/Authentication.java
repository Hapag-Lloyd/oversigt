package com.hlag.oversigt.web.resources;

import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.core.Response.ok;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.security.Role;
import com.hlag.oversigt.security.RoleProvider;
import com.hlag.oversigt.util.Utils;
import com.hlag.oversigt.web.api.ApiAuthenticationFilter;
import com.hlag.oversigt.web.api.ApiAuthenticationUtils;
import com.hlag.oversigt.web.api.ErrorResponse;
import com.hlag.oversigt.web.api.JwtSecured;
import com.hlag.oversigt.web.api.NoChangeLog;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Olaf Neumann
 * @see <a href=
 *      "https://stackoverflow.com/questions/26777083/best-practice-for-rest-token-based-authentication-with-jax-rs-and-jersey">https://stackoverflow.com/questions/26777083/best-practice-for-rest-token-based-authentication-with-jax-rs-and-jersey</a>
 */
@Api(tags = { "Authentication" })
@Path("/authentication")
@Singleton
public class Authentication {
	private static final Logger LOGGER = LoggerFactory.getLogger(Authentication.class);

	@Inject
	private ApiAuthenticationUtils authentication;
	@Inject
	private RoleProvider roleProvider;

	@Context
	private SecurityContext securityContext;

	private static Map<String, String> createTokenMap(String token) {
		Map<String, String> map = new HashMap<>();
		map.put("token", token);
		return map;
	}

	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/login")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "User successfully logged in", response = AuthData.class), //
			@ApiResponse(code = 403, message = "Log in failed") //
	})
	@ApiOperation("Log in a user")
	public Response authenticateUser(@FormParam("username") String username,
			@FormParam("password") @ApiParam(format = "password") String password) {
		try {
			// Authenticate the user using the credentials provided
			Principal principal = authentication.authenticate(username, password);

			// Issue a token for the user
			String token = authentication.issueToken(principal);
			Utils.logChange(principal, "logged in");

			// Return the token on the response
			return ok(new AuthData(principal.getUsername(), principal.getName(), token, findRolesForUser(principal)),
					MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			LOGGER.warn("MAYBE " + username + " - tried to authenticate", e);
			return ErrorResponse.forbidden("The user and password combination is unknown.");
		}
	}

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/renew")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "Token successfully renewed", response = AuthData.class), //
			@ApiResponse(code = 403, message = "Token renewal failed") //
	})
	@ApiOperation("Renew the authentication token")
	@NoChangeLog
	public Response renewToken(
			@HeaderParam("token") @NotBlank @ApiParam(allowEmptyValue = false, value = "The JWT to renew") String token) {
		String newToken = null;
		try {
			Principal principal = authentication.validateToken(token);
			newToken = authentication.issueToken(principal);

			return ok(new AuthData(principal.getUsername(), principal.getName(), newToken, findRolesForUser(principal)),
					MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
		}
		if (newToken != null) {
			return ok(createTokenMap(newToken)).build();
		} else {
			return Response.status(Status.FORBIDDEN).build();
		}
	}

	@GET
	@Path("/roles")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "A list of roles the current user has.", response = AuthData.class), //
	})
	@ApiOperation(value = "Get user roles", authorizations = {
			@Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@NoChangeLog
	@JwtSecured
	public Response getRoles() {
		return ok(new AuthData(findRolesForUser((Principal) securityContext.getUserPrincipal()))).build();
	}

	private Set<String> findRolesForUser(final Principal principal) {
		return roleProvider//
				.getRoles(principal.getUsername())
				.stream()
				.map(Role::getName)
				.collect(toSet());
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/check-token")
	@ApiOperation("Check a token's validity")
	@ApiResponses({
			@ApiResponse(code = 200, message = "The check was successful and the result can be found in the response body", response = boolean.class) })
	@NoChangeLog
	public boolean checkToken(
			@HeaderParam("token") @NotBlank @ApiParam(allowEmptyValue = false, value = "The JWT to check") String token) {
		try {
			authentication.validateToken(token);
			return true; //ok(true).build();
		} catch (Exception e) {
			return false; //ok(false).build();
		}
	}

	@Getter
	@ToString
	@AllArgsConstructor
	public static class AuthData {
		private String userId;
		private String displayName;
		private String token;
		@NotNull
		private Set<@NotBlank String> roles;

		AuthData(Set<String> roles) {
			this.token = null;
			this.roles = roles;
		}
	}
}