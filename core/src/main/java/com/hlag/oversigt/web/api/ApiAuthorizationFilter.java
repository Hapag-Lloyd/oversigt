package com.hlag.oversigt.web.api;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Priority;
import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHORIZATION)
@JwtSecured
public class ApiAuthorizationFilter implements ContainerRequestFilter {
	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

	@Context
	private UriInfo uriInfo;

	@Context
	private ResourceInfo resourceInfo;

	@Override
	public void filter(final ContainerRequestContext requestContext) throws IOException {
		final Method method = resourceInfo.getResourceMethod();

		// @DenyAll on the method takes precedence over @RolesAllowed and @PermitAll
		if (method.isAnnotationPresent(DenyAll.class)) {
			refuseRequest(requestContext);
		}

		// @RolesAllowed on the method takes precedence over @PermitAll
		RolesAllowed rolesAllowed = method.getAnnotation(RolesAllowed.class);
		if (rolesAllowed != null) {
			performAuthorization(requestContext, rolesAllowed.value());
			return;
		}

		// @PermitAll on the method takes precedence over @RolesAllowed on the class
		if (method.isAnnotationPresent(PermitAll.class)) {
			// Do nothing
			return;
		}

		// @DenyAll can't be attached to classes

		// @RolesAllowed on the class takes precedence over @PermitAll on the class
		rolesAllowed = resourceInfo.getResourceClass().getAnnotation(RolesAllowed.class);
		if (rolesAllowed != null) {
			performAuthorization(requestContext, rolesAllowed.value());
		}

		// @PermitAll on the class
		if (resourceInfo.getResourceClass().isAnnotationPresent(PermitAll.class)) {
			// Do nothing
			return;
		}

		// Authentication is required for non-annotated methods
		if (!isAuthenticated(requestContext)) {
			refuseRequest(requestContext);
		}
	}

	/**
	 * Perform authorization based on roles.
	 */
	private void performAuthorization(final ContainerRequestContext requestContext, final String[] rolesAllowed) {
		if (rolesAllowed.length > 0 && !isAuthenticated(requestContext)) {
			refuseRequest(requestContext);
		}

		for (final String role : rolesAllowed) {
			if (requestContext.getSecurityContext().isUserInRole(enhanceRole(requestContext, role))) {
				return;
			}
		}

		refuseRequest(requestContext);
	}

	/**
	 * Check if the user is authenticated.
	 */
	private boolean isAuthenticated(final ContainerRequestContext requestContext) {
		return requestContext.getSecurityContext().getUserPrincipal() != null;
	}

	/**
	 * Refuse the request.
	 */
	private void refuseRequest(final ContainerRequestContext requestContext) {
		requestContext.abortWith(ErrorResponse.forbidden("You don't have permissions to perform this action."));
	}

	private String enhanceRole(final ContainerRequestContext requestContext, final String requiredRole) {
		String role = requiredRole;
		for (final String placeholder : getPlaceholders(role)) {
			final String newValue = getParameter(requestContext, placeholder);
			role = role.replace("{" + placeholder + "}", newValue);
		}
		return role;
	}

	private String getParameter(final ContainerRequestContext requestContext, final String parameterName) {
		Optional<String> param = Optional.empty();

		// path params
		param = Optional.ofNullable(uriInfo.getPathParameters().get(parameterName)).map(l -> l.get(0));
		// param=Optional.of(context.get)

		// query params
		if (!param.isPresent()) {
			param = Optional.ofNullable(uriInfo.getQueryParameters().get(parameterName)).map(l -> l.get(0));
		}

		// header params
		if (!param.isPresent()) {
			param = Optional.ofNullable(requestContext.getHeaders().get(parameterName)).map(l -> l.get(0));
		}

		// Cookie params
		if (!param.isPresent()) {
			param = Optional.ofNullable(requestContext.getCookies().get(parameterName)).map(Cookie::getValue);
		}

		// form params

		// matrix params

		return param.orElseThrow(() -> new RuntimeException("The parameter " + parameterName + " is not present."));
	}

	private static Set<String> getPlaceholders(final String roleString) {
		final Set<String> placeholders = new HashSet<>();
		final Matcher matcher = PLACEHOLDER_PATTERN.matcher(roleString);
		while (matcher.find()) {
			placeholders.add(matcher.group(1));
		}
		return placeholders;
	}
}
