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

import dm.jail.async.AsyncEvaluations;
import dm.jail.async.AsyncLoop;
import dm.jail.async.AsyncStatement.Forker;
import dm.jail.async.RuntimeInterruptedException;
import dm.jail.async.SimpleState;
import dm.jail.config.BuildConfig;
import dm.jail.executor.ExecutorPool;
import dm.jail.executor.OwnerExecutor;
import dm.jail.util.ConstantConditions;
import dm.jail.util.Iterables;
import dm.jail.util.SerializableProxy;
import dm.jail.util.TimeUnits;
import dm.jail.util.TimeUnits.Condition;
import dm.jale.ext.fork.BackoffForker.ForkerEvaluations;

/**
 * Created by davide-maestroni on 02/09/2018.
 */
class BackoffForker<S, V>
    implements Forker<ForkerEvaluations, AsyncLoop<V>, V, AsyncEvaluations<V>>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final Backoffer<S, V> mBackoffer;

  private final OwnerExecutor mExecutor;

  BackoffForker(@NotNull final Executor executor, @NotNull final Backoffer<S, V> backoffer) {
    mExecutor = ExecutorPool.register(executor);
    mBackoffer = ConstantConditions.notNull("backoffer", backoffer);
  }

  public ForkerEvaluations done(final ForkerEvaluations stack,
      @NotNull final AsyncLoop<V> loop) throws Exception {
    mBackoffer.done(stack.getStack(), stack);
    return stack.withStack(null);
  }

  public ForkerEvaluations evaluation(final ForkerEvaluations stack,
      @NotNull final AsyncEvaluations<V> evaluations, @NotNull final AsyncLoop<V> loop) throws
      Exception {
    if (!stack.setEvaluations(evaluations)) {
      evaluations.addFailure(new IllegalStateException("the loop cannot be chained")).set();
    }

    return stack;
  }

  public ForkerEvaluations failure(final ForkerEvaluations stack, @NotNull final Throwable failure,
      @NotNull final AsyncLoop<V> loop) throws Exception {
    return stack.withStack(mBackoffer.failure(stack.getStack(), failure, stack));
  }

  public ForkerEvaluations init(@NotNull final AsyncLoop<V> loop) throws Exception {
    return new ForkerEvaluations(mBackoffer.init());
  }

  public ForkerEvaluations value(final ForkerEvaluations stack, final V value,
      @NotNull final AsyncLoop<V> loop) throws Exception {
    return stack.withStack(mBackoffer.value(stack.getStack(), value, stack));
  }

  @NotNull
  private Object writeReplace() throws ObjectStreamException {
    return new ForkerProxy<S, V>(mExecutor, mBackoffer);
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

  class ForkerEvaluations implements PendingEvaluations<V> {

    private final AtomicBoolean mIsSet = new AtomicBoolean(false);

    private final Object mMutex = new Object();

    private final ArrayList<SimpleState<V>> mStates = new ArrayList<SimpleState<V>>();

    private AsyncEvaluations<V> mEvaluations;

    private int mPendingTasks;

    private long mPendingValues;

    private S mStack;

    private ForkerEvaluations(final S stack) {
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

    private ForkerEvaluations withStack(final S stack) {
      mStack = stack;
      return this;
    }
  }
}
