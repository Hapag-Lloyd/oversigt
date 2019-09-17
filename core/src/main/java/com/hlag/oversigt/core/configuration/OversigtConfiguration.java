package com.hlag.oversigt.core.configuration;

import static com.hlag.oversigt.util.TypeUtils.deserializer;
import static com.hlag.oversigt.util.TypeUtils.serializer;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.Resources;
import com.google.inject.Singleton;
import com.hlag.oversigt.core.Oversigt;
import com.hlag.oversigt.core.configuration.WroManagerFactory.CustomWroConfiguration;
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
public final class OversigtConfiguration {
	private static final Logger LOGGER = LoggerFactory.getLogger(OversigtConfiguration.class);

	private static ObjectMapper createObjectMapper() {
		final SimpleModule module = new SimpleModule("Oversigt-API");
		module.addSerializer(Duration.class, serializer(Duration.class, Duration::toString));
		module.addDeserializer(Duration.class, deserializer(Duration.class, Duration::parse));

		final ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(module);
		objectMapper.registerModule(new Jdk8Module());
		objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
		objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		return objectMapper;
	}

	static OversigtConfiguration readConfiguration() {
		try {
			final URL configUrl = Resources.getResource(Oversigt.APPLICATION_CONFIG_FILE);
			Preconditions.checkState(configUrl != null,
					"Main application config [%s] not found",
					Oversigt.APPLICATION_CONFIG_FILE);
			LOGGER.info("Reading Oversigt configuration: " + configUrl);
			final String configString = Resources.toString(configUrl, Charsets.UTF_8);
			return createObjectMapper().readValue(configString, OversigtConfiguration.class);
		} catch (final IOException e) {
			throw new IllegalStateException("Unable to read configuration", e);
		}
	}

	private boolean debug = false;

	private Optional<String> hostname = Optional.empty();

	private UiConfiguration ui = new UiConfiguration();

	private ApiConfiguration api = new ApiConfiguration();

	private EventManagerConfiguration eventManager = new EventManagerConfiguration();

	private Optional<DatabaseConfiguration> database = Optional.empty();

	private SecurityConfiguration security = new SecurityConfiguration();

	private MailConfiguration mail = new MailConfiguration();

	private String templateNumberFormat = "0";

	private List<HttpListenerConfiguration> listeners = new ArrayList<>(Arrays.asList(new HttpListenerConfiguration()));

	private EventSourceConfiguration eventSources = new EventSourceConfiguration();

	private JiraConfiguration jira = new JiraConfiguration();

	private CustomWroConfiguration wro = new CustomWroConfiguration();

	@JsonCreator
	private OversigtConfiguration() {
		// fields will be filled by jackson
	}

	public boolean isDebug() {
		return debug;
	}

	public Optional<String> getHostname() {
		return hostname;
	}

	public ApiConfiguration getApi() {
		return api;
	}

	public UiConfiguration getUi() {
		return ui;
	}

	public EventManagerConfiguration getEventManager() {
		return eventManager;
	}

	public DatabaseConfiguration getDatabase() {
		return database.orElseThrow(() -> new RuntimeException("No database configuration supplied."));
	}

	public SecurityConfiguration getSecurity() {
		return security;
	}

	public MailConfiguration getMail() {
		return mail;
	}

	public String getTemplateNumberFormat() {
		return templateNumberFormat;
	}

	public List<HttpListenerConfiguration> getListeners() {
		return listeners;
	}

	public EventSourceConfiguration getEventSources() {
		return eventSources;
	}

	public JiraConfiguration getJira() {
		return jira;
	}

	public CustomWroConfiguration getWro() {
		return wro;
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

	static final class ApiConfiguration {
		private SignatureAlgorithm jwtAlgorithm = SignatureAlgorithm.HS256;

		private Optional<String> jwtSecretBase64 = Optional.empty();

		private long jwtTimeToLive = 4 * 60 * 60 * 1000; // 4 hours

		private ApiConfiguration() {
			// no fields to be initialized
		}

		public long getJwtTimeToLive() {
			return jwtTimeToLive;
		}

		public String getJwtSecretBase64() {
			return jwtSecretBase64.orElseThrow(() -> new RuntimeException("No JWT secret configured"));
		}

		public SignatureAlgorithm getJwtAlgorithm() {
			return jwtAlgorithm;
		}
	}

	static final class EventManagerConfiguration {
		private long rateLimit = 10;

		private Duration discardEventsAfter = Duration.ofHours(1);

		private EventManagerConfiguration() {
			// no fields to be initialized
		}

		public long getRateLimit() {
			return rateLimit;
		}

		public Duration getDiscardEventsAfter() {
			return discardEventsAfter;
		}
	}

	static final class DatabaseConfiguration {
		private final Class<? extends SqlDialect> sqlDialect;

		private final String location;

		private final String name;

		private final String username;

		private final String password;

		@JsonCreator
		private DatabaseConfiguration(@JsonProperty("sqlDialect") final Class<? extends SqlDialect> sqlDialect,
				@JsonProperty("location") final String location,
				@JsonProperty("name") final String name,
				@JsonProperty("username") final String username,
				@JsonProperty("password") final String password) {
			this.sqlDialect = sqlDialect;
			this.location = location;
			this.name = name;
			this.username = username;
			this.password = password;
		}

		public Class<? extends SqlDialect> getSqlDialect() {
			return sqlDialect;
		}

		public String getLocation() {
			return location;
		}

		public String getName() {
			return name;
		}

		public String getUsername() {
			return username;
		}

		public String getPassword() {
			return password;
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

	static final class MailConfiguration {
		private Optional<String> hostname = Optional.empty();

		private int port = 0;

		private boolean startTls = false;

		private Optional<String> username = Optional.empty();

		private Optional<String> password = Optional.empty();

		private Optional<String> senderAddress = Optional.empty();

		private MailConfiguration() {
			// no fields to be initialized
		}

		public Optional<String> getHostname() {
			return hostname;
		}

		public int getPort() {
			return port;
		}

		public boolean isStartTls() {
			return startTls;
		}

		public Optional<String> getUsername() {
			return username;
		}

		public Optional<String> getPassword() {
			return password;
		}

		public String getSenderAddress() {
			return senderAddress.orElseThrow(() -> new RuntimeException("No sender address configured"));
		}
	}

	static final class EventSourceConfiguration {
		private String[] packages = new String[] { AbstractDownloadEventSource.class.getPackage().getName() };

		private String[] addonFolders = new String[0];

		private String[] widgetsPaths = new String[] { "statics/widgets/" };

		private EventSourceConfiguration() {
			// no fields to be initialized
		}

		public String[] getPackages() {
			return packages;
		}

		public String[] getAddonFolders() {
			return addonFolders;
		}

		public String[] getWidgetsPaths() {
			return widgetsPaths;
		}

	}

	static final class JiraConfiguration {
		private int socketTimeout = 120;

		private JiraConfiguration() {
			// no fields to be initialized
		}

		public int getSocketTimeout() {
			return socketTimeout;
		}
	}

	static final class SecurityConfiguration {
		private Optional<List<String>> serverAdmins = Optional.empty();

		private Map<String, String> users = new HashMap<>();

		private Optional<LdapConfiguration> ldap = Optional.empty();

		private ForeignEventsConfiguration foreignEvents = new ForeignEventsConfiguration();

		private SecurityConfiguration() {
			// no fields to be initialized
		}

		public ForeignEventsConfiguration getForeignEvents() {
			return foreignEvents;
		}

		public Optional<LdapConfiguration> getLdap() {
			return ldap;
		}

		public Map<String, String> getUsers() {
			return users;
		}

		public Optional<List<String>> getServerAdmins() {
			return serverAdmins;
		}
	}

	static final class ForeignEventsConfiguration {
		private boolean enabled = false;

		private boolean needAuthentication = true;

		private String apiKeyHeaderName = "x-oversigt-api-key";

		private List<String> allowedApiKeys = Collections.emptyList();

		private ForeignEventsConfiguration() {
			// nothing to do
		}

		public boolean isEnabled() {
			return enabled;
		}

		public boolean isNeedAuthentication() {
			return needAuthentication;
		}

		public String getApiKeyHeaderName() {
			return apiKeyHeaderName;
		}

		public List<String> getAllowedApiKeys() {
			return allowedApiKeys;
		}
	}

	static final class UiConfiguration {
		private boolean showOwnersInWelcomePage = true;

		public boolean isShowOwnersInWelcomePage() {
			return showOwnersInWelcomePage;
		}
	}
}
