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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import dm.jail.util.WeakIdentityHashMap;

/**
 * Executor decorator employing a shared synchronous executor when commands are enqueued with a 0
 * delay on one of the managed threads.
 * <p>
 * Created by davide-maestroni on 04/09/2016.
 */
class ZeroDelayExecutor extends ScheduledExecutorDecorator implements Serializable {

  private static final WeakIdentityHashMap<ScheduledExecutor, WeakReference<ZeroDelayExecutor>>
      sExecutors = new WeakIdentityHashMap<ScheduledExecutor, WeakReference<ZeroDelayExecutor>>();

  private static final LoopExecutor sSyncExecutor = LoopExecutor.instance();

  private final ScheduledExecutor mExecutor;

  /**
   * Constructor.
   *
   * @param executor the wrapped instance.
   */
  private ZeroDelayExecutor(@NotNull final ScheduledExecutor executor) {
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
  static ZeroDelayExecutor of(@NotNull final ScheduledExecutor wrapped) {
    if (wrapped instanceof ZeroDelayExecutor) {
      return (ZeroDelayExecutor) wrapped;
    }

    ZeroDelayExecutor zeroDelayExecutor;
    synchronized (sExecutors) {
      final WeakIdentityHashMap<ScheduledExecutor, WeakReference<ZeroDelayExecutor>> executors =
          sExecutors;
      final WeakReference<ZeroDelayExecutor> executor = executors.get(wrapped);
      zeroDelayExecutor = (executor != null) ? executor.get() : null;
      if (zeroDelayExecutor == null) {
        zeroDelayExecutor = new ZeroDelayExecutor(wrapped);
        executors.put(wrapped, new WeakReference<ZeroDelayExecutor>(zeroDelayExecutor));
      }
    }

    return zeroDelayExecutor;
  }

  @Override
  public void execute(@NotNull final Runnable command) {
    if (isExecutionThread()) {
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

  private Object writeReplace() throws ObjectStreamException {
    return new ExecutorProxy(mExecutor);
  }

  private static class ExecutorProxy implements Serializable {

    private final ScheduledExecutor mExecutor;

    private ExecutorProxy(final ScheduledExecutor executor) {
      mExecutor = executor;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      try {
        return new ZeroDelayExecutor(mExecutor);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}
