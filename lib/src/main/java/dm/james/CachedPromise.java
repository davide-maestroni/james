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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import dm.james.promise.Action;
import dm.james.promise.DeferredPromise;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.RejectionException;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
class CachedPromise<I, O> implements Promise<O> {

  private final DeferredPromise<I, O> mDeferred;

  private final Promise<I> mPromise;

  private CachedPromise(@NotNull final Promise<I> promise,
      @NotNull final DeferredPromise<I, O> deferred) {
    mPromise = promise;
    mDeferred = deferred;
  }

  @NotNull
  static <I, O> CachedPromise<I, O> create(@NotNull final Promise<I> promise,
      @NotNull final DeferredPromise<I, O> deferred) {
    final CachedPromise<I, O> cachedPromise =
        new CachedPromise<I, O>(ConstantConditions.notNull("promise", promise),
            ConstantConditions.notNull("deferred", deferred));
    promise.then(new DeferredProcessor<I>(deferred));
    return cachedPromise;
  }

  @NotNull
  public <R> Promise<R> apply(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    return new CachedPromise<I, R>(mPromise, mDeferred.apply(mapper));
  }

  public O get() {
    return mDeferred.get();
  }

  public O get(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mDeferred.get(timeout, timeUnit);
  }

  @Nullable
  public RejectionException getError() {
    return mDeferred.getError();
  }

  @Nullable
  public RejectionException getError(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mDeferred.getError(timeout, timeUnit);
  }

  public RejectionException getErrorOr(final RejectionException other, final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return mDeferred.getErrorOr(other, timeout, timeUnit);
  }

  public O getOr(final O other, final long timeout, @NotNull final TimeUnit timeUnit) {
    return mDeferred.getOr(other, timeout, timeUnit);
  }

  public boolean isBound() {
    return mDeferred.isBound();
  }

  public boolean isFulfilled() {
    return mDeferred.isFulfilled();
  }

  public boolean isPending() {
    return mDeferred.isPending();
  }

  public boolean isRejected() {
    return mDeferred.isRejected();
  }

  public boolean isResolved() {
    return mDeferred.isResolved();
  }

  @NotNull
  public <R> Promise<R> then(@Nullable final Handler<O, R, Callback<R>> outputHandler,
      @Nullable final Handler<Throwable, R, Callback<R>> errorHandler) {
    return new CachedPromise<I, R>(mPromise, mDeferred.then(outputHandler, errorHandler));
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Mapper<O, R> mapper) {
    return new CachedPromise<I, R>(mPromise, mDeferred.then(mapper));
  }

  @NotNull
  public <R> Promise<R> then(@NotNull final Processor<O, R> processor) {
    return new CachedPromise<I, R>(mPromise, mDeferred.then(processor));
  }

  @NotNull
  public Promise<O> thenCatch(@NotNull final Mapper<Throwable, O> mapper) {
    return new CachedPromise<I, O>(mPromise, mDeferred.thenCatch(mapper));
  }

  public void waitResolved() {
    mDeferred.waitResolved();
  }

  public boolean waitResolved(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mDeferred.waitResolved(timeout, timeUnit);
  }

  @NotNull
  public Promise<O> whenFulfilled(@NotNull final Observer<O> observer) {
    return new CachedPromise<I, O>(mPromise, mDeferred.whenFulfilled(observer));
  }

  @NotNull
  public Promise<O> whenRejected(@NotNull final Observer<Throwable> observer) {
    return new CachedPromise<I, O>(mPromise, mDeferred.whenRejected(observer));
  }

  @NotNull
  public Promise<O> whenResolved(@NotNull final Action action) {
    return new CachedPromise<I, O>(mPromise, mDeferred.whenResolved(action));
  }

  private Object writeReplace() throws ObjectStreamException {
    return new PromiseProxy<I, O>(mPromise, mDeferred);
  }

  private static class DeferredProcessor<O> implements Processor<O, O>, Serializable {

    private final DeferredPromise<O, ?> mDeferred;

    private DeferredProcessor(@NotNull final DeferredPromise<O, ?> deferred) {
      mDeferred = deferred;
    }

    public void reject(final Throwable reason, @NotNull final Callback<O> callback) {
      mDeferred.reject(reason);
    }

    public void resolve(final O input, @NotNull final Callback<O> callback) {
      mDeferred.resolve(input);
    }
  }

  private static class PromiseProxy<I, O> implements Serializable {

    private final DeferredPromise<I, O> mDeferred;

    private final Promise<I> mPromise;

    private PromiseProxy(@NotNull final Promise<I> promise,
        @NotNull final DeferredPromise<I, O> deferred) {
      mPromise = promise;
      mDeferred = deferred;
    }

    Object readResolve() throws ObjectStreamException {
      return CachedPromise.create(mPromise, mDeferred);
    }
  }
}
