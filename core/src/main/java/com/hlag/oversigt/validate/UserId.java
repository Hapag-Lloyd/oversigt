package com.hlag.oversigt.validate;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.validation.Constraint;
import javax.validation.Payload;

/**
 * Defines a constraint to an annotated String that the content of the String
 * must be a valid username in the current applications context.
 *
 * @author neumaol
 */
@Target({ METHOD, FIELD, ANNOTATION_TYPE, TYPE_USE })
@Retention(RUNTIME)
@Constraint(validatedBy = UserIdValidator.class)
@Documented
public @interface UserId {
	String message() default "must be a valid user ID";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
