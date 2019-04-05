package com.hlag.oversigt.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.Locale;

import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.properties.SerializableProperty;

public class UiUtils {
	/**
	 * Determines whether the provided class has a dedicated editor in the UI or
	 * not.<br>
	 * Classes that have a dedicated editor:
	 * <ul>
	 * <li>all primitive types</li>
	 * <li>all types extending {@link SerializableProperty}</li>
	 * <li>all {@link Enum}s</li>
	 * <li>{@link String}</li>
	 * <li>{@link Color}</li>
	 * <li>{@link Locale}</li>
	 * <li>{@link Date}</li>
	 * <li>{@link LocalDate}</li>
	 * <li>{@link LocalTime}</li>
	 * <li>{@link TemporalAmount}</li>
	 * <li>{@link ZoneId}</li>
	 * </ul>
	 *
	 * @param clazz the class to test
	 * @return <code>true</code> if the class has a dedicated UI editor, otherwise
	 *         <code>false</code>
	 */
	public static boolean hasDedicatedEditor(final Class<?> clazz) {
		return clazz.isPrimitive()//
				|| clazz == String.class//
				|| SerializableProperty.class.isAssignableFrom(clazz)//
				|| clazz.isEnum() //
				|| clazz == Color.class//
				|| clazz == Locale.class//
				|| clazz == Date.class//
				|| clazz == LocalDate.class//
				|| clazz == LocalTime.class//
				|| TemporalAmount.class.isAssignableFrom(clazz)//
				|| clazz == ZoneId.class// TODO really?
		;
	}
}
