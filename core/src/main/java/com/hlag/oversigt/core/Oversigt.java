package com.hlag.oversigt.core;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.util.concurrent.Service;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.storage.AbstractJdbcConnector;
import com.hlag.oversigt.storage.Storage;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

/**
 * Application Entry Point. Creates Guava's Injector and runs spark server
 *
 * @author avarabyeu
 * @author noxfireone
 */
public final class Oversigt {

	private static final Logger LOGGER = LoggerFactory.getLogger(Oversigt.class);

	static final String APPLICATION_CONFIG = "config.json";

	private AtomicBoolean bootstrapped;
	private Injector injector;
	private Thread shutdownHook;

	private Oversigt(Injector injector) {
		this.injector = injector;
		this.bootstrapped = new AtomicBoolean(false);
		this.shutdownHook = new Thread(this::shutdown, "Oversigt-Shutdown-Hook");
	}

	/**
	 * Bootstraps Oversigt. This operation is allowed only once. Bootstrapping already started Oversigt is not permitted
	 *
	 * @return itself
	 */
	public Oversigt bootstrap() {
		if (bootstrapped.compareAndSet(false, true)) {
			boolean debug = injector.getInstance(Key.get(boolean.class, Names.named("debug")));
			if (debug) {
				((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AbstractJdbcConnector.class))
						.setLevel(Level.ALL);
			}

			/* bootstrap server */
			Service application = injector.getInstance(OversigtServer.class);
			application.startAsync();

			Runtime.getRuntime().addShutdownHook(shutdownHook);

			/* wait until server is running */
			application.awaitRunning();
			LOGGER.info(String.format("Oversigt has started in %s mode!", debug ? "DEBUG" : "PRODUCTION"));
		} else {
			throw new IllegalStateException("Oversigt already bootstrapped");
		}
		return this;
	}

	/**
	 * Shutdowns Oversigt. Permitted only for bootstrapped instance
	 */
	public void shutdown() {
		if (bootstrapped.compareAndSet(true, false)) {
			LOGGER.info("Shutting down Oversigt...");

			injector.getInstance(OversigtServer.class).stopAsync().awaitTerminated();
			LOGGER.info("Stopping storage service");
			close(Storage.class);
			LOGGER.info("Stopping authenticator service");
			close(Authenticator.class);

			/*
			 * shutdown method might be called by this hook. So, trying to remove hook which is currently is progress
			 * causes error
			 */
			if (!shutdownHook.isAlive()) {
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			}
			LOGGER.info("Oversigt has stopped.");

			// Shutdown slf4j
			((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
		} else {
			throw new IllegalStateException("Oversigt is not bootstrapped");
		}
	}

	private void close(Class<? extends AutoCloseable> clazz) {
		try {
			injector.getInstance(clazz).close();
		} catch (Exception e) {
			LOGGER.error("Unable to close " + clazz.getSimpleName() + " instance.", e);
		}
	}

	/**
	 * Creates new Oversigt builder
	 *
	 * @return Builder of Oversigt instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	public static void main(String... args) throws InterruptedException, IOException {
		// parse command line options
		CommandLineOptions options = CommandLineOptions.parse(args);
		// if command line options fail: shut down
		if (options == null) {
			return;
		}

		// bootstrap Oversigt
		try {
			LOGGER.info("Bootstrapping Oversigt");
			Oversigt.builder().startOptions(options).build().bootstrap();
		} catch (Exception e) {
			LOGGER.error("Oversigt cannot start", e);
		}
	}

	/**
	 * Oversigt builder
	 */
	public static class Builder {

		/* List of extension-modules */
		private List<Module> modules = new LinkedList<>();
		private CommandLineOptions options = null;

		/**
		 * Registers extension modules
		 *
		 * @param modules
		 *            Array of extension modules
		 * @return this builder
		 */
		public Builder registerModule(@Nullable Module... modules) {
			if (null != modules) {
				Collections.addAll(this.modules, modules);
			}
			return this;
		}

		public Builder startOptions(CommandLineOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Builds Oversigt instance
		 *
		 * @return Oversigt
		 */
		public Oversigt build() {
			Oversigt[] oversigt = new Oversigt[] { null };
			Injector createdInjector = Guice//
					.createInjector(//
							new OversigtModule(//
									options,
									() -> oversigt[0].shutdown(), //
									ImmutableList.//
									<Module> builder()//
											.addAll(modules)//
											.build()));

			return oversigt[0] = new Oversigt(createdInjector);
		}
	}

	static void handleEventBusException(Throwable throwable, SubscriberExceptionContext context) {
		StringBuilder sb = new StringBuilder();
		sb.append("Could not dispatch event");
		if (context.getEvent() instanceof OversigtEvent) {
			sb.append(" ID ").append(((OversigtEvent) context.getEvent()).getId());
		}
		sb.append(": ").append(context.getSubscriber()).append(" to ").append(context.getSubscriberMethod());
		LOGGER.error(sb.toString(), throwable);
	}
}
