package com.hlag.oversigt.util;

import java.util.function.Consumer;

import de.larssh.utils.SneakyException;
import edu.umd.cs.findbugs.annotations.Nullable;

@FunctionalInterface
public interface ThrowingConsumer<T> extends Consumer<T> {
	@Override
	default void accept(@Nullable final T t) {
		try {
			acceptThrowing(t);
		} catch (final Exception e) {
			throw e instanceof RuntimeException ? (RuntimeException) e : new SneakyException(e);
		}
	}

	void acceptThrowing(@Nullable T t) throws Exception;
}
