package com.hlag.oversigt.sources;

import java.time.ZoneId;

import com.hlag.oversigt.connect.exchange.ExchangeClient;
import com.hlag.oversigt.connect.exchange.ExchangeClientFactory;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

import microsoft.exchange.webservices.data.core.exception.http.HttpErrorException;
import microsoft.exchange.webservices.data.core.exception.service.remote.ServiceRequestException;

public abstract class AbstractExchangeEventSource<T extends OversigtEvent> extends ScheduledEventSource<T> {

	private static boolean isLoginException(final Exception e) {
		return e instanceof ServiceRequestException
				&& e.getCause() != null
				&& e.getCause() instanceof ServiceRequestException
				&& e.getCause().getCause() != null
				&& e.getCause().getCause() instanceof HttpErrorException
				&& "The remote server returned an error: (401)Unauthorized"
						.equals(e.getCause().getCause().getMessage());
	}

	private ServerConnection serverConnection = ServerConnection.EMPTY;

	private Credentials credentials = Credentials.EMPTY;

	private ZoneId zoneId = ZoneId.systemDefault();

	private ExchangeClient exchangeClient;

	@Override
	protected void startUp() throws Exception {
		exchangeClient = ExchangeClientFactory.createExchangeClient(getServerConnection(), getCredentials());
		super.startUp();
	}

	@Override
	protected void shutDown() throws Exception {
		exchangeClient.close();
		super.shutDown();
	}

	@Property(name = "Exchange Server", description = "The exchange server to connect to.")
	public final ServerConnection getServerConnection() {
		return serverConnection;
	}

	public final void setServerConnection(final ServerConnection serverConnection) {
		this.serverConnection = serverConnection;
	}

	@Property(name = "Credentials",
			description = "The credentials to be used when connecting to Exchange.",
			needsRestart = true)
	public final Credentials getCredentials() {
		return credentials;
	}

	public final void setCredentials(final Credentials credentials) {
		this.credentials = credentials;
	}

	@Property(name = "Time Zone", description = "The time zone that will be used for displayed dates and times")
	public ZoneId getZoneId() {
		return zoneId;
	}

	public void setZoneId(final ZoneId zoneId) {
		this.zoneId = zoneId;
	}

	protected ExchangeClient getExchangeClient() {
		return exchangeClient;
	}

	@Override
	protected final T produceEvent() throws Exception {
		try {
			return produceExchangeEvent();
		} catch (final Exception e) {
			if (isLoginException(e)) {
				return failure(String.format("Unable to log in with user '%s' to %s",
						getCredentials() != null ? getCredentials().getUsername() : "",
						getServerConnection() != null ? getServerConnection().getUrl() : ""));
			}
			final String message = getFailureMessage(e);
			if (message == null) {
				return failure("Unable to produce Exchange event", e);
			}
			return failure(message, e);
		}
	}

	protected String getFailureMessage(@SuppressWarnings("unused") final Exception e) {
		return null;
	}

	protected abstract T produceExchangeEvent() throws Exception;
}
