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
import java.util.concurrent.TimeUnit;

import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;
import dm.james.util.Threads;

/**
 * Class implementing an executor employing an executor service.
 * <p>
 * Created by davide-maestroni on 10/14/2014.
 */
class ServiceExecutor extends AsyncExecutor implements Serializable {

  private static final WeakHashMap<ScheduledExecutorService, Boolean> mOwners =
      new WeakHashMap<ScheduledExecutorService, Boolean>();

  private final ThreadLocal<Boolean> mIsManaged = new ThreadLocal<Boolean>();

  private final ScheduledExecutorService mService;

  /**
   * Constructor.
   *
   * @param service the executor service.
   */
  private ServiceExecutor(@NotNull final ScheduledExecutorService service) {
    mService = ConstantConditions.notNull("service", service);
  }

  /**
   * Returns an executor instance employing the specified service.
   *
   * @param service the executor service.
   * @return the executor.
   */
  @NotNull
  static ServiceExecutor of(@NotNull final ScheduledExecutorService service) {
    final ServiceExecutor executor = new ServiceExecutor(service);
    synchronized (mOwners) {
      if (!Boolean.TRUE.equals(mOwners.put(service, Boolean.TRUE))) {
        Threads.register(executor);
      }
    }

    return executor;
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
    final StoppableServiceExecutor executor = new StoppableServiceExecutor(service);
    synchronized (mOwners) {
      if (!Boolean.TRUE.equals(mOwners.put(service, Boolean.TRUE))) {
        Threads.register(executor);
      }
    }

    return executor;
  }

  public void execute(@NotNull final Runnable command, final long delay,
      @NotNull final TimeUnit timeUnit) {
    mService.schedule(new RunnableWrapper(command), delay, timeUnit);
  }

  public boolean isOwnedThread() {
    return Boolean.TRUE.equals(mIsManaged.get());
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
     * @param command the wrapped command.
     */
    private RunnableWrapper(@NotNull final Runnable command) {
      mCommand = command;
      mCurrentThreadId = Thread.currentThread().getId();
    }

    public void run() {
      final Thread currentThread = Thread.currentThread();
      if (currentThread.getId() != mCurrentThreadId) {
        mIsManaged.set(true);
      }

      mCommand.run();
    }
  }
}
