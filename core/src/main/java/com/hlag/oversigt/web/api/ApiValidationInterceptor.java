package com.hlag.oversigt.web.api;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import com.google.inject.Inject;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Interceptor that checks parameters of calls to public API methods. The javax
 * annotations will be used to determine valid values for the parameters.
 *
 * @author Olaf Neumann
 *
 */
public class ApiValidationInterceptor implements MethodInterceptor {
	@Inject
	private Validator validator;

	public ApiValidationInterceptor() {
		// no fields to be initialized manually, some will be injected
	}

	/** {@inheritDoc} */
	@Override
	public Object invoke(@Nullable final MethodInvocation nullableInvocation) throws Throwable {
		final MethodInvocation invocation = Objects.requireNonNull(nullableInvocation);

		// save parameters for logging
		LoggingInterceptor.PARAMETERS.set(invocation.getArguments());

		// Validate call
		final Set<ConstraintViolation<?>> violations = new LinkedHashSet<>();

		violations.addAll(validator.forExecutables()
				.validateParameters(invocation.getThis(), invocation.getMethod(), invocation.getArguments()));

		final Class<?>[] types = invocation.getMethod().getParameterTypes();
		for (int i = 0; i < types.length; i += 1) {
			if (!(types[i].isPrimitive()
					|| Number.class.isAssignableFrom(types[i])
					|| types[i] == String.class
					|| types[i] == Boolean.class)) {
				violations.addAll(validator.validate(invocation.getArguments()[i]));
			}
		}

		if (violations.isEmpty()) {
			return invocation.proceed();
		}
		throw new ApiValidationException(invocation.getMethod(), violations);
	}
}
