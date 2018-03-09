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

package dm.fates.ext.fork;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dm.fates.Eventual;
import dm.fates.eventual.EvaluationCollection;
import dm.fates.eventual.Loop;
import dm.fates.eventual.Loop.YieldOutputs;
import dm.fates.eventual.Loop.Yielder;
import dm.fates.eventual.LoopForker;
import dm.fates.eventual.Observer;
import dm.fates.eventual.RuntimeInterruptedException;
import dm.fates.eventual.Statement;
import dm.fates.executor.EvaluationExecutor;
import dm.fates.executor.ExecutorPool;
import dm.fates.ext.backpressure.PendingOutputs;
import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.fork.BackPressureForker.ForkerOutputs;
import dm.fates.util.ConstantConditions;
import dm.fates.util.SerializableProxy;
import dm.fates.util.TimeUnits;
import dm.fates.util.TimeUnits.Condition;

import static dm.fates.executor.ExecutorPool.NO_OP;

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
    return Eventual.bufferedLoopForker(new BackPressureForker<S, V>(executor, yielder));
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

    private int mPendingCount = 1;

    private S mStack;

    private ForkerOutputs(@NotNull final EvaluationExecutor executor, final S stack) {
      mExecutor = ExecutorPool.withErrorBackPropagation(executor);
      mEvaluationExecutor = executor;
      mStack = stack;
    }

    public int pendingCount() {
      synchronized (mMutex) {
        return mPendingCount - 1;
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

    public boolean wait(final int maxCount, final long timeout, @NotNull final TimeUnit timeUnit) {
      ConstantConditions.notNegative("maxCount", maxCount);
      checkOwner();
      try {
        return TimeUnits.waitUntil(mMutex, new Condition() {

          public boolean isTrue() {
            return (mPendingCount - 1) <= maxCount;
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
        ++mPendingCount;
      }

      final EvaluationCollection<V> evaluation = mEvaluation;
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            evaluation.addFailure(failure);

          } finally {
            decrementPendingCount();
          }
        }
      });
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldFailures(@Nullable final Iterable<Throwable> failures) {
      checkSet();
      if (failures != null) {
        ConstantConditions.notNullElements("failures", failures);
      }

      synchronized (mMutex) {
        ++mPendingCount;
      }

      final EvaluationCollection<V> evaluation = mEvaluation;
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            evaluation.addFailures(failures);

          } finally {
            decrementPendingCount();
          }
        }
      });
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldLoop(
        @NotNull final Statement<? extends Iterable<? extends V>> loop) {
      checkSet();
      synchronized (mMutex) {
        ++mPendingCount;
      }

      final Executor executor = mExecutor;
      executor.execute(NO_OP);
      final EvaluationCollection<V> evaluation = mEvaluation;
      loop.forkOn(executor).eventuallyDo(new Observer<Iterable<? extends V>>() {

        public void accept(final Iterable<? extends V> values) throws Exception {
          try {
            evaluation.addValues(values);

          } finally {
            decrementPendingCount();
          }
        }
      }).elseDo(new Observer<Throwable>() {

        public void accept(final Throwable failure) throws Exception {
          try {
            evaluation.addFailure(failure);

          } finally {
            decrementPendingCount();
          }
        }
      }).evaluated().consume();
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldStatement(@NotNull final Statement<? extends V> statement) {
      checkSet();
      synchronized (mMutex) {
        ++mPendingCount;
      }

      final Executor executor = mExecutor;
      executor.execute(NO_OP);
      final EvaluationCollection<V> evaluation = mEvaluation;
      statement.forkOn(executor).eventuallyDo(new Observer<V>() {

        public void accept(final V value) throws Exception {
          try {
            evaluation.addValue(value);

          } finally {
            decrementPendingCount();
          }
        }
      }).elseDo(new Observer<Throwable>() {

        public void accept(final Throwable failure) throws Exception {
          try {
            evaluation.addFailure(failure);

          } finally {
            decrementPendingCount();
          }
        }
      }).evaluated().consume();
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldValue(final V value) {
      checkSet();
      synchronized (mMutex) {
        ++mPendingCount;
      }

      final EvaluationCollection<V> evaluation = mEvaluation;
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            evaluation.addValue(value);

          } finally {
            decrementPendingCount();
          }
        }
      });
      return this;
    }

    @NotNull
    public YieldOutputs<V> yieldValues(@Nullable final Iterable<V> values) {
      checkSet();
      synchronized (mMutex) {
        ++mPendingCount;
      }

      final EvaluationCollection<V> evaluation = mEvaluation;
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            evaluation.addValues(values);

          } finally {
            decrementPendingCount();
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

    private void decrementPendingCount() {
      final int pendingTasks;
      synchronized (mMutex) {
        pendingTasks = --mPendingCount;
        mMutex.notifyAll();
      }

      if (pendingTasks == 0) {
        mEvaluation.set();
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
          decrementPendingCount();
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
