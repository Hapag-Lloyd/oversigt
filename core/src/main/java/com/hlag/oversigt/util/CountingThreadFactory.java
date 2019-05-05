package com.hlag.oversigt.util;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A thread factory that is counting the number of threads created. Every thread
 * created can be uniquely identified by its number.
 *
 * @author Olaf Neumann
 *
 */
public class CountingThreadFactory implements ThreadFactory {
	/**
	 * Create a new thread factory with the given name prefix and all created
	 * threads are daemon threads.
	 *
	 * @param namePrefix the prefix to be prepended to every newly created thread
	 *                   name
	 * @return the newly created {@link CountingThreadFactory}
	 * @throws NullPointerException if one of the parameter is <code>null</code>
	 */
	public static ThreadFactory createDaemonThreadFactory(final String namePrefix) {
		return new CountingThreadFactory(namePrefix, t -> {
			t.setDaemon(true);
		});
	}

	/**
	 * A counter to identify every thread by a number
	 */
	private final AtomicInteger threadCounter = new AtomicInteger(0);

	/**
	 * the name prefix to be prepended to new threads' names
	 */
	private final String namePrefix;

	private final Consumer<Thread> threadModifier;

	/**
	 * Create a new thread factory with the given name prefix.
	 *
	 * @param namePrefix the prefix to be prepended to every newly created thread
	 *                   name
	 * @throws NullPointerException if one of the parameter is <code>null</code>
	 */
	public CountingThreadFactory(final String namePrefix) {
		this(namePrefix, t -> {/* do nothing */});
	}

	/**
	 * Create a new thread factory with the given name prefix and with the specified
	 * consumer to modify the thread after creation.
	 *
	 * @param namePrefix     the prefix to be prepended to every newly created
	 *                       thread name
	 * @param threadModifier a consumer that is called for each newly created
	 *                       thread. It can be used to modify the thread be the
	 *                       thread factory releases it for usage.
	 * @throws NullPointerException if one of the parameter is <code>null</code>
	 */
	public CountingThreadFactory(final String namePrefix, final Consumer<Thread> threadModifier) {
		this.namePrefix = Objects.requireNonNull(namePrefix);
		this.threadModifier = Objects.requireNonNull(threadModifier);
	}

	/** {@inheritDoc} */
	@Override
	public Thread newThread(final Runnable runnable) {
		final Thread thread = new Thread(runnable, namePrefix + threadCounter.incrementAndGet());
		threadModifier.accept(thread);
		return thread;
	}
}
