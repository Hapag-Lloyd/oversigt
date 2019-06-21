package com.hlag.oversigt.web;

import static com.hlag.oversigt.util.Utils.map;
import static com.hlag.oversigt.web.ActionResponse.ok;
import static com.hlag.oversigt.web.ActionResponse.okJson;
import static com.hlag.oversigt.web.ActionResponse.redirect;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.core.event.EventSender;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.model.EventSourceDescriptor;
import com.hlag.oversigt.model.EventSourceInstance;
import com.hlag.oversigt.model.EventSourceKey;
import com.hlag.oversigt.model.EventSourceProperty;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.properties.SerializablePropertyController;
import com.hlag.oversigt.security.NeedsRole;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.security.Roles;
import com.hlag.oversigt.util.ImageUtil;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.TypeUtils.SerializablePropertyMember;
import com.hlag.oversigt.util.TypeUtils.SerializablePropertyMember.MemberMissingException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;

@Singleton
public class EventSourceConfigurationHandler extends AbstractConfigurationHandler {

	private static final String PATH = "views/layout/eventSourceConfig/";

	private final Runnable shutdown;

	@Inject
	private EventSender eventSender;

	@Inject
	private JsonUtils json;

	@Inject
	private SerializablePropertyController spController;

	@Inject
	public EventSourceConfigurationHandler(final DashboardController dashboardController,
			final HttpServerExchangeHandler exchangeHelper,
			@Named("Shutdown") final Runnable shutdown) {
		super(dashboardController,
				exchangeHelper,
				PATH,
				new String[] {
						"page_1_createEventSource.ftl.html",
						"page_configureEventSource.ftl.html",
						"page_dashboards.ftl.html",
						"page_properties.ftl.html",
						"page_settings.ftl.html",
						"page_troubleshooting.ftl.html" });
		this.shutdown = shutdown;
	}

	@Override
	protected String getPreferredInitialPage() {
		return "configureEventSource";
	}

	@Override
	protected String getTemplateName(final HttpServerExchange exchange, final PageInfo pi) {
		if ("configureEventSource".equals(pi.getName())) {
			if (getHelper().query(exchange, "eventSource").isPresent()) {
				return PATH + "eventSourceInstance.ftl.html";
			} else if (getHelper().query(exchange, "action").map("search"::equals).orElse(false)) {
				return PATH + "search.ftl.json";
			}
		}
		return super.getTemplateName(exchange, pi);
	}

	@Override
	protected String getContentType(final HttpServerExchange exchange, final PageInfo pi) {
		if ("configureEventSource".equals(pi.getName())) {
			if (getHelper().query(exchange, "action").map("search"::equals).orElse(false)) {
				return "application/json";
			}
		}
		return super.getContentType(exchange, pi);
	}

	@SuppressWarnings("deprecation")
	@Override
	protected Map<String, Object> getModel(final HttpServerExchange exchange, final String page) {
		switch (page) {
		case "dashboards":
			return map("dashboards",
					getDashboardController().getDashboardIds()//
							.stream()
							.map(getDashboardController()::getDashboard)
							.sorted(Comparator.comparing(Dashboard::getTitle, String.CASE_INSENSITIVE_ORDER))
							.collect(Collectors.toList()));
		case "createEventSource":
			return map("availableEventSourceKeys",
					getDashboardController()//
							.getEventSourceKeys()
							.stream()
							.sorted(EventSourceKey.COMPARATOR)
							.collect(toList()),
					"dashboardManager",
					getDashboardController(),
					"preview",
					(Function<EventSourceKey, String>) k -> ImageUtil
							.getPreviewImageUrl(getDashboardController().getEventSourceDescriptor(k)));
		case "configureEventSource":
			if (getHelper().query(exchange, "action").map("search"::equals).orElse(false)) {
				return map("json",
						json.toJson(getDashboardController()//
								.getEventSourceInstances()//
								.stream()//
								.collect(Collectors.toMap(EventSourceInstance::getId, instance -> {
									return Stream.of(instance.getId(),
											instance.getName(),
											instance.getDescriptor().getServiceClass().map(Class::getName).orElse(""),
											instance.getDescriptor().getView(),
											instance.getDescriptor()
													.getProperties()
													.stream()
													.map(instance::getPropertyValueString)
													.collect(Collectors.joining(" ")),
											instance.getDescriptor()
													.getDataItems()
													.stream()
													.map(instance::getPropertyValueString)
													.collect(Collectors.joining(" "))// ,
									// TODO SerializableProperty hier mit reinbringen...
									).collect(Collectors.joining(" "));
								}))));
			}
			return map("eventSourceInstances",
					getDashboardController().getEventSourceInstances()
							.stream()
							.sorted(EventSourceInstance.COMPARATOR)
							.collect(toList()),
					"eventSourceInstance",
					getHelper().query(exchange, "eventSource")
							.map(getDashboardController()::getEventSourceInstance)
							.orElse(null),
					"dashboardManager",
					getDashboardController(),
					"values",
					spController.getProperties());
		case "troubleshooting":
			final int lineCount = getHelper().query(exchange, "lines", Integer::parseInt, 250);
			return map("linesCount",
					lineCount,
					"logFileContent",
					getLastLogLines(lineCount),
					"threads",
					getThreads(),
					"lastEvents",
					getCurrentEvents(),
					"json",
					json,
					"loggers",
					getLoggers(),
					"logLevels",
					new String[] { "OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL" });
		case "properties":
			return map("typeNames",
					spController.getClasses().stream().map(Class::getSimpleName).collect(Collectors.toList()),
					"values",
					spController.getProperties(),
					"members",
					spController.getAllMembers());
		default:
			return null;
		}
	}

	private List<Thread> getThreads() {
		final Map<Thread, StackTraceElement[]> sts = Thread.getAllStackTraces();
		return sts.entrySet()
				.stream()
				.filter(e -> e.getValue().length > 0)
				.map(Entry::getKey)
				.sorted((t1, t2) -> String.CASE_INSENSITIVE_ORDER.compare(t1.getName(), t2.getName()))
				.collect(Collectors.toList());
	}

	private Collection<OversigtEvent> getCurrentEvents() {
		return eventSender.getCachedEvents().stream().sorted().collect(Collectors.toList());
	}

	private String getLastLogLines(final int amount) {
		final Path logFilePath = Paths.get("log", "oversigt.log");

		try (ReversedLinesFileReader rlfr = new ReversedLinesFileReader(logFilePath.toFile(), 4096, Charsets.UTF_8)) {
			final Deque<String> lines = new LinkedList<>();
			String line;
			while ((line = rlfr.readLine()) != null) {
				lines.offerFirst(line);
				if (lines.size() >= amount) {
					break;
				}
			}
			return String.join("\r\n", lines);
		} catch (final IOException e) {
			return Throwables.getStackTraceAsString(e);
		}
	}

	private static Collection<Logger> getLoggers() {
		final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		return context.getLoggerList();
	}

	@NeedsRole(role = Roles.DASHBOARD_OWNER)
	protected ActionResponse doAction_newEventSourceInstance(final HttpServerExchange exchange, final FormData data) {
		final String eventSourceKey = getHelper().param(data, "eventSourceName");
		final String instanceId
				= getDashboardController()
						.createEventSourceInstance(
								getDashboardController()
										.getEventSourceDescriptor(EventSourceKey.getKey(eventSourceKey)),
								getHelper().getPrincipal(exchange).get())
						.getId();
		logChange(exchange, "Create event source name[%s] id[%s]", eventSourceKey, instanceId);
		return redirect("configureEventSource#" + instanceId);
	}

	@NeedsRole(role = Roles.DASHBOARD_OWNER)
	protected ActionResponse doAction_configureInstance(final HttpServerExchange exchange, final FormData data) {
		final Principal principal = getHelper().getPrincipal(exchange).get();

		final String eventSourceId = getHelper().param(data, "id");
		final String name = getHelper().param(data, "name");
		final Duration frequency = getHelper().maybeParam(data, "frequency")//
				.map(Duration::parse)//
				.orElse(null);

		final EventSourceInstance originalInstance = getDashboardController().getEventSourceInstance(eventSourceId);
		final EventSourceDescriptor descriptor = originalInstance.getDescriptor();
		final EventSourceInstance instance = new EventSourceInstance(descriptor,
				eventSourceId,
				name,
				getDashboardController().getEventSourceInstance(eventSourceId).isEnabled(),
				frequency,
				originalInstance.getCreatedBy(),
				principal.getUsername());

		// read Properties
		for (final EventSourceProperty p : descriptor.getProperties()) {
			Optional<String> maybeParam = getHelper().maybeParam(data, eventSourceId + ".property." + p.getName());
			if ((p.getClazz() == Boolean.class || p.getClazz() == boolean.class) && !maybeParam.isPresent()) {
				maybeParam = Optional.of("false");
			}
			final String value = maybeParam.get();
			instance.setPropertyString(p, value);
		}
		// read DataItems
		for (final EventSourceProperty p : descriptor.getDataItems()) {
			final boolean enabled = getHelper().maybeParam(data, "enable." + eventSourceId + ".data." + p.getName())//
					.map(Boolean::parseBoolean)//
					.orElse(false);
			if (enabled) {
				final String value = getHelper().maybeParam(data, eventSourceId + ".data." + p.getName()).get();
				instance.setPropertyString(p, value);
			}
		}

		// update event source
		getDashboardController().updateEventSourceInstance(instance);
		logChange(exchange,
				"Configure event source instance id[%s] name[%s] frequency[%s] properties[%s] datas[%s]",
				eventSourceId,
				name,
				frequency,
				instance.getDescriptor()
						.getProperties()
						.stream()
						.collect(Collectors.toMap(Function.identity(), p -> instance.getPropertyValueString(p))),
				instance.getDescriptor()
						.getDataItems()
						.stream()
						.collect(Collectors.toMap(Function.identity(), p -> instance.getPropertyValueString(p))));
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_OWNER)
	protected ActionResponse doAction_deleteInstance(final HttpServerExchange exchange, final FormData data) {
		final String eventSourceId = getHelper().param(data, "id");
		final Collection<String> preventingDashboards
				= getDashboardController().deleteEventSourceInstance(eventSourceId);
		if (preventingDashboards == null || preventingDashboards.isEmpty()) {
			logChange(exchange, "Delete event source id[%s]", eventSourceId);
			return redirect(exchange.getRequestURI());
		}
		logChange(exchange,
				"Try to delete event source id[%s], preventing dashboards: %s",
				eventSourceId,
				preventingDashboards);
		return okJson(preventingDashboards);
	}

	@NeedsRole(role = Roles.DASHBOARD_OWNER)
	protected void doAction_restartInstance(final HttpServerExchange exchange, final FormData data) {
		final String id = getHelper().param(data, "id");
		getDashboardController().restartInstance(id, true);
		logChange(exchange, "Restart event source id[%s]", id);
	}

	@NeedsRole(role = Roles.DASHBOARD_OWNER)
	protected ActionResponse doAction_disableInstance(final HttpServerExchange exchange, final FormData data) {
		final String id = getHelper().param(data, "id");
		getDashboardController().disableEventSourceInstance(id);
		logChange(exchange, "Disable event source id[%s]", id);
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_OWNER)
	protected ActionResponse doAction_enableInstance(final HttpServerExchange exchange, final FormData data) {
		final String id = getHelper().param(data, "id");
		getDashboardController().enableEventSourceInstance(id);
		logChange(exchange, "Enable event source id[%s]", id);
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_OWNER)
	protected ActionResponse doAction_stopEventSource(final HttpServerExchange exchange, final FormData data) {
		final String id = getHelper().param(data, "id");
		logChange(exchange, "Stop event source id[%s]", id);
		getDashboardController().stopInstance(id);
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_OWNER)
	protected ActionResponse doAction_startEventSource(final HttpServerExchange exchange, final FormData data) {
		final String id = getHelper().param(data, "id");
		getDashboardController().startInstance(id);
		logChange(exchange, "Start event source id[%s]", id);
		return ok();
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_stopAllEventSources(final HttpServerExchange exchange, final FormData formData) {
		logChange(exchange, "Stop all event sources");
		getDashboardController().stopAllInstances();
		return ok();
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_startAllEventSources(final HttpServerExchange exchange, final FormData formData) {
		logChange(exchange, "Start all event sources");
		getDashboardController().startAllInstances();
		return ok();
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_reloadDashboard(final HttpServerExchange exchange, final FormData formData) {
		final String id = getHelper().param(formData, "dashboardId");
		getDashboardController().reloadDashboards(getDashboardController().getDashboard(id));
		return ok();
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_deleteDashboard(final HttpServerExchange exchange, final FormData formData) {
		final String id = getHelper().param(formData, "dashboardId");
		getDashboardController().deleteDashboard(id);
		logChange(exchange, "Delete dashboard id[%s]", id);
		return ok();
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_shutdown(final HttpServerExchange exchange, final FormData formData) {
		logChange(exchange, "Shutdown");
		ForkJoinPool.commonPool().execute(() -> {
			try {
				Thread.sleep(1000);
			} catch (final Exception ignore) {}
			shutdown.run();
		});
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_OWNER)
	protected ActionResponse doAction_createValue(final HttpServerExchange exchange, final FormData formData) {
		final String type = getHelper().param(formData, "type");

		final Class<? extends SerializableProperty> spClass = spController.getClass(type);

		final Map<SerializablePropertyMember, Object> values = new LinkedHashMap<>();
		for (final SerializablePropertyMember member : spController.getMembers(spClass)) {
			try {
				values.put(member,
						member.createInstance(getHelper().param(formData, "create-" + type + "-" + member.getName())));
			} catch (final MemberMissingException e) {
				throw new RuntimeException("The member '"
						+ member.getDisplayName()
						+ "' has not been set, but it is mandatory.\nNo updates have been done.");
			}
		}
		final ArrayList<Object> parameters = new ArrayList<>(values.values());
		final String name = (String) parameters.remove(0);
		final SerializableProperty value = spController.createProperty(spClass, name, parameters);
		logChange(exchange, "Create property: %s", value);
		return ok();
	}

	@NeedsRole(role = Roles.DASHBOARD_OWNER)
	protected ActionResponse doAction_editValue(final HttpServerExchange exchange, final FormData formData) {
		final String type = getHelper().param(formData, "type");
		final int id = Integer.parseInt(getHelper().param(formData, "id"));

		final Class<? extends SerializableProperty> spClass = spController.getClass(type);
		final SerializableProperty property = spController.getProperty(spClass, id);
		final SerializableProperty clone = spController.clone(property);

		for (final SerializablePropertyMember member : spController.getMembers(spClass)) {
			try {
				member.set(clone, getHelper().param(formData, "edit-" + type + "-" + member.getName()));
			} catch (final MemberMissingException e) {
				throw new RuntimeException("The member '"
						+ member.getDisplayName()
						+ "' has not been set, but it is mandatory.\nNo updates have been done.");
			}
		}

		spController.updateProperty(clone);

		// restart event sources using this value
		getDashboardController().restartInstancesUsingSerializableProperty(property);

		logChange(exchange, "Edit property: %s", property);
		return ok();
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_deleteValue(final HttpServerExchange exchange, final FormData formData) {
		final String type = getHelper().param(formData, "type");
		final int id = Integer.parseInt(getHelper().param(formData, "id"));

		final Class<? extends SerializableProperty> spClass = spController.getClass(type);

		spController.deleteProperty(spClass, id);
		logChange(exchange, "Delete property id[%s]", id);
		return ok();
	}

	@NeedsRole(role = Roles.ADMIN)
	protected ActionResponse doAction_changeLogLevel(final HttpServerExchange exchange, final FormData formData) {
		final String loggerName = getHelper().param(formData, "logger");
		final Level level = Level.valueOf(getHelper().param(formData, "level"));

		final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		final Logger logger = context.getLogger(loggerName);
		logger.setLevel(level);

		logChange(exchange, "Set logger[%s] to level[%s]", logger.getName(), level);
		return ok();
	}
}
