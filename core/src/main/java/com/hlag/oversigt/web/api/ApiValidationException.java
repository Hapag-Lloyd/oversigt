package com.hlag.oversigt.web.api;

import java.lang.reflect.Method;
import java.util.Set;

import javax.validation.ConstraintViolation;

public class ApiValidationException extends Exception {
	private static final long serialVersionUID = 2371275922151051261L;

	private final Set<? extends ConstraintViolation<?>> violations;

	public ApiValidationException(final Method method, final Set<? extends ConstraintViolation<?>> violations) {
		super("Constraint violation while calling method: " + method.toString());
		this.violations = violations;
	}

	public Set<? extends ConstraintViolation<?>> getViolations() {
		return violations;
	}
}
