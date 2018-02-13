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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;

import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.WeakIdentityHashMap;

/**
 * Class implementing an executor employing an executor service.
 * <p>
 * Created by davide-maestroni on 10/14/2014.
 */
class OwnerExecutorServiceWrapper implements OwnerExecutor, StoppableExecutor, Serializable {

  private static final WeakIdentityHashMap<ExecutorService, OwnerExecutorServiceWrapper> sOwners =
      new WeakIdentityHashMap<ExecutorService, OwnerExecutorServiceWrapper>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final ThreadLocal<Boolean> mIsManaged = new ThreadLocal<Boolean>();

  private final ExecutorService mService;

  /**
   * Constructor.
   *
   * @param service the executor service.
   */
  private OwnerExecutorServiceWrapper(@NotNull final ExecutorService service) {
    mService = ConstantConditions.notNull("service", service);
  }

  /**
   * Returns an executor instance employing the specified service.
   *
   * @param service the executor service.
   * @return the executor.
   */
  @NotNull
  static OwnerExecutorServiceWrapper of(@NotNull final ExecutorService service) {
    OwnerExecutorServiceWrapper ownerExecutor;
    synchronized (sOwners) {
      ownerExecutor = sOwners.get(service);
      if (ownerExecutor == null) {
        ownerExecutor = new OwnerExecutorServiceWrapper(service);
        sOwners.put(service, ownerExecutor);
      }
    }

    return ownerExecutor;
  }

  public void execute(@NotNull final Runnable command) {
    mService.execute(new RunnableWrapper(command));
  }

  public boolean isOwnedThread() {
    return Boolean.TRUE.equals(mIsManaged.get());
  }

  public void stop() {
    mService.shutdown();
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ExecutorProxy(mService);
  }

  private static class ExecutorProxy implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ExecutorService mService;

    private ExecutorProxy(@NotNull final ExecutorService service) {
      mService = service;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      try {
        return of(mService);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
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
