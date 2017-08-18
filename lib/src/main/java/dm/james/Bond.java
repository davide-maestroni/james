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
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import dm.james.executor.ScheduledExecutor;
import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.log.Logger;
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
import dm.james.promise.RejectionException;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 06/30/2017.
 */
public class Bond implements Serializable {

  // TODO: 15/08/2017 race
  // TODO: 06/08/2017 Handlers
  // TODO: 08/08/2017 promisify
  // TODO: 08/08/2017 BinaryObserver??
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
    return resolvedIterable(null).anySorted(new PromisesHandler<O>(promises), null);
  }

  @NotNull
  public <O> PromiseIterable<O> all(@NotNull final ScheduledExecutor executor,
      @NotNull final Iterable<? extends Promise<?>> promises) {
    return resolvedIterable(null).scheduleAny(executor, null)
                                 .anySorted(new PromisesHandler<O>(promises), null);
  }

  @NotNull
  public <O> Mapper<Promise<O>, Promise<O>> cache() {
    return new CacheMapper<O>(this.<O>deferred());
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
  public <O> PromiseIterable<O> each(@NotNull final Iterable<? extends Promise<?>> promises) {
    return iterable(new PromisesHandler<O>(promises));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <O> PromiseIterable<O> each(@NotNull final Promise<? extends Iterable<O>> promise) {
    if (promise instanceof PromiseIterable) {
      return (PromiseIterable<O>) promise;
    }

    return iterable(new IterableObserver<O>(promise));
  }

  @NotNull
  public <O> PromiseIterable<O> each(@NotNull final ScheduledExecutor executor,
      @NotNull final Iterable<? extends Promise<?>> promises) {
    return iterable(executor, new PromisesHandler<O>(promises));
  }

  @NotNull
  public <O> PromiseIterable<O> each(@NotNull final ScheduledExecutor executor,
      @NotNull final Promise<? extends Iterable<O>> promise) {
    return iterable(executor, new IterableObserver<O>(promise));
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

  @NotNull
  public <O> PromiseIterable<O> race(@NotNull final Iterable<? extends Promise<?>> promises) {
    // TODO: 15/08/2017 race & cancel
    return iterable(new RaceObserver<O>(this.<O>deferredIterable(), promises));
  }

  @NotNull
  public <O> PromiseIterable<O> race(@NotNull final ScheduledExecutor executor,
      @NotNull final Iterable<? extends Promise<?>> promises) {
    return iterable(executor, new RaceObserver<O>(this.<O>deferredIterable(), promises));
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
  public <O> Mapper<PromiseIterable<O>, PromiseIterable<Buffer>> toBuffer(
      @Nullable final AllocationType allocationType) {
    return new BufferMapper<O>(this, allocationType);
  }

  @NotNull
  public <O> Mapper<PromiseIterable<O>, PromiseIterable<Buffer>> toBuffer(
      @Nullable final AllocationType allocationType, final int coreSize) {
    return new BufferMapper<O>(this, allocationType, coreSize);
  }

  @NotNull
  public <O> Mapper<PromiseIterable<O>, PromiseIterable<Buffer>> toBuffer(
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return new BufferMapper<O>(this, allocationType, bufferSize, poolSize);
  }

  @NotNull
  public Bond withLog(@Nullable final Log log) {
    return new Bond(log, mLogLevel);
  }

  @NotNull
  public Bond withLogLevel(@Nullable final Level level) {
    return new Bond(mLog, level);
  }

  private void safeClose(@NotNull final Closeable closeable) {
    try {
      closeable.close();

    } catch (final IOException e) {
      Logger.newLogger(mLog, mLogLevel, this).wrn(e, "Suppressed exception");
    }
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

    private final Bond mBond;

    private final int mBufferSize;

    private final int mCoreSize;

    private final int mPoolSize;

    private BufferHandler(@NotNull final Bond bond, @Nullable final AllocationType allocationType,
        final int coreSize, final int bufferSize, final int poolSize) {
      mBond = bond;
      mAllocationType = allocationType;
      mCoreSize = coreSize;
      mBufferSize = bufferSize;
      mPoolSize = poolSize;
    }

    public BufferOutputStream create(@NotNull final CallbackIterable<Buffer> callback) {
      final DeferredPromiseIterable<Buffer, Buffer> deferred = mBond.deferredIterable();
      final int coreSize = mCoreSize;
      final int bufferSize = mBufferSize;
      final BufferOutputStream outputStream;
      if (coreSize > 0) {
        outputStream = new BufferOutputStream(deferred, mAllocationType, coreSize);

      } else if (bufferSize > 0) {
        outputStream = new BufferOutputStream(deferred, mAllocationType, bufferSize, mPoolSize);

      } else {
        outputStream = new BufferOutputStream(deferred, mAllocationType);
      }

      callback.addAllDeferred(deferred);
      return outputStream;
    }

    public BufferOutputStream fulfill(final BufferOutputStream state, final O input,
        @NotNull final CallbackIterable<Buffer> callback) throws Exception {
      if (input instanceof InputStream) {
        final InputStream inputStream = (InputStream) input;
        try {
          state.transfer(inputStream);

        } finally {
          mBond.safeClose(inputStream);
        }

      } else if (input instanceof ReadableByteChannel) {
        final ReadableByteChannel channel = (ReadableByteChannel) input;
        try {
          state.transfer(channel);

        } finally {
          mBond.safeClose(channel);
        }

      } else if (input instanceof ByteBuffer) {
        state.write((ByteBuffer) input);

      } else if (input instanceof Buffer) {
        state.write((Buffer) input);

      } else if (input instanceof byte[]) {
        state.write((byte[]) input);

      } else {
        throw new IllegalArgumentException("unsupported input type: " + input);
      }

      return state;
    }

    public BufferOutputStream reject(final BufferOutputStream state, final Throwable reason,
        @NotNull final CallbackIterable<Buffer> callback) throws Exception {
      throw RejectionException.wrapIfNotException(reason);
    }

    public void resolve(final BufferOutputStream state,
        @NotNull final CallbackIterable<Buffer> callback) {
      callback.resolve();
    }
  }

  private static class BufferMapper<O>
      implements Mapper<PromiseIterable<O>, PromiseIterable<Buffer>>, Serializable {

    private final AllocationType mAllocationType;

    private final Bond mBond;

    private final int mBufferSize;

    private final int mCoreSize;

    private final int mPoolSize;

    private BufferMapper(@NotNull final Bond bond, @Nullable final AllocationType allocationType) {
      mBond = bond;
      mAllocationType = allocationType;
      mCoreSize = -1;
      mBufferSize = -1;
      mPoolSize = -1;
    }

    private BufferMapper(@NotNull final Bond bond, @Nullable final AllocationType allocationType,
        final int coreSize) {
      mBond = bond;
      mAllocationType = allocationType;
      mCoreSize = ConstantConditions.positive("coreSize", coreSize);
      mBufferSize = -1;
      mPoolSize = -1;
    }

    private BufferMapper(@NotNull final Bond bond, @Nullable final AllocationType allocationType,
        final int bufferSize, final int poolSize) {
      mBond = bond;
      mAllocationType = allocationType;
      mCoreSize = -1;
      mBufferSize = ConstantConditions.positive("bufferSize", bufferSize);
      mPoolSize = ConstantConditions.positive("poolSize", poolSize);
    }

    public PromiseIterable<Buffer> apply(final PromiseIterable<O> promise) {
      return promise.thenTryState(
          new BufferHandler<O>(mBond, mAllocationType, mCoreSize, mBufferSize, mPoolSize));
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

  private static class RaceFulfillHandler<O> implements Handler<O, Callback<Void>> {

    private final int mIndex;

    private final DeferredPromiseIterable<O, ?> mPromise;

    private final AtomicInteger mWinner;

    private RaceFulfillHandler(@NotNull final DeferredPromiseIterable<O, ?> promise,
        @NotNull final AtomicInteger winner, final int index) {
      mPromise = promise;
      mWinner = winner;
      mIndex = index;
    }

    public void accept(final O input, final Callback<Void> callback) {
      final int index = mIndex;
      final AtomicInteger winner = mWinner;
      if (winner.compareAndSet(-1, index) || (winner.get() == index)) {
        final DeferredPromiseIterable<O, ?> promise = mPromise;
        promise.add(input);
        promise.resolve();
      }
    }
  }

  private static class RaceObserver<O> implements Observer<CallbackIterable<O>> {

    private final DeferredPromiseIterable<O, ?> mDeferred;

    private final Iterable<? extends Promise<?>> mPromises;

    private RaceObserver(@NotNull final DeferredPromiseIterable<O, ?> deferred,
        @NotNull final Iterable<? extends Promise<?>> promises) {
      mPromises = ConstantConditions.notNull("promises", promises);
      mDeferred = deferred;
    }

    @SuppressWarnings("unchecked")
    public void accept(final CallbackIterable<O> input) throws Exception {
      final DeferredPromiseIterable<O, ?> deferred = mDeferred;
      final AtomicInteger winner = new AtomicInteger(-1);
      int i = 0;
      for (final Promise<?> promise : mPromises) {
        if (promise instanceof PromiseIterable) {
          final int index = i++;
          ((PromiseIterable<O>) promise).then(new RaceFulfillHandler<O>(deferred, winner, index),
              new RaceRejectHandler(deferred, winner, index),
              new RaceResolveHandler(deferred, winner, index));

        } else {
          final int index = i++;
          ((Promise<O>) promise).then(new RaceFulfillHandler<O>(deferred, winner, index),
              new RaceRejectHandler(deferred, winner, index));
        }
      }
    }
  }

  private static class RaceRejectHandler implements Handler<Throwable, Callback<Void>> {

    private final int mIndex;

    private final DeferredPromiseIterable<?, ?> mPromise;

    private final AtomicInteger mWinner;

    private RaceRejectHandler(@NotNull final DeferredPromiseIterable<?, ?> promise,
        @NotNull final AtomicInteger winner, final int index) {
      mPromise = promise;
      mWinner = winner;
      mIndex = index;
    }

    public void accept(final Throwable reason, final Callback<Void> callback) {
      final int index = mIndex;
      final AtomicInteger winner = mWinner;
      if (winner.compareAndSet(-1, index) || (winner.get() == index)) {
        mPromise.reject(reason);
      }
    }
  }

  private static class RaceResolveHandler implements Observer<Callback<Void>> {

    private final int mIndex;

    private final DeferredPromiseIterable<?, ?> mPromise;

    private final AtomicInteger mWinner;

    private RaceResolveHandler(@NotNull final DeferredPromiseIterable<?, ?> promise,
        @NotNull final AtomicInteger winner, final int index) {
      mPromise = promise;
      mWinner = winner;
      mIndex = index;
    }

    public void accept(final Callback<Void> callback) {
      final int index = mIndex;
      final AtomicInteger winner = mWinner;
      if (winner.compareAndSet(-1, index) || (winner.get() == index)) {
        mPromise.resolve();
      }
    }
  }
}
