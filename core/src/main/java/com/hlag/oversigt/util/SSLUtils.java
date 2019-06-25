package com.hlag.oversigt.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.google.common.io.Resources;

public final class SSLUtils {
	private SSLUtils() {}

	public static SSLContext createSSLContext(final SSLConfiguration config) {
		try {
			final KeyStore keyStore = loadKeyStore(Resources.getResource(config.keystore), config.keystorePassword);
			final KeyStore trustStore
					= loadKeyStore(Resources.getResource(config.truststore), config.truststorePassword);
			return createSSLContext(keyStore, config.keystoreEntryPassword, trustStore);
		} catch (final UnrecoverableKeyException
				| KeyManagementException
				| KeyStoreException
				| NoSuchAlgorithmException
				| CertificateException
				| IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static SSLContext createSSLContext(final KeyStore keyStore,
			final String keyEntryPassword,
			final KeyStore trustStore)
			throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
		final KeyManagerFactory keyManagerFactory
				= KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore, keyEntryPassword.toCharArray());
		final KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

		final TrustManagerFactory trustManagerFactory
				= TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(trustStore);
		final TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

		final SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagers, trustManagers, null);
		return sslContext;
	}

	private static SSLContext createSslContext() {
		try {
			final SSLContext sslContext = SSLContext.getInstance("SSL");

			final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
				@Override
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				@Override
				public void checkClientTrusted(final X509Certificate[] certs, final String authType) {}

				@Override
				public void checkServerTrusted(final X509Certificate[] certs, final String authType) {}
			} };
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			return sslContext;
		} catch (final NoSuchAlgorithmException | KeyManagementException e) {
			throw new RuntimeException("Unable to create SSLContext", e);
		}
	}

	private static KeyStore loadKeyStore(final URL url, final String password)
			throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
		try (InputStream stream = new BufferedInputStream(url.openStream())) {
			final KeyStore keystore = KeyStore.getInstance("JKS");
			keystore.load(stream, password.toCharArray());
			return keystore;
		}
	}

	private static final Map<String, SSLContext> SSL_CONTEXTS = Collections.synchronizedMap(new HashMap<>());

	private static SSLContext getSslContext(final String id) {
		return SSL_CONTEXTS.computeIfAbsent(id, x -> createSslContext());
	}

	public static SSLSocketFactory getNonCheckingSSLSocketFactory(final String id) {
		return getSslContext(id).getSocketFactory();
	}

	public static class SSLConfiguration {
		private final String keystore;

		private final String truststore;

		private final String keystorePassword;

		private final String truststorePassword;

		private final String keystoreEntryPassword;

		public SSLConfiguration(final String keystore,
				final String truststore,
				final String keystorePassword,
				final String truststorePassword,
				final String keystoreEntryPassword) {
			this.keystore = keystore;
			this.truststore = truststore;
			this.keystorePassword = keystorePassword;
			this.truststorePassword = truststorePassword;
			this.keystoreEntryPassword = keystoreEntryPassword;
		}

		public SSLContext createSSLContext() {
			return SSLUtils.createSSLContext(this);
		}
	}
}
