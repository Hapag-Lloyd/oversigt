package com.hlag.oversigt.core.configuration;

import static com.hlag.oversigt.util.TypeUtils.createArray;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.name.Names;
import com.hlag.oversigt.core.configuration.OversigtConfiguration.DatabaseConfiguration;
import com.hlag.oversigt.core.configuration.OversigtConfiguration.HttpListenerConfiguration;
import com.hlag.oversigt.core.configuration.OversigtConfiguration.LdapConfiguration;
import com.hlag.oversigt.core.configuration.WroManagerFactory.CustomWroConfiguration;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.LdapAuthenticator;
import com.hlag.oversigt.security.MapAuthenticator;
import com.hlag.oversigt.storage.SqlDialect;

import io.jsonwebtoken.SignatureAlgorithm;

public class OversigtConfigurationModule extends AbstractModule {
	private static final Logger LOGGER = LoggerFactory.getLogger(OversigtConfigurationModule.class);

	private static final TypeLiteral<Map<String, String>> MAP_OF_STRING_TO_STRING
			= new TypeLiteral<Map<String, String>>() {
				// just type literal for generics detection
			};

	private static final TypeLiteral<List<String>> LIST_OF_STRINGS = new TypeLiteral<List<String>>() {
		// just type literal for generics detection
	};

	private static final TypeLiteral<List<HttpListenerConfiguration>> LIST_OF_HTTP_LISTENER_CONFIGURATION
			= new TypeLiteral<List<HttpListenerConfiguration>>() {
				// just type literal for generics detection
			};

	private final OversigtConfiguration config;

	private final boolean debugFallback;

	private final String ldapBindPasswordFallback;

	public OversigtConfigurationModule(final boolean debugFallback, final String ldapBindPasswordFallback) {
		config = OversigtConfiguration.readConfiguration();
		this.debugFallback = debugFallback;
		this.ldapBindPasswordFallback = ldapBindPasswordFallback;
	}

	private ConstantBindingBuilder bind(final String name) {
		return bindConstant().annotatedWith(Names.named(name));
	}

	@Override
	protected void configure() {
		// config itself
		bind(OversigtConfiguration.class).toInstance(config);

		// debug
		bind("debug").to(config.isDebug() || debugFallback);

		// template handling
		bind(CustomWroConfiguration.class).toInstance(config.getWro());

		// api settings
		bind("hostname").to(config.getHostname().orElseThrow(() -> new RuntimeException("No hostname specified")));
		bind(SignatureAlgorithm.class).toInstance(config.getApi().getJwtAlgorithm());
		bind("api.secret.base64")
				.to(Objects.requireNonNull(config.getApi().getJwtSecretBase64(), "api.jwtSecretBase64"));
		bind("api.ttl").to(config.getApi().getJwtTimeToLive());
		bind("rateLimit").to(config.getEventManager().getRateLimit());
		bind(Duration.class).annotatedWith(Names.named("discardEventsAfter"))
				.toInstance(config.getEventManager().getDiscardEventsAfter());
		bind("templateNumberFormat").to(config.getTemplateNumberFormat());

		// database
		final DatabaseConfiguration database = config.getDatabase();
		bind("databaseLocation").to(database.getLocation());
		bind("databaseName").to(database.getName());
		bind("databaseUsername").to(database.getUsername());
		bind("databasePassword").to(database.getPassword());
		bind(SqlDialect.class).to(database.getSqlDialect());

		// http server config
		bind(LIST_OF_HTTP_LISTENER_CONFIGURATION).annotatedWith(Names.named("listeners"))
				.toInstance(config.getListeners());

		// foreign events
		bind("foreignEvents.enabled").to(config.getSecurity().getForeignEvents().isEnabled());
		bind("foreignEvents.needAuthentication").to(config.getSecurity().getForeignEvents().isNeedAuthentication());
		bind("foreignEvents.apiKeyHeaderName").to(config.getSecurity().getForeignEvents().getApiKeyHeaderName());
		bind(LIST_OF_STRINGS).annotatedWith(Names.named("foreignEvents.allowedApiKeys"))
				.toInstance(config.getSecurity().getForeignEvents().getAllowedApiKeys());

		// event sources
		bindNamedStringArray("additionalPackages", config.getEventSources().getPackages());
		bindNamedStringArray("widgetsPaths", config.getEventSources().getWidgetsPaths());
		bindNamedArray(Path[].class, "addonFolders", Paths::get, config.getEventSources().getAddonFolders());

		// Mail Settings
		bind("mailSenderHost").to(
				config.getMail().getHostname().orElseThrow(() -> new RuntimeException("No mail hostname configured.")));
		bind("mailSenderPort").to(config.getMail().getPort());
		bind("mailSenderStartTls").to(config.getMail().isStartTls());
		bind("mailSenderUsername").to(config.getMail().getUsername().orElse(""));
		bind("mailSenderPassword").to(config.getMail().getPassword().orElse(""));
		bind("mailSenderAddress").to(config.getMail().getSenderAddress());

		// Security
		boolean boundAuthenticator = false;
		if (config.getSecurity().getLdap().isPresent()) {
			final LdapConfiguration ldapConfiguration = config.getSecurity().getLdap().get();
			if (!ldapConfiguration.isBindPasswordSet()) {
				ldapConfiguration.setBindPassword(ldapBindPasswordFallback);
			}
			if (ldapConfiguration.isBindPasswordSet()) {
				bind(LdapConfiguration.class).toInstance(ldapConfiguration);
				bind(Authenticator.class).to(LdapAuthenticator.class);
				boundAuthenticator = true;
			}
		}
		if (!boundAuthenticator && !config.getSecurity().getUsers().isEmpty()) {
			bind(MAP_OF_STRING_TO_STRING).annotatedWith(Names.named("UsernamesAndPasswords"))
					.toInstance(config.getSecurity().getUsers());
			bind(Authenticator.class).to(MapAuthenticator.class);
			boundAuthenticator = true;
		}
		final List<String> admins = config.getSecurity().getServerAdmins().orElseGet(ArrayList::new);
		bind(LIST_OF_STRINGS).annotatedWith(Names.named("serverAdmins")).toInstance(admins);
		if (admins.isEmpty()) {
			LOGGER.warn("No server admins configured. Please check configuration security.serverAdmins");
		}

		// JIRA
		bind("jira.socketTimeout").to(config.getJira().getSocketTimeout());
	}

	private void bindNamedStringArray(final String name, final String[] inputs) {
		bindNamedArray(String[].class, name, Function.identity(), inputs);
	}

	@SuppressWarnings("unchecked")
	private <T, I> void bindNamedArray(final Class<T[]> targetClass,
			final String name,
			final Function<I, T> converter,
			final I[] inputs) {
		bind(targetClass).annotatedWith(Names.named(name))
				.toInstance((T[]) Stream.of(inputs)
						.map(converter)
						.collect(Collectors.toList())
						.toArray(createArray(targetClass.getComponentType(), 0)));
	}
}
