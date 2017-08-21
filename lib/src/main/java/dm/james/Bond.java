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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import dm.james.executor.ScheduledExecutor;
import dm.james.executor.ScheduledExecutors;
import dm.james.io.AllocationType;
import dm.james.io.Buffer;
import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.log.Logger;
import dm.james.promise.Action;
import dm.james.promise.CancellationException;
import dm.james.promise.DeferredPromise;
import dm.james.promise.DeferredPromiseIterable;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.Promise.Callback;
import dm.james.promise.Promise.Handler;
import dm.james.promise.PromiseIterable;
import dm.james.promise.PromiseIterable.CallbackIterable;
import dm.james.promise.PromiseIterable.StatefulHandler;
import dm.james.util.ConstantConditions;
import dm.james.util.InterruptedExecutionException;
import dm.james.util.Iterables;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 06/30/2017.
 */
public class Bond implements Serializable {

  // TODO: 06/08/2017 Handlers
  // TODO: 08/08/2017 promisify
  // TODO: 06/08/2017 james-android, james-retrofit, james-swagger

  private final Log mLog;

  private final Level mLogLevel;

  public Bond() {
    this(null, null);
  }

  private Bond(@Nullable final Log log, @Nullable final Level level) {
    mLog = log;
    mLogLevel = level;
  }

  @NotNull
  public <O> Promise<O> aPlus(@NotNull final Promise<O> promise) {
    if ((promise instanceof MappedPromise)
        && (((MappedPromise) promise).mapper() instanceof APlusMapper)) {
      return promise;
    }

    return new MappedPromise<O>(new APlusMapper(this), promise);
  }

  @NotNull
  public <O> PromiseIterable<O> all(@NotNull final Iterable<? extends Promise<?>> promises) {
    return iterable(new PromisesHandler<O>(promises));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <O> PromiseIterable<O> all(@NotNull final Promise<? extends Iterable<O>> promise) {
    if (promise instanceof PromiseIterable) {
      return (PromiseIterable<O>) promise;
    }

    return iterable(new IterableObserver<O>(promise));
  }

  @NotNull
  public <O> PromiseIterable<O> allSorted(@NotNull final Iterable<? extends Promise<?>> promises) {
    return resolvedIterable(null).anySorted(new PromisesHandler<O>(promises), null);
  }

  @NotNull
  public <O> Mapper<Promise<O>, Promise<O>> cache() {
    return new CacheMapper<O>(this.<O>deferred());
  }

  @NotNull
  public <O, S> PromiseIterable<O> combine(@NotNull final Iterable<? extends Promise<?>> promises,
      @NotNull final CombinationHandler<O, S> handler) {
    return iterable(new CombinationObserver<O, S>(handler, promises, mLog, mLogLevel));
  }

  @NotNull
  public <I> DeferredPromise<I, I> deferred() {
    return new DefaultDeferredPromise<I, I>(mLog, mLogLevel);
  }

  @NotNull
  public <I> DeferredPromiseIterable<I, I> deferredIterable() {
    return new DefaultDeferredPromiseIterable<I, I>(mLog, mLogLevel);
  }

  @NotNull
  public <O> Promise<O> from(@NotNull final Future<O> future) {
    return from(future, false);
  }

  @NotNull
  public <O> Promise<O> from(@NotNull final Future<O> future, final boolean mayInterruptIfRunning) {
    return new FuturePromise<O>(promise(new FutureObserver<O>(future)), future,
        mayInterruptIfRunning);
  }

  @NotNull
  public <O> Promise<O> from(@NotNull final Callable<O> callable) {
    return promise(new CallableObserver<O>(callable));
  }

  @NotNull
  public <O> Promise<O> from(@NotNull final ScheduledExecutor executor,
      @NotNull final Future<O> future) {
    return from(executor, future, false);
  }

  @NotNull
  public <O> Promise<O> from(@NotNull final ScheduledExecutor executor,
      @NotNull final Future<O> future, final boolean mayInterruptIfRunning) {
    return new FuturePromise<O>(promise(executor, new FutureObserver<O>(future)), future,
        mayInterruptIfRunning);
  }

  @NotNull
  public <O> Promise<O> from(@NotNull final ScheduledExecutor executor,
      @NotNull final Callable<O> callable) {
    return promise(executor, new CallableObserver<O>(callable));
  }

  @NotNull
  public <O> PromiseIterable<O> iterable(
      @NotNull final Observer<? super CallbackIterable<O>> observer) {
    return new DefaultPromiseIterable<O>(observer, mLog, mLogLevel);
  }

  @NotNull
  public <O> PromiseIterable<O> iterable(@NotNull final ScheduledExecutor executor,
      @NotNull final Observer<? super CallbackIterable<O>> observer) {
    return iterable(new ScheduledIterableObserver<O>(executor, observer));
  }

  @NotNull
  public <O> Promise<O> promise(@NotNull final Observer<? super Callback<O>> observer) {
    return new DefaultPromise<O>(observer, mLog, mLogLevel);
  }

  @NotNull
  public <O> Promise<O> promise(@NotNull final ScheduledExecutor executor,
      @NotNull final Observer<? super Callback<O>> observer) {
    return promise(new ScheduledObserver<O>(executor, observer));
  }

  // TODO: 19/08/2017 join?? remove race and survive??

  @NotNull
  public <O> PromiseIterable<O> race(@NotNull final Iterable<? extends Promise<?>> promises) {
    return combine(promises, new RaceHandler<O>());
  }

  @NotNull
  public <O> Promise<O> rejected(final Throwable reason) {
    return promise(new RejectedObserver<O>(reason));
  }

  @NotNull
  public <O> PromiseIterable<O> rejectedIterable(final Throwable reason) {
    return iterable(new RejectedObserver<O>(reason));
  }

  @NotNull
  public <O> Promise<O> resolved(final O output) {
    return promise(new ResolvedObserver<O>(output));
  }

  @NotNull
  public <O> PromiseIterable<O> resolvedIterable(@Nullable final Iterable<O> outputs) {
    return iterable(new ResolvedIterableObserver<O>(outputs));
  }

  @NotNull
  public <O> PromiseIterable<O> survive(@NotNull final Iterable<? extends Promise<?>> promises) {
    return combine(promises, new SurviveHandler<O>());
  }

  @NotNull
  public <O> Mapper<PromiseIterable<O>, PromiseIterable<Buffer>> toBuffer(
      @Nullable final AllocationType allocationType) {
    return new BufferMapper<O>(allocationType, mLog, mLogLevel);
  }

  @NotNull
  public <O> Mapper<PromiseIterable<O>, PromiseIterable<Buffer>> toBuffer(
      @Nullable final AllocationType allocationType, final int coreSize) {
    return new BufferMapper<O>(allocationType, coreSize, mLog, mLogLevel);
  }

  @NotNull
  public <O> Mapper<PromiseIterable<O>, PromiseIterable<Buffer>> toBuffer(
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return new BufferMapper<O>(allocationType, bufferSize, poolSize, mLog, mLogLevel);
  }

  @NotNull
  public Bond withLog(@Nullable final Log log) {
    return new Bond(log, mLogLevel);
  }

  @NotNull
  public Bond withLogLevel(@Nullable final Level level) {
    return new Bond(mLog, level);
  }

  private static class APlusMapper implements Mapper<Promise<?>, Promise<?>>, Serializable {

    private final Bond mBond;

    private APlusMapper(@NotNull final Bond bond) {
      mBond = bond;
    }

    @SuppressWarnings("unchecked")
    public Promise<?> apply(final Promise<?> promise) {
      return ((Promise<Object>) promise).apply(mBond.cache());
    }
  }

  private static class BufferHandler<O>
      implements StatefulHandler<O, Buffer, BufferOutputStream>, Serializable {

    private final AllocationType mAllocationType;

    private final int mBufferSize;

    private final int mCoreSize;

    private final Logger mLogger;

    private final int mPoolSize;

    private BufferHandler(@Nullable final AllocationType allocationType, final int coreSize,
        final int bufferSize, final int poolSize, @Nullable final Log log,
        @Nullable final Level level) {
      mAllocationType = allocationType;
      mCoreSize = coreSize;
      mBufferSize = bufferSize;
      mPoolSize = poolSize;
      mLogger = Logger.newLogger(log, level, this);
    }

    public BufferOutputStream create(@NotNull final CallbackIterable<Buffer> callback) {
      final int coreSize = mCoreSize;
      final int bufferSize = mBufferSize;
      final BufferOutputStream outputStream;
      if (coreSize > 0) {
        outputStream = new BufferOutputStream(callback, mAllocationType, coreSize);

      } else if (bufferSize > 0) {
        outputStream = new BufferOutputStream(callback, mAllocationType, bufferSize, mPoolSize);

      } else {
        outputStream = new BufferOutputStream(callback, mAllocationType);
      }

      return outputStream;
    }

    public BufferOutputStream fulfill(final BufferOutputStream state, final O input,
        @NotNull final CallbackIterable<Buffer> callback) throws Exception {
      if (state == null) {
        return null;
      }

      if (input instanceof InputStream) {
        final InputStream inputStream = (InputStream) input;
        try {
          state.transfer(inputStream);

        } finally {
          safeClose(inputStream);
        }

      } else if (input instanceof ReadableByteChannel) {
        final ReadableByteChannel channel = (ReadableByteChannel) input;
        try {
          state.transfer(channel);

        } finally {
          safeClose(channel);
        }

      } else if (input instanceof ByteBuffer) {
        state.write((ByteBuffer) input);

      } else if (input instanceof Buffer) {
        state.write((Buffer) input);

      } else if (input instanceof byte[]) {
        state.write((byte[]) input);

      } else if (input instanceof Number) {
        state.write(((Number) input).intValue());

      } else {
        callback.addRejection(new IllegalArgumentException("unsupported input type: " + input));
      }

      return state;
    }

    public BufferOutputStream reject(final BufferOutputStream state, final Throwable reason,
        @NotNull final CallbackIterable<Buffer> callback) throws Exception {
      if (state != null) {
        state.close();
        callback.reject(reason);
      }

      return null;
    }

    public void resolve(final BufferOutputStream state,
        @NotNull final CallbackIterable<Buffer> callback) {
      if (state != null) {
        state.close();
        callback.resolve();
      }
    }

    private void safeClose(@NotNull final Closeable closeable) {
      try {
        closeable.close();

      } catch (final IOException e) {
        mLogger.wrn(e, "Suppressed exception");
      }
    }
  }

  private static class BufferMapper<O>
      implements Mapper<PromiseIterable<O>, PromiseIterable<Buffer>>, Serializable {

    private final AllocationType mAllocationType;

    private final int mBufferSize;

    private final int mCoreSize;

    private final Log mLog;

    private final Level mLogLevel;

    private final int mPoolSize;

    private BufferMapper(@Nullable final AllocationType allocationType, @Nullable final Log log,
        @Nullable final Level level) {
      mAllocationType = allocationType;
      mCoreSize = -1;
      mBufferSize = -1;
      mPoolSize = -1;
      mLog = log;
      mLogLevel = level;
    }

    private BufferMapper(@Nullable final AllocationType allocationType, final int coreSize,
        @Nullable final Log log, @Nullable final Level level) {
      mAllocationType = allocationType;
      mCoreSize = ConstantConditions.positive("coreSize", coreSize);
      mBufferSize = -1;
      mPoolSize = -1;
      mLog = log;
      mLogLevel = level;
    }

    private BufferMapper(@Nullable final AllocationType allocationType, final int bufferSize,
        final int poolSize, @Nullable final Log log, @Nullable final Level level) {
      mAllocationType = allocationType;
      mCoreSize = -1;
      mBufferSize = ConstantConditions.positive("bufferSize", bufferSize);
      mPoolSize = ConstantConditions.positive("poolSize", poolSize);
      mLog = log;
      mLogLevel = level;
    }

    public PromiseIterable<Buffer> apply(final PromiseIterable<O> promise) {
      return promise.then(
          new BufferHandler<O>(mAllocationType, mCoreSize, mBufferSize, mPoolSize, mLog,
              mLogLevel));
    }
  }

  private static class CacheMapper<O> implements Mapper<Promise<O>, Promise<O>>, Serializable {

    private final DeferredPromise<O, O> mDeferred;

    private CacheMapper(@NotNull final DeferredPromise<O, O> deferred) {
      mDeferred = deferred;
    }

    public Promise<O> apply(final Promise<O> promise) {
      return BoundPromise.create(promise, mDeferred);
    }
  }

  private static class CombinationFulfillHandler<O, S> implements Handler<O, Callback<Void>> {

    private final CallbackIterable<O> mCallback;

    private final ScheduledExecutor mExecutor;

    private final CombinationHandler<O, S> mHandler;

    private final int mIndex;

    private final Logger mLogger;

    private final List<? extends Promise<?>> mPromises;

    private final CombinationState<S> mState;

    private CombinationFulfillHandler(@NotNull final CombinationState<S> state,
        @NotNull final CombinationHandler<O, S> handler, @NotNull final ScheduledExecutor executor,
        @NotNull final List<? extends Promise<?>> promises, final int index,
        @NotNull final CallbackIterable<O> callback, @NotNull final Logger logger) {
      mState = state;
      mHandler = handler;
      mExecutor = executor;
      mPromises = promises;
      mIndex = index;
      mCallback = callback;
      mLogger = logger;
    }

    public void accept(final O input, final Callback<Void> callback) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isRejected()) {
            // TODO: 19/08/2017 log??
            return;
          }

          try {
            state.set(mHandler.fulfill(state.get(), input, mPromises, mIndex, mCallback));

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Promise has been cancelled");
            state.setRejected(true);
            mCallback.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            mLogger.err(t, "Error while processing input: %s", input);
            state.setRejected(true);
            mCallback.reject(t);

          } finally {
            callback.reject(null);
          }
        }
      });
    }
  }

  private static class CombinationObserver<O, S>
      implements Observer<CallbackIterable<O>>, Serializable {

    private final ScheduledExecutor mExecutor;

    private final CombinationHandler<O, S> mHandler;

    private final Logger mLogger;

    private final List<? extends Promise<?>> mPromises;

    private CombinationObserver(@NotNull final CombinationHandler<O, S> handler,
        @NotNull final Iterable<? extends Promise<?>> promises, @Nullable final Log log,
        @Nullable final Level logLevel) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mPromises = Collections.unmodifiableList(Iterables.toList(promises));
      mExecutor = ScheduledExecutors.withThrottling(ScheduledExecutors.immediateExecutor(), 1);
      mLogger = Logger.newLogger(log, logLevel, this);
    }

    @SuppressWarnings("unchecked")
    public void accept(final CallbackIterable<O> callback) throws Exception {
      int i = 0;
      final Logger logger = mLogger;
      final ScheduledExecutor executor = mExecutor;
      final CombinationHandler<O, S> handler = mHandler;
      final List<? extends Promise<?>> promises = mPromises;
      final CombinationState<S> state = new CombinationState<S>(promises.size());
      try {
        state.set(handler.create(promises, callback));
        for (final Promise<?> promise : promises) {
          final int index = i++;
          if (promise instanceof PromiseIterable) {
            ((PromiseIterable<O>) promise).then(
                new CombinationFulfillHandler<O, S>(state, handler, executor, promises, index,
                    callback, logger),
                new CombinationRejectHandler<O, S>(state, handler, executor, promises, index,
                    callback, logger),
                new CombinationResolveObserver<O, S>(state, handler, executor, promises, index,
                    callback, logger));

          } else {
            ((Promise<O>) promise).then(
                new CombinationFulfillHandler<O, S>(state, handler, executor, promises, index,
                    callback, logger),
                new CombinationRejectHandler<O, S>(state, handler, executor, promises, index,
                    callback, logger))
                                  .whenResolved(
                                      new CombinationResolveObserver<O, S>(state, handler, executor,
                                          promises, index, callback, logger));
          }
        }

      } catch (final CancellationException e) {
        mLogger.wrn(e, "Promise has been cancelled");
        state.setRejected(true);
        callback.reject(e);

      } catch (final Throwable t) {
        InterruptedExecutionException.throwIfInterrupt(t);
        mLogger.err(t, "Error while initializing promise combination");
        state.setRejected(true);
        callback.reject(t);
      }
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new ObserverProxy<O, S>(mHandler, mPromises, logger.getLog(), logger.getLogLevel());
    }

    private static class ObserverProxy<O, S> extends SerializableProxy {

      private ObserverProxy(final CombinationHandler<O, S> handler,
          final List<? extends Promise<?>> promises, final Log log, final Level logLevel) {
        super(proxy(handler), promises, log, logLevel);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new CombinationObserver<O, S>((CombinationHandler<O, S>) args[0],
              (Iterable<? extends Promise<?>>) args[1], (Log) args[2], (Level) args[3]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }
  }

  private static class CombinationRejectHandler<O, S>
      implements Handler<Throwable, Callback<Void>> {

    private final CallbackIterable<O> mCallback;

    private final ScheduledExecutor mExecutor;

    private final CombinationHandler<O, S> mHandler;

    private final int mIndex;

    private final Logger mLogger;

    private final List<? extends Promise<?>> mPromises;

    private final CombinationState<S> mState;

    private CombinationRejectHandler(@NotNull final CombinationState<S> state,
        @NotNull final CombinationHandler<O, S> handler, @NotNull final ScheduledExecutor executor,
        @NotNull final List<? extends Promise<?>> promises, final int index,
        @NotNull final CallbackIterable<O> callback, @NotNull final Logger logger) {
      mState = state;
      mHandler = handler;
      mExecutor = executor;
      mPromises = promises;
      mIndex = index;
      mCallback = callback;
      mLogger = logger;
    }

    public void accept(final Throwable reason, final Callback<Void> callback) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isRejected()) {
            // TODO: 19/08/2017 log??
            return;
          }

          try {
            state.set(mHandler.reject(state.get(), reason, mPromises, mIndex, mCallback));

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Promise has been cancelled");
            state.setRejected(true);
            mCallback.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            mLogger.err(t, "Error while processing rejection with reason: %s", reason);
            state.setRejected(true);
            mCallback.reject(t);

          } finally {
            callback.reject(null);
          }
        }
      });
    }
  }

  private static class CombinationResolveObserver<O, S>
      implements Observer<Callback<Void>>, Action {

    private final CallbackIterable<O> mCallback;

    private final ScheduledExecutor mExecutor;

    private final CombinationHandler<O, S> mHandler;

    private final int mIndex;

    private final Logger mLogger;

    private final List<? extends Promise<?>> mPromises;

    private final CombinationState<S> mState;

    private CombinationResolveObserver(@NotNull final CombinationState<S> state,
        @NotNull final CombinationHandler<O, S> handler, @NotNull final ScheduledExecutor executor,
        @NotNull final List<? extends Promise<?>> promises, final int index,
        @NotNull final CallbackIterable<O> callback, @NotNull final Logger logger) {
      mState = state;
      mHandler = handler;
      mExecutor = executor;
      mPromises = promises;
      mIndex = index;
      mCallback = callback;
      mLogger = logger;
    }

    public void perform() {
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isRejected()) {
            // TODO: 19/08/2017 log??
            return;
          }

          try {
            state.set(mHandler.resolve(state.get(), mPromises, mIndex, mCallback));
            if (state.addResolved()) {
              mHandler.resolve(state.get(), mPromises, mCallback);
            }

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Promise has been cancelled");
            state.setRejected(true);
            mCallback.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            mLogger.err(t, "Error while processing resolution");
            state.setRejected(true);
            mCallback.reject(t);
          }
        }
      });
    }

    public void accept(final Callback<Void> callback) {
      perform();
    }
  }

  private static class CombinationState<S> {

    private final int mCount;

    private boolean mIsRejected;

    private int mResolved;

    private S mState;

    private CombinationState(final int count) {
      mCount = count;
    }

    boolean addResolved() {
      return (++mResolved >= mCount);
    }

    S get() {
      return mState;
    }

    boolean isRejected() {
      return mIsRejected;
    }

    void setRejected(final boolean isRejected) {
      mIsRejected = isRejected;
    }

    void set(final S state) {
      mState = state;
    }
  }

  private static class FuturePromise<O> extends PromiseWrapper<O> {

    private final Future<?> mFuture;

    private final boolean mMayInterruptIfRunning;

    private FuturePromise(@NotNull final Promise<O> promise, @NotNull final Future<?> future,
        final boolean mayInterruptIfRunning) {
      super(promise);
      mFuture = future;
      mMayInterruptIfRunning = mayInterruptIfRunning;
    }

    @Override
    public boolean cancel() {
      final boolean cancelled = super.cancel();
      mFuture.cancel(mMayInterruptIfRunning);
      return cancelled;
    }

    @NotNull
    protected <R> Promise<R> newInstance(@NotNull final Promise<R> promise) {
      return new FuturePromise<R>(promise, mFuture, mMayInterruptIfRunning);
    }
  }

  private static class IterableObserver<O> implements Observer<CallbackIterable<O>>, Serializable {

    private final Promise<? extends Iterable<O>> mPromise;

    private IterableObserver(@NotNull final Promise<? extends Iterable<O>> promise) {
      mPromise = ConstantConditions.notNull("promise", promise);
    }

    public void accept(final CallbackIterable<O> callback) {
      callback.addAllDeferred(mPromise);
      callback.resolve();
    }
  }

  private static class PromisesHandler<O>
      implements Handler<Object, CallbackIterable<O>>, Observer<CallbackIterable<O>>, Serializable {

    private final Iterable<? extends Promise<?>> mPromises;

    private PromisesHandler(@NotNull final Iterable<? extends Promise<?>> promises) {
      mPromises = ConstantConditions.notNull("promises", promises);
    }

    @SuppressWarnings("unchecked")
    public void accept(final Object input, final CallbackIterable<O> callback) {
      for (final Promise<?> promise : mPromises) {
        if (promise instanceof PromiseIterable) {
          callback.addAllDeferred((PromiseIterable<O>) promise);

        } else {
          callback.addDeferred((Promise<O>) promise);
        }
      }

      callback.resolve();
    }

    @SuppressWarnings("unchecked")
    public void accept(final CallbackIterable<O> callback) {
      for (final Promise<?> promise : mPromises) {
        if (promise instanceof PromiseIterable) {
          callback.addAllDeferred((PromiseIterable<O>) promise);

        } else {
          callback.addDeferred((Promise<O>) promise);
        }
      }

      callback.resolve();
    }
  }

  private static class RaceHandler<O> implements CombinationHandler<O, Integer>, Serializable {

    public Integer create(@NotNull final List<? extends Promise<?>> promises,
        @NotNull final CallbackIterable<O> callback) {
      return null;
    }

    public Integer fulfill(final Integer state, final O output,
        @NotNull final List<? extends Promise<?>> promises, final int index,
        @NotNull final CallbackIterable<O> callback) {
      if ((state == null) || (state == index)) {
        callback.add(output);
        return index;
      }

      return state;
    }

    public Integer reject(final Integer state, final Throwable reason,
        @NotNull final List<? extends Promise<?>> promises, final int index,
        @NotNull final CallbackIterable<O> callback) throws Exception {
      if ((state == null) || (state == index)) {
        callback.addRejection(reason);
        return index;
      }

      return state;
    }

    public Integer resolve(final Integer state, @NotNull final List<? extends Promise<?>> promises,
        final int index, @NotNull final CallbackIterable<O> callback) {
      if ((state == null) || (state == index)) {
        callback.resolve();
        return index;
      }

      return state;
    }

    public void resolve(final Integer state, @NotNull final List<? extends Promise<?>> promises,
        @NotNull final CallbackIterable<O> callback) throws Exception {
    }
  }

  private static class SurviveHandler<O> implements CombinationHandler<O, Integer>, Serializable {

    private int cancel(final Integer state, @NotNull final List<? extends Promise<?>> promises,
        final int index) {
      if (state == null) {
        final Promise<?> winner = promises.get(index);
        for (final Promise<?> promise : promises) {
          if (promise != winner) {
            promise.cancel();
          }
        }

        return index;
      }

      return state;
    }

    public Integer create(@NotNull final List<? extends Promise<?>> promises,
        @NotNull final CallbackIterable<O> callback) {
      return null;
    }

    public Integer fulfill(Integer state, final O output,
        @NotNull final List<? extends Promise<?>> promises, final int index,
        @NotNull final CallbackIterable<O> callback) {
      state = cancel(state, promises, index);
      if (state == index) {
        callback.add(output);
      }

      return state;
    }

    public Integer reject(Integer state, final Throwable reason,
        @NotNull final List<? extends Promise<?>> promises, final int index,
        @NotNull final CallbackIterable<O> callback) throws Exception {
      state = cancel(state, promises, index);
      if (state == index) {
        callback.addRejection(reason);
      }

      return state;
    }

    public Integer resolve(Integer state, @NotNull final List<? extends Promise<?>> promises,
        final int index, @NotNull final CallbackIterable<O> callback) {
      state = cancel(state, promises, index);
      if (state == index) {
        callback.resolve();
      }

      return state;
    }

    public void resolve(final Integer state, @NotNull final List<? extends Promise<?>> promises,
        @NotNull final CallbackIterable<O> callback) throws Exception {
    }
  }
}
