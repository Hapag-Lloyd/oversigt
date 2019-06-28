package com.hlag.oversigt.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import org.jboss.weld.exceptions.IllegalStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.atlassian.util.concurrent.atomic.AtomicInteger;

/**
 * An executor that uses a variable number of threads to execute the provided
 * tasks. The number of threads grows slowly if required.
 *
 * @author Olaf Neumann
 *
 */
public class GrowingExecutor {
	private static final int DEFAULT_CORE_POOL_SIZE = 1;

	private static final int DEFAULT_MAXIMUM_NUMBER_OF_THREADS = Integer.MAX_VALUE;

	private static final Duration DEFAULT_TIME_BEFORE_ADDING_NEW_THREADS = Duration.ofSeconds(1);

	private static final Duration DEFAULT_TIME_BEFORE_SHUTTING_DOWN_THREADS = Duration.ofMinutes(5);

	/** The logger to write information to a log file */
	private static final Logger LOGGER = LoggerFactory.getLogger(GrowingExecutor.class);

	/**
	 * a counter for the number of instances of the {@link GrowingExecutor} class
	 */
	private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

	/**
	 * Executor for scheduling tasks that need to be done after certain time
	 */
	private static final ScheduledExecutorService TIMES = Executors.newScheduledThreadPool(1,
			CountingThreadFactory.createDaemonThreadFactory("GrowingExecutor-TimerThread-"));

	/**
	 * The thread factory creating threads for this executor
	 */
	private final ThreadFactory threadFactory;

	/**
	 * The minimum number of threads to keep alive
	 */
	private final int corePoolSize;

	/**
	 * The maximum number of threads to create for this executor
	 */
	private final int maximumNumberOfThreads;

	/**
	 * The list of threads working for this executor
	 */
	private final List<Thread> threads = new ArrayList<>();

	/**
	 * The tasks waiting for execution
	 */
	private final LinkedList<FutureTask<?>> tasks = new LinkedList<>();

	/**
	 * The time to wait before a new thread will be added if there's still work to
	 * do
	 */
	private final Duration waitBeforeAddingThreads;

	/**
	 * The time to wait before ending a thread if there is no more task awaiting
	 * execution
	 */
	private final Duration waitBeforeThreadShutdown;

	/**
	 * mutex object to acquire the monitor for when manipulating the task list
	 */
	private final Object taskMutex = new Object();

	/**
	 * mutex object to acquire the monitor for when manipulating the list of
	 * threads<br>
	 * <em>Attention:</em> In order to avoid deadlocks you <strong>must</strong>
	 * acquire the monitor for {@link #taskMutex} before acquiring this monitor!
	 */
	private final Object threadMutex = new Object();

	/**
	 * Flag indicating whether a timer has been set and is waiting for execution
	 */
	private final AtomicBoolean timerSet = new AtomicBoolean(false);

	/**
	 * A flag indicating whether this executor is accepting new tasks
	 */
	private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);

	/**
	 * Create a new {@link GrowingExecutor} with default settings.<br>
	 * The default settings are:
	 * <ul>
	 * <li>Core pool size: 1</li>
	 * <li>Unlimited number of additional threads</li>
	 * <li>Wait one second before adding new threads to the executor</li>
	 * <li>Wait 5 minutes before ending a thread</li>
	 * </ul>
	 */
	public GrowingExecutor() {
		this(DEFAULT_CORE_POOL_SIZE,
				DEFAULT_MAXIMUM_NUMBER_OF_THREADS,
				DEFAULT_TIME_BEFORE_ADDING_NEW_THREADS,
				DEFAULT_TIME_BEFORE_SHUTTING_DOWN_THREADS,
				CountingThreadFactory.createDaemonThreadFactory(
						GrowingExecutor.class.getSimpleName() + "-" + INSTANCE_COUNTER.incrementAndGet() + "-thread-"));
	}

	/**
	 * Create a new {@link GrowingExecutor} with the given configuration
	 *
	 * @param corePoolSize             the minimum number of threads to maintain if
	 *                                 the executor has nothing to do
	 * @param maximumNumberOfThreads   the maximum number of threads to create if
	 *                                 the executor is busy
	 * @param waitBeforeAddingThreads  time to wait before adding a new thread to
	 *                                 the executor
	 * @param waitBeforeThreadShutdown time to keep a thread alive if there's
	 *                                 nothing to do
	 * @param threadFactory            the thread factory creating new threads for
	 *                                 the executor
	 */
	public GrowingExecutor(final int corePoolSize,
			final int maximumNumberOfThreads,
			final Duration waitBeforeAddingThreads,
			final Duration waitBeforeThreadShutdown,
			final ThreadFactory threadFactory) {
		this.corePoolSize = corePoolSize;
		this.maximumNumberOfThreads = maximumNumberOfThreads;
		this.waitBeforeAddingThreads = waitBeforeAddingThreads;
		this.waitBeforeThreadShutdown = waitBeforeThreadShutdown;
		this.threadFactory = threadFactory;
	}

	/**
	 * Execute the given task and return a {@link Future} to retrieve the calculated
	 * result.
	 *
	 * @param <T>      the type return value
	 * @param callable the action to be executed
	 * @return a value to retrieve the calculated result
	 * @throws IllegalStateException if the {@link Callable} is submitted but
	 *                               {@link #shutdown()} has been called before.
	 */
	public <T> Future<T> execute(final Callable<T> callable) {
		LOGGER.debug("Executing callable");
		final FutureTask<T> task = new FutureTask<>(callable);
		queueFutureTask(task);
		return task;
	}

	/**
	 * Sets an internal flag that new tasks will not be accepted any more. Only
	 * already queued tasks will be executed.
	 */
	public void shutdown() {
		acceptingTasks.set(false);
	}

	/**
	 * Joins all running threads. This method blocks until all threads of this
	 * executor have ended.<br>
	 * If the core pool size is greater than 0 this method will never return if
	 * {@link #shutdown()} has not been called. On the other hand, this method will
	 * return if the core pool size is 0 but the executor is still accepting new
	 * tasks.
	 *
	 * @throws InterruptedException if a thread is interrupted while joining it
	 */
	public void join() throws InterruptedException {
		while (true) {
			final Thread thread;
			synchronized (threadMutex) {
				if (threads.isEmpty()) {
					return;
				}
				thread = threads.get(0);
			}
			thread.join();
		}
	}

	private void queueFutureTask(final FutureTask<?> task) {
		if (acceptingTasks.get()) {
			synchronized (taskMutex) {
				tasks.add(task);
				// inform possibly sleeping threads that there is something to do
				taskMutex.notifyAll();

				// start a timer to check whether new threads are needed
				scheduleCheckIfEnoughThreadsAreAvailable();
			}
		} else {
			throw new IllegalStateException("Executor is shutting down. New Callable will not be accepted.");
		}
	}

	private void scheduleCheckIfEnoughThreadsAreAvailable() {
		synchronized (taskMutex) {
			synchronized (threadMutex) {
				if (threads.isEmpty()) {
					checkIfEnoughThreadsAreAvailable();
				}
			}
		}
		if (timerSet.compareAndSet(false, true)) {
			TIMES.schedule(this::checkIfEnoughThreadsAreAvailable,
					waitBeforeAddingThreads.toMillis(),
					TimeUnit.MILLISECONDS);
		}
	}

	private void checkIfEnoughThreadsAreAvailable() {
		timerSet.set(false);
		synchronized (taskMutex) {
			synchronized (threadMutex) {
				if (!tasks.isEmpty() && threads.size() < maximumNumberOfThreads) {
					startNewExecutorThread();
					scheduleCheckIfEnoughThreadsAreAvailable();
				}
			}
		}
	}

	private void startNewExecutorThread() {
		final Thread thread = threadFactory.newThread(this::executeTasks);
		synchronized (taskMutex) {
			synchronized (threadMutex) {
				threads.add(thread);
			}
		}
		thread.start();
	}

	private void executeTasks() {
		LOGGER.info("Starting new executor thread.");
		FutureTask<?> task = null;
		while (true) {
			// get a new task to execute
			synchronized (taskMutex) {
				// wait one loop before shutting down the thread
				boolean firstLoop = true;
				while ((task = tasks.pollFirst()) == null) {
					// check if we're the only thread... if not, we can quit
					if (!firstLoop) {
						synchronized (threadMutex) {
							if (threads.size() > corePoolSize || !acceptingTasks.get()) {
								LOGGER.info("No more tasks waiting. Shutting down thread.");
								threads.remove(Thread.currentThread());
								return;
							}
						}
					}

					// wait until new stuff is available
					try {
						// TODO as every thread is being notified we will reach the end of the wait loop
						taskMutex.wait(Long.max(1, waitBeforeThreadShutdown.toMillis()));
						// too early and will end the thread to early...
						firstLoop = false;
					} catch (final InterruptedException e) {
						LOGGER.error("Error while waiting for new task.", e);
						// exit run loop
						return;
					}
				}
			}

			// execute the task
			LOGGER.debug("Running task");
			task.run();
			task = null;

			// check if we need to kill us
			if (Thread.interrupted()) {
				return;
			}
		}
	}

	/**
	 * Test method
	 *
	 * @param args command line arguments
	 * @throws Exception if something fails
	 */
	public static void main(final String[] args) throws Exception {
		final GrowingExecutor executor = new GrowingExecutor();
		IntStream.range(1, 21).mapToObj(GrowingExecutor::createCallable).forEach(executor::execute);
		System.out.println(Thread.currentThread().getName() + ": " + "Scheduled");
		Thread.sleep(2000);
		executor.shutdown();
		executor.join();
		System.out.println(Thread.currentThread().getName() + ": " + "Joined");
	}

	private static Callable<Integer> createCallable(final int retVal) {
		return () -> {
			final int seconds = (int) (5 + Math.random() * 5);
			Thread.sleep(seconds * 1000);
			System.out.println(Thread.currentThread().getName() + ": " + retVal);
			return retVal;
		};
	}

}
