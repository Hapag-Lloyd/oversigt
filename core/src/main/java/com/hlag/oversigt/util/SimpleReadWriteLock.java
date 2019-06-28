package com.hlag.oversigt.util;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public class SimpleReadWriteLock {
	private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

	public SimpleReadWriteLock() {
		// empty by design
	}

	public <T> T read(final Supplier<T> supplier) {
		lock.readLock().lock();
		try {
			return supplier.get();
		} finally {
			lock.readLock().unlock();
		}
	}

	public void write(final Runnable runnable) {
		lock.writeLock().lock();
		try {
			runnable.run();
		} finally {
			lock.writeLock().unlock();
		}
	}
}
