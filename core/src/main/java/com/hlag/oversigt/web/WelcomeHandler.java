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
	private final DashboardController dashboardController;

	@Inject
	public WelcomeHandler(DashboardController dashboardController) {
		super("views/layout/root/", new String[] { "page_welcome.ftl.html" });
		this.dashboardController = dashboardController;
	}

	@Override
	protected Map<String, Object> getModel(HttpServerExchange exchange, String page) {
		switch (page) {
			case "welcome":
				return map("title",
						"Welcome",
						"dashboards",
						dashboardController.getDashboardIds()//
								.stream()
								.map(dashboardController::getDashboard)
								.sorted(Comparator.comparing(Dashboard::getTitle, String.CASE_INSENSITIVE_ORDER))
								.collect(Collectors.toList()));
			default:
				return null;
		}
	}

}
