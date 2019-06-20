package com.hlag.oversigt.connect.exchange;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

public class ExchangeClientFactory {
	public static ExchangeClient createExchangeClient(final ServerConnection connection, final Credentials credentials)
			throws MalformedURLException, URISyntaxException {
		if (connection == ServerConnection.EMPTY) {
			throw new RuntimeException("No server connection configured.");
		}
		if (credentials == Credentials.EMPTY) {
			throw new RuntimeException("No credentials configured.");
		}
		return createExchangeClient(new URL(connection.getUrl()).toURI(),
				credentials.getUsername(),
				credentials.getPassword());
	}

	public static ExchangeClient createExchangeClient(final URI exchangeServerUri,
			final String username,
			final String password) {
		return new UnlimitedExchangeClient(exchangeServerUri, username, password);
	}
}
