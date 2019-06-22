package com.hlag.oversigt.core;

import static com.google.common.io.Resources.getResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;
import java.util.Properties;

import com.google.common.io.CharStreams;
import com.google.common.io.Resources;

import edu.umd.cs.findbugs.annotations.Nullable;
import ro.isdc.wro.extensions.processor.css.RubySassCssProcessor;
import ro.isdc.wro.extensions.processor.js.CoffeeScriptProcessor;
import ro.isdc.wro.manager.factory.ConfigurableWroManagerFactory;
import ro.isdc.wro.model.factory.WroModelFactory;
import ro.isdc.wro.model.factory.XmlModelFactory;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.processor.decorator.ProcessorDecorator;
import ro.isdc.wro.model.resource.processor.factory.ProcessorsFactory;
import ro.isdc.wro.model.resource.processor.factory.SimpleProcessorsFactory;
import ro.isdc.wro.model.resource.processor.impl.css.CssUrlRewritingProcessor;

/**
 * Wro4j Manager Factory. Optimized for built-in usage
 *
 * @author avarabyeu
 */
class WroManagerFactory extends ConfigurableWroManagerFactory {

	private static final String COFFEE_FILENAME = ".coffee";

	@Override
	protected ProcessorsFactory newProcessorsFactory() {
		final SimpleProcessorsFactory factory = new SimpleProcessorsFactory();
		factory.addPreProcessor(new ProcessorDecorator(new CoffeeScriptProcessor()) {
			@Override
			public void process(@Nullable final Resource resource,
					@Nullable final Reader reader,
					@Nullable final Writer writer) throws IOException {
				if (Objects.requireNonNull(resource).getUri().endsWith(COFFEE_FILENAME)) {
					super.process(resource, reader, writer);
				} else {
					CharStreams.copy(reader, writer);
				}
			}
		});
		factory.addPreProcessor(new CssUrlRewritingProcessor());
		factory.addPostProcessor(new RubySassCssProcessor());

		return factory;
	}

	@Override
	protected WroModelFactory newModelFactory() {
		return new XmlModelFactory() {
			@Override
			protected InputStream getModelResourceAsStream() throws IOException {
				return Resources.asByteSource(getResource("wro.xml")).openStream();
			}
		};
	}

	@Override
	protected Properties newConfigProperties() {
		final Properties properties = new Properties();
		try (InputStream is = Resources.asByteSource(Resources.getResource("wro.properties")).openStream()) {
			properties.load(is);
		} catch (final IOException e) {
			throw new RuntimeException("Unable to load wro configuration", e);
		}

		return properties;
	}

}
