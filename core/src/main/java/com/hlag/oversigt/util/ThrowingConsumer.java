package com.hlag.oversigt.util;

import java.util.function.Consumer;

import de.larssh.utils.SneakyException;

@FunctionalInterface
public interface ThrowingConsumer<T> extends Consumer<T> {
	static <T> Consumer<T> sneakc(final ThrowingConsumer<T> consumer) {
		return consumer;
	}

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
