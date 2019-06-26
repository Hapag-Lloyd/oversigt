package com.hlag.oversigt.core.eventsource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.google.inject.Module;

/**
 * Marks class as event source
 *
 * @author Andrei Varabyeu
 * @author Olaf Neumann
 */
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface EventSource {
	/**
	 * Defines the display name of the event source. If this field is left out the
	 * event source's class name will be used.
	 *
	 * @return the display name of the event source
	 */
	String displayName() default "";

	String description() default "";

	/**
	 * Defines the name of the widget that will be used to render the data produced
	 * by this event source
	 *
	 * @return the name of the widget to be used
	 */
	String view();

	/**
	 * Lists data items that users may change to configure the appearance of the
	 * rendered widget
	 *
	 * @return a list of data item names
	 */
	String[] dataItems() default {};

	/**
	 * Widgets may bring a lot of data items. Sometimes it is not meaningful to show
	 * those data items to the user so with this attribute you have the possibility
	 * to hide these data items form the user.
	 *
	 * @return a list of data item names
	 */
	String[] hiddenDataItems() default {};

	/**
	 * Provide default values for data items of widgets
	 *
	 * @return a list of default values
	 */
	DataItemDefault[] dataItemDefaults() default {};

	/**
	 * Provides custom Guice configuration for event source instances of the current
	 * event source
	 *
	 * @return a {@link Module} class object describing the module to use
	 */
	Class<? extends Module> explicitConfiguration() default NOP.class;

	/**
	 * Empty Module to provide an empty Guice configuration if the event source does
	 * not need custom configuration
	 */
	interface NOP extends Module {
		// empty by design
	}
}
