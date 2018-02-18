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
import java.util.concurrent.atomic.AtomicLong;

import dm.jale.ExecutorLoopForker.Stack;
import dm.jale.async.EvaluationCollection;
import dm.jale.async.FailureException;
import dm.jale.async.Loop;
import dm.jale.async.LoopForker;
import dm.jale.config.BuildConfig;
import dm.jale.executor.EvaluationExecutor;
import dm.jale.executor.ExecutorPool;
import dm.jale.log.Logger;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
class ExecutorLoopForker<V> extends BufferedForker<Stack<V>, V, EvaluationCollection<V>, Loop<V>> {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  ExecutorLoopForker(@NotNull final Executor executor, @Nullable final String loggerName) {
    super(new InnerForker<V>(executor, loggerName));
  }

  static class Stack<V> {

    private final AtomicLong pendingCount = new AtomicLong(1);

    private EvaluationCollection<V> evaluation;

    private volatile Throwable failure;
  }

  private static class InnerForker<V> implements LoopForker<Stack<V>, V>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final EvaluationExecutor mExecutor;

    private final Logger mLogger;

    private InnerForker(@NotNull final Executor executor, @Nullable final String loggerName) {
      mExecutor = ExecutorPool.register(executor);
      mLogger = Logger.newLogger(this, loggerName, Locale.ENGLISH);
    }

    private static void checkFailed(@NotNull final Stack<?> stack) {
      final Throwable failure = stack.failure;
      if (failure != null) {
        throw FailureException.wrap(failure);
      }
    }

    public Stack<V> done(final Stack<V> stack, @NotNull final Loop<V> context) {
      checkFailed(stack);
      mExecutor.execute(new ForkerRunnable(stack) {

        protected void innerRun(@NotNull final EvaluationCollection<V> evaluation) {
        }
      });
      return stack;
    }

    public Stack<V> evaluation(final Stack<V> stack,
        @NotNull final EvaluationCollection<V> evaluation, @NotNull final Loop<V> context) {
      checkFailed(stack);
      if (stack.evaluation == null) {
        stack.evaluation = evaluation;

      } else {
        evaluation.addFailure(new IllegalStateException()).set();
      }

      return stack;
    }

    public Stack<V> failure(final Stack<V> stack, @NotNull final Throwable failure,
        @NotNull final Loop<V> context) {
      checkFailed(stack);
      stack.pendingCount.incrementAndGet();
      mExecutor.execute(new ForkerRunnable(stack) {

        protected void innerRun(@NotNull final EvaluationCollection<V> evaluation) {
          evaluation.addFailure(failure);
        }
      });
      return stack;
    }

    public Stack<V> init(@NotNull final Loop<V> context) throws Exception {
      return new Stack<V>();
    }

    public Stack<V> value(final Stack<V> stack, final V value,
        @NotNull final Loop<V> context) throws Exception {
      checkFailed(stack);
      stack.pendingCount.incrementAndGet();
      mExecutor.execute(new ForkerRunnable(stack) {

        protected void innerRun(@NotNull final EvaluationCollection<V> evaluation) {
          evaluation.addValue(value);
        }
      });
      return stack;
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ForkerProxy<V>(mExecutor, mLogger.getName());
    }

    private static class ForkerProxy<V> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final Executor mExecutor;

      private final String mLoggerName;

      private ForkerProxy(final Executor executor, final String loggerName) {
        mExecutor = executor;
        mLoggerName = loggerName;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new ExecutorLoopForker<V>(mExecutor, mLoggerName);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private abstract class ForkerRunnable implements Runnable {

      private final Stack<V> mStack;

      private ForkerRunnable(@NotNull final Stack<V> stack) {
        mStack = stack;
      }

      public void run() {
        final Stack<V> stack = mStack;
        final EvaluationCollection<V> evaluation = stack.evaluation;
        try {
          if (stack.failure != null) {
            mLogger.wrn("Ignoring evaluation");
            return;
          }

          innerRun(evaluation);
          if (stack.pendingCount.decrementAndGet() == 0) {
            evaluation.set();
          }

        } catch (final CancellationException e) {
          mLogger.wrn(e, "Loop has been cancelled");
          stack.failure = e;

        } catch (final Throwable t) {
          mLogger.err(t, "Loop has failed");
          stack.failure = t;
        }
      }

      protected abstract void innerRun(@NotNull EvaluationCollection<V> evaluation) throws
          Exception;
    }
  }
}
