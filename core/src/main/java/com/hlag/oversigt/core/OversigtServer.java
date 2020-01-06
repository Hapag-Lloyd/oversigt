package com.hlag.oversigt.core;

import static com.hlag.oversigt.util.Utils.not;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.controller.DashboardController;
import com.hlag.oversigt.controller.EventSourceInstanceController;
import com.hlag.oversigt.core.configuration.OversigtConfiguration.HttpListenerConfiguration;
import com.hlag.oversigt.core.event.EventSender;
import com.hlag.oversigt.core.event.OversigtEvent;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.DeflateEncodingProvider;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.server.handlers.sse.ServerSentEventConnection;

/**
 * HTTP Server Controller. Bootstraps server and specifies all needed mappings
 * and request handlers
 *
 * @author avarabyeu
 * @author Olaf Neumann
 */
@Singleton
public class OversigtServer extends AbstractIdleService {
	private static final Logger LOGGER = LoggerFactory.getLogger(OversigtServer.class);

	@Inject
	@Named("listeners")
	private List<HttpListenerConfiguration> listeners;

	@Inject
	private EventBus eventBus;

	private Optional<Undertow> server = Optional.empty();

	@Inject
	private EventSender sender;

	@Inject
	private HttpHandlers handlers;

	@Inject
	private EventSourceInstanceController instanceController;

	@Inject
	private DashboardController dashboardController;

	@Inject
	@Named("startEventSources")
	private boolean startEventSources;

	@Inject
	@Named("NightlyDashboardReloader")
	private Service nightlyDashboardReloader;

	@Inject
	@Named("NightlyEventSourceRestarter")
	private Service nightlyEventSourceRestarter;

	@Inject
	@Named("foreignEvents.enabled")
	private boolean foreignEventsEnabled;

	@Inject
	public OversigtServer() {
		addListener(new OversigtServerListener(), MoreExecutors.directExecutor());
	}

	@Override
	protected void startUp() throws Exception {
		// Configure listeners
		if (listeners.isEmpty()) {
			throw new RuntimeException("No http listeners configured.");
		}

		LOGGER.info("Initializing controllers");
		dashboardController.initialize();

		LOGGER.info("Configuring web server");
		eventBus.register(new Consumer<OversigtEvent>() {
			@Subscribe
			@Override
			public void accept(@Nullable final OversigtEvent event) {
				if (event == null) {
					return;
				}
				handlers.getServerSentEventsHandler()
						.getConnections()
						.forEach(connection -> sender.sendEventToConnection(event, connection));
			}
		});

		// Create Handlers for dynamic content
		final RoutingHandler routingHandler = Handlers.routing()
				// dashboard handling
				.get("/{dashboard}", handlers.createDashboardHandler())
				// send events to dashboards
				.get("/events", handlers.getServerSentEventsHandler())
				// get widget details
				.get("/views/{widget}", handlers.createWidgetHandler())
				// JSON Schema output
				.get("/schema/{class}", handlers.createJsonSchemaHandler())
				// default handler
				.get("/", handlers.createWelcomePageHandler());
		if (foreignEventsEnabled) {
			// get events from outside
			routingHandler.post("/events", handlers.createForeignEventHandler());
		}

		// Create Handlers for static content
		final HttpHandler rootHandler = Handlers.path(routingHandler)
				.addPrefixPath("/assets", handlers.createAssetsHandler())
				.addPrefixPath("/compiled", handlers.createAggregationHandler())
				.addPrefixPath("/api/swagger", handlers.createSwaggerUiHandler())
				.addPrefixPath(HttpHandlers.MAPPING_API, handlers.createApiHandler())
				.addPrefixPath("/config", handlers.createAngularHandler());

		// Create Handler for compressing content
		final EncodingHandler encodingHandler = new EncodingHandler(new ContentEncodingRepository()
				.addEncodingHandler("gzip", new GzipEncodingProvider(), 50, Predicates.maxContentSize(5))
				.addEncodingHandler("deflate", new DeflateEncodingProvider(), 50, Predicates.maxContentSize(10)))
						.setNext(rootHandler);

		final Logger accessLogger = LoggerFactory.getLogger("access");

		final AccessLogHandler accessHandler = new AccessLogHandler(encodingHandler,
				message -> accessLogger.info(message),
				"combined",
				OversigtServer.class.getClassLoader());

		final Builder builder = Undertow.builder();
		listeners.stream()
				.filter(not(HttpListenerConfiguration::isTls))
				.forEach(c -> builder.addHttpListener(c.getPort(), c.getIp()));
		listeners.stream()
				.filter(HttpListenerConfiguration::isTls)
				.forEach(c -> builder.addHttpsListener(c.getPort(),
						c.getIp(),
						Objects.requireNonNull(c.getTLSConfiguration()).createSSLContext()));
		final Undertow server = builder.setHandler(accessHandler).build();
		this.server = Optional.of(server);

		LOGGER.info("Starting web server");
		try {
			server.start();
		} catch (final Exception e) {
			if (e.getCause() instanceof BindException) {
				LOGGER.error("Cannot start server", e.getCause());
				stopAsync();
			}
		}

		nightlyDashboardReloader.startAsync();
		nightlyEventSourceRestarter.startAsync();
		if (startEventSources) {
			LOGGER.info("Starting event source instances");
			instanceController.startAllInstances();
		}

		LOGGER.info("StartUp finished");
	}

	@Override
	protected void shutDown() throws Exception {
		/* stop the service for nightly reloading */
		LOGGER.info("Stopping nightly services");
		nightlyDashboardReloader.stopAsync();
		nightlyDashboardReloader.awaitTerminated();
		nightlyEventSourceRestarter.stopAsync();
		nightlyEventSourceRestarter.awaitTerminated();

		/* stop all event source instances */
		LOGGER.info("Stopping event sources");
		instanceController.stopAllInstances();

		/* close connections */
		LOGGER.info("Shutting down server sent event connections");
		handlers.getServerSentEventsHandler().getConnections().forEach(ServerSentEventConnection::shutdown);

		/* stop the server */
		LOGGER.info("Stopping web server");
		server.ifPresent(Undertow::stop);
	}

	private final class OversigtServerListener extends Listener {
		private OversigtServerListener() {
			// no fields to be initialized
		}

		@Override
		public void running() {
			final Undertow server = OversigtServer.this.server.get();
			if (server != null) {
				LOGGER.info("Embedded Oversigt server has started and is listening on port(s) {}",
						server.getListenerInfo()
								.stream()
								.map(li -> (InetSocketAddress) li.getAddress())
								.mapToInt(InetSocketAddress::getPort)
								.toArray());
			}
		}

		@Override
		public void failed(@Nullable final State from, @Nullable final Throwable failure) {
			LOGGER.error("Embedded Oversigt server failed from: " + from, failure);
		}

		@Override
		public void stopping(@SuppressWarnings("unused") @Nullable final State from) {
			LOGGER.info("Stopping embedded Oversigt server");
		}
	}
}
