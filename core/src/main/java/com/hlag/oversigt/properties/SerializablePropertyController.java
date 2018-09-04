package com.hlag.oversigt.properties;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hlag.oversigt.storage.Storage;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.SneakyException;
import com.hlag.oversigt.util.TypeUtils;
import com.hlag.oversigt.util.TypeUtils.SerializablePropertyMember;
import com.hlag.oversigt.util.TypeUtils.SerializablePropertyMember.MemberMissingException;

@Singleton
public class SerializablePropertyController {
	private final Map<String, Class<SerializableProperty>> namesToClasses;
	private final Map<Class<? extends SerializableProperty>, SerializableProperty> classToEmpty = new HashMap<>();

	private final Storage storage;
	private final Map<Integer, SerializableProperty> properties = new HashMap<>();

	@Inject
	private JsonUtils json;

	@Inject
	public SerializablePropertyController(Storage storage) {
		this.storage = storage;
		namesToClasses = TypeUtils.findClasses(getClass().getPackage(), SerializableProperty.class)
				.peek(c -> classToEmpty.put(c, createEmptyItem(c)))
				.collect(Collectors.toMap(Class::getSimpleName, Function.identity()));

		properties.putAll(namesToClasses//
				.values()
				.stream()
				.flatMap(c -> storage.listProperties(c)//
						.stream())
				.collect(Collectors.toMap(SerializableProperty::getId, Function.identity())));
	}

	public Collection<String> getNames() {
		return namesToClasses.keySet();
	}

	@SuppressWarnings("unchecked")
	public <T extends SerializableProperty> Stream<T> streamProperties(Class<T> clazz) {
		//return storage.listProperties(clazz);
		return properties.values().stream().filter(p -> p.getClass() == clazz).map(p -> (T) p);
	}

	@SuppressWarnings("unchecked")
	public <T extends SerializableProperty> T getProperty(Class<T> clazz, int id) {
		if (id != 0) {
			//return storage.getProperty(clazz, id).get();
			return (T) Optional.ofNullable(properties.get(id)).get();
		} else {
			return getEmpty(clazz);
		}
	}

	public Class<? extends SerializableProperty> getClass(String name) {
		return Optional.ofNullable(namesToClasses.get(name)).get();
	}

	public Set<Class<? extends SerializableProperty>> getClasses() {
		return classToEmpty.keySet();
	}

	@Deprecated
	public Map<String, List<? extends SerializableProperty>> getProperties() {
		// TODO remove this method
		//		return namesToClasses.entrySet()//
		//		.stream()
		//		.collect(Collectors.toMap(e -> e.getKey(), e -> storage.listProperties(e.getValue())));
		return namesToClasses.entrySet()//
				.stream()
				.collect(Collectors.toMap(e -> e.getKey(),
						e -> streamProperties(e.getValue()).collect(Collectors.toList())));
	}

	@Deprecated
	public Map<String, Collection<SerializablePropertyMember>> getAllMembers() {
		// TODO remove this method
		return namesToClasses.entrySet()//
				.stream()
				.collect(Collectors.toMap(Entry::getKey, c -> getMembers(c.getValue())));
	}

	public Collection<SerializablePropertyMember> getMembers(Class<? extends SerializableProperty> clazz) {
		return TypeUtils.getSerializablePropertyMembers(clazz);
	}

	public String toString(SerializableProperty value) {
		if (value != null) {
			return Integer.toString(value.getId());
		} else {
			return "0";
		}
	}

	public <T extends SerializableProperty> T createProperty(Class<T> clazz,
			String name,
			Map<String, Object> parameters) throws MemberMissingException {
		return createProperty(clazz,
				name,
				getMembers(clazz)//
						.stream()
						.filter(m -> !"name".equals(m.getName()))
						.map(SneakyException.sneaky(m -> m.createInstance((String) parameters.get(m.getName()))))
						.collect(Collectors.toList()));
	}

	public <T extends SerializableProperty> T createProperty(Class<T> clazz, String name, List<Object> parameters) {
		T property = storage.createProperty(clazz, name, parameters.toArray());
		properties.put(property.getId(), property);
		return property;
	}

	public void updateProperty(SerializableProperty property) {
		if (!properties.values().contains(property)) {
			SerializableProperty real = getProperty(property.getClass(), property.getId());
			TypeUtils.getSerializablePropertyMembers(property.getClass())
					.forEach(SneakyException.sneakc(m -> m.set(real, m.get(property))));
			storage.updateProperty(real);
		} else {
			storage.updateProperty(property);
		}
	}

	public void deleteProperty(Class<? extends SerializableProperty> clazz, int id) {
		storage.deleteProperty(clazz, id);
		properties.remove(id);
	}

	@SuppressWarnings("unchecked")
	public <T extends SerializableProperty> T getEmpty(Class<T> clazz) {
		return (T) classToEmpty.get(clazz);
	}

	private static SerializableProperty createEmptyItem(Class<? extends SerializableProperty> clazz) {
		if (SerializableProperty.class.isAssignableFrom(clazz)) {
			try {
				return (SerializableProperty) clazz.getField("EMPTY").get(null);
			} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
				throw new RuntimeException("Unable to get field 'EMPTY' from type " + clazz.getSimpleName(), e);
			}
		} else {
			throw new RuntimeException(
					"Type " + clazz.getName() + " is not a " + SerializableProperty.class.getSimpleName());
		}
	}

	public <T> T clone(T original) {
		return json.fromJson(json.toJson(original), original.getClass());
	}
}
