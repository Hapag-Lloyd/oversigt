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

import com.google.common.base.Strings;
import com.google.inject.Binder;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.hlag.oversigt.connect.jira.config.JiraConfigurationProvider;
import com.hlag.oversigt.core.WroManagerFactory.CustomWroConfiguration;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.LdapAuthenticator;
import com.hlag.oversigt.security.MapAuthenticator;
import com.hlag.oversigt.sources.AbstractDownloadEventSource;
import com.hlag.oversigt.storage.SqlDialect;
import com.hlag.oversigt.util.SSLUtils.TLSConfiguration;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.jsonwebtoken.SignatureAlgorithm;

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

	private List<HttpListenerConfiguration> listeners = new ArrayList<>(Arrays.asList(new HttpListenerConfiguration()));

	@Nullable
	private EventSourceConfiguration eventSources;

	private JiraConfiguration jira = new JiraConfiguration();

	private CustomWroConfiguration wro = new CustomWroConfiguration();

	public OversigtConfiguration() {
		// no fields to be initialized manually, some will be injected
	}

	void bindProperties(final Binder binder, final boolean debugFallback, final String ldapBindPasswordFallback) {
		bind(binder, "debug", debug || debugFallback);

		binder.bind(CustomWroConfiguration.class).toInstance(wro);

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
		binder.bind(new TypeLiteral<List<HttpListenerConfiguration>>() {
			/* just type literal for generics detection */
		}).annotatedWith(Names.named("listeners")).toInstance(listeners);
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
			binder.bind(new TypeLiteral<Map<String, String>>() { /* just type literal for generics detection */

			}).annotatedWith(Names.named("UsernamesAndPasswords")).toInstance(security.users);
			binder.bind(Authenticator.class).to(MapAuthenticator.class);
			boundAuthenticator = true;
		}
		final List<String> admins = Optional.ofNullable(security.serverAdmins).orElseGet(ArrayList::new);
		binder.bind(new TypeLiteral<List<String>>() {
			/* just type literal for generics detection */
		}).annotatedWith(Names.named("serverAdmins")).toInstance(admins);
		if (admins.isEmpty()) {
			LOGGER.warn("No server admins configured. Please check configuration security.serverAdmins");
		}
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

	void applyConfiguration() {
		JiraConfigurationProvider.setSocketTimeout(jira.socketTimeout);
	}

	public static class HttpListenerConfiguration {
		private String ip = "0.0.0.0";

		private int port = 80;

		private Optional<TLSConfiguration> tls = Optional.empty();

		public HttpListenerConfiguration() {
			// no fields to be initialized
		}

		public String getIp() {
			return ip;
		}

		public int getPort() {
			return port;
		}

		public TLSConfiguration getTLSConfiguration() {
			return tls.get();
		}

		public boolean isTls() {
			return tls.isPresent();
		}
	}

	private static final class ApiConfiguration {
		private SignatureAlgorithm jwtAlgorithm = SignatureAlgorithm.HS256;

		@Nullable
		private String jwtSecretBase64 = null;

		private long jwtTimeToLive = 4 * 60 * 60 * 1000; // 4 hours

		private ApiConfiguration() {
			// no fields to be initialized
		}
	}

	private static final class EventManagerConfiguration {
		private long rateLimit = 10;

		private Duration discardEventsAfter = Duration.ofHours(1);

		private EventManagerConfiguration() {
			// no fields to be initialized
		}
	}

	private static final class DatabaseConfiguration {
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

		private DatabaseConfiguration() {
			// no fields to be initialized
		}
	}

	public static class LdapConfiguration {
		@Nullable
		private String url;

		@Nullable
		private String baseDn;

		@Nullable
		private String bindUser;

		@Nullable
		private String bindPassword;

		@Nullable
		private String uidAttribute;

		public LdapConfiguration() {
			// no fields to be initialized manually, some will be injected
		}

		public boolean isBindPasswordSet() {
			return !Strings.isNullOrEmpty(bindPassword);
		}

		public void setBindPassword(final String bindPassword) {
			this.bindPassword = bindPassword;
		}

		@Nullable
		public String getUrl() {
			return url;
		}

		@Nullable
		public String getBaseDn() {
			return baseDn;
		}

		@Nullable
		public String getBindPassword() {
			return bindPassword;
		}

		@Nullable
		public String getBindUser() {
			return bindUser;
		}

		@Nullable
		public String getUidAttribute() {
			return uidAttribute;
		}

	}

	private static final class MailConfiguration {
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

		private MailConfiguration() {
			// no fields to be initialized
		}
	}

	private static final class EventSourceConfiguration {
		private String[] packages = new String[] { AbstractDownloadEventSource.class.getPackage().getName() };

		private String[] addonFolders = new String[0];

		private String[] widgetsPaths = new String[] { "statics/widgets/" };

		private EventSourceConfiguration() {
			// no fields to be initialized
		}
	}

	private static final class JiraConfiguration {
		private int socketTimeout = 120;

		private JiraConfiguration() {
			// no fields to be initialized
		}
	}

	private static final class SecurityConfiguration {
		@Nullable
		private List<String> serverAdmins;

		@Nullable
		private Map<String, String> users;

		@Nullable
		private LdapConfiguration ldap;

		private SecurityConfiguration() {
			// no fields to be initialized
		}
	}
}
