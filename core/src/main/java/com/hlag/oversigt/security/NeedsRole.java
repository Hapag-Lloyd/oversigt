package com.hlag.oversigt.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = { ElementType.METHOD })
@Retention(value = RetentionPolicy.RUNTIME)
@Documented
public @interface NeedsRole {
	Roles role();

	boolean dashboard() default false;
}
