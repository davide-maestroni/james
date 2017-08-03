/*
 * Copyright 2017 Davide Maestroni
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

package dm.james.executor;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;
import dm.james.util.WeakIdentityHashMap;

/**
 * Class implementing an executor employing an executor service.
 * <p>
 * Created by davide-maestroni on 10/14/2014.
 */
class ServiceExecutor extends AsyncExecutor implements Serializable {

  private final WeakIdentityHashMap<Runnable, WeakHashMap<ScheduledFuture<?>, Void>> mFutures =
      new WeakIdentityHashMap<Runnable, WeakHashMap<ScheduledFuture<?>, Void>>();

  private final ScheduledExecutorService mService;

  /**
   * Constructor.
   *
   * @param service the executor service.
   */
  private ServiceExecutor(@NotNull final ScheduledExecutorService service) {
    super(new ScheduledThreadManager());
    mService = ConstantConditions.notNull("executor service", service);
  }

  /**
   * Returns an executor instance employing the specified service.
   *
   * @param service the executor service.
   * @return the executor.
   */
  @NotNull
  static ServiceExecutor of(@NotNull final ScheduledExecutorService service) {
    return new ServiceExecutor(service);
  }

  /**
   * Returns an executor instance employing the specified service.
   * <br>
   * The returned executor will shut down the service as soon as it is stopped.
   *
   * @param service the executor service.
   * @return the executor.
   */
  @NotNull
  static ServiceExecutor ofStoppable(@NotNull final ScheduledExecutorService service) {
    return new StoppableServiceExecutor(service);
  }

  @Override
  public void cancel(@NotNull final Runnable command) {
    final WeakHashMap<ScheduledFuture<?>, Void> scheduledFutures;
    synchronized (mFutures) {
      scheduledFutures = mFutures.remove(command);
    }

    if (scheduledFutures != null) {
      for (final ScheduledFuture<?> future : scheduledFutures.keySet()) {
        future.cancel(false);
      }
    }
  }

  @Override
  public void execute(@NotNull final Runnable command, final long delay,
      @NotNull final TimeUnit timeUnit) {
    final ScheduledFuture<?> future =
        mService.schedule(new RunnableWrapper(command), delay, timeUnit);
    synchronized (mFutures) {
      final WeakIdentityHashMap<Runnable, WeakHashMap<ScheduledFuture<?>, Void>> futures = mFutures;
      WeakHashMap<ScheduledFuture<?>, Void> scheduledFutures = futures.get(command);
      if (scheduledFutures == null) {
        scheduledFutures = new WeakHashMap<ScheduledFuture<?>, Void>();
        futures.put(command, scheduledFutures);
      }

      scheduledFutures.put(future, null);
    }
  }

  @NotNull
  @Override
  protected ScheduledThreadManager getThreadManager() {
    return (ScheduledThreadManager) super.getThreadManager();
  }

  private Object writeReplace() throws ObjectStreamException {
    return new ExecutorProxy(mService);
  }

  private static class ExecutorProxy extends SerializableProxy {

    private ExecutorProxy(final ScheduledExecutorService service) {
      super(service);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ServiceExecutor((ScheduledExecutorService) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  /**
   * Thread manager implementation.
   */
  private static class ScheduledThreadManager implements ThreadManager {

    private final ThreadLocal<Boolean> mIsManaged = new ThreadLocal<Boolean>();

    public boolean isManagedThread() {
      final Boolean isManaged = mIsManaged.get();
      return (isManaged != null) && isManaged;
    }

    private void setManaged() {
      mIsManaged.set(true);
    }
  }

  /**
   * Implementation of a scheduled executor shutting down the backing service as soon as the
   * executor is stopped.
   */
  private static class StoppableServiceExecutor extends ServiceExecutor {

    private final ScheduledExecutorService mService;

    /**
     * Constructor.
     *
     * @param service the executor service.
     */
    private StoppableServiceExecutor(final ScheduledExecutorService service) {
      super(service);
      mService = service;
    }

    @Override
    public void stop() {
      mService.shutdown();
    }
  }

  /**
   * Class used to keep track of the threads employed by this executor.
   */
  private class RunnableWrapper implements Runnable {

    private final Runnable mCommand;

    private final long mCurrentThreadId;

    /**
     * Constructor.
     *
     * @param wrapped the wrapped command.
     */
    private RunnableWrapper(@NotNull final Runnable wrapped) {
      mCommand = wrapped;
      mCurrentThreadId = Thread.currentThread().getId();
    }

    public void run() {
      final Thread currentThread = Thread.currentThread();
      if (currentThread.getId() != mCurrentThreadId) {
        getThreadManager().setManaged();
      }

      mCommand.run();
    }
  }
}
