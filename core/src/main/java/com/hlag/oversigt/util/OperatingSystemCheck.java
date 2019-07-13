package com.hlag.oversigt.util;

import java.util.Locale;

public final class OperatingSystemCheck {
	/**
	 * types of Operating Systems
	 */
	public enum OperatingSystem {
		Windows,
		MacOS,
		Linux,
		Other
	}

	/**
	 * detect the operating system from the os.name System property and cache the
	 * result
	 *
	 * @returns - the operating system detected
	 */
	public static OperatingSystem getOperatingSystemType() {
		final String OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
		if (OS.indexOf("mac") >= 0 || OS.indexOf("darwin") >= 0) {
			return OperatingSystem.MacOS;
		} else if (OS.indexOf("win") >= 0) {
			return OperatingSystem.Windows;
		} else if (OS.indexOf("nux") >= 0) {
			return OperatingSystem.Linux;
		} else {
			return OperatingSystem.Other;
		}
	}
}
