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

package dm.james.processor;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import dm.james.executor.ScheduledExecutor;
import dm.james.promise.Promise.Callback;
import dm.james.promise.Promise.Processor;
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public class ScheduleProcessor<I> implements Processor<I, I>, Serializable {

  // TODO: 21/07/2017 delay, backoff to ScheduledExecutors

  private final ScheduledExecutor mExecutor;

  ScheduleProcessor(@NotNull final ScheduledExecutor executor) {
    mExecutor = ConstantConditions.notNull("executor", executor);
  }

  @NotNull
  public ScheduleProcessor<I> delayed(final long delay, @NotNull final TimeUnit timeUnit) {
    return new DelayedProcessor<I>(mExecutor, delay, timeUnit);
  }

  public void reject(final Throwable reason, @NotNull final Callback<I> callback) {
    mExecutor.execute(new Runnable() {

      public void run() {
        callback.reject(reason);
      }
    });
  }

  public void resolve(final I input, @NotNull final Callback<I> callback) {
    mExecutor.execute(new Runnable() {

      public void run() {
        callback.resolve(input);
      }
    });
  }

  private Object writeReplace() throws ObjectStreamException {
    return new ProcessorProxy<I>(mExecutor);
  }

  private static class DelayedProcessor<I> extends ScheduleProcessor<I> {

    private final long mDelay;

    private final ScheduledExecutor mExecutor;

    private final TimeUnit mTimeUnit;

    private DelayedProcessor(@NotNull final ScheduledExecutor executor, final long delay,
        @NotNull final TimeUnit timeUnit) {
      super(executor);
      mExecutor = executor;
      mTimeUnit = ConstantConditions.notNull("time unit", timeUnit);
      mDelay = ConstantConditions.notNegative("delay", delay);
    }

    @NotNull
    @Override
    public ScheduleProcessor<I> delayed(final long delay, @NotNull final TimeUnit timeUnit) {
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

      return new DelayedProcessor<I>(mExecutor, newDelay, newUnit);
    }

    public void reject(final Throwable reason, @NotNull final Callback<I> callback) {
      mExecutor.execute(new Runnable() {

        public void run() {
          callback.reject(reason);
        }
      }, mDelay, mTimeUnit);
    }

    public void resolve(final I input, @NotNull final Callback<I> callback) {
      mExecutor.execute(new Runnable() {

        public void run() {
          callback.resolve(input);
        }
      }, mDelay, mTimeUnit);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new ProcessorProxy<I>(mExecutor, mDelay, mTimeUnit);
    }

    private static class ProcessorProxy<I> extends SerializableProxy {

      private ProcessorProxy(final ScheduledExecutor executor, final long delay,
          final TimeUnit timeUnit) {
        super(executor, delay, timeUnit);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new DelayedProcessor<I>((ScheduledExecutor) args[0], (Long) args[1],
              (TimeUnit) args[2]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class ProcessorProxy<I> extends SerializableProxy {

    private ProcessorProxy(final ScheduledExecutor executor) {
      super(executor);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new ScheduleProcessor<I>((ScheduledExecutor) args[0]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}
