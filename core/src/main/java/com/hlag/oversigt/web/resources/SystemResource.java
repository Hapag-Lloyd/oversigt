package com.hlag.oversigt.web.resources;

import static com.hlag.oversigt.web.api.ErrorResponse.notFound;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.ok;

import java.io.IOException;
import java.lang.Thread.State;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.NotBlank;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.base.Strings;
import com.google.common.collect.EvictingQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.controller.DashboardController;
import com.hlag.oversigt.controller.EventSourceInstanceController;
import com.hlag.oversigt.core.Oversigt;
import com.hlag.oversigt.core.configuration.OversigtConfiguration;
import com.hlag.oversigt.core.event.EventSender;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.model.EventSourceInstance;
import com.hlag.oversigt.properties.SerializablePropertyController;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.security.Role;
import com.hlag.oversigt.util.FileUtils;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.MailSender;
import com.hlag.oversigt.web.api.ApiAuthenticationFilter;
import com.hlag.oversigt.web.api.ErrorResponse;
import com.hlag.oversigt.web.api.JwtSecured;
import com.hlag.oversigt.web.api.NoChangeLog;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import de.larssh.utils.Nullables;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

@Api(tags = { "System" })
@Path("/system")
@Singleton
public class SystemResource {
	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SystemResource.class);

	private final Runnable shutdownRunnable;

	@Inject
	private EventSender eventSender;

	@Inject
	private MailSender mailSender;

	@Inject
	private Authenticator authenticator;

	@Inject
	private DashboardController dashboardController;

	@Inject
	private EventSourceInstanceController eventSourceInstanceController;

	@Inject
	private SerializablePropertyController spController;

	@Inject
	private OversigtConfiguration configuration;

	@Inject
	public SystemResource(@Named("Shutdown") final Runnable shutdown) {
		shutdownRunnable = shutdown;
	}

	@POST
	@Path("/shutdown")
	@ApiResponses({
			@ApiResponse(code = 202, message = "Server shutdown initiated."),
			@ApiResponse(code = 401, message = "If the user is not authorized.") })
	@JwtSecured
	@ApiOperation(value = "Shut down the server",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) },
			notes = "Shuts down the dashboard server. Once the request has been accepted the shut down will be initiated after two seconds.")
	@RolesAllowed(Role.ROLE_NAME_SERVER_ADMIN)
	public Response shutdown() {
		ForkJoinPool.commonPool().execute(() -> {
			try {
				Thread.sleep(1000);
			} catch (@SuppressWarnings("unused") final Exception ignore) {
				// on interruption continue
			}
			shutdownRunnable.run();
		});
		return Response.status(Status.ACCEPTED).build();
	}

	@GET
	@Path("/configuration")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Server configuration.", response = OversigtConfiguration.class) })
	@JwtSecured
	@ApiOperation(value = "Read the current server configuration",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_SERVER_ADMIN)
	public Response readConfiguration() {
		final String configJson = JsonUtils.removeKeysFromJson(JsonUtils.toJson(configuration), s -> {
			final String key = s.toLowerCase();
			return !(key.contains("password") || key.contains("secret"));
		});

		return ok(configJson).type(MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("/logfiles")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "A list of available log files will be transferred.",
					response = String.class,
					responseContainer = "list") })
	@JwtSecured
	@ApiOperation(value = "List available log files",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@NoChangeLog
	@RolesAllowed(Role.ROLE_NAME_SERVER_ADMIN)
	public List<String> listLogFiles() throws IOException {
		return FileUtils.closedPathStream(Files.list(Paths.get("log")))
				.map(java.nio.file.Path::getFileName)
				.map(Object::toString)
				.collect(Collectors.toList());
	}

	@GET
	@Path("/logfiles/{filename}")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "The requested lines of the log file will be transferred",
					response = String.class,
					responseContainer = "list") })
	@JwtSecured
	@ApiOperation(value = "Retreive log file content",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@NoChangeLog
	@RolesAllowed(Role.ROLE_NAME_SERVER_ADMIN)
	public Response getLogFileContent(@PathParam("filename") @NotBlank final String filename,
			@QueryParam("lines") @ApiParam(required = false,
					defaultValue = "0",
					value = "Number of lines to read from the log file. Negative value to read from the end of the file.") final Integer nullableLineCount)
			throws IOException {
		final int lineCount = Nullables.orElse(nullableLineCount, 0);

		final java.nio.file.Path logfile = Paths.get("log", filename);
		if (!Files.exists(logfile)) {
			return notFound("The log file '" + filename + "' does not exist.");
		}

		final Collection<String> lines;
		try (Stream<String> lineStream = Files.lines(logfile)) {
			if (lineCount == 0) {
				lines = lineStream.collect(Collectors.toList());
			} else if (lineCount > 0) {
				lines = lineStream.limit(lineCount).collect(Collectors.toList());
			} else {
				lines = lineStream.collect(Collectors.toCollection(() -> EvictingQueue.create(-lineCount)));
			}
		}
		return ok(lines).build();
	}

	@GET
	@Path("/loggers")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "A list of the current loggers",
					response = LoggerInfo.class,
					responseContainer = "list") })
	@JwtSecured
	@ApiOperation(value = "Get a list of the server's loggers",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@NoChangeLog
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public List<LoggerInfo> getLoggers(@QueryParam("configuredLevelsOnly") @ApiParam(required = false,
			defaultValue = "false",
			value = "Whether to filter the logger infos") final boolean onlyConfigured) {
		Predicate<LoggerInfo> filter = l -> true;
		if (onlyConfigured) {
			filter = l -> l.level.isPresent();
		}
		return getLoggerStream().map(LoggerInfo::new).filter(filter).collect(Collectors.toList());
	}

	private Stream<Logger> getLoggerStream() {
		final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		return context.getLoggerList().stream();
	}

	@PUT
	@Path("/loggers/{logger}")
	@ApiResponses({ @ApiResponse(code = 200, message = "Changed the logger's level") })
	@JwtSecured
	@ApiOperation(value = "Change the log level",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_SERVER_ADMIN)
	public Response setLogLevel(@PathParam("logger") @NotBlank final String loggerName,
			@QueryParam("level") @ApiParam(required = true) @NotBlank final String levelName) {

		final Level level = Level.valueOf(levelName);

		final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		final Logger logger = context.getLogger(loggerName);
		logger.setLevel(level);

		return ok().build();
	}

	@GET
	@Path("/log-levels")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "A list of the avaiblable log levels",
					response = String.class,
					responseContainer = "list") })
	@ApiOperation(value = "Get a list of available log levels")
	@PermitAll
	@NoChangeLog
	public String[] getLogLevels() {
		return new String[] { "OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL" };
	}

	@GET
	@Path("/threads")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "A list of the current threads",
					response = ThreadInfo.class,
					responseContainer = "list") })
	@JwtSecured
	@ApiOperation(value = "Get a list the server's threads",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	public List<ThreadInfo> getThreads() {
		return Thread.getAllStackTraces()
				.keySet()
				.stream()
				.sorted((t1, t2) -> String.CASE_INSENSITIVE_ORDER.compare(t1.getName(), t2.getName()))
				.map(ThreadInfo::new)
				.collect(Collectors.toList());
	}

	@GET
	@Path("/cached-events")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "A list of the cached events",
					response = OversigtEvent.class,
					responseContainer = "list"),
			@ApiResponse(code = 404,
					message = "The filtered event source does not exist",
					response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Retrieve the cached events",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	@NoChangeLog
	public Response getCachedEvents(
			@QueryParam("eventSourceId") @ApiParam(value = "Optional filter to get only one cached event",
					required = false) final String filter) {
		final Collection<OversigtEvent> events = new ArrayList<>(eventSender.getCachedEvents());
		if (Strings.isNullOrEmpty(filter)) {
			return ok(events.stream()
					.map(OversigtEvent::toJson)
					.map(json -> JsonUtils.fromJson(json, Map.class))
					.collect(toList())).build();
		}

		final Optional<OversigtEvent> event
				= events.stream().filter(e -> Objects.requireNonNull(e.getId()).equals(filter)).findFirst();
		if (!event.isPresent()) {
			return ErrorResponse.notFound("The event source does not exist", filter);
		}

		final List<OversigtEvent> eventList
				= Arrays.asList(event.orElseThrow(() -> new RuntimeException("The event is not present")));
		final String json = JsonUtils.toJson(eventList);
		return ok(json).build();
	}

	@GET
	@Path("/users/{userId}/validity")
	@ApiResponses({
			@ApiResponse(code = 200,
					message = "A boolean indicating whether or not the user id is valid",
					response = boolean.class) })
	@JwtSecured
	@ApiOperation(value = "Check a userid's validity",
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) })
	@RolesAllowed(Role.ROLE_NAME_GENERAL_DASHBOARD_OWNER)
	@NoChangeLog
	public boolean isUserValid(@PathParam("userId") @ApiParam(value = "The ID of the user to check",
			required = true) final String userId) {
		return authenticator.isUsernameValid(userId);
	}

	@GET
	@Path("/server/info")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Generic information about the server", response = ServerInfo.class) })
	@ApiOperation(value = "Get server information")
	@NoChangeLog
	public ServerInfo getServerInfo() {
		return new ServerInfo();
	}

	@POST
	@Path("/mail")
	@ApiResponses({ @ApiResponse(code = 200, message = "Send mail for test purpose", response = boolean.class) })
	@ApiOperation(value = "Send mail")
	@NoChangeLog
	public boolean sendMail(@Context final SecurityContext securityContext,
			@HeaderParam("x-os-recipient") final String recipient,
			final String content) {
		try {
			mailSender.sendRawMail((Principal) securityContext.getUserPrincipal(), recipient, content);
			return true;
		} catch (final Exception e) {
			LOGGER.error("Unable to send test mail.", e);
			return false;
		}
	}

	@GET
	@Path("/server/all-objects")
	@ApiResponses({ @ApiResponse(code = 200, message = "Search all server objects", response = ServerInfo.class) })
	@ApiOperation(value = "Server wide search for objects")
	@NoChangeLog
	public Response searchAllObjects(@QueryParam("query") @ApiParam(value = "Text to search for",
			required = true) final String rawSearchString) {
		if (Strings.nullToEmpty(rawSearchString).trim().length() == 0) {
			return ErrorResponse.badRequest("The search text must not be empty.");
		}

		// prepare search
		final String searchString = rawSearchString.toLowerCase();
		final List<SearchResult> results = new ArrayList<>();

		// TODO implement dynamic search registration

		// search for dashboards
		results.addAll(dashboardController.getDashboardIds()
				.stream()
				.map(dashboardController::getDashboard)
				.map(Optional::get)
				.filter(d -> d.getTitle().toLowerCase().contains(searchString)
						|| d.getId().toLowerCase().contains(searchString))
				.map(d -> new SearchResult(d.getTitle(), d.getId(), "dashboard"))
				.collect(toList()));

		// search for event sources
		results.addAll(eventSourceInstanceController.getEventSourceInstances()
				.stream()
				.filter(EventSourceInstance.createFilter(searchString))
				.map(i -> new SearchResult(i.getName(), i.getId(), "event-source"))
				.collect(toList()));

		// search for properties
		spController.getClasses()
				.stream()
				.flatMap(spController::streamProperties)
				.filter(p -> SerializablePropertyResource.toMapWithoutPassword(p)
						.values()
						.stream()
						.filter(v -> v != null)
						.map(Object::toString)
						.map(String::toLowerCase)
						.anyMatch(s -> s.contains(searchString)))
				.map(p -> new SearchResult(p.getName(),
						Integer.toString(p.getId()),
						"serializable-property",
						p.getClass().getSimpleName()))
				.collect(Collectors.toList());

		// search for loggers
		results.addAll(getLoggerStream().map(Logger::getName)
				.map(String::toLowerCase)
				.filter(name -> name.contains(searchString))
				.map(name -> new SearchResult(name, name, "logger"))
				.collect(toList()));

		return ok(results).build();
	}

	public static class LoggerInfo {
		private final String name;

		private final Optional<Level> level;

		private final Level effectiveLevel;

		public LoggerInfo(final Logger logger) {
			name = logger.getName();
			level = Optional.ofNullable(logger.getLevel());
			effectiveLevel = logger.getEffectiveLevel();
		}

		public String getName() {
			return name;
		}

		public String getLevel() {
			return level.map(Object::toString).orElse(null);
		}

		public String getEffectiveLevel() {
			return effectiveLevel.toString();
		}
	}

	public static class ThreadInfo {
		private final String name;

		private final long id;

		private final int priority;

		private final State state;

		private final StackTraceElement[] stackTrace;

		public ThreadInfo(final Thread thread) {
			name = thread.getName();
			id = thread.getId();
			priority = thread.getPriority();
			state = thread.getState();
			stackTrace = thread.getStackTrace();
		}

		public String getName() {
			return name;
		}

		public long getId() {
			return id;
		}

		public int getPriority() {
			return priority;
		}

		public State getState() {
			return state;
		}

		public StackTraceElement[] getStackTrace() {
			return stackTrace;
		}
	}

	public static class ServerInfo {
		private final String name = Oversigt.APPLICATION_NAME;

		private final String version = Oversigt.APPLICATION_VERSION;

		public ServerInfo() {
			// no fields to be initialized
		}

		public String getName() {
			return name;
		}

		public String getVersion() {
			return version;
		}
	}

	@JsonInclude(content = Include.NON_NULL)
	public static class SearchResult {
		private final String title;

		private final String id;

		private final String type;

		private final Optional<String> subtype;

		public SearchResult(final String title, final String id, final String type) {
			this.title = title;
			this.id = id;
			this.type = type;
			subtype = Optional.empty();
		}

		public SearchResult(final String title, final String id, final String type, final String subtype) {
			this.title = title;
			this.id = id;
			this.type = type;
			this.subtype = Optional.of(subtype);
		}

		public String getTitle() {
			return title;
		}

		public String getId() {
			return id;
		}

		public String getType() {
			return type;
		}

		public Optional<String> getSubtype() {
			return subtype;
		}
	}
}
