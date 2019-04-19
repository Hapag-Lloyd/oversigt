package com.hlag.oversigt.util;

import java.util.function.IntFunction;

import de.larssh.utils.SneakyException;

@FunctionalInterface
public interface ThrowingIntFunction<T> extends IntFunction<T> {
	static <T> IntFunction<T> sneakyInt(final ThrowingIntFunction<T> function) {
		return function;
	}

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
