package com.hlag.oversigt.connect.ssh;

import org.slf4j.Logger;

import com.google.common.base.Strings;

import edu.umd.cs.findbugs.annotations.Nullable;

public class JSchLogger implements com.jcraft.jsch.Logger {
	private final Logger logger;

	public JSchLogger(final Logger logger) {
		this.logger = logger;
	}

	@Override
	public boolean isEnabled(final int level) {
		switch (level) {
		case DEBUG:
			return logger.isDebugEnabled();
		case WARN:
			return logger.isWarnEnabled();
		case INFO:
			return logger.isInfoEnabled();
		case ERROR:
		case FATAL:
			return logger.isErrorEnabled();
		default:
			return false;
		}
	}

	@Override
	public void log(final int level, @Nullable final String input) {
		final String string = Strings.nullToEmpty(input);
		switch (level) {
		case DEBUG:
			logger.debug(string);
			return;
		case WARN:
			logger.warn(string);
			return;
		case INFO:
			logger.info(string);
			return;
		case ERROR:
		case FATAL:
			logger.error(string);
			return;
		default:
			logger.trace(string);
			return;
		}
	}
}
