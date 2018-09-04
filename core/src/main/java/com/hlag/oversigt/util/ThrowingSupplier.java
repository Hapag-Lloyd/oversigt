package com.hlag.oversigt.util;

import java.util.function.Supplier;

@FunctionalInterface
public interface ThrowingSupplier<T> extends Supplier<T> {
	@Override
	default T get() {
		try {
			return getThrowing();
		} catch (Exception e) {
			throw e instanceof RuntimeException ? (RuntimeException) e : new SneakyException(e);
		}
	}

	T getThrowing() throws Exception;
}