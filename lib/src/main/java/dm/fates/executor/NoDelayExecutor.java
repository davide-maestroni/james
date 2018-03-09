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

package dm.fates.executor;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import dm.fates.config.BuildConfig;
import dm.fates.util.WeakIdentityHashMap;

/**
 * Executor decorator employing a synchronous executor when commands are enqueued with a 0 delay on
 * one of the managed threads.
 * <p>
 * Created by davide-maestroni on 04/09/2016.
 */
class NoDelayExecutor extends ScheduledExecutorDecorator implements Serializable {

  private static final WeakIdentityHashMap<ScheduledExecutor, WeakReference<NoDelayExecutor>>
      sExecutors = new WeakIdentityHashMap<ScheduledExecutor, WeakReference<NoDelayExecutor>>();

  private static final LoopExecutor sSyncExecutor = LoopExecutor.instance();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final ScheduledExecutor mExecutor;

  /**
   * Constructor.
   *
   * @param executor the wrapped instance.
   */
  private NoDelayExecutor(@NotNull final ScheduledExecutor executor) {
    super(executor);
    mExecutor = executor;
  }

  /**
   * Returns an instance wrapping the specified executor.
   *
   * @param wrapped the wrapped instance.
   * @return the zero delay executor.
   */
  @NotNull
  static NoDelayExecutor of(@NotNull final ScheduledExecutor wrapped) {
    if (wrapped instanceof NoDelayExecutor) {
      return (NoDelayExecutor) wrapped;
    }

    NoDelayExecutor noDelayExecutor;
    synchronized (sExecutors) {
      final WeakIdentityHashMap<ScheduledExecutor, WeakReference<NoDelayExecutor>> executors =
          sExecutors;
      final WeakReference<NoDelayExecutor> executor = executors.get(wrapped);
      noDelayExecutor = (executor != null) ? executor.get() : null;
      if (noDelayExecutor == null) {
        noDelayExecutor = new NoDelayExecutor(wrapped);
        executors.put(wrapped, new WeakReference<NoDelayExecutor>(noDelayExecutor));
      }
    }

    return noDelayExecutor;
  }

  @Override
  public void execute(@NotNull final Runnable command) {
    if (isOwnedThread()) {
      sSyncExecutor.execute(command);

    } else {
      super.execute(command);
    }
  }

  @Override
  public void execute(@NotNull final Runnable command, final long delay,
      @NotNull final TimeUnit timeUnit) {
    if (delay == 0) {
      execute(command);

    } else {
      super.execute(command, delay, timeUnit);
    }
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ExecutorProxy(mExecutor);
  }

  private static class ExecutorProxy implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ScheduledExecutor mExecutor;

    private ExecutorProxy(final ScheduledExecutor executor) {
      mExecutor = executor;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      try {
        return new NoDelayExecutor(mExecutor);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}
