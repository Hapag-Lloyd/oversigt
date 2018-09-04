package com.hlag.oversigt.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class SneakyException extends RuntimeException {

	private static final long serialVersionUID = 0;

	public static <T> Consumer<T> sneakc(ThrowingConsumer<T> consumer) {
		return consumer;
	}

	public static <T, R> Function<T, R> sneaky(ThrowingFunction<T, R> function) {
		return function;
	}

	public static <T> Supplier<T> sneaky(ThrowingSupplier<T> supplier) {
		return supplier;
	}

	public static <T> IntFunction<T> sneakyInt(ThrowingIntFunction<T> function) {
		return function;
	}

	@SuppressWarnings("unchecked")
	private static <T extends Throwable> T sneakyThrow(Throwable t) throws T {
		throw (T) t;
	}

	public SneakyException(Throwable throwable) {
		throw SneakyException.<RuntimeException> sneakyThrow(throwable);
	}
}
