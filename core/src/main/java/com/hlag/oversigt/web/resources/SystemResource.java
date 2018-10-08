package com.hlag.oversigt.web.resources;

import static com.hlag.oversigt.web.api.ErrorResponse.notFound;
import static javax.ws.rs.core.Response.ok;

import java.io.IOException;
import java.lang.Thread.State;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.NotBlank;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.EvictingQueue;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hlag.oversigt.core.OversigtEvent;
import com.hlag.oversigt.core.OversigtEventSender;
import com.hlag.oversigt.security.Role;
import com.hlag.oversigt.util.FileUtils;
import com.hlag.oversigt.web.api.ApiAuthenticationFilter;
import com.hlag.oversigt.web.api.ErrorResponse;
import com.hlag.oversigt.web.api.JwtSecured;
import com.hlag.oversigt.web.api.NoChangeLog;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

@Api(tags = { "System" })
@Path("/system")
@RolesAllowed(Role.ROLE_NAME_SERVER_ADMIN)
public class SystemResource {
	private final Runnable shutdownRunnable;

	@Inject
	private OversigtEventSender eventSender;

	@Inject
	public SystemResource(@Named("Shutdown") Runnable shutdown) {
		this.shutdownRunnable = shutdown;
	}

	@POST
	@Path("/shutdown")
	@ApiResponses({ @ApiResponse(code = 202, message = "Server shutdown initiated."),
			@ApiResponse(code = 401, message = "If the user is not authorized.") })
	@JwtSecured
	@ApiOperation(value = "Shut down the server", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) }, //
			notes = "Shuts down the dashboard server. Once the request has been accepted the shut down will be initiated after two seconds.")
	public Response shutdown() {
		ForkJoinPool.commonPool().execute(() -> {
			try {
				Thread.sleep(1000);
			} catch (Exception ignore) {
			}
			shutdownRunnable.run();
		});
		return Response.status(Status.ACCEPTED).build();
	}

	@GET
	@Path("/logfiles")
	@ApiResponses({ @ApiResponse(code = 200, message = "A list of available log files will be transferred.") })
	@JwtSecured
	@ApiOperation(value = "List available log files", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) } //
	)
	@NoChangeLog
	public List<String> listLogFiles() throws IOException {
		return FileUtils.closedPathStream(Files.list(Paths.get("log")))//
				.map(java.nio.file.Path::getFileName)
				.map(Object::toString)
				.collect(Collectors.toList());
	}

	@GET
	@Path("/logfiles/{filename}")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "A list of available log files will be transferred", response = String.class, responseContainer = "list") })
	@JwtSecured
	@ApiOperation(value = "List available log files", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) } //
	)
	@NoChangeLog
	public Response getLogFileContent(@PathParam("filename") @NotBlank String filename,
			@QueryParam("lines") @ApiParam(required = false, defaultValue = "0", value = "Number of lines to read from the log file. Negative value to read from the end of the file.") Integer lineCount)
			throws IOException {
		if (lineCount == null) {
			lineCount = 0;
		}

		java.nio.file.Path logfile = Paths.get("log", filename);
		if (Files.exists(logfile)) {
			Collection<String> lines;

			try (Stream<String> lineStream = Files.lines(logfile)) {
				if (lineCount == 0) {
					lines = lineStream.collect(Collectors.toList());
				} else if (lineCount > 0) {
					lines = lineStream.limit(lineCount).collect(Collectors.toList());
				} else {
					int iLineCount = -lineCount.intValue();
					lines = lineStream.collect(Collectors.toCollection(() -> EvictingQueue.create(iLineCount)));
				}
			}

			return ok(lines).build();
		} else {
			return notFound("The log file '" + filename + "' does not exist.");
		}
	}

	@GET
	@Path("/loggers")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "A list of the current loggers", response = LoggerInfo.class, responseContainer = "list") })
	@JwtSecured
	@ApiOperation(value = "Get a list of the server's loggers", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) } //
	)
	@NoChangeLog
	public List<LoggerInfo> getLoggers(
			@QueryParam("configuredLevelsOnly") @ApiParam(required = false, defaultValue = "false", value = "Whether to filter the logger infos") boolean onlyConfigured) {
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		Predicate<LoggerInfo> filter = l -> true;
		if (onlyConfigured) {
			filter = l -> l.level != null;
		}
		return context.getLoggerList()//
				.stream()
				.map(LoggerInfo::new)
				.filter(filter)
				.collect(Collectors.toList());
	}

	@PUT
	@Path("/loggers/{logger}")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "Changed the logger's level") })
	@JwtSecured
	@ApiOperation(value = "Change the log level", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) } //
	)
	public Response setLogLevel(@PathParam("logger") @NotBlank String loggerName,
			@QueryParam("level") @ApiParam(required = true) @NotBlank String levelName) {

		Level level = Level.valueOf(levelName);

		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger logger = context.getLogger(loggerName);
		logger.setLevel(level);

		return ok().build();
	}

	@GET
	@Path("/log-levels")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "A list of the avaiblable log levels", response = String.class, responseContainer = "list") })
	@ApiOperation(value = "Get a list of available log levels")
	@PermitAll
	@NoChangeLog
	public String[] getLogLevels() {
		return new String[] { "OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL" };
	}

	@GET
	@Path("/threads")
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "A list of the current threads", response = ThreadInfo.class, responseContainer = "list") })
	@JwtSecured
	@ApiOperation(value = "Get a list the server's threads", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) } //
	)
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
	@ApiResponses({ //
			@ApiResponse(code = 200, message = "A list of the cached events", response = OversigtEvent.class, responseContainer = "list"),
			@ApiResponse(code = 404, message = "The filtered event source does not exist", response = ErrorResponse.class) })
	@JwtSecured
	@ApiOperation(value = "Retrieve the cached events", //
			authorizations = { @Authorization(value = ApiAuthenticationFilter.API_OPERATION_AUTHENTICATION) } //
	)
	@RolesAllowed(Role.ROLE_NAME_SERVER_ADMIN)
	@NoChangeLog
	public Response getCachedEvents(
			@QueryParam("eventSourceId") @ApiParam(value = "Optional filter to get only one cached event", required = false) String filter) {
		Collection<OversigtEvent> events = new ArrayList<>(eventSender.getCachedEvents());
		if (Strings.isNullOrEmpty(filter)) {
			return ok(events).build();
		} else {
			Optional<OversigtEvent> event = events.stream().filter(e -> e.getId().equals(filter)).findFirst();
			if (event.isPresent()) {
				return ok(event.map(Arrays::asList).get()).build();
			} else {
				return ErrorResponse.notFound("The event source does not exist", filter);
			}
		}

	}

	public static class LoggerInfo {
		private final String name;
		private final Level level;
		private final Level effectiveLevel;

		public LoggerInfo(Logger logger) {
			this.name = logger.getName();
			this.level = logger.getLevel();
			this.effectiveLevel = logger.getEffectiveLevel();
		}

		public String getName() {
			return name;
		}

		public String getLevel() {
			return Optional.ofNullable(level).map(Object::toString).orElse(null);
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

		public ThreadInfo(Thread thread) {
			this.name = thread.getName();
			this.id = thread.getId();
			this.priority = thread.getPriority();
			this.state = thread.getState();
			this.stackTrace = thread.getStackTrace();
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

}
