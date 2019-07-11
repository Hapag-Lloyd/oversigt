package com.hlag.oversigt.web;

import static com.hlag.oversigt.util.Utils.map;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.model.Dashboard;

import io.undertow.server.HttpServerExchange;

@Singleton
public class WelcomeHandler extends AbstractSimpleConfigurationHandler {
	@Inject
	public WelcomeHandler() {
		super("views/layout/root/", new String[] { "page_welcome.ftl.html" });
	}

	@Override
	protected Map<String, Object> getModel(@SuppressWarnings("unused") final HttpServerExchange exchange,
			final String page) {
		switch (page) {
		case "welcome":
			return map("title",
					"Welcome",
					"dashboards",
					getDashboardController().getDashboardIds()
							.stream()
							.map(getDashboardController()::getDashboard)
							.map(Optional::get)
							.sorted(Comparator.comparing(Dashboard::getTitle, String.CASE_INSENSITIVE_ORDER))
							.collect(Collectors.toList()));
		default:
			return new HashMap<>();
		}
	}

}
