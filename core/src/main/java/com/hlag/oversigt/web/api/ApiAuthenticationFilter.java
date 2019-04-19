package com.hlag.oversigt.web.api;

import java.lang.reflect.Method;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.hlag.oversigt.security.Principal;

import io.jsonwebtoken.JwtException;

/**
 * @author Olaf Neumann see
 *         https://stackoverflow.com/questions/26777083/best-practice-for-rest-token-based-authentication-with-jax-rs-and-jersey
 *
 */
@JwtSecured
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiAuthenticationFilter implements ContainerRequestFilter {
	private static final Logger LOGGER = LoggerFactory.getLogger(ApiAuthenticationFilter.class);

	public static final String API_OPERATION_AUTHENTICATION = "JsonWebToken";

	// https://stackoverflow.com/questions/26777083/best-practice-for-rest-token-based-authentication-with-jax-rs-and-jersey
	private static final String REALM = "oversigt-api";

	private static final String AUTHENTICATION_SCHEME = "Bearer";

	@Inject
	private ApiAuthenticationUtils authentication;

	@Context
	private ResourceInfo resourceInfo;

	@Override
	public void filter(final ContainerRequestContext requestContext) {
		if (!authorize(requestContext)) {
			final Method method = resourceInfo.getResourceMethod();
			if (method.isAnnotationPresent(JwtSecured.class)
					&& method.getAnnotation(JwtSecured.class).mustBeAuthenticated()) {
				abortWithUnauthorized(requestContext);
			}
		}
	}

	private boolean authorize(final ContainerRequestContext requestContext) {
		// Get the Authorization header from the request
		final String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

		// Validate the Authorization header
		if (!isTokenBasedAuthentication(authorizationHeader)) {
			return false;
		}

		// Extract the token from the Authorization header
		final String token = authorizationHeader.substring(AUTHENTICATION_SCHEME.length()).trim();

		try {
			// Validate the token
			final Principal principal = authentication.validateToken(token);
			overrideSecurityContext(requestContext, principal);
			return true;
		} catch (final JwtException e) {
			LOGGER.warn("UNKNOWN - tried to use token: " + token, e);
			return false;
		}
	}

	private void overrideSecurityContext(final ContainerRequestContext requestContext, final Principal principal) {
		final SecurityContext currentSecurityContext = requestContext.getSecurityContext();
		requestContext.setSecurityContext(new ApiSecurityContext(currentSecurityContext, principal));
	}

	private boolean isTokenBasedAuthentication(final String authorizationHeader) {
		// Check if the Authorization header is valid
		// It must not be null and must be prefixed with "Bearer" plus a whitespace
		// The authentication scheme comparison must be case-insensitive
		return authorizationHeader != null
				&& authorizationHeader.toLowerCase().startsWith(AUTHENTICATION_SCHEME.toLowerCase() + " ");
	}

	private void abortWithUnauthorized(final ContainerRequestContext requestContext) {
		// Abort the filter chain with a 401 status code response
		// The WWW-Authenticate header is sent along with the response
		requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
				.header(HttpHeaders.WWW_AUTHENTICATE, AUTHENTICATION_SCHEME + " realm=\"" + REALM + "\"")
				.build());
	}

	private static final class ApiSecurityContext implements SecurityContext {
		private final Principal principal;

		private final SecurityContext securityContext;

		private ApiSecurityContext(final SecurityContext securityContext, final Principal principal) {
			this.principal = principal;
			this.securityContext = securityContext;
		}

		@Override
		public Principal getUserPrincipal() {
			return principal;
		}

		@Override
		public boolean isUserInRole(final String role) {
			return principal.hasRole(role);
		}

		@Override
		public boolean isSecure() {
			return securityContext.isSecure();
		}

		@Override
		public String getAuthenticationScheme() {
			return AUTHENTICATION_SCHEME;
		}
	}
}
