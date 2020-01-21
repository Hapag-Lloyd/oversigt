package com.hlag.oversigt.util;

import static com.hlag.oversigt.util.Utils.map;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;
import com.hlag.oversigt.storage.Storage;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import edu.umd.cs.findbugs.annotations.Nullable;

public final class JsonUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);

	private static final String SERIALIZABLE_PROPERTY_TYPE = "number";

	private static final Predicate<String> PASSWORD_FILTER = s -> !s.toLowerCase().contains("password");

	@Inject
	@Nullable
	private static Storage storage; // TODO remove this

	@Inject
	@Named("all-fields")
	@Nullable
	private static ObjectMapper allFieldObjectMapper;

	@Inject
	@Nullable
	private static Configuration jsonpathConfiguration;

	private static ObjectMapper getAllFieldsJsonConverter() {
		return Objects.requireNonNull(allFieldObjectMapper);
	}

	public static String toJson(final Object object) {
		try {
			return getAllFieldsJsonConverter().writeValueAsString(object);
		} catch (final JsonProcessingException e) {
			throw new RuntimeException("Unable to convert object to JSON", e);
		}
	}

	@Nullable
	public static <T> T fromJson(final String json, final Class<T> type) {
		// fromJson can return null!!
		try {
			return getAllFieldsJsonConverter().readValue(json, type);
		} catch (final IOException e) {
			throw new RuntimeException("Unable to parse JSON", e);
		}
	}

	@Nullable
	public static <T> T extractValueUsingJsonPath(final JsonPath jsonPath, final String json) {
		return jsonPath.read(json, jsonpathConfiguration);
	}

	/**
	 * Remove keys from the JSON string
	 *
	 * @param json   the JSON to work on
	 * @param filter determine whether a key should stay in the JSON or not. If the
	 *               {@link Predicate} returns <code>false</code> the key will be
	 *               removed.
	 * @return the stripped JSON
	 */
	public static String removeKeysFromJson(final String json, final Predicate<String> filter) {
		return toJson(removeKeys(Objects.requireNonNull(fromJson(json, Object.class)),
				filter,
				obj -> obj instanceof Double ? ((Double) obj).longValue() : obj));
	}

	/**
	 * Remove all keys from the JSON string that contain the phrase "password" (not
	 * case sensitive)
	 *
	 * @param string the JSON to work on
	 * @return the JSON without keys containing "password" (not case sensitive)
	 */
	public static String removePasswordsFromJson(final String string) {
		return removeKeysFromJson(string, PASSWORD_FILTER);
	}

	public static <T> Map<String, T> removePasswords(final Map<String, T> map) {
		removeKeys(map, PASSWORD_FILTER, Function.identity());
		return map;
	}

	@SuppressWarnings("unchecked")
	private static Object removeKeys(final Object object,
			final Predicate<String> filter,
			final Function<Object, Object> customConverter) {
		if (object instanceof Collection) {
			removeKeys((Collection<?>) object, filter, customConverter);
		} else if (object instanceof Map) {
			removeKeys((Map<String, Object>) object, filter, customConverter);
		}
		return customConverter.apply(object);
	}

	private static void removeKeys(final Collection<?> collection,
			final Predicate<String> filter,
			final Function<Object, Object> customConverter) {
		for (final Object e : collection) {
			removeKeys(e, filter, customConverter);
		}
	}

	private static void removeKeys(final Map<String, Object> map,
			final Predicate<String> filter,
			final Function<Object, Object> customConverter) {
		for (final Entry<String, Object> e : map.entrySet()) {
			if (!filter.test(e.getKey())) {
				e.setValue(null);
			} else {
				e.setValue(removeKeys(e.getValue(), filter, customConverter));
			}
		}
	}

	public static String toJsonSchema(final Class<?> clazz) {
		return toJsonSchema(clazz, Optional.empty());
	}

	@SuppressWarnings("unchecked")
	public static String toJsonSchema(final Class<?> clazz, final Optional<JsonHint> hint) {
		LOGGER.debug("Creating JSONSchema for class: " + clazz.getName());
		final Map<String, Object> schema;
		if (SerializableProperty.class.isAssignableFrom(clazz)) {
			// TODO move this conversion to another class
			schema = map("$schema",
					"http://json-schema.org/schema#",
					"$id",
					"http://schema.hlag.com/oversigt/property/" + clazz.getName(),
					"title",
					clazz.getSimpleName());
			schema.putAll(toJsonSchemaFromProperty((Class<? extends SerializableProperty>) clazz));
		} else {
			schema = map("$schema",
					"http://json-schema.org/schema#",
					"$id",
					"http://schema.hlag.com/oversigt/type/" + clazz.getName(),
					"title",
					clazz.getSimpleName());
			schema.putAll(toJsonSchemaFromType(clazz, hint));
		}
		return toJson(schema);
	}

	private static Map<String, Object> toJsonSchemaFromProperty(final Class<? extends SerializableProperty> clazz) {
		final List<? extends SerializableProperty> propertyValues
				= Objects.requireNonNull(storage).listProperties(clazz);
		final List<String> names = new ArrayList<>(
				propertyValues.stream().map(SerializableProperty::getName).collect(Collectors.toList()));
		final List<Integer> ids = new ArrayList<>(
				propertyValues.stream().map(SerializableProperty::getId).collect(Collectors.toList()));
		final List<Map<String, Object>> mapping = propertyValues.stream()
				.map(p -> map("value", p.getId(), "title", p.getName()))
				.collect(Collectors.toList());
		try {
			if (clazz.getDeclaredField("EMPTY") != null) {
				names.add(0, "\u00a0");
				ids.add(0, 0);
				mapping.add(0, map("value", 0, "title", "\u00a0"));
			}
		} catch (@SuppressWarnings("unused") final NoSuchFieldException | SecurityException ignore) {
			// continue if EMPTY is not found
		}
		return map("type",
				SERIALIZABLE_PROPERTY_TYPE,
				"uniqueItems",
				true,
				"enum",
				ids,
				"oversigt-ids",
				ids,
				"enumSource",
				Arrays.asList(map("title", "{{item.title}}", "value", "{{item.value}}", "source", mapping)));
	}

	private static Map<String, Object> toJsonSchemaFromType(final Type type, final Optional<JsonHint> hint) {
		if (type instanceof Class) {
			Optional<JsonHint> jsonHint = hint;
			if (((Class<?>) type).isAnnotationPresent(JsonHint.class)) {
				jsonHint = Optional.of(((Class<?>) type).getAnnotation(JsonHint.class));
			}
			return toJsonSchemaFromClass((Class<?>) type, new Type[0], jsonHint);
		} else if (type instanceof ParameterizedType) {
			return toJsonSchemaFromType(((ParameterizedType) type).getActualTypeArguments()[0], hint);
		} else if (type instanceof WildcardType) {
			return toJsonSchemaFromClass(Object.class, new Type[0], hint);
		} else if (type instanceof TypeVariable) {
			// TypeVariable<?> tv = (TypeVariable<?>) type;
			// AnnotatedType[] at = tv.getAnnotatedBounds();
			// Type[] b = tv.getBounds();
			// GenericDeclaration d = tv.getGenericDeclaration();
			return map();
		} else {
			throw new RuntimeException("Unknown type: " + type);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> toJsonSchemaFromClass(final Class<?> clazz,
			final Type[] typeArguments,
			final Optional<JsonHint> hint) {
		Optional<JsonHint> jsonHint = hint;
		if (clazz == String.class) {
			return map("type", "string");
		} else if (clazz == boolean.class || clazz == Boolean.class) {
			return map("type", "boolean");
		} else if (clazz == int.class || clazz == Integer.class) {
			return makeNumber(true, Optional.of(Integer.MIN_VALUE), Optional.of(Integer.MAX_VALUE));
		} else if (clazz == long.class || clazz == Long.class) {
			return makeNumber(true, Optional.of(Long.MIN_VALUE), Optional.of(Long.MAX_VALUE));
		} else if (clazz == double.class || clazz == Double.class) {
			return makeNumber(true, Optional.of(Double.MIN_VALUE), Optional.of(Double.MAX_VALUE));
		} else if (clazz == float.class || clazz == Float.class) {
			return makeNumber(true, Optional.of(Float.MIN_VALUE), Optional.of(Float.MAX_VALUE));
		} else if (clazz == byte.class || clazz == Byte.class) {
			return makeNumber(true, Optional.of(Byte.MIN_VALUE), Optional.of(Byte.MAX_VALUE));
		} else if (clazz == short.class || clazz == Short.class) {
			return makeNumber(true, Optional.of(Short.MIN_VALUE), Optional.of(Short.MAX_VALUE));
		} else if (clazz == char.class || clazz == Character.class) {
			return makeNumber(true, Optional.of(Character.MIN_VALUE), Optional.of(Character.MAX_VALUE));
		} else if (clazz == BigDecimal.class) {
			return makeNumber(false, Optional.empty(), Optional.empty());
		} else if (Enum.class.isAssignableFrom(clazz)) {
			final List<Map<String, Object>> abc = Arrays.stream(clazz.getEnumConstants())
					.map(e -> map("value", ((Enum<?>) e).name(), "title", e.toString()))
					.collect(Collectors.toList());
			return map("type",
					"string",
					"uniqueItems",
					true, // TODO check whether this should always be true
					"oversigt-ids",
					getEnumConstants((Class<Enum<?>>) clazz),
					"enumSource",
					Arrays.asList(map("title", "{{item.title}}", "value", "{{item.value}}", "source", abc)));
		} else if (SerializableProperty.class.isAssignableFrom(clazz)) {
			return map("type",
					SERIALIZABLE_PROPERTY_TYPE,
					"$ref",
					"/schema/" + clazz.getName(),
					"oversigt-property",
					clazz.getName(),
					"$class",
					clazz.getName(),
					"serializable-property",
					clazz.getSimpleName());
		} else if (clazz.isArray() || Collection.class.isAssignableFrom(clazz)) {
			final Type componentType;
			if (clazz.isArray()) {
				componentType = clazz.getComponentType();
			} else if (typeArguments.length == 1) {
				componentType = typeArguments[0];
			} else {
				componentType = clazz.getGenericInterfaces()[0];
			}
			final Map<String, Object> map = map("type",
					"array",
					"items",
					toJsonSchemaFromType(componentType, Optional.empty()),
					"additionalItems",
					false);
			if (Set.class.isAssignableFrom(clazz)) {
				map.put("unique", true);
			}
			if (componentType instanceof Class && ((Class<?>) componentType).isAnnotationPresent(JsonHint.class)) {
				jsonHint = Optional.of(((Class<?>) componentType).getAnnotation(JsonHint.class));
			}
			if (jsonHint.isPresent() && jsonHint.get().arrayStyle().value().isPresent()) {
				map.put("format", jsonHint.map(JsonHint::arrayStyle).flatMap(ArrayStyle::value).get());
			} else {
				final Map<String, Object> items = (Map<String, Object>) map.get("items");
				if (items.containsKey("properties") && ((Map<?, ?>) items.get("properties")).size() <= 3) {
					map.put("format", "table");
				}
			}
			return map;
		} else if (clazz == Map.class) {
			return map("type", "object", "additionalProperties", true);
		} else if (clazz == Color.class) {
			return map("type", "string", "format", "color");
		} else if (clazz == LocalDate.class) {
			return map("type", "string", "format", "date");
		} else if (clazz == LocalTime.class) {
			return map("type", "string", "format", "time");
		} else if (clazz == Optional.class) {
			if (typeArguments.length == 1) {
				return toJsonSchemaFromType(typeArguments[0], Optional.empty());
			}
			return toJsonSchemaFromType(Object.class, Optional.empty());
		} else {
			// TODO check for notnull ???
			final List<Field> fields = TypeUtils.streamFields(clazz)
					.filter(f -> !Modifier.isTransient(f.getModifiers()))
					.filter(f -> !Modifier.isStatic(f.getModifiers()))
					.collect(Collectors.toList());
			final Map<String, Map<String, Object>> fieldsMap = new LinkedHashMap<>();
			for (final Field field : fields) {
				final Map<String, Object> map = JsonUtils.toJsonSchemaFromField(field);
				map.put("title", makeFirstCharacterCapital(field.getName()));
				getFormat(field).ifPresent(format -> map.put("format", format));
				fieldsMap.put(field.getName(), map);
			}
			final Map<String, Object> map = map("type",
					"object",
					"title",
					makeFirstCharacterCapital(clazz.getSimpleName()),
					"properties",
					fieldsMap,
					"required",
					fields.stream().filter(JsonUtils::isRequired).map(Field::getName).toArray(),
					"additionalProperties",
					false);

			if (clazz.isAnnotationPresent(JsonHint.class)) {
				jsonHint = Optional.of(clazz.getAnnotation(JsonHint.class));
			}
			if (jsonHint.isPresent() && !Strings.isNullOrEmpty(jsonHint.get().headerTemplate())) {
				map.put("headerTemplate", jsonHint.get().headerTemplate());
			}
			return map;
		}
	}

	private static Map<String, Object> toJsonSchemaFromField(final Field field) {
		Optional<JsonHint> fieldHint = Optional.empty();
		if (field.isAnnotationPresent(JsonHint.class)) {
			fieldHint = Optional.of(field.getAnnotation(JsonHint.class));
		}

		final Type type = field.getGenericType();
		if (type instanceof Class) {
			return JsonUtils.toJsonSchemaFromClass(field.getType(), new Type[0], fieldHint);
		} else if (type instanceof ParameterizedType) {
			return JsonUtils.toJsonSchemaFromClass(field.getType(),
					((ParameterizedType) type).getActualTypeArguments(),
					fieldHint);
		} else if (type instanceof TypeVariable) {
			LOGGER.warn(String.format("Type %s contains a VariableType. Unable to handle this.", type.getTypeName()));
			return JsonUtils.toJsonSchemaFromClass(field.getType(), new Type[0], fieldHint);
		} else {
			throw new RuntimeException("Unknown type: " + type);
		}
	}

	private static boolean isRequired(final Field field) {
		if (field.isAnnotationPresent(javax.annotation.Nullable.class)) {
			return false;
		} else if (field.isAnnotationPresent(com.fasterxml.jackson.annotation.JsonProperty.class)) {
			return field.getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class).required();
		} else {
			return field.getType() != Optional.class;
		}
	}

	private static Map<String, Object> makeNumber(final boolean wholeNumbers,
			final Optional<Object> min,
			final Optional<Object> max) {
		final Map<String, Object> map = map("type", "number");
		min.ifPresent(v -> map.put("minimum", v));
		max.ifPresent(v -> map.put("maximum", v));
		if (wholeNumbers) {
			map.put("multipleOf", 1.0);
		}
		return map;
	}

	private static String makeFirstCharacterCapital(final String string) {
		return Character.toUpperCase(string.charAt(0)) + string.substring(1);
	}

	private static <E extends Enum<?>> String[] getEnumConstants(final Class<E> clazz) {
		return Stream.of((Object[]) clazz.getEnumConstants())
				.map(o -> ((Enum<?>) o).name())
				.collect(Collectors.toList())
				.toArray(new String[0]);
	}

	private static Optional<String> getFormat(final Field field) {
		final String name = field.getName().toLowerCase();
		if (name.contains("password")) {
			return Optional.of("password");
		} else if (name.contains("mail")) {
			return Optional.of("email");
		} else if (name.endsWith("hostname")) {
			return Optional.of("hostname");
		} else if (name.endsWith("url")) {
			return Optional.of("url");
		}
		return Optional.empty();
	}

	private JsonUtils() {
		throw new RuntimeException("Do not create an instance.s");
	}
}
