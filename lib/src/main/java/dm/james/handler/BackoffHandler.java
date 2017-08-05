/*
 * Copyright 2017 Davide Maestroni
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

package dm.james.handler;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import dm.james.executor.ScheduledExecutor;
import dm.james.handler.BackoffHandler.BackoffInputs;
import dm.james.handler.Handlers.ScheduledInputs;
import dm.james.promise.PromiseIterable.CallbackIterable;
import dm.james.promise.PromiseIterable.StatefulHandler;
import dm.james.promise.RejectionException;
import dm.james.util.Backoff;
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;
import dm.james.util.TimeUtils;
import dm.james.util.TimeUtils.Condition;

/**
 * Created by davide-maestroni on 08/03/2017.
 */
class BackoffHandler<I> implements StatefulHandler<I, I, BackoffInputs<I>>, Serializable {

  private final Backoff<ScheduledInputs<I>> mBackoff;

  private final ScheduledExecutor mExecutor;

  /**
   * Constructor.
   */
  BackoffHandler(@NotNull final ScheduledExecutor executor,
      @NotNull final Backoff<ScheduledInputs<I>> backoff) {
    mExecutor = ConstantConditions.notNull("executor", executor);
    mBackoff = ConstantConditions.notNull("backoff", backoff);
  }

  public BackoffInputs<I> create(@NotNull final CallbackIterable<I> callback) {
    return new BackoffInputs<I>();
  }

  public BackoffInputs<I> fulfill(final BackoffInputs<I> state, final I input,
      @NotNull final CallbackIterable<I> callback) throws Exception {
    state.inputs().add(input);
    applyBackoff(state);
    if (!state.resetRetain()) {
      final List<I> inputs = state.resetInputs();
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            callback.addAll(inputs);

          } finally {
            state.decrementPending();
          }
        }
      });
    }

    return state;
  }

  public BackoffInputs<I> reject(final BackoffInputs<I> state, final Throwable reason,
      @NotNull final CallbackIterable<I> callback) throws Exception {
    final List<I> inputs = state.resetInputs();
    mExecutor.execute(new Runnable() {

      public void run() {
        try {
          callback.addAll(inputs);
          callback.addRejection(reason);

        } finally {
          state.decrementPending();
        }
      }
    });

    return state;
  }

  public void resolve(final BackoffInputs<I> state,
      @NotNull final CallbackIterable<I> callback) throws Exception {
    applyBackoff(state);
    final List<I> inputs = state.resetInputs();
    mExecutor.execute(new Runnable() {

      public void run() {
        try {
          callback.addAll(inputs);
          callback.resolve();

        } finally {
          state.decrementPending();
        }
      }
    });
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  private void applyBackoff(@NotNull final BackoffInputs<I> inputs) throws Exception {
    final ScheduledExecutor executor = mExecutor;
    if (executor.isExecutionThread()) {
      throw new RejectedExecutionException(
          "cannot wait on executor thread [" + Thread.currentThread() + " " + executor + "]");
    }

    final Object mutex = inputs.getMutex();
    synchronized (mutex) {
      inputs.incrementPending();
      final long delay = mBackoff.getDelay(inputs);
      if (delay > 0) {
        TimeUtils.waitUntil(mutex, new Condition() {

          public boolean isTrue() {
            try {
              return (mBackoff.getDelay(inputs) <= 0);

            } catch (final Exception e) {
              throw RejectionException.wrapIfNot(RuntimeException.class, e);
            }
          }
        }, delay, TimeUnit.MILLISECONDS);
      }
    }
  }

  private Object writeReplace() throws ObjectStreamException {
    return new ExecutorProxy(mExecutor, mBackoff);
  }

  static class BackoffInputs<I> implements ScheduledInputs<I> {

    private final ArrayList<I> mInputs = new ArrayList<I>();

    private final Object mMutex = new Object();

    private int mCount;

    private boolean mIsRetain;

    private BackoffInputs() {
    }

    @NotNull
    public List<I> inputs() {
      return mInputs;
    }

    public int pending() {
      synchronized (mMutex) {
        return mCount;
      }
    }

    public void retain() {
      mIsRetain = true;
    }

    private void decrementPending() {
      synchronized (mMutex) {
        ++mCount;
        mMutex.notifyAll();
      }
    }

    @NotNull
    private Object getMutex() {
      return mMutex;
    }

    private void incrementPending() {
      synchronized (mMutex) {
        ++mCount;
      }
    }

    @NotNull
    private List<I> resetInputs() {
      final ArrayList<I> inputs = new ArrayList<I>(mInputs);
      mInputs.clear();
      return inputs;
    }

    private boolean resetRetain() {
      final boolean isRetain = mIsRetain;
      mIsRetain = false;
      return isRetain;
    }
  }

  private static class ExecutorProxy extends SerializableProxy {

    private ExecutorProxy(final ScheduledExecutor wrapped, final Backoff backoff) {
      super(wrapped, backoff);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new BackoffHandler((ScheduledExecutor) args[0], (Backoff) args[1]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }
}
