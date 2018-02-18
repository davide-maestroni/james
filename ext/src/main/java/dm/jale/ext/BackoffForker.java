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

package dm.jale.ext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dm.jale.async.EvaluationCollection;
import dm.jale.async.Loop;
import dm.jale.async.LoopForker;
import dm.jale.async.RuntimeInterruptedException;
import dm.jale.executor.EvaluationExecutor;
import dm.jale.executor.ExecutorPool;
import dm.jale.ext.BackoffForker.ForkerEvaluation;
import dm.jale.ext.backoff.Backoffer;
import dm.jale.ext.backoff.PendingEvaluation;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;
import dm.jale.util.Iterables;
import dm.jale.util.SerializableProxy;
import dm.jale.util.TimeUnits;
import dm.jale.util.TimeUnits.Condition;

/**
 * Created by davide-maestroni on 02/09/2018.
 */
class BackoffForker<S, V> implements LoopForker<ForkerEvaluation<S, V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Backoffer<S, V> mBackoffer;

  private final EvaluationExecutor mExecutor;

  BackoffForker(@NotNull final Executor executor, @NotNull final Backoffer<S, V> backoffer) {
    mExecutor = ExecutorPool.register(executor);
    mBackoffer = ConstantConditions.notNull("backoffer", backoffer);
  }

  public ForkerEvaluation<S, V> done(final ForkerEvaluation<S, V> stack,
      @NotNull final Loop<V> context) throws Exception {
    mBackoffer.done(stack.getStack(), stack);
    return stack.withStack(null);
  }

  public ForkerEvaluation<S, V> evaluation(final ForkerEvaluation<S, V> stack,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final Loop<V> context) throws
      Exception {
    if (!stack.setEvaluations(evaluation)) {
      evaluation.addFailure(new IllegalStateException("the loop cannot be chained")).set();
    }

    return stack;
  }

  public ForkerEvaluation<S, V> failure(final ForkerEvaluation<S, V> stack,
      @NotNull final Throwable failure, @NotNull final Loop<V> context) throws Exception {
    return stack.withStack(mBackoffer.failure(stack.getStack(), failure, stack));
  }

  public ForkerEvaluation<S, V> init(@NotNull final Loop<V> context) throws Exception {
    return new ForkerEvaluation<S, V>(mExecutor, mBackoffer.init());
  }

  public ForkerEvaluation<S, V> value(final ForkerEvaluation<S, V> stack, final V value,
      @NotNull final Loop<V> context) throws Exception {
    return stack.withStack(mBackoffer.value(stack.getStack(), value, stack));
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mExecutor, mBackoffer);
  }

  static class ForkerEvaluation<S, V> implements PendingEvaluation<V> {

    private final EvaluationExecutor mExecutor;

    private final AtomicBoolean mIsSet = new AtomicBoolean(false);

    private final Object mMutex = new Object();

    private EvaluationCollection<V> mEvaluation;

    private int mPendingTasks;

    private long mPendingValues;

    private S mStack;

    private ForkerEvaluation(@NotNull final EvaluationExecutor executor, final S stack) {
      mExecutor = executor;
      mStack = stack;
    }

    @NotNull
    public EvaluationCollection<V> addFailure(@NotNull final Throwable failure) {
      checkSet();
      final EvaluationCollection<V> evaluation = mEvaluation;
      synchronized (mMutex) {
        ++mPendingTasks;
        ++mPendingValues;
      }

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
    public EvaluationCollection<V> addFailures(
        @Nullable final Iterable<? extends Throwable> failures) {
      checkSet();
      if (failures == null) {
        return this;
      }

      ConstantConditions.notNullElements("failures", failures);
      final EvaluationCollection<V> evaluation = mEvaluation;
      final int size = Iterables.size(failures);
      synchronized (mMutex) {
        ++mPendingTasks;
        mPendingValues += size;
      }

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
    public EvaluationCollection<V> addValue(final V value) {
      checkSet();
      final EvaluationCollection<V> evaluation = mEvaluation;
      synchronized (mMutex) {
        ++mPendingTasks;
        ++mPendingValues;
      }

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
    public EvaluationCollection<V> addValues(@Nullable final Iterable<? extends V> values) {
      checkSet();
      if (values == null) {
        return this;
      }

      final EvaluationCollection<V> evaluation = mEvaluation;
      final int size = Iterables.size(values);
      synchronized (mMutex) {
        ++mPendingTasks;
        mPendingValues += size;
      }

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

    public void set() {
      if (mIsSet.getAndSet(true)) {
        checkSet();
      }

      final EvaluationCollection<V> evaluation = mEvaluation;
      synchronized (mMutex) {
        ++mPendingTasks;
      }

      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            evaluation.set();

          } finally {
            decrementPendingTasks();
          }
        }
      });
    }

    public int pendingTasks() {
      synchronized (mMutex) {
        return mPendingTasks;
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
            return mPendingTasks <= maxCount;
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

    private void checkOwner() {
      final EvaluationExecutor executor = mExecutor;
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
      synchronized (mMutex) {
        --mPendingTasks;
        mMutex.notifyAll();
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

    private boolean setEvaluations(@NotNull final EvaluationCollection<V> evaluation) {
      if (mEvaluation == null) {
        mEvaluation = evaluation;
        return true;
      }

      return false;
    }

    private ForkerEvaluation<S, V> withStack(final S stack) {
      mStack = stack;
      return this;
    }
  }

  private static class ForkerProxy<S, V> extends SerializableProxy {

    private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

    private ForkerProxy(final Executor executor, final Backoffer<S, V> backoffer) {
      super(executor, proxy(backoffer));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new BackoffForker<S, V>((Executor) args[0], (Backoffer<S, V>) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}
