package com.hlag.oversigt.core.eventsource;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

/**
 * Provide default values for data item entries of a widget
 * 
 * @author neumaol
 */
@Retention(RUNTIME)
public @interface DataItemDefault {
	/**
	 * Provides the name of data item
	 * 
	 * @return the name of the data item to set
	 */
	String name();

	/**
	 * Provides a value to be used as default
	 * 
	 * @return the default value to use
	 */
	String value();
}
