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

package dm.jale.executor;

import org.jetbrains.annotations.NotNull;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.WeakIdentityHashMap;

/**
 * Utility class for creating and sharing executor instances.
 * <p>
 * Created by davide-maestroni on 09/09/2014.
 */
@SuppressWarnings("WeakerAccess")
public class ExecutorPool {

  // TODO: 14/02/2018 ExecutorPool.withBackoff()?
  public static final Runnable NO_OP = new Runnable() {

    public void run() {
    }
  };

  private static final Object sMutex = new Object();

  private static ScheduledExecutor sBackgroundExecutor;

  private static ScheduledExecutor sDefaultExecutor;

  private static ScheduledExecutor sForegroundExecutor;

  private static WeakIdentityHashMap<ThreadOwner, Void> sOwners =
      new WeakIdentityHashMap<ThreadOwner, Void>();

  /**
   * Avoid explicit instantiation.
   */
  protected ExecutorPool() {
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
  public static Executor immediateExecutor() {
    return ImmediateExecutor.instance();
  }

  public static boolean isOwnedThread() {
    for (final ThreadOwner owner : sOwners.keySet()) {
      if ((owner != null) && owner.isOwnedThread()) {
        return true;
      }
    }

    return false;
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
  public static Executor loopExecutor() {
    return LoopExecutor.instance();
  }

  @NotNull
  public static StoppableExecutor newCachedPoolExecutor() {
    return new PoolExecutor();
  }

  @NotNull
  public static StoppableExecutor newCachedPoolExecutor(
      @NotNull final ThreadFactory threadFactory) {
    return new PoolExecutor(threadFactory);
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
    return EvaluationScheduledExecutorServiceWrapper.of(
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
    return EvaluationScheduledExecutorServiceWrapper.of(
        new DynamicScheduledThreadPoolExecutorService(corePoolSize, maximumPoolSize, keepAliveTime,
            keepAliveUnit, threadFactory));
  }

  @NotNull
  public static StoppableExecutor newFixedPoolExecutor(final int nThreads) {
    return new PoolExecutor(nThreads);
  }

  @NotNull
  public static StoppableExecutor newFixedPoolExecutor(final int nThreads,
      @NotNull final ThreadFactory threadFactory) {
    return new PoolExecutor(nThreads, threadFactory);
  }

  /**
   * Returns an executor employing an optimum number of threads.
   *
   * @return the executor instance.
   */
  @NotNull
  public static ScheduledExecutor newScheduledPoolExecutor() {
    return new ScheduledPoolExecutor();
  }

  /**
   * Returns an executor employing the specified number of threads.
   *
   * @param corePoolSize the thread pool size.
   * @return the executor instance.
   * @throws IllegalArgumentException if the pool size is less than 1.
   */
  @NotNull
  public static ScheduledExecutor newScheduledPoolExecutor(final int corePoolSize) {
    return new ScheduledPoolExecutor(corePoolSize);
  }

  @NotNull
  public static ScheduledExecutor newScheduledPoolExecutor(final int poolSize,
      @NotNull final ThreadFactory threadFactory) {
    return new ScheduledPoolExecutor(poolSize, threadFactory);
  }

  @NotNull
  public static ScheduledExecutor newScheduledPoolExecutor(
      @NotNull final ThreadFactory threadFactory) {
    return new ScheduledPoolExecutor(threadFactory);
  }

  @NotNull
  public static Executor ordered(@NotNull final Executor executor) {
    return OrderedExecutor.of(asOwner(executor));
  }

  @NotNull
  public static ScheduledExecutor register(@NotNull final ScheduledExecutor executor) {
    // TODO: 17/02/2018 remove...
    return registerOwner(ConstantConditions.notNull("executor", executor));
  }

  @NotNull
  public static EvaluationExecutor register(@NotNull final Executor executor) {
    return registerOwner(asOwner(executor));
  }

  @NotNull
  public static ScheduledExecutor withDelay(final long delay, @NotNull final TimeUnit timeUnit,
      @NotNull final ScheduledExecutor executor) {
    // TODO: 02/03/2018 withInterval OR withThrottling(tasks/timeUnit)
    if (ConstantConditions.notNegative("delay", delay) == 0) {
      return executor;
    }

    return DelayedExecutor.of(executor, delay, timeUnit);
  }

  @NotNull
  public static ScheduledExecutor withErrorBackPropagation(
      @NotNull final ScheduledExecutor executor) {
    return new BackPropagationErrorExecutor(executor);
  }

  @NotNull
  public static Executor withErrorBackPropagation(@NotNull final Executor executor) {
    return withErrorBackPropagation(new ScheduledExecutorWrapper(asOwner(executor)));
  }

  /**
   * Returns an executor employing a synchronous one when executions are enqueued with a 0 delay on
   * one of the managed threads.
   *
   * @param executor the wrapped instance.
   * @return the executor instance.
   */
  @NotNull
  public static ScheduledExecutor withNoDelay(@NotNull final ScheduledExecutor executor) {
    return NoDelayExecutor.of(executor);
  }

  @NotNull
  public static Executor withNoDelay(@NotNull final Executor executor) {
    return withNoDelay(new ScheduledExecutorWrapper(asOwner(executor)));
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
   * @param priority the commands priority.
   * @param executor the wrapped executor instance.
   * @return the executor instance.
   */
  @NotNull
  public static ScheduledExecutor withPriority(final int priority,
      @NotNull final ScheduledExecutor executor) {
    return PriorityExecutor.of(executor, priority);
  }

  @NotNull
  public static Executor withPriority(final int priority, @NotNull final Executor executor) {
    return withPriority(priority, new ScheduledExecutorWrapper(asOwner(executor)));
  }

  /**
   * Returns an executor throttling the number of running executions so to keep it under the
   * specified limit.
   * <p>
   * Note that applying throttling to a synchronous executor might make command invocations happen
   * in different threads than the calling one, thus causing the results to be not immediately
   * available.
   *
   * @param maxExecutions the maximum number of running executions.
   * @param executor      the wrapped instance.
   * @return the executor instance.
   * @throws IllegalArgumentException if the specified max number is less than 1.
   */
  @NotNull
  public static ScheduledExecutor withThrottling(final int maxExecutions,
      @NotNull final ScheduledExecutor executor) {
    return ThrottlingExecutor.of(executor, maxExecutions);
  }

  @NotNull
  public static Executor withThrottling(final int maxExecutions, @NotNull final Executor executor) {
    return withThrottling(maxExecutions, new ScheduledExecutorWrapper(asOwner(executor)));
  }

  @NotNull
  private static EvaluationExecutor asOwner(@NotNull final Executor executor) {
    final EvaluationExecutor managedExecutor;
    if (executor instanceof EvaluationExecutor) {
      managedExecutor = (EvaluationExecutor) executor;

    } else if (executor instanceof ThreadOwner) {
      managedExecutor = ThreadOwnerExecutor.of(executor);

    } else if (executor instanceof ScheduledExecutorService) {
      managedExecutor =
          EvaluationScheduledExecutorServiceWrapper.of((ScheduledExecutorService) executor);

    } else if (executor instanceof ExecutorService) {
      managedExecutor = EvaluationExecutorServiceWrapper.of(((ExecutorService) executor));

    } else {
      managedExecutor = EvaluationExecutorWrapper.of(executor);
    }

    return managedExecutor;
  }

  @NotNull
  private static ScheduledExecutor optimizedExecutor(@NotNull final String threadName,
      final int threadPriority) {
    final int processors = Runtime.getRuntime().availableProcessors();
    return EvaluationScheduledExecutorServiceWrapper.ofUnstoppable(
        new DynamicScheduledThreadPoolExecutorService(Math.max(2, processors >> 1),
            Math.max(2, (processors << 1) - 1), 10L, TimeUnit.SECONDS,
            new PriorityThreadFactory(threadName, threadPriority)));
  }

  @NotNull
  private static <T extends EvaluationExecutor> T registerOwner(@NotNull final T owner) {
    EvaluationExecutor decorated = owner;
    while (decorated instanceof ExecutorDecorator) {
      decorated = ((ExecutorDecorator) decorated).getDecorated();
    }

    synchronized (sMutex) {
      if (!sOwners.containsKey(owner)) {
        final WeakIdentityHashMap<ThreadOwner, Void> owners =
            new WeakIdentityHashMap<ThreadOwner, Void>(sOwners);
        owners.put(decorated, null);
        sOwners = owners;
      }
    }

    return owner;
  }

  static class ThreadOwnerExecutor implements EvaluationExecutor, Serializable {

    private static final WeakIdentityHashMap<Executor, ThreadOwnerExecutor> sOwners =
        new WeakIdentityHashMap<Executor, ThreadOwnerExecutor>();

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Executor mExecutor;

    private final ThreadOwner mThreadOwner;

    @SuppressWarnings("unchecked")
    ThreadOwnerExecutor(@NotNull final Executor executor) {
      mExecutor = ConstantConditions.notNull("executor", executor);
      mThreadOwner = (ThreadOwner) executor;
    }

    @NotNull
    public static ThreadOwnerExecutor of(@NotNull final Executor executor) {
      ThreadOwnerExecutor ownerExecutor;
      synchronized (sOwners) {
        ownerExecutor = sOwners.get(executor);
        if (ownerExecutor == null) {
          ownerExecutor = new ThreadOwnerExecutor(executor);
          sOwners.put(executor, ownerExecutor);
        }
      }

      return ownerExecutor;
    }

    public void execute(@NotNull final Runnable runnable) {
      mExecutor.execute(runnable);
    }

    public boolean isOwnedThread() {
      return mThreadOwner.isOwnedThread();
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ExecutorProxy(mExecutor);
    }

    private static class ExecutorProxy implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final Executor mExecutor;

      private ExecutorProxy(final Executor executor) {
        mExecutor = executor;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        return ThreadOwnerExecutor.of(mExecutor);
      }
    }
  }

  private static class BackgroundExecutor extends ScheduledExecutorDecorator
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    /**
     * Constructor.
     */
    private BackgroundExecutor() {
      super(optimizedExecutor("jale-background-thread", Thread.MIN_PRIORITY));
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ExecutorProxy();
    }

    private static class ExecutorProxy implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        return backgroundExecutor();
      }
    }
  }

  private static class DefaultExecutor extends ScheduledExecutorDecorator implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    /**
     * Constructor.
     */
    private DefaultExecutor() {
      super(optimizedExecutor("jale-default-thread", Thread.NORM_PRIORITY));
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ExecutorProxy();
    }

    private static class ExecutorProxy implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        return defaultExecutor();
      }
    }
  }

  private static class ForegroundExecutor extends ScheduledExecutorDecorator
      implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    /**
     * Constructor.
     */
    private ForegroundExecutor() {
      super(optimizedExecutor("jale-foreground-thread", Thread.MAX_PRIORITY));
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ExecutorProxy();
    }

    private static class ExecutorProxy implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        return foregroundExecutor();
      }
    }
  }

  private static class PriorityThreadFactory implements ThreadFactory, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final String mName;

    private final int mPriority;

    private PriorityThreadFactory(@NotNull final String name, final int priority) {
      if ((priority < Thread.MIN_PRIORITY) || (priority > Thread.MAX_PRIORITY)) {
        throw new IllegalArgumentException(
            "priority level must be in the range [" + Thread.MIN_PRIORITY + ", "
                + Thread.MAX_PRIORITY + "]");
      }

      mName = name;
      mPriority = priority;
    }

    public Thread newThread(@NotNull final Runnable runnable) {
      final Thread thread = new Thread(runnable, mName);
      thread.setPriority(mPriority);
      return thread;
    }
  }
}
