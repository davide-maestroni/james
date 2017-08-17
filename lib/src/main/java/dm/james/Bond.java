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

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import dm.james.executor.ScheduledExecutor;
import dm.james.handler.Handlers;
import dm.james.io.Buffer;
import dm.james.io.BufferOutputStream;
import dm.james.log.Log;
import dm.james.log.Log.Level;
import dm.james.promise.DeferredPromise;
import dm.james.promise.DeferredPromiseIterable;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.Promise.Callback;
import dm.james.promise.Promise.Handler;
import dm.james.promise.PromiseIterable;
import dm.james.promise.PromiseIterable.CallbackIterable;
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
    return this.<O>resolvedIterable(null).allSorted(new PromisesHandler<O>(promises), null);
  }

  @NotNull
  public <O> PromiseIterable<O> all(@NotNull final Promise<? extends Iterable<O>> promise) {
    return each(promise).all(IdentityMapper.<Iterable<O>>instance());
  }

  @NotNull
  public <O> PromiseIterable<O> all(@NotNull final ScheduledExecutor executor,
      @NotNull final Iterable<? extends Promise<?>> promises) {
    return this.<O>resolvedIterable(null).then(null, null, Handlers.<O>resolveOn(executor))
                                         .allSorted(new PromisesHandler<O>(promises), null);
  }

  @NotNull
  public <O> PromiseIterable<O> all(@NotNull final ScheduledExecutor executor,
      @NotNull final Promise<? extends Iterable<O>> promise) {
    return each(executor, promise).all(IdentityMapper.<Iterable<O>>instance());
  }

  @NotNull
  public <O> PromiseIterable<O> any(@NotNull final Iterable<? extends Promise<?>> promises) {
    return this.<O>each(promises).any(IdentityMapper.<O>instance());
  }

  @NotNull
  public <O> PromiseIterable<O> any(@NotNull final Promise<? extends Iterable<O>> promise) {
    return each(promise).any(IdentityMapper.<O>instance());
  }

  @NotNull
  public <O> PromiseIterable<O> any(@NotNull final ScheduledExecutor executor,
      @NotNull final Iterable<? extends Promise<?>> promises) {
    return this.<O>each(executor, promises).any(IdentityMapper.<O>instance());
  }

  @NotNull
  public <O> PromiseIterable<O> any(@NotNull final ScheduledExecutor executor,
      @NotNull final Promise<? extends Iterable<O>> promise) {
    return each(executor, promise).any(IdentityMapper.<O>instance());
  }

  // TODO: 15/08/2017 anySorted(Iterable), anySorted(ScheduledExecutor, Iterable) => eachSorted?

  @NotNull
  public BufferOutputStream bufferStream(@Nullable final AllocationType allocationType) {
    return new DefaultBufferOutputStream(this.<Buffer>deferredIterable(), allocationType);
  }

  @NotNull
  public BufferOutputStream bufferStream(@Nullable final AllocationType allocationType,
      final int coreSize) {
    return new DefaultBufferOutputStream(this.<Buffer>deferredIterable(), allocationType, coreSize);
  }

  @NotNull
  public BufferOutputStream bufferStream(@Nullable final AllocationType allocationType,
      final int bufferSize, final int poolSize) {
    return new DefaultBufferOutputStream(this.<Buffer>deferredIterable(), allocationType,
        bufferSize, poolSize);
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
    final AtomicInteger atomicInteger = new AtomicInteger(-1);
    int index = 0;
    if (atomicInteger.compareAndSet(-1, index) || atomicInteger.get() == index) {

    }
    return null;
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
      implements Handler<Iterable<O>, CallbackIterable<O>>, Observer<CallbackIterable<O>>,
      Serializable {

    private final Iterable<? extends Promise<?>> mPromises;

    private PromisesHandler(@NotNull final Iterable<? extends Promise<?>> promises) {
      mPromises = ConstantConditions.notNull("promises", promises);
    }

    @SuppressWarnings("unchecked")
    public void accept(final Iterable<O> input, final CallbackIterable<O> callback) {
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
}
