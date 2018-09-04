package com.hlag.oversigt.validate;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import com.google.inject.Inject;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.Principal;

public class UserIdValidator implements ConstraintValidator<UserId, String> {

	@Inject
	private Authenticator authenticator;

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		try {
			Principal principal = Principal.loadPrincipal(authenticator, value);
			return principal != null;
		} catch (Exception e) {
			return false;
		}
	}

}
