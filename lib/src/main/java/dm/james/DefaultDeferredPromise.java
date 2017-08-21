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
import org.jetbrains.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dm.james.executor.ScheduledExecutor;
import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.log.Logger;
import dm.james.promise.Action;
import dm.james.promise.DeferredPromise;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.RejectionException;
import dm.james.util.ConstantConditions;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 07/19/2017.
 */
class DefaultDeferredPromise<I, O> implements DeferredPromise<I, O> {

  private final Logger mLogger;

  private final Promise<O> mPromise;

  private final StateHolder<I> mState;

  @SuppressWarnings("unchecked")
  DefaultDeferredPromise(@Nullable final Log log, @Nullable final Level level) {
    mLogger = Logger.newLogger(log, level, this);
    mState = new StateHolder<I>();
    mPromise = (DefaultPromise<O>) new DefaultPromise<I>(mState, log, level);
  }

  private DefaultDeferredPromise(@NotNull final Promise<O> promise, @Nullable final Log log,
      @Nullable final Level level, @NotNull final StateHolder<I> state) {
    // serialization
    mPromise = promise;
    mLogger = Logger.newLogger(log, level, this);
    mState = state;
  }

  private DefaultDeferredPromise(@NotNull final Promise<O> promise, @NotNull final Logger logger,
      @NotNull final StateHolder<I> state) {
    // copy
    mPromise = promise;
    mLogger = logger;
    mState = state;
  }

  @NotNull
  public <R> DeferredPromise<I, R> apply(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    return newInstance(mPromise.apply(mapper));
  }

  @NotNull
  public DeferredPromise<I, O> catchAll(@NotNull final Iterable<Class<? extends Throwable>> errors,
      @NotNull final Mapper<Throwable, O> mapper) {
    return newInstance(mPromise.catchAll(errors, mapper));
  }

  @NotNull
  public DeferredPromise<I, O> catchAll(@NotNull final Mapper<Throwable, O> mapper) {
    return newInstance(mPromise.catchAll(mapper));
  }

  @NotNull
  public DeferredPromise<I, O> scheduleAll(@Nullable final ScheduledExecutor fulfillExecutor,
      @Nullable final ScheduledExecutor rejectExecutor) {
    return newInstance(mPromise.scheduleAll(fulfillExecutor, rejectExecutor));
  }

  @NotNull
  public <R> DeferredPromise<I, R> then(@Nullable final Handler<O, ? super Callback<R>> fulfill,
      @Nullable final Handler<Throwable, ? super Callback<R>> reject) {
    return newInstance(mPromise.then(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromise<I, R> then(@NotNull final Mapper<O, R> mapper) {
    return newInstance(mPromise.then(mapper));
  }

  @NotNull
  public <R> DeferredPromise<I, R> thenTry(@Nullable final Handler<O, ? super Callback<R>> fulfill,
      @Nullable final Handler<Throwable, ? super Callback<R>> reject) {
    return newInstance(mPromise.thenTry(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromise<I, R> thenTry(@NotNull final Mapper<O, R> mapper) {
    return newInstance(mPromise.thenTry(mapper));
  }

  @NotNull
  public DeferredPromise<I, O> whenFulfilled(@NotNull final Observer<O> observer) {
    return newInstance(mPromise.whenFulfilled(observer));
  }

  @NotNull
  public DeferredPromise<I, O> whenRejected(@NotNull final Observer<Throwable> observer) {
    return newInstance(mPromise.whenRejected(observer));
  }

  @NotNull
  public DeferredPromise<I, O> whenResolved(@NotNull final Action action) {
    return newInstance(mPromise.whenResolved(action));
  }

  public void defer(@NotNull final Promise<I> promise) {
    mLogger.dbg("Resolving deferred promise with deferred: %s", promise);
    final List<Callback<I>> callbacks = mState.defer(promise);
    promise.then(new Handler<I, Callback<Void>>() {

      public void accept(final I input, final Callback<Void> ignored) {
        for (final Callback<I> callback : callbacks) {
          callback.resolve(input);
        }
      }
    }, new Handler<Throwable, Callback<Void>>() {

      public void accept(final Throwable reason, final Callback<Void> ignored) {
        for (final Callback<I> callback : callbacks) {
          callback.reject(reason);
        }
      }
    });
  }

  public void reject(final Throwable reason) {
    mLogger.dbg("Rejecting deferred promise with reason: %s", reason);
    final List<Callback<I>> callbacks = mState.reject(reason);
    for (final Callback<I> callback : callbacks) {
      callback.reject(reason);
    }
  }

  public void resolve(final I input) {
    mLogger.dbg("Resolving deferred promise with resolution: %s", input);
    final List<Callback<I>> callbacks = mState.resolve(input);
    for (final Callback<I> callback : callbacks) {
      callback.resolve(input);
    }
  }

  public boolean cancel() {
    return mPromise.cancel();
  }

  public O get() {
    return mPromise.get();
  }

  public O get(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.get(timeout, timeUnit);
  }

  public O getOr(final O other, final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.getOr(other, timeout, timeUnit);
  }

  @Nullable
  public RejectionException getReason() {
    return mPromise.getReason();
  }

  @Nullable
  public RejectionException getReason(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.getReason(timeout, timeUnit);
  }

  public RejectionException getReasonOr(final RejectionException other, final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return mPromise.getReasonOr(other, timeout, timeUnit);
  }

  public boolean isChained() {
    return mPromise.isChained();
  }

  public boolean isFulfilled() {
    return mPromise.isFulfilled();
  }

  public boolean isPending() {
    return mPromise.isPending();
  }

  public boolean isRejected() {
    return mPromise.isRejected();
  }

  public boolean isResolved() {
    return mPromise.isResolved();
  }

  public void waitResolved() {
    mPromise.waitResolved();
  }

  public boolean waitResolved(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.waitResolved(timeout, timeUnit);
  }

  @NotNull
  private <R> DeferredPromise<I, R> newInstance(@NotNull final Promise<R> promise) {
    return new DefaultDeferredPromise<I, R>(promise, mLogger, mState);
  }

  private Object writeReplace() throws ObjectStreamException {
    final Logger logger = mLogger;
    return new PromiseProxy<I, O>(mPromise, logger.getLog(), logger.getLogLevel(), mState);
  }

  private static class PromiseProxy<I, O> extends SerializableProxy {

    private PromiseProxy(final Promise<O> promise, final Log log, final Level logLevel,
        final StateHolder<I> state) {
      super(promise, log, logLevel, state);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new DefaultDeferredPromise<I, O>((Promise<O>) args[0], (Log) args[1],
            (Level) args[2], (StateHolder<I>) args[3]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class StateHolder<I> implements Observer<Callback<I>>, Serializable {

    private final ArrayList<Callback<I>> mCallbacks = new ArrayList<Callback<I>>();

    private final Object mMutex = new Object();

    private StatePending mState = new StatePending();

    public void accept(final Callback<I> callback) throws Exception {
      final Observer<Callback<I>> observer;
      synchronized (mMutex) {
        observer = mState.apply(callback);
      }

      if (observer != null) {
        observer.accept(callback);
      }
    }

    @NotNull
    List<Callback<I>> defer(@NotNull final Promise<I> promise) {
      synchronized (mMutex) {
        return mState.defer(promise);
      }
    }

    @NotNull
    List<Callback<I>> reject(final Throwable reason) {
      synchronized (mMutex) {
        return mState.reject(reason);
      }
    }

    @NotNull
    List<Callback<I>> resolve(final I input) {
      synchronized (mMutex) {
        return mState.resolve(input);
      }
    }

    private Object writeReplace() throws ObjectStreamException {
      return new StateProxy<I>();
    }

    private static class StateProxy<I> implements Serializable {

      Object readResolve() throws ObjectStreamException {
        return new StateHolder<I>();
      }
    }

    private class StateDeferred extends StatePending implements Observer<Callback<I>> {

      private final Promise<I> mPromise;

      private StateDeferred(final Promise<I> promise) {
        mPromise = ConstantConditions.notNull("promise", promise);
      }

      @Override
      public Observer<Callback<I>> apply(final Callback<I> callback) {
        return this;
      }

      @NotNull
      private IllegalStateException exception() {
        return new IllegalStateException("promise already resolved");
      }

      @NotNull
      @Override
      List<Callback<I>> defer(@NotNull final Promise<I> promise) {
        throw exception();
      }

      @NotNull
      @Override
      List<Callback<I>> resolve(final I input) {
        throw exception();
      }

      @NotNull
      @Override
      List<Callback<I>> reject(final Throwable reason) {
        throw exception();
      }

      public void accept(final Callback<I> callback) {
        callback.defer(mPromise);
      }
    }

    private class StatePending implements Mapper<Callback<I>, Observer<Callback<I>>> {

      public Observer<Callback<I>> apply(final Callback<I> callback) {
        mCallbacks.add(callback);
        return null;
      }

      @NotNull
      List<Callback<I>> defer(@NotNull final Promise<I> promise) {
        mState = new StateDeferred(promise);
        return mCallbacks;
      }

      @NotNull
      List<Callback<I>> reject(final Throwable reason) {
        mState = new StateRejected(reason);
        return mCallbacks;
      }

      @NotNull
      List<Callback<I>> resolve(final I input) {
        mState = new StateResolved(input);
        return mCallbacks;
      }
    }

    private class StateRejected extends StatePending implements Observer<Callback<I>> {

      private final Throwable mException;

      private StateRejected(@Nullable final Throwable reason) {
        mException = reason;
      }

      @NotNull
      private IllegalStateException exception() {
        return new IllegalStateException("promise already rejected");
      }

      @Override
      public Observer<Callback<I>> apply(final Callback<I> callback) {
        return this;
      }

      @NotNull
      @Override
      List<Callback<I>> defer(@NotNull final Promise<I> promise) {
        throw exception();
      }

      @NotNull
      @Override
      List<Callback<I>> resolve(final I input) {
        throw exception();
      }

      @NotNull
      @Override
      List<Callback<I>> reject(final Throwable reason) {
        throw exception();
      }

      public void accept(final Callback<I> callback) {
        callback.reject(mException);
      }
    }

    private class StateResolved extends StatePending implements Observer<Callback<I>> {

      private final I mInput;

      private StateResolved(@Nullable final I input) {
        mInput = input;
      }

      @NotNull
      private IllegalStateException exception() {
        return new IllegalStateException("promise already resolved");
      }

      @Override
      public Observer<Callback<I>> apply(final Callback<I> callback) {
        return this;
      }

      @NotNull
      @Override
      List<Callback<I>> defer(@NotNull final Promise<I> promise) {
        throw exception();
      }

      @NotNull
      @Override
      List<Callback<I>> resolve(final I input) {
        throw exception();
      }

      @NotNull
      @Override
      List<Callback<I>> reject(final Throwable reason) {
        throw exception();
      }

      public void accept(final Callback<I> callback) {
        callback.resolve(mInput);
      }
    }
  }
}
