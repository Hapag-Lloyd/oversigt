package com.hlag.oversigt.util;

import java.util.function.Function;

import de.larssh.utils.SneakyException;

@FunctionalInterface
public interface ThrowingFunction<T, R> extends Function<T, R> {
	static <T, R> Function<T, R> sneaky(final ThrowingFunction<T, R> function) {
		return function;
	}

	@Override
	default R apply(final T t) {
		try {
			return applyThrowing(t);
		} catch (final Exception e) {
			throw e instanceof RuntimeException ? (RuntimeException) e : new SneakyException(e);
		}
	}

	R applyThrowing(T t) throws Exception;
}
