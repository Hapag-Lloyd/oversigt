/**
 * Copyright Alex Objelean
 */
package com.hlag.oversigt.web.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hlag.oversigt.util.TypeUtils.ClassProxy;
import com.hlag.oversigt.util.TypeUtils.ReturnValue;

import edu.umd.cs.findbugs.annotations.Nullable;
import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.config.Context;
import ro.isdc.wro.config.factory.PropertyWroConfigurationFactory;
import ro.isdc.wro.config.jmx.WroConfiguration;
import ro.isdc.wro.http.support.DelegatingServletOutputStream;
import ro.isdc.wro.manager.factory.standalone.ConfigurableStandaloneContextAwareManagerFactory;
import ro.isdc.wro.manager.factory.standalone.StandaloneContext;
import ro.isdc.wro.manager.factory.standalone.StandaloneContextAware;
import ro.isdc.wro.model.factory.ConfigurableModelFactory;
import ro.isdc.wro.model.factory.WroModelFactory;
import ro.isdc.wro.model.factory.XmlModelFactory;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.locator.UriLocator;
import ro.isdc.wro.model.resource.locator.factory.ConfigurableLocatorFactory;
import ro.isdc.wro.model.resource.locator.factory.UriLocatorFactory;

/**
 * Defines most common properties used by wro4j build-time solution
 * infrastructure.
 *
 * @author Alex Objelean
 */
public class Wro4jExecutor {
	private static final Logger LOGGER = LoggerFactory.getLogger(Wro4jExecutor.class);

	private static final String XML_TEMPLATE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<groups xmlns=\"http://www.isdc.ro/wro\">\n"
			+ "    <group name=\"{{groupName}}\">\n"
			+ "        <js>classpath:statics/widgets/{{viewName}}/**.js</js>\n"
			+ "        <js>classpath:statics/widgets/{{viewName}}/**.coffee</js>\n"
			+ "        <css>classpath:statics/widgets/{{viewName}}/**.scss</css>\n"
			+
			// " <css>classpath:statics/widgets/{{viewName}}/**.css</css>\n" +
			"    </group>\n"
			+ "</groups>\n";

	private static final String WRO_PROPERTIES
			= "preProcessors=cssUrlRewriting,coffeeScript\r\npostProcessors=rubySassCss";

	private static final String GROUP = "group";

	private final ConfigurableWroManagerFactory managerFactory;

	@SuppressWarnings("checkstyle:XIllegalCatchDefault")
	public Wro4jExecutor(final String viewName, final boolean minimize) {
		try {
			Context.set(Context.standaloneContext());
			managerFactory = new ConfigurableWroManagerFactory(WRO_PROPERTIES,
					XML_TEMPLATE.replace("{{groupName}}", GROUP).replace("{{viewName}}", viewName));

			final StandaloneContext runContext = new StandaloneContext();
			runContext.setContextFoldersAsCSV(".");
			runContext.setMinimize(minimize);
			runContext.setIgnoreMissingResourcesAsString("true");
			managerFactory.initialize(runContext);
		} catch (final RuntimeException e) {
			throw WroRuntimeException.wrap(e);
		}
	}

	public final Optional<String> execute(final ResourceType resourceType) {
		final String groupWithExtension = GROUP + "." + resourceType.name().toLowerCase();

		// mock request
		final HttpServletRequest request = ClassProxy.create(HttpServletRequest.class,
				ReturnValue.find(HttpServletRequest.class, "getContextPath", "/"),
				ReturnValue.find(HttpServletRequest.class, "getRequestURI", groupWithExtension));

		// mock response
		try (ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
				DelegatingServletOutputStream delegatingServletOutputStream
						= new DelegatingServletOutputStream(resultOutputStream)) {
			final HttpServletResponse response = ClassProxy.create(HttpServletResponse.class,
					ReturnValue.find(HttpServletResponse.class, "getOutputStream", delegatingServletOutputStream));

			// init context
			final WroConfiguration config = Context.get().getConfig();
			config.setIgnoreEmptyGroup(true);
			Context.set(Context.webContext(request, response, ClassProxy.create(FilterConfig.class)), config);

			// perform processing
			managerFactory.create().process();
			return Optional.of(new String(resultOutputStream.toByteArray(), StandardCharsets.UTF_8));
		} catch (final Exception e) {
			LOGGER.error("Unable to execute WRO", e);
			return Optional.empty();
		}
	}

	/**
	 * Default implementation which use a property file to read the pre & post
	 * processors to be used during processing.
	 *
	 * @author Alex Objelean
	 * @created 2 Aug 2011
	 * @since 1.4.0
	 */
	private static class ConfigurableWroManagerFactory extends ConfigurableStandaloneContextAwareManagerFactory {
		private final String properties;

		private final String wroXml;

		ConfigurableWroManagerFactory(final String properties, final String wroXml) {
			this.properties = properties;
			this.wroXml = wroXml;
		}

		@Override
		public void initialize(@Nullable final StandaloneContext standaloneContext) {
			Context.get().setConfig(initConfiguration());
			super.initialize(standaloneContext);
		}

		private WroConfiguration initConfiguration() {
			return new PropertyWroConfigurationFactory(createProperties()).create();
		}

		@Override
		protected WroModelFactory newModelFactory() {
			return new ConfigurableModelFactory() {
				@Override
				protected Properties newProperties() {
					return createProperties();
				}

				@Override
				protected WroModelFactory getDefaultStrategy() {
					return new XmlModelFactory() {
						@Override
						protected InputStream getModelResourceAsStream() throws IOException {
							return new ByteArrayInputStream(wroXml.getBytes(StandardCharsets.UTF_8));
						}
					};
				}
			};
		}

		@Override
		protected UriLocatorFactory newUriLocatorFactory() {
			return new ConfigurableLocatorFactory() {
				@Override
				public UriLocator getInstance(@Nullable final String uri) {
					final UriLocator locator = super.getInstance(uri);
					// ensure standalone context is provided to each locator requiring it for
					// initialization.
					if (locator instanceof StandaloneContextAware) {
						((StandaloneContextAware) locator).initialize(getStandaloneContext());
					}
					return locator;
				}

				@Override
				protected Properties newProperties() {
					return createProperties();
				}
			};
		}

		@Override
		protected Properties createProperties() {
			try {
				final Reader reader = new StringReader(properties);
				final Properties properties = new Properties();
				properties.load(reader);
				return properties;
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}
