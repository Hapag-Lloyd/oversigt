package com.hlag.oversigt.util;

import java.util.function.IntFunction;

@FunctionalInterface
public interface ThrowingIntFunction<T> extends IntFunction<T> {
	@Override
	default T apply(int value) {
		try {
			return applyThrowing(value);
		} catch (Exception e) {
			throw e instanceof RuntimeException ? (RuntimeException) e : new SneakyException(e);
		}
	}

	T applyThrowing(int value) throws Exception;
}