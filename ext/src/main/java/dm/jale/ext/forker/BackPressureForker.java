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

package dm.jale.ext.forker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dm.jale.Eventual;
import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.Loop;
import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.Loop.Yielder;
import dm.jale.eventual.LoopForker;
import dm.jale.eventual.Observer;
import dm.jale.eventual.RuntimeInterruptedException;
import dm.jale.eventual.Statement;
import dm.jale.executor.EvaluationExecutor;
import dm.jale.executor.ExecutorPool;
import dm.jale.ext.backpressure.PendingOutputs;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.forker.BackPressureForker.ForkerOutputs;
import dm.jale.util.ConstantConditions;
import dm.jale.util.Iterables;
import dm.jale.util.SerializableProxy;
import dm.jale.util.TimeUnits;
import dm.jale.util.TimeUnits.Condition;

import static dm.jale.executor.ExecutorPool.NO_OP;

/**
 * Created by davide-maestroni on 02/09/2018.
 */
class BackPressureForker<S, V> implements LoopForker<ForkerOutputs<S, V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final EvaluationExecutor mExecutor;

  private final Yielder<S, V, ? super PendingOutputs<V>> mYielder;

  private BackPressureForker(@NotNull final Executor executor,
      @NotNull final Yielder<S, V, ? super PendingOutputs<V>> yielder) {
    mYielder = ConstantConditions.notNull("yielder", yielder);
    mExecutor = ExecutorPool.register(executor);
  }

  @NotNull
  static <S, V> LoopForker<?, V> newForker(@NotNull final Executor executor,
      @NotNull final Yielder<S, V, ? super PendingOutputs<V>> yielder) {
    return Eventual.bufferedLoop(new BackPressureForker<S, V>(executor, yielder));
  }

  public ForkerOutputs<S, V> done(final ForkerOutputs<S, V> stack,
      @NotNull final Loop<V> context) throws Exception {
    mYielder.done(stack.getStack(), stack);
    return stack.withStack(null).set();
  }

  public ForkerOutputs<S, V> evaluation(final ForkerOutputs<S, V> stack,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final Loop<V> context) throws
      Exception {
    if (!stack.setEvaluations(evaluation)) {
      evaluation.addFailure(new IllegalStateException("the loop evaluation cannot be propagated"))
          .set();
    }

    return stack;
  }

  public ForkerOutputs<S, V> failure(final ForkerOutputs<S, V> stack,
      @NotNull final Throwable failure, @NotNull final Loop<V> context) throws Exception {
    return stack.withStack(mYielder.failure(stack.getStack(), failure, stack));
  }

  public ForkerOutputs<S, V> init(@NotNull final Loop<V> context) throws Exception {
    return new ForkerOutputs<S, V>(mExecutor, mYielder.init());
  }

  public ForkerOutputs<S, V> value(final ForkerOutputs<S, V> stack, final V value,
      @NotNull final Loop<V> context) throws Exception {
    return stack.withStack(mYielder.value(stack.getStack(), value, stack));
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mExecutor, mYielder);
  }

  static class ForkerOutputs<S, V> implements PendingOutputs<V> {

    private final EvaluationExecutor mEvaluationExecutor;

    private final Executor mExecutor;

    private final AtomicBoolean mIsSet = new AtomicBoolean(false);

    private final Object mMutex = new Object();

    private EvaluationCollection<V> mEvaluation;

    private int mPendingTasks = 1;

    private long mPendingValues;

    private S mStack;

    private ForkerOutputs(@NotNull final EvaluationExecutor executor, final S stack) {
      mExecutor = ExecutorPool.withErrorBackPropagation(executor);
      mEvaluationExecutor = executor;
      mStack = stack;
    }

    public int pendingTasks() {
      synchronized (mMutex) {
        return mPendingTasks - 1;
      }
    }

    public long pendingValues() {
      synchronized (mMutex) {
        return mPendingValues;
      }
    }

    public void wait(final long timeout, @NotNull final TimeUnit timeUnit) {
      checkOwner();
      try {
        TimeUnits.sleepAtLeast(timeout, timeUnit);

      } catch (final InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
    }

    public boolean waitTasks(final int maxCount, final long timeout,
        @NotNull final TimeUnit timeUnit) {
      ConstantConditions.notNegative("maxCount", maxCount);
      checkOwner();
      try {
        return TimeUnits.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            return (mPendingTasks - 1) <= maxCount;
          }
        }, timeout, timeUnit);

      } catch (final InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
    }

    public boolean waitValues(final long maxCount, final long timeout,
        @NotNull final TimeUnit timeUnit) {
      ConstantConditions.notNegative("maxCount", maxCount);
      checkOwner();
      try {
        return TimeUnits.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            return mPendingValues <= maxCount;
          }
        }, timeout, timeUnit);

      } catch (final InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
    }

    @NotNull
    public YieldOutputs<V> yieldFailure(@NotNull final Throwable failure) {
      checkSet();
      synchronized (mMutex) {
        ++mPendingTasks;
        ++mPendingValues;
      }

      final EvaluationCollection<V> evaluation = mEvaluation;
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            evaluation.addFailure(failure);

          } finally {
            decrementPendingValues(1);
            decrementPendingTasks();
          }
        }
      });
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldFailures(@Nullable final Iterable<Throwable> failures) {
      checkSet();
      final int size;
      if (failures != null) {
        ConstantConditions.notNullElements("failures", failures);
        size = Iterables.size(failures);

      } else {
        size = 0;
      }

      synchronized (mMutex) {
        ++mPendingTasks;
        mPendingValues += size;
      }

      final EvaluationCollection<V> evaluation = mEvaluation;
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            evaluation.addFailures(failures);

          } finally {
            decrementPendingValues(size);
            decrementPendingTasks();
          }
        }
      });
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldIf(@NotNull final Statement<? extends V> statement) {
      checkSet();
      synchronized (mMutex) {
        ++mPendingTasks;
        ++mPendingValues;
      }

      final Executor executor = mExecutor;
      executor.execute(NO_OP);
      final EvaluationCollection<V> evaluation = mEvaluation;
      statement.forkOn(executor).eventuallyDo(new Observer<V>() {

        public void accept(final V value) throws Exception {
          try {
            evaluation.addValue(value);

          } finally {
            decrementPendingValues(1);
            decrementPendingTasks();
          }
        }
      }).elseDo(new Observer<Throwable>() {

        public void accept(final Throwable failure) throws Exception {
          try {
            evaluation.addFailure(failure);

          } finally {
            decrementPendingValues(1);
            decrementPendingTasks();
          }
        }
      }).consume();
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldLoopIf(
        @NotNull final Statement<? extends Iterable<? extends V>> loop) {
      checkSet();
      synchronized (mMutex) {
        ++mPendingTasks;
        ++mPendingValues;
      }

      final Executor executor = mExecutor;
      executor.execute(NO_OP);
      final EvaluationCollection<V> evaluation = mEvaluation;
      loop.forkOn(executor).eventuallyDo(new Observer<Iterable<? extends V>>() {

        public void accept(final Iterable<? extends V> values) throws Exception {
          try {
            evaluation.addValues(values);

          } finally {
            decrementPendingValues(1);
            decrementPendingTasks();
          }
        }
      }).elseDo(new Observer<Throwable>() {

        public void accept(final Throwable failure) throws Exception {
          try {
            evaluation.addFailure(failure);

          } finally {
            decrementPendingValues(1);
            decrementPendingTasks();
          }
        }
      }).consume();
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldValue(final V value) {
      checkSet();
      synchronized (mMutex) {
        ++mPendingTasks;
        ++mPendingValues;
      }

      final EvaluationCollection<V> evaluation = mEvaluation;
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            evaluation.addValue(value);

          } finally {
            decrementPendingValues(1);
            decrementPendingTasks();
          }
        }
      });
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldValues(@Nullable final Iterable<V> values) {
      checkSet();
      final int size = (values != null) ? Iterables.size(values) : 0;
      synchronized (mMutex) {
        ++mPendingTasks;
        mPendingValues += size;
      }

      final EvaluationCollection<V> evaluation = mEvaluation;
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            evaluation.addValues(values);

          } finally {
            decrementPendingValues(size);
            decrementPendingTasks();
          }
        }
      });
      return this;
    }

    private void checkOwner() {
      final EvaluationExecutor executor = mEvaluationExecutor;
      if (executor.isOwnedThread()) {
        throw new IllegalStateException(
            "cannot wait on executor thread [" + Thread.currentThread() + " " + executor + "]");
      }
    }

    private void checkSet() {
      if (mIsSet.get()) {
        throw new IllegalStateException("evaluation is already set");
      }
    }

    private void decrementPendingTasks() {
      final int pendingTasks;
      synchronized (mMutex) {
        pendingTasks = --mPendingTasks;
        mMutex.notifyAll();
      }

      if (pendingTasks == 0) {
        mEvaluation.set();
      }
    }

    private void decrementPendingValues(final int count) {
      synchronized (mMutex) {
        mPendingValues -= count;
        mMutex.notifyAll();
      }
    }

    private S getStack() {
      return mStack;
    }

    @NotNull
    private ForkerOutputs<S, V> set() {
      mIsSet.set(true);
      mExecutor.execute(new Runnable() {

        public void run() {
          decrementPendingTasks();
        }
      });
      return this;
    }

    private boolean setEvaluations(@NotNull final EvaluationCollection<V> evaluation) {
      if (mEvaluation == null) {
        mEvaluation = evaluation;
        return true;
      }

      return false;
    }

    private ForkerOutputs<S, V> withStack(final S stack) {
      mStack = stack;
      return this;
    }
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(final Executor executor,
        final Yielder<S, V, ? super PendingOutputs<V>> yielder) {
      super(executor, proxy(yielder));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new BackPressureForker<S, V>((Executor) args[0],
            (Yielder<S, V, ? super PendingOutputs<V>>) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}
