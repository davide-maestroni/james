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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import dm.fates.ExecutorOrderedLoopForker.ForkerStack;
import dm.fates.config.BuildConfig;
import dm.fates.eventual.EvaluationCollection;
import dm.fates.eventual.FailureException;
import dm.fates.eventual.Loop;
import dm.fates.eventual.LoopForker;
import dm.fates.eventual.SimpleState;
import dm.fates.executor.EvaluationExecutor;
import dm.fates.executor.ExecutorPool;

import static dm.fates.executor.ExecutorPool.withErrorBackPropagation;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
class ExecutorOrderedLoopForker<V> implements LoopForker<ForkerStack<V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final EvaluationExecutor mExecutor;

  ExecutorOrderedLoopForker(@NotNull final Executor executor) {
    mExecutor = ExecutorPool.register(executor);
  }

  public ForkerStack<V> done(final ForkerStack<V> stack, @NotNull final Loop<V> context) {
    if (stack.evaluation != null) {
      stack.execute(new ForkerRunnable(stack) {

        protected void innerRun() {
        }
      });

    } else {
      stack.states.add(SimpleState.<V>settled());
    }

    return stack;
  }

  public ForkerStack<V> evaluation(final ForkerStack<V> stack,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final Loop<V> context) throws
      Exception {
    if (stack.evaluation == null) {
      stack.evaluation = evaluation;
      try {
        for (final SimpleState<V> state : stack.states) {
          if (state.isSet()) {
            value(stack, state.value(), context);

          } else if (state.isFailed()) {
            failure(stack, state.failure(), context);

          } else {
            done(stack, context);
          }
        }

      } finally {
        stack.states = null;
      }

    } else {
      evaluation.addFailure(new IllegalStateException("the loop evaluation cannot be propagated"))
          .set();
    }

    return stack;
  }

  public ForkerStack<V> failure(final ForkerStack<V> stack, @NotNull final Throwable failure,
      @NotNull final Loop<V> context) {
    if (stack.evaluation != null) {
      stack.pendingCount.incrementAndGet();
      final NestedQueue<SimpleState<V>> queue;
      synchronized (stack) {
        queue = stack.queue.addNested();
      }

      stack.execute(new ForkerRunnable(stack) {

        protected void innerRun() {
          synchronized (stack) {
            queue.add(SimpleState.<V>ofFailure(failure));
            queue.close();
          }
        }
      });

    } else {
      stack.states.add(SimpleState.<V>ofFailure(failure));
    }

    return stack;
  }

  public ForkerStack<V> init(@NotNull final Loop<V> context) throws Exception {
    return new ForkerStack<V>(mExecutor);
  }

  public ForkerStack<V> value(final ForkerStack<V> stack, final V value,
      @NotNull final Loop<V> context) throws Exception {
    if (stack.evaluation != null) {
      stack.pendingCount.incrementAndGet();
      final NestedQueue<SimpleState<V>> queue;
      synchronized (stack) {
        queue = stack.queue.addNested();
      }

      stack.execute(new ForkerRunnable(stack) {

        protected void innerRun() {
          synchronized (stack) {
            queue.add(SimpleState.ofValue(value));
            queue.close();
          }
        }
      });

    } else {
      stack.states.add(SimpleState.ofValue(value));
    }

    return stack;
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<V>(mExecutor);
  }

  static class ForkerStack<V> implements Executor {

    private final Executor mExecutor;

    private final AtomicLong pendingCount = new AtomicLong(1);

    private final NestedQueue<SimpleState<V>> queue = new NestedQueue<SimpleState<V>>();

    private EvaluationCollection<V> evaluation;

    private ArrayList<SimpleState<V>> states = new ArrayList<SimpleState<V>>();

    private ForkerStack(@NotNull final EvaluationExecutor executor) {
      mExecutor = withErrorBackPropagation(executor);
    }

    public void execute(@NotNull final Runnable runnable) {
      mExecutor.execute(runnable);
    }

    private void flushQueue(@NotNull final EvaluationCollection<V> evaluation) {
      final ArrayList<SimpleState<V>> states = new ArrayList<SimpleState<V>>();
      synchronized (this) {
        queue.transferTo(states);
      }

      for (final SimpleState<V> state : states) {
        state.addTo(evaluation);
      }
    }
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
        return new ExecutorOrderedLoopForker<V>(mExecutor);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private abstract class ForkerRunnable implements Runnable {

    private final ForkerStack<V> mStack;

    private ForkerRunnable(@NotNull final ForkerStack<V> stack) {
      mStack = stack;
    }

    public void run() {
      final ForkerStack<V> stack = mStack;
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
