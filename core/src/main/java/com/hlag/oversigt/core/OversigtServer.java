package com.hlag.oversigt.core;

import static com.hlag.oversigt.core.event.EventSender.DASHBOARD_KEY;
import static com.hlag.oversigt.util.HttpUtils.redirect;
import static com.hlag.oversigt.util.StringUtils.substringBefore;
import static com.hlag.oversigt.util.Utils.map;
import static com.hlag.oversigt.util.Utils.not;
import static io.undertow.servlet.Servlets.defaultContainer;
import static io.undertow.servlet.Servlets.listener;
import static io.undertow.servlet.Servlets.servlet;

import java.io.IOException;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.ws.rs.core.Application;

import org.apache.commons.io.IOUtils;
import org.jboss.resteasy.cdi.CdiInjectorFactory;
import org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.hlag.oversigt.core.Configuration.HttpListenerConfiguration;
import com.hlag.oversigt.core.event.JsonEvent;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.event.EventSender;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.sources.MotivationEventSource;
import com.hlag.oversigt.util.ClassPathResourceManager;
import com.hlag.oversigt.util.HttpUtils;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.Utils;
import com.hlag.oversigt.web.DashboardConfigurationHandler;
import com.hlag.oversigt.web.DashboardCreationHandler;
import com.hlag.oversigt.web.EventSourceConfigurationHandler;
import com.hlag.oversigt.web.HttpServerExchangeHandler;
import com.hlag.oversigt.web.LoginHandler;
import com.hlag.oversigt.web.WelcomeHandler;
import com.hlag.oversigt.web.api.ApiBootstrapListener;

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
import io.undertow.util.Headers;
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

	private ServerSentEventHandler sseHandler;
	private Undertow server;
	private EventSender sender;

	private final Configuration templateConfiguration;

	private final WelcomeHandler welcomeHandler;
	private final LoginHandler loginHandler;
	private final DashboardConfigurationHandler dashboardConfigurationHandler;
	private final DashboardCreationHandler dashboardCreationHandler;
	private final EventSourceConfigurationHandler eventSourceConfigurationHandler;
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
	@Named("NightlyReloader")
	private Service nightlyReloader;

	@Inject
	@Named("debug")
	private boolean debug;
	@Inject
	private Injector injector;
	@Inject
	private JsonUtils json;

	@Inject
	public OversigtServer(@Named("listeners") List<HttpListenerConfiguration> listeners,
			EventBus eventBus,
			EventSender sender,

			Configuration templateConfiguration,
			WelcomeHandler welcomeHandler,
			LoginHandler loginHandler,
			DashboardConfigurationHandler dashboardConfigurationHandler,
			DashboardCreationHandler dashboardCreationHandler,
			EventSourceConfigurationHandler eventSourceConfigurationHandler,
			DashboardController dashboardController,
			HttpServerExchangeHandler exchangeHandler,
			Application restApiApplication,
			@Named("additionalPackages") String[] additionalPackages,
			@Named("addonFolders") Path[] addonFolders,
			@Named("widgetsPaths") String[] widgetsPaths) {
		this.listeners = listeners;
		this.eventBus = eventBus;
		this.sender = sender;
		this.templateConfiguration = templateConfiguration;
		this.welcomeHandler = welcomeHandler;
		this.loginHandler = loginHandler;
		this.dashboardConfigurationHandler = dashboardConfigurationHandler;
		this.dashboardCreationHandler = dashboardCreationHandler;
		this.eventSourceConfigurationHandler = eventSourceConfigurationHandler;
		this.dashboardController = dashboardController;
		this.exchangeHandler = exchangeHandler;
		this.restApiApplication = restApiApplication;
		this.addonFolders = addonFolders;
		this.widgetsPaths = widgetsPaths;

		// Configure listeners
		if (listeners.isEmpty()) {
			throw new RuntimeException("No http listeners configured.");
		}

		addListener(new Listener() {
			@Override
			public void running() {
				LOGGER.info("Embedded Oversigt server has started and is listening on port(s) {}",
						server.getListenerInfo()
								.stream()
								.map(li -> (InetSocketAddress) li.getAddress())
								.mapToInt(InetSocketAddress::getPort)
								.toArray());
			}

			@Override
			public void failed(State from, Throwable failure) {
				LOGGER.error("Embedded Oversigt server failed from: " + from, failure);
			}

			@Override
			public void stopping(State from) {
				LOGGER.info("Stopping embedded Oversigt server");
			}
		}, MoreExecutors.directExecutor());
	}

	@Override
	protected void startUp() throws Exception {
		LOGGER.info("Loading event source descriptors");
		dashboardController.loadEventSourceDescriptors(//
				Arrays.asList(MotivationEventSource.class.getPackage()),
				Arrays.asList(addonFolders),
				Arrays.asList(widgetsPaths));
		LOGGER.info("Loading event source instances");
		dashboardController.loadEventSourceInstances();
		LOGGER.info("Loading dashboards");
		dashboardController.loadDashboards();

		LOGGER.info("Configuring web server");
		sseHandler = Handlers.serverSentEvents((connection, lastEventId) -> {
			Optional.ofNullable(connection.getQueryParameters().get("dashboard"))//
					.map(Deque::getFirst)
					.map(dashboardController::getDashboard)
					.ifPresent(db -> connection.putAttachment(DASHBOARD_KEY, db));
			eventBus.post(connection);
		});

		eventBus.register(new Consumer<OversigtEvent>() {
			@Subscribe
			@Override
			public void accept(OversigtEvent event) {
				sseHandler.getConnections()
						.stream()
						.forEach(connection -> sender.sendEventToConnection(event, connection));
			}
		});

		final HttpHandler securedEventSourceConfigurationHandler = withLogin(eventSourceConfigurationHandler);
		final HttpHandler securedDashboardCreationHandler = withLogin(dashboardCreationHandler);
		final HttpHandler securedDashboardConfigurationHandler = withLogin(dashboardConfigurationHandler);

		// Create Handlers for dynamic content
		final RoutingHandler routingHandler = Handlers//
				.routing()//
				// dashboard handling
				.get("/{dashboard}", this::serveDashboard)//
				.get("/events", sseHandler)//
				.get("/views/{widget}", this::serveWidget)//
				// get events from outside
				.post("/widgets/{widget}", this::handleForeignEvents)
				// dashboard configuration
				.get("/{dashboard}/config", securedDashboardConfigurationHandler)//
				.post("/{dashboard}/config", securedDashboardConfigurationHandler)//
				.get("/{dashboard}/config/{page}", securedDashboardConfigurationHandler)//
				.post("/{dashboard}/config/{page}", securedDashboardConfigurationHandler)//
				.get("/{dashboard}/create", securedDashboardCreationHandler)//
				.post("/{dashboard}/create", securedDashboardCreationHandler)//
				.get("/{dashboard}/create/{page}", securedDashboardCreationHandler)//
				.post("/{dashboard}/create/{page}", securedDashboardCreationHandler)//
				// server configuration
				.get("/config", securedEventSourceConfigurationHandler)//
				.post("/config", securedEventSourceConfigurationHandler)//
				.get("/config/{page}", securedEventSourceConfigurationHandler)//
				.post("/config/{page}", securedEventSourceConfigurationHandler)//
				// JSON Schema output
				.get("/schema/{class}", withLogin(this::serveJsonSchema))
				// session handling
				.get("/logout", withSession(this::doLogout))//
				// default handler
				.get("/", this::redirectToWelcomePage)
				.get("/welcome", welcomeHandler)
				.get("/welcome/{page}", welcomeHandler);

		// Create Handlers for static content
		final HttpHandler rootHandler = Handlers//
				.path(routingHandler)//
				.addPrefixPath("/assets", createAssetsHandler())
				.addPrefixPath("/compiled", createAggregationHandler())
				.addPrefixPath("/api/swagger", createSwaggerUiHandler())
				.addPrefixPath(MAPPING_API, createApiHandler());

		// Create Handler for compressing content
		final EncodingHandler encodingHandler = new EncodingHandler(new ContentEncodingRepository()//
				.addEncodingHandler("gzip", new GzipEncodingProvider(), 50, Predicates.maxContentSize(5))//
				.addEncodingHandler("deflate", new DeflateEncodingProvider(), 50, Predicates.maxContentSize(10)))//
						.setNext(rootHandler);

		Logger accessLogger = LoggerFactory.getLogger("access");
		final AccessLogHandler accessHandler = new AccessLogHandler(encodingHandler,
				message -> accessLogger.info(message),
				"combined",
				OversigtServer.class.getClassLoader());

		Builder builder = Undertow.builder();
		listeners.stream()//
				.filter(not(HttpListenerConfiguration::isSsl))
				.forEach(c -> builder.addHttpListener(c.getPort(), c.getIp()));
		listeners.stream()//
				.filter(HttpListenerConfiguration::isSsl)
				.forEach(c -> builder
						.addHttpsListener(c.getPort(), c.getIp(), c.getSSLConfiguration().createSSLContext()));
		server = builder.setHandler(accessHandler).build();

		LOGGER.info("Starting web server");
		server.start();

		nightlyReloader.startAsync();
		if (startEventSources) {
			LOGGER.info("Starting event source instances");
			dashboardController.startAllInstances();
		}

		LOGGER.info("StartUp finished");
	}

	private HttpHandler withLogin(HttpHandler handler) {
		return withSession(Handlers.predicate(exchangeHandler::hasSession, handler, loginHandler));
	}

	private HttpHandler withSession(HttpHandler handler) {
		return new SessionAttachmentHandler(handler, sessionManager, sessionConfig);
	}

	private void doLogout(HttpServerExchange exchange) {
		Optional<Principal> principal = exchangeHandler.getPrincipal(exchange);
		if (principal.isPresent()) {
			exchangeHandler.getSession(exchange).ifPresent(s -> s.invalidate(exchange));
			CHANGE_LOGGER.info("User logged out: " + principal.get().getUsername());
			HttpUtils.redirect(exchange, "/config", false, true);
		} else {
			HttpUtils.internalServerError(exchange);
		}
	}

	private void serveJsonSchema(HttpServerExchange exchange) {
		String className = exchange.getQueryParameters().get("class").poll();
		try {
			Class<?> clazz = Class.forName(className);
			if (SerializableProperty.class.isAssignableFrom(clazz)) {
				exchange.setStatusCode(StatusCodes.OK);
				exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
				exchange.getResponseSender()
						.send(ByteBuffer.wrap(json.toJsonSchema(clazz).getBytes(StandardCharsets.UTF_8)));
				exchange.endExchange();
			} else {
				HttpUtils.notFound(exchange);
			}
		} catch (Exception e) {
			HttpUtils.notFound(exchange);
		}
	}

	private void redirectToWelcomePage(HttpServerExchange exchange) {
		HttpUtils.redirect(exchange, "/welcome", false, true);
	}

	private void serveWidget(HttpServerExchange exchange) {
		exchange.setStatusCode(StatusCodes.SEE_OTHER);
		String widgetName = exchange.getQueryParameters().get("widget").getFirst();
		exchange.getResponseHeaders()
				.put(Headers.LOCATION, "/assets/widgets/" + substringBefore(widgetName, ".html") + "/" + widgetName);
		exchange.endExchange();
	}

	private void serveDashboard(HttpServerExchange exchange) throws Exception {
		Object acceptHeader = exchange.getRequestHeaders().get(Headers.ACCEPT);
		String acceptHeaderString = acceptHeader != null ? acceptHeader.toString() : "html";
		boolean searchHtml = acceptHeaderString.toLowerCase().contains("html");

		if (searchHtml) {
			String dashboardId = exchange.getQueryParameters().get("dashboard").poll();
			if ("favicon.ico".equals(dashboardId)) {
				// XXX add a proper favicon handler
				HttpUtils.notFound(exchange);
				return;
			}
			// check if the URI is correct... otherwise redirect to proper dashboard URI
			String correctUri = "/" + dashboardId;
			if (correctUri.equals(exchange.getRequestURI())) {
				Dashboard dashboard = dashboardController.getDashboard(dashboardId);
				if (dashboard == null || !dashboard.isEnabled()) {
					// redirect to config page in order to create new dashboard
					redirect(exchange, "/" + dashboardId + "/create", false, true);
				} else {
					String html = processTemplate("/views/layout/dashboard/instance.ftl.html",
							map("title",
									dashboard.getTitle(),
									"columns",
									dashboard.getColumns(),
									"backgroundColor",
									dashboard.getBackgroundColor().getHexColor(),
									"computedTileWidth",
									dashboard.getComputedTileWidth(),
									"computedTileHeight",
									dashboard.getComputedTileHeight(),
									"widgets",
									dashboard.getWidgets()));
					exchange.getResponseSender().send(html);
				}
			} else {
				redirect(exchange, correctUri, true, true);
			}
		} else {
			redirect(exchange, "/assets" + exchange.getRequestURI(), false, true);
		}
	}

	private String processTemplate(String templateName, Object model) throws IOException, TemplateException {
		StringWriter out = new StringWriter();
		templateConfiguration.getTemplate(templateName).process(model, out);
		return out.toString();
	}

	private void handleForeignEvents(HttpServerExchange exchange) throws IOException {
		String encoding = exchange.getRequestCharset();
		Charset charset = Charset.forName(encoding);

		String widgetId = exchange.getQueryParameters().get("widget").poll();
		String json = IOUtils.toString(exchange.getInputStream(), charset);

		// XXX check if event is OK
		// - does this widget exist?
		// - is JSON well formed for this widget?

		JsonEvent event = new JsonEvent(widgetId, json);
		eventBus.post(event);
	}

	private HttpHandler createAssetsHandler() {
		return Handlers.resource(new ClassPathResourceManager("statics") {
			@Override
			protected URL getResourceUrl(String realPath) {
				if (realPath.startsWith("statics/widgets/")) {
					final String end = realPath.substring("statics/widgets/".length());
					return Arrays.stream(widgetsPaths)
							.map(s -> s + end)
							.map(OversigtServer::getResourceUrl)
							.filter(Utils::isNotNull)
							.findFirst()
							.orElse(null);
				} else {
					return super.getResourceUrl(realPath);
				}
			}
		});
	}

	private static URL getResourceUrl(String path) {
		try {
			return Resources.getResource(path);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private HttpHandler createSwaggerUiHandler() {
		return Handlers.resource(new ClassPathResourceManager("swagger/swagger-ui/3.8.0"));
	}

	/**
	 * Uses Wro4j Filter to pre-process resources Required for coffee scripts
	 * compilation and saas processing Wro4j uses Servlet API so we make fake
	 * Servlet Deployment here to emulate servlet-based environment
	 *
	 * @return Static resources handler
	 */
	private HttpHandler createAggregationHandler() throws ServletException {
		DeploymentInfo deploymentInfo = Servlets.deployment()
				.setClassLoader(OversigtServer.class.getClassLoader())
				.setContextPath("/")
				.setDeploymentName("oversigt")
				.addFilterUrlMapping("wro4j", "/*", DispatcherType.REQUEST)
				.addFilter(Servlets.filter("wro4j", ConfigurableWroFilter.class, () -> {
					ConfigurableWroFilter filter = new ConfigurableWroFilter();
					filter.setWroManagerFactory(new WroManagerFactory());
					return new ImmediateInstanceHandle<>(filter);
				}));
		DeploymentManager manager = Servlets.defaultContainer().addDeployment(deploymentInfo);
		manager.deploy();
		return manager.start();
	}

	private HttpHandler createApiHandler() throws ServletException {
		// https://github.com/ukarim/undertow-resteasy-example
		ResteasyDeployment deployment = new ResteasyDeployment();
		deployment.setApplication(restApiApplication);
		deployment.setInjectorFactoryClass(CdiInjectorFactory.class.getName());
		DeploymentInfo deploymentInfo = createUndertowDeployment(deployment, "/");
		deploymentInfo.setClassLoader(OversigtServer.class.getClassLoader());
		deploymentInfo.setDeploymentName("oversigt-api");
		deploymentInfo.setContextPath(MAPPING_API);
		deploymentInfo.addListener(listener(org.jboss.weld.environment.servlet.Listener.class));
		deploymentInfo
				.addListener(listener(ApiBootstrapListener.class, createInstanceFactory(ApiBootstrapListener.class)));

		DeploymentManager manager = defaultContainer().addDeployment(deploymentInfo);
		manager.deploy();
		return manager.start();
	}

	private DeploymentInfo createUndertowDeployment(ResteasyDeployment deployment, String mapping) {
		if (mapping == null) {
			mapping = "/";
		}
		if (!mapping.startsWith("/")) {
			mapping = "/" + mapping;
		}
		if (!mapping.endsWith("/")) {
			mapping += "/";
		}
		mapping = mapping + "*";
		String prefix = null;
		if (!mapping.equals("/*")) {
			prefix = mapping.substring(0, mapping.length() - 2);
		}
		ServletInfo resteasyServlet = servlet("ResteasyServlet", HttpServlet30Dispatcher.class)//
				.setAsyncSupported(true)
				.setLoadOnStartup(1)
				.addMapping(mapping);
		if (prefix != null) {
			resteasyServlet.addInitParam("resteasy.servlet.mapping.prefix", prefix);
		}

		return new DeploymentInfo()//
				.addServletContextAttribute(ResteasyDeployment.class.getName(), deployment)
				.addServlet(resteasyServlet);
	}

	@Override
	protected void shutDown() throws Exception {
		/* stop the service for nightly reloading */
		LOGGER.info("Stopping reloader service");
		nightlyReloader.stopAsync();
		nightlyReloader.awaitTerminated();

		/* stop all event source instances */
		LOGGER.info("Stopping event sources");
		dashboardController.stopAllInstances();

		/* close connections */
		LOGGER.info("Shutting down server sent event connections");
		sseHandler.getConnections().forEach(ServerSentEventConnection::shutdown);

		/* stop the server */
		LOGGER.info("Stopping web server");
		server.stop();
	}

	private <T> InstanceFactory<T> createInstanceFactory(Class<T> clazz) {
		Injector injector = this.injector.createChildInjector(binder -> binder.bind(clazz));
		return () -> new ImmediateInstanceHandle<>(injector.getInstance(clazz));
	}
}
