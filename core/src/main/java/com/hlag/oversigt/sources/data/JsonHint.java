package com.hlag.oversigt.sources.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = { ElementType.TYPE, ElementType.FIELD, ElementType.METHOD })
@Retention(value = RetentionPolicy.RUNTIME)
public @interface JsonHint {
	String headerTemplate() default "";

	ArrayStyle arrayStyle() default ArrayStyle.DEFAULT;

	public static enum ArrayStyle {
		DEFAULT(null),
		GRID(null),
		TABLE("table"),
		TABS("tabs");
		private final String value;

		private ArrayStyle(String value) {
			this.value = value;
		}

		public String value() {
			return value;
		}
	}
}
