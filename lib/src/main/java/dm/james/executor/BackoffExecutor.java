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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import dm.james.util.Backoff;
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;
import dm.james.util.TimeUtils;
import dm.james.util.TimeUtils.Condition;

/**
 * Created by davide-maestroni on 08/03/2017.
 */
class BackoffExecutor extends ScheduledExecutorDecorator implements Serializable {

  private final Backoff<Integer> mBackoff;

  private final ScheduledExecutor mExecutor;

  private final Object mMutex = new Object();

  private int mCount;

  private final Condition mCondition = new Condition() {

    public boolean isTrue() {
      try {
        return (mBackoff.getDelay(mCount) < 0);

      } catch (final Exception e) {
        throw new RejectedExecutionException(e);
      }
    }
  };

  /**
   * Constructor.
   *
   * @param wrapped the wrapped instance.
   */
  private BackoffExecutor(@NotNull final ScheduledExecutor wrapped,
      @NotNull final Backoff<Integer> backoff) {
    super(wrapped);
    mExecutor = wrapped;
    mBackoff = ConstantConditions.notNull("backoff", backoff);
  }

  @NotNull
  static BackoffExecutor of(@NotNull final ScheduledExecutor wrapped,
      @NotNull final Backoff<Integer> backoff) {
    return new BackoffExecutor(wrapped, backoff);
  }

  @Override
  public void execute(@NotNull final Runnable command) {
    waitBackoff();
    super.execute(new BackoffCommand(command));
  }

  @Override
  public void execute(@NotNull final Runnable command, final long delay,
      @NotNull final TimeUnit timeUnit) {
    waitBackoff();
    super.execute(new BackoffCommand(command), delay, timeUnit);
  }

  private void waitBackoff() {
    final ScheduledExecutor executor = mExecutor;
    if (executor.isExecutionThread()) {
      throw new RejectedExecutionException(
          "cannot wait on executor thread [" + Thread.currentThread() + " " + executor + "]");
    }

    synchronized (mMutex) {
      try {
        final long delay = mBackoff.getDelay(++mCount);
        if (delay >= 0) {
          TimeUtils.waitUntil(mMutex, mCondition, delay, TimeUnit.MILLISECONDS);
        }

      } catch (final Exception e) {
        throw new RejectedExecutionException(e);
      }
    }
  }

  private Object writeReplace() throws ObjectStreamException {
    return new ExecutorProxy(mExecutor, mBackoff);
  }

  private static class ExecutorProxy extends SerializableProxy {

    private ExecutorProxy(final ScheduledExecutor wrapped, final Backoff backoff) {
      super(wrapped, backoff);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new BackoffExecutor((ScheduledExecutor) args[0], (Backoff) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private class BackoffCommand extends RunnableDecorator {

    /**
     * Constructor.
     *
     * @param wrapped the wrapped instance.
     */
    private BackoffCommand(@NotNull final Runnable wrapped) {
      super(wrapped);
    }

    public void run() {
      try {
        super.run();

      } finally {
        synchronized (mMutex) {
          --mCount;
          mMutex.notifyAll();
        }
      }
    }
  }
}
