package com.hlag.oversigt.web;

import static com.hlag.oversigt.util.HttpUtils.forbidden;
import static com.hlag.oversigt.util.HttpUtils.notFound;
import static com.hlag.oversigt.util.Utils.map;
import static io.undertow.util.Methods.GET;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.security.Role;
import com.hlag.oversigt.security.Roles;
import com.hlag.oversigt.util.HttpUtils;

import freemarker.template.Configuration;
import freemarker.template.Template;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

public class AbstractSimpleConfigurationHandler implements HttpHandler {
	private static final Map<Class<?>, Logger> LOGGERS = Collections.synchronizedMap(new HashMap<>());

	protected final Logger getLogger() {
		return LOGGERS.computeIfAbsent(getClass(), LoggerFactory::getLogger);
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
		final ConfigPatternInfo info = getConfigPatternInfo(path);
		final Matcher m = PAGE_NUMBER_PATTERN.matcher(filename);
		m.find();
		return new PageInfo(m.group(1), path, info.getFilename(), info.getNeededRole());
	}

	private static ConfigPatternInfo getConfigPatternInfo(final String filename) {
		try {
			final String content = readContentString(filename);
			final Matcher matcher = CONFIG_LAYOUT_PATTERN.matcher(content);
			if (matcher.find()) {
				return new ConfigPatternInfo(matcher.group(1), Strings.emptyToNull(matcher.group(2)));
			}
			return new ConfigPatternInfo(filename);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Checks if the given URL points to a resource in the current class path and if
	 * so loads the resource as string with UTF-8 encoding
	 *
	 * @param urlPath the path to the resource to load
	 * @return the resource content interpreted as string with UTF-8 encoding
	 * @throws IOException if reading the resource fails
	 */
	private static String readContentString(final String urlPath) throws IOException {
		final URL url = Resources.getResource(urlPath);
		Preconditions.checkNotNull(url, "Unable to read bytes: %s", urlPath);
		return new String(Files.readAllBytes(Paths.get(urlPath)), StandardCharsets.UTF_8);
	}

	@Inject
	private Configuration templateConfiguration;

	@Inject
	private DashboardController dashboardController;

	@Inject
	private HttpServerExchangeHelper exchangeHelper;

	@Inject
	@Named("debug")
	private boolean debug;

	private final Map<String, PageInfo> pages = new LinkedHashMap<>();

	protected AbstractSimpleConfigurationHandler(final String path, final String[] filenames) {
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

	protected Map<String, Object> getModel(@SuppressWarnings("unused") final HttpServerExchange exchange,
			@SuppressWarnings("unused") final String page) {
		return new HashMap<>();
	}

	private Map<String, Object> getExtendedModel(final HttpServerExchange exchange, final String page) {
		// TODO make not nullable
		Map<String, Object> model = Optional.ofNullable(getModel(exchange, page)).orElse(new HashMap<>());
		if (model == null) {
			model = new HashMap<>();
		}
		final Optional<Principal> principal = getExchangeHelper().getPrincipal(exchange);
		model.putAll(map("principal",
				principal.orElse(null),
				"menuItems",
				pages.entrySet()
						.stream()
						.filter(p -> principal.map(p.getValue()::isAllowedFor).orElse(false))
						.map(e -> map("link", e.getKey(), "name", e.getValue().title))
						.toArray(),
				"activeMenuItem",
				page,
				"formUrl",
				"?"));
		return model;
	}

	public HttpServerExchangeHelper getExchangeHelper() {
		return exchangeHelper;
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
				getExchangeHelper().doNonBlocking(this::handleRequestGet, exchange);
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

	protected String getTemplateName(@SuppressWarnings("unused") final HttpServerExchange exchange, final PageInfo pi) {
		return pi.filename;
	}

	protected String getContentType(@SuppressWarnings("unused") final HttpServerExchange exchange,
			@SuppressWarnings("unused") final PageInfo pi) {
		return "text/html";
	}

	private void handleRequestGet(final HttpServerExchange exchange) {
		final String page = getExchangeHelper().query(exchange, "page").orElse(null);
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
				if (pi.needsPrincipal()
						&& !getExchangeHelper().getPrincipal(exchange).map(pi::isAllowedFor).orElse(false)) {
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

	protected static class PageInfo implements Comparable<PageInfo> {
		private final String name;

		private final String filename;

		private final String title;

		private final Role neededRole;

		PageInfo(final String name, final String filename, final String title, final Optional<String> neededRole) {
			this(name, filename, title, neededRole.flatMap(Roles::fromString).map(Roles::getRole).orElse(null));
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
				return name.equals(that.name) && filename.equals(that.filename) && title.equals(that.title);
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

	private static final class ConfigPatternInfo {
		private final String filename;

		private final Optional<String> neededRole;

		ConfigPatternInfo(final String filename) {
			this.filename = filename;
			neededRole = Optional.empty();
		}

		ConfigPatternInfo(final String filename, final String neededRole) {
			this.filename = filename;
			this.neededRole = Optional.of(neededRole);
		}

		public String getFilename() {
			return filename;
		}

		public Optional<String> getNeededRole() {
			return neededRole;
		}
	}
}
