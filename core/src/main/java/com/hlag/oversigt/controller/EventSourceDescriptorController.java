package com.hlag.oversigt.controller;

import static com.hlag.oversigt.util.Utils.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Service;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;
import com.hlag.oversigt.model.EventSourceDescriptor;
import com.hlag.oversigt.model.EventSourceProperty;
import com.hlag.oversigt.properties.JsonBasedData;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.sources.MotivationEventSource;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.util.FileUtils;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.StringUtils;
import com.hlag.oversigt.util.TypeUtils;
import com.hlag.oversigt.util.UiUtils;
import com.hlag.oversigt.util.Utils;

import de.larssh.utils.Collectors;
import de.larssh.utils.function.ThrowingFunction;

@Singleton
public class EventSourceDescriptorController {
	private static final Logger LOGGER = LoggerFactory.getLogger(EventSourceDescriptorController.class);

	private static final Package CORE_EVENT_SOURCE_PACKAGE = ScheduledEventSource.class.getPackage();

	// TODO make this constant configurable
	private static final Collection<Package> PACKAGES_TO_SCAN = Arrays.asList(MotivationEventSource.class.getPackage());

	private static final Collection<String> RESERVED_DATA_BINDINGS = Arrays.asList("title");

	// Event Sources
	// this field will be written once and then only read several times -> no
	// synchronization needed
	private final Collection<EventSourceDescriptor> eventSourceDescriptors = new HashSet<>();

	private final Collection<Path> addonFolders;

	private final Collection<String> widgetsPaths;

	@Inject
	public EventSourceDescriptorController(@Named("addonFolders") final Path[] addonFolders,
			@Named("widgetsPaths") final String[] widgetsPaths) {
		this.addonFolders = Arrays.asList(addonFolders);
		this.widgetsPaths = Arrays.asList(widgetsPaths);
	}

	public void loadEventSourceDescriptors() {
		// load event sources without class
		LOGGER.info("Scanning resources paths for EventSources: {}", widgetsPaths.stream().collect(joining(", ")));
		final List<EventSourceDescriptor> descriptorsFromResources = loadMultipleEventSourceFromResources(widgetsPaths);
		LOGGER.info("Loaded {} EventSources", descriptorsFromResources.size());

		// load event sources from classes
		LOGGER.info("Scanning packages for EventSources: {} ",
				PACKAGES_TO_SCAN.stream().map(Package::getName).collect(joining(", ")));
		final List<EventSourceDescriptor.Builder> descriptorsFromClasses = PACKAGES_TO_SCAN.stream()
				.flatMap(p -> TypeUtils.findClasses(p, Service.class, EventSource.class))
				.map(this::loadEventSourceFromClass)
				.collect(toList());
		LOGGER.info("Loaded {} EventSources", descriptorsFromClasses.size());

		LOGGER.info("Scanning addon folders for EventSources: {}",
				addonFolders.stream().map(Path::toAbsolutePath).map(Object::toString).collect(joining(", ")));
		final URL[] jarFileUrls = addonFolders.stream()
				.map(this::findAddonJarFiles)
				.flatMap(Collection::stream)
				.map(Path::toUri)
				.map(ThrowingFunction.throwing(URI::toURL))
				.collect(toSet())
				.toArray(new URL[0]);
		final List<String> classNamesToLoad;
		try {
			classNamesToLoad = TypeUtils.listClassesInJarFiles(jarFileUrls);
		} catch (final IOException e) {
			throw new RuntimeException("Unable to scan JAR files", e);
		}
		@SuppressWarnings("resource")
		final ClassLoader addonClassLoader
				= URLClassLoader.newInstance(jarFileUrls, ClassLoader.getSystemClassLoader());
		final List<EventSourceDescriptor.Builder> descriptorsFromAddons
				= TypeUtils.findClasses(addonClassLoader, classNamesToLoad, Service.class, EventSource.class)
						.map(this::loadEventSourceFromClass)
						.collect(toList());
		LOGGER.info("Loaded {} EventSources", descriptorsFromAddons.size());

		final List<EventSourceDescriptor.Builder> descriptorsJavaBased = new ArrayList<>();
		descriptorsJavaBased.addAll(descriptorsFromClasses);
		descriptorsJavaBased.addAll(descriptorsFromAddons);

		// add properties from views into class' event sources
		final List<EventSourceDescriptor> standAloneDescriptorsFromFileSystem
				= descriptorsFromResources.stream().filter(EventSourceDescriptor::isStandAlone).collect(toList());

		LOGGER.debug("Available view ids: {}",
				descriptorsFromResources.stream().map(EventSourceDescriptor::getView).sorted().collect(joining(", ")));
		for (final EventSourceDescriptor.Builder dfc : descriptorsJavaBased) {
			final EventSourceDescriptor descriptorForView = descriptorsFromResources.stream()
					.filter(d -> d.getView().equals(dfc.getView()))
					.findAny()
					.orElseThrow(() -> new RuntimeException("No widget found for view id: " + dfc.getView()));
			descriptorForView.getDataItems()
					.stream()
					.filter(di -> !dfc.getDataItemsToHide().contains(di.getName()))
					.forEach(dfc::addDataItem);
		}

		// Done
		eventSourceDescriptors.clear();
		eventSourceDescriptors
				.addAll(descriptorsJavaBased.stream().map(EventSourceDescriptor.Builder::build).collect(toList()));
		eventSourceDescriptors.addAll(standAloneDescriptorsFromFileSystem);
	}

	private Collection<Path> findAddonJarFiles(final Path folder) {
		try (Stream<Path> files = Files.walk(folder)) {
			return files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar")).collect(toSet());
		} catch (final IOException e) {
			throw new RuntimeException("Unable to scan path: " + folder.toAbsolutePath().toString(), e);
		}
	}

	public Collection<EventSourceKey> getEventSourceKeys() {
		return eventSourceDescriptors.stream().map(EventSourceDescriptor::getKey).collect(toList());
	}

	public EventSourceDescriptor getEventSourceDescriptor(final EventSourceKey key) {
		return eventSourceDescriptors.stream().filter(d -> d.getKey().equals(key)).findAny().get();
	}

	private EventSourceDescriptor.Builder loadEventSourceFromClass(final Class<? extends Service> serviceClass) {
		final EventSource eventSourceAnnotation = Objects.requireNonNull(serviceClass.getAnnotation(EventSource.class));

		// collect easy event source information
		final EventSourceKey key = EventSourceKey.createKeyFromClass(serviceClass);
		final String displayName = Objects.requireNonNull(eventSourceAnnotation.displayName());
		final String description = eventSourceAnnotation.description();
		final String view = Objects.requireNonNull(eventSourceAnnotation.view());
		final Class<? extends OversigtEvent> eventClass = Objects.requireNonNull(getEventClass(serviceClass));
		final Class<? extends Module> moduleClass = eventSourceAnnotation.explicitConfiguration();
		final EventSourceDescriptor.Builder builder = new EventSourceDescriptor.Builder(key,
				displayName,
				description,
				view,
				serviceClass,
				eventClass,
				moduleClass);

		// Find fields of the event
		final Set<String> eventFields = TypeUtils.getMembers(eventClass)
				.map(s -> CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, s))
				.collect(toSet());

		// add event source defined data items
		Stream.of(eventSourceAnnotation.dataItems())
				.filter(not(Strings::isNullOrEmpty))
				.filter(not(eventFields::contains))
				.map(this::createDummyEventSourceProperty)
				.forEach(builder::addDataItem);

		// list data items to be hidden from view
		Stream.of(eventSourceAnnotation.hiddenDataItems())
				.filter(not(Strings::isNullOrEmpty))
				.forEach(builder::addDataItemToHide);

		// find class properties
		final BeanInfo beanInfo;
		try {
			beanInfo = Introspector.getBeanInfo(serviceClass, Object.class);
		} catch (final IntrospectionException e) {
			throw new RuntimeException(String.format("Unable to examine class %s", serviceClass), e);
		}
		Stream.of(Objects.requireNonNull(beanInfo.getPropertyDescriptors()))
				// we need both getters and setters
				.filter(p -> Objects.nonNull(p.getReadMethod()))
				.filter(p -> Objects.nonNull(p.getWriteMethod()))
				// getters should not return anything
				.filter(p -> p.getReadMethod().getParameterCount() == 0)
				// setters should accept exactly one parameter
				.filter(p -> p.getWriteMethod().getParameterCount() == 1)
				// getters and setters from base classes are not relevant
				.filter(p -> !p.getReadMethod().getDeclaringClass().getPackage().equals(CORE_EVENT_SOURCE_PACKAGE))
				// getters and setters without annotation are not relevant
				.filter(p -> p.getReadMethod().isAnnotationPresent(Property.class)
						|| p.getWriteMethod().isAnnotationPresent(Property.class))
				// convert into our own structure
				.map(this::createEventSourceProperty)
				.forEach(builder::addProperty);

		return builder;
	}

	private EventSourceProperty createDummyEventSourceProperty(final String name) {
		return new EventSourceProperty(name, name, "", "text", true, Collections.emptyMap());
	}

	private EventSourceProperty createEventSourceProperty(final PropertyDescriptor descriptor) {
		if (descriptor.getReadMethod().getAnnotation(Property.class) != null
				&& descriptor.getWriteMethod().getAnnotation(Property.class) != null) {
			throw new RuntimeException("Unable to load property '"
					+ descriptor.getName()
					+ "' from class "
					+ descriptor.getReadMethod().getDeclaringClass().getName()
					+ " because both the read and the write method are declaring a @"
					+ Property.class.getSimpleName()
					+ " annotation.");
		}

		// find Property annotation
		final Property property = Utils.getOne(//
				descriptor.getReadMethod().getAnnotation(Property.class),
				descriptor.getWriteMethod().getAnnotation(Property.class));

		final String name = descriptor.getName();
		final String displayName = property.name();
		final String description = Strings.nullToEmpty(property.description());
		final boolean customValuesAllowed = false;
		final Method getter = descriptor.getReadMethod();
		final Method setter = descriptor.getWriteMethod();
		final Class<?> clazz = descriptor.getPropertyType();
		final Optional<JsonHint> hint = findJsonHint(descriptor);
		final boolean json = !UiUtils.hasDedicatedEditor(clazz);

		final String inputType = getType(name,
				Optional.ofNullable(Strings.emptyToNull(property.type())),
				Optional.of(clazz),
				json,
				Collections.emptyList());
		final Map<String, String> allowedValues = collectAllowedValues(clazz);

		final EventSourceProperty esProperty = new EventSourceProperty(name,
				displayName,
				description,
				inputType,
				customValuesAllowed,
				allowedValues,
				getter,
				setter,
				clazz,
				hint,
				json,
				json ? Optional.of(JsonUtils.toJsonSchema(clazz, hint)) : Optional.empty());

		return esProperty;
	}

	private Optional<JsonHint> findJsonHint(final PropertyDescriptor descriptor) {
		Optional<JsonHint> hint = Optional.ofNullable(descriptor.getReadMethod().getAnnotation(JsonHint.class));
		if (hint.isPresent()) {
			return hint;
		}
		hint = Optional.ofNullable(descriptor.getWriteMethod().getAnnotation(JsonHint.class));
		if (hint.isPresent()) {
			return hint;
		}
		Class<?> tmpClass = descriptor.getPropertyType();
		while (tmpClass.isArray()) {
			tmpClass = tmpClass.getComponentType();
		}
		return Optional.ofNullable(tmpClass.getAnnotation(JsonHint.class));
	}

	private EventSourceProperty createEventSourceProperty(final String name, final Properties properties) {
		final String displayName = properties.getProperty("dataItem." + name + ".title", name);
		final String description = Strings.nullToEmpty(properties.getProperty("dataItem." + name + ".description"));
		final String inputType = properties.getProperty("dataItem." + name + ".type", "text");
		final boolean customValuesAllowed
				= Boolean.parseBoolean(properties.getProperty("dataItem." + name + ".customValuesAllowed", "false"));
		final Map<String, String> allowedValues
				= StringUtils.list(properties.getProperty("dataItem." + name + ".values"))
						.stream()
						.collect(Collectors.toLinkedHashMap(Function.identity(), Function.identity()));

		final EventSourceProperty property = new EventSourceProperty(name,
				displayName,
				description,
				getType(displayName, Optional.of(inputType), Optional.empty(), false, allowedValues.keySet()),
				customValuesAllowed,
				allowedValues);
		return property;
	}

	private Map<String, String> collectAllowedValues(final Class<?> clazz) {
		if (clazz.isEnum()) {
			return Stream.of(clazz.getEnumConstants())
					.map(e -> (Enum<?>) e)
					.collect(Collectors.toLinkedHashMap(Enum::name, Enum::toString));
		}
		if (clazz == ZoneId.class) {
			return ZoneId.getAvailableZoneIds()
					.stream()
					.sorted(String::compareToIgnoreCase)
					.collect(Collectors.toLinkedHashMap(Function.identity(), Function.identity()));
		}
		if (clazz == Locale.class) {
			return Stream.of(Locale.getAvailableLocales())
					.sorted((a, b) -> a.getDisplayName().compareTo(b.getDisplayName()))
					.collect(Collectors.toLinkedHashMap(l -> l.toString().toLowerCase(), Locale::getDisplayName));
		}
		return Collections.emptyMap();
	}

	private String getType(final String name,
			final Optional<String> propertyType,
			final Optional<Class<?>> optionalType,
			final boolean json,
			final Collection<?> allowedValues) {
		if (propertyType.isPresent() && propertyType.get().trim().length() > 0) {
			return propertyType.get();
		} else if (optionalType.isPresent()) {
			final Class<?> type = optionalType.get();
			if (Enum.class.isAssignableFrom(type) || !allowedValues.isEmpty()) {
				return "enum";
			} else if (json || TypeUtils.isOfType(type, JsonBasedData.class)) {
				return "json";
			} else if (type == LocalDate.class) {
				return "date";
			} else if (type == LocalTime.class) {
				return "time";
			} else if (type == LocalDateTime.class) {
				return "datetime-local";
			} else if (type == ZonedDateTime.class) {
				return "datetime";
			} else if (type == Duration.class) {
				return "duration";
			} else if (type == boolean.class) {
				return "boolean";
			} else if (SerializableProperty.class.isAssignableFrom(type)) {
				return "value_" + type.getSimpleName();
			} else if (type == int.class
					|| type == long.class
					|| type == short.class
					|| type == byte.class
					|| type == Integer.class
					|| type == Long.class
					|| type == Short.class
					|| type == Byte.class) {
						return "number";
					} else if (name.toLowerCase().contains("password")) {
						return "password";
					} else if (type == URL.class
							|| type == String.class
									&& (name.toLowerCase().endsWith("url") || name.toLowerCase().startsWith("url"))) {
										return "url";
									} else {
										return "text";
									}
		} else {
			throw new RuntimeException("Unable to determine type for: " + name);
		}
	}

	private List<EventSourceDescriptor> loadMultipleEventSourceFromResources(final Collection<String> widgetsPaths) {
		final Collection<String> allowedPaths = widgetsPaths.stream()
				.flatMap(wp -> Stream.of(wp.replace('\\', '/'), wp.replace('/', '\\')))
				.collect(toSet());

		final Predicate<Path> allowedPathsFilter = path -> {
			final String filename = path.getFileName().toString();
			final String fullpath = path.toString();
			return filename.toLowerCase().endsWith(".html") && allowedPaths.stream().anyMatch(fullpath::contains);
		};

		return FileUtils.streamResourcesFromClasspath()
				.filter(allowedPathsFilter)
				.map(Path::getParent)
				.map(this::loadEventSourceFromPathOrFail)
				.filter(not(Objects::isNull))
				.collect(toList());
	}

	private EventSourceDescriptor loadEventSourceFromPathOrFail(final Path folder) {
		try {
			return loadEventSourceFromPath(folder);
		} catch (final IOException e) {
			throw new RuntimeException("Unable to load widget from: " + folder, e);
		}
	}

	private EventSourceDescriptor loadEventSourceFromPath(final Path folder) throws IOException {
		LOGGER.trace("Inspecting path: %s", folder);

		// find name of EventSource
		final String name = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, folder.getFileName().toString());
		String displayName = name;
		final EventSourceKey key = EventSourceKey.createKeyFromWidget(name, name);
		Optional<String> description = Optional.empty();

		// other info
		final boolean standAlone;

		// load additional information
		final Path prop = folder.resolve(folder.getFileName().toString() + ".properties");
		final Properties properties = new Properties();
		if (Files.exists(prop)) {
			try (Reader in = Files.newBufferedReader(prop)) {
				properties.load(in);
			}

			// Check if this view is already used and if yes if it may be used stand-alone
			standAlone
					= Boolean.parseBoolean(Strings.nullToEmpty(properties.getProperty("standalone-available", "true")));

			// maybe change display name
			final String newName = Strings.emptyToNull(properties.getProperty("displayName"));
			if (newName != null) {
				displayName = newName;
			}

			description = Optional.ofNullable(properties.getProperty("description"));
		} else {
			standAlone = true;
		}

		final EventSourceDescriptor.Builder builder
				= new EventSourceDescriptor.Builder(key, displayName, description, name, standAlone);

		// Load data items
		final Set<String> dataItems = new HashSet<>();
		addDataItemsFromHtml(dataItems, folder);
		addDataItemsFromCoffeeScript(dataItems, folder);
		dataItems.removeAll(RESERVED_DATA_BINDINGS);
		dataItems.removeAll(StringUtils.list(properties.getProperty("hiddenDataItems")));
		dataItems.addAll(StringUtils.list(properties.getProperty("additionalDataItems")));

		dataItems.stream().map(d -> createEventSourceProperty(d, properties)).forEach(builder::addDataItem);

		return builder.build();
	}

	private static void addDataItemsFromHtml(final Collection<String> dataItems, final Path folder) throws IOException {
		final Path html = folder.resolve(folder.getFileName().toString() + ".html");
		if (Files.exists(html)) {
			dataItems.addAll(Utils.findDataBindings(Files.lines(html)));
		}
	}

	private static void addDataItemsFromCoffeeScript(final Collection<String> dataItems, final Path folder)
			throws IOException {
		final Path coffee = folder.resolve(folder.getFileName().toString() + ".coffee");
		if (Files.exists(coffee)) {
			final List<String> lines = Files.lines(coffee)
					.filter(l -> !l.trim().startsWith("#"))
					.filter(not(Strings::isNullOrEmpty))
					.collect(toList());
			final Set<String> gets = Utils.findGets(lines);
			final Set<String> sets = Utils.findSets(lines);
			dataItems.addAll(gets);
			dataItems.removeAll(sets);
		}
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends OversigtEvent> getEventClass(final Class<? extends Service> clazz) {
		final Optional<Method> method = TypeUtils.getMethod(clazz,
				Arrays.asList("produceEventFromData", "produceCachedEvent", "produceEvent"),
				new Class<?>[0]);
		if (!method.isPresent()) {
			return OversigtEvent.class;
		}

		final Class<?> rawTypeOfOptional;
		if (method.get().getReturnType() == Optional.class) {
			final ParameterizedType parameterizedType = (ParameterizedType) method.get().getGenericReturnType();
			final Type typeOfOptional = parameterizedType.getActualTypeArguments()[0];
			if (typeOfOptional instanceof TypeVariable<?>) {
				final TypeVariable<?> typeVariable = (TypeVariable<?>) typeOfOptional;
				final Type boundary = typeVariable.getBounds()[0];
				if (boundary instanceof Class<?>) {
					rawTypeOfOptional = (Class<?>) boundary;
				} else {
					throw new RuntimeException(String.format(
							"Unable to inspect optional return value of method [%s]. Type in question: %s",
							method.toString(),
							parameterizedType));
				}
			} else if (typeOfOptional instanceof Class<?>) {
				rawTypeOfOptional = (Class<?>) typeOfOptional;
			} else if (typeOfOptional instanceof ParameterizedType) {
				rawTypeOfOptional = (Class<?>) ((ParameterizedType) typeOfOptional).getRawType();
			} else {
				throw new RuntimeException("Unknown type: " + typeOfOptional.getClass().getName());
			}
		} else {
			rawTypeOfOptional = method.get().getReturnType();
		}

		if (TypeUtils.isOfType(rawTypeOfOptional, OversigtEvent.class)) {
			return (Class<? extends OversigtEvent>) rawTypeOfOptional;
		}
		throw new RuntimeException(String.format("Event producing method [%s] does not return event of type %s",
				method.toString(),
				OversigtEvent.class.getName()));
	}
}
