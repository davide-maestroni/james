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

package dm.fates;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import dm.fates.ExecutorOrderedBatchLoopForker.Stack;
import dm.fates.config.BuildConfig;
import dm.fates.eventual.EvaluationCollection;
import dm.fates.eventual.FailureException;
import dm.fates.eventual.Loop;
import dm.fates.eventual.LoopForker;
import dm.fates.eventual.Observer;
import dm.fates.executor.EvaluationExecutor;
import dm.fates.executor.ExecutorPool;
import dm.fates.util.ConstantConditions;

import static dm.fates.executor.ExecutorPool.withErrorBackPropagation;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
class ExecutorOrderedBatchLoopForker<V>
    extends BufferedForker<Stack<V>, V, EvaluationCollection<V>, Loop<V>> {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  ExecutorOrderedBatchLoopForker(@NotNull final Executor executor, final int maxValues,
      final int maxFailures) {
    super(new InnerForker<V>(executor, maxValues, maxFailures));
  }

  @Nullable
  private static <E> List<E> copyOrNull(@NotNull final List<E> list) {
    final int size = list.size();
    return (size == 0) ? null
        : (size == 1) ? Collections.singletonList(list.get(0)) : new ArrayList<E>(list);
  }

  static class Stack<V> implements Executor {

    private final ArrayList<Throwable> failures = new ArrayList<Throwable>();

    private final Executor mExecutor;

    private final AtomicLong pendingCount = new AtomicLong(1);

    private final NestedQueue<Observer<EvaluationCollection<V>>> queue =
        new NestedQueue<Observer<EvaluationCollection<V>>>();

    private final ArrayList<V> values = new ArrayList<V>();

    private EvaluationCollection<V> evaluation;

    private Stack(@NotNull final EvaluationExecutor executor) {
      mExecutor = withErrorBackPropagation(executor);
    }

    public void execute(@NotNull final Runnable runnable) {
      mExecutor.execute(runnable);
    }

    private void flushQueue(@NotNull final EvaluationCollection<V> evaluation) throws Exception {
      final ArrayList<Observer<EvaluationCollection<V>>> observers =
          new ArrayList<Observer<EvaluationCollection<V>>>();
      synchronized (this) {
        queue.transferTo(observers);
      }

      for (final Observer<EvaluationCollection<V>> observer : observers) {
        observer.accept(evaluation);
      }
    }
  }

  private static class InnerForker<V> implements LoopForker<Stack<V>, V>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final EvaluationExecutor mExecutor;

    private final int mMaxFailures;

    private final int mMaxValues;

    private InnerForker(@NotNull final Executor executor, final int maxValues,
        final int maxFailures) {
      mExecutor = ExecutorPool.register(executor);
      mMaxValues = ConstantConditions.positive("maxValues", maxValues);
      mMaxFailures = ConstantConditions.positive("maxFailures", maxFailures);
    }

    public Stack<V> done(final Stack<V> stack, @NotNull final Loop<V> context) {
      final NestedQueue<Observer<EvaluationCollection<V>>> queue;
      synchronized (stack) {
        queue = stack.queue.addNested();
      }

      stack.execute(new ForkerRunnable(stack) {

        protected void innerRun() {
          synchronized (stack) {
            queue.add(new Observer<EvaluationCollection<V>>() {

              public void accept(final EvaluationCollection<V> evaluation) {
                evaluation.addFailures(stack.failures).addValues(stack.values);
              }
            });

            queue.close();
          }
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
      final AtomicLong pendingCount = stack.pendingCount;
      final ArrayList<V> values = stack.values;
      final List<V> valueList = copyOrNull(values);
      values.clear();
      final ArrayList<Throwable> failures = stack.failures;
      failures.add(failure);
      final List<Throwable> failureList;
      if (failures.size() >= mMaxFailures) {
        failureList = copyOrNull(failures);
        failures.clear();

      } else {
        failureList = null;
      }

      pendingCount.incrementAndGet();
      final NestedQueue<Observer<EvaluationCollection<V>>> queue;
      synchronized (stack) {
        queue = stack.queue.addNested();
      }

      stack.execute(new ForkerRunnable(stack) {

        protected void innerRun() {
          synchronized (stack) {
            queue.add(new Observer<EvaluationCollection<V>>() {

              public void accept(final EvaluationCollection<V> evaluation) {
                evaluation.addValues(valueList).addFailures(failureList);
              }
            });

            queue.close();
          }
        }
      });
      return stack;
    }

    public Stack<V> init(@NotNull final Loop<V> context) throws Exception {
      return new Stack<V>(mExecutor);
    }

    public Stack<V> value(final Stack<V> stack, final V value,
        @NotNull final Loop<V> context) throws Exception {
      final AtomicLong pendingCount = stack.pendingCount;
      final ArrayList<Throwable> failures = stack.failures;
      final List<Throwable> failureList = copyOrNull(failures);
      failures.clear();
      final ArrayList<V> values = stack.values;
      values.add(value);
      final List<V> valueList;
      if (values.size() >= mMaxValues) {
        valueList = copyOrNull(values);
        values.clear();

      } else {
        valueList = null;
      }

      pendingCount.incrementAndGet();
      final NestedQueue<Observer<EvaluationCollection<V>>> queue;
      synchronized (stack) {
        queue = stack.queue.addNested();
      }

      stack.execute(new ForkerRunnable(stack) {

        protected void innerRun() {
          synchronized (stack) {
            queue.add(new Observer<EvaluationCollection<V>>() {

              public void accept(final EvaluationCollection<V> evaluation) {
                evaluation.addFailures(failureList).addValues(valueList);
              }
            });

            queue.close();
          }
        }
      });
      return stack;
    }

    @NotNull
    private Object writeReplace() throws ObjectStreamException {
      return new ForkerProxy<V>(mExecutor, mMaxValues, mMaxFailures);
    }

    private static class ForkerProxy<V> implements Serializable {

      private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

      private final Executor mExecutor;

      private final int mMaxFailures;

      private final int mMaxValues;

      private ForkerProxy(final Executor executor, final int maxValues, final int maxFailures) {
        mExecutor = executor;
        mMaxValues = maxValues;
        mMaxFailures = maxFailures;
      }

      @NotNull
      private Object readResolve() throws ObjectStreamException {
        try {
          return new ExecutorOrderedBatchLoopForker<V>(mExecutor, mMaxValues, mMaxFailures);

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
          innerRun();
          stack.flushQueue(evaluation);
          if (stack.pendingCount.decrementAndGet() == 0) {
            evaluation.set();
          }

        } catch (final Throwable t) {
          throw FailureException.wrapIfNot(RuntimeException.class, t);
        }
      }

      protected abstract void innerRun() throws Exception;
    }
  }
}
