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

package dm.jale.ext.fork;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dm.jale.async.AsyncEvaluations;
import dm.jale.async.AsyncLoop;
import dm.jale.async.AsyncStatement.Forker;
import dm.jale.async.RuntimeInterruptedException;
import dm.jale.async.SimpleState;
import dm.jale.executor.ExecutorPool;
import dm.jale.executor.OwnerExecutor;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.fork.BackoffForker.ForkerEvaluations;
import dm.jale.util.ConstantConditions;
import dm.jale.util.Iterables;
import dm.jale.util.SerializableProxy;
import dm.jale.util.TimeUnits;
import dm.jale.util.TimeUnits.Condition;

/**
 * Created by davide-maestroni on 02/09/2018.
 */
class BackoffForker<S, V>
    implements Forker<ForkerEvaluations<S, V>, V, AsyncEvaluations<V>, AsyncLoop<V>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Backoffer<S, V> mBackoffer;

  private final OwnerExecutor mExecutor;

  BackoffForker(@NotNull final Executor executor, @NotNull final Backoffer<S, V> backoffer) {
    mExecutor = ExecutorPool.register(executor);
    mBackoffer = ConstantConditions.notNull("backoffer", backoffer);
  }

  public ForkerEvaluations<S, V> done(final ForkerEvaluations<S, V> stack,
      @NotNull final AsyncLoop<V> async) throws Exception {
    mBackoffer.done(stack.getStack(), stack);
    return stack.withStack(null);
  }

  public ForkerEvaluations<S, V> evaluation(final ForkerEvaluations<S, V> stack,
      @NotNull final AsyncEvaluations<V> evaluations, @NotNull final AsyncLoop<V> async) throws
      Exception {
    if (!stack.setEvaluations(evaluations)) {
      evaluations.addFailure(new IllegalStateException("the loop cannot be chained")).set();
    }

    return stack;
  }

  public ForkerEvaluations<S, V> failure(final ForkerEvaluations<S, V> stack,
      @NotNull final Throwable failure, @NotNull final AsyncLoop<V> async) throws Exception {
    return stack.withStack(mBackoffer.failure(stack.getStack(), failure, stack));
  }

  public ForkerEvaluations<S, V> init(@NotNull final AsyncLoop<V> async) throws Exception {
    return new ForkerEvaluations<S, V>(mExecutor, mBackoffer.init());
  }

  public ForkerEvaluations<S, V> value(final ForkerEvaluations<S, V> stack, final V value,
      @NotNull final AsyncLoop<V> async) throws Exception {
    return stack.withStack(mBackoffer.value(stack.getStack(), value, stack));
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mExecutor, mBackoffer);
  }

  static class ForkerEvaluations<S, V> implements PendingEvaluations<V> {

    private final OwnerExecutor mExecutor;

    private final AtomicBoolean mIsSet = new AtomicBoolean(false);

    private final Object mMutex = new Object();

    private final ArrayList<SimpleState<V>> mStates = new ArrayList<SimpleState<V>>();

    private AsyncEvaluations<V> mEvaluations;

    private int mPendingTasks;

    private long mPendingValues;

    private S mStack;

    private ForkerEvaluations(@NotNull final OwnerExecutor executor, final S stack) {
      mExecutor = executor;
      mStack = stack;
    }

    @NotNull
    public AsyncEvaluations<V> addFailure(@NotNull final Throwable failure) {
      checkSet();
      final AsyncEvaluations<V> evaluations = mEvaluations;
      if (evaluations == null) {
        mStates.add(SimpleState.<V>ofFailure(failure));
        synchronized (mMutex) {
          mPendingTasks = 1;
          ++mPendingValues;
        }

      } else {
        synchronized (mMutex) {
          ++mPendingTasks;
          ++mPendingValues;
        }

        mExecutor.execute(new Runnable() {

          public void run() {
            try {
              evaluations.addFailure(failure);

            } finally {
              decrementPendingValues(1);
              decrementPendingTasks();
            }
          }
        });
      }

      return this;
    }

    @NotNull
    public AsyncEvaluations<V> addFailures(@Nullable final Iterable<? extends Throwable> failures) {
      checkSet();
      if (failures != null) {
        if (Iterables.contains(failures, null)) {
          throw new NullPointerException("failures cannot contain null objects");
        }

        final AsyncEvaluations<V> evaluations = mEvaluations;
        if (evaluations == null) {
          int count = 0;
          for (final Throwable failure : failures) {
            mStates.add(SimpleState.<V>ofFailure(failure));
            ++count;
          }

          synchronized (mMutex) {
            mPendingTasks = 1;
            mPendingValues += count;
          }

        } else {
          final int size = Iterables.size(failures);
          synchronized (mMutex) {
            ++mPendingTasks;
            mPendingValues += size;
          }

          mExecutor.execute(new Runnable() {

            public void run() {
              try {
                evaluations.addFailures(failures);

              } finally {
                decrementPendingValues(size);
                decrementPendingTasks();
              }
            }
          });
        }
      }

      return this;
    }

    @NotNull
    public AsyncEvaluations<V> addValue(final V value) {
      checkSet();
      final AsyncEvaluations<V> evaluations = mEvaluations;
      if (evaluations == null) {
        mStates.add(SimpleState.ofValue(value));
        synchronized (mMutex) {
          mPendingTasks = 1;
          ++mPendingValues;
        }

      } else {
        synchronized (mMutex) {
          ++mPendingTasks;
          ++mPendingValues;
        }

        mExecutor.execute(new Runnable() {

          public void run() {
            try {
              evaluations.addValue(value);

            } finally {
              decrementPendingValues(1);
              decrementPendingTasks();
            }
          }
        });
      }

      return this;
    }

    @NotNull
    public AsyncEvaluations<V> addValues(@Nullable final Iterable<? extends V> values) {
      checkSet();
      if (values != null) {
        final AsyncEvaluations<V> evaluations = mEvaluations;
        if (evaluations == null) {
          int count = 0;
          for (final V value : values) {
            mStates.add(SimpleState.ofValue(value));
            ++count;
          }

          synchronized (mMutex) {
            mPendingTasks = 1;
            mPendingValues += count;
          }

        } else {
          final int size = Iterables.size(values);
          synchronized (mMutex) {
            ++mPendingTasks;
            mPendingValues += size;
          }

          mExecutor.execute(new Runnable() {

            public void run() {
              try {
                evaluations.addValues(values);

              } finally {
                decrementPendingValues(size);
                decrementPendingTasks();
              }
            }
          });
        }
      }

      return this;
    }

    public void set() {
      if (mIsSet.getAndSet(true)) {
        checkSet();
      }

      final AsyncEvaluations<V> evaluations = mEvaluations;
      if (evaluations == null) {
        synchronized (mMutex) {
          mPendingTasks = 1;
        }

      } else {
        synchronized (mMutex) {
          ++mPendingTasks;
        }

        mExecutor.execute(new Runnable() {

          public void run() {
            try {
              evaluations.set();

            } finally {
              decrementPendingTasks();
            }
          }
        });
      }
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
      final OwnerExecutor executor = mExecutor;
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

    private boolean setEvaluations(@NotNull final AsyncEvaluations<V> evaluations) {
      if (mEvaluations == null) {
        mEvaluations = evaluations;
        final ArrayList<SimpleState<V>> states = mStates;
        for (final SimpleState<V> state : states) {
          state.addTo(evaluations);
        }

        states.clear();
        return true;
      }

      return false;
    }

    private ForkerEvaluations<S, V> withStack(final S stack) {
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
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new BackoffForker<S, V>((Executor) args[0], (Backoffer<S, V>) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}
