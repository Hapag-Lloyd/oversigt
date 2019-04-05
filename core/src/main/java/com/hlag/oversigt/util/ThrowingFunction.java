package com.hlag.oversigt.util;

import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<T, R> extends Function<T, R> {
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
