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

package dm.james;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import dm.james.BackoffHandler.BackoffData;
import dm.james.executor.ScheduledExecutor;
import dm.james.promise.ChainableIterable.CallbackIterable;
import dm.james.promise.ChainableIterable.StatefulHandler;
import dm.james.promise.RejectionException;
import dm.james.promise.ScheduledData;
import dm.james.util.Backoff;
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;
import dm.james.util.TimeUtils;
import dm.james.util.TimeUtils.Condition;

/**
 * Created by davide-maestroni on 08/03/2017.
 */
class BackoffHandler<O> implements StatefulHandler<O, O, BackoffData<O>>, Serializable {

  private final Backoff<ScheduledData<O>> mBackoff;

  private final ScheduledExecutor mExecutor;

  /**
   * Constructor.
   */
  BackoffHandler(@NotNull final ScheduledExecutor executor,
      @NotNull final Backoff<ScheduledData<O>> backoff) {
    mExecutor = ConstantConditions.notNull("executor", executor);
    mBackoff = ConstantConditions.notNull("backoff", backoff);
  }

  public BackoffData<O> create(@NotNull final CallbackIterable<O> callback) {
    return new BackoffData<O>();
  }

  public BackoffData<O> fulfill(final BackoffData<O> state, final O input,
      @NotNull final CallbackIterable<O> callback) throws Exception {
    state.outputs().add(input);
    applyBackoff(state);
    final List<O> outputs = state.resetOutputs();
    if (!outputs.isEmpty()) {
      mExecutor.execute(new Runnable() {

        public void run() {
          try {
            callback.addAll(outputs);

          } finally {
            state.decrementPending();
          }
        }
      });
    }

    return state;
  }

  public BackoffData<O> reject(final BackoffData<O> state, final Throwable reason,
      @NotNull final CallbackIterable<O> callback) throws Exception {
    applyBackoff(state);
    final List<O> outputs = state.resetOutputs();
    mExecutor.execute(new Runnable() {

      public void run() {
        try {
          callback.addAll(outputs);
          callback.addRejection(reason);

        } finally {
          state.decrementPending();
        }
      }
    });

    return state;
  }

  public void resolve(final BackoffData<O> state,
      @NotNull final CallbackIterable<O> callback) throws Exception {
    applyBackoff(state);
    final List<O> outputs = state.resetOutputs();
    mExecutor.execute(new Runnable() {

      public void run() {
        try {
          callback.addAll(outputs);
          callback.resolve();

        } finally {
          state.decrementPending();
        }
      }
    });
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  private void applyBackoff(@NotNull final BackoffData<O> data) throws Exception {
    final ScheduledExecutor executor = mExecutor;
    if (executor.isExecutionThread()) {
      throw new RejectedExecutionException(
          "cannot wait on executor thread [" + Thread.currentThread() + " " + executor + "]");
    }

    final Object mutex = data.getMutex();
    synchronized (mutex) {
      data.incrementPending();
      final Backoff<ScheduledData<O>> backoff = mBackoff;
      final long delay = backoff.getDelay(data);
      if (delay > 0) {
        TimeUtils.waitUntil(mutex, new Condition() {

          public boolean isTrue() {
            try {
              return (backoff.getDelay(data) < 0);

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

  static class BackoffData<I> implements ScheduledData<I> {

    private final ArrayList<I> mInputs = new ArrayList<I>();

    private final Object mMutex = new Object();

    private int mCount;

    private int mRetainCount;

    private BackoffData() {
    }

    @NotNull
    public List<I> outputs() {
      return mInputs;
    }

    public int pending() {
      synchronized (mMutex) {
        return mCount;
      }
    }

    public void retain(final int count) {
      mRetainCount = count;
    }

    public void retainAll() {
      retain(Integer.MAX_VALUE);
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
    private List<I> resetOutputs() {
      final ArrayList<I> inputs = mInputs;
      final List<I> toRemove = inputs.subList(0, Math.max(0, inputs.size() - mRetainCount));
      final ArrayList<I> outputs = new ArrayList<I>(toRemove);
      toRemove.clear();
      mRetainCount = 0;
      return outputs;
    }
  }

  private static class ExecutorProxy extends SerializableProxy {

    private ExecutorProxy(final ScheduledExecutor executor, final Backoff backoff) {
      super(executor, backoff);
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
