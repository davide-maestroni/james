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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import dm.fates.config.BuildConfig;
import dm.fates.util.ConstantConditions;

/**
 * Scheduled thread pool executor wrapping an executor service.
 * <p>
 * Created by davide-maestroni on 05/24/2016.
 */
class ScheduledThreadPoolExecutorService extends ScheduledThreadPoolExecutor
    implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final ExecutorService mExecutor;

  /**
   * Constructor.
   *
   * @param service the executor service.
   */
  ScheduledThreadPoolExecutorService(@NotNull final ExecutorService service) {
    super(1);
    mExecutor = ConstantConditions.notNull("service", service);
  }

  @NotNull
  @Override
  public ScheduledFuture<?> schedule(final Runnable command, final long delay,
      final TimeUnit unit) {
    return super.schedule(new CommandRunnable(mExecutor, command), delay, unit);
  }

  @NotNull
  @Override
  public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay,
      final TimeUnit unit) {
    return ConstantConditions.unsupported();
  }

  @NotNull
  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay,
      final long period, final TimeUnit unit) {
    return super.scheduleAtFixedRate(new CommandRunnable(mExecutor, command), initialDelay, period,
        unit);
  }

  @NotNull
  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay,
      final long delay, final TimeUnit unit) {
    return super.scheduleWithFixedDelay(new CommandRunnable(mExecutor, command), initialDelay,
        delay, unit);
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ExecutorProxy(mExecutor);
  }

  /**
   * Runnable executing another runnable.
   */
  private static class CommandRunnable implements Runnable {

    private final Runnable mCommand;

    private final ExecutorService mService;

    /**
     * Constructor.
     *
     * @param service the executor service.
     * @param command the command to execute.
     */
    private CommandRunnable(@NotNull final ExecutorService service,
        @NotNull final Runnable command) {
      mService = service;
      mCommand = command;
    }

    public void run() {
      mService.execute(mCommand);
    }
  }

  private static class ExecutorProxy implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ExecutorService mService;

    private ExecutorProxy(@NotNull final ExecutorService service) {
      mService = service;
    }

    @NotNull
    private Object readResolve() throws ObjectStreamException {
      try {
        return new ScheduledThreadPoolExecutorService(mService);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}
