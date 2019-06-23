package com.hlag.oversigt.sources.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import edu.umd.cs.findbugs.annotations.Nullable;

@Target(value = { ElementType.TYPE, ElementType.FIELD, ElementType.METHOD })
@Retention(value = RetentionPolicy.RUNTIME)
public @interface JsonHint {
	String headerTemplate() default "";

	ArrayStyle arrayStyle() default ArrayStyle.DEFAULT;

	enum ArrayStyle {
		DEFAULT,
		GRID,
		TABLE("table"),
		TABS("tabs");
		@Nullable
		private final String value;

		ArrayStyle() {
			value = null;
		}

		ArrayStyle(final String value) {
			this.value = value;
		}

		@Nullable
		public String value() {
			return value;
		}
	}
}
