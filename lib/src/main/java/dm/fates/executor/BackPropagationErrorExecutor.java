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

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import dm.fates.config.BuildConfig;
import dm.fates.eventual.FailureException;
import dm.fates.util.ConstantConditions;

/**
 * Created by davide-maestroni on 03/02/2018.
 */
class BackPropagationErrorExecutor extends ScheduledExecutorDecorator implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private transient volatile Throwable mError;

  /**
   * Constructor.
   *
   * @param executor the wrapped instance.
   */
  BackPropagationErrorExecutor(@NotNull final ScheduledExecutor executor) {
    super(executor);
  }

  @Override
  public void execute(@NotNull final Runnable command) {
    if (command instanceof FailingRunnable) {
      super.execute(new FailingRunnableWrapper((FailingRunnable) command));
      return;
    }

    checkFailed();
    super.execute(new RunnableWrapper(command));
  }

  @Override
  public void execute(@NotNull final Runnable command, final long delay,
      @NotNull final TimeUnit timeUnit) {
    if (command instanceof FailingRunnable) {
      super.execute(new FailingRunnableWrapper((FailingRunnable) command), delay, timeUnit);
      return;
    }

    checkFailed();
    super.execute(new RunnableWrapper(command), delay, timeUnit);
  }

  private void checkFailed() {
    final Throwable error = mError;
    if (error != null) {
      throw FailureException.wrapIfNot(RuntimeException.class, error);
    }
  }

  private class FailingRunnableWrapper implements Runnable {

    private final FailingRunnable mRunnable;

    private FailingRunnableWrapper(@NotNull final FailingRunnable runnable) {
      mRunnable = ConstantConditions.notNull("runnable", runnable);
    }

    public void run() {
      try {
        final Throwable error = mError;
        if (error != null) {
          mRunnable.fail(error);
          return;
        }

        mRunnable.run();

      } catch (final Throwable t) {
        if (mError == null) {
          mError = t;
        }
      }
    }
  }

  private class RunnableWrapper implements Runnable {

    private final Runnable mRunnable;

    private RunnableWrapper(@NotNull final Runnable runnable) {
      mRunnable = ConstantConditions.notNull("runnable", runnable);
    }

    public void run() {
      if (mError != null) {
        return;
      }

      try {
        mRunnable.run();

      } catch (final Throwable t) {
        mError = t;
      }
    }
  }
}
