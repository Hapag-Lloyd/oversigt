package com.hlag.oversigt.web;

import static com.hlag.oversigt.util.Utils.map;
import static com.hlag.oversigt.web.ActionResponse.ok;
import static com.hlag.oversigt.web.ActionResponse.okJson;
import static com.hlag.oversigt.web.ActionResponse.redirect;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Splitter;
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

	private final JsonUtils json;

	private static final Type TYPE_POSITIONS_MAP = new TypeToken<Map<String, Map<String, String>>>() {
		/* generics detection */
	}.getType();

	@Inject
	public DashboardConfigurationHandler(final DashboardController dashboardController,
			final HttpServerExchangeHandler exchangeHelper,
			final Authenticator authenticator,
			final MailSender mailSender,
			final JsonUtils json) {
		super(dashboardController,
				exchangeHelper,
				"views/layout/dashboardConfig/",
				new String[] { "page_addWidget.ftl.html", "page_configureWidgets.ftl.html", "page_settings.ftl.html" });
		this.authenticator = authenticator;
		this.mailSender = mailSender;
		this.json = json;
	}

	@Override
	public void handleRequest(final HttpServerExchange exchange) throws Exception {
		final Optional<Dashboard> maybeDashboard = maybeGetDashboard(exchange);
		if (maybeDashboard.map(d -> !d.isEnabled()).orElse(true)) {
			HttpUtils.redirect(exchange, "/" + getHelper().query(exchange, "dashboard").get() + "/create", false, true);
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

	private void triggerDashboardReload(final Dashboard... dashboards) {
		// we only need to send this event if there are dashboards that should be
		// reloaded
		if (dashboards.length > 0) {
			eventBus.post(new ReloadEvent(Stream.of(dashboards).map(Dashboard::getId).collect(Collectors.toList())));
		}
	}

	@Override
	protected Map<String, Object> getModel(final HttpServerExchange exchange, final String page) {
		final Dashboard dashboard = getDashboard(exchange);
		switch (page) {
		case "addWidget":
			return map("dashboard",
					dashboard,
					"dashboardManager",
					getDashboardController(),
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
	protected ActionResponse doAction_addWidget(final HttpServerExchange exchange, final FormData formData) {
		final String eventSourceInstance = getHelper().param(formData, "eventSourceId");
		final Widget widget
				= getDashboardController().createWidgetForDashboard(getDashboard(exchange), eventSourceInstance);
		// getDashboard(exchange).addWidget(storage, eventSource);
		logChange(exchange,
				"Dashboard %s: add Widget: %s (%s)",
				getDashboard(exchange).getId(),
				widget.getId(),
				widget.getEventSourceInstance().getId());
		return redirect("configureWidgets#widget" + widget.getId());
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_updateWidget(final HttpServerExchange exchange, final FormData formData) {
		final Dashboard dashboard = getDashboard(exchange);
		final int widgetId = Integer.parseInt(getHelper().param(formData, "widget.id"));
		final Widget widget = dashboard.getWidget(widgetId);
		widget.setName(getHelper().param(formData, "widget.name"));
		widget.setTitle(getHelper().param(formData, "widget.title"));
		widget.setPosX(Integer.parseInt(getHelper().param(formData, "widget.posx")));
		widget.setPosY(Integer.parseInt(getHelper().param(formData, "widget.posy")));
		widget.setSizeX(Integer.parseInt(getHelper().param(formData, "widget.sizex")));
		widget.setSizeY(Integer.parseInt(getHelper().param(formData, "widget.sizey")));
		widget.setBackgroundColor(Color.parse(getHelper().maybeParam(formData, "widget.backgroundColor")
				.orElse(widget.getBackgroundColor().toString())));
		widget.setStyle(getHelper().param(formData, "widget.style"));
		// Read Data
		final List<String> parameters = new ArrayList<>();
		formData.forEach(parameters::add);
		final String widgetData = widgetId + ".data.";
		final BiFunction<Widget, String, EventSourceProperty> getProperty = (w, s) -> w.getEventSourceInstance()
				.getDescriptor()
				.getDataItems()
				.stream()
				.filter(i -> i.getName().equals(s))
				.findAny()
				.get();
		parameters//
				.stream()//
				.filter(s -> s.startsWith(widgetData))//
				.forEach(s -> widget.setWidgetData(getProperty.apply(widget, s.substring(widgetData.length())),
						getHelper().param(formData, s)));
		// save
		getDashboardController().updateWidget(widget);
		logChange(exchange, "Dashboard %s: Update widget %s", dashboard.getId(), widget.getId());
		triggerDashboardReload(dashboard);
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_updateWidgetPositions(final HttpServerExchange exchange,
			final FormData formData) {
		final Dashboard dashboard = getDashboard(exchange);
		final String json = formData.get("positions").getFirst().getValue();
		// TODO check for null from JSON
		final Map<String, Map<String, String>> positions
				= Objects.requireNonNull(this.json.fromJson(json, TYPE_POSITIONS_MAP));

		// Update positions in database
		positions.entrySet().forEach(e -> {
			final Widget widget = dashboard.getWidget(Integer.parseInt(e.getKey()));
			final Map<String, String> value = e.getValue();
			final int px = Integer.parseInt(value.get("x"));
			final int py = Integer.parseInt(value.get("y"));
			final int sx = Integer.parseInt(value.get("w"));
			final int sy = Integer.parseInt(value.get("h"));
			if (widget.getPosX() != px //
					|| widget.getPosY() != py //
					|| widget.getSizeX() != sx //
					|| widget.getSizeY() != sy) {
				widget.setPosX(px);
				widget.setPosY(py);
				widget.setSizeX(sx);
				widget.setSizeY(sy);
				getDashboardController().updateWidget(widget);
			}
		});
		logChange(exchange, "Dashboard %s: Update widget positions", dashboard.getId());
		triggerDashboardReload(dashboard);
		// TODO rückgabewert überprüfen... da kommt manchmal eine komische Fehlermeldung
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_deleteWidget(final HttpServerExchange exchange, final FormData formData) {
		final int widgetId = Integer.parseInt(getHelper().param(formData, "widget.id"));
		final Dashboard dashboard = getDashboard(exchange);
		final Widget widget = dashboard.getWidget(widgetId);
		getDashboardController().deleteWidget(widget);
		logChange(exchange,
				"Dashboard %s: Delete widget %s (%s)",
				dashboard.getId(),
				widget.getId(),
				widget.getEventSourceInstance().getId());
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_updateDashboard(final HttpServerExchange exchange, final FormData formData) {
		final String title = formData.get("title").getFirst().getValue();
		final String screenWidth = formData.get("screenWidth").getFirst().getValue();
		final String screenHeight = formData.get("screenHeight").getFirst().getValue();
		final String columns = formData.get("columns").getFirst().getValue();
		final String backgroundColor = formData.get("backgroundColor").getFirst().getValue();
		final String colorScheme = formData.get("colorScheme").getFirst().getValue();
		final String foregroundColorStart = formData.get("foregroundColorStart").getFirst().getValue();
		final String foregroundColorEnd = formData.get("foregroundColorEnd").getFirst().getValue();
		final Dashboard dashboard = getDashboard(exchange);
		dashboard.setBackgroundColor(backgroundColor);
		dashboard.setColumns(Integer.parseInt(columns));
		dashboard.setScreenHeight(Integer.parseInt(screenHeight));
		dashboard.setScreenWidth(Integer.parseInt(screenWidth));
		dashboard.setTitle(title);
		dashboard.setColorScheme(colorScheme);
		dashboard.setForegroundColorStart(foregroundColorStart);
		dashboard.setForegroundColorEnd(foregroundColorEnd);
		getDashboardController().updateDashboard(dashboard);
		logChange(exchange, "Update dashboard %s", dashboard.getId());
		triggerDashboardReload(dashboard);
		return ok();
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_deleteDashboard(final HttpServerExchange exchange,
			@SuppressWarnings("unused") final FormData formData) {
		final Dashboard dashboard = getDashboard(exchange);
		getDashboardController().deleteDashboard(dashboard);
		logChange(exchange, "Delete dashboard %s", dashboard.getId());
		return redirect("/" + dashboard.getId() + "/create");
	}

	protected ActionResponse doAction_reloadDashboard(final HttpServerExchange exchange,
			@SuppressWarnings("unused") final FormData formData) {
		triggerDashboardReload(getDashboard(exchange));
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_enableWidget(final HttpServerExchange exchange, final FormData formData) {
		return enableDisableWidget(exchange, Integer.parseInt(getHelper().param(formData, "widget.id")), true);
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_disableWidget(final HttpServerExchange exchange, final FormData formData) {
		return enableDisableWidget(exchange, Integer.parseInt(getHelper().param(formData, "widget.id")), false);
	}

	private ActionResponse enableDisableWidget(final HttpServerExchange exchange,
			final int widgetId,
			final boolean enabled) {
		final Dashboard dashboard = getDashboard(exchange);
		final Widget widget = dashboard.getWidget(widgetId);
		widget.setEnabled(enabled);
		getDashboardController().updateWidget(widget);
		logChange(exchange,
				"Dashboard %s: widget %s set enabled to %s",
				dashboard.getId(),
				widget.getId(),
				widget.isEnabled());
		triggerDashboardReload(dashboard);
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_checkUsername(@SuppressWarnings("unused") final HttpServerExchange exchange,
			final FormData formData) {
		return okJson(getHelper().maybeParam(formData, "username")//
				.map(authenticator::isUsernameValid)//
				.orElse(false));
	}

	@NeedsRole(role = Roles.DASHBOARD_OWNER, dashboard = true)
	protected ActionResponse doAction_setOwners(final HttpServerExchange exchange, final FormData formData) {
		final Optional<List<String>> newOwners = getHelper().maybeParam(formData, "usernames")//
				.map(Splitter.on(Pattern.compile("[,\\s]+")).omitEmptyStrings()::splitToList)
				.map(ArrayList<String>::new);

		if (newOwners.isPresent() && newOwners.get().stream().allMatch(authenticator::isUsernameValid)) {
			final Dashboard dashboard = getDashboard(exchange);
			final Set<String> oldOwners = new HashSet<>(dashboard.getOwners());

			// change owners
			dashboard.setOwners(newOwners.get());
			getDashboardController().updateDashboard(dashboard);

			logChange(exchange, "Dashboard %s: Set owners to %s", dashboard.getId(), dashboard.getOwners());

			// reload roles
			oldOwners.forEach(authenticator::reloadRoles);
			dashboard.getOwners().forEach(authenticator::reloadRoles);

			// inform users
			final Set<String> usersToInform = new HashSet<>(dashboard.getOwners());
			usersToInform.removeAll(oldOwners);

			if (!newOwners.get().isEmpty()) {
				mailSender.sendPermissionsReceived(getHelper().getPrincipal(exchange).get(),
						usersToInform,
						Roles.DASHBOARD_OWNER,
						dashboard);
				return okJson(dashboard.getOwners().stream().collect(Collectors.joining(",")));
			}

			return okJson(true);
		}
		return okJson(false);
	}

	@NeedsRole(role = Roles.DASHBOARD_EDITOR, dashboard = true)
	protected ActionResponse doAction_setEditors(final HttpServerExchange exchange, final FormData formData) {
		final Optional<List<String>> newEditors = getHelper().maybeParam(formData, "usernames")//
				.map(Splitter.on(Pattern.compile("[,\\s]+")).omitEmptyStrings()::splitToList)
				.map(ArrayList<String>::new);

		if (newEditors.isPresent() && newEditors.get().stream().allMatch(authenticator::isUsernameValid)) {
			final Dashboard dashboard = getDashboard(exchange);
			final Set<String> oldEditors = new HashSet<>(dashboard.getEditors());

			// change editors
			dashboard.setEditors(newEditors.get());
			getDashboardController().updateDashboard(dashboard);

			logChange(exchange, "Dashboard %s: Set editors to %s", dashboard.getId(), dashboard.getEditors());

			// reload roles
			oldEditors.forEach(authenticator::reloadRoles);
			dashboard.getEditors().forEach(authenticator::reloadRoles);

			// inform users
			final Set<String> usersToInform = new HashSet<>(dashboard.getEditors());
			usersToInform.removeAll(oldEditors);

			if (!newEditors.get().isEmpty()) {
				mailSender.sendPermissionsReceived(getHelper().getPrincipal(exchange).get(),
						usersToInform,
						Roles.DASHBOARD_EDITOR,
						dashboard);
				return okJson(dashboard.getEditors().stream().collect(Collectors.joining(",")));
			}

			return okJson(true);
		}
		return okJson(false);
	}
}
