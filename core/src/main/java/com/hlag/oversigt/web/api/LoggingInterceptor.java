package com.hlag.oversigt.web.api;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Priority;
import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.Priorities;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.util.Utils;

@Provider
@Priority(Priorities.USER)
public class LoggingInterceptor implements ContainerRequestFilter, ContainerResponseFilter {
	private static final Logger LOGGER = LoggerFactory.getLogger(LoggingInterceptor.class);

	@Context
	private ResourceInfo resourceInfo;

	static final ThreadLocal<Object[]> PAREMETERS = new ThreadLocal<>();

	public LoggingInterceptor() {
		// no fields to be initialized manually, some will be injected
	}

	@Override
	public void filter(final ContainerRequestContext requestContext) throws IOException {
		PAREMETERS.set(null);
	}

	@Override
	public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext responseContext)
			throws IOException {
		final Method method = resourceInfo.getResourceMethod();
		if (method != null) {
			if (method.isAnnotationPresent(JwtSecured.class) && !method.isAnnotationPresent(NoChangeLog.class)) {
				final Principal principal = (Principal) requestContext.getSecurityContext().getUserPrincipal();

				if (principal != null) {
					final List<String> values = new ArrayList<>();
					final Parameter[] parameters = method.getParameters();
					for (int i = 0; i < parameters.length; i += 1) {
						if (!values.isEmpty() || isApiParameter(parameters[i])) {
							values.add(
									getApiParameterName(parameters[i]) + "=" + LoggingInterceptor.PAREMETERS.get()[i]);
						}
					}
					Utils.logChange(principal,
							"Called method %s.%s(%s)",
							method.getDeclaringClass().getSimpleName(),
							method.getName(),
							values.stream().collect(Collectors.joining(", ")));
				} else if (responseContext.getStatus() == 401) {
					// 401 means UNAUTHORIZED
					Utils.logChange("somebody",
							"Failed calling %s.%s()",
							method.getDeclaringClass().getSimpleName(),
							method.getName());
				} else {
					throw new RuntimeException("A @"
							+ JwtSecured.class.getSimpleName()
							+ " annotated class does not have a Principal in the context parameters");
				}
			} else {
				Utils.logChange("somebody", "called method: %s", method.toString());
			}
		} else {
			Utils.logWarn(LOGGER,
					"Request with method[%s] to URI[%s]",
					requestContext.getMethod(),
					requestContext.getUriInfo().getAbsolutePath());
		}

		PAREMETERS.set(null);
	}

	private static boolean isApiParameter(final Parameter parameter) {
		return parameter.isAnnotationPresent(PathParam.class)
				|| parameter.isAnnotationPresent(QueryParam.class)
				|| parameter.isAnnotationPresent(HeaderParam.class)
				|| parameter.isAnnotationPresent(CookieParam.class)
				|| parameter.isAnnotationPresent(FormParam.class)
				|| parameter.isAnnotationPresent(MatrixParam.class);
	}

	private static String getApiParameterName(final Parameter parameter) {
		if (parameter.isAnnotationPresent(PathParam.class)) {
			return parameter.getAnnotation(PathParam.class).value();
		} else if (parameter.isAnnotationPresent(QueryParam.class)) {
			return parameter.getAnnotation(QueryParam.class).value();
		} else if (parameter.isAnnotationPresent(HeaderParam.class)) {
			return parameter.getAnnotation(HeaderParam.class).value();
		} else if (parameter.isAnnotationPresent(CookieParam.class)) {
			return parameter.getAnnotation(CookieParam.class).value();
		} else if (parameter.isAnnotationPresent(FormParam.class)) {
			return parameter.getAnnotation(FormParam.class).value();
		} else if (parameter.isAnnotationPresent(MatrixParam.class)) {
			return parameter.getAnnotation(MatrixParam.class).value();
		} else {
			return "[body]";
		}
	}
}
