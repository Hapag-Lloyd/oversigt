package com.hlag.oversigt.core;

import static com.hlag.oversigt.core.Oversigt.APPLICATION_CONFIG;
import static com.hlag.oversigt.util.JsonUtils.deserializer;
import static com.hlag.oversigt.util.JsonUtils.serializer;

import java.io.IOException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.core.Application;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import org.hibernate.validator.internal.util.privilegedactions.NewInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.rest.client.internal.async.AsynchronousHttpClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.eventbus.EventBus;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Service;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.hlag.oversigt.core.eventsource.NightlyReloaderService;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.model.EventSourceNameGenerator;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.properties.SerializablePropertyController;
import com.hlag.oversigt.security.RoleProvider;
import com.hlag.oversigt.storage.JDBCDatabase;
import com.hlag.oversigt.storage.Storage;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.MailSender;
import com.hlag.oversigt.util.TypeUtils;
import com.hlag.oversigt.util.text.TextProcessor;
import com.hlag.oversigt.validate.UserId;
import com.hlag.oversigt.web.DashboardConfigurationHandler;
import com.hlag.oversigt.web.DashboardCreationHandler;
import com.hlag.oversigt.web.EventSourceConfigurationHandler;
import com.hlag.oversigt.web.HttpServerExchangeHandler;
import com.hlag.oversigt.web.LoginHandler;
import com.hlag.oversigt.web.WelcomeHandler;
import com.hlag.oversigt.web.api.ApiApplication;
import com.hlag.oversigt.web.api.ApiAuthenticationUtils;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.TemplateExceptionHandler;

/**
 * Main application configuration module. Configures server and all necessary
 * stuff
 *
 * @author avarabyeu
 * @author noxfireone
 */
class OversigtModule extends AbstractModule {
	private static final Logger LOGGER = LoggerFactory.getLogger(OversigtModule.class);

	private final Runnable shutdownRunnable;
	private final StartOptions options;

	OversigtModule(StartOptions options, Runnable shutdownRunnable, List<Module> extensions) {
		this.shutdownRunnable = shutdownRunnable;
		this.options = options;
	}

	@Override
	protected void configure() {
		binder().requireExplicitBindings();

		// some interesting values
		binder().bind(String.class).annotatedWith(Names.named("application-id")).toInstance(
				UUID.randomUUID().toString());

		// Jira
		binder().requestStaticInjection(AsynchronousHttpClientFactory.class);
		// TextProcessor
		binder().requestStaticInjection(TextProcessor.class);

		// Add default constructors for explicit bindings
		binder().bind(OversigtServer.class);
		binder().bind(WelcomeHandler.class);
		binder().bind(LoginHandler.class);
		binder().bind(DashboardConfigurationHandler.class);
		binder().bind(DashboardCreationHandler.class);
		binder().bind(EventSourceConfigurationHandler.class);
		binder().bind(HttpServerExchangeHandler.class);
		binder().bind(OversigtEventSender.class);
		binder().bind(JsonUtils.class);
		binder().bind(RoleProvider.class);
		binder().bind(MailSender.class);

		// model
		binder().bind(DashboardController.class);
		binder().bind(EventSourceNameGenerator.class);
		binder().bind(SerializablePropertyController.class);

		// database
		binder().bind(Storage.class).to(JDBCDatabase.class);

		// REST API
		binder().bind(Application.class).to(ApiApplication.class);
		binder().bind(ApiAuthenticationUtils.class);

		// Bind needed variables
		binder().bind(Runnable.class).annotatedWith(Names.named("Shutdown")).toInstance(shutdownRunnable);
		binder().bind(Service.class).annotatedWith(Names.named("NightlyReloader")).to(NightlyReloaderService.class);

		// GSON
		Gson gson = new GsonBuilder()//
				.registerTypeAdapter(Class.class, deserializer(Class::forName))
				.registerTypeAdapter(Color.class, serializer(Color::getHexColor))
				.registerTypeAdapter(Color.class, deserializer(Color::parse))
				.registerTypeAdapter(Duration.class, serializer(Duration::toString))
				.registerTypeAdapter(Duration.class, deserializer(Duration::parse))
				.registerTypeAdapter(LocalDate.class, serializer(DateTimeFormatter.ISO_LOCAL_DATE::format))
				.registerTypeAdapter(LocalDate.class,
						deserializer(s -> LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE)))
				.create();
		binder().bind(Gson.class).toInstance(gson);

		// Jackson for our API
		SimpleModule module = new SimpleModule("Oversigt-API");
		module.addSerializer(Color.class, serializer(Color.class, Color::getHexColor));
		module.addDeserializer(Color.class, deserializer(Color.class, Color::parse));
		module.addSerializer(Duration.class, serializer(Duration.class, Duration::toString));
		module.addDeserializer(Duration.class, deserializer(Duration.class, Duration::parse));
		module.addDeserializer(ZonedDateTime.class, InstantDeserializer.ZONED_DATE_TIME);
		module.addSerializer(ZonedDateTime.class, ZonedDateTimeSerializer.INSTANCE);
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(module);
		// objectMapper.registerModule(new JavaTimeModule()); // instead the
		// InstantDeserializer and ZonedDateTimeSerializer are used directly
		objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		binder().bind(ObjectMapper.class).toInstance(objectMapper);

		// Object validation
		TypeUtils.bindClasses(UserId.class.getPackage(), ConstraintValidator.class::isAssignableFrom, binder());
		final GuiceConstraintValidatorFactory constraintValidatorFactory = new GuiceConstraintValidatorFactory();
		requestInjection(constraintValidatorFactory);
		Validator validator = Validation.buildDefaultValidatorFactory()
				.usingContext()
				.constraintValidatorFactory(constraintValidatorFactory)
				.getValidator();
		binder().bind(Validator.class).toInstance(validator);

		// XML
		try {
			binder().bind(DatatypeFactory.class).toInstance(DatatypeFactory.newInstance());
		} catch (DatatypeConfigurationException e) {
			throw new RuntimeException("Unable to create DatatypeFactory");
		}

		// binds properties
		Configuration configuration = readConfiguration(APPLICATION_CONFIG, gson);
		configuration.bindProperties(binder(), options.isDebugFallback(), options.getLdapBindPasswordFallback());
		if (options != null) {
			Names.bindProperties(binder(), options.getProperties());
		}
	}

	@Singleton
	@Provides
	@Inject
	EventBus provideEventBus(OversigtEventSender sender) {
		EventBus eventBus = new EventBus(Oversigt::handleEventBusException);
		eventBus.register(sender);
		return eventBus;
	}

	@Singleton
	@Provides
	@Inject
	freemarker.template.Configuration provideTemplateConfiguration(
			@Named("templateNumberFormat") String templateNumberFormat) {
		freemarker.template.Configuration configuration = new freemarker.template.Configuration(
				freemarker.template.Configuration.VERSION_2_3_23);
		configuration.setTemplateLoader(new ClassTemplateLoader(Oversigt.class, "/"));
		configuration.setNumberFormat(templateNumberFormat);
		configuration.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
		return configuration;
	}

	@Singleton
	@Provides
	com.jayway.jsonpath.Configuration provideJsonpathConfiguration() {
		final JsonProvider jsonProvider = new JacksonJsonProvider();
		final MappingProvider mappingProvider = new JacksonMappingProvider();

		final com.jayway.jsonpath.Configuration jsonpathConfiguration = com.jayway.jsonpath.Configuration.builder()
				.options(Option.DEFAULT_PATH_LEAF_TO_NULL)
				.build();
		com.jayway.jsonpath.Configuration.setDefaults(new com.jayway.jsonpath.Configuration.Defaults() {
			@Override
			public Set<Option> options() {
				return EnumSet.noneOf(Option.class);
			}

			@Override
			public MappingProvider mappingProvider() {
				return mappingProvider;
			}

			@Override
			public JsonProvider jsonProvider() {
				return jsonProvider;
			}
		});
		return jsonpathConfiguration;
	}

	private static Configuration readConfiguration(String resourceUrlString, Gson gson) {
		try {
			URL configUrl = Resources.getResource(resourceUrlString);
			Preconditions.checkState(configUrl != null, "Main application config [%s] not found", resourceUrlString);
			LOGGER.info("Reading Oversigt configuration: " + configUrl);
			String configString = Resources.toString(configUrl, Charsets.UTF_8);
			return gson.fromJson(configString, Configuration.class);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read configuration", e);
		}
	}

	public class GuiceConstraintValidatorFactory implements ConstraintValidatorFactory {

		@Inject
		private Injector injector;

		@Override
		public <T extends ConstraintValidator<?, ?>> T getInstance(final Class<T> key) {
			/*
			 * By default, all beans are in prototype scope, so new instance will be
			 * obtained each time. Validator implementer may declare it as singleton and
			 * manually maintain internal state (to re-use validators and simplify life for
			 * GC)
			 */
			boolean bound = injector.getBindings()//
					.keySet()
					.stream()
					.map(k -> k.getTypeLiteral().getRawType())
					.anyMatch(k -> key.equals(k));
			if (bound) {
				return injector.getInstance(key);
			} else {
				return run(NewInstance.action(key, "ConstraintValidator"));
			}
		}

		@Override
		public void releaseInstance(final ConstraintValidator<?, ?> instance) {
			/* Garbage collector will do it */
		}

		private <T> T run(PrivilegedAction<T> action) {
			return System.getSecurityManager() != null ? AccessController.doPrivileged(action) : action.run();
		}

	}
}