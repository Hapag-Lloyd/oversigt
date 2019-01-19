package com.hlag.oversigt.web.api;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import com.google.inject.Inject;

public class ApiValidationInterceptor implements MethodInterceptor {
	@Inject
	private Validator validator;

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		// save parameters for logging
		LoggingInterceptor.parameters.set(invocation.getArguments());

		// Validate call
		Set<ConstraintViolation<?>> violations = new LinkedHashSet<>();

		violations.addAll(validator.forExecutables()
				.validateParameters(invocation.getThis(), invocation.getMethod(), invocation.getArguments()));

		Class<?>[] types = invocation.getMethod().getParameterTypes();
		for (int i = 0; i < types.length; ++i) {
			if (!(types[i].isPrimitive() || Number.class.isAssignableFrom(types[i]) || types[i] == String.class
					|| types[i] == Boolean.class)) {
				violations.addAll(validator.validate(invocation.getArguments()[i]));
			}
		}

		if (violations.isEmpty()) {
			return invocation.proceed();
		} else {
			throw new ApiValidationException(invocation.getMethod(), violations);
		}
	}
}