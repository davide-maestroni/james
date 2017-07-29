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

import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.log.Logger;
import dm.james.promise.Action;
import dm.james.promise.DeferredPromise;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.RejectionException;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 07/19/2017.
 */
class DefaultDeferredPromise<I, O> implements DeferredPromise<I, O> {

  private final Logger mLogger;

  private final Object mMutex;

  private final Promise<O> mPromise;

  private final StateHolder<I> mState;

  @SuppressWarnings("unchecked")
  DefaultDeferredPromise(@Nullable final PropagationType propagationType, @Nullable final Log log,
      @Nullable final Level level) {
    mLogger = Logger.newLogger(log, level, this);
    mState = new StateHolder<I>();
    mMutex = mState.getMutex();
    mPromise = (DefaultPromise<O>) new DefaultPromise<I>(mState, propagationType, log, level);
  }

  private DefaultDeferredPromise(@NotNull final Promise<O> promise, @Nullable final Log log,
      @Nullable final Level level, @NotNull final StateHolder<I> state) {
    // serialization
    mPromise = promise;
    mLogger = Logger.newLogger(log, level, this);
    mMutex = state.getMutex();
    mState = state;
  }

  private DefaultDeferredPromise(@NotNull final Promise<O> promise, @NotNull final Logger logger,
      @NotNull final StateHolder<I> state) {
    // copy
    mPromise = promise;
    mLogger = logger;
    mMutex = state.getMutex();
    mState = state;
  }

  @NotNull
  public <R> DeferredPromise<I, R> apply(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    return new DefaultDeferredPromise<I, R>(mPromise.apply(mapper), mLogger, mState);
  }

  @NotNull
  public DeferredPromise<I, O> catchAny(@NotNull final Mapper<Throwable, O> mapper) {
    return new DefaultDeferredPromise<I, O>(mPromise.catchAny(mapper), mLogger, mState);
  }

  @NotNull
  public <R> DeferredPromise<I, R> then(
      @Nullable final Handler<O, R, ? super Callback<R>> outputHandler,
      @Nullable final Handler<Throwable, R, ? super Callback<R>> errorHandler) {
    return new DefaultDeferredPromise<I, R>(mPromise.then(outputHandler, errorHandler), mLogger,
        mState);
  }

  @NotNull
  public <R> DeferredPromise<I, R> then(@NotNull final Mapper<O, R> mapper) {
    return new DefaultDeferredPromise<I, R>(mPromise.then(mapper), mLogger, mState);
  }

  @NotNull
  public <R> DeferredPromise<I, R> then(@NotNull final Processor<O, R> processor) {
    return new DefaultDeferredPromise<I, R>(mPromise.then(processor), mLogger, mState);
  }

  @NotNull
  public DeferredPromise<I, O> whenFulfilled(@NotNull final Observer<O> observer) {
    return new DefaultDeferredPromise<I, O>(mPromise.whenFulfilled(observer), mLogger, mState);
  }

  @NotNull
  public DeferredPromise<I, O> whenRejected(@NotNull final Observer<Throwable> observer) {
    return new DefaultDeferredPromise<I, O>(mPromise.whenRejected(observer), mLogger, mState);
  }

  @NotNull
  public DeferredPromise<I, O> whenResolved(@NotNull final Action action) {
    return new DefaultDeferredPromise<I, O>(mPromise.whenResolved(action), mLogger, mState);
  }

  @NotNull
  public DeferredPromise<I, O> rejected(final Throwable reason) {
    reject(reason);
    return this;
  }

  @NotNull
  public DeferredPromise<I, O> resolved(final I input) {
    resolve(input);
    return this;
  }

  public O get() {
    return mPromise.get();
  }

  public O get(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.get(timeout, timeUnit);
  }

  @Nullable
  public RejectionException getError() {
    return mPromise.getError();
  }

  @Nullable
  public RejectionException getError(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.getError(timeout, timeUnit);
  }

  public RejectionException getErrorOr(final RejectionException other, final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return mPromise.getErrorOr(other, timeout, timeUnit);
  }

  public O getOr(final O other, final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.getOr(other, timeout, timeUnit);
  }

  public boolean isBound() {
    return mPromise.isBound();
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

  public void reject(final Throwable reason) {
    mLogger.dbg("Rejecting deferred promise with reason: %s", reason);
    final List<Callback<I>> callbacks;
    synchronized (mMutex) {
      callbacks = mState.reject(reason);
    }

    if (callbacks != null) {
      for (final Callback<I> callback : callbacks) {
        callback.reject(reason);
      }
    }
  }

  public void resolve(final I output) {
    mLogger.dbg("Resolving deferred promise with resolution: %s", output);
    final List<Callback<I>> callbacks;
    synchronized (mMutex) {
      callbacks = mState.resolve(output);
    }

    if (callbacks != null) {
      for (final Callback<I> callback : callbacks) {
        callback.resolve(output);
      }
    }
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
    Object getMutex() {
      return mMutex;
    }

    List<Callback<I>> reject(final Throwable reason) {
      return mState.reject(reason);
    }

    List<Callback<I>> resolve(final I input) {
      return mState.resolve(input);
    }

    private Object writeReplace() throws ObjectStreamException {
      return new StateProxy<I>();
    }

    private static class StateProxy<I> implements Serializable {

      Object readResolve() throws ObjectStreamException {
        return new StateHolder<I>();
      }
    }

    private class StatePending implements Mapper<Callback<I>, Observer<Callback<I>>> {

      public Observer<Callback<I>> apply(final Callback<I> callback) {
        mCallbacks.add(callback);
        return null;
      }

      List<Callback<I>> reject(final Throwable reason) {
        mState = new StateRejected(reason);
        return mCallbacks;
      }

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

      @Override
      public Observer<Callback<I>> apply(final Callback<I> callback) {
        return this;
      }

      @NotNull
      private IllegalStateException exception() {
        return new IllegalStateException("promise already rejected");
      }

      @Override
      List<Callback<I>> resolve(final I input) {
        throw exception();
      }

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

      @Override
      List<Callback<I>> resolve(final I input) {
        throw exception();
      }

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
