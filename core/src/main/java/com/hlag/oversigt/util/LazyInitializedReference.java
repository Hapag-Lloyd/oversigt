package com.hlag.oversigt.util;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * https://stackoverflow.com/questions/20087173/how-to-do-a-lazy-create-and-set-with-atomicreference-in-a-safe-and-efficient-man
 *
 * @param <V> the type to hold in this class
 */
public class LazyInitializedReference<V> {
	private final AtomicReference<V> cachedValue = new AtomicReference<>();
	private final Callable<V> callable;

	public LazyInitializedReference(final Callable<V> callable) {
		this.callable = callable;
	}

	public synchronized V get() {
		try {
			if (cachedValue.get() == null) {
				cachedValue.set(callable.call());
			}
			return cachedValue.get();
		} catch (ExecutionException e) {
			/* Re-set the reference. */
			cachedValue.set(null);

			final Throwable cause = e.getCause();
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw toUnchecked(cause);
		} catch (final InterruptedException ie) {
			/* Re-set the reference. */
			cachedValue.set(null);

			/* It's the client's responsibility to check the cause. */
			throw new RuntimeException("Interrupted", ie);
		} catch (Exception e) {
			throw toUnchecked(e);
		}
	}

	public Optional<V> peek() {
		return Optional.ofNullable(cachedValue.get());
	}

	public V reset() {
		return cachedValue.getAndSet(null);
	}

	private static RuntimeException toUnchecked(final Throwable t) {
		return t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException("Unchecked", t);
	}
}