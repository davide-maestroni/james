/*
 * Copyright 2018 Davide Maestroni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dm.jail.executor;

import org.jetbrains.annotations.NotNull;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import dm.jail.util.ConstantConditions;

/**
 * Utility class for creating and sharing executor instances.
 * <p>
 * Created by davide-maestroni on 09/09/2014.
 */
@SuppressWarnings("WeakerAccess")
public class ScheduledExecutors {

  private static final Object sMutex = new Object();

  private static ScheduledExecutor sBackgroundExecutor;

  private static ScheduledExecutor sDefaultExecutor;

  private static ScheduledExecutor sForegroundExecutor;

  /**
   * Avoid explicit instantiation.
   */
  protected ScheduledExecutors() {
    ConstantConditions.avoid();
  }

  @NotNull
  public static ScheduledExecutor backgroundExecutor() {
    synchronized (sMutex) {
      if (sBackgroundExecutor == null) {
        sBackgroundExecutor = new BackgroundExecutor();
      }

      return sBackgroundExecutor;
    }
  }

  /**
   * Returns the default instance of a thread pool asynchronous executor.
   *
   * @return the executor instance.
   */
  @NotNull
  public static ScheduledExecutor defaultExecutor() {
    synchronized (sMutex) {
      if (sDefaultExecutor == null) {
        sDefaultExecutor = new DefaultExecutor();
      }

      return sDefaultExecutor;
    }
  }

  @NotNull
  public static ScheduledExecutor foregroundExecutor() {
    synchronized (sMutex) {
      if (sForegroundExecutor == null) {
        sForegroundExecutor = new ForegroundExecutor();
      }

      return sForegroundExecutor;
    }
  }

  /**
   * Returns the shared instance of an immediate executor.
   * <p>
   * The returned executor will immediately run any passed command.
   * <p>
   * Be careful when employing the returned executor, since it may lead to recursive calls, thus
   * causing the invocation lifecycle to be not strictly honored. In fact, it might happen, for
   * example, that the abortion method is called in the middle of the execution of another
   * invocation method.
   *
   * @return the executor instance.
   */
  @NotNull
  public static ScheduledExecutor immediateExecutor() {
    return ImmediateExecutor.instance();
  }

  /**
   * Returns the shared instance of a synchronous loop executor.
   * <p>
   * The returned executor maintains an internal buffer of executions that are consumed only when
   * the last one completes, thus avoiding overflowing the call stack because of nested calls to
   * other routines.
   *
   * @return the executor instance.
   */
  @NotNull
  public static ScheduledExecutor loopExecutor() {
    return LoopExecutor.instance();
  }

  /**
   * Returns an executor employing a dynamic pool of threads.
   * <br>
   * The number of threads may increase when needed from the core to the maximum pool size. The
   * number of threads exceeding the core size are kept alive when idle for the specified time.
   * If they stay idle for a longer time they will be destroyed.
   *
   * @param corePoolSize    the number of threads to keep in the pool, even if they are idle.
   * @param maximumPoolSize the maximum number of threads to allow in the pool.
   * @param keepAliveTime   when the number of threads is greater than the core one, this is the
   *                        maximum time that excess idle threads will wait for new tasks before
   *                        terminating.
   * @param keepAliveUnit   the time unit for the keep alive time.
   * @return the executor instance.
   * @throws IllegalArgumentException if one of the following holds:<ul>
   *                                  <li>{@code corePoolSize < 0}</li>
   *                                  <li>{@code maximumPoolSize <= 0}</li>
   *                                  <li>{@code keepAliveTime < 0}</li></ul>
   */
  @NotNull
  public static ScheduledExecutor newDynamicPoolExecutor(final int corePoolSize,
      final int maximumPoolSize, final long keepAliveTime, @NotNull final TimeUnit keepAliveUnit) {
    return newStoppableServiceExecutor(
        new DynamicScheduledThreadPoolExecutorService(corePoolSize, maximumPoolSize, keepAliveTime,
            keepAliveUnit));
  }

  /**
   * Returns an executor employing a dynamic pool of threads.
   * <br>
   * The number of threads may increase when needed from the core to the maximum pool size. The
   * number of threads exceeding the core size are kept alive when idle for the specified time.
   * If they stay idle for a longer time they will be destroyed.
   *
   * @param corePoolSize    the number of threads to keep in the pool, even if they are idle.
   * @param maximumPoolSize the maximum number of threads to allow in the pool.
   * @param keepAliveTime   when the number of threads is greater than the core one, this is the
   *                        maximum time that excess idle threads will wait for new tasks before
   *                        terminating.
   * @param keepAliveUnit   the time unit for the keep alive time.
   * @param threadFactory   the thread factory.
   * @return the executor instance.
   * @throws IllegalArgumentException if one of the following holds:<ul>
   *                                  <li>{@code corePoolSize < 0}</li>
   *                                  <li>{@code maximumPoolSize <= 0}</li>
   *                                  <li>{@code keepAliveTime < 0}</li></ul>
   */
  @NotNull
  public static ScheduledExecutor newDynamicPoolExecutor(final int corePoolSize,
      final int maximumPoolSize, final long keepAliveTime, @NotNull final TimeUnit keepAliveUnit,
      @NotNull final ThreadFactory threadFactory) {
    return newStoppableServiceExecutor(
        new DynamicScheduledThreadPoolExecutorService(corePoolSize, maximumPoolSize, keepAliveTime,
            keepAliveUnit, threadFactory));
  }

  /**
   * Returns an executor employing an optimum number of threads.
   *
   * @return the executor instance.
   */
  @NotNull
  public static ScheduledExecutor newPoolExecutor() {
    return new PoolExecutor();
  }

  /**
   * Returns an executor employing the specified number of threads.
   *
   * @param poolSize the thread pool size.
   * @return the executor instance.
   * @throws IllegalArgumentException if the pool size is less than 1.
   */
  @NotNull
  public static ScheduledExecutor newPoolExecutor(final int poolSize) {
    return new PoolExecutor(poolSize);
  }

  /**
   * Returns an executor employing the specified executor service.
   * <p>
   * Be aware that the created executor will not fully comply with the interface contract. Java
   * executor services do not in fact publish the used threads, so that knowing in advance whether
   * a thread belongs to the managed pool is not feasible. This issue actually exposes routines
   * employing the executor to possible deadlocks, in case the specified service is not exclusively
   * accessed by the executor itself.
   * <br>
   * Be then careful when employing executors returned by this method.
   *
   * @param service the executor service.
   * @return the executor instance.
   */
  @NotNull
  public static ScheduledExecutor newServiceExecutor(
      @NotNull final ScheduledExecutorService service) {
    return ServiceExecutor.of(service);
  }

  /**
   * Returns an executor employing the specified executor service.
   * <p>
   * Be aware that the created executor will not fully comply with the interface contract. Java
   * executor services do not in fact publish the used threads, so that knowing in advance whether
   * a thread belongs to the managed pool is not feasible. This issue actually exposes routines
   * employing the executor to possible deadlocks, in case the specified service is not exclusively
   * accessed by the executor itself.
   * <br>
   * Be then careful when employing executors returned by this method.
   *
   * @param service the executor service.
   * @return the executor instance.
   */
  @NotNull
  public static ScheduledExecutor newServiceExecutor(@NotNull final ExecutorService service) {
    return newServiceExecutor(new ScheduledThreadPoolExecutorService(service));
  }

  @NotNull
  public static ScheduledExecutor newStoppableServiceExecutor(
      @NotNull final ScheduledExecutorService service) {
    return ServiceExecutor.ofStoppable(service);
  }

  @NotNull
  public static ScheduledExecutor newStoppableServiceExecutor(
      @NotNull final ExecutorService service) {
    return newStoppableServiceExecutor(new ScheduledThreadPoolExecutorService(service));
  }

  @NotNull
  public static ScheduledExecutor withDelay(@NotNull final ScheduledExecutor executor,
      final long delay, @NotNull final TimeUnit timeUnit) {
    if (ConstantConditions.notNegative("delay", delay) == 0) {
      return executor;
    }

    return DelayedExecutor.of(executor, delay, timeUnit);
  }

  /**
   * Returns an executor providing ordering of executions based on priority.
   * <p>
   * Each enqueued command will age every time an higher priority one takes the precedence, so that
   * older commands slowly increases their priority. Such mechanism has been implemented to avoid
   * starvation of low priority commands. Hence, when assigning priority to different executors, it
   * is important to keep in mind that the difference between two priorities corresponds to the
   * maximum age the lower priority command will have, before getting precedence over the higher
   * priority one.
   * <p>
   * Note that applying a priority to a synchronous executor might make command invocations happen
   * in different threads than the calling one, thus causing the results to be not immediately
   * available.
   *
   * @param executor the wrapped executor instance.
   * @param priority the commands priority.
   * @return the executor instance.
   */
  @NotNull
  public static ScheduledExecutor withPriority(@NotNull final ScheduledExecutor executor,
      final int priority) {
    return PriorityExecutor.of(executor, priority);
  }

  /**
   * Returns an executor throttling the number of running executions so to keep it under the
   * specified limit.
   * <p>
   * Note that applying throttling to a synchronous executor might make command invocations happen
   * in different threads than the calling one, thus causing the results to be not immediately
   * available.
   *
   * @param executor      the wrapped instance.
   * @param maxExecutions the maximum number of running executions.
   * @return the executor instance.
   * @throws IllegalArgumentException if the specified max number is less than 1.
   */
  @NotNull
  public static ScheduledExecutor withThrottling(@NotNull final ScheduledExecutor executor,
      final int maxExecutions) {
    return ThrottlingExecutor.of(executor, maxExecutions);
  }

  /**
   * Returns an executor employing a shared synchronous one when executions are enqueued with a 0
   * delay on one of the managed threads.
   *
   * @param executor the wrapped instance.
   * @return the executor instance.
   */
  @NotNull
  public static ScheduledExecutor withZeroDelay(@NotNull final ScheduledExecutor executor) {
    return ZeroDelayExecutor.of(executor);
  }

  @NotNull
  private static ScheduledExecutor optimizedExecutor(final int threadPriority) {
    final int processors = Runtime.getRuntime().availableProcessors();
    return newServiceExecutor(
        new DynamicScheduledThreadPoolExecutorService(Math.max(2, processors >> 1),
            Math.max(2, (processors << 1) - 1), 10L, TimeUnit.SECONDS,
            new ExecutorThreadFactory(threadPriority)));
  }

  private static class BackgroundExecutor extends ScheduledExecutorDecorator
      implements Serializable {

    /**
     * Constructor.
     */
    private BackgroundExecutor() {
      super(optimizedExecutor(Thread.MIN_PRIORITY));
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ExecutorProxy();
    }

    private static class ExecutorProxy implements Serializable {

      Object readResolve() throws ObjectStreamException {
        return backgroundExecutor();
      }
    }
  }

  private static class DefaultExecutor extends ScheduledExecutorDecorator implements Serializable {

    /**
     * Constructor.
     */
    private DefaultExecutor() {
      super(optimizedExecutor(Thread.NORM_PRIORITY));
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ExecutorProxy();
    }

    private static class ExecutorProxy implements Serializable {

      Object readResolve() throws ObjectStreamException {
        return defaultExecutor();
      }
    }
  }

  private static class ExecutorThreadFactory implements ThreadFactory, Serializable {

    private final int mPriority;

    private ExecutorThreadFactory(final int priority) {
      if ((priority < Thread.MIN_PRIORITY) || (priority > Thread.MAX_PRIORITY)) {
        throw new IllegalArgumentException();
      }

      mPriority = priority;
    }

    public Thread newThread(@NotNull final Runnable runnable) {
      final Thread thread = new Thread(runnable);
      thread.setName("jail-" + thread.getName());
      thread.setPriority(mPriority);
      return thread;
    }
  }

  private static class ForegroundExecutor extends ScheduledExecutorDecorator
      implements Serializable {

    /**
     * Constructor.
     */
    private ForegroundExecutor() {
      super(optimizedExecutor(Thread.MAX_PRIORITY));
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ExecutorProxy();
    }

    private static class ExecutorProxy implements Serializable {

      Object readResolve() throws ObjectStreamException {
        return foregroundExecutor();
      }
    }
  }

  private static class PoolExecutor extends ScheduledExecutorDecorator implements Serializable {

    private final int mPoolSize;

    private PoolExecutor() {
      super(newStoppableServiceExecutor(
          Executors.newScheduledThreadPool((Runtime.getRuntime().availableProcessors() << 1) - 1)));
      mPoolSize = Integer.MIN_VALUE;
    }

    private PoolExecutor(final int poolSize) {
      super(newStoppableServiceExecutor(Executors.newScheduledThreadPool(poolSize)));
      mPoolSize = poolSize;
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ExecutorProxy(mPoolSize);
    }

    private static class ExecutorProxy implements Serializable {

      private final int mPoolSize;

      private ExecutorProxy(final int poolSize) {
        mPoolSize = poolSize;
      }

      Object readResolve() throws ObjectStreamException {
        return (mPoolSize == Integer.MIN_VALUE) ? new ScheduledExecutors.PoolExecutor()
            : new ScheduledExecutors.PoolExecutor(mPoolSize);
      }
    }
  }
}
