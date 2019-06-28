package com.hlag.oversigt.validate;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import com.google.inject.Inject;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.Principal;

public class UserIdValidator implements ConstraintValidator<UserId, String> {

	@Inject
	private Authenticator authenticator;

	public UserIdValidator() {
		// empty by design
	}

	@Override
	public boolean isValid(final String value, final ConstraintValidatorContext context) {
		try {
			final Principal principal = Principal.loadPrincipal(authenticator, value);
			return principal != null;
		} catch (final Exception e) {
			return false;
		}
	}

}
