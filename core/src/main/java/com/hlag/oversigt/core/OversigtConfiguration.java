package com.hlag.oversigt.core;

import static com.hlag.oversigt.util.TypeUtils.createArray;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.inject.Binder;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.LdapAuthenticator;
import com.hlag.oversigt.security.LdapAuthenticator.LdapConfiguration;
import com.hlag.oversigt.security.MapAuthenticator;
import com.hlag.oversigt.sources.AbstractDownloadEventSource;
import com.hlag.oversigt.storage.SqlDialect;
import com.hlag.oversigt.util.SSLUtils.SSLConfiguration;

import io.jsonwebtoken.SignatureAlgorithm;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionManager;

/**
 * Events and event sources configuration
 *
 * @author avarabyeu
 */
@Singleton
public class OversigtConfiguration {
	private static final Logger LOGGER = LoggerFactory.getLogger(OversigtConfiguration.class);

	private static void bind(final Binder binder, final String name, final Object value) {
		binder.bindConstant()
				.annotatedWith(Names.named(name))
				.to(Objects.requireNonNull(value, "The value for '" + name + "' is null.").toString());
	}

	private boolean debug = false;

	private String hostname = null;

	private ApiConfiguration api = new ApiConfiguration();

	private EventManagerConfiguration eventManager = new EventManagerConfiguration();

	private DatabaseConfiguration database;

	private SecurityConfiguration security = new SecurityConfiguration();

	private MailConfiguration mail = new MailConfiguration();

	private String templateNumberFormat = "0";

	private List<HttpListenerConfiguration> listeners = Lists.newArrayList(new HttpListenerConfiguration());

	private EventSourceConfiguration eventSources;

	private JiraConfiguration jira;

	void bindProperties(final Binder binder, final boolean debugFallback, final String ldapBindPasswordFallback) {
		bind(binder, "debug", debug || debugFallback);

		bind(binder, "hostname", hostname);
		binder.bind(SignatureAlgorithm.class).toInstance(api.jwtAlgorithm);
		bind(binder, "api.secret.base64", api.jwtSecretBase64);
		bind(binder, "api.ttl", api.jwtTimeToLive);
		bind(binder, "rateLimit", eventManager.rateLimit);
		binder.bind(Duration.class)
				.annotatedWith(Names.named("discardEventsAfter"))
				.toInstance(eventManager.discardEventsAfter);
		bind(binder, "templateNumberFormat", templateNumberFormat);
		bind(binder, "databaseLocation", database.location);
		bind(binder, "databaseName", database.name);
		bind(binder, "databaseUsername", database.username);
		bind(binder, "databasePassword", database.password);
		binder.bind(SqlDialect.class).to(database.sqlDialect);
		binder.bind(new TypeLiteral<List<HttpListenerConfiguration>>() {})
				.annotatedWith(Names.named("listeners"))
				.toInstance(listeners);
		bind(binder, "jiraSocketTimeout", jira.socketTimeout);
		bindNamedArray(binder, String[].class, "additionalPackages", eventSources.packages);
		bindNamedArray(binder, Path[].class, "addonFolders", Paths::get, eventSources.addonFolders);
		bindNamedArray(binder, String[].class, "widgetsPaths", eventSources.widgetsPaths);

		// Mail Settings
		bind(binder, "mailSenderHost", mail.hostname);
		bind(binder, "mailSenderPort", mail.port);
		bind(binder, "mailSenderStartTls", mail.startTls);
		bind(binder, "mailSenderUsername", mail.username);
		bind(binder, "mailSenderPassword", mail.password);
		bind(binder, "mailSenderAddress", mail.senderAddress);

		// Security
		boolean boundAuthenticator = false;
		if (security.ldap != null) {
			if (!security.ldap.isBindPasswordSet()) {
				security.ldap.setBindPassword(ldapBindPasswordFallback);
			}
			if (security.ldap.isBindPasswordSet()) {
				binder.bind(LdapConfiguration.class).toInstance(security.ldap);
				binder.bind(Authenticator.class).to(LdapAuthenticator.class);
				boundAuthenticator = true;
			}
		}
		if (!boundAuthenticator && security.users != null) {
			binder.bind(new TypeLiteral<Map<String, String>>() {})
					.annotatedWith(Names.named("UsernamesAndPasswords"))
					.toInstance(security.users);
			binder.bind(Authenticator.class).to(MapAuthenticator.class);
			boundAuthenticator = true;
		}
		final List<String> admins = Optional.ofNullable(security.serverAdmins).orElseGet(ArrayList::new);
		binder.bind(new TypeLiteral<List<String>>() {}).annotatedWith(Names.named("serverAdmins")).toInstance(admins);
		if (admins.isEmpty()) {
			LOGGER.warn("No server admins configured. Please check configuration security.serverAdmins");
		}
		binder.bind(SessionManager.class).toProvider(() -> provideSessionManager(security.session)).asEagerSingleton();
		binder.bind(SessionConfig.class).toInstance(security.session.cookieConfig);
	}

	private static void bindNamedArray(final Binder binder,
			final Class<String[]> targetClass,
			final String name,
			final String[] input) {
		bindNamedArray(binder, targetClass, name, Function.identity(), input);
	}

	@SuppressWarnings("unchecked")
	private static <T> void bindNamedArray(final Binder binder,
			final Class<T[]> targetClass,
			final String name,
			final Function<String, T> converter,
			final String[] input) {
		binder.bind(targetClass)
				.annotatedWith(Names.named(name))
				.toInstance((T[]) Arrays.stream(input != null ? input : new String[0])
						.map(converter)
						.collect(Collectors.toList())
						.toArray(createArray(targetClass.getComponentType(), 0)));
	}

	private SessionManager provideSessionManager(final SessionConfiguration sc) {
		final InMemorySessionManager sm = new InMemorySessionManager(new SecureRandomSessionIdGenerator(),
				"SESSION_MANAGER",
				sc.maxCount,
				sc.expireOldestUnusedSessionOnMax,
				sc.statisticsEnabled);
		sm.setDefaultSessionTimeout(sc.timeout * 60);
		return sm;
	}

	public static class HttpListenerConfiguration {
		private String ip = "0.0.0.0";

		private int port = 80;

		private SSLConfiguration ssl = null;

		public String getIp() {
			return ip != null ? ip : "0.0.0.0";
		}

		public int getPort() {
			return port;
		}

		public SSLConfiguration getSSLConfiguration() {
			return ssl;
		}

		public boolean isSsl() {
			return ssl != null;
		}
	}

	public static class ApiConfiguration {
		private SignatureAlgorithm jwtAlgorithm = SignatureAlgorithm.HS256;

		private String jwtSecretBase64 = null;

		private long jwtTimeToLive = 4 * 60 * 60 * 1000; // 4 hours
	}

	public static class EventManagerConfiguration {
		private long rateLimit = 10;

		private Duration discardEventsAfter = Duration.ofHours(1);
	}

	private static class DatabaseConfiguration {
		private Class<? extends SqlDialect> sqlDialect;

		private String location;

		private String name;

		private String username;

		private String password;
	}

	private static class MailConfiguration {
		private String hostname;

		private int port;

		private boolean startTls;

		private String username;

		private String password;

		private String senderAddress;
	}

	private static class EventSourceConfiguration {
		private String[] packages = new String[] { AbstractDownloadEventSource.class.getPackage().getName() };

		private String[] addonFolders = new String[0];

		private String[] widgetsPaths = new String[] { "statics/widgets/" };
	}

	private static class JiraConfiguration {
		private int socketTimeout = 120;
	}

	private static class SecurityConfiguration {
		private List<String> serverAdmins;

		private Map<String, String> users;

		private LdapConfiguration ldap;

		private SessionConfiguration session = new SessionConfiguration();
	}

	private static class SessionConfiguration {
		private int timeout = 30;

		private int maxCount = -1;

		private boolean expireOldestUnusedSessionOnMax = false;

		private boolean statisticsEnabled = true;

		private SessionCookieConfig cookieConfig;
	}
}
