package com.hlag.oversigt.sources.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

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
		private final Optional<String> value;

		ArrayStyle() {
			value = Optional.empty();
		}

		ArrayStyle(final String value) {
			this.value = Optional.of(value);
		}

		public Optional<String> value() {
			return value;
		}
	}
}
