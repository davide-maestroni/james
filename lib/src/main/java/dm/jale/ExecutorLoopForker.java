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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import dm.jale.ExecutorLoopForker.Stack;
import dm.jale.config.BuildConfig;
import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.FailureException;
import dm.jale.eventual.Loop;
import dm.jale.eventual.LoopForker;
import dm.jale.executor.EvaluationExecutor;
import dm.jale.executor.ExecutorPool;

import static dm.jale.executor.ExecutorPool.withErrorBackPropagation;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
class ExecutorLoopForker<V> extends BufferedForker<Stack<V>, V, EvaluationCollection<V>, Loop<V>> {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  ExecutorLoopForker(@NotNull final Executor executor) {
    super(new InnerForker<V>(executor));
  }

  static class Stack<V> implements Executor {

    private final Executor mExecutor;

    private final AtomicLong pendingCount = new AtomicLong(1);

    private EvaluationCollection<V> evaluation;

    private Stack(@NotNull final EvaluationExecutor executor) {
      mExecutor = withErrorBackPropagation(executor);
    }

    public void execute(@NotNull final Runnable runnable) {
      mExecutor.execute(runnable);
    }
  }

  private static class InnerForker<V> implements LoopForker<Stack<V>, V>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final EvaluationExecutor mExecutor;

    private InnerForker(@NotNull final Executor executor) {
      mExecutor = ExecutorPool.register(executor);
    }

    public Stack<V> done(final Stack<V> stack, @NotNull final Loop<V> context) {
      stack.execute(new ForkerRunnable(stack) {

        protected void innerRun(@NotNull final EvaluationCollection<V> evaluation) {
        }
      });
      return stack;
    }

    public Stack<V> evaluation(final Stack<V> stack,
        @NotNull final EvaluationCollection<V> evaluation, @NotNull final Loop<V> context) {
      if (stack.evaluation == null) {
        stack.evaluation = evaluation;

      } else {
        evaluation.addFailure(new IllegalStateException("the loop evaluation cannot be propagated"))
            .set();
      }

      return stack;
    }

    public Stack<V> failure(final Stack<V> stack, @NotNull final Throwable failure,
        @NotNull final Loop<V> context) {
      stack.pendingCount.incrementAndGet();
      stack.execute(new ForkerRunnable(stack) {

        protected void innerRun(@NotNull final EvaluationCollection<V> evaluation) {
          evaluation.addFailure(failure);
        }
      });
      return stack;
    }

    public Stack<V> init(@NotNull final Loop<V> context) throws Exception {
      return new Stack<V>(mExecutor);
    }

    public Stack<V> value(final Stack<V> stack, final V value,
        @NotNull final Loop<V> context) throws Exception {
      stack.pendingCount.incrementAndGet();
      stack.execute(new ForkerRunnable(stack) {

        protected void innerRun(@NotNull final EvaluationCollection<V> evaluation) {
          evaluation.addValue(value);
        }
      });
      return stack;
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ForkerProxy<V>(mExecutor);
    }

    private static class ForkerProxy<V> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final Executor mExecutor;

      private ForkerProxy(final Executor executor) {
        mExecutor = executor;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new ExecutorLoopForker<V>(mExecutor);

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
          innerRun(evaluation);
          if (stack.pendingCount.decrementAndGet() == 0) {
            evaluation.set();
          }

        } catch (final Throwable t) {
          throw FailureException.wrapIfNot(RuntimeException.class, t);
        }
      }

      protected abstract void innerRun(@NotNull EvaluationCollection<V> evaluation) throws
          Exception;
    }
  }
}
