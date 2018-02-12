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
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import dm.jail.async.AsyncEvaluations;
import dm.jail.config.BuildConfig;
import dm.jail.executor.ExecutorPool;
import dm.jail.log.Logger;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ExecutorLoopHandler<V> extends AsyncLoopHandler<V, V> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Executor mExecutor;

  private final Logger mLogger;

  ExecutorLoopHandler(@NotNull final Executor executor, @Nullable final String loggerName) {
    mExecutor = ExecutorPool.register(executor);
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
  }

  @Override
  void addFailure(@NotNull final Throwable failure,
      @NotNull final AsyncEvaluations<V> evaluations) {
    if (failure instanceof CancellationException) {
      try {
        evaluations.addFailure(failure).set();

      } catch (final Throwable t) {
        mLogger.err(t, "Suppressed failure");
      }

    } else {
      mExecutor.execute(new HandlerRunnable(evaluations) {

        @Override
        protected void innerRun(@NotNull final AsyncEvaluations<V> evaluations) {
          evaluations.addFailure(failure).set();
        }
      });
    }
  }

  @Override
  void addFailures(@Nullable final Iterable<? extends Throwable> failures,
      @NotNull final AsyncEvaluations<V> evaluations) {
    mExecutor.execute(new HandlerRunnable(evaluations) {

      @Override
      protected void innerRun(@NotNull final AsyncEvaluations<V> evaluations) {
        evaluations.addFailures(failures).set();
      }
    });
  }

  @Override
  void addValue(final V value, @NotNull final AsyncEvaluations<V> evaluations) {
    mExecutor.execute(new HandlerRunnable(evaluations) {

      @Override
      protected void innerRun(@NotNull final AsyncEvaluations<V> evaluations) {
        evaluations.addValue(value).set();
      }
    });
  }

  @Override
  void addValues(@Nullable final Iterable<? extends V> values,
      @NotNull final AsyncEvaluations<V> evaluations) {
    mExecutor.execute(new HandlerRunnable(evaluations) {

      @Override
      protected void innerRun(@NotNull final AsyncEvaluations<V> evaluations) {
        evaluations.addValues(values).set();
      }
    });
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new HandlerProxy<V>(mExecutor, mLogger.getName());
  }

  private static class HandlerProxy<V> implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Executor mExecutor;

    private final String mLoggerName;

    private HandlerProxy(final Executor executor, final String loggerName) {
      mExecutor = executor;
      mLoggerName = loggerName;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      try {
        return new ExecutorLoopHandler<V>(mExecutor, mLoggerName);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private abstract class HandlerRunnable implements Runnable {

    private final AsyncEvaluations<V> mEvaluations;

    private HandlerRunnable(@NotNull final AsyncEvaluations<V> evaluations) {
      mEvaluations = evaluations;
    }

    public void run() {
      try {
        innerRun(mEvaluations);

      } catch (final Throwable t) {
        mLogger.err(t, "Suppressed failure");
      }
    }

    protected abstract void innerRun(@NotNull AsyncEvaluations<V> evaluations);
  }
}
