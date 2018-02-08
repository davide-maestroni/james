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
import java.util.concurrent.Executor;

import dm.jail.async.AsyncEvaluation;
import dm.jail.config.BuildConfig;
import dm.jail.executor.ExecutorPool;
import dm.jail.log.LogLevel;
import dm.jail.log.LogPrinter;
import dm.jail.log.Logger;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class ExecutorStatementHandler<V> extends AsyncStatementHandler<V, V> implements Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Executor mExecutor;

  private final Logger mLogger;

  ExecutorStatementHandler(@NotNull final Executor executor, @Nullable final LogPrinter printer,
      @Nullable final LogLevel level) {
    mExecutor = ExecutorPool.register(executor);
    mLogger = Logger.newLogger(this, printer, level);
  }

  @Override
  void failure(@NotNull final Throwable failure,
      @NotNull final AsyncEvaluation<V> evaluation) throws Exception {
    if (failure instanceof CancellationException) {
      try {
        evaluation.fail(failure);

      } catch (final Throwable t) {
        mLogger.dbg(t, "Suppressed failure");
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
    final Logger logger = mLogger;
    return new HandlerProxy<V>(mExecutor, logger.getLogPrinter(), logger.getLogLevel());
  }

  private static class HandlerProxy<V> implements Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final Executor mExecutor;

    private final LogLevel mLogLevel;

    private final LogPrinter mLogPrinter;

    private HandlerProxy(final Executor executor, final LogPrinter printer, final LogLevel level) {
      mExecutor = executor;
      mLogPrinter = printer;
      mLogLevel = level;
    }

    @NotNull
    Object readResolve() throws ObjectStreamException {
      try {
        return new ExecutorStatementHandler<V>(mExecutor, mLogPrinter, mLogLevel);

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
        mLogger.dbg(t, "Suppressed failure");
      }
    }

    protected abstract void innerRun(@NotNull AsyncEvaluation<V> evaluation);
  }
}
