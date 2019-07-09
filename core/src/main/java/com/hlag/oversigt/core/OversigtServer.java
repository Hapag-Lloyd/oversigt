package com.hlag.oversigt.core;

import static com.hlag.oversigt.core.event.EventSender.DASHBOARD_KEY;
import static com.hlag.oversigt.util.HttpUtils.redirect;
import static com.hlag.oversigt.util.StringUtils.substringBefore;
import static com.hlag.oversigt.util.Utils.map;
import static com.hlag.oversigt.util.Utils.not;
import static io.undertow.servlet.Servlets.defaultContainer;
import static io.undertow.servlet.Servlets.listener;
import static io.undertow.servlet.Servlets.servlet;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.StringWriter;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.ws.rs.core.Application;

import org.apache.commons.io.IOUtils;
import org.jboss.resteasy.cdi.CdiInjectorFactory;
import org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.core.OversigtConfiguration.HttpListenerConfiguration;
import com.hlag.oversigt.core.event.EventSender;
import com.hlag.oversigt.core.event.JsonEvent;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.sources.MotivationEventSource;
import com.hlag.oversigt.util.ClassPathResourceManager;
import com.hlag.oversigt.util.FileUtils;
import com.hlag.oversigt.util.HttpUtils;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.web.DashboardCreationHandler;
import com.hlag.oversigt.web.HttpServerExchangeHandler;
import com.hlag.oversigt.web.LoginHandler;
import com.hlag.oversigt.web.WelcomeHandler;
import com.hlag.oversigt.web.api.ApiBootstrapListener;
import com.hlag.oversigt.web.ui.OversigtUiHelper;

import de.larssh.utils.Nullables;
import edu.umd.cs.findbugs.annotations.Nullable;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.DeflateEncodingProvider;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.util.ImmediateInstanceHandle;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import ro.isdc.wro.http.ConfigurableWroFilter;

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

	private static final Logger CHANGE_LOGGER = LoggerFactory.getLogger("change");

	public static final String MAPPING_API = "/api/v1";

	private final List<HttpListenerConfiguration> listeners;

	private final EventBus eventBus;

	private Optional<ServerSentEventHandler> sseHandler = Optional.empty();

	private Optional<Undertow> server = Optional.empty();

	private EventSender sender;

	private final Configuration templateConfiguration;

	private final WelcomeHandler welcomeHandler;

	private final LoginHandler loginHandler;

	// private final DashboardConfigurationHandler dashboardConfigurationHandler;
	private final DashboardCreationHandler dashboardCreationHandler;

	// private final EventSourceConfigurationHandler
	// eventSourceConfigurationHandler;
	private final DashboardController dashboardController;

	private final Application restApiApplication;

	private final HttpServerExchangeHandler exchangeHandler;

	private final Path[] addonFolders;

	private final String[] widgetsPaths;

	@Inject
	private SessionManager sessionManager;

	@Inject
	private SessionConfig sessionConfig;

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
	@Named("debug")
	private boolean debug;

	@Inject
	private Injector injector;

	@Inject
	private JsonUtils json;

	@Inject
	public OversigtServer(@Named("listeners") final List<HttpListenerConfiguration> listeners,
			final EventBus eventBus,
			final EventSender sender,
			final Configuration templateConfiguration,
			final WelcomeHandler welcomeHandler,
			final LoginHandler loginHandler,
			// DashboardConfigurationHandler dashboardConfigurationHandler,
			final DashboardCreationHandler dashboardCreationHandler,
			// EventSourceConfigurationHandler eventSourceConfigurationHandler,
			final DashboardController dashboardController,
			final HttpServerExchangeHandler exchangeHandler,
			final Application restApiApplication,
			@Named("addonFolders") final Path[] addonFolders,
			@Named("widgetsPaths") final String[] widgetsPaths) {
		this.listeners = listeners;
		this.eventBus = eventBus;
		this.sender = sender;
		this.templateConfiguration = templateConfiguration;
		this.welcomeHandler = welcomeHandler;
		this.loginHandler = loginHandler;
		// this.dashboardConfigurationHandler = dashboardConfigurationHandler;
		this.dashboardCreationHandler = dashboardCreationHandler;
		// this.eventSourceConfigurationHandler = eventSourceConfigurationHandler;
		this.dashboardController = dashboardController;
		this.exchangeHandler = exchangeHandler;
		this.restApiApplication = restApiApplication;
		this.addonFolders = addonFolders;
		this.widgetsPaths = widgetsPaths;

		// Configure listeners
		if (listeners.isEmpty()) {
			throw new RuntimeException("No http listeners configured.");
		}

		addListener(new OversigtServerListener(), MoreExecutors.directExecutor());
	}

	private ServerSentEventHandler getServerSentEventHandler() {
		return sseHandler
				.orElseThrow(() -> new RuntimeException("The ServerSentEventHandler has not been initialized."));
	}

	@Override
	protected void startUp() throws Exception {
		LOGGER.info("Loading event source descriptors");
		dashboardController.loadEventSourceDescriptors(Arrays.asList(MotivationEventSource.class.getPackage()),
				Arrays.asList(addonFolders),
				Arrays.asList(widgetsPaths));
		LOGGER.info("Loading event source instances");
		dashboardController.loadEventSourceInstances();
		LOGGER.info("Loading dashboards");
		dashboardController.loadDashboards();

		LOGGER.info("Configuring web server");
		sseHandler = Optional.of(Handlers.serverSentEvents((connection, lastEventId) -> {
			Optional.ofNullable(connection.getQueryParameters().get("dashboard"))
					.map(Deque::getFirst)
					.flatMap(dashboardController::getDashboard)
					.ifPresent(db -> connection.putAttachment(DASHBOARD_KEY, db));
			eventBus.post(connection);
		}));

		eventBus.register(new Consumer<OversigtEvent>() {
			@Subscribe
			@Override
			public void accept(@Nullable final OversigtEvent event) {
				if (event == null) {
					return;
				}
				getServerSentEventHandler().getConnections()
						.stream()
						.forEach(connection -> sender.sendEventToConnection(event, connection));
			}
		});

		// final HttpHandler securedEventSourceConfigurationHandler =
		// withLogin(eventSourceConfigurationHandler);
		final HttpHandler securedDashboardCreationHandler = withLogin(dashboardCreationHandler);
		// final HttpHandler securedDashboardConfigurationHandler =
		// withLogin(dashboardConfigurationHandler);

		// Create Handlers for dynamic content
		final RoutingHandler routingHandler = Handlers.routing()
				// dashboard handling
				.get("/{dashboard}", this::serveDashboard)
				.get("/events", getServerSentEventHandler())
				.get("/views/{widget}", this::serveWidget)
				// get events from outside
				.post("/widgets/{widget}", this::handleForeignEvents)
				// dashboard configuration
				.get("/{dashboard}/config", this::redirectToConfigPage)
				.post("/{dashboard}/config", this::redirectToConfigPage)
				.get("/{dashboard}/config/{page}", this::redirectToConfigPage)
				.post("/{dashboard}/config/{page}", this::redirectToConfigPage)
				.get("/{dashboard}/create", securedDashboardCreationHandler)
				.post("/{dashboard}/create", securedDashboardCreationHandler)
				.get("/{dashboard}/create/{page}", securedDashboardCreationHandler)
				.post("/{dashboard}/create/{page}", securedDashboardCreationHandler)
				// server configuration
				// .get("/config", securedEventSourceConfigurationHandler)
				// .post("/config", securedEventSourceConfigurationHandler)
				// .get("/config/{page}", securedEventSourceConfigurationHandler)
				// .post("/config/{page}", securedEventSourceConfigurationHandler)
				// JSON Schema output
				.get("/schema/{class}", withLogin(this::serveJsonSchema))
				// session handling
				.get("/logout", withSession(this::doLogout))
				// default handler
				.get("/", this::redirectToWelcomePage)
				.get("/welcome", welcomeHandler)
				.get("/welcome/{page}", welcomeHandler);

		// Create Handlers for static content
		final HttpHandler rootHandler = Handlers.path(routingHandler)
				.addPrefixPath("/assets", createAssetsHandler())
				.addPrefixPath("/compiled", createAggregationHandler())
				.addPrefixPath("/api/swagger", createSwaggerUiHandler())
				.addPrefixPath(MAPPING_API, createApiHandler())
				.addPrefixPath("/config", createAngularHandler());

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
				.filter(not(HttpListenerConfiguration::isSsl))
				.forEach(c -> builder.addHttpListener(c.getPort(), c.getIp()));
		listeners.stream()
				.filter(HttpListenerConfiguration::isSsl)
				.forEach(c -> builder.addHttpsListener(c.getPort(),
						c.getIp(),
						Objects.requireNonNull(c.getSSLConfiguration()).createSSLContext()));
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
			dashboardController.startAllInstances();
		}

		LOGGER.info("StartUp finished");
	}

	private HttpHandler withLogin(final HttpHandler handler) {
		return withSession(Handlers.predicate(exchangeHandler::hasSession, handler, loginHandler));
	}

	private HttpHandler withSession(final HttpHandler handler) {
		return new SessionAttachmentHandler(handler, sessionManager, sessionConfig);
	}

	private void redirectToConfigPage(final HttpServerExchange exchange) {
		final List<String> parts = Splitter.on('/').omitEmptyStrings().splitToList(exchange.getRequestPath());
		if ("config".equals(parts.get(1))) {
			HttpUtils.redirect(exchange, "/config/dashboards/" + parts.get(0), false, true);
		} else {
			HttpUtils.badRequest(exchange);
		}
	}

	private void doLogout(final HttpServerExchange exchange) {
		final Optional<Principal> principal = exchangeHandler.getPrincipal(exchange);
		if (principal.isPresent()) {
			exchangeHandler.getSession(exchange).ifPresent(s -> s.invalidate(exchange));
			CHANGE_LOGGER.info("User logged out: "
					+ principal.orElseThrow(() -> new RuntimeException("The principal is not present.")).getUsername());
			HttpUtils.redirect(exchange, "/config", false, true);
		} else {
			HttpUtils.internalServerError(exchange);
		}
	}

	private void serveJsonSchema(final HttpServerExchange exchange) {
		final String className = exchange.getQueryParameters().get("class").poll();
		try {
			final Class<?> clazz = Class.forName(className);
			if (SerializableProperty.class.isAssignableFrom(clazz)) {
				exchange.setStatusCode(StatusCodes.OK);
				exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
				exchange.getResponseSender()
						.send(ByteBuffer.wrap(json.toJsonSchema(clazz).getBytes(StandardCharsets.UTF_8)));
				exchange.endExchange();
			} else {
				HttpUtils.notFound(exchange);
			}
		} catch (@SuppressWarnings("unused") final Exception ignore) {
			HttpUtils.notFound(exchange);
		}
	}

	private void redirectToWelcomePage(final HttpServerExchange exchange) {
		HttpUtils.redirect(exchange, "/welcome", false, true);
	}

	private void serveWidget(final HttpServerExchange exchange) {
		exchange.setStatusCode(StatusCodes.SEE_OTHER);
		final String widgetName = exchange.getQueryParameters().get("widget").getFirst();
		exchange.getResponseHeaders()
				.put(Headers.LOCATION, "/assets/widgets/" + substringBefore(widgetName, ".html") + "/" + widgetName);
		exchange.endExchange();
	}

	private void serveDashboard(final HttpServerExchange exchange) throws Exception {
		final boolean searchHtml = Optional.ofNullable(exchange.getRequestHeaders().get(Headers.ACCEPT))
				.map(Object::toString)
				.map(String::toLowerCase)
				.map(s -> s.contains("html"))
				.orElse(true);

		// Check if request wants to download some asset of a widget
		if (!searchHtml) {
			redirect(exchange, "/assets" + exchange.getRequestURI(), false, true);
			return;
		}

		// check whether to serve a favicon
		final String dashboardId = exchange.getQueryParameters().get("dashboard").poll();
		if ("favicon.ico".equals(dashboardId)) {
			// XXX add a proper favicon handler
			HttpUtils.notFound(exchange);
			return;
		}

		// check if the URI is correct... otherwise redirect to proper dashboard URI
		final String correctUri = "/" + dashboardId;
		if (!correctUri.equals(exchange.getRequestURI())) {
			redirect(exchange, correctUri, true, true);
			return;
		}

		// check if dashboard is present and enabled
		final Optional<Dashboard> dashboard = dashboardController.getDashboard(dashboardId);
		if (!dashboard.isPresent() || !dashboard.get().isEnabled()) {
			redirect(exchange, "/" + dashboardId + "/create", false, true);
			return;
		}

		// actually serve the dashboard
		final String html = processTemplate("/views/layout/dashboard/instance.ftl.html",
				map("title",
						dashboard.get().getTitle(),
						"columns",
						dashboard.get().getColumns(),
						"backgroundColor",
						dashboard.get().getBackgroundColor().getHexColor(),
						"computedTileWidth",
						dashboard.get().getComputedTileWidth(),
						"computedTileHeight",
						dashboard.get().getComputedTileHeight(),
						"widgets",
						dashboard.get().getWidgets()));
		exchange.getResponseSender().send(html);
	}

	private String processTemplate(final String templateName, final Object model)
			throws IOException, TemplateException {
		final StringWriter out = new StringWriter();
		templateConfiguration.getTemplate(templateName).process(model, out);
		return out.toString();
	}

	private void handleForeignEvents(final HttpServerExchange exchange) throws IOException {
		final String encoding = exchange.getRequestCharset();
		final Charset charset = Charset.forName(encoding);

		final String widgetId = exchange.getQueryParameters().get("widget").poll();
		final String json = IOUtils.toString(exchange.getInputStream(), charset);

		// XXX check if event is OK
		// - does this widget exist?
		// - is JSON well formed for this widget?

		final JsonEvent event = new JsonEvent(widgetId, json);
		eventBus.post(event);
	}

	@SuppressWarnings("resource")
	private HttpHandler createAssetsHandler() {
		return Handlers.resource(new AssetsClassPathResourceManager("statics", widgetsPaths));
	}

	private static Optional<URL> getResourceUrl(final String path) {
		try {
			return Optional.of(Resources.getResource(path));
		} catch (@SuppressWarnings("unused") final IllegalArgumentException ignore) {
			return Optional.empty();
		}
	}

	@SuppressWarnings("resource")
	private HttpHandler createSwaggerUiHandler() {
		return Handlers.resource(new ClassPathResourceManager("swagger/swagger-ui/3.8.0"));
	}

	private HttpHandler createAngularHandler() throws IOException {
		final Path basePath = FileUtils.getPath(OversigtUiHelper.getPathToUiResources().get());
		final Path indexHtml = basePath.resolve("index.html");
		if (!(Files.exists(indexHtml) && Files.isRegularFile(indexHtml))) {
			throw new RuntimeException(String.format("No file called 'index.html' found for Oversigt UI"));
		}

		// find all files belonging to the UI
		final List<String> otherFiles = new ArrayList<>();
		final Path parent = indexHtml.getParent();
		try (Stream<Path> paths = Files.list(parent)) {
			otherFiles.addAll(paths.filter(path -> !path.getFileName().toString().toLowerCase().endsWith(".txt"))
					.map(path -> path.getFileName().toString())
					.filter(name -> !name.equals("index.html"))
					.collect(toList()));
		}

		return exchange -> {
			// Find the file to serve
			final String relativePath
					= exchange.getRelativePath().substring(exchange.getRelativePath().startsWith("/") ? 1 : 0);
			final Path requestedFile = basePath.resolve(relativePath);
			final Path fileToServe;
			if (Files.isRegularFile(requestedFile)) {
				fileToServe = requestedFile;
			} else {
				// TODO check if it is really the HTML page that should be served...
				fileToServe = basePath.resolve("index.html");
			}

			// if index.html has been requested -> push other files
			if (fileToServe.getFileName().toString().equals("index.html")) {
				for (final String filename : otherFiles) {
					exchange.getConnection().pushResource(filename, new HttpString("GET"), new HeaderMap());
				}
			}

			// actually serve the file
			final String contentType = FileUtils.getExtension(fileToServe).map(extension -> {
				switch (extension.toLowerCase()) {
				case "css":
					return "text/css";
				case "html":
					return "text/html";
				case "js":
					return "application/javascript";
				default:
					return "application/octet-stream";
				}
			}).get();
			exchange.setStatusCode(StatusCodes.OK);
			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
			exchange.getResponseSender().send(ByteBuffer.wrap(Files.readAllBytes(fileToServe)));
			exchange.endExchange();
		};

	}

	/**
	 * Uses Wro4j Filter to pre-process resources Required for coffee scripts
	 * compilation and saas processing Wro4j uses Servlet API so we make fake
	 * Servlet Deployment here to emulate servlet-based environment
	 *
	 * @return Static resources handler
	 */
	private HttpHandler createAggregationHandler() throws ServletException {
		final DeploymentInfo deploymentInfo = Servlets.deployment()
				.setClassLoader(OversigtServer.class.getClassLoader())
				.setContextPath("/")
				.setDeploymentName("oversigt")
				.addFilterUrlMapping("wro4j", "/*", DispatcherType.REQUEST)
				.addFilter(Servlets.filter("wro4j", ConfigurableWroFilter.class, () -> {
					final ConfigurableWroFilter filter = new ConfigurableWroFilter();
					filter.setWroManagerFactory(new WroManagerFactory());
					return new ImmediateInstanceHandle<>(filter);
				}));
		final DeploymentManager manager = Servlets.defaultContainer().addDeployment(deploymentInfo);
		manager.deploy();
		return manager.start();
	}

	private HttpHandler createApiHandler() throws ServletException {
		// https://github.com/ukarim/undertow-resteasy-example
		final ResteasyDeployment deployment = new ResteasyDeployment();
		deployment.setApplication(restApiApplication);
		deployment.setInjectorFactoryClass(CdiInjectorFactory.class.getName());
		final DeploymentInfo deploymentInfo = createUndertowDeployment(deployment, "/");
		deploymentInfo.setClassLoader(OversigtServer.class.getClassLoader());
		deploymentInfo.setDeploymentName("oversigt-api");
		deploymentInfo.setContextPath(MAPPING_API);
		deploymentInfo.addListener(listener(org.jboss.weld.environment.servlet.Listener.class));
		deploymentInfo
				.addListener(listener(ApiBootstrapListener.class, createInstanceFactory(ApiBootstrapListener.class)));

		final DeploymentManager manager = defaultContainer().addDeployment(deploymentInfo);
		manager.deploy();
		return manager.start();
	}

	private DeploymentInfo createUndertowDeployment(final ResteasyDeployment deployment, final String mapping) {
		String mappingString = Nullables.orElse(mapping, "/");
		if (!mappingString.startsWith("/")) {
			mappingString = "/" + mappingString;
		}
		if (!mappingString.endsWith("/")) {
			mappingString += "/";
		}
		mappingString = mappingString + "*";
		String prefix = null;
		if (!mappingString.equals("/*")) {
			prefix = mappingString.substring(0, mappingString.length() - 2);
		}
		final ServletInfo resteasyServlet
				= servlet("ResteasyServlet", HttpServlet30Dispatcher.class).setAsyncSupported(true)
						.setLoadOnStartup(1)
						.addMapping(mappingString);
		if (prefix != null) {
			resteasyServlet.addInitParam("resteasy.servlet.mapping.prefix", prefix);
		}

		return new DeploymentInfo().addServletContextAttribute(ResteasyDeployment.class.getName(), deployment)
				.addServlet(resteasyServlet);
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
		dashboardController.stopAllInstances();

		/* close connections */
		LOGGER.info("Shutting down server sent event connections");
		getServerSentEventHandler().getConnections().forEach(ServerSentEventConnection::shutdown);

		/* stop the server */
		LOGGER.info("Stopping web server");
		server.ifPresent(Undertow::stop);
	}

	private <T> InstanceFactory<T> createInstanceFactory(final Class<T> clazz) {
		final Injector injector = this.injector.createChildInjector(binder -> binder.bind(clazz));
		return () -> new ImmediateInstanceHandle<>(injector.getInstance(clazz));
	}

	private final class OversigtServerListener extends Listener {
		private OversigtServerListener() {
			// no fileds to be initialized
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

	private static final class AssetsClassPathResourceManager extends ClassPathResourceManager {
		private final String[] widgetsPaths;

		private AssetsClassPathResourceManager(final String prefix, final String[] widgetsPaths) {
			super(prefix);
			this.widgetsPaths = widgetsPaths;
		}

		@Override
		protected URL getResourceUrl(final String realPath) throws IllegalArgumentException {
			if (realPath.startsWith("statics/widgets/")) {
				final String end = realPath.substring("statics/widgets/".length());
				return Arrays.stream(widgetsPaths)
						.map(s -> s + end)
						.map(OversigtServer::getResourceUrl)
						.filter(Optional::isPresent)
						.map(Optional::get)
						.findFirst()
						.orElseThrow(() -> new IllegalArgumentException("Resource " + realPath + " cannot be found."));
			}
			return super.getResourceUrl(realPath);
		}
	}
}
