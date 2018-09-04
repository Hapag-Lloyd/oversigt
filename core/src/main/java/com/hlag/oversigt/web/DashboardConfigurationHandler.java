package com.hlag.oversigt.web;

import static com.hlag.oversigt.util.HttpUtils.getPrincipal;
import static com.hlag.oversigt.util.HttpUtils.maybeParam;
import static com.hlag.oversigt.util.HttpUtils.param;
import static com.hlag.oversigt.util.HttpUtils.query;
import static com.hlag.oversigt.util.Utils.map;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.model.DashboardDesign;
import com.hlag.oversigt.model.EventSourceInstance;
import com.hlag.oversigt.model.EventSourceProperty;
import com.hlag.oversigt.model.Widget;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.NeedsRole;
import com.hlag.oversigt.security.Roles;
import com.hlag.oversigt.sources.event.ReloadEvent;
import com.hlag.oversigt.util.HttpUtils;
import com.hlag.oversigt.util.ImageUtil;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.MailSender;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;

@Singleton
public class DashboardConfigurationHandler extends AbstractConfigurationHandler {
	private final Authenticator authenticator;
	private final MailSender mailSender;
	@Inject
	private DashboardController dashboardController;
	private final JsonUtils json;

	private static final Type TYPE_POSITIONS_MAP = new TypeToken<Map<String, Map<String, String>>>() {
	}.getType();

	@Inject
	public DashboardConfigurationHandler(Authenticator authenticator, MailSender mailSender, JsonUtils json) {
		super(
			"views/layout/dashboardConfig/",
			new String[] { "page_addWidget.ftl.html", "page_configureWidgets.ftl.html", "page_settings.ftl.html" });
		this.authenticator = authenticator;
		this.mailSender = mailSender;
		this.json = json;
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {
		Optional<Dashboard> maybeDashboard = maybeGetDashboard(exchange);
		if (maybeDashboard.map(d -> !d.isEnabled()).orElse(true)) {
			HttpUtils.redirect(exchange, "/" + query(exchange, "dashboard").get() + "/create", false, true);
		} else {
			super.handleRequest(exchange);
		}
	}

	@Override
	protected String getPreferredInitialPage() {
		return "configureWidgets";
	}

	@Inject
	private EventBus eventBus;

	private void triggerDashboardReload(Dashboard... dashboards) {
		// we only need to send this event if there are dashboards that should be reloaded
		if (dashboards.length > 0) {
			eventBus.post(new ReloadEvent(Stream.of(dashboards).map(Dashboard::getId).collect(Collectors.toList())));
		}
	}

	@Override
	protected Map<String, Object> getModel(HttpServerExchange exchange, String page) {
		Dashboard dashboard = getDashboard(exchange);
		switch (page) {
			case "addWidget":
				return map("dashboard",
						dashboard,
						"dashboardManager",
						dashboardController,
						"preview",
						(Function<EventSourceInstance, String>) k -> ImageUtil.getPreviewImageUrl(k.getDescriptor()));
			case "configureWidgets":
				return map("dashboard",
						dashboard,
						"addColorCssToWidgets",
						DashboardDesign.isAddColorCssToWidgets(dashboard));
			case "settings":
				return map("dashboard", dashboard);
			default:
				return null;
		}
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_addWidget(HttpServerExchange exchange, FormData formData) {
		String eventSourceInstance = param(formData, "eventSourceId");
		Widget widget = dashboardController.createWidgetForDashboard(getDashboard(exchange), eventSourceInstance);
		//getDashboard(exchange).addWidget(storage, eventSource);
		logChange(exchange,
				"Dashboard %s: add Widget: %s (%s)",
				getDashboard(exchange).getId(),
				widget.getId(),
				widget.getEventSourceInstance().getId());
		return redirect("configureWidgets#widget" + widget.getId());
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_updateWidget(HttpServerExchange exchange, FormData formData) {
		final Dashboard dashboard = getDashboard(exchange);
		int widgetId = Integer.parseInt(param(formData, "widget.id"));
		Widget widget = dashboard.getWidget(widgetId);
		widget.setName(param(formData, "widget.name"));
		widget.setTitle(param(formData, "widget.title"));
		widget.setPosX(Integer.parseInt(param(formData, "widget.posx")));
		widget.setPosY(Integer.parseInt(param(formData, "widget.posy")));
		widget.setSizeX(Integer.parseInt(param(formData, "widget.sizex")));
		widget.setSizeY(Integer.parseInt(param(formData, "widget.sizey")));
		widget.setBackgroundColor(Color
				.parse(maybeParam(formData, "widget.backgroundColor").orElse(widget.getBackgroundColor().toString())));
		widget.setStyle(param(formData, "widget.style"));
		// Read Data
		List<String> parameters = new ArrayList<>();
		formData.forEach(parameters::add);
		final String WIDGET_DATA = widgetId + ".data.";
		BiFunction<Widget, String, EventSourceProperty> getProperty = (w, s) -> w.getEventSourceInstance()
				.getDescriptor()
				.getDataItems()
				.stream()
				.filter(i -> i.getName().equals(s))
				.findAny()
				.get();
		parameters//
				.stream()//
				.filter(s -> s.startsWith(WIDGET_DATA))//
				.forEach(s -> widget.setWidgetData(getProperty.apply(widget, s.substring(WIDGET_DATA.length())),
						param(formData, s)));
		// save
		dashboardController.updateWidget(widget);
		logChange(exchange, "Dashboard %s: Update widget %s", dashboard.getId(), widget.getId());
		triggerDashboardReload(dashboard);
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_updateWidgetPositions(HttpServerExchange exchange, FormData formData) {
		final Dashboard dashboard = getDashboard(exchange);
		String json = formData.get("positions").getFirst().getValue();
		Map<String, Map<String, String>> positions = this.json.fromJson(json, TYPE_POSITIONS_MAP);

		// Update positions in database
		positions.entrySet().forEach(e -> {
			Widget widget = dashboard.getWidget(Integer.parseInt(e.getKey()));
			Map<String, String> value = e.getValue();
			int px = Integer.parseInt(value.get("x"));
			int py = Integer.parseInt(value.get("y"));
			int sx = Integer.parseInt(value.get("w"));
			int sy = Integer.parseInt(value.get("h"));
			if (widget.getPosX() != px //
					|| widget.getPosY() != py //
					|| widget.getSizeX() != sx //
					|| widget.getSizeY() != sy) {
				widget.setPosX(px);
				widget.setPosY(py);
				widget.setSizeX(sx);
				widget.setSizeY(sy);
				dashboardController.updateWidget(widget);
			}
		});
		logChange(exchange, "Dashboard %s: Update widget positions", dashboard.getId());
		triggerDashboardReload(dashboard);
		// TODO rückgabewert überprüfen... da kommt manchmal eine komische Fehlermeldung
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_deleteWidget(HttpServerExchange exchange, FormData formData) {
		int widgetId = Integer.parseInt(param(formData, "widget.id"));
		Dashboard dashboard = getDashboard(exchange);
		Widget widget = dashboard.getWidget(widgetId);
		dashboardController.deleteWidget(widget);
		logChange(exchange,
				"Dashboard %s: Delete widget %s (%s)",
				dashboard.getId(),
				widget.getId(),
				widget.getEventSourceInstance().getId());
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_updateDashboard(HttpServerExchange exchange, FormData formData) {
		String title = formData.get("title").getFirst().getValue();
		String screenWidth = formData.get("screenWidth").getFirst().getValue();
		String screenHeight = formData.get("screenHeight").getFirst().getValue();
		String columns = formData.get("columns").getFirst().getValue();
		String backgroundColor = formData.get("backgroundColor").getFirst().getValue();
		String colorScheme = formData.get("colorScheme").getFirst().getValue();
		String foregroundColorStart = formData.get("foregroundColorStart").getFirst().getValue();
		String foregroundColorEnd = formData.get("foregroundColorEnd").getFirst().getValue();
		Dashboard dashboard = getDashboard(exchange);
		dashboard.setBackgroundColor(backgroundColor);
		dashboard.setColumns(Integer.parseInt(columns));
		dashboard.setScreenHeight(Integer.parseInt(screenHeight));
		dashboard.setScreenWidth(Integer.parseInt(screenWidth));
		dashboard.setTitle(title);
		dashboard.setColorScheme(colorScheme);
		dashboard.setForegroundColorStart(foregroundColorStart);
		dashboard.setForegroundColorEnd(foregroundColorEnd);
		dashboardController.updateDashboard(dashboard);
		logChange(exchange, "Update dashboard %s", dashboard.getId());
		triggerDashboardReload(dashboard);
		return ok();
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_deleteDashboard(HttpServerExchange exchange, FormData formData) {
		Dashboard dashboard = getDashboard(exchange);
		dashboardController.deleteDashboard(dashboard);
		logChange(exchange, "Delete dashboard %s", dashboard.getId());
		return redirect("/" + dashboard.getId() + "/create");
	}

	protected ActionResponse doAction_reloadDashboard(HttpServerExchange exchange, FormData formData) {
		triggerDashboardReload(getDashboard(exchange));
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_enableWidget(HttpServerExchange exchange, FormData formData) {

		return enableDisableWidget(exchange, Integer.parseInt(param(formData, "widget.id")), true);
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_disableWidget(HttpServerExchange exchange, FormData formData) {
		return enableDisableWidget(exchange, Integer.parseInt(param(formData, "widget.id")), false);
	}

	private ActionResponse enableDisableWidget(HttpServerExchange exchange, int widgetId, boolean enabled) {
		Dashboard dashboard = getDashboard(exchange);
		Widget widget = dashboard.getWidget(widgetId);
		widget.setEnabled(enabled);
		dashboardController.updateWidget(widget);
		logChange(exchange,
				"Dashboard %s: widget %s set enabled to %s",
				dashboard.getId(),
				widget.getId(),
				widget.isEnabled());
		triggerDashboardReload(dashboard);
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_checkUsername(HttpServerExchange exchange, FormData formData) {
		return okJson(maybeParam(formData, "username")//
				.map(authenticator::isUsernameValid)//
				.orElse(false));
	}

	@NeedsRole(role = Roles.DASHBOARD_OWNER, dashboard = true)
	protected ActionResponse doAction_setOwner(HttpServerExchange exchange, FormData formData) {
		Optional<String> newOwner = maybeParam(formData, "username");

		if (newOwner.isPresent() && authenticator.isUsernameValid(newOwner.get())) {
			Dashboard dashboard = getDashboard(exchange);
			String oldOwner = dashboard.getOwner();

			// change owner
			dashboard.setOwner(newOwner.get());
			dashboardController.updateDashboard(dashboard);

			logChange(exchange, "Dashboard %s: Set owner to %s", dashboard.getId(), dashboard.getOwner());

			// reload roles
			authenticator.reloadRoles(oldOwner);
			authenticator.reloadRoles(dashboard.getOwner());

			if (!oldOwner.equals(dashboard.getOwner())) {
				mailSender.sendPermissionsReceived(getPrincipal(exchange).get(),
						dashboard.getOwner(),
						Roles.DASHBOARD_OWNER,
						dashboard);
			}

			return okJson(newOwner.get());
		} else {
			return okJson(false);
		}
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_setEditors(HttpServerExchange exchange, FormData formData) {
		Optional<List<String>> newEditors = maybeParam(formData, "usernames")//
				.map(s -> s.split("[,\\s]+"))
				.map(Arrays::asList)
				.map(ArrayList<String>::new);

		if (newEditors.isPresent() && newEditors.get().size() == 1 && Strings.isNullOrEmpty(newEditors.get().get(0))) {
			newEditors.get().clear();
		}

		if (newEditors.isPresent()
				&& newEditors.get().stream().filter(authenticator::isUsernameValid).count() == newEditors.get().size()) {
			Dashboard dashboard = getDashboard(exchange);

			Set<String> usersToInform = new HashSet<>(newEditors.get());
			usersToInform.removeAll(dashboard.getEditors());

			// find editors to reload their roles
			Set<String> editorsToReload = new HashSet<>(dashboard.getEditors());
			editorsToReload.addAll(newEditors.get());
			// kann hier noch verbessert werden, so dass nur die leute neu geladen werden, bei denen sich wirklich was ändert... war ich zu faul...

			// change editors
			dashboard.getEditors().addAll(newEditors.get());
			dashboard.getEditors().retainAll(newEditors.get());
			dashboardController.updateDashboard(dashboard);
			logChange(exchange, "Dashboard %s: Set editors to %s", dashboard.getId(), dashboard.getEditors());
			// update old and new editors roles
			editorsToReload.forEach(authenticator::reloadRoles);

			mailSender.sendPermissionsReceived(getPrincipal(exchange).get(),
					usersToInform,
					Roles.DASHBOARD_EDITOR,
					dashboard);

			if (!newEditors.get().isEmpty()) {
				return okJson(dashboard.getEditors().stream().collect(Collectors.joining(",")));
			} else {
				return okJson(true);
			}
		} else {
			return okJson(false);
		}
	}
}
