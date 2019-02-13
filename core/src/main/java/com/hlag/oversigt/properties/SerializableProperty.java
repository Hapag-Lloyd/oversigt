package com.hlag.oversigt.properties;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * This interface marks classes that will be stored in the database in a separate tables.
 *
 * @author neumaol
 *
 */
public abstract class SerializableProperty implements Comparable<SerializableProperty> {

	private final int id;
	private String name;

	protected SerializableProperty(int id, String name) {
		this.id = id;
		this.name = name;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public int compareTo(SerializableProperty that) {
		return String.CASE_INSENSITIVE_ORDER.compare(this.getName(), that.getName());
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
	}

	@Override
	public final boolean equals(Object that) {
		return this == that;
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public static @interface Member {
		String icon() default "tag";

		int size() default 2;

		boolean mayBeEmpty() default false;
	}

	/**
	 * Use this annotation to add an description to the annotated object. Unlike JavaDoc this description can be inspected at runtime.
	 *
	 * @author Olaf Neumann
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ ElementType.TYPE, ElementType.FIELD })
	public static @interface Description {
		/**The description of the annotated object
		 * @return the description
		 */
		String value();
	}
}
