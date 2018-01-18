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
import java.util.concurrent.TimeUnit;

import dm.jail.util.ConstantConditions;
import dm.jail.util.SerializableProxy;

/**
 * Created by davide-maestroni on 08/03/2017.
 */
class DelayedExecutor extends ScheduledExecutorDecorator implements Serializable {

  private final long mDelay;

  private final ScheduledExecutor mExecutor;

  private final TimeUnit mTimeUnit;

  /**
   * Constructor.
   *
   * @param executor the wrapped instance.
   */
  private DelayedExecutor(@NotNull final ScheduledExecutor executor, final long delay,
      @NotNull final TimeUnit timeUnit) {
    super(executor);
    mExecutor = executor;
    mTimeUnit = ConstantConditions.notNull("timeUnit", timeUnit);
    mDelay = ConstantConditions.notNegative("delay", delay);
  }

  @NotNull
  static DelayedExecutor of(@NotNull final ScheduledExecutor executor, final long delay,
      @NotNull final TimeUnit timeUnit) {
    return new DelayedExecutor(executor, delay, timeUnit);
  }

  @Override
  public void execute(@NotNull final Runnable command) {
    super.execute(command, mDelay, mTimeUnit);
  }

  @Override
  public void execute(@NotNull final Runnable command, final long delay,
      @NotNull final TimeUnit timeUnit) {
    ConstantConditions.notNegative("delay", delay);
    final TimeUnit currentUnit = mTimeUnit;
    final long newDelay;
    final TimeUnit newUnit;
    if (currentUnit.compareTo(timeUnit) > 0) {
      newDelay = timeUnit.convert(mDelay, currentUnit) + delay;
      newUnit = timeUnit;

    } else {
      newDelay = mDelay + currentUnit.convert(delay, timeUnit);
      newUnit = currentUnit;
    }

    super.execute(command, newDelay, newUnit);
  }

  private Object writeReplace() throws ObjectStreamException {
    return new ExecutorProxy(mExecutor, mDelay, mTimeUnit);
  }

  private static class ExecutorProxy extends SerializableProxy {

    private ExecutorProxy(final ScheduledExecutor executor, final long delay,
        final TimeUnit timeUnit) {
      super(executor, delay, timeUnit);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new DelayedExecutor((ScheduledExecutor) args[0], (Long) args[1], (TimeUnit) args[2]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}
