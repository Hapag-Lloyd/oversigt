package com.hlag.oversigt.util;

import static com.hlag.oversigt.util.Utils.map;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;
import com.hlag.oversigt.storage.Storage;

@Singleton
public class JsonUtils {
	public static <T> JsonSerializer<T> serializer(ThrowingFunction<T, String> converter) {
		return (object, type, context) -> new JsonPrimitive(converter.apply(object));
	}

	public static <T> JsonDeserializer<T> deserializer(ThrowingFunction<String, T> converter) {
		return (json, type, context) -> converter.apply(json.getAsString());
	}

	public static <T> StdSerializer<T> serializer(Class<T> clazz, ThrowingFunction<T, String> converter) {
		return new StdSerializer<T>(clazz) {
			private static final long serialVersionUID = 1L;

			@Override
			public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
				gen.writeString(converter.apply(value));
			}
		};
	}

	public static <T> StdDeserializer<T> deserializer(Class<T> clazz, ThrowingFunction<String, T> converter) {
		return new StdDeserializer<T>(clazz) {
			private static final long serialVersionUID = 1L;

			@Override
			public T deserialize(JsonParser p, DeserializationContext ctxt)
					throws IOException, JsonProcessingException {
				return converter.apply(p.getValueAsString());
			}
		};
	}

	@Inject
	private Gson gson;
	@Inject
	private Storage storage;

	public String toJson(Object object) {
		return gson.toJson(object);
	}

	public <T> T fromJson(String json, Type type) {
		return gson.fromJson(json, type);
	}

	/**Remove  keys from the JSON string
	 * @param json the JSON to work on
	 * @param filter determine whether a key should stay in the JSON or not. If the {@link Predicate} returns <code>false</code> the key will be removed.
	 *  @return the stripped JSON
	 */
	public String removeKeysFromJson(String json, Predicate<String> filter) {
		return toJson(removeKeys(gson.fromJson(json, Object.class), filter));
	}

	/**Remove all keys from the JSON string that contain the phrase "password" (not case sensitive)
	 * @param json the JSON to work on
	 * @param filter determine whether a key should stay in the JSON or not. If the {@link Predicate} returns <code>false</code> the key will be removed.
	 *  @return the stripped JSON
	 */
	public String removePasswordsFromJson(String string) {
		return removeKeysFromJson(string, s -> !s.toLowerCase().contains("password"));
	}

	@SuppressWarnings("unchecked")
	private Object removeKeys(Object json, Predicate<String> filter) {
		if (json instanceof Collection) {
			removeKeys((Collection<?>) json, filter);
		} else if (json instanceof Map) {
			removeKeys((Map<String, Object>) json, filter);
		} else if (json instanceof Double) {
			long l = ((Double) json).longValue();
			if ((double) l == (Double) json) {
				return l;
			}
		}
		return json;
	}

	private void removeKeys(Collection<?> json, Predicate<String> filter) {
		for (Object e : json) {
			removeKeys(e, filter);
		}
	}

	private void removeKeys(Map<String, Object> json, Predicate<String> filter) {
		for (Entry<String, Object> e : json.entrySet()) {
			if (!filter.test(e.getKey())) {
				e.setValue(null);
			} else {
				e.setValue(removeKeys(e.getValue(), filter));
			}
		}
	}

	public String toJsonSchema(Class<?> clazz) {
		return toJsonSchema(clazz, null);
	}

	@SuppressWarnings("unchecked")
	public String toJsonSchema(Class<?> clazz, JsonHint hint) {
		Map<String, Object> schema;
		if (!SerializableProperty.class.isAssignableFrom(clazz)) {
			schema = map("$schema",
					"http://json-schema.org/schema#",
					"$id",
					"http://schema.hlag.com/oversigt/type/" + clazz.getName(),
					"title",
					clazz.getSimpleName());
			schema.putAll(toJsonSchema_internal((Type) clazz, hint));
		} else {
			schema = map("$schema",
					"http://json-schema.org/schema#",
					"$id",
					"http://schema.hlag.com/oversigt/property/" + clazz.getName(),
					"title",
					clazz.getSimpleName());
			schema.putAll(toJsonSchema_SerializablePropertyEnum((Class<? extends SerializableProperty>) clazz));
		}
		return gson.toJson(schema);
	}

	private Map<String, Object> toJsonSchema_SerializablePropertyEnum(Class<? extends SerializableProperty> clazz) {
		List<? extends SerializableProperty> props = storage.listProperties(clazz);
		List<String> names = new ArrayList<>(
				props.stream().map(SerializableProperty::getName).collect(Collectors.toList()));
		List<Integer> ids = new ArrayList<>(
				props.stream().map(SerializableProperty::getId).collect(Collectors.toList()));
		List<Map<String, Object>> maps = props.stream()
				.map(p -> map("value", p.getId(), "title", p.getName()))
				.collect(Collectors.toList());
		try {
			if (clazz.getDeclaredField("EMPTY") != null) {
				names.add(0, "\u00a0");
				ids.add(0, 0);
				maps.add(0, map("value", 0, "title", "\u00a0"));
			}
		} catch (NoSuchFieldException | SecurityException ignore) {
		}
		return map("type",
				"string",
				"uniqueItems",
				true,
				//				"enum",
				//				names,
				"oversigt-ids",
				ids,
				"enumSource",
				Arrays.asList(map("title", "{{item.title}}", "value", "{{item.value}}", "source", maps)));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> toJsonSchema_internal(Class<?> clazz, JsonHint hint) {
		if (clazz == String.class) {
			return map("type", "string");
		} else if (clazz == boolean.class || clazz == Boolean.class) {
			return map("type", "boolean");
		} else if (clazz == int.class || clazz == Integer.class) {
			return makeNumber(true, Integer.MIN_VALUE, Integer.MAX_VALUE);
		} else if (clazz == long.class || clazz == Long.class) {
			return makeNumber(true, Long.MIN_VALUE, Long.MAX_VALUE);
		} else if (clazz == double.class || clazz == Double.class) {
			return makeNumber(true, Double.MIN_VALUE, Double.MAX_VALUE);
		} else if (clazz == float.class || clazz == Float.class) {
			return makeNumber(true, Float.MIN_VALUE, Float.MAX_VALUE);
		} else if (clazz == byte.class || clazz == Byte.class) {
			return makeNumber(true, Byte.MIN_VALUE, Byte.MAX_VALUE);
		} else if (clazz == short.class || clazz == Short.class) {
			return makeNumber(true, Short.MIN_VALUE, Short.MAX_VALUE);
		} else if (clazz == char.class || clazz == Character.class) {
			return makeNumber(true, Character.MIN_VALUE, Character.MAX_VALUE);
		} else if (clazz == BigDecimal.class) {
			return makeNumber(false, null, null);
		} else if (Enum.class.isAssignableFrom(clazz)) {
			List<Map<String, Object>> abc = Arrays.stream(clazz.getEnumConstants())
					.map(e -> map("value", ((Enum<?>) e).name(), "title", e.toString()))
					.collect(Collectors.toList());
			return map("type",
					"string",
					"uniqueItems",
					true,
					"oversigt-ids",
					getEnumConstants((Class<Enum<?>>) clazz),
					"enumSource",
					Arrays.asList(map("title", "{{item.title}}", "value", "{{item.value}}", "source", abc)));
		} else if (SerializableProperty.class.isAssignableFrom(clazz)) {
			return map("type", "string", "$ref", "/schema/" + clazz.getName(), "oversigt-property", clazz.getName());
		} else if (clazz.isArray() || Collection.class.isAssignableFrom(clazz)) {
			Type componentType = clazz.isArray() ? clazz.getComponentType() : clazz.getGenericInterfaces()[0];
			Map<String, Object> map = map("type",
					"array",
					"items",
					toJsonSchema_internal(componentType, null),
					"additionalItems",
					false);
			if (Set.class.isAssignableFrom(clazz)) {
				map.put("unique", true);
			}
			if (componentType instanceof Class && ((Class<?>) componentType).isAnnotationPresent(JsonHint.class)) {
				hint = ((Class<?>) componentType).getAnnotation(JsonHint.class);
			}
			if (hint != null && hint.arrayStyle() != ArrayStyle.DEFAULT) {
				if (hint.arrayStyle().value() != null) {
					map.put("format", hint.arrayStyle().value());
				}
			} else {
				Map<String, Object> items = (Map<String, Object>) map.get("items");
				if (items.containsKey("properties") && ((Map<?, ?>) items.get("properties")).size() <= 3) {
					map.put("format", "table");
				}
			}
			return map;
		} else if (clazz == Color.class)

		{
			return map("type", "string", "format", "color");
		} else if (clazz == LocalDate.class) {
			return map("type", "string", "format", "date");
		} else if (clazz == LocalTime.class) {
			return map("type", "string", "format", "time");
		} else {
			// check for notnull
			List<Field> fields = TypeUtils.streamFields(clazz)
					.filter(f -> (f.getModifiers() & Modifier.TRANSIENT) == 0)//
					.filter(f -> (f.getModifiers() & Modifier.STATIC) == 0)//
					.collect(Collectors.toList());
			Map<String, Map<String, Object>> fieldsMap = new LinkedHashMap<>();
			for (Field field : fields) {
				JsonHint fieldHint = null;
				if (field.isAnnotationPresent(JsonHint.class)) {
					fieldHint = field.getAnnotation(JsonHint.class);
				}
				Map<String, Object> map = JsonUtils.toJsonSchema_internal(field.getType(), fieldHint);
				map.put("title", makeFirstCharacterCapital(field.getName()));
				getFormat(field).ifPresent(format -> map.put("format", format));
				fieldsMap.put(field.getName(), map);
			}
			Map<String, Object> map = map("type",
					"object", //
					"title",
					makeFirstCharacterCapital(clazz.getSimpleName()),
					"properties",
					fieldsMap,
					"required",
					fields.stream().filter(JsonUtils::isRequired).map(Field::getName).toArray(),
					"additionalProperties",
					false);

			if (clazz.isAnnotationPresent(JsonHint.class)) {
				hint = clazz.getAnnotation(JsonHint.class);
			}
			if (hint != null) {
				if (!Strings.isNullOrEmpty(hint.headerTemplate())) {
					map.put("headerTemplate", hint.headerTemplate());
				}
			}
			return map;
		}
	}

	private static boolean isRequired(Field field) {
		if (field.isAnnotationPresent(Nullable.class)) {
			return false;
		} else if (field.isAnnotationPresent(com.fasterxml.jackson.annotation.JsonProperty.class)) {
			return field.getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class).required();
		} else {
			return true;
		}
	}

	private static Map<String, Object> toJsonSchema_internal(Type type, JsonHint hint) {
		if (type instanceof Class) {
			if (((Class<?>) type).isAnnotationPresent(JsonHint.class)) {
				hint = ((Class<?>) type).getAnnotation(JsonHint.class);
			}
			return toJsonSchema_internal((Class<?>) type, hint);
		} else if (type instanceof ParameterizedType) {
			return toJsonSchema_internal(((ParameterizedType) type).getActualTypeArguments()[0], hint);
		} else if (type instanceof TypeVariable) {
			//                                              TypeVariable<?> tv = (TypeVariable<?>) type;
			//                                              AnnotatedType[] at = tv.getAnnotatedBounds();
			//                                              Type[] b = tv.getBounds();
			//                                              GenericDeclaration d = tv.getGenericDeclaration();
			return map();
		} else {
			throw new RuntimeException("Unknown type: " + type);
		}
	}

	private static Map<String, Object> makeNumber(boolean wholeNumbers, Object min, Object max) {
		Map<String, Object> map = map("type", "number");
		if (min != null) {
			map.put("minimum", min);
		}
		if (max != null) {
			map.put("maximum", max);
		}
		if (wholeNumbers) {
			map.put("multipleOf", 1.0);
		}
		return map;
	}

	private static String makeFirstCharacterCapital(String string) {
		return Character.toUpperCase(string.charAt(0)) + string.substring(1);
	}

	private static <E extends Enum<?>> String[] getEnumConstants(Class<E> clazz) {
		return Stream.of((Object[]) clazz.getEnumConstants())
				.map(o -> ((Enum<?>) o).name())
				.collect(Collectors.toList())
				.toArray(new String[0]);
	}

	private static Optional<String> getFormat(Field field) {
		String name = field.getName().toLowerCase();
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
}
