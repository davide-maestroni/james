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
import java.util.ArrayList;
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
import dm.james.promise.CancellationException;
import dm.james.promise.Chainable;
import dm.james.promise.Chainable.Callback;
import dm.james.promise.Chainable.Handler;
import dm.james.promise.ChainableIterable;
import dm.james.promise.ChainableIterable.CallbackIterable;
import dm.james.promise.ChainableIterable.StatefulHandler;
import dm.james.promise.DeferredPromise;
import dm.james.promise.DeferredPromiseIterable;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.PromiseIterable;
import dm.james.promise.RejectionIterableException;
import dm.james.util.ConstantConditions;
import dm.james.util.InterruptedExecutionException;
import dm.james.util.Iterables;
import dm.james.util.SerializableProxy;

/**
 * Created by davide-maestroni on 06/30/2017.
 */
public class Bond implements Serializable {

  // TODO: 06/08/2017 Handlers => james-handler??
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
        && (((MappedPromise) promise).mapper() instanceof MapperAPlus)) {
      return promise;
    }

    return new MappedPromise<O>(new MapperAPlus(this), promise);
  }

  @NotNull
  public <O> PromiseIterable<O> all(@NotNull final Iterable<? extends Chainable<?>> chainables) {
    return iterable(new HandlerChainables<O>(chainables));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <O> PromiseIterable<O> all(@NotNull final Chainable<? extends Iterable<O>> chainable) {
    if (chainable instanceof PromiseIterable) {
      return (PromiseIterable<O>) chainable;
    }

    return iterable(new ObserverChainableIterable<O>(chainable));
  }

  @NotNull
  public <O> PromiseIterable<O> allSorted(
      @NotNull final Iterable<? extends Chainable<?>> chainables) {
    return resolvedIterable(null).forEachSorted(new HandlerChainables<O>(chainables));
  }

  @NotNull
  public <O> PromiseIterable<O> any(@NotNull final Iterable<? extends Chainable<?>> chainables) {
    return this.<O>all(chainables).then(new HandlerAny<O>(mLog, mLogLevel));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <O> PromiseIterable<O> any(@NotNull final Chainable<? extends Iterable<O>> chainable) {
    return all(chainable).then(new HandlerAny<O>(mLog, mLogLevel));
  }

  @NotNull
  public <O> Mapper<Promise<O>, Promise<O>> cache() {
    return new CacheMapper<O>(this.<O>deferred());
  }

  @NotNull
  public <O, S> PromiseIterable<O> combine(
      @NotNull final Iterable<? extends Chainable<?>> chainables,
      @NotNull final CombinationHandler<O, S> handler) {
    return iterable(new ObserverCombination<O, S>(handler, chainables, mLog, mLogLevel));
  }

  @NotNull
  public <I> DeferredPromise<I, I> deferred() {
    return new DefaultDeferredPromise<I, I>(mLog, mLogLevel);
  }

  @NotNull
  public <I> DeferredPromiseIterable<I, I> deferredIterable() {
    // TODO: 02/09/2017 max cached propagations
    return new DefaultDeferredPromiseIterable<I, I>(mLog, mLogLevel);
  }

  @NotNull
  public <O> PromiseIterable<O> first(@NotNull final Iterable<? extends Chainable<?>> chainables) {
    return this.<O>all(chainables).then(new HandlerFirst<O>(mLog, mLogLevel));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <O> PromiseIterable<O> first(@NotNull final Chainable<? extends Iterable<O>> chainable) {
    return all(chainable).then(new HandlerFirst<O>(mLog, mLogLevel));
  }

  @NotNull
  public <O> Promise<O> fromCallable(@NotNull final Callable<O> callable) {
    return promise(new ObserverCallable<O>(callable));
  }

  @NotNull
  public <O> Promise<O> fromCallable(@NotNull final ScheduledExecutor executor,
      @NotNull final Callable<O> callable) {
    return promise(executor, new ObserverCallable<O>(callable));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <O> Promise<O> fromChainable(@NotNull final Chainable<O> chainable) {
    if (chainable instanceof Promise) {
      return (Promise<O>) chainable;
    }

    return promise(new ObserverChainable<O>(chainable));
  }

  @NotNull
  public <O> Promise<O> fromFuture(@NotNull final Future<O> future) {
    return fromFuture(future, false);
  }

  @NotNull
  public <O> Promise<O> fromFuture(@NotNull final Future<O> future,
      final boolean mayInterruptIfRunning) {
    return new FuturePromise<O>(promise(new ObserverFuture<O>(future)), future,
        mayInterruptIfRunning);
  }

  @NotNull
  public <O> Promise<O> fromFuture(@NotNull final ScheduledExecutor executor,
      @NotNull final Future<O> future) {
    return fromFuture(executor, future, false);
  }

  @NotNull
  public <O> Promise<O> fromFuture(@NotNull final ScheduledExecutor executor,
      @NotNull final Future<O> future, final boolean mayInterruptIfRunning) {
    return new FuturePromise<O>(promise(executor, new ObserverFuture<O>(future)), future,
        mayInterruptIfRunning);
  }

  @NotNull
  public <O> PromiseIterable<O> iterable(
      @NotNull final Observer<? super CallbackIterable<O>> observer) {
    return new DefaultPromiseIterable<O>(observer, mLog, mLogLevel);
  }

  @NotNull
  public <O> PromiseIterable<O> iterable(@NotNull final ScheduledExecutor executor,
      @NotNull final Observer<? super CallbackIterable<O>> observer) {
    return iterable(new ObserverScheduledIterable<O>(executor, observer));
  }

  @NotNull
  public <O> Promise<O> promise(@NotNull final Observer<? super Callback<O>> observer) {
    return new DefaultPromise<O>(observer, mLog, mLogLevel);
  }

  @NotNull
  public <O> Promise<O> promise(@NotNull final ScheduledExecutor executor,
      @NotNull final Observer<? super Callback<O>> observer) {
    return promise(new ObserverScheduled<O>(executor, observer));
  }

  @NotNull
  public <O> PromiseIterable<O> race(@NotNull final Iterable<? extends Chainable<?>> chainables) {
    return combine(chainables, new HandlerRace<O>());
  }

  @NotNull
  public <O> Promise<O> rejected(final Throwable reason) {
    return promise(new ObserverRejected<O>(reason));
  }

  @NotNull
  public <O> PromiseIterable<O> rejectedIterable(final Throwable reason) {
    return iterable(new ObserverRejected<O>(reason));
  }

  @NotNull
  public <O> Promise<O> resolved(final O output) {
    return promise(new ObserverResolved<O>(output));
  }

  @NotNull
  public <O> PromiseIterable<O> resolvedIterable(@Nullable final Iterable<O> outputs) {
    return iterable(new ObserverResolvedIterable<O>(outputs));
  }

  @NotNull
  public <O> Mapper<Promise<O>, Promise<O>> share() {
    // TODO: 02/09/2017 classes
    return new Mapper<Promise<O>, Promise<O>>() {

      public Promise<O> apply(final Promise<O> promise) throws Exception {
        final DeferredPromise<O, O> deferred = deferred();
        promise.then(new Handler<O, Callback<Void>>() {

          public void fulfill(final O input, @NotNull final Callback<Void> callback) throws
              Exception {
            deferred.resolve(input);
          }

          public void reject(final Throwable reason, @NotNull final Callback<Void> callback) throws
              Exception {
            deferred.reject(reason);
          }
        });
        return deferred;
      }
    };
  }

  @NotNull
  public <O> PromiseIterable<O> survive(
      @NotNull final Iterable<? extends Chainable<?>> chainables) {
    return combine(chainables, new HandlerSurvive<O>());
  }

  @NotNull
  public <O> Mapper<PromiseIterable<O>, PromiseIterable<Buffer>> toBuffer(
      @Nullable final AllocationType allocationType) {
    return new MapperBuffer<O>(allocationType, mLog, mLogLevel);
  }

  @NotNull
  public <O> Mapper<PromiseIterable<O>, PromiseIterable<Buffer>> toBuffer(
      @Nullable final AllocationType allocationType, final int coreSize) {
    return new MapperBuffer<O>(allocationType, coreSize, mLog, mLogLevel);
  }

  @NotNull
  public <O> Mapper<PromiseIterable<O>, PromiseIterable<Buffer>> toBuffer(
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return new MapperBuffer<O>(allocationType, bufferSize, poolSize, mLog, mLogLevel);
  }

  @NotNull
  public Bond withLog(@Nullable final Log log) {
    return new Bond(log, mLogLevel);
  }

  @NotNull
  public Bond withLogLevel(@Nullable final Level level) {
    return new Bond(mLog, level);
  }

  private interface AnyState<R> extends Observer<Callback<R>> {

    boolean isRejection();
  }

  private static class AnyStateRejected<R> implements AnyState<R> {

    private final ArrayList<Throwable> mReasons = new ArrayList<Throwable>();

    private AnyStateRejected(final Throwable reason) {
      mReasons.add(reason);
    }

    public void accept(final Callback<R> callback) {
      callback.reject(new RejectionIterableException(mReasons));
    }

    public boolean isRejection() {
      return true;
    }

    void add(final Throwable reason) {
      mReasons.add(reason);
    }
  }

  private static class AnyStateResolved<R> implements AnyState<R> {

    public void accept(final Callback<R> input) {
    }

    public boolean isRejection() {
      return false;
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

    private void safeClose(@NotNull final Closeable closeable) {
      try {
        closeable.close();

      } catch (final IOException e) {
        mLogger.wrn(e, "Suppressed exception");
      }
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

  private static class HandlerAny<O> implements StatefulHandler<O, O, AnyState<O>>, Serializable {

    private final Logger mLogger;

    @SuppressWarnings("unchecked")
    private HandlerAny(@Nullable final Log log, @Nullable final Level level) {
      mLogger = Logger.newLogger(log, level, this);
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new HandlerProxy<O>(logger.getLog(), logger.getLogLevel());
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Log log, final Level level) {
        super(log, level);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerAny<O>((Log) args[0], (Level) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public AnyState<O> create(@NotNull final CallbackIterable<O> callback) {
      return null;
    }

    public AnyState<O> fulfill(final AnyState<O> state, final O input,
        @NotNull final CallbackIterable<O> callback) throws Exception {
      if ((state == null) || state.isRejection()) {
        callback.resolve(input);
        return new AnyStateResolved<O>();
      }

      return state;
    }

    @SuppressWarnings("unchecked")
    public AnyState<O> reject(final AnyState<O> state, final Throwable reason,
        @NotNull final CallbackIterable<O> callback) throws Exception {
      if (state == null) {
        return new AnyStateRejected<O>(reason);

      } else if (state.isRejection()) {
        ((AnyStateRejected<O>) state).add(reason);
      }

      return state;
    }

    public void resolve(final AnyState<O> state, @NotNull final CallbackIterable<O> callback) throws
        Exception {
      if (state == null) {
        callback.resolve();

      } else {
        state.accept(callback);
      }
    }
  }

  private static class HandlerChainables<O>
      implements Handler<Object, CallbackIterable<O>>, Observer<CallbackIterable<O>>, Serializable {

    private final Iterable<? extends Chainable<?>> mChainables;

    private HandlerChainables(@NotNull final Iterable<? extends Chainable<?>> chainables) {
      mChainables = ConstantConditions.notNull("chainables", chainables);
    }

    @SuppressWarnings("unchecked")
    public void fulfill(final Object input, @NotNull final CallbackIterable<O> callback) {
      for (final Chainable<?> chainable : mChainables) {
        if (chainable instanceof ChainableIterable) {
          callback.addAllDeferred((ChainableIterable<O>) chainable);

        } else {
          callback.addDeferred((Chainable<O>) chainable);
        }
      }

      callback.resolve();
    }

    @SuppressWarnings("unchecked")
    public void accept(final CallbackIterable<O> callback) {
      for (final Chainable<?> chainable : mChainables) {
        if (chainable instanceof ChainableIterable) {
          callback.addAllDeferred((ChainableIterable<O>) chainable);

        } else {
          callback.addDeferred((Chainable<O>) chainable);
        }
      }

      callback.resolve();
    }

    public void reject(final Throwable reason, @NotNull final CallbackIterable<O> callback) {
      callback.reject(reason);
    }
  }

  private static class HandlerCombination<O, S> implements Handler<O, Callback<Void>> {

    private final CallbackIterable<O> mCallback;

    private final List<? extends Chainable<?>> mChainables;

    private final ScheduledExecutor mExecutor;

    private final CombinationHandler<O, S> mHandler;

    private final int mIndex;

    private final Logger mLogger;

    private final CombinationState<S> mState;

    private HandlerCombination(@NotNull final CombinationState<S> state,
        @NotNull final CombinationHandler<O, S> handler, @NotNull final ScheduledExecutor executor,
        @NotNull final List<? extends Chainable<?>> chainables, final int index,
        @NotNull final CallbackIterable<O> callback, @NotNull final Logger logger) {
      mState = state;
      mHandler = handler;
      mExecutor = executor;
      mChainables = chainables;
      mIndex = index;
      mCallback = callback;
      mLogger = logger;
    }

    public void fulfill(final O input, @NotNull final Callback<Void> ignored) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isRejected()) {
            mLogger.wrn("Ignoring fulfillment: %s", input);
            return;
          }

          final CallbackIterable<O> callback = mCallback;
          try {
            final int index = mIndex;
            final CombinationHandler<O, S> handler = mHandler;
            final List<? extends Chainable<?>> chainables = mChainables;
            state.set(handler.fulfill(state.get(), input, chainables, index, callback));
            state.set(handler.resolve(state.get(), chainables, index, callback));
            if (state.addResolved()) {
              handler.settle(state.get(), chainables, callback);
            }

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Promise has been cancelled");
            state.setRejected(true);
            callback.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            mLogger.err(t, "Error while processing fulfillment: %s", input);
            state.setRejected(true);
            callback.reject(t);
          }
        }
      });
    }

    public void reject(final Throwable reason, @NotNull final Callback<Void> ignored) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isRejected()) {
            mLogger.wrn(reason, "Ignoring rejection");
            return;
          }

          final CallbackIterable<O> callback = mCallback;
          try {
            final int index = mIndex;
            final CombinationHandler<O, S> handler = mHandler;
            final List<? extends Chainable<?>> chainables = mChainables;
            state.set(handler.reject(state.get(), reason, chainables, index, callback));
            state.set(handler.resolve(state.get(), chainables, index, callback));
            if (state.addResolved()) {
              handler.settle(state.get(), chainables, callback);
            }

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Promise has been cancelled");
            state.setRejected(true);
            callback.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            mLogger.err(t, "Error while processing rejection with reason: %s", reason);
            state.setRejected(true);
            callback.reject(t);
          }
        }
      });
    }
  }

  private static class HandlerFirst<O> implements StatefulHandler<O, O, Boolean>, Serializable {

    private final Logger mLogger;

    @SuppressWarnings("unchecked")
    private HandlerFirst(@Nullable final Log log, @Nullable final Level level) {
      mLogger = Logger.newLogger(log, level, this);
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new HandlerProxy<O>(logger.getLog(), logger.getLogLevel());
    }

    private static class HandlerProxy<O> extends SerializableProxy {

      private HandlerProxy(final Log log, final Level level) {
        super(log, level);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new HandlerAny<O>((Log) args[0], (Level) args[1]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    public Boolean create(@NotNull final CallbackIterable<O> callback) {
      return Boolean.FALSE;
    }

    public Boolean fulfill(final Boolean state, final O input,
        @NotNull final CallbackIterable<O> callback) {
      if (!state) {
        callback.resolve(input);
      }

      return Boolean.TRUE;
    }

    @SuppressWarnings("unchecked")
    public Boolean reject(final Boolean state, final Throwable reason,
        @NotNull final CallbackIterable<O> callback) {
      if (!state) {
        callback.reject(reason);
      }

      return Boolean.TRUE;
    }

    public void resolve(final Boolean state, @NotNull final CallbackIterable<O> callback) {
      if (!state) {
        callback.resolve();
      }
    }
  }

  private static class HandlerRace<O> implements CombinationHandler<O, Integer>, Serializable {

    public Integer create(@NotNull final List<? extends Chainable<?>> promises,
        @NotNull final CallbackIterable<O> callback) {
      return null;
    }

    public Integer fulfill(final Integer state, final O output,
        @NotNull final List<? extends Chainable<?>> chainables, final int index,
        @NotNull final CallbackIterable<O> callback) {
      if ((state == null) || (state == index)) {
        callback.add(output);
        return index;
      }

      return state;
    }

    public Integer reject(final Integer state, final Throwable reason,
        @NotNull final List<? extends Chainable<?>> chainables, final int index,
        @NotNull final CallbackIterable<O> callback) throws Exception {
      if ((state == null) || (state == index)) {
        callback.addRejection(reason);
        return index;
      }

      return state;
    }

    public Integer resolve(final Integer state,
        @NotNull final List<? extends Chainable<?>> chainables, final int index,
        @NotNull final CallbackIterable<O> callback) {
      if ((state == null) || (state == index)) {
        callback.resolve();
        return index;
      }

      return state;
    }

    public void settle(final Integer state, @NotNull final List<? extends Chainable<?>> chainables,
        @NotNull final CallbackIterable<O> callback) throws Exception {
    }
  }

  private static class HandlerSurvive<O> implements CombinationHandler<O, Integer>, Serializable {

    private int cancel(final Integer state, @NotNull final List<? extends Chainable<?>> chainables,
        final int index) {
      if (state == null) {
        final Chainable<?> winner = chainables.get(index);
        for (final Chainable<?> chainable : chainables) {
          if ((chainable != winner) && (chainable instanceof Promise)) {
            ((Promise<?>) chainable).cancel();
          }
        }

        return index;
      }

      return state;
    }

    public Integer create(@NotNull final List<? extends Chainable<?>> chainables,
        @NotNull final CallbackIterable<O> callback) {
      return null;
    }

    public Integer fulfill(Integer state, final O output,
        @NotNull final List<? extends Chainable<?>> chainables, final int index,
        @NotNull final CallbackIterable<O> callback) {
      state = cancel(state, chainables, index);
      if (state == index) {
        callback.add(output);
      }

      return state;
    }

    public Integer reject(Integer state, final Throwable reason,
        @NotNull final List<? extends Chainable<?>> chainables, final int index,
        @NotNull final CallbackIterable<O> callback) throws Exception {
      state = cancel(state, chainables, index);
      if (state == index) {
        callback.addRejection(reason);
      }

      return state;
    }

    public Integer resolve(Integer state, @NotNull final List<? extends Chainable<?>> chainables,
        final int index, @NotNull final CallbackIterable<O> callback) {
      state = cancel(state, chainables, index);
      if (state == index) {
        callback.resolve();
      }

      return state;
    }

    public void settle(final Integer state, @NotNull final List<? extends Chainable<?>> chainables,
        @NotNull final CallbackIterable<O> callback) throws Exception {
    }
  }

  private static class MapperAPlus implements Mapper<Promise<?>, Promise<?>>, Serializable {

    private final Bond mBond;

    private MapperAPlus(@NotNull final Bond bond) {
      mBond = bond;
    }

    @SuppressWarnings("unchecked")
    public Promise<?> apply(final Promise<?> promise) {
      return ((Promise<Object>) promise).apply(mBond.cache());
    }
  }

  private static class MapperBuffer<O>
      implements Mapper<PromiseIterable<O>, PromiseIterable<Buffer>>, Serializable {

    private final AllocationType mAllocationType;

    private final int mBufferSize;

    private final int mCoreSize;

    private final Log mLog;

    private final Level mLogLevel;

    private final int mPoolSize;

    private MapperBuffer(@Nullable final AllocationType allocationType, @Nullable final Log log,
        @Nullable final Level level) {
      mAllocationType = allocationType;
      mCoreSize = -1;
      mBufferSize = -1;
      mPoolSize = -1;
      mLog = log;
      mLogLevel = level;
    }

    private MapperBuffer(@Nullable final AllocationType allocationType, final int coreSize,
        @Nullable final Log log, @Nullable final Level level) {
      mAllocationType = allocationType;
      mCoreSize = ConstantConditions.positive("coreSize", coreSize);
      mBufferSize = -1;
      mPoolSize = -1;
      mLog = log;
      mLogLevel = level;
    }

    private MapperBuffer(@Nullable final AllocationType allocationType, final int bufferSize,
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

  private static class ObserverChainable<O> implements Observer<Callback<O>>, Serializable {

    private final Chainable<O> mChainable;

    private ObserverChainable(@NotNull final Chainable<O> chainable) {
      mChainable = ConstantConditions.notNull("chainable", chainable);
    }

    public void accept(final Callback<O> callback) {
      callback.defer(mChainable);
    }
  }

  private static class ObserverChainableIterable<O>
      implements Observer<CallbackIterable<O>>, Serializable {

    private final Chainable<? extends Iterable<O>> mChainable;

    private ObserverChainableIterable(@NotNull final Chainable<? extends Iterable<O>> chainable) {
      mChainable = ConstantConditions.notNull("chainable", chainable);
    }

    public void accept(final CallbackIterable<O> callback) {
      callback.addAllDeferred(mChainable);
      callback.resolve();
    }
  }

  private static class ObserverCombination<O, S>
      implements Observer<CallbackIterable<O>>, Serializable {

    private final List<? extends Chainable<?>> mChainableList;

    private final List<? extends Chainable<?>> mChainables;

    private final ScheduledExecutor mExecutor;

    private final CombinationHandler<O, S> mHandler;

    private final Logger mLogger;

    private ObserverCombination(@NotNull final CombinationHandler<O, S> handler,
        @NotNull final Iterable<? extends Chainable<?>> chainables, @Nullable final Log log,
        @Nullable final Level logLevel) {
      mHandler = ConstantConditions.notNull("handler", handler);
      mChainableList = Iterables.toList(chainables);
      mChainables = Collections.unmodifiableList(mChainableList);
      mExecutor = ScheduledExecutors.withThrottling(ScheduledExecutors.immediateExecutor(), 1);
      mLogger = Logger.newLogger(log, logLevel, this);
    }

    private Object writeReplace() throws ObjectStreamException {
      final Logger logger = mLogger;
      return new ObserverProxy<O, S>(mHandler, mChainableList, logger.getLog(),
          logger.getLogLevel());
    }

    private static class ObserverProxy<O, S> extends SerializableProxy {

      private ObserverProxy(final CombinationHandler<O, S> handler,
          final List<? extends Chainable<?>> chainables, final Log log, final Level logLevel) {
        super(proxy(handler), chainables, log, logLevel);
      }

      @SuppressWarnings("unchecked")
      Object readResolve() throws ObjectStreamException {
        try {
          final Object[] args = deserializeArgs();
          return new ObserverCombination<O, S>((CombinationHandler<O, S>) args[0],
              (Iterable<? extends Chainable<?>>) args[1], (Log) args[2], (Level) args[3]);

        } catch (final Throwable t) {
          throw new InvalidObjectException(t.getMessage());
        }
      }
    }

    @SuppressWarnings("unchecked")
    public void accept(final CallbackIterable<O> callback) throws Exception {
      int i = 0;
      final Logger logger = mLogger;
      final ScheduledExecutor executor = mExecutor;
      final CombinationHandler<O, S> handler = mHandler;
      final List<? extends Chainable<?>> chainables = mChainables;
      final CombinationState<S> state = new CombinationState<S>(chainables.size());
      try {
        state.set(handler.create(chainables, callback));
        for (final Chainable<?> chainable : chainables) {
          final int index = i++;
          if (chainable instanceof ChainableIterable) {
            ((ChainableIterable<O>) chainable).then(
                new StatefulHandlerCombination<O, S>(state, handler, executor, chainables, index,
                    callback, logger));

          } else {
            ((Chainable<O>) chainable).then(
                new HandlerCombination<O, S>(state, handler, executor, chainables, index, callback,
                    logger));
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
  }

  private static class StatefulHandlerCombination<O, S> implements StatefulHandler<O, Void, Void> {

    private final CallbackIterable<O> mCallback;

    private final List<? extends Chainable<?>> mChainables;

    private final ScheduledExecutor mExecutor;

    private final CombinationHandler<O, S> mHandler;

    private final int mIndex;

    private final Logger mLogger;

    private final CombinationState<S> mState;

    private StatefulHandlerCombination(@NotNull final CombinationState<S> state,
        @NotNull final CombinationHandler<O, S> handler, @NotNull final ScheduledExecutor executor,
        @NotNull final List<? extends Chainable<?>> chainables, final int index,
        @NotNull final CallbackIterable<O> callback, @NotNull final Logger logger) {
      mState = state;
      mHandler = handler;
      mExecutor = executor;
      mChainables = chainables;
      mIndex = index;
      mCallback = callback;
      mLogger = logger;
    }

    public Void create(@NotNull final CallbackIterable<Void> callback) {
      return null;
    }

    public Void fulfill(final Void state, final O input,
        @NotNull final CallbackIterable<Void> ignored) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isRejected()) {
            mLogger.wrn("Ignoring fulfillment: %s", input);
            return;
          }

          final CallbackIterable<O> callback = mCallback;
          try {
            state.set(mHandler.fulfill(state.get(), input, mChainables, mIndex, callback));

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Promise has been cancelled");
            state.setRejected(true);
            callback.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            mLogger.err(t, "Error while processing fulfillment: %s", input);
            state.setRejected(true);
            callback.reject(t);
          }
        }
      });

      return null;
    }

    public Void reject(final Void state, final Throwable reason,
        @NotNull final CallbackIterable<Void> ignored) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isRejected()) {
            mLogger.wrn(reason, "Ignoring rejection");
            return;
          }

          final CallbackIterable<O> callback = mCallback;
          try {
            state.set(mHandler.reject(state.get(), reason, mChainables, mIndex, callback));

          } catch (final CancellationException e) {
            mLogger.wrn(e, "Promise has been cancelled");
            state.setRejected(true);
            callback.reject(e);

          } catch (final Throwable t) {
            InterruptedExecutionException.throwIfInterrupt(t);
            mLogger.err(t, "Error while processing rejection with reason: %s", reason);
            state.setRejected(true);
            callback.reject(t);
          }
        }
      });

      return null;
    }

    public void resolve(final Void state, @NotNull final CallbackIterable<Void> ignored) {
      mExecutor.execute(new Runnable() {

        public void run() {
          final CombinationState<S> state = mState;
          if (state.isRejected()) {
            mLogger.wrn("Ignoring resolution");
            return;
          }

          try {
            state.set(mHandler.resolve(state.get(), mChainables, mIndex, mCallback));
            if (state.addResolved()) {
              mHandler.settle(state.get(), mChainables, mCallback);
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
  }
}
