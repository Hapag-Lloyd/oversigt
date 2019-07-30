package com.hlag.oversigt.core.configuration;

import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.core.Application;

import org.hibernate.validator.internal.util.privilegedactions.NewInstance;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.core.Oversigt;
import com.hlag.oversigt.security.OversigtIdentityManager;
import com.hlag.oversigt.util.TypeUtils;
import com.hlag.oversigt.validate.UserId;
import com.hlag.oversigt.web.api.ApiApplication;
import com.hlag.oversigt.web.api.ApiAuthenticationUtils;

import edu.umd.cs.findbugs.annotations.Nullable;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.TemplateExceptionHandler;
import io.undertow.security.idm.IdentityManager;

public class WebModule extends AbstractModule {
	@Override
	protected void configure() {
		// REST API
		binder().bind(IdentityManager.class).to(OversigtIdentityManager.class);
		binder().bind(Application.class).to(ApiApplication.class);
		binder().bind(ApiAuthenticationUtils.class);

		// Object validation
		TypeUtils.bindClasses(UserId.class.getPackage(), ConstraintValidator.class::isAssignableFrom, binder());
		final GuiceConstraintValidatorFactory constraintValidatorFactory = new GuiceConstraintValidatorFactory();
		requestInjection(constraintValidatorFactory);
		final Validator validator = Validation.buildDefaultValidatorFactory()
				.usingContext()
				.constraintValidatorFactory(constraintValidatorFactory)
				.getValidator();
		binder().bind(Validator.class).toInstance(validator);

	}

	/**
	 * Create the configuration for the template engine used to render HTML output
	 *
	 * @param templateNumberFormat the number format to be used by the rendering
	 *                             engine
	 * @return the configuration
	 */
	@Singleton
	@Provides
	@Inject
	freemarker.template.Configuration provideTemplateConfiguration(
			@Named("templateNumberFormat") final String templateNumberFormat) {
		final freemarker.template.Configuration configuration
				= new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_23);
		configuration.setTemplateLoader(new ClassTemplateLoader(Oversigt.class, "/"));
		configuration.setNumberFormat(templateNumberFormat);
		configuration.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
		return configuration;
	}

	/**
	 * Constraint validator to check each the API calls for valid values
	 */
	class GuiceConstraintValidatorFactory implements ConstraintValidatorFactory {

		@Inject
		private Injector injector;

		GuiceConstraintValidatorFactory() {
			// no fields to be initialized manually, some will be injected
		}

		/** {@inheritDoc} */
		@Override
		public <T extends ConstraintValidator<?, ?>> T getInstance(@Nullable final Class<T> key) {
			/*
			 * By default, all beans are in prototype scope, so new instance will be
			 * obtained each time. Validator implementer may declare it as singleton and
			 * manually maintain internal state (to re-use validators and simplify life for
			 * GC)
			 */
			final boolean bound = injector.getBindings()//
					.keySet()
					.stream()
					.map(k -> k.getTypeLiteral().getRawType())
					.anyMatch(k -> key != null && key.equals(k));
			if (bound) {
				return injector.getInstance(key);
			}
			return run(NewInstance.action(key, "ConstraintValidator"));
		}

		/** {@inheritDoc} */
		@Override
		public void releaseInstance(@SuppressWarnings("unused") @Nullable final ConstraintValidator<?, ?> instance) {
			/* Garbage collector will do it */
		}

		private <T> T run(final PrivilegedAction<T> action) {
			return System.getSecurityManager() != null ? AccessController.doPrivileged(action) : action.run();
		}
	}
}
