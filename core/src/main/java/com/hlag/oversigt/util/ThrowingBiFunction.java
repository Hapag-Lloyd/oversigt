package com.hlag.oversigt.util;

import java.io.IOException;
import java.util.Objects;
import java.util.function.BiFunction;

import de.larssh.utils.SneakyException;
import edu.umd.cs.findbugs.annotations.Nullable;

@FunctionalInterface
public interface ThrowingBiFunction<T, U, R> extends BiFunction<T, U, R> {
	@Override
	default R apply(@Nullable final T t, @Nullable final U u) {
		try {
			return applyThrowing(Objects.requireNonNull(t), Objects.requireNonNull(u));
		} catch (final Exception e) {
			throw e instanceof RuntimeException ? (RuntimeException) e : new SneakyException(e);
		}
	}

	R applyThrowing(T t, U u) throws IOException;
}
