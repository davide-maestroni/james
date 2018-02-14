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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import dm.jale.ExecutorLoopBatchForker.Stack;
import dm.jale.async.AsyncEvaluations;
import dm.jale.async.AsyncLoop;
import dm.jale.async.AsyncStatement.Forker;
import dm.jale.config.BuildConfig;
import dm.jale.executor.ExecutorPool;
import dm.jale.executor.OwnerExecutor;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
class ExecutorLoopBatchForker<V>
    extends BufferedForker<Stack<V>, V, AsyncEvaluations<V>, AsyncLoop<V>> {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  ExecutorLoopBatchForker(@NotNull final Executor executor, final int maxValues,
      final int maxFailures) {
    super(new LoopForker<V>(executor, maxValues, maxFailures));
  }

  @Nullable
  private static <E> List<E> copyOrNull(@NotNull final List<E> list) {
    final int size = list.size();
    return (size == 0) ? null
        : (size == 1) ? Collections.singletonList(list.get(0)) : new ArrayList<E>(list);
  }

  static class Stack<V> {

    private final ArrayList<Throwable> failures = new ArrayList<Throwable>();

    private final AtomicLong pendingCount = new AtomicLong();

    private final ArrayList<V> values = new ArrayList<V>();

    private AsyncEvaluations<V> evaluations;
  }

  private static class LoopForker<V>
      implements Forker<Stack<V>, V, AsyncEvaluations<V>, AsyncLoop<V>>, Serializable {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private final OwnerExecutor mExecutor;

    private final int mMaxFailures;

    private final int mMaxValues;

    private LoopForker(@NotNull final Executor executor, final int maxValues,
        final int maxFailures) {
      mExecutor = ExecutorPool.register(executor);
      mMaxValues = ConstantConditions.positive("maxValues", maxValues);
      mMaxFailures = ConstantConditions.positive("maxFailures", maxFailures);
    }

    public Stack<V> done(final Stack<V> stack, @NotNull final AsyncLoop<V> async) {
      mExecutor.execute(new Runnable() {

        public void run() {
          if (stack.pendingCount.decrementAndGet() == 0) {
            stack.evaluations.set();
          }
        }
      });

      return stack;
    }

    public Stack<V> evaluation(final Stack<V> stack, @NotNull final AsyncEvaluations<V> evaluations,
        @NotNull final AsyncLoop<V> async) {
      if (stack.evaluations == null) {
        stack.evaluations = evaluations;

      } else {
        evaluations.addFailure(new IllegalStateException()).set();
      }

      return stack;
    }

    public Stack<V> failure(final Stack<V> stack, @NotNull final Throwable failure,
        @NotNull final AsyncLoop<V> async) {
      final AtomicLong pendingCount = stack.pendingCount;
      final AsyncEvaluations<V> evaluations = stack.evaluations;
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
      mExecutor.execute(new Runnable() {

        public void run() {
          evaluations.addValues(valueList).addFailures(failureList);
          if (pendingCount.decrementAndGet() == 0) {
            evaluations.set();
          }
        }
      });

      return stack;
    }

    public Stack<V> init(@NotNull final AsyncLoop<V> async) throws Exception {
      return new Stack<V>();
    }

    public Stack<V> value(final Stack<V> stack, final V value,
        @NotNull final AsyncLoop<V> async) throws Exception {
      final AtomicLong pendingCount = stack.pendingCount;
      final AsyncEvaluations<V> evaluations = stack.evaluations;
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
      mExecutor.execute(new Runnable() {

        public void run() {
          evaluations.addFailures(failureList).addValues(valueList);
          if (pendingCount.decrementAndGet() == 0) {
            evaluations.set();
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
      Object readResolve() throws ObjectStreamException {
        try {
          return new ExecutorLoopBatchForker<V>(mExecutor, mMaxValues, mMaxFailures);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }
}
