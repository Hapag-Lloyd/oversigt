package com.hlag.oversigt.web;

import static com.hlag.oversigt.util.Utils.map;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.POST;

import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.util.HttpUtils;

import freemarker.template.Configuration;
import freemarker.template.Template;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.session.Session;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

@Singleton
public class LoginHandler implements HttpHandler {
	private static final Logger CHANGE_LOGGER = LoggerFactory.getLogger("change");

	private final Configuration templateConfiguration;

	private final Authenticator authenticator;

	private final HttpServerExchangeHandler exchangeHelper;

	private List<String> availableImages = new ArrayList<>();

	@Inject
	public LoginHandler(final Configuration templateConfiguration,
			final Authenticator authenticator,
			final HttpServerExchangeHandler exchangeHelper) {
		this.templateConfiguration = templateConfiguration;
		this.authenticator = authenticator;
		this.exchangeHelper = exchangeHelper;

		for (int i = 1;; i += 1) {
			try {
				final String name = StringUtils.leftPad(Integer.toString(i), 3, '0');
				final URL url = Resources.getResource("statics/login/" + name + ".jpg");
				final ByteSource source = Resources.asByteSource(url);
				try (InputStream in = source.openStream()) {
					if (in != null) {
						availableImages.add(name);
					} else {
						break;
					}
				} catch (@SuppressWarnings("unused") final Exception ignore) { /* do nothing */ }
			} catch (@SuppressWarnings("unused") final IllegalArgumentException ignore) {
				break;
			}
		}
	}

	@Override
	public void handleRequest(final HttpServerExchange exchange) throws Exception {
		if (GET.equals(exchange.getRequestMethod())) {
			exchangeHelper.doNonBlocking(this::doGet, exchange);
		} else if (POST.equals(exchange.getRequestMethod())) {
			exchangeHelper.doNonBlocking(this::doPost, exchange);
		} else {
			exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
		}
	}

	private void doGet(final HttpServerExchange exchange) throws Exception {
		final Optional<String> message = exchangeHelper.query(exchange, "message");

		final StringWriter writer = new StringWriter();
		final String backgroundImage = availableImages.get((int) (Math.random() * availableImages.size()));
		final Map<String, Object> model = map("backgroundNumber", backgroundImage);
		if (message.isPresent() && message.get().equals("failed")) {
			model.putAll(map("message", "Login failed. Please try again!"));
		}

		final Template template = templateConfiguration.getTemplate("views/layout/login/layout.ftl.html");
		template.process(model, writer);
		final String content = writer.toString();
		exchange.setStatusCode(StatusCodes.OK);
		exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
		exchange.getResponseSender().send(ByteBuffer.wrap(content.getBytes("UTF-8")));
		exchange.endExchange();
	}

	private void doPost(final HttpServerExchange exchange) throws Exception {
		final FormData formData = exchangeHelper.getFormData(exchange);
		final String action = exchangeHelper.param(formData, "action");

		if ("login".equals(action)) {
			final String username = exchangeHelper.param(formData, "username");
			final String password = exchangeHelper.param(formData, "password");

			final Optional<Principal> principal = authenticator.login(username, password);
			if (principal.isPresent()) {
				final Session session = exchangeHelper.getOrCreateSession(exchange);
				session.setAttribute("PRINCIPAL", principal.get());
				CHANGE_LOGGER.info("User logged in: " + principal.get().getUsername());
				HttpUtils.redirect(exchange, exchange.getRequestURI(), true, true);
			} else {
				HttpUtils.redirect(exchange, "?message=failed", false, true);
			}
		}
	}
}
