package com.hlag.oversigt.web;

import static com.hlag.oversigt.util.HttpUtils.badRequest;
import static com.hlag.oversigt.util.HttpUtils.forbidden;
import static com.hlag.oversigt.util.HttpUtils.notFound;
import static com.hlag.oversigt.util.HttpUtils.reloadWithGet;
import static com.hlag.oversigt.util.Utils.map;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.POST;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.security.NeedsRole;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.security.Role;
import com.hlag.oversigt.security.Roles;
import com.hlag.oversigt.util.HttpUtils;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.Tuple;
import com.hlag.oversigt.util.Utils;

import de.larssh.utils.Nullables;
import freemarker.template.Configuration;
import freemarker.template.Template;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormData.FormValue;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

public class AbstractConfigurationHandler implements HttpHandler {
	private static final Map<Class<?>, Logger> LOGGERS = Collections.synchronizedMap(new HashMap<>());

	protected final Logger getLogger() {
		return LOGGERS.computeIfAbsent(getClass(), LoggerFactory::getLogger);
	}

	protected void logChange(final HttpServerExchange exchange, final String string, final Object... objects) {
		final String username = getHelper().getPrincipal(exchange).map(Principal::getUsername).orElse("%unknown%");
		Utils.logChange(username, string, objects);
	}

	private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("^(?:[0-9]+_)?(.+)$");

	private static final Pattern CONFIG_LAYOUT_PATTERN
			= Pattern.compile("<@layout\\.ConfigurationLayout\\s+\"([^\"]*)\"(?:\\s+\"([^\"]*)\")?>");

	private static PageInfo getPageInfo(final String path) {
		String filename = Paths.get(path).getFileName().toString();
		if (filename.toLowerCase().startsWith("page_")) {
			filename = filename.substring("page_".length());
		}
		if (filename.toLowerCase().endsWith(".ftl.html")) {
			filename = filename.substring(0, filename.length() - ".ftl.html".length());
		}
		final Tuple<String, String> info = getConfigPatternInfo(path);
		final Matcher m = PAGE_NUMBER_PATTERN.matcher(filename);
		m.find();
		return new PageInfo(m.group(1), path, info.getFirst(), info.getSecond());
	}

	private static Tuple<String, String> getConfigPatternInfo(final String filename) {
		try {
			final String content = readContentString(filename);
			final Matcher matcher = CONFIG_LAYOUT_PATTERN.matcher(content);
			if (matcher.find()) {
				return new Tuple<>(matcher.group(1), Strings.emptyToNull(matcher.group(2)));
			}
			return new Tuple<>(filename, null);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Checks if the given url points to a resource in the current class path and if
	 * so loads the resource as string with UTF-8 encoding
	 *
	 * @param urlPath the path to the resource to load
	 * @return the resource content interpreted as string with UTF-8 encoding
	 * @throws IOException if reading the resource fails
	 */
	private static String readContentString(final String urlPath) throws IOException {
		final URL url = Resources.getResource(urlPath);
		Preconditions.checkNotNull(url, "Unable to read bytes: %s", urlPath);
		return IOUtils.toString(url, Charsets.UTF_8);
	}

	@Inject
	private Configuration templateConfiguration;

	@Inject
	private JsonUtils json;

	private final DashboardController dashboardController;

	private final HttpServerExchangeHandler exchangeHelper;

	@Inject
	@Named("debug")
	private boolean debug;

	private final Map<String, PageInfo> pages = new LinkedHashMap<>();

	protected AbstractConfigurationHandler(final DashboardController dashboardController,
			final HttpServerExchangeHandler exchangeHelper,
			final String path,
			final String[] filenames) {
		this.dashboardController = dashboardController;
		this.exchangeHelper = exchangeHelper;
		getLogger()
				.info("Initializing configuration handler for path: " + path + " with " + filenames.length + " pages");
		for (final String filename : filenames) {
			final PageInfo pi = getPageInfo(path + filename);
			pages.put(pi.getName(), pi);
		}
	}

	protected DashboardController getDashboardController() {
		return dashboardController;
	}

	public HttpServerExchangeHandler getHelper() {
		return exchangeHelper;
	}

	protected Map<String, Object> getModel(final HttpServerExchange exchange, final String page) {
		return null;
	}

	private Map<String, Object> getExtendedModel(final HttpServerExchange exchange, final String page) {
		Map<String, Object> model = getModel(exchange, page);
		if (model == null) {
			model = new HashMap<>();
		}
		final Optional<Principal> principal = exchangeHelper.getPrincipal(exchange);
		model.putAll(map(//
				"principal",
				principal.orElse(null),
				"menuItems",
				pages.entrySet()
						.stream()
						.filter(p -> principal.map(p.getValue()::isAllowedFor).orElse(false))
						.map(e -> map("link", e.getKey(), "name", e.getValue().title))
						.toArray(), //
				"activeMenuItem",
				page,
				"formUrl",
				"?"));
		return model;
	}

	protected final Optional<Dashboard> maybeGetDashboard(final HttpServerExchange exchange) {
		return exchangeHelper.query(exchange, "dashboard").map(dashboardController::getDashboard);
	}

	protected final Dashboard getDashboard(final HttpServerExchange exchange) {
		return maybeGetDashboard(exchange).get();
	}

	protected final void printException(final HttpServerExchange exchange, final Throwable throwable) {
		LoggerFactory.getLogger(getClass()).error("Unable to serve request", throwable);
		exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
		final StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		sb.append("<head><title>500 - Internal Server Error</title></head>");
		sb.append("<body>");
		sb.append("<h1>500 <small>Internal Server Error</small></h1><div>");
		if (!debug) {
			sb.append(
					"<p>An error occurred while executing the current request. Details can be found in the log file.</p>");
		} else {
			sb.append("<p>An error occurred while executing the current request.</p>");
			sb.append("<pre>").append(Throwables.getStackTraceAsString(throwable)).append("</pre>");
		}
		sb.append("</div></body>");
		sb.append("</html>");
		exchange.getResponseSender().send(sb.toString());
	}

	@Override
	public void handleRequest(final HttpServerExchange exchange) throws Exception {
		try {
			if (GET.equals(exchange.getRequestMethod())) {
				exchangeHelper.doNonBlocking(this::handleRequestGet, exchange);
			} else if (POST.equals(exchange.getRequestMethod())) {
				exchangeHelper.doNonBlocking(this::handleRequestPost, exchange);
			} else {
				exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
			}
		} catch (final Exception e) {
			printException(exchange, e);
		}
	}

	protected String getPreferredInitialPage() {
		return null;
	}

	private String getInitialPage(final Collection<String> availablePages) {
		final String preferred = getPreferredInitialPage();
		if (preferred != null && availablePages.contains(preferred)) {
			return preferred;
		}
		return availablePages.iterator().next();
	}

	protected String getTemplateName(final HttpServerExchange exchange, final PageInfo pi) {
		return pi.filename;
	}

	protected String getContentType(final HttpServerExchange exchange, final PageInfo pi) {
		return "text/html";
	}

	private void handleRequestGet(final HttpServerExchange exchange) throws Exception {
		final String page = exchangeHelper.query(exchange, "page").orElse(null);
		if (Strings.isNullOrEmpty(page)) {
			String url = exchange.getRequestURI();
			while (url.endsWith("/")) {
				url = url.substring(0, url.length() - 1);
			}
			url += "/" + getInitialPage(pages.keySet());
			HttpUtils.redirect(exchange, url, false, true);
			return;
		}
		if (!Strings.isNullOrEmpty(page)) {
			if (pages.containsKey(page)) {
				final PageInfo pi = pages.get(page);
				if (pi.needsPrincipal() && !exchangeHelper.getPrincipal(exchange).map(pi::isAllowedFor).orElse(false)) {
					forbidden(exchange);
					return;
				}
				try {
					final String templateFilename = getTemplateName(exchange, pi);
					final Template template = templateConfiguration.getTemplate(templateFilename);
					final StringWriter writer = new StringWriter();
					template.process(getExtendedModel(exchange, page), writer);
					final String content = writer.toString();
					exchange.setStatusCode(StatusCodes.OK);
					exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, getContentType(exchange, pi));
					exchange.getResponseSender().send(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
					exchange.endExchange();
				} catch (final Exception e) {
					getLogger().error("Unable to write page from template", e);
					printException(exchange, e);
					exchange.endExchange();
				}
			} else {
				notFound(exchange, "Page '" + page + "' not found");
			}
		} else {
			HttpUtils
					.redirect(exchange, exchange.getRequestURI() + "/" + pages.keySet().iterator().next(), false, true);
		}
	}

	protected boolean isAjax(final FormData data) {
		return Optional//
				.ofNullable(data.getFirst("ajax"))//
				.map(FormValue::getValue)//
				.map(Boolean::parseBoolean)//
				.orElse(false);
	}

	private Method getMethod(final String name, final Object... objects) {
		try {
			final Object[] objs = Nullables.orElseGet(objects, () -> new Object[0]);
			final Class<?>[] classes = new Class<?>[objs.length];
			for (int i = 0; i < objs.length; i += 1) {
				classes[i] = objs[i].getClass();
			}
			return getClass().getDeclaredMethod("doAction_" + name, classes);
		} catch (final Exception ignore) {
			// empty by design
		}
		return null;
	}

	@SuppressWarnings("checkstyle:XIllegalCatchDefault")
	protected void handleRequestPost(final HttpServerExchange exchange) throws IOException {
		final FormData formData = exchangeHelper.getFormData(exchange);
		final Optional<String> action = Optional.ofNullable(formData.getFirst("action")).map(FormValue::getValue);

		try {
			if (action.isPresent()) {
				final Method method = getMethod(action.get(), exchange, formData);
				if (method != null) {
					if (method.isAnnotationPresent(NeedsRole.class)) {
						final NeedsRole needsRole = method.getAnnotation(NeedsRole.class);
						boolean proceed = false;
						if (!needsRole.dashboard()) {
							proceed = exchangeHelper.getPrincipal(exchange)//
									.map(p -> p.hasRole(needsRole.role().getRole()))//
									.orElse(false);
						} else {
							proceed = exchangeHelper.getPrincipal(exchange)//
									.map(p -> p.hasRole(needsRole.role()
											.getRole()
											.getDashboardSpecificRole(getDashboard(exchange).getId())))//
									.orElse(false);
						}
						if (!proceed) {
							forbidden(exchange);
							return;
						}
					}
					Object object = method.invoke(this, exchange, formData);
					if (!exchange.isComplete()) {
						if (object == null) {
							object = new ActionResponse();
						}
						if (object instanceof ActionResponse) {
							final ActionResponse response = (ActionResponse) object;
							if (response.isDoNoAction()) {
								// nothing
							} else if (response.isDoGetRedirect()) {
								reloadWithGet(exchange);
								return;
							} else if (response.getJsonObject() != null) {
								exchange.setStatusCode(
										response.getStatusCode() != null ? response.getStatusCode() : StatusCodes.OK);
								exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
								exchange.getResponseSender().send(json.toJson(response.getJsonObject()));
								exchange.endExchange();
								return;
							} else if (response.getRedirect() != null) {
								HttpUtils.redirect(exchange, response.getRedirect(), false, true);
								return;
							} else {
								throw new RuntimeException("Unknown action!");
							}
						} else {
							throw new RuntimeException("Unknown return type");
						}
					}
				} else {
					badRequest(exchange, "Action '" + action.get() + "' not found.");
					return;
				}
			} else {
				badRequest(exchange, "No action found");
				return;
			}
		} catch (final Throwable e) {
			printException(exchange, e);
			return;
		}
	}

	protected static class PageInfo implements Comparable<PageInfo> {
		private final String name;

		private final String filename;

		private final String title;

		private final Role neededRole;

		PageInfo(final String name, final String filename, final String title, final String neededRole) {
			this(name, filename, title, Roles.maybeFromString(neededRole).map(Roles::getRole).orElse(null));
		}

		PageInfo(final String name, final String filename, final String title, final Role neededRole) {
			this.name = Objects.requireNonNull(name);
			this.filename = Objects.requireNonNull(filename);
			this.title = Objects.requireNonNull(title);
			this.neededRole = neededRole;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (filename == null ? 0 : filename.hashCode());
			result = prime * result + (name == null ? 0 : name.hashCode());
			result = prime * result + (title == null ? 0 : title.hashCode());
			return result;
		}

		@Override
		public boolean equals(final Object other) {
			if (other instanceof PageInfo) {
				final PageInfo that = (PageInfo) other;
				return name.equals(that.name) //
						&& filename.equals(that.filename) //
						&& title.equals(that.title);
			}
			return false;
		}

		@Override
		public int compareTo(final PageInfo that) {
			return filename.compareTo(that.filename);
		}

		public String getName() {
			return name;
		}

		public boolean isAllowedFor(final Principal principal) {
			return !needsPrincipal() || principal != null && principal.hasRole(neededRole);
		}

		public boolean needsPrincipal() {
			return neededRole != null;
		}
	}
}
