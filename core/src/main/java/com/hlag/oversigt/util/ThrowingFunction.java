package com.hlag.oversigt.util;

import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<T, R> extends Function<T, R> {
	@Override
	default R apply(T t) {
		try {
			return applyThrowing(t);
		} catch (Exception e) {
			throw e instanceof RuntimeException ? (RuntimeException) e : new SneakyException(e);
		}
	}

	R applyThrowing(T t) throws Exception;
}