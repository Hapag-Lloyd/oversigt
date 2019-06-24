package com.hlag.oversigt.core;

import static com.hlag.oversigt.util.TypeUtils.createArray;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

import edu.umd.cs.findbugs.annotations.Nullable;
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

	// TODO remove all nullables
	@Nullable
	private String hostname = null;

	private ApiConfiguration api = new ApiConfiguration();

	private EventManagerConfiguration eventManager = new EventManagerConfiguration();

	@Nullable
	private DatabaseConfiguration database;

	private SecurityConfiguration security = new SecurityConfiguration();

	private MailConfiguration mail = new MailConfiguration();

	private String templateNumberFormat = "0";

	private List<HttpListenerConfiguration> listeners = Lists.newArrayList(new HttpListenerConfiguration());

	@Nullable
	private EventSourceConfiguration eventSources;

	@Nullable
	private JiraConfiguration jira;

	void bindProperties(final Binder binder, final boolean debugFallback, final String ldapBindPasswordFallback) {
		bind(binder, "debug", debug || debugFallback);

		bind(binder, "hostname", Objects.requireNonNull(hostname, "hostname"));
		binder.bind(SignatureAlgorithm.class).toInstance(api.jwtAlgorithm);
		bind(binder, "api.secret.base64", Objects.requireNonNull(api.jwtSecretBase64, "api.jwtSecretBase64"));
		bind(binder, "api.ttl", api.jwtTimeToLive);
		bind(binder, "rateLimit", eventManager.rateLimit);
		binder.bind(Duration.class)
				.annotatedWith(Names.named("discardEventsAfter"))
				.toInstance(eventManager.discardEventsAfter);
		bind(binder, "templateNumberFormat", templateNumberFormat);
		final DatabaseConfiguration database = Objects.requireNonNull(this.database);
		bind(binder, "databaseLocation", Objects.requireNonNull(database.location, "database.location"));
		bind(binder, "databaseName", Objects.requireNonNull(database.name, "database.name"));
		bind(binder, "databaseUsername", Objects.requireNonNull(database.username, "database.username"));
		bind(binder, "databasePassword", Objects.requireNonNull(database.password, "database.password"));
		binder.bind(SqlDialect.class).to(database.sqlDialect);
		binder.bind(new TypeLiteral<List<HttpListenerConfiguration>>() {/* just type literal for generics detection */})
				.annotatedWith(Names.named("listeners"))
				.toInstance(listeners);
		bind(binder, "jiraSocketTimeout", Objects.requireNonNull(jira, "").socketTimeout);
		bindNamedArray(binder,
				String[].class,
				"additionalPackages",
				Arrays.asList(Objects.requireNonNull(eventSources, "").packages));
		bindNamedArray(binder,
				Path[].class,
				"addonFolders",
				Paths::get,
				Arrays.asList(Objects.requireNonNull(eventSources, "").addonFolders));
		bindNamedArray(binder,
				String[].class,
				"widgetsPaths",
				Arrays.asList(Objects.requireNonNull(eventSources, "").widgetsPaths));

		// Mail Settings
		bind(binder, "mailSenderHost", Objects.requireNonNull(mail.hostname, "mail.hostname"));
		bind(binder, "mailSenderPort", mail.port);
		bind(binder, "mailSenderStartTls", mail.startTls);
		bind(binder, "mailSenderUsername", Objects.requireNonNull(mail.username, "mail.username"));
		bind(binder, "mailSenderPassword", Objects.requireNonNull(mail.password, "mail.password"));
		bind(binder, "mailSenderAddress", Objects.requireNonNull(mail.senderAddress, "mail.senderAddress"));

		// Security
		boolean boundAuthenticator = false;
		final LdapConfiguration ldap = security.ldap;
		if (ldap != null) {
			if (!ldap.isBindPasswordSet()) {
				ldap.setBindPassword(ldapBindPasswordFallback);
			}
			if (ldap.isBindPasswordSet()) {
				binder.bind(LdapConfiguration.class).toInstance(security.ldap);
				binder.bind(Authenticator.class).to(LdapAuthenticator.class);
				boundAuthenticator = true;
			}
		}
		if (!boundAuthenticator && security.users != null) {
			binder.bind(new TypeLiteral<Map<String, String>>() {/* just type literal for generics detection */})
					.annotatedWith(Names.named("UsernamesAndPasswords"))
					.toInstance(security.users);
			binder.bind(Authenticator.class).to(MapAuthenticator.class);
			boundAuthenticator = true;
		}
		final List<String> admins = Optional.ofNullable(security.serverAdmins).orElseGet(ArrayList::new);
		binder.bind(new TypeLiteral<List<String>>() {/* just type literal for generics detection */})
				.annotatedWith(Names.named("serverAdmins"))
				.toInstance(admins);
		if (admins.isEmpty()) {
			LOGGER.warn("No server admins configured. Please check configuration security.serverAdmins");
		}
		binder.bind(SessionManager.class).toProvider(() -> provideSessionManager(security.session)).asEagerSingleton();
		binder.bind(SessionConfig.class).toInstance(security.session.cookieConfig);
	}

	private static void bindNamedArray(final Binder binder,
			final Class<String[]> targetClass,
			final String name,
			final Collection<String> inputs) {
		bindNamedArray(binder, targetClass, name, Function.identity(), inputs);
	}

	@SuppressWarnings("unchecked")
	private static <T> void bindNamedArray(final Binder binder,
			final Class<T[]> targetClass,
			final String name,
			final Function<String, T> converter,
			final Collection<String> inputs) {
		binder.bind(targetClass)
				.annotatedWith(Names.named(name))
				.toInstance((T[]) inputs.stream()
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

		@Nullable
		private SSLConfiguration ssl = null;

		public String getIp() {
			return ip;
		}

		public int getPort() {
			return port;
		}

		@Nullable
		public SSLConfiguration getSSLConfiguration() {
			return ssl;
		}

		public boolean isSsl() {
			return ssl != null;
		}
	}

	public static class ApiConfiguration {
		private SignatureAlgorithm jwtAlgorithm = SignatureAlgorithm.HS256;

		@Nullable
		private String jwtSecretBase64 = null;

		private long jwtTimeToLive = 4 * 60 * 60 * 1000; // 4 hours
	}

	public static class EventManagerConfiguration {
		private long rateLimit = 10;

		private Duration discardEventsAfter = Duration.ofHours(1);
	}

	private static class DatabaseConfiguration {
		@Nullable
		private Class<? extends SqlDialect> sqlDialect;

		@Nullable
		private String location;

		@Nullable
		private String name;

		@Nullable
		private String username;

		@Nullable
		private String password;
	}

	private static class MailConfiguration {
		@Nullable
		private String hostname;

		private int port;

		private boolean startTls;

		@Nullable
		private String username;

		@Nullable
		private String password;

		@Nullable
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
		@Nullable
		private List<String> serverAdmins;

		@Nullable
		private Map<String, String> users;

		@Nullable
		private LdapConfiguration ldap;

		private SessionConfiguration session = new SessionConfiguration();
	}

	private static class SessionConfiguration {
		private int timeout = 30;

		private int maxCount = -1;

		private boolean expireOldestUnusedSessionOnMax = false;

		private boolean statisticsEnabled = true;

		@Nullable
		private SessionCookieConfig cookieConfig;
	}
}
