package com.hlag.oversigt.web;

import static com.hlag.oversigt.util.HttpUtils.*;
import static com.hlag.oversigt.util.Utils.map;

import java.util.Map;
import java.util.Optional;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.NeedsRole;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.security.Role;
import com.hlag.oversigt.security.Roles;
import com.hlag.oversigt.util.HttpUtils;
import com.hlag.oversigt.util.MailSender;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;

@Singleton
public class DashboardCreationHandler extends AbstractConfigurationHandler {
	private final DashboardController dashboardController;
	private final Authenticator authenticator;
	private final MailSender mailSender;

	@Inject
	public DashboardCreationHandler(DashboardController dashboardController, Authenticator authenticator, MailSender mailSender) {
		super("views/layout/dashboardCreate/", new String[] { "page_create.ftl.html" });
		this.dashboardController = dashboardController;
		this.authenticator = authenticator;
		this.mailSender = mailSender;
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {
		Optional<Dashboard> maybeDashboard = maybeGetDashboard(exchange);
		if (maybeDashboard.map(Dashboard::isEnabled).orElse(false)) {
			HttpUtils.redirect(exchange, "/" + query(exchange, "dashboard").get() + "/config", false, true);
		} else {
			super.handleRequest(exchange);
		}
	}

	@Override
	protected Map<String, Object> getModel(HttpServerExchange exchange, String page) {
		Optional<Dashboard> dashboard = maybeGetDashboard(exchange);
		String title = dashboard.map(Dashboard::getTitle).orElse(query(exchange, "dashboard").get());
		switch (page) {
			case "create":
				return map("dashboard", map("title", title, "exists", dashboard.isPresent()));
			default:
				return null;
		}
	}

	protected ActionResponse doAction_create(HttpServerExchange exchange, FormData formData) {
		Optional<Dashboard> maybeDashboard = maybeGetDashboard(exchange);
		if (!maybeDashboard.isPresent()) {
			String dashboardId = query(exchange, "dashboard").get();
			Principal principal = getPrincipal(exchange).get();
			Dashboard dashboard = dashboardController
					.createDashboard(dashboardId, principal, principal.hasRole(Role.ROLE_NAME_SERVER_ADMIN));
			logChange(exchange, "Created dashboard %s - enabled: %s", dashboard.getId(), dashboard.isEnabled());
			if (dashboard.isEnabled()) {
				return redirect("/" + dashboard.getId() + "/config");
			} else {
				mailSender.sendNewDashboard(getPrincipal(exchange).get(), dashboard);
				return redirect("/" + dashboard.getId() + "/create/create");
			}
		} else {
			throw new RuntimeException("The dashboard to be created already exists.");
		}
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_enable(HttpServerExchange exchange, FormData formData) {
		Optional<Dashboard> maybeDashboard = maybeGetDashboard(exchange);
		if (maybeDashboard.isPresent()) {
			Dashboard dashboard = maybeDashboard.get();
			if (!dashboard.isEnabled()) {
				dashboard.setEnabled(true);
				dashboardController.updateDashboard(dashboard);
				dashboard.getOwners().forEach(authenticator::reloadRoles);
				mailSender.sendDashboardEnabled(getPrincipal(exchange).get(), dashboard);
				logChange(exchange, "Enabled dashboard %s", dashboard.getId());
			}
			return redirect("/" + dashboard.getId() + "/config");
		} else {
			throw new RuntimeException("The dashboard to be enabled doesn't exists.");
		}
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_delete(HttpServerExchange exchange, FormData formData) {
		Optional<Dashboard> maybeDashboard = maybeGetDashboard(exchange);
		if (maybeDashboard.isPresent()) {
			Dashboard dashboard = maybeDashboard.get();
			dashboardController.deleteDashboard(dashboard);
			dashboard.getOwners().forEach(authenticator::reloadRoles);
			logChange(exchange, "Deleted dashboard %s", dashboard.getId());
			return redirect("/" + dashboard.getId());
		} else {
			throw new RuntimeException("The dashboard to be deleted doesn't exists.");
		}
	}

	protected ActionResponse doAction_abort(HttpServerExchange exchange, FormData formData) {
		return redirect("/");
	}
}
