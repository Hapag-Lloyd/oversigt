package com.hlag.oversigt.properties;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * This interface marks classes that will be stored in the database in a
 * separate tables.
 *
 * @author neumaol
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "$class")
public abstract class SerializableProperty implements Comparable<SerializableProperty> {
	private final int id;

	private String name;

	protected SerializableProperty(final int id, final String name) {
		this.id = id;
		this.name = name;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	@Override
	public int compareTo(@Nullable final SerializableProperty that) {
		return String.CASE_INSENSITIVE_ORDER.compare(getName(),
				Optional.ofNullable(that).map(SerializableProperty::getName).orElse(""));
	}

	@Override
	public final boolean equals(@Nullable final Object that) {
		return this == that;
	}

	@Override
	public final int hashCode() {
		return Objects.hashCode(this);
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public static @interface Member {
		String icon() default "tag";

		int size() default 2;

		boolean mayBeEmpty() default false;
	}

	/**
	 * Use this annotation to add an description to the annotated object. Unlike
	 * JavaDoc this description can be inspected at runtime.
	 *
	 * @author Olaf Neumann
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ ElementType.TYPE, ElementType.FIELD })
	public static @interface Description {
		/**
		 * The description of the annotated object
		 *
		 * @return the description
		 */
		String value();
	}
}
