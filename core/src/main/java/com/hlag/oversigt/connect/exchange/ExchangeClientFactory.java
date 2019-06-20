package com.hlag.oversigt.connect.exchange;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.ServerConnection;

/**
 * Factory creating the exchange clients hiding the implementation details.
 *
 * @author neumaol
 *
 */
public final class ExchangeClientFactory {
	/** hidden constructor so nobody instantiates this class */
	private ExchangeClientFactory() {
		throw new RuntimeException();
	}

	/**
	 * Create an exchange client using the specified connection details and
	 * credentials
	 *
	 * @param connection  the connection details to use for the exchange server
	 * @param credentials the credentials for logging in to exchange
	 * @return the created exchange client. This method does not return
	 *         <code>null</code>.
	 * @throws MalformedURLException if the server connection contains bad
	 *                               information
	 * @throws URISyntaxException    if the server connection contains bad
	 *                               information
	 */
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

	/**
	 * Create an exchange client using the specified URI and credentials
	 *
	 * @param exchangeServerUri the URI of the exchange server web UI
	 * @param username          the username to use
	 * @param password          the password of the user
	 * @return the created exchange client. This method does not return
	 *         <code>null</code>.
	 */
	public static ExchangeClient createExchangeClient(final URI exchangeServerUri,
			final String username,
			final String password) {
		return new UnlimitedExchangeClient(exchangeServerUri, username, password);
	}
}
