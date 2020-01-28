package com.hlag.oversigt.web.api;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletContext;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;

import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.util.FindAnnotation;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.matcher.Matchers;
import com.hlag.oversigt.util.TypeUtils;
import com.hlag.oversigt.web.resources.Authentication;

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

public class ApiBootstrapListener extends GuiceResteasyBootstrapServletContextListener {
	private static final Pattern CHECK_METHOD_WITH_PATH_PATTERN = Pattern.compile("\\{[^\\}]*?\\}");

	public ApiBootstrapListener() {
		// no fields to be initialized
	}

	/** {@inheritDoc} */
	@Override
	protected List<? extends Module> getModules(@SuppressWarnings("unused") @Nullable final ServletContext context) {
		return Arrays.asList(new ApiModule());
	}

	/** {@inheritDoc} */
	@Override
	protected void withInjector(@Nullable final Injector injector) {
		processInjector(Objects.requireNonNull(injector), this::checkClassMethods);
	}

	private void checkClassMethods(final Class<?> clazz) {
		if (clazz.isAnnotationPresent(Path.class)) {
			checkMethodWithPath(clazz.getAnnotation(Path.class).value(), "Class " + clazz.getName() + " ");
		}
		for (final Method method : clazz.getDeclaredMethods()) {
			if (hasAnnotations(FindAnnotation.getResourcesAnnotations(
					method), GET.class, PUT.class, POST.class, DELETE.class, HEAD.class, OPTIONS.class)) {
				checkMethod(method);
			}
			if (method.isAnnotationPresent(Path.class)) {
				checkMethodWithPath(method.getAnnotation(Path.class).value(), "Method " + method + " ");
			}
		}
	}

	private void checkMethodWithPath(final String originalPath, final String message) {
		final String path = CHECK_METHOD_WITH_PATH_PATTERN.matcher(originalPath).replaceAll("");
		Arrays.asList(CaseFormat.values()).forEach(cf -> checkCaseFormatOnMethodWithPath(path, cf, message));
	}

	private void checkCaseFormatOnMethodWithPath(final String before, final CaseFormat from, final String message) {
		final String after = from.converterTo(CaseFormat.LOWER_HYPHEN).convert(before);
		if (!before.equals(after)) {
			error(message
					+ "does not follow the path guide line. The Path must be in lower-hypen format: "
					+ after
					+ " instead of "
					+ before);
		}
	}

	private void checkMethod(final Method method) {
		final ApiOperation operation = method.getAnnotation(ApiOperation.class);
		Objects.requireNonNull(operation, message(method, "must use @ApiOperation", false));
		verify(!Strings.isNullOrEmpty(operation.value()),
				method,
				"must expose a short description using @ApiOperation.value()",
				false);

		final ApiResponses responses = method.getAnnotation(ApiResponses.class);
		Objects.requireNonNull(responses, message(method, "must declare response codes using @ApiResponses", false));
		verify(responses.value().length > 0,
				method,
				"must declare at least one response code using @ApiResponses and @ApiResponse",
				false);

		if (method.isAnnotationPresent(RolesAllowed.class)) {
			Objects.requireNonNull(method.getAnnotation(JwtSecured.class),
					message(method,
							"must declare @"
									+ JwtSecured.class.getSimpleName()
									+ " as it has @"
									+ RolesAllowed.class.getSimpleName()
									+ " declared.",
							false));
		}

		if (method.getDeclaringClass().isAnnotationPresent(RolesAllowed.class)
				&& !method.isAnnotationPresent(PermitAll.class)) {
			Objects.requireNonNull(method.getAnnotation(JwtSecured.class),
					message(method,
							"must declare @"
									+ JwtSecured.class.getSimpleName()
									+ " as it has @"
									+ RolesAllowed.class.getSimpleName()
									+ " declared.",
							false));
		}

		if (method.isAnnotationPresent(JwtSecured.class)) {
			checkSecuredMethod(method);
		}
	}

	private void checkSecuredMethod(final Method method) {
		final ApiOperation operation = method.getAnnotation(ApiOperation.class);
		final String message = "or its declaring class must declare exactly one authorization in @ApiOperation: "
				+ ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION;
		Authorization[] authorizations = operation.authorizations();
		final Authorization[] classAuthorizations
				= Optional.ofNullable(method.getDeclaringClass().getAnnotation(io.swagger.annotations.Api.class))
						.map(io.swagger.annotations.Api::authorizations)
						.orElse(null);
		if (authorizations == null || Strings.isNullOrEmpty(authorizations[0].value())) {
			authorizations = classAuthorizations;
		} else if (classAuthorizations != null
				&& classAuthorizations.length == 1
				&& ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION.equals(classAuthorizations[0].value())) {
					error(method, "contains the same authorization like its declaring class. Remove one.", true);
				}
		Objects.requireNonNull(authorizations, message(method, message, true));
		verify(authorizations.length == 1
				&& ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION.equals(authorizations[0].value()),
				method,
				message,
				true);
	}

	private static String message(final Method method, final String message, final boolean secured) {
		return "The "
				+ (secured ? "@" + JwtSecured.class.getSimpleName() + " annotated" : "")
				+ " method "
				+ method.getName()
				+ " in class "
				+ method.getDeclaringClass().getName()
				+ " "
				+ message;
	}

	private static void error(final Method method, final String message, final boolean secured) {
		error(message(method, message, secured));
	}

	private static void error(final String message) {
		throw new ApiBootstrapException(message);
	}

	private static void verify(final boolean check, final Method method, final String message, final boolean secured) {
		if (!check) {
			error(method, message, secured);
		}
	}

	@SafeVarargs
	private static boolean hasAnnotations(final Annotation[] annotations,
			final Class<? extends Annotation>... classes) {
		for (final Class<?> clazz : classes) {
			if (FindAnnotation.findAnnotation(annotations, clazz) != null) {
				return true;
			}
		}
		return false;
	}

	private void processInjector(final Injector injector, final Consumer<Class<?>> consumer) {
		for (final Key<?> key : injector.getBindings().keySet()) {
			final Type type = key.getTypeLiteral().getRawType();
			if (type instanceof Class) {
				consumer.accept((Class<?>) type);
			}
		}
	}

	@SuppressFBWarnings(value = "IMC_IMMATURE_CLASS_BAD_SERIALVERSIONUID", justification = "false positive")
	private static final class ApiBootstrapException extends RuntimeException {
		private static final long serialVersionUID = 7618319300693716653L;

		private ApiBootstrapException(final String message) {
			super(message);
		}
	}

	private static final class ApiModule extends AbstractModule {
		private ApiModule() {
			// no fields to be initialized
		}

		@Override
		protected void configure() {
			final List<Class<? extends Annotation>> annotations
					= Arrays.asList(io.swagger.annotations.Api.class, javax.ws.rs.ext.Provider.class);
			final Predicate<Class<?>> predicate
					= clazz -> annotations.stream().filter(clazz::isAnnotationPresent).findAny().isPresent();
			// TODO Create possibility to register more packages to scan
			TypeUtils.bindClasses(Api.class.getPackage(), predicate, binder());
			TypeUtils.bindClasses(Authentication.class.getPackage(), predicate, binder());

			final ApiValidationInterceptor interceptor = new ApiValidationInterceptor();
			binder().requestInjection(interceptor);
			binder().bindInterceptor(Matchers.annotatedWith(Path.class),
					Matchers.annotatedWith(GET.class)
							.or(Matchers.annotatedWith(POST.class))
							.or(Matchers.annotatedWith(PUT.class))
							.or(Matchers.annotatedWith(PATCH.class))
							.or(Matchers.annotatedWith(DELETE.class))
							.or(Matchers.annotatedWith(HEAD.class))
							.or(Matchers.annotatedWith(OPTIONS.class)),
					interceptor);
		}
	}
}
