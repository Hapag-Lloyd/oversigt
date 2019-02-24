package com.hlag.oversigt.sources;

import static com.hlag.oversigt.util.Utils.logDebug;
import static com.hlag.oversigt.util.Utils.logInfo;
import static com.hlag.oversigt.util.Utils.logTrace;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.IOUtils;

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

import lombok.ToString;

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

	public void setHttpProxy(HttpProxy proxy) {
		this.proxy = proxy;
	}

	private URLConnection createConnection(String urlString, String cookie, LoginData[] loginData) throws IOException {
		URL url = new URL(urlString);
		URLConnection con = url.openConnection(getHttpProxy().getProxy());
		con.setConnectTimeout(30 * 1000);
		con.setReadTimeout(30 * 1000);
		con.setRequestProperty("User-Agent",
				"Mozilla/5.0 Java/" + System.getProperty("java.version") + " Oversigt/0.9");

		if (con instanceof HttpsURLConnection && !isCheckSSL()) {
			((HttpsURLConnection) con).setSSLSocketFactory(SSLUtils.getNonCheckingSSLSocketFactory(getEventId()));
			((HttpsURLConnection) con).setHostnameVerifier((hostname, session) -> true);
		}

		if (con instanceof HttpURLConnection) {
			for (HttpHeader httpHeader : getHttpHeaders()) {
				con.setRequestProperty(httpHeader.getName(), httpHeader.getValue());
			}
		}

		if (cookie != null) {
			con.setRequestProperty("Cookie", cookie);
		} else if ((loginData == null || loginData.length == 0) && getCredentials() != null
				&& getCredentials() != Credentials.EMPTY) {
			String encoded = Base64.getEncoder()
					.encodeToString((getCredentials().getUsername() + ":" + getCredentials().getPassword())
							.getBytes(StandardCharsets.UTF_8));
			con.setRequestProperty("Authorization", "Basic " + encoded);
		}

		if (con instanceof HttpURLConnection && loginData != null && loginData.length > 0) {
			HttpURLConnection hcon = (HttpURLConnection) con;
			hcon.setDoOutput(true);
			hcon.setInstanceFollowRedirects(false);
			hcon.setRequestMethod("POST");
			hcon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			hcon.setUseCaches(false);
			try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(hcon.getOutputStream()))) {
				List<String> parts = new ArrayList<>();
				for (LoginData data : loginData) {
					parts.add(data.getName());
					parts.add(data.getValue()
							.replace("${domain}", getCredentials().getDomain())
							.replace("${username}", getCredentials().getUsername())
							.replace("${password}", getCredentials().getPassword()));
				}
				String string = createXWwwUrlEncoded(parts.toArray(new String[0]));
				writer.append(string);
			}
		}

		return con;
	}

	protected URLConnection createConfiguredConnection() throws IOException {
		return createConnection(new ArrayList<>(Arrays.asList(getUrls())));
	}

	protected URLConnection createConnection(String urlString) throws IOException {
		return createConnection(Arrays.asList(InternetAddress.fromUrl(new URL(urlString))));
	}

	protected URLConnection createConnection(URL url) throws IOException {
		return createConnection(Arrays.asList(InternetAddress.fromUrl(url)));
	}

	protected URLConnection createConnection(List<InternetAddress> addresses) throws IOException {
		List<List<String>> replacements = new ArrayList<>();
		URLConnection connection = null;
		String cookie = null;

		for (int i = 0; i < addresses.size(); ++i) {
			InternetAddress internetAddress = addresses.get(i);
			if (connection != null) {
				logDebug(getLogger(), "Closing connection");
				connection.getInputStream().close();
				connection = null;
			}

			String url = internetAddress.getUrlString();
			logTrace(getLogger(), "Matching URL: %s", url);
			Matcher repmat = PATTERN_URL_MATCHER_REPLACEMENT.matcher(url);
			while (repmat.find()) {
				int major = Integer.parseInt(repmat.group(1));
				int minor = Integer.parseInt(repmat.group(2));
				if (replacements.size() < major || replacements.get(major - 1).size() < minor) {
					throw new RuntimeException("Unable to find replacement ${" + major + "." + minor + "}");
				}
				if (replacements.get(major - 1) != null && replacements.get(major - 1).get(minor - 1) != null) {
					String search = "${" + major + "." + minor + "}";
					String replacement = replacements.get(major - 1).get(minor - 1);
					url = url.replace(search, replacement);
					logTrace(getLogger(), "Replacing %s with %s to %s", search, replacement, url);
				}
			}
			logInfo(getLogger(), "Connecting to URL: %s", url);
			if (url != null) {
				url = textProcessor.process(url);
				connection = createConnection(url, cookie, internetAddress.loginDatas);
				Pattern pattern = internetAddress.getPattern();
				if (pattern != null) {
					List<String> reps = new ArrayList<>();
					logInfo(getLogger(), "Downloading content for matcher: %s", pattern.toString());
					String content = downloadString(connection);
					Matcher matcher = pattern.matcher(content);
					if (matcher.find()) {
						for (int j = 1; j <= matcher.groupCount(); ++j) {
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
				String newCookie = connection.getHeaderField("Set-Cookie");
				logTrace(getLogger(), "New Cookie: %s", cookie);
				if (newCookie != null) {
					cookie = newCookie;
				}
			}
		}
		return connection;
	}

	protected Tuple<byte[], String> downloadBytes(URLConnection connectionToRead) throws IOException {
		return read(connectionToRead, //
				(connection, inputStream) -> //
				new Tuple<>(ByteStreams.toByteArray(inputStream), connection.getContentType()));
	}

	protected String downloadString(URLConnection connectionToRead) throws IOException {
		return read(connectionToRead, //
				(connection, inputStream) -> //
				IOUtils.toString(inputStream, Objects.toString(connection.getContentEncoding(), "UTF-8")));
	}

	private <R> R read(final URLConnection inputConnection,
			final ThrowingBiFunction<URLConnection, InputStream, R> inputStreamConsumer) throws IOException {
		final URLConnection connectionToRead = handleRedirects(inputConnection);
		try (InputStream in = connectionToRead.getInputStream()) {
			return inputStreamConsumer.apply(connectionToRead, in);
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Error while reading data from connection.", e);
		}
	}

	private URLConnection handleRedirects(URLConnection connection) throws IOException {
		if (connection instanceof HttpURLConnection) {
			return handleRedirects((HttpURLConnection) connection);
		} else {
			return connection;
		}
	}

	private URLConnection handleRedirects(final HttpURLConnection connection) throws IOException {
		int status = connection.getResponseCode();
		if (status == HttpURLConnection.HTTP_OK) {
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
			} catch (URISyntaxException e) {
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
			URLConnection newConnection = url.openConnection();
			if (newConnection instanceof HttpURLConnection) {
				HttpURLConnection httpConnection = (HttpURLConnection) newConnection;
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
			connection.getRequestProperties()
					.entrySet()
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

	@Property(name = "Internet Addresses", description = "The internet addresses to download. The event source will call all addressed you specify in the given order. Use these addresses to navigate through complex websites and use placeholders like ${1.1} etc. to reference groups from previous urls. If the last address contains a pattern with exactly one capturing group this group will be interpreted as an URL and Oversigt will try to reach that address. If you need to log in to a page: use the placeholders ${domain}, ${username} and ${password} to insert credentials into login data.", json = true)
	public InternetAddress[] getUrls() {
		return urls;
	}

	public void setUrls(InternetAddress[] urls) {
		this.urls = urls;
	}

	@Property(name = "Credentials", description = "The username and password to be used to access the websites")
	public Credentials getCredentials() {
		return credentials;
	}

	public void setCredentials(Credentials credentials) {
		this.credentials = credentials;
	}

	@Property(name = "HTTP Headers", description = "Possible HTTP headers to be sent to the server", json = true)
	public HttpHeader[] getHttpHeaders() {
		return httpHeaders != null ? httpHeaders : new HttpHeader[0];
	}

	public void setHttpHeaders(HttpHeader[] httpHeaders) {
		this.httpHeaders = httpHeaders;
	}

	@ToString
	@JsonHint(headerTemplate = "URL #{{i1}}", arrayStyle = ArrayStyle.GRID)
	public static class InternetAddress {
		static InternetAddress fromUrl(URL url) {
			return new InternetAddress(url.toString(), null);
		}

		private final String address;
		private final String pattern;

		private LoginData[] loginDatas = null;

		public InternetAddress(String urlString, String patternString) {
			this.address = urlString;
			this.pattern = patternString;
		}

		public String getUrlString() {
			if (!Strings.isNullOrEmpty(address)) {
				return address;
			} else {
				return null;
			}
		}

		public Pattern getPattern() {
			if (!Strings.isNullOrEmpty(pattern)) {
				return Pattern.compile(pattern, Pattern.DOTALL);
			} else {
				return null;
			}
		}
	}

	@ToString
	@JsonHint(arrayStyle = ArrayStyle.TABLE, headerTemplate = "{{self.name}}")
	public static class LoginData {
		private final String name;
		private final String value;

		public LoginData(String name, String value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}
	}

	@ToString
	@JsonHint(arrayStyle = ArrayStyle.TABLE, headerTemplate = "{{self.name}}")
	public static class HttpHeader {
		private final String name;
		private final String value;

		public HttpHeader(String name, String value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}
	}

	private static String createXWwwUrlEncoded(String... strings) throws UnsupportedEncodingException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < strings.length; i += 2) {
			if (i > 0) {
				sb.append("&");
			}
			sb.append(strings[i]).append("=").append(URLEncoder.encode(strings[i + 1], "UTF-8"));
		}
		return sb.toString();
	}

}
