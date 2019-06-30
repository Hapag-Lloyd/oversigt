package com.hlag.oversigt.web;

import static com.hlag.oversigt.util.Utils.map;
import static com.hlag.oversigt.web.ActionResponse.redirect;

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
	private final Authenticator authenticator;

	private final MailSender mailSender;

	@Inject
	public DashboardCreationHandler(final DashboardController dashboardController,
			final HttpServerExchangeHandler exchangeHelper,
			final Authenticator authenticator,
			final MailSender mailSender) {
		super(dashboardController,
				exchangeHelper,
				"views/layout/dashboardCreate/",
				new String[] { "page_create.ftl.html" });
		this.authenticator = authenticator;
		this.mailSender = mailSender;
	}

	@Override
	public void handleRequest(final HttpServerExchange exchange) throws Exception {
		final Optional<Dashboard> maybeDashboard = maybeGetDashboard(exchange);
		if (maybeDashboard.map(Dashboard::isEnabled).orElse(false)) {
			HttpUtils.redirect(exchange, "/" + getHelper().query(exchange, "dashboard").get() + "/config", false, true);
		} else {
			super.handleRequest(exchange);
		}
	}

	@Override
	protected Map<String, Object> getModel(final HttpServerExchange exchange, final String page) {
		final Optional<Dashboard> dashboard = maybeGetDashboard(exchange);
		final String title = dashboard.map(Dashboard::getTitle).orElse(getHelper().query(exchange, "dashboard").get());
		switch (page) {
		case "create":
			return map("dashboard", map("title", title, "exists", dashboard.isPresent()));
		default:
			return null;
		}
	}

	protected ActionResponse doAction_create(final HttpServerExchange exchange,
			@SuppressWarnings("unused") final FormData formData) {
		final Optional<Dashboard> maybeDashboard = maybeGetDashboard(exchange);
		if (!maybeDashboard.isPresent()) {
			final String dashboardId = getHelper().query(exchange, "dashboard").get();
			final Principal principal = getHelper().getPrincipal(exchange).get();
			final Optional<Dashboard> dashboard = getDashboardController()
					.createDashboard(dashboardId, principal, principal.hasRole(Role.ROLE_NAME_SERVER_ADMIN));
			logChange(exchange,
					"Created dashboard %s - enabled: %s",
					dashboard.get().getId(),
					dashboard.get().isEnabled());
			if (dashboard.get().isEnabled()) {
				return redirect("/" + dashboard.get().getId() + "/config");
			}
			mailSender.sendNewDashboard(getHelper().getPrincipal(exchange).get(), dashboard.get());
			return redirect("/" + dashboard.get().getId() + "/create/create");
		}
		throw new RuntimeException("The dashboard to be created already exists.");
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_enable(final HttpServerExchange exchange,
			@SuppressWarnings("unused") final FormData formData) {
		final Optional<Dashboard> maybeDashboard = maybeGetDashboard(exchange);
		if (maybeDashboard.isPresent()) {
			final Dashboard dashboard = maybeDashboard.get();
			if (!dashboard.isEnabled()) {
				dashboard.setEnabled(true);
				getDashboardController().updateDashboard(dashboard);
				dashboard.getOwners().forEach(authenticator::reloadRoles);
				mailSender.sendDashboardEnabled(getHelper().getPrincipal(exchange).get(), dashboard);
				logChange(exchange, "Enabled dashboard %s", dashboard.getId());
			}
			return redirect("/" + dashboard.getId() + "/config");
		}
		throw new RuntimeException("The dashboard to be enabled doesn't exists.");
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_delete(final HttpServerExchange exchange,
			@SuppressWarnings("unused") final FormData formData) {
		final Optional<Dashboard> maybeDashboard = maybeGetDashboard(exchange);
		if (maybeDashboard.isPresent()) {
			final Dashboard dashboard = maybeDashboard.get();
			getDashboardController().deleteDashboard(dashboard);
			dashboard.getOwners().forEach(authenticator::reloadRoles);
			logChange(exchange, "Deleted dashboard %s", dashboard.getId());
			return redirect("/" + dashboard.getId());
		}
		throw new RuntimeException("The dashboard to be deleted doesn't exists.");
	}

	protected ActionResponse doAction_abort(@SuppressWarnings("unused") final HttpServerExchange exchange,
			@SuppressWarnings("unused") final FormData formData) {
		return redirect("/");
	}
}
