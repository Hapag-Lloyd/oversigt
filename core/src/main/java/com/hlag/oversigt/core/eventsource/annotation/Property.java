package com.hlag.oversigt.core.eventsource.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks properties if event sources and determines how they should be handled
 *
 * @author Olaf Neumann
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = { ElementType.METHOD })
@Documented
public @interface Property {

	/**
	 * The name of the property. If this annotation is not used the camel case name of the getter method is used.
	 *
	 * @return the name of the property
	 */
	String name();

	/**
	 * A short description of the property. This is a text presented to the end user.
	 *
	 * @return a description of the property.
	 */
	String description()

	default "";

	/**
	 * The input type of the property. This is used for the type attribute of input fields.
	 * Leave empty for auto detection based on the properties type.
	 *
	 * @return input type of the property.
	 */
	String type()

	default "";

	/**
	 * Determines whether this property should by serialized using JSON.
	 *
	 * @return <code>true</code> if the property should by serialized using JSON, otherwise <code>false</code>
	 */
	boolean json()

	default false;

	/**Determines whether you need to restart the event source if you changed this property
	 * @return <code>true</code> if you need to restart the event source if you changed this property
	 */
	boolean needsRestart() default true;

	/**Determines the allowed values for the current property. If the list is empty any value is valid
		 * @return a list of allowed values
		 */
	String[] allowedValues() default {};
}
