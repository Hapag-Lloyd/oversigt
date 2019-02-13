package com.hlag.oversigt.util;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Strings;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.inject.Binder;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.properties.SerializableProperty;
import com.hlag.oversigt.properties.SerializableProperty.Member;

public class TypeUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(TypeUtils.class);

	public static boolean isOfType(Class<?> typeToCheck, Class<?> typeToCheckAgainst) {
		return typeToCheckAgainst.isAssignableFrom(typeToCheck)
				|| typeToCheck.isArray() && isOfType(typeToCheck.getComponentType(), typeToCheckAgainst);
	}

	public static <T> Stream<Class<T>> findClasses(Package packageToSearch, Class<T> assignableTo) {
		return findClasses(packageToSearch, assignableTo, null);
	}

	public static <T> Stream<Class<T>> findClasses(Package packageToSearch,
			Class<T> assignableTo,
			Class<? extends Annotation> annotationToBePresent) {
		return findClasses(packageToSearch, c -> assignableTo.isAssignableFrom(c) && //
				(annotationToBePresent == null || c.isAnnotationPresent(annotationToBePresent)));
	}

	public static <T> Stream<Class<T>> findClasses(Package packageToSearch, Predicate<Class<?>> filter) {
		LOGGER.info("Inspecting classes using ClassLoader[{}] and package[{}]",
				Thread.currentThread().getContextClassLoader(),
				packageToSearch);
		Set<ClassInfo> classes;
		try {
			classes = ClassPath//
					.from(Thread.currentThread().getContextClassLoader())
					.getTopLevelClassesRecursive(packageToSearch.getName());
		} catch (IOException e) {
			throw new RuntimeException("Unable to search classes in class path", e);
		}

		// iterates over all classes, filter by HandlesEvent annotation and transforms stream to needed form
		@SuppressWarnings("unchecked")
		Stream<Class<T>> stream = classes//
				.stream()//
				.map(TypeUtils::loadClassInfo)//
				.filter(Optional::isPresent)//
				.map(Optional::get)//
				.filter(c -> !c.isInterface())//
				.filter(filter)//
				.filter(c -> (c.getModifiers() & Modifier.ABSTRACT) == 0)//
				.map(c -> (Class<T>) c);
		return stream;
	}

	public static void bindClasses(Package packageToSearch, Predicate<Class<?>> filter, Binder binder) {
		findClasses(packageToSearch, filter).forEach(binder::bind);
	}

	public static <T> Stream<Class<T>> findClasses(ClassLoader classLoader,
			Collection<String> classNames,
			Class<T> assignableTo,
			Class<? extends Annotation> annotationToBePresent) {
		Predicate<Class<?>> predicate = c -> assignableTo.isAssignableFrom(c) && //
				(annotationToBePresent == null || c.isAnnotationPresent(annotationToBePresent));
		@SuppressWarnings("unchecked")
		Stream<Class<T>> stream = classNames.stream()
				.map(s -> loadClassInfo(classLoader, s))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.filter(c -> !c.isInterface())//
				.filter(predicate)//
				.filter(c -> (c.getModifiers() & Modifier.ABSTRACT) == 0)//
				.map(c -> (Class<T>) c);
		return stream;
	}

	/**
	 * Tries to load a {@link Class} and return <code>true</code> if successful.
	 *
	 * @param classInfo the {@link Class} to load
	 * @return <code>true</code> if the {@link Class} could be loaded, otherwise <code>false</code>
	 */
	private static Optional<Class<?>> loadClassInfo(ClassInfo classInfo) {
		try {
			return Optional.of(classInfo.load());
		} catch (Exception | NoClassDefFoundError e) {
			LOGGER.trace("Class cannot be loaded: {}", classInfo.getName(), e);
			return Optional.empty();
		}
	}

	private static Optional<Class<?>> loadClassInfo(ClassLoader classLoader, String className) {
		try {
			return Optional.of(classLoader.loadClass(className));
		} catch (Exception | NoClassDefFoundError e) {
			LOGGER.trace("Class cannot be loaded: {}", className, e);
			return Optional.empty();
		}
	}

	public static List<String> listClassesInJarFiles(URL[] jarFileUrls) throws IOException {
		List<String> classNames = new ArrayList<>();
		for (URL jarFileUrl : jarFileUrls) {
			try (JarInputStream in = new JarInputStream(jarFileUrl.openStream())) {
				JarEntry jarEntry;
				while ((jarEntry = in.getNextJarEntry()) != null) {
					if (!jarEntry.isDirectory()) {
						String name = jarEntry.getName();
						if (name.toLowerCase().endsWith(".class")) {
							name = name.substring(0, name.length() - ".class".length());
							name = name.replace("/", ".");
							classNames.add(name);
						}
					}
				}
			}
		}
		return classNames;
	}

	public static Stream<String> getMembers(Class<?> clazz) {
		return streamFields(clazz)//
				.filter(f -> (f.getModifiers() & Modifier.TRANSIENT) == 0)//
				.filter(f -> (f.getModifiers() & Modifier.STATIC) == 0)//
				.map(Field::getName)//
		;
	}

	public static Map<String, Object> toMemberMap(Object object) {
		try {
			return Stream//
					.of(Introspector.getBeanInfo(object.getClass(), Object.class).getPropertyDescriptors())//
					.filter(i -> i.getReadMethod() != null)
					.filter(i -> !i.getReadMethod().isAnnotationPresent(JsonIgnore.class))
					.filter(i -> i.getWriteMethod() == null
							|| !i.getWriteMethod().isAnnotationPresent(JsonIgnore.class))
					.collect(Collectors.toMap(PropertyDescriptor::getName, pd -> get(pd.getReadMethod(), object)));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Object get(Method method, Object target) {
		try {
			Object object = method.invoke(target);
			if (object != null) {
				return object/* TODO why did we convert to string here? .toString()*/;
			} else {
				return null;
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T extends SerializableProperty> Collection<SerializablePropertyMember> getSerializablePropertyMembers(
			Class<T> clazz) {
		Collection<SerializablePropertyMember> members = new LinkedHashSet<>();
		try {
			members.add(new SerializablePropertyMember(SerializableProperty.class.getDeclaredField("id")));
			members.add(new SerializablePropertyMember(SerializableProperty.class.getDeclaredField("name")));
		} catch (NoSuchFieldException | SecurityException e) {
			throw new RuntimeException("Unable to find standard values", e);
		}
		members.addAll(streamFields(clazz)//
				.filter(f -> (f.getModifiers() & Modifier.TRANSIENT) == 0)//
				.filter(f -> (f.getModifiers() & Modifier.STATIC) == 0)//
				.map(SerializablePropertyMember::new)//
				.collect(Collectors.toList()));
		members = new ArrayList<>(members);
		((List<?>) members).remove(0);
		return members;
	}

	static Stream<Field> streamFields(Class<?> clazz) {
		if (clazz != Object.class) {
			return Stream.concat(Stream.of(clazz.getDeclaredFields()), streamFields(clazz.getSuperclass()));
		} else {
			return Stream.empty();
		}
	}

	@SuppressWarnings("unchecked")
	public static <C> Constructor<C> getAppropriateConstructor(Class<C> c, Object[] initArgs) {
		if (initArgs == null) {
			initArgs = new Object[0];
		}
		for (@SuppressWarnings("rawtypes")
		Constructor con : c.getDeclaredConstructors()) {
			@SuppressWarnings("rawtypes")
			Class[] types = con.getParameterTypes();
			if (types.length != initArgs.length) {
				continue;
			}
			boolean match = true;
			for (int i = 0; i < types.length; i++) {
				Class<?> need = types[i], got = initArgs[i].getClass();
				if (!need.isAssignableFrom(got)) {
					if (need.isPrimitive()) {
						match = int.class.equals(need) && Integer.class.equals(got)
								|| long.class.equals(need) && Long.class.equals(got)
								|| char.class.equals(need) && Character.class.equals(got)
								|| short.class.equals(need) && Short.class.equals(got)
								|| boolean.class.equals(need) && Boolean.class.equals(got)
								|| byte.class.equals(need) && Byte.class.equals(got);
					} else {
						match = false;
					}
				}
				if (!match) {
					break;
				}
			}
			if (match) {
				return con;
			}
		}
		throw new IllegalArgumentException("Cannot find an appropriate constructor for class " + c + " and arguments "
				+ Arrays.toString(initArgs));
	}

	@SafeVarargs
	public static <T> T tryToCreateInstance(Class<T> target,
			String input,
			Supplier<T> defaultValueSupplier,
			Class<? extends T>... classes) {
		for (Class<? extends T> clazz : classes) {
			try {
				return createInstance(clazz, input);
			} catch (Exception ignore) {
			}
		}
		return defaultValueSupplier.get();
	}

	@SuppressWarnings("unchecked")
	public static <T> T createInstance(Class<? extends T> clazz, Object... parameters) {
		Objects.requireNonNull(parameters);
		for (Object param : parameters) {
			Objects.requireNonNull(param);
		}
		if (parameters.length == 1 && parameters[0].getClass() == clazz) {
			return (T) parameters[0];
		} else if (clazz.isPrimitive()) {
			if (int.class == clazz) {
				int value = Integer.parseInt((String) parameters[0]);
				Object[] values = { value };
				return (T) values[0];
			} else if (long.class == clazz) {
				Object[] values = { Long.parseLong((String) parameters[0]) };
				return (T) values[0];
			} else if (double.class == clazz) {
				Object[] values = { Double.parseDouble((String) parameters[0]) };
				return (T) values[0];
			} else if (float.class == clazz) {
				Object[] values = { Float.parseFloat((String) parameters[0]) };
				return (T) values[0];
			} else if (byte.class == clazz) {
				Object[] values = { Byte.parseByte((String) parameters[0]) };
				return (T) values[0];
			} else if (short.class == clazz) {
				Object[] values = { Short.parseShort((String) parameters[0]) };
				return (T) values[0];
			} else if (char.class == clazz) {
				Object[] values = { ((String) parameters[0]).charAt(0) };
				return (T) values[0];
			} else {
				throw new RuntimeException("Unknown primitive type: " + clazz.getName());
			}
		} else {
			List<Constructor<?>> constructors = Arrays//
					.stream(clazz.getDeclaredConstructors())//
					.filter(c -> c.getParameterCount() == parameters.length)
					.filter(c -> {
						for (int i = 0; i < parameters.length; ++i) {
							if (!c.getParameterTypes()[i].isAssignableFrom(parameters[i].getClass())) {
								return false;
							}
						}
						return true;
					})
					.limit(2)//
					.collect(Collectors.toList());
			if (constructors.size() == 1) {
				constructors.get(0).setAccessible(true);
				try {
					return (T) constructors.get(0).newInstance(parameters);
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException e) {
					throw new RuntimeException("Unable to create instance of class: " + clazz.getName(), e);
				}
			} else {
				List<Method> fabricMethods = Arrays//
						.stream(clazz.getDeclaredMethods())//
						.filter(m -> (m.getModifiers() & Modifier.STATIC) != 0)//
						.filter(m -> m.getParameterTypes().length == parameters.length)//
						.filter(m -> clazz.isAssignableFrom(m.getReturnType()))//
						.filter(m -> {
							for (int i = 0; i < parameters.length; ++i) {
								if (!m.getParameterTypes()[i].isAssignableFrom(parameters[i].getClass())) {
									return false;
								}
							}
							return true;
						})
						.collect(Collectors.toList());
				if (fabricMethods.size() == 1) {
					return invokeStaticMethod(fabricMethods.get(0), parameters);
				} else if (fabricMethods.size() > 1) {
					Optional<T> maybe = tryStaticMethods(fabricMethods,
							new String[] { "parse", "create", "compile", "get", "createInstance", "getInstance",
									"construct" },
							parameters);
					return maybe.get();
				} else {
					throw new RuntimeException("No fitting constructor or method found for class: " + clazz.getName());
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> Optional<T> tryStaticMethods(Collection<Method> methods, String[] names, Object... values) {
		return (Optional<T>) Arrays//
				.stream(names)//
				.map(name -> tryStaticMethod(methods, name, values))//
				.findAny()//
				.orElse(Optional.empty());
	}

	private static <T> Optional<T> tryStaticMethod(Collection<Method> methods, String name, Object... values) {
		return methods//
				.stream()//
				.filter(m -> m.getName().equals(name))//
				.findAny()//
				.map(m -> invokeStaticMethod(m, values));
	}

	@SuppressWarnings("unchecked")
	private static <T> T invokeStaticMethod(Method method, Object... arguments) {
		method.setAccessible(true);
		try {
			return (T) method.invoke(null, arguments);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static Method getMethod(Class<?> clazz, List<String> methodNames, Class<?>[] parameterTypes) {
		if (methodNames == null || methodNames.size() == 0) {
			return null;
		}

		while (clazz != Object.class) {
			Optional<Method> method = Stream//
					.of(clazz.getDeclaredMethods())//
					.filter(m -> methodNames.contains(m.getName()))//
					.filter(m -> Arrays.deepEquals(m.getParameterTypes(), parameterTypes))
					.sorted(new IndexOfComparator(methodNames).thenComparing(CLASS_DEPTH_COMPARATOR))//
					.findFirst();
			if (method.isPresent()) {
				return method.get();
			} else {
				clazz = clazz.getSuperclass();
			}
		}
		return null;
	}

	private static int getClassDepth(Class<?> clazz) {
		int i = 1;
		while (clazz != null && clazz != Object.class) {
			++i;
			clazz = clazz.getSuperclass();
		}
		return i;
	}

	@SuppressWarnings("unchecked")
	public static <T> T[] createArray(Class<T> clazz, int length) {
		return (T[]) Array.newInstance(clazz, length);
	}

	public static boolean isDebug() {
		return java.lang.management.ManagementFactory.getRuntimeMXBean()
				.getInputArguments()
				.toString()
				.indexOf("jdwp") >= 0;
	}

	private static final Comparator<Method> CLASS_DEPTH_COMPARATOR = (a,
			b) -> -Integer.compare(getClassDepth(a.getReturnType()), getClassDepth(b.getReturnType()));

	private static class IndexOfComparator implements Comparator<Method> {
		private final List<String> methodNames;

		private IndexOfComparator(List<String> methodNames) {
			this.methodNames = methodNames;
		}

		@Override
		public int compare(Method a, Method b) {
			return Integer.compare(methodNames.indexOf(a.getName()), methodNames.indexOf(b.getName()));
		}
	}

	public static class SerializablePropertyMember {
		private final Field field;
		private final String name;
		private final String displayName;
		private final Type type;
		private final String icon;
		private final int size;
		private final boolean required;

		private SerializablePropertyMember(Field field) {
			this(//
				field,
				field.getName(),
				Type.fromField(field),
				field.isAnnotationPresent(Member.class) ? field.getAnnotation(Member.class) : null);
		}

		private SerializablePropertyMember(Field field, String name, Type type, Member member) {
			this(//
				field,
				name,
				type,
				member != null ? member.icon() : "tag",
				member != null ? member.size() : 2,
				member != null ? member.mayBeEmpty() : false);
		}

		private SerializablePropertyMember(Field field, String name, Type type, String icon, int size, boolean empty) {
			this.field = field;
			field.setAccessible(true);
			this.name = name;
			this.displayName = Character.toUpperCase(name.charAt(0)) + name.substring(1);
			this.type = type;
			this.icon = icon;
			this.size = size;
			this.required = !empty;
		}

		private Class<?> getClazz() {
			return field.getType();
		}

		public Object createInstance(String stringValue) throws MemberMissingException {
			if (isRequired() && Strings.isNullOrEmpty(stringValue)) {
				throw new MemberMissingException("Value for member " + name + " must not be null or empty");
			}
			return TypeUtils.createInstance(getClazz(), stringValue);
		}

		public void set(SerializableProperty property, String value) throws MemberMissingException {
			set(property, createInstance(value));
		}

		public void set(SerializableProperty property, Object object) throws MemberMissingException {
			try {
				field.set(property, object);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new RuntimeException("Unable to set value of member '" + getName() + "': " + object.toString(),
						e);
			}
		}

		public Object get(SerializableProperty property) {
			try {
				return field.get(property);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new RuntimeException("Unable to get value of member '" + getName() + "'", e);
			}
		}

		public String getName() {
			return name;
		}

		public String getDisplayName() {
			return displayName;
		}

		public Type getType() {
			return type;
		}

		public String getIcon() {
			return icon;
		}

		public int getSize() {
			return size;
		}

		public boolean isRequired() {
			return required;
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			SerializablePropertyMember other = (SerializablePropertyMember) obj;
			if (name == null) {
				if (other.name != null) {
					return false;
				}
			} else if (!name.equals(other.name)) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			return "SerializablePropertyMember [name=" + name + ", type=" + type + ", icon=" + icon + ", size=" + size
					+ "]";
		}

		public static enum Type {
			text,
			number,
			password,
			color;

			static Type fromField(Field field) {
				Class<?> type = field.getType();
				if (type == String.class && field.getName().toLowerCase().contains("password")) {
					return password;
				} else if (type == Color.class) {
					return color;
				} else if (type == String.class) {
					return text;
				} else if (Number.class.isAssignableFrom(type) || type.isPrimitive() && type != boolean.class) {
					return number;
				} else {
					throw new RuntimeException("Unknown class: " + type.getName());
				}
			}
		}

		public static class MemberMissingException extends Exception {
			private static final long serialVersionUID = 9022818290418219308L;

			private MemberMissingException(String message) {
				super(message);
				// TODO Auto-generated constructor stub
			}
		}
	}

	public static class ClassProxy implements InvocationHandler {
		@SuppressWarnings("unchecked")
		public static <T> T create(Class<T> clazz, ReturnValue... returns) {
			return (T) Proxy.newProxyInstance(ClassProxy.class.getClassLoader(),
					new Class[] { clazz },
					new ClassProxy(returns));
		}

		private final List<ReturnValue> returns;

		private ClassProxy(ReturnValue[] returns) {
			this.returns = Arrays.asList(returns);
		}

		@Override
		public Object invoke(Object object, Method method, Object[] args) throws Throwable {
			return returns.stream()
					.filter(r -> r.getMethod().equals(method))
					.map(ReturnValue::getReturnValue)
					.findFirst()
					.orElse(null);
		}
	}

	public static class ReturnValue {
		public static ReturnValue find(Class<?> clazz, String methodName, Object returnValue) {
			return new ReturnValue(Arrays.stream(clazz.getMethods())//
					.filter(m -> m.getName().equals(methodName))
					.findFirst()
					.get(), returnValue);
		}

		private final Method method;
		private final Object returnValue;

		private ReturnValue(Method method, Object returnValue) {
			this.method = method;
			this.returnValue = returnValue;
		}

		public Method getMethod() {
			return method;
		}

		public Object getReturnValue() {
			return returnValue;
		}
	}
}
