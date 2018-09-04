package com.hlag.oversigt.util;

import java.util.function.Consumer;

@FunctionalInterface
public interface ThrowingConsumer<T> extends Consumer<T> {

	@Override
	default void accept(T t) {
		try {
			acceptThrowing(t);
		} catch (Exception e) {
			throw e instanceof RuntimeException ? (RuntimeException) e : new SneakyException(e);
		}
	}

	void acceptThrowing(T t) throws Exception;
}