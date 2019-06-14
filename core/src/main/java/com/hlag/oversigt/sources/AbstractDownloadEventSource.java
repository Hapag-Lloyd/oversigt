package com.hlag.oversigt.sources;

import static com.hlag.oversigt.util.Utils.logDebug;
import static com.hlag.oversigt.util.Utils.logInfo;
import static com.hlag.oversigt.util.Utils.logTrace;
import static com.hlag.oversigt.util.Utils.logWarn;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.HttpProxy;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;
import com.hlag.oversigt.util.SSLUtils;
import com.hlag.oversigt.util.ThrowingBiFunction;
import com.hlag.oversigt.util.Tuple;
import com.hlag.oversigt.util.text.TextProcessor;

import de.larssh.utils.text.StringConverters;

public abstract class AbstractDownloadEventSource<T extends OversigtEvent> extends AbstractSslAwareEventSource<T> {
	private static final Pattern PATTERN_URL_MATCHER_REPLACEMENT = Pattern.compile("\\$\\{([0-9]+)\\.([0-9]+)\\}");

	private static final TextProcessor textProcessor = TextProcessor.create().registerDatetimeFunctions();

	private HttpProxy proxy = HttpProxy.EMPTY;

	private InternetAddress[] urls = new InternetAddress[] {
			new InternetAddress("http://dilbert.com",
					"<img[^>]*src=\"(?:https?:)?(//assets\\.amuniversal\\.com/[a-zA-Z0-9]+)\"[^>]*/?>"),
			new InternetAddress("https:${1.1}", "") };

	private HttpHeader[] httpHeaders = new HttpHeader[0];

	private Credentials credentials = Credentials.EMPTY;

	@Property(name = "Web Proxy", description = "The proxy server to be used to contact to the internet.")
	public HttpProxy getHttpProxy() {
		return proxy;
	}

	public void setHttpProxy(final HttpProxy proxy) {
		this.proxy = proxy;
	}

	private URLConnection createConnection(final String urlString, final String cookie, final LoginData[] loginData)
			throws IOException {
		final URL url = new URL(urlString);
		final URLConnection con = url.openConnection(getHttpProxy().getProxy());
		con.setConnectTimeout(30 * 1000);
		con.setReadTimeout(30 * 1000);
		con.setRequestProperty("User-Agent",
				"Mozilla/5.0 Java/" + System.getProperty("java.version") + " Oversigt/0.9");

		if (con instanceof HttpsURLConnection && !isCheckSSL()) {
			((HttpsURLConnection) con).setSSLSocketFactory(SSLUtils.getNonCheckingSSLSocketFactory(getEventId()));
			((HttpsURLConnection) con).setHostnameVerifier((hostname, session) -> true);
		}

		if (con instanceof HttpURLConnection) {
			for (final HttpHeader httpHeader : getHttpHeaders()) {
				con.setRequestProperty(httpHeader.getName(), httpHeader.getValue());
			}
		}

		if (cookie != null) {
			con.setRequestProperty("Cookie", cookie);
		} else if ((loginData == null || loginData.length == 0)
				&& getCredentials() != null
				&& getCredentials() != Credentials.EMPTY) {
					final String encoded = Base64.getEncoder()
							.encodeToString((getCredentials().getUsername() + ":" + getCredentials().getPassword())
									.getBytes(StandardCharsets.UTF_8));
					con.setRequestProperty("Authorization", "Basic " + encoded);
				}

		if (con instanceof HttpURLConnection && loginData != null && loginData.length > 0) {
			final HttpURLConnection hcon = (HttpURLConnection) con;
			hcon.setDoOutput(true);
			hcon.setInstanceFollowRedirects(false);
			hcon.setRequestMethod("POST");
			hcon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			hcon.setUseCaches(false);
			try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(hcon.getOutputStream()))) {
				final List<String> parts = new ArrayList<>();
				for (final LoginData data : loginData) {
					parts.add(data.getName());
					parts.add(data.getValue()
							.replace("${domain}", getCredentials().getDomain())
							.replace("${username}", getCredentials().getUsername())
							.replace("${password}", getCredentials().getPassword()));
				}
				final String string = createXWwwUrlEncoded(parts.toArray(new String[0]));
				writer.append(string);
			}
		}

		return con;
	}

	protected URLConnection createConnection(final String urlString) throws IOException {
		return createConnection(Arrays.asList(InternetAddress.fromUrl(new URL(urlString))));
	}

	protected URLConnection createConnection(final URL url) throws IOException {
		return createConnection(Arrays.asList(InternetAddress.fromUrl(url)));
	}

	protected URLConnection createConnection(final List<InternetAddress> addresses) throws IOException {
		final List<List<String>> replacements = new ArrayList<>();
		URLConnection connection = null;
		String cookie = null;

		for (int i = 0; i < addresses.size(); i += 1) {
			final InternetAddress internetAddress = addresses.get(i);
			if (connection != null) {
				logDebug(getLogger(), "Reading cookie from old connection");
				final String newCookie = connection.getHeaderField("Set-Cookie");
				logTrace(getLogger(), "New Cookie: %s", cookie);
				if (newCookie != null) {
					cookie = newCookie;
				}

				logDebug(getLogger(), "Closing connection");
				connection.getInputStream().close();
				connection = null;
			}

			String url = internetAddress.getUrlString();
			logTrace(getLogger(), "Matching URL: %s", url);
			final Matcher repmat = PATTERN_URL_MATCHER_REPLACEMENT.matcher(url);
			while (repmat.find()) {
				final int major = Integer.parseInt(repmat.group(1));
				final int minor = Integer.parseInt(repmat.group(2));
				if (replacements.size() < major || replacements.get(major - 1).size() < minor) {
					throw new RuntimeException("Unable to find replacement ${" + major + "." + minor + "}");
				}
				if (replacements.get(major - 1) != null && replacements.get(major - 1).get(minor - 1) != null) {
					final String search = "${" + major + "." + minor + "}";
					final String replacement = replacements.get(major - 1).get(minor - 1);
					url = url.replace(search, replacement);
					logTrace(getLogger(), "Replacing %s with %s to %s", search, replacement, url);
				}
			}
			logInfo(getLogger(), "Connecting to URL: %s", url);
			if (url != null) {
				url = textProcessor.process(url);
				connection = createConnection(url, cookie, internetAddress.loginDatas);
				final Pattern pattern = internetAddress.getPattern();
				if (pattern != null) {
					final List<String> reps = new ArrayList<>();
					logInfo(getLogger(), "Downloading content for matcher: %s", pattern.toString());
					final String content = downloadString(connection);
					final Matcher matcher = pattern.matcher(content);
					if (matcher.find()) {
						for (int j = 1; j <= matcher.groupCount(); j += 1) {
							reps.add(matcher.group(j));
						}
					}
					logDebug(getLogger(), "Adding replacements: %s", (Supplier<String>) () -> reps.toString());
					replacements.add(reps);

					if (i == addresses.size() - 1 && replacements.size() == 1) {
						if (reps.isEmpty()) {
							throw new RuntimeException("Pattern did not match. Unable to call next URL.");
						}
						addresses.add(new InternetAddress(reps.get(0), null));
					}
				}
			}
		}
		return connection;
	}

	protected URLConnection createConfiguredConnection() throws IOException {
		return createConnection(new ArrayList<>(Arrays.asList(getUrls())));
	}

	protected Tuple<byte[], String> downloadBytes(final URLConnection connectionToRead) throws IOException {
		return read(connectionToRead, //
				(connection, inputStream) -> //
				new Tuple<>(ByteStreams.toByteArray(inputStream), connection.getContentType()));
	}

	protected String downloadString(final URLConnection connectionToRead) throws IOException {
		return read(connectionToRead, //
				(connection, inputStream) -> //
				IOUtils.toString(inputStream, Objects.toString(connection.getContentEncoding(), "UTF-8")));
	}

	private <R> R read(final URLConnection inputConnection,
			final ThrowingBiFunction<URLConnection, InputStream, R> inputStreamConsumer) throws IOException {
		final URLConnection connectionToRead = handleRedirects(inputConnection);
		try (InputStream in = connectionToRead.getInputStream()) {
			return inputStreamConsumer.apply(connectionToRead, in);
		} catch (final IOException e) {
			throw e;
		} catch (final Exception e) {
			throw new RuntimeException("Error while reading data from connection.", e);
		}
	}

	private URLConnection handleRedirects(final URLConnection connection) throws IOException {
		if (connection instanceof HttpURLConnection) {
			return handleRedirects((HttpURLConnection) connection);
		}
		logWarn(getLogger(),
				"Encountered URLConnection of type [%s]. Unable to handle redirect.",
				connection.getClass().getName());
		return connection;
	}

	private URLConnection handleRedirects(final HttpURLConnection connection) throws IOException {
		final Map<String, List<String>> headerParameters
				= Collections.unmodifiableMap(new LinkedHashMap<>(connection.getRequestProperties()));

		final int status = connection.getResponseCode();
		if (status >= 200 && status < 300) /* status == HttpURLConnection.HTTP_OK */ {
			return connection;
		}

		if (status == HttpURLConnection.HTTP_MOVED_PERM //
				|| status == HttpURLConnection.HTTP_MOVED_TEMP //
				|| status == HttpURLConnection.HTTP_SEE_OTHER//
				|| status == 307 // temporary redirect
				|| status == 308 // permanent redirect // TODO safe new URL
		) {
			// create new connection new new URL
			final URI uri;
			try {
				uri = new URI(connection.getHeaderField("Location"));
			} catch (final URISyntaxException e) {
				throw new IOException("Invalid redirect", e);
			}
			final URL url;
			if (uri.isAbsolute()) {
				url = uri.toURL();
			} else {
				url = new URL(connection.getURL(), connection.getHeaderField("Location"));
			}

			// set correct HTTP method
			final String method;
			final URLConnection newConnection = url.openConnection(getHttpProxy().getProxy());
			if (newConnection instanceof HttpURLConnection) {
				final HttpURLConnection httpConnection = (HttpURLConnection) newConnection;
				if (status == HttpURLConnection.HTTP_SEE_OTHER) {
					httpConnection.setRequestMethod("GET");
				} else {
					httpConnection.setRequestMethod(connection.getRequestMethod());
				}
				method = httpConnection.getRequestMethod();
			} else {
				method = null;
			}

			// copy settings from old connection
			newConnection.setAllowUserInteraction(connection.getAllowUserInteraction());
			newConnection.setConnectTimeout(connection.getConnectTimeout());
			newConnection.setDefaultUseCaches(connection.getDefaultUseCaches());
			newConnection.setDoInput(connection.getDoInput());
			newConnection.setDoOutput(connection.getDoOutput());
			newConnection.setIfModifiedSince(connection.getIfModifiedSince());
			newConnection.setReadTimeout(connection.getReadTimeout());
			newConnection.setUseCaches(connection.getUseCaches());
			headerParameters.entrySet()
					.stream()
					.filter(e -> !Arrays.asList("Host", "Connection").contains(e.getKey()))
					.filter(e -> method == null || !e.getKey().startsWith(method + " "))
					.forEach(e -> e //
							.getValue()
							.forEach(v -> newConnection.addRequestProperty(e.getKey(), v)));

			// done
			return handleRedirects(newConnection);
		}

		throw new IOException("Return code " + status + " from URL: " + connection.getURL());
	}

	@Property(name = "Internet Addresses",
			description = "The internet addresses to download. The event source will call all addressed you specify in the given order. Use these addresses to navigate through complex websites and use placeholders like ${1.1} etc. to reference groups from previous urls. If the last address contains a pattern with exactly one capturing group this group will be interpreted as an URL and Oversigt will try to reach that address. If you need to log in to a page: use the placeholders ${domain}, ${username} and ${password} to insert credentials into login data.")
	public InternetAddress[] getUrls() {
		return urls;
	}

	public void setUrls(final InternetAddress[] urls) {
		this.urls = urls;
	}

	@Property(name = "Credentials", description = "The username and password to be used to access the websites")
	public Credentials getCredentials() {
		return credentials;
	}

	public void setCredentials(final Credentials credentials) {
		this.credentials = credentials;
	}

	@Property(name = "HTTP Headers", description = "Possible HTTP headers to be sent to the server")
	public HttpHeader[] getHttpHeaders() {
		return httpHeaders != null ? httpHeaders : new HttpHeader[0];
	}

	public void setHttpHeaders(final HttpHeader[] httpHeaders) {
		this.httpHeaders = httpHeaders;
	}

	private static String createXWwwUrlEncoded(final String... strings) {
		final StringBuilder builder = new StringBuilder();
		for (int index = 0; index < strings.length; index += 2) {
			if (index > 0) {
				builder.append("&");
			}
			builder.append(strings[index]).append("=").append(StringConverters.encodeUrl(strings[index + 1]));
		}
		return builder.toString();
	}

	@JsonHint(headerTemplate = "URL #{{i1}}", arrayStyle = ArrayStyle.GRID)
	public static class InternetAddress {
		static InternetAddress fromUrl(final URL url) {
			return new InternetAddress(url.toString(), null);
		}

		private final String address;

		private final String pattern;

		private LoginData[] loginDatas = null;

		public InternetAddress(final String urlString, final String patternString) {
			address = urlString;
			pattern = patternString;
		}

		public String getUrlString() {
			if (Strings.isNullOrEmpty(address)) {
				return null;
			}
			return address;
		}

		public Pattern getPattern() {
			if (Strings.isNullOrEmpty(pattern)) {
				return null;
			}
			return Pattern.compile(pattern, Pattern.DOTALL);
		}

		/** {@inheritDoc} */
		@Override
		public String toString() {
			return ToStringBuilder.reflectionToString(this);
		}
	}

	@JsonHint(arrayStyle = ArrayStyle.TABLE, headerTemplate = "{{self.name}}")
	public static class LoginData {
		private final String name;

		private final String value;

		public LoginData(final String name, final String value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}

		/** {@inheritDoc} */
		@Override
		public String toString() {
			return ToStringBuilder.reflectionToString(this);
		}
	}

	@JsonHint(arrayStyle = ArrayStyle.TABLE, headerTemplate = "{{self.name}}")
	public static class HttpHeader {
		private final String name;

		private final String value;

		public HttpHeader(final String name, final String value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}

		/** {@inheritDoc} */
		@Override
		public String toString() {
			return ToStringBuilder.reflectionToString(this);
		}
	}
}