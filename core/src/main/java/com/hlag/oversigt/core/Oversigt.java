package com.hlag.oversigt.core;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.util.concurrent.Service;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.hlag.oversigt.connect.jira.config.JiraConfigurationProvider;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.storage.Storage;

import ch.qos.logback.classic.LoggerContext;
import de.larssh.utils.Finals;

/**
 * Application Entry Point. Creates Guava's Injector and runs spark server
 *
 * @author avarabyeu
 * @author noxfireone
 */
public final class Oversigt {
	/**
	 * The human readable application name
	 */
	public static final String APPLICATION_NAME = Finals.constant("Oversigt");

	/**
	 * The human readable application version
	 */
	public static final String APPLICATION_VERSION = Finals.constant("0.6");

	private static final Logger LOGGER = LoggerFactory.getLogger(Oversigt.class);

	/**
	 * The name of the file containing the application configuration
	 */
	static final String APPLICATION_CONFIG_FILE = Finals.constant("config.json");

	private AtomicBoolean bootstrapped = new AtomicBoolean(false);

	private AtomicBoolean shuttingDown = new AtomicBoolean(false);

	private Injector injector;

	private Thread shutdownHook;

	private Oversigt(final Injector injector) {
		this.injector = injector;
		shutdownHook = new Thread(this::shutdown, "Oversigt-Shutdown-Hook");
	}

	/**
	 * Bootstraps Oversigt. This operation is allowed only once. Bootstrapping
	 * already started Oversigt is not permitted
	 *
	 * @return itself
	 */
	private Oversigt bootstrap() {
		if (bootstrapped.compareAndSet(false, true)) {
			final boolean debug = injector.getInstance(Key.get(boolean.class, Names.named("debug")));
			// if (debug) {
			// ((ch.qos.logback.classic.Logger)
			// LoggerFactory.getLogger(AbstractJdbcConnector.class))
			// .setLevel(Level.ALL);
			// }

			/* bootstrap server */
			final Service application = injector.getInstance(OversigtServer.class);
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
	private void shutdown() {
		if (bootstrapped.get()) {
			if (shuttingDown.compareAndSet(false, true)) {
				LOGGER.info("Shutting down Oversigt...");

				injector.getInstance(OversigtServer.class).stopAsync().awaitTerminated();
				LOGGER.info("Stopping storage service");
				close(Storage.class);
				LOGGER.info("Stopping authenticator service");
				close(Authenticator.class);

				/*
				 * shutdown method might be called by this hook. So, trying to remove hook which
				 * is currently is progress causes error
				 */
				if (!shutdownHook.isAlive()) {
					Runtime.getRuntime().removeShutdownHook(shutdownHook);
				}
				LOGGER.info("Oversigt has stopped.");

				// Shutdown slf4j
				((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
			}
		} else {
			throw new IllegalStateException("Oversigt is not bootstrapped");
		}
	}

	private void close(final Class<? extends AutoCloseable> clazz) {
		try {
			injector.getInstance(clazz).close();
		} catch (final Exception e) {
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

	/**
	 * The main Oversigt entry point
	 *
	 * @param args command line arguments
	 */
	public static void main(final String[] args) {
		// parse command line options
		final Optional<CommandLineOptions> options = CommandLineOptions.parse(args);
		// if command line options fail: shut down
		if (!options.isPresent()) {
			return;
		}

		// bootstrap Oversigt
		try {
			LOGGER.info("Bootstrapping Oversigt");
			Oversigt.builder().startOptions(options.get()).build().bootstrap();
		} catch (final Exception e) {
			LOGGER.error("Oversigt cannot start", e);
		}
	}

	/**
	 * Called if an exception occurred when dispatching an event in the
	 * application's event bus
	 *
	 * @param throwable the exception that occurred
	 * @param context   the context in which the exception occurred
	 */
	static void handleEventBusException(final Throwable throwable, final SubscriberExceptionContext context) {
		final StringBuilder sb = new StringBuilder();
		sb.append("Could not dispatch event");
		if (context.getEvent() instanceof OversigtEvent) {
			sb.append(" ID ").append(((OversigtEvent) context.getEvent()).getId());
		}
		sb.append(": ").append(context.getSubscriber()).append(" to ").append(context.getSubscriberMethod());
		LOGGER.error(sb.toString(), throwable);
	}

	/**
	 * Oversigt builder
	 */
	public static class Builder {
		private CommandLineOptions options = new CommandLineOptions();

		public Builder() {
			// no fields to be initialized
		}

		/**
		 * Create a builder
		 *
		 * @param options the options from the user
		 * @return the created builder
		 */
		public Builder startOptions(final CommandLineOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Builds Oversigt instance
		 *
		 * @return Oversigt
		 */
		public Oversigt build() {
			final AtomicReference<Oversigt> oversigt = new AtomicReference<>();
			final Injector injector = Guice.createInjector(//
					binder -> binder.bind(Runnable.class)
							.annotatedWith(Names.named("Shutdown"))
							.toInstance(() -> oversigt.get().shutdown()),
					options.createModule(),
					new OversigtConfigurationModule(options.isDebugFallback(), options.getLdapBindPasswordFallback()),
					new OversigtModule());

			JiraConfigurationProvider.setSocketTimeout(
					injector.getBinding(Key.get(int.class, Names.named("jira.socketTimeout"))).getProvider().get());

			oversigt.set(new Oversigt(injector));
			return oversigt.get();
		}
	}
}
