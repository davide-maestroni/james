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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dm.james.executor.ScheduledExecutor;
import dm.james.executor.ScheduledExecutors;
import dm.james.executor.SyncExecutor;
import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.log.Logger;
import dm.james.promise.Action;
import dm.james.promise.DeferredPromise;
import dm.james.promise.DeferredPromiseIterable;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.PromiseIterable;
import dm.james.promise.RejectionException;
import dm.james.promise.ResolvableIterable;
import dm.james.util.ConstantConditions;
import dm.james.util.DoubleQueue;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 08/01/2017.
 */
class DefaultDeferredPromiseIterable<I, O> implements DeferredPromiseIterable<I, O> {

  private final Logger mLogger;

  private final PromiseIterable<O> mPromise;

  private final StateHolder<I> mState;

  @SuppressWarnings("unchecked")
  DefaultDeferredPromiseIterable(@Nullable final PropagationType propagationType,
      @Nullable final Log log, @Nullable final Level level) {
    mLogger = Logger.newLogger(log, level, this);
    mState = new StateHolder<I>(log, level);
    mPromise =
        (DefaultPromiseIterable<O>) new DefaultPromiseIterable<I>(mState, propagationType, log,
            level);
  }

  private DefaultDeferredPromiseIterable(@NotNull final PromiseIterable<O> promise,
      @Nullable final Log log, @Nullable final Level level, @NotNull final StateHolder<I> state) {
    // serialization
    mPromise = promise;
    mLogger = Logger.newLogger(log, level, this);
    mState = state;
  }

  private DefaultDeferredPromiseIterable(@NotNull final PromiseIterable<O> promise,
      @NotNull final Logger logger, @NotNull final StateHolder<I> state) {
    // copy
    mPromise = promise;
    mLogger = logger;
    mState = state;
  }

  public void add(final I input) {
    mState.add(input);
  }

  @NotNull
  public DeferredPromiseIterable<I, O> added(final I input) {
    add(input);
    return this;
  }

  @NotNull
  public DeferredPromiseIterable<I, O> addedAll(final Iterable<I> input) {
    addAll(input);
    return this;
  }

  @NotNull
  public DeferredPromiseIterable<I, O> addedRejection(final Throwable reason) {
    addRejection(reason);
    return this;
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> all(
      @Nullable final Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return newInstance(mPromise.all(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> all(
      @NotNull final Mapper<Iterable<O>, Iterable<R>> mapper) {
    return newInstance(mPromise.all(mapper));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> allSorted(
      @Nullable final Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return newInstance(mPromise.allSorted(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> allTry(
      @Nullable final Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return newInstance(mPromise.allTry(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> allTry(
      @NotNull final Mapper<Iterable<O>, Iterable<R>> mapper) {
    return newInstance(mPromise.allTry(mapper));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> allTrySorted(
      @Nullable final Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return newInstance(mPromise.allTrySorted(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> any(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return newInstance(mPromise.any(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> any(@NotNull final Mapper<O, R> mapper) {
    return newInstance(mPromise.any(mapper));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> anySorted(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return newInstance(mPromise.anySorted(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> anyTry(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return newInstance(mPromise.anyTry(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> anyTry(@NotNull final Mapper<O, R> mapper) {
    return newInstance(mPromise.anyTry(mapper));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> anyTrySorted(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return newInstance(mPromise.anyTrySorted(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> applyAll(
      @NotNull final Mapper<PromiseIterable<O>, PromiseIterable<R>> mapper) {
    return newInstance(mPromise.applyAll(mapper));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> applyAny(
      @NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    return newInstance(mPromise.applyAny(mapper));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> applyEach(
      @NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    return newInstance(mPromise.applyEach(mapper));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> applyEachSorted(
      @NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    return newInstance(mPromise.applyEachSorted(mapper));
  }

  @NotNull
  public DeferredPromiseIterable<I, O> catchAny(
      @NotNull final Mapper<Throwable, Iterable<O>> mapper) {
    return newInstance(mPromise.catchAny(mapper));
  }

  @NotNull
  public DeferredPromiseIterable<I, O> whenFulfilled(
      @NotNull final Observer<Iterable<O>> observer) {
    return newInstance(mPromise.whenFulfilled(observer));
  }

  @NotNull
  public DeferredPromiseIterable<I, O> whenRejected(@NotNull final Observer<Throwable> observer) {
    return newInstance(mPromise.whenRejected(observer));
  }

  @NotNull
  public DeferredPromiseIterable<I, O> whenResolved(@NotNull final Action action) {
    return newInstance(mPromise.whenResolved(action));
  }

  @NotNull
  public DeferredPromiseIterable<I, O> catchEach(final int minBatchSize,
      @NotNull final Mapper<Throwable, O> mapper) {
    return newInstance(mPromise.catchEach(minBatchSize, mapper));
  }

  @NotNull
  public DeferredPromiseIterable<I, O> catchEach(@NotNull final Mapper<Throwable, O> mapper) {
    return newInstance(mPromise.catchEach(mapper));
  }

  @NotNull
  public DeferredPromiseIterable<I, O> catchEach(@NotNull final Mapper<Throwable, O> mapper,
      final int maxBatchSize) {
    return newInstance(mPromise.catchEach(mapper, maxBatchSize));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> each(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return newInstance(mPromise.each(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> each(final int minBatchSize,
      @NotNull final Mapper<O, R> mapper) {
    return newInstance(mPromise.each(minBatchSize, mapper));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> each(@NotNull final Mapper<O, R> mapper) {
    return newInstance(mPromise.each(mapper));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> each(@NotNull final Mapper<O, R> mapper,
      final int maxBatchSize) {
    return newInstance(mPromise.each(mapper, maxBatchSize));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> eachSorted(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return newInstance(mPromise.eachSorted(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> eachTry(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return newInstance(mPromise.eachTry(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> eachTry(final int minBatchSize,
      @NotNull final Mapper<O, R> mapper) {
    return newInstance(mPromise.eachTry(minBatchSize, mapper));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> eachTry(@NotNull final Mapper<O, R> mapper) {
    return newInstance(mPromise.eachTry(mapper));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> eachTry(@NotNull final Mapper<O, R> mapper,
      final int maxBatchSize) {
    return newInstance(mPromise.eachTry(mapper, maxBatchSize));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> eachTrySorted(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject) {
    return newInstance(mPromise.eachTrySorted(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> then(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject,
      @Nullable final Observer<? super CallbackIterable<R>> resolve) {
    return newInstance(mPromise.then(fulfill, reject, resolve));
  }

  @NotNull
  public <R, S> DeferredPromiseIterable<I, R> then(
      @NotNull final StatefulHandler<O, R, S> handler) {
    return newInstance(mPromise.then(handler));
  }

  @NotNull
  public <R> DeferredPromiseIterable<I, R> thenSorted(
      @Nullable final Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable final Handler<Throwable, ? super CallbackIterable<R>> reject,
      @Nullable final Observer<? super CallbackIterable<R>> resolve) {
    return newInstance(mPromise.thenSorted(fulfill, reject, resolve));
  }

  @NotNull
  public <R, S> DeferredPromiseIterable<I, R> thenSorted(
      @NotNull final StatefulHandler<O, R, S> handler) {
    return newInstance(mPromise.thenSorted(handler));
  }

  @NotNull
  public DeferredPromiseIterable<I, O> whenFulfilledAny(@NotNull final Observer<O> observer) {
    return newInstance(mPromise.whenFulfilledAny(observer));
  }

  @NotNull
  public DeferredPromiseIterable<I, O> whenFulfilledEach(@NotNull final Observer<O> observer) {
    return newInstance(mPromise.whenFulfilledEach(observer));
  }

  @NotNull
  public DeferredPromiseIterable<I, O> whenRejectedEach(
      @NotNull final Observer<Throwable> observer) {
    return newInstance(mPromise.whenRejectedEach(observer));
  }

  @NotNull
  public DeferredPromiseIterable<I, O> rejected(final Throwable reason) {
    reject(reason);
    return this;
  }

  @NotNull
  public DeferredPromiseIterable<I, O> resolved(final Iterable<I> inputs) {
    resolve(inputs);
    return this;
  }

  @NotNull
  public <R> DeferredPromise<Iterable<I>, R> then(
      @Nullable final Handler<Iterable<O>, ? super Callback<R>> fulfill,
      @Nullable final Handler<Throwable, ? super Callback<R>> reject) {
    return new WrappingDeferredPromise<Iterable<I>, R>(this, mPromise.then(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromise<Iterable<I>, R> then(@NotNull final Mapper<Iterable<O>, R> mapper) {
    return new WrappingDeferredPromise<Iterable<I>, R>(this, mPromise.then(mapper));
  }

  @NotNull
  public <R> DeferredPromise<Iterable<I>, R> thenTry(
      @Nullable final Handler<Iterable<O>, ? super Callback<R>> fulfill,
      @Nullable final Handler<Throwable, ? super Callback<R>> reject) {
    return new WrappingDeferredPromise<Iterable<I>, R>(this, mPromise.thenTry(fulfill, reject));
  }

  @NotNull
  public <R> DeferredPromise<Iterable<I>, R> thenTry(@NotNull final Mapper<Iterable<O>, R> mapper) {
    return new WrappingDeferredPromise<Iterable<I>, R>(this, mPromise.thenTry(mapper));
  }

  @NotNull
  public <R> DeferredPromise<Iterable<I>, R> apply(
      @NotNull final Mapper<Promise<Iterable<O>>, Promise<R>> mapper) {
    return new WrappingDeferredPromise<Iterable<I>, R>(this, mPromise.apply(mapper));
  }

  public Iterable<O> get() {
    return mPromise.get();
  }

  public Iterable<O> get(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.get(timeout, timeUnit);
  }

  public Iterable<O> getOr(final Iterable<O> other, final long timeout,
      @NotNull final TimeUnit timeUnit) {
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

  @NotNull
  public List<O> get(final int maxSize) {
    return mPromise.get(maxSize);
  }

  @NotNull
  public List<O> get(final int maxSize, final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.get(maxSize, timeout, timeUnit);
  }

  @NotNull
  public List<O> getAll() {
    return mPromise.getAll();
  }

  @NotNull
  public List<O> getAll(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.getAll(timeout, timeUnit);
  }

  public O getAny() {
    return mPromise.getAny();
  }

  public O getAny(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.getAny(timeout, timeUnit);
  }

  public O getAnyOr(final O other, final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.getAnyOr(other, timeout, timeUnit);
  }

  @NotNull
  public Iterator<O> iterator(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.iterator(timeout, timeUnit);
  }

  public O remove() {
    return mPromise.remove();
  }

  @NotNull
  public List<O> remove(final int maxSize) {
    return mPromise.remove(maxSize);
  }

  @NotNull
  public List<O> remove(final int maxSize, final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.remove(maxSize, timeout, timeUnit);
  }

  public O remove(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.remove(timeout, timeUnit);
  }

  @NotNull
  public List<O> removeAll() {
    return mPromise.removeAll();
  }

  @NotNull
  public List<O> removeAll(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.removeAll(timeout, timeUnit);
  }

  public O removeOr(final O other, final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.removeOr(other, timeout, timeUnit);
  }

  public void waitComplete() {
    mPromise.waitComplete();
  }

  public boolean waitComplete(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.waitComplete(timeout, timeUnit);
  }

  public Iterator<O> iterator() {
    return mPromise.iterator();
  }

  public void reject(final Throwable reason) {
    mState.reject(reason);
  }

  @NotNull
  private <R> DeferredPromiseIterable<I, R> newInstance(@NotNull final PromiseIterable<R> promise) {
    return new DefaultDeferredPromiseIterable<I, R>(promise, mLogger, mState);
  }

  private Object writeReplace() throws ObjectStreamException {
    final Logger logger = mLogger;
    return new PromiseProxy<I, O>(mPromise, logger.getLog(), logger.getLogLevel(), mState);
  }

  private static class PromiseProxy<I, O> extends SerializableProxy {

    private PromiseProxy(final PromiseIterable<O> promise, final Log log, final Level logLevel,
        final StateHolder<I> state) {
      super(promise, log, logLevel, state);
    }

    @SuppressWarnings("unchecked")
    Object readResolve() throws ObjectStreamException {
      try {
        final Object[] args = deserializeArgs();
        return new DefaultDeferredPromiseIterable<I, O>((PromiseIterable<O>) args[0], (Log) args[1],
            (Level) args[2], (StateHolder<I>) args[3]);

      } catch (final Throwable t) {
        throw new InvalidObjectException(t.getMessage());
      }
    }
  }

  private static class StateExecutor extends SyncExecutor {

    private final DoubleQueue<Runnable> mCommands = new DoubleQueue<Runnable>();

    private final Object mMutex;

    private StateExecutor(@NotNull final Object mutex) {
      mMutex = mutex;
    }

    public void execute(@NotNull final Runnable command) {
      synchronized (mMutex) {
        mCommands.add(command);
      }
    }

    public void execute(@NotNull final Runnable command, final long delay,
        @NotNull final TimeUnit timeUnit) {
      ConstantConditions.unsupported();
    }

    void run() {
      Runnable command = getCommand();
      while (command != null) {
        command.run();
        command = getCommand();
      }
    }

    @Nullable
    private Runnable getCommand() {
      synchronized (mMutex) {
        final DoubleQueue<Runnable> commands = mCommands;
        if (!commands.isEmpty()) {
          return commands.removeFirst();
        }
      }

      return null;
    }
  }

  private static class StateHolder<I>
      implements Observer<CallbackIterable<I>>, ResolvableIterable<I>, Serializable {

    private final ArrayList<CallbackIterable<I>> mCallbacks = new ArrayList<CallbackIterable<I>>();

    private final ScheduledExecutor mExecutor;

    private final Logger mLogger;

    private final Object mMutex = new Object();

    private final StateExecutor mStateExecutor;

    private StatePending mState = new StatePending();

    private StateHolder(@Nullable final Log log, @Nullable final Level level) {
      mStateExecutor = new StateExecutor(mMutex);
      mExecutor = ScheduledExecutors.withThrottling(mStateExecutor, 1);
      mLogger = Logger.newLogger(log, level, this);
    }

    public void accept(final CallbackIterable<I> callback) throws Exception {
      synchronized (mMutex) {
        mState.accept(callback);
      }

      mStateExecutor.run();
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new StateProxy<I>(logger.getLog(), logger.getLogLevel());
    }

    private static class StateProxy<I> extends SerializableProxy {

      private StateProxy(final Log log, final Level level) {
        super(log, level);
      }

      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new StateHolder<I>((Log) args[0], (Level) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    private class StatePending implements Observer<CallbackIterable<I>>, ResolvableIterable<I> {

      private final DoubleQueue<I> mOutputs = new DoubleQueue<I>();

      public void accept(final CallbackIterable<I> callback) {
        mExecutor.execute(new Runnable() {

          public void run() {
            callback.addAll(new ArrayList<I>(mOutputs));
            mCallbacks.add(callback);
          }
        });
      }

      public void add(final I input) {
        mExecutor.execute(new Runnable() {

          public void run() {
            mOutputs.add(input);
            for (final CallbackIterable<I> callback : mCallbacks) {
              callback.add(input);
            }
          }
        });
      }

      public void addAll(@Nullable final Iterable<I> inputs) {
        if (inputs == null) {
          return;
        }

        mExecutor.execute(new Runnable() {

          public void run() {
            @SuppressWarnings("UnnecessaryLocalVariable") final DoubleQueue<I> outputs = mOutputs;
            for (final I input : inputs) {
              outputs.add(input);
            }

            for (final CallbackIterable<I> callback : mCallbacks) {
              callback.addAll(inputs);
            }
          }
        });
      }

      public void addRejection(final Throwable reason) {
        mExecutor.execute(new Runnable() {

          public void run() {
            for (final CallbackIterable<I> callback : mCallbacks) {
              callback.addRejection(reason);
            }
          }
        });
      }

      public void reject(final Throwable reason) {
        mState = new StateRejected(mOutputs, reason);
        mExecutor.execute(new Runnable() {

          public void run() {
            for (final CallbackIterable<I> callback : mCallbacks) {
              callback.reject(reason);
            }
          }
        });
      }

      public void resolve(final Iterable<I> inputs) {
        final ArrayList<I> outputs = new ArrayList<I>(mOutputs);
        for (final I input : inputs) {
          outputs.add(input);
        }

        mState = new StateResolved(outputs);
        mExecutor.execute(new Runnable() {

          public void run() {
            for (final CallbackIterable<I> callback : mCallbacks) {
              callback.addAll(inputs);
              callback.resolve();
            }
          }
        });
      }

      public void resolve() {
        mState = new StateResolved(mOutputs);
        mExecutor.execute(new Runnable() {

          public void run() {
            for (final CallbackIterable<I> callback : mCallbacks) {
              callback.resolve();
            }
          }
        });
      }
    }

    private class StateRejected extends StatePending {

      private final Throwable mException;

      private final Collection<I> mOutputs;

      private StateRejected(@NotNull final Collection<I> outputs,
          @Nullable final Throwable reason) {
        mOutputs = outputs;
        mException = reason;
      }

      public void accept(final CallbackIterable<I> callback) {
        mExecutor.execute(new Runnable() {

          public void run() {
            callback.addAll(mOutputs);
            callback.reject(mException);
          }
        });
      }

      @NotNull
      private IllegalStateException exception() {
        return new IllegalStateException("promise already rejected");
      }

      @Override
      public void add(final I input) {
        throw exception();
      }

      @Override
      public void addAll(@Nullable final Iterable<I> inputs) {
        throw exception();
      }

      @Override
      public void addRejection(final Throwable reason) {
        mLogger.wrn(reason, "Suppressed rejection");
      }

      @Override
      public void reject(final Throwable reason) {
        mLogger.wrn(reason, "Suppressed rejection");
      }

      @Override
      public void resolve(final Iterable<I> inputs) {
        throw exception();
      }

      @Override
      public void resolve() {
        throw exception();
      }
    }

    private class StateResolved extends StatePending {

      private final Collection<I> mOutputs;

      private StateResolved(@NotNull final Collection<I> outputs) {
        mOutputs = outputs;
      }

      @NotNull
      private IllegalStateException exception() {
        return new IllegalStateException("promise already resolved");
      }

      public void accept(final CallbackIterable<I> callback) {
        mExecutor.execute(new Runnable() {

          public void run() {
            callback.addAll(mOutputs);
            callback.resolve();
            mCallbacks.add(callback);
          }
        });
      }

      @Override
      public void add(final I input) {
        throw exception();
      }

      @Override
      public void addAll(@Nullable final Iterable<I> inputs) {
        throw exception();
      }

      @Override
      public void addRejection(final Throwable reason) {
        throw exception();
      }

      @Override
      public void reject(final Throwable reason) {
        mState = new StateRejected(mOutputs, reason);
        mExecutor.execute(new Runnable() {

          public void run() {
            for (final CallbackIterable<I> callback : mCallbacks) {
              callback.reject(reason);
            }
          }
        });
      }

      @Override
      public void resolve(final Iterable<I> inputs) {
        throw exception();
      }

      @Override
      public void resolve() {
        throw exception();
      }
    }

    public void add(final I output) {
      synchronized (mMutex) {
        mState.add(output);
      }

      mStateExecutor.run();
    }

    public void addAll(@Nullable final Iterable<I> outputs) {
      synchronized (mMutex) {
        mState.addAll(outputs);
      }

      mStateExecutor.run();
    }

    public void addRejection(final Throwable reason) {
      synchronized (mMutex) {
        mState.addRejection(reason);
      }

      mStateExecutor.run();
    }

    public void resolve() {
      synchronized (mMutex) {
        mState.resolve();
      }

      mStateExecutor.run();
    }

    public void reject(final Throwable reason) {
      synchronized (mMutex) {
        mState.reject(reason);
      }

      mStateExecutor.run();
    }

    public void resolve(final Iterable<I> inputs) {
      synchronized (mMutex) {
        mState.resolve(inputs);
      }

      mStateExecutor.run();
    }
  }

  public void resolve(final Iterable<I> inputs) {
    mState.resolve(inputs);
  }

  public void addAll(@Nullable final Iterable<I> inputs) {
    mState.addAll(inputs);
  }

  public void addRejection(final Throwable reason) {
    mState.addRejection(reason);
  }

  public void resolve() {
    mState.resolve();
  }
}
