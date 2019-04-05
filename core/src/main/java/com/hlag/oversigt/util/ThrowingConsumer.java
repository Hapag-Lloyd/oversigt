package com.hlag.oversigt.util;

import java.util.function.Consumer;

@FunctionalInterface
public interface ThrowingConsumer<T> extends Consumer<T> {

	@Override
	default void accept(final T t) {
		try {
			acceptThrowing(t);
		} catch (final Exception e) {
			throw e instanceof RuntimeException ? (RuntimeException) e : new SneakyException(e);
		}
	}

	void acceptThrowing(T t) throws Exception;
}
