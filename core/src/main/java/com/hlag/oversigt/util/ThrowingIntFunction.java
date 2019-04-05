package com.hlag.oversigt.util;

import java.util.function.IntFunction;

@FunctionalInterface
public interface ThrowingIntFunction<T> extends IntFunction<T> {
	@Override
	default T apply(final int value) {
		try {
			return applyThrowing(value);
		} catch (final Exception e) {
			throw e instanceof RuntimeException ? (RuntimeException) e : new SneakyException(e);
		}
	}

	T applyThrowing(int value) throws Exception;
}
