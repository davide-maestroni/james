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

package dm.jail;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.CancellationException;

import dm.jail.async.AsyncResultCollection;
import dm.jail.config.BuildConfig;
import dm.jail.executor.ScheduledExecutor;
import dm.jail.log.LogPrinter;
import dm.jail.log.LogPrinter.Level;
import dm.jail.log.Logger;
import dm.jail.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ExecutorLoopHandler<V> extends AsyncLoopHandler<V, V> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final ScheduledExecutor mExecutor;

  private final Logger mLogger;

  ExecutorLoopHandler(@NotNull final ScheduledExecutor executor, @Nullable final LogPrinter printer,
      @Nullable final Level level) {
    mExecutor = ConstantConditions.notNull("executor", executor);
    mLogger = Logger.newLogger(printer, level, this);
  }

  @Override
  void addFailure(@NotNull final Throwable failure,
      @NotNull final AsyncResultCollection<V> results) {
    if (failure instanceof CancellationException) {
      try {
        results.addFailure(failure).set();

      } catch (final Throwable t) {
        mLogger.dbg(t, "Suppressed failure");
      }

    } else {
      mExecutor.execute(new HandlerRunnable(results) {

        @Override
        protected void innerRun(@NotNull final AsyncResultCollection<V> results) {
          results.addFailure(failure).set();
        }
      });
    }
  }

  @Override
  void addFailures(@Nullable final Iterable<Throwable> failures,
      @NotNull final AsyncResultCollection<V> results) {
    mExecutor.execute(new HandlerRunnable(results) {

      @Override
      protected void innerRun(@NotNull final AsyncResultCollection<V> results) {
        results.addFailures(failures).set();
      }
    });
  }

  @Override
  void addValue(final V value, @NotNull final AsyncResultCollection<V> results) {
    mExecutor.execute(new HandlerRunnable(results) {

      @Override
      protected void innerRun(@NotNull final AsyncResultCollection<V> results) {
        results.addValue(value).set();
      }
    });
  }

  @Override
  void addValues(@Nullable final Iterable<V> values,
      @NotNull final AsyncResultCollection<V> results) {
    mExecutor.execute(new HandlerRunnable(results) {

      @Override
      protected void innerRun(@NotNull final AsyncResultCollection<V> results) {
        results.addValues(values).set();
      }
    });
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    final Logger logger = mLogger;
    return new HandlerProxy<V>(mExecutor, logger.getLogPrinter(), logger.getLogLevel());
  }

  private static class HandlerProxy<V> implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final ScheduledExecutor mExecutor;

    private final Level mLevel;

    private final LogPrinter mPrinter;

    private HandlerProxy(final ScheduledExecutor executor, final LogPrinter printer,
        final Level level) {
      mExecutor = executor;
      mPrinter = printer;
      mLevel = level;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      try {
        return new ExecutorLoopHandler<V>(mExecutor, mPrinter, mLevel);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private abstract class HandlerRunnable implements Runnable {

    private final AsyncResultCollection<V> mResults;

    private HandlerRunnable(@NotNull final AsyncResultCollection<V> results) {
      mResults = results;
    }

    public void run() {
      try {
        innerRun(mResults);

      } catch (final Throwable t) {
        mLogger.dbg(t, "Suppressed failure");
      }
    }

    protected abstract void innerRun(@NotNull AsyncResultCollection<V> results);
  }
}
