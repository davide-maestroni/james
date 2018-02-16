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
import java.util.concurrent.Executor;

import dm.jale.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.WeakIdentityHashMap;

/**
 * Class implementing an executor employing an executor service.
 * <p>
 * Created by davide-maestroni on 10/14/2014.
 */
class OwnerExecutorWrapper implements OwnerExecutor, Serializable {

  private static final WeakIdentityHashMap<Executor, OwnerExecutorWrapper> sOwners =
      new WeakIdentityHashMap<Executor, OwnerExecutorWrapper>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Executor mExecutor;

  private final ThreadLocal<Boolean> mIsManaged = new ThreadLocal<Boolean>();

  /**
   * Constructor.
   *
   * @param executor the executor instance.
   */
  private OwnerExecutorWrapper(@NotNull final Executor executor) {
    mExecutor = ConstantConditions.notNull("executor", executor);
  }

  /**
   * Returns an executor instance employing the specified service.
   *
   * @param executor the executor instance.
   * @return the executor.
   */
  @NotNull
  static OwnerExecutorWrapper of(@NotNull final Executor executor) {
    OwnerExecutorWrapper ownerExecutor;
    synchronized (sOwners) {
      ownerExecutor = sOwners.get(executor);
      if (ownerExecutor == null) {
        ownerExecutor = new OwnerExecutorWrapper(executor);
        sOwners.put(executor, ownerExecutor);
      }
    }

    return ownerExecutor;
  }

  public void execute(@NotNull final Runnable command) {
    mExecutor.execute(new RunnableWrapper(command));
  }

  public boolean isOwnedThread() {
    return Boolean.TRUE.equals(mIsManaged.get());
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ExecutorProxy(mExecutor);
  }

  private static class ExecutorProxy implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Executor mExecutor;

    private ExecutorProxy(@NotNull final Executor executor) {
      mExecutor = executor;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      try {
        return of(mExecutor);

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
