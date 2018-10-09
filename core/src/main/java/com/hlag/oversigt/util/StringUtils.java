package com.hlag.oversigt.util;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

/**
 * Set of useful stuff for working with strings
 *
 * @author avarabyeu
 */
public final class StringUtils {

	private StringUtils() {
	}

	/**
	 * Returns substring before provided string
	 *
	 * @param str
	 *          String to be truncated
	 * @param separator
	 *          Separator
	 * @return Null of initial string is Null, empty if provided string is empty, otherwise substring before
	 */
	public static String substringBefore(final String str, final String separator) {
		if (Strings.isNullOrEmpty(str)) {
			return str;
		}
		final int pos = str.indexOf(separator);
		if (pos == -1) {
			return str;
		}
		return str.substring(0, pos);
	}

	public static List<String> list(String string) {
		if (string == null) {
			return Collections.emptyList();
		}
		return Splitter.on(Pattern.compile("\\s*,+\\s*")).omitEmptyStrings().splitToList(string);
	}
}
