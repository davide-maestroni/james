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

package dm.jale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import dm.jale.async.AsyncEvaluation;
import dm.jale.config.BuildConfig;
import dm.jale.executor.ExecutorPool;
import dm.jale.log.Logger;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ExecutorStatementHandler<V> extends AsyncStatementHandler<V, V> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Executor mExecutor;

  private final Logger mLogger;

  ExecutorStatementHandler(@NotNull final Executor executor, @Nullable final String loggerName) {
    mExecutor = ExecutorPool.register(executor);
    mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
  }

  @Override
  void failure(@NotNull final Throwable failure,
      @NotNull final AsyncEvaluation<V> evaluation) throws Exception {
    if (failure instanceof CancellationException) {
      try {
        evaluation.fail(failure);

      } catch (final Throwable t) {
        mLogger.err(t, "Suppressed failure");
      }

    } else {
      mExecutor.execute(new HandlerRunnable(evaluation) {

        @Override
        protected void innerRun(@NotNull final AsyncEvaluation<V> evaluation) {
          evaluation.fail(failure);
        }
      });
    }
  }

  @Override
  void value(final V value, @NotNull final AsyncEvaluation<V> evaluation) throws Exception {
    mExecutor.execute(new HandlerRunnable(evaluation) {

      @Override
      protected void innerRun(@NotNull final AsyncEvaluation<V> evaluation) {
        evaluation.set(value);
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
        return new ExecutorStatementHandler<V>(mExecutor, mLoggerName);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private abstract class HandlerRunnable implements Runnable {

    private final AsyncEvaluation<V> mEvaluation;

    private HandlerRunnable(@NotNull final AsyncEvaluation<V> evaluation) {
      mEvaluation = evaluation;
    }

    public void run() {
      try {
        innerRun(mEvaluation);

      } catch (final Throwable t) {
        mLogger.err(t, "Suppressed failure");
      }
    }

    protected abstract void innerRun(@NotNull AsyncEvaluation<V> evaluation);
  }
}
