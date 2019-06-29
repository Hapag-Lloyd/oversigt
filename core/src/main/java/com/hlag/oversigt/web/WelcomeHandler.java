package com.hlag.oversigt.web;

import static com.hlag.oversigt.util.Utils.map;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardController;

import io.undertow.server.HttpServerExchange;

@Singleton
public class WelcomeHandler extends AbstractConfigurationHandler {
	@Inject
	public WelcomeHandler(final DashboardController dashboardController,
			final HttpServerExchangeHandler exchangeHelper) {
		super(dashboardController, exchangeHelper, "views/layout/root/", new String[] { "page_welcome.ftl.html" });
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
							.sorted(Comparator.comparing(Dashboard::getTitle, String.CASE_INSENSITIVE_ORDER))
							.collect(Collectors.toList()));
		default:
			return null;
		}
	}

}
