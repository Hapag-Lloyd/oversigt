package com.hlag.oversigt.sources;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

import com.hlag.oversigt.connect.exchange.MailboxInfoRetriever;
import com.hlag.oversigt.core.OversigtEvent;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;
import com.hlag.oversigt.core.eventsource.annotation.Property;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.exception.http.HttpErrorException;
import microsoft.exchange.webservices.data.core.exception.service.remote.ServiceRequestException;
import microsoft.exchange.webservices.data.search.CalendarView;

public abstract class AbstractExchangeEventSource<T extends OversigtEvent> extends ScheduledEventSource<T> {
	public static CalendarView getCalendarView(ZonedDateTime start, ZonedDateTime end) {
		return new CalendarView(Date.from(start.toInstant()), Date.from(end.toInstant()));
	}

	private static boolean isExceptionToIgnore(Exception e) {
		return e instanceof ServiceRequestException //
				&& e.getMessage().equals("The request failed. 40");
	}

	private static boolean isLoginException(Exception e) {
		return e instanceof ServiceRequestException //
				&& e.getCause() != null //
				&& e.getCause() instanceof ServiceRequestException//
				&& e.getCause().getCause() != null//
				&& e.getCause().getCause() instanceof HttpErrorException
				&& "The remote server returned an error: (401)Unauthorized"
						.equals(e.getCause().getCause().getMessage());
	}

	private ServerConnection serverConnection = ServerConnection.EMPTY;
	private Credentials credentials = Credentials.EMPTY;
	private ZoneId zoneId = ZoneId.systemDefault();

	@Property(name = "Exchange Server", description = "The exchange server to connect to.", needsRestart = true)
	public final ServerConnection getServerConnection() {
		return serverConnection;
	}

	public final void setServerConnection(ServerConnection serverConnection) {
		this.serverConnection = serverConnection;
	}

	@Property(name = "Credentials", description = "The credentials to be used when connecting to Exchange.", needsRestart = true)
	public final Credentials getCredentials() {
		return credentials;
	}

	public final void setCredentials(Credentials credentials) {
		this.credentials = credentials;
	}

	@Property(name = "Time Zone", needsRestart = true, description = "The time zone that will be used for displayed dates and times")
	public ZoneId getZoneId() {
		return zoneId;
	}

	public void setZoneId(ZoneId zoneId) {
		this.zoneId = zoneId;
	}

	protected Optional<ExchangeService> createExchangeService() {
		if (getServerConnection() != null //
				&& getServerConnection() != ServerConnection.EMPTY //
				&& getCredentials() != null //
				&& getCredentials() != Credentials.EMPTY) {
			return Optional.of(MailboxInfoRetriever.createService(getServerConnection().getUrl(),
					getCredentials().getUsername(),
					getCredentials().getPassword()));
		} else {
			return Optional.empty();
		}
	}

	@Override
	protected final T produceEvent() throws Exception {
		try {
			return produceExchangeEvent();
		} catch (Exception e) {
			if (isExceptionToIgnore(e)) {
				return null;
			} else if (isLoginException(e)) {
				return failure(String.format("Unable to log in with user '%s' to %s",
						getCredentials() != null ? getCredentials().getUsername() : "",
						getServerConnection() != null ? getServerConnection().getUrl() : ""));
			} else {
				String message = getFailureMessage(e);
				if (message != null) {
					return failure(message, e);
				} else {
					return failure("Unable to produce Exchange event", e);
				}
			}
		}
	}

	protected String getFailureMessage(Exception e) {
		return null;
	}

	protected abstract T produceExchangeEvent() throws Exception;
}
