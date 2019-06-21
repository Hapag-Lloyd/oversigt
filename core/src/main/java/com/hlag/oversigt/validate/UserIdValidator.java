package com.hlag.oversigt.validate;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import com.google.inject.Inject;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.Principal;

import edu.umd.cs.findbugs.annotations.Nullable;

public class UserIdValidator implements ConstraintValidator<UserId, String> {

	@Inject
	private Authenticator authenticator;

	@Override
	public boolean isValid(@Nullable final String value,
			@SuppressWarnings("unused") @Nullable final ConstraintValidatorContext context) {
		if (value == null) {
			return false;
		}
		try {
			// TODO improve the way this method works. Do we need to catch all exceptions?
			return Principal.loadPrincipal(authenticator, value).isPresent();
		} catch (@SuppressWarnings("unused") final Exception ignore) {
			return false;
		}
	}

}
