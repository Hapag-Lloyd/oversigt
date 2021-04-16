package com.hlag.oversigt.core;

import static com.hlag.oversigt.core.event.EventSender.DASHBOARD_KEY;
import static com.hlag.oversigt.util.HttpUtils.redirect;
import static com.hlag.oversigt.util.StringUtils.substringBefore;
import static com.hlag.oversigt.util.Utils.map;
import static de.larssh.utils.Finals.constant;
import static io.undertow.servlet.Servlets.defaultContainer;
import static io.undertow.servlet.Servlets.listener;
import static io.undertow.servlet.Servlets.servlet;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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

import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.controller.DashboardController;
import com.hlag.oversigt.controller.DashboardDesignHelper;
import com.hlag.oversigt.controller.EventSourceInstanceController;
import com.hlag.oversigt.core.configuration.WroManagerFactory;
import com.hlag.oversigt.core.configuration.WroManagerFactory.CustomWroConfiguration;
import com.hlag.oversigt.core.configuration.WroManagerFactory.WroGroupContent;
import com.hlag.oversigt.core.event.JsonEvent;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.Widget;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.util.ClassPathResourceManager;
import com.hlag.oversigt.util.FileUtils;
import com.hlag.oversigt.util.HttpUtils;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.TypeUtils;
import com.hlag.oversigt.web.api.ApiBootstrapListener;
import com.hlag.oversigt.web.ui.OversigtUiHelper;

import de.larssh.utils.Finals;
import de.larssh.utils.Nullables;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import io.undertow.Handlers;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.util.ImmediateInstanceHandle;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import ro.isdc.wro.http.ConfigurableWroFilter;

@Singleton
public class HttpHandlers {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpHandlers.class);

	public static final String MAPPING_API = constant("/api/v1");

	@Inject
	private Injector injector;

	@Inject
	private EventBus eventBus;

	@Inject
	private Configuration templateConfiguration;

	@Inject
	private DashboardController dashboardController;

	@Inject
	private EventSourceInstanceController instanceController;

	@Inject
	private IdentityManager identityManager;

	@Inject
	private Authenticator authenticator;

	@Inject
	@Named("widgetsPaths")
	private String[] widgetsPaths;

	@Inject
	@Named("showOwnersInWelcomePage")
	private boolean showOwnersInWelcomePage;

	@Inject
	@Named("foreignEvents.needAuthentication")
	private boolean foreignEventsNeedAuthentication;

	@Inject
	@Named("foreignEvents.apiKeyHeaderName")
	private String foreignEventsApiKeyHeaderName;

	@Inject
	@Named("foreignEvents.allowedApiKeys")
	private List<String> foreignEventsAllowedApiKeys;

	@Inject
	private Application restApiApplication;

	public HttpHandlers() {
		// nothing to do
	}

	private final Supplier<ServerSentEventHandler> serverSentEventHandlerSupplier
			= Finals.lazy(this::createServerSentEventHandler);

	ServerSentEventHandler getServerSentEventsHandler() {
		return serverSentEventHandlerSupplier.get();
	}

	private ServerSentEventHandler createServerSentEventHandler() {
		return Handlers.serverSentEvents((final ServerSentEventConnection connection, final String lastEventId) -> {
			Optional.ofNullable(connection.getQueryParameters().get("dashboard"))//
					.map(Deque::getFirst)
					.flatMap(dashboardController::getDashboard)
					.ifPresent(dashboard -> connection.putAttachment(DASHBOARD_KEY, dashboard));
			eventBus.post(connection);
		});
	}

	HttpHandler createDashboardHandler() {
		return this::serveDashboard;
	}

	private void serveDashboard(final HttpServerExchange exchange) throws Exception {
		final boolean searchHtml = Optional.ofNullable(exchange.getRequestHeaders().get(Headers.ACCEPT))
				.map(Object::toString)
				.map(Ascii::toLowerCase)
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
			exchange.getResponseHeaders().add(HttpString.tryFromString("Content-Type"), "image/x-icon");
			exchange.getResponseSender()
					.send(ByteBuffer.wrap(Files.readAllBytes(
							Paths.get(Resources.getResource("statics/assets/images/favicon.ico").toURI()))));
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
			redirect(exchange, "/config/dashboards/create/" + dashboardId, false, true);
			return;
		}

		// actually serve the dashboard
		final String html = processTemplate("/web-templates/layout/dashboard/instance.ftl.html",
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
						dashboard.get().getWidgets(),
						"getWidgetDisplayStyle",
						(Function<Widget, String>) DashboardDesignHelper::getDisplayStyle,
						"getWidgetDisplayClass",
						(Function<Widget, String>) DashboardDesignHelper::getDisplayClass));

		exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
		exchange.getResponseSender().send(html);
	}

	HttpHandler createWelcomePageHandler() {
		return this::serveWelcomePage;
	}

	private void serveWelcomePage(final HttpServerExchange exchange) throws Exception {
		final String html = processTemplate("/web-templates/layout/root/page_welcome.ftl.html",
				map("title",
						"Welcome",
						"dashboards",
						dashboardController.getDashboardIds()
								.stream()
								.map(dashboardController::getDashboard)
								.map(Optional::get)
								.filter(Dashboard::isEnabled)
								.sorted(Comparator.comparing(Dashboard::getTitle, String.CASE_INSENSITIVE_ORDER))
								.collect(Collectors.toList()),
						"showOwnersInWelcomePage",
						showOwnersInWelcomePage,
						"getOwnerName",
						(Function<String, String>) dashboardId -> dashboardController.getDashboard(dashboardId)
								.map(d -> d.getOwners().iterator().next())
								.flatMap(authenticator::readPrincipal)
								.map(p -> Strings.isNullOrEmpty(p.getName()) ? p.getUsername() : p.getName())
								.orElse("No owner"),
						"getOwnerMail",
						(Function<String, String>) dashboardId -> dashboardController.getDashboard(dashboardId)
								.map(d -> d.getOwners().iterator().next())
								.flatMap(authenticator::readPrincipal)
								.map(com.hlag.oversigt.security.Principal::getEmail)
								.orElse(null)));

		exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
		exchange.getResponseSender().send(html);
	}

	private String processTemplate(final String templateName, final Map<String, Object> model)
			throws IOException, TemplateException {
		final StringWriter out = new StringWriter();
		templateConfiguration.getTemplate(templateName).process(model, out);
		return out.toString();
	}

	HttpHandler createJsonSchemaHandler() {
		return this::serveJsonSchema;
	}

	private void serveJsonSchema(final HttpServerExchange exchange) {
		final Optional<String> jsonSchema = Optional.ofNullable(exchange.getQueryParameters().get("class").poll())
				.flatMap(TypeUtils::getClassForName)
				.filter(SerializableProperty.class::isAssignableFrom)
				.map(JsonUtils::toJsonSchema);

		if (jsonSchema.isPresent()) {
			exchange.setStatusCode(StatusCodes.OK);
			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
			exchange.getResponseSender().send(ByteBuffer.wrap(jsonSchema.get().getBytes(StandardCharsets.UTF_8)));
		} else {
			HttpUtils.notFound(exchange);
		}
	}

	HttpHandler createWidgetHandler() {
		return this::serveWidget;
	}

	private void serveWidget(final HttpServerExchange exchange) {
		exchange.setStatusCode(StatusCodes.SEE_OTHER);
		final String widgetName = exchange.getQueryParameters().get("widget").getFirst();
		exchange.getResponseHeaders()
				.put(Headers.LOCATION, "/assets/widgets/" + substringBefore(widgetName, ".html") + "/" + widgetName);
		exchange.endExchange();
	}

	HttpHandler createForeignEventHandler() {
		HttpHandler handler = new BlockingHandler(this::handleForeignEvents);
		if (foreignEventsNeedAuthentication) {
			handler = addBasicAuthentication(handler, identityManager);
		}
		if (!foreignEventsAllowedApiKeys.isEmpty()) {
			handler = addApiKeyAuthentication(handler,
					foreignEventsApiKeyHeaderName,
					foreignEventsAllowedApiKeys::contains);
		}
		return handler;
	}

	@SuppressWarnings("unchecked")
	private void handleForeignEvents(final HttpServerExchange exchange) throws IOException {
		// identify principal
		final String name = Optional.of(exchange)
				.map(HttpServerExchange::getSecurityContext)
				.map(SecurityContext::getAuthenticatedAccount)
				.map(Account::getPrincipal)
				.map(Principal::getName)
				.orElse("<unknown>");
		LOGGER.info("Incoming foreign event. From: " + name);

		// read JSON
		final String encoding = exchange.getRequestCharset();
		final Charset charset = Charset.forName(encoding);
		final String json = IOUtils.toString(exchange.getInputStream(), charset);
		LOGGER.trace("JSON from outside: " + json);
		final Map<String, Object> jsonMap;

		// parse JSON
		try {
			jsonMap = Objects.requireNonNull(JsonUtils.fromJson(json, Map.class));
		} catch (final Exception e) {
			LOGGER.error("Unable to parse JSON for foreign event: " + json, e);
			HttpUtils.badRequest(exchange);
			return;
		}

		// check JSON
		if (!jsonMap.containsKey("id")) {
			LOGGER.warn("Request does not contain an ID.");
			HttpUtils.badRequest(exchange);
			return;
		}
		final String id = jsonMap.get("id").toString();
		try {
			instanceController.getEventSourceInstance(id);
			// TODO check if JSON fits event definition
		} catch (@SuppressWarnings("unused") final NoSuchElementException e) {
			LOGGER.warn("Event source instance '{}' does not exist.", id);
			HttpUtils.badRequest(exchange);
			return;
		}

		LOGGER.info("Posting event for: " + id);
		final JsonEvent event = new JsonEvent(json);
		eventBus.post(event);
		exchange.setStatusCode(StatusCodes.NO_CONTENT);
		exchange.endExchange();
	}

	@SuppressWarnings("resource")
	HttpHandler createAssetsHandler() {
		return Handlers.resource(new AssetsClassPathResourceManager("statics", widgetsPaths));
	}

	@SuppressWarnings("resource")
	HttpHandler createSwaggerUiHandler() {
		return Handlers.resource(new ClassPathResourceManager("swagger/swagger-ui/3.8.0"));
	}

	HttpHandler createAngularHandler() throws IOException {
		final Path basePath = FileUtils.getPath(OversigtUiHelper.getPathToUiResources().get());
		final Path indexHtml = basePath.resolve("index.html");
		if (!Files.exists(indexHtml) || !Files.isRegularFile(indexHtml)) {
			throw new RuntimeException(String.format("No file called 'index.html' found for Oversigt UI"));
		}

		// find all files belonging to the UI
		final List<String> otherFilesNames = new ArrayList<>();
		final Path parent = indexHtml.getParent();
		try (Stream<Path> paths = Files.list(parent)) {
			otherFilesNames.addAll(paths.filter(path -> !Ascii.toLowerCase(path.getFileName().toString()).endsWith(".txt"))
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

			// HTTP/2.0 -> if index.html has been requested -> push other files
			if (fileToServe.getFileName().toString().equals("index.html")) {
				for (final String filename : otherFilesNames) {
					exchange.getConnection().pushResource(filename, new HttpString("GET"), new HeaderMap());
				}
			}

			// actually serve the file
			final String contentType = FileUtils.getExtension(fileToServe).map(extension -> {
				switch (Ascii.toLowerCase(extension)) {
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
	HttpHandler createAggregationHandler() throws ServletException {
		final CustomWroConfiguration wroConfig = injector.getInstance(CustomWroConfiguration.class);
		final WroGroupContent content = new WroGroupContent();
		content.addFiltered(FileUtils.streamResourcesFromClasspath(), wroConfig);

		final DeploymentInfo deploymentInfo = Servlets.deployment()
				.setClassLoader(OversigtServer.class.getClassLoader())
				.setContextPath("/")
				.setDeploymentName("oversigt")
				.addFilterUrlMapping("wro4j", "/*", DispatcherType.REQUEST)
				.addFilter(Servlets.filter("wro4j", ConfigurableWroFilter.class, () -> {
					final ConfigurableWroFilter filter = new ConfigurableWroFilter();
					filter.setWroManagerFactory(new WroManagerFactory(content));
					return new ImmediateInstanceHandle<>(filter);
				}));
		final DeploymentManager manager = Servlets.defaultContainer().addDeployment(deploymentInfo);
		manager.deploy();
		return manager.start();
	}

	HttpHandler createApiHandler() throws ServletException {
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

	private <T> InstanceFactory<T> createInstanceFactory(final Class<T> clazz) {
		final Injector injector = this.injector.createChildInjector(binder -> binder.bind(clazz));
		return () -> new ImmediateInstanceHandle<>(injector.getInstance(clazz));
	}

	private static HttpHandler addBasicAuthentication(final HttpHandler next, final IdentityManager identityManager) {
		HttpHandler handler = next;
		handler = new AuthenticationCallHandler(handler);
		handler = new AuthenticationConstraintHandler(handler);
		final List<AuthenticationMechanism> mechanisms
				= Collections.singletonList(new BasicAuthenticationMechanism("Oversigt"));
		handler = new AuthenticationMechanismsHandler(handler, mechanisms);
		handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, handler);
		return handler;
	}

	private static HttpHandler addApiKeyAuthentication(final HttpHandler next,
			final String apiKeyHeaderName,
			final Predicate<String> isApiKeyValid) {
		return exchange -> {
			final boolean apiKeyOk = Optional.of(exchange)
					.map(HttpServerExchange::getRequestHeaders)
					.map(hm -> hm.get(apiKeyHeaderName))
					.map(HeaderValues::stream)
					.orElse(Stream.empty())
					.anyMatch(isApiKeyValid);
			if (!apiKeyOk) {
				HttpUtils.forbidden(exchange);
				return;
			}
			next.handleRequest(exchange);
		};
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
						.map(FileUtils::getResourceUrl)
						.filter(Optional::isPresent)
						.map(Optional::get)
						.findFirst()
						.orElseThrow(() -> new IllegalArgumentException("Resource " + realPath + " cannot be found."));
			}
			return super.getResourceUrl(realPath);
		}
	}
}
