package com.hlag.oversigt.util.function;

import java.util.function.Function;

import de.larssh.utils.SneakyException;
import edu.umd.cs.findbugs.annotations.Nullable;

@FunctionalInterface
public interface ThrowingFunction<T, R> extends Function<T, R> {
	static <T, R> Function<T, R> sneaky(final ThrowingFunction<T, R> function) {
		return function;
	}

	@Override
	default R apply(@Nullable final T t) {
		try {
			return applyThrowing(t);
		} catch (final Exception e) {
			throw e instanceof RuntimeException ? (RuntimeException) e : new SneakyException(e);
		}
	}

	R applyThrowing(@Nullable T t) throws Exception;
}
