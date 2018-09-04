package com.hlag.oversigt.core;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;

public class StartOptions {

	public static StartOptions parse(String[] args) {
		StartOptions options = new StartOptions();
		CmdLineParser parser = new CmdLineParser(options);
		try {
			parser.parseArgument(args);
			return options;
		} catch (CmdLineException e) {
			// if there's a problem in the command line,
			// you'll get this exception. this will report
			// an error message.
			System.err.println(e.getMessage());
			System.err.println("Valid options for this application are:");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();
			return null;
		}
	}

	private StartOptions() {
	}

	@Option(name = "--startEventSources", usage = "When starting the server also start all event sources", handler = BooleanOptionHandler.class, required = false)
	private boolean startEventSources = false;

	@Option(name = "--delete", usage = "delete event sources from database where the event source class does not exist any more", handler = BooleanOptionHandler.class, required = false)
	private boolean deleteNonExistingEventSourceFromDatabase;

	@Option(name = "--ldapBindPassword", required = false)
	private String ldapBindPasswordFallback = "";

	@Option(name = "--debug", handler = BooleanOptionHandler.class, usage = "Enables extra output for debugging purpose. Should not be used in production mode.")
	private boolean debugFallback = false;

	public boolean isStartEventSources() {
		return startEventSources;
	}

	public boolean isDeleteNonExistingEventSourceFromDatabase() {
		return deleteNonExistingEventSourceFromDatabase;
	}

	public boolean isDebugFallback() {
		return debugFallback;
	}

	public String getLdapBindPasswordFallback() {
		return ldapBindPasswordFallback;
	}

	Map<String, String> getProperties() {
		try {
			return Stream//
					.of(Introspector.getBeanInfo(getClass(), Object.class).getPropertyDescriptors())//
					.collect(Collectors.toMap(PropertyDescriptor::getName, pd -> get(pd.getReadMethod())));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String get(Method method) {
		try {
			Object object = method.invoke(this);
			if (object != null) {
				return object.toString();
			} else {
				return null;
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this);
	}
}
