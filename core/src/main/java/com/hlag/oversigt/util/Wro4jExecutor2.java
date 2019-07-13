/**
 * Copyright Alex Objelean
 */
package com.hlag.oversigt.util;

import static java.util.stream.Collectors.toList;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources;
import com.hlag.oversigt.util.TypeUtils.ClassProxy;
import com.hlag.oversigt.util.TypeUtils.ReturnValue;

import edu.umd.cs.findbugs.annotations.Nullable;
import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.config.Context;
import ro.isdc.wro.config.jmx.WroConfiguration;
import ro.isdc.wro.http.support.DelegatingServletOutputStream;
import ro.isdc.wro.manager.factory.ConfigurableWroManagerFactory;
import ro.isdc.wro.manager.factory.WroManagerFactory;
import ro.isdc.wro.model.WroModel;
import ro.isdc.wro.model.factory.WroModelFactory;
import ro.isdc.wro.model.group.Group;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.locator.UriLocator;
import ro.isdc.wro.model.resource.locator.factory.AbstractUriLocatorFactory;
import ro.isdc.wro.model.resource.locator.factory.UriLocatorFactory;

/**
 * Defines most common properties used by wro4j build-time solution
 * infrastructure.
 *
 * @author Alex Objelean
 */
public class Wro4jExecutor2 {
	private static final Logger LOGGER = LoggerFactory.getLogger(Wro4jExecutor2.class);

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

	private static final String GROUP = "group";

	private final WroManagerFactory managerFactory;

	@SuppressWarnings("checkstyle:XIllegalCatchDefault")
	public Wro4jExecutor2() {
		try {
			Context.set(Context.standaloneContext());
			final ConfigurableWroManagerFactory managerFactory = new CustomWroManagerFactory();
			this.managerFactory = managerFactory;
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

	private static final class CustomWroManagerFactory extends ConfigurableWroManagerFactory {

		@Override
		protected UriLocatorFactory newUriLocatorFactory() {
			return new AbstractUriLocatorFactory() {
				@Override
				public UriLocator getInstance(@SuppressWarnings("unused") @Nullable final String uri) {
					return new CustomUriLocator();
				}
			};
		}

		@Override
		protected WroModelFactory newModelFactory() {
			return new WroModelFactory() {
				@Override
				public WroModel create() {
					final WroModel model = new WroModel();
					final Group group = new Group(GROUP);
					model.addGroup(group);
					group.addResource(Resource.create("statics/dashing/jquery.js", ResourceType.JS));
					group.addResource(Resource.create("statics/dashing/batman.js", ResourceType.JS));
					return model;
				}

				@Override
				public void destroy() {
					// nothing to do
				}
			};
		}

		// @Override
		// protected ProcessorsFactory newProcessorsFactory() {
		// return new ConfigurableProcessorsFactory() {
		// @Override
		// protected Properties newProperties() {
		// final Map<String, Object> settings = Utils
		// .map("preProcessors", "cssUrlRewriting,coffeeScript", "postProcessors",
		// "rubySassCss");
		// final Properties properties = new Properties();
		// properties.putAll(settings);
		// return properties;
		// }
		// };
		// }
	}

	private static final class CustomUriLocator implements UriLocator {
		@Override
		public InputStream locate(@Nullable final String nullableUri) throws IOException {
			final String uri = Objects.requireNonNull(nullableUri);
			try {
				final URL url = Resources.getResource(uri);
				return Files.newInputStream(Paths.get(url.toURI()));
			} catch (@SuppressWarnings("unused") final IllegalArgumentException e) {
				throw new FileNotFoundException(uri);
			} catch (final URISyntaxException e) {
				throw new IOException("Cannot convert URL to URI", e);
			}
		}

		@Override
		public boolean accept(@Nullable final String uri) {
			// XXX check this
			System.out.println(uri);
			return true;
		}
	}

	public static final class WroGroupContent {
		private final Map<ResourceType, Collection<URI>> paths = new HashMap<>();

		public WroGroupContent() {
			// nothing to do
		}

		private List<Pattern> createMatchers(final List<String> patterns) {
			return patterns.stream().map(FileUtils::toRegex).map(Pattern::compile).collect(toList());
		}

		public void addFiltered(final Stream<Path> paths, final CustomWroConfiguration config) {
			final Map<ResourceType, Collection<URI>> abc = new HashMap<>();
			abc.put(ResourceType.JS, new ArrayList<>());
			abc.put(ResourceType.CSS, new ArrayList<>());
			final List<Pattern> jsMatchers = createMatchers(config.types.get(ResourceType.JS));
			final List<Pattern> cssMatchers = createMatchers(config.types.get(ResourceType.CSS));

			paths.forEach(path -> {
				final String pathString = path.toAbsolutePath().toString();
				if (jsMatchers.stream().anyMatch(m -> m.matcher(pathString).find())) {
					abc.get(ResourceType.JS).add(path.toUri());
				} else if (cssMatchers.stream().anyMatch(m -> m.matcher(pathString).find())) {
					abc.get(ResourceType.CSS).add(path.toUri());
				}
			});
			this.paths.putAll(abc);
		}

		// public void addPaths(final ResourceType type, final Collection<Path> paths) {
		// this.paths.computeIfAbsent(type, x -> new ArrayList<>()).addAll(paths);
		// }

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
	}
}
