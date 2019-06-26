package com.hlag.oversigt.core.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark an event class with this annotation to prevent the event from being
 * cached
 *
 * @author Olaf Neumann
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NoCache {
	// empty by design
}
