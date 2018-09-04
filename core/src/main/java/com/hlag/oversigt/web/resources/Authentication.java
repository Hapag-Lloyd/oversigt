package com.hlag.oversigt.web.resources;

import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;

import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.NotBlank;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.util.Utils;
import com.hlag.oversigt.web.api.ApiAuthenticationUtils;
import com.hlag.oversigt.web.api.NoChangeLog;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @author Olaf Neumann
 * @see https://stackoverflow.com/questions/26777083/best-practice-for-rest-token-based-authentication-with-jax-rs-and-jersey
 *
 */
@Api(tags = { "Authentication" })
@Path("/authentication")
public class Authentication {
	private static final Logger LOGGER = LoggerFactory.getLogger(Authentication.class);

	@Inject
	private ApiAuthenticationUtils authentication;

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
			@ApiResponse(code = 200, message = "User successfully logged in"), //
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
			return ok(createTokenMap(token), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			LOGGER.warn("MAYBE " + username + " - tried to authenticate", e);
			return status(Response.Status.FORBIDDEN).build();
		}
	}

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/renew")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "Token successfully renewed"), //
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
		} catch (Exception e) {
		}
		if (newToken != null) {
			return ok(createTokenMap(newToken)).build();
		} else {
			return Response.status(Status.FORBIDDEN).build();
		}
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
}