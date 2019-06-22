package com.hlag.oversigt.core.eventsource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.google.inject.BindingAnnotation;

/**
 * Binds event ID to field this annotation specified on
 *
 * @author Andrey Vorobyov
 */
@Target(value = { ElementType.FIELD, ElementType.PARAMETER })
@Retention(value = RetentionPolicy.RUNTIME)
@Documented
@BindingAnnotation
public @interface EventId {/* just a marker annotation */}
