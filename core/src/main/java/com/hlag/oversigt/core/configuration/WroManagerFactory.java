package com.hlag.oversigt.core.configuration;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.common.io.CharStreams;
import com.hlag.oversigt.util.FileUtils;
import com.hlag.oversigt.util.Utils;

import edu.umd.cs.findbugs.annotations.Nullable;
import ro.isdc.wro.extensions.processor.css.RubySassCssProcessor;
import ro.isdc.wro.extensions.processor.js.CoffeeScriptProcessor;
import ro.isdc.wro.manager.factory.ConfigurableWroManagerFactory;
import ro.isdc.wro.model.WroModel;
import ro.isdc.wro.model.factory.WroModelFactory;
import ro.isdc.wro.model.group.Group;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.processor.decorator.ProcessorDecorator;
import ro.isdc.wro.model.resource.processor.factory.ProcessorsFactory;
import ro.isdc.wro.model.resource.processor.factory.SimpleProcessorsFactory;
import ro.isdc.wro.model.resource.processor.impl.css.CssUrlRewritingProcessor;

/**
 * Wro4j Manager Factory. Optimized for built-in usage
 *
 * @author avarabyeu
 */
public class WroManagerFactory extends ConfigurableWroManagerFactory {

	private static final String COFFEE_FILENAME = ".coffee";

	private final WroGroupContent content;

	public WroManagerFactory(final WroGroupContent content) {
		this.content = content;
	}

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
		return new WroModelFactory() {

			@Override
			public WroModel create() {
				final WroModel model = new WroModel();
				model.addGroup(content.toGroup("application"));
				return model;
			}

			@Override
			public void destroy() {
				// nothing to do
			}
		};
	}

	@Override
	protected Properties newConfigProperties() {
		final Properties properties = new Properties();
		properties.putAll(Utils.map(//
				"minimizeEnabled",
				"false", //
				"disableCache",
				"true", //
				"gzipResources",
				"false", //
				"parallelPreprocessing",
				"true"));
		return properties;
	}

	public static final class WroGroupContent {
		private final Map<ResourceType, Collection<URI>> paths = new HashMap<>();

		public WroGroupContent() {
			// nothing to do
		}

		public void addFiltered(final Stream<Path> paths, final CustomWroConfiguration config) {
			final Map<ResourceType, Map<Pattern, List<URI>>> foundPathsPerPattern = new HashMap<>();
			final List<ResourceType> types = Arrays.asList(ResourceType.values());
			types.forEach(type -> foundPathsPerPattern.put(type, new LinkedHashMap<>()));
			types.forEach(type -> config.types.get(type)
					.forEach(patternString -> foundPathsPerPattern.get(type)
							.put(Pattern.compile(FileUtils.toRegex(patternString)), new ArrayList<>())));

			paths.forEach(path -> {
				final String pathString = path.toAbsolutePath().toString();
				types.forEach(type -> {
					foundPathsPerPattern.get(type).keySet().forEach(pattern -> {
						if (pattern.matcher(pathString).find()) {
							foundPathsPerPattern.get(type).get(pattern).add(path.toUri());
						}
					});
				});
			});
			types.forEach(type -> {
				this.paths.put(type,
						foundPathsPerPattern.get(type)
								.entrySet()
								.stream()
								.flatMap(e -> e.getValue().stream())
								.collect(toList()));
			});
		}

		public Group toGroup(final String name) {
			final Group group = new Group(name);
			group.setResources(Arrays.stream(ResourceType.values())
					.flatMap(type -> paths.get(type)
							.stream()
							.map(path -> path.toString())
							.map(uri -> Resource.create(uri, type)))
					.collect(toList()));
			return group;
		}
	}

	public static final class CustomWroConfiguration {
		private Map<ResourceType, List<String>> types = new HashMap<>();

		public CustomWroConfiguration() {
			// nothing to do
		}
	}
}
