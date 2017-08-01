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
import java.util.concurrent.TimeUnit;

import dm.james.promise.Action;
import dm.james.promise.DeferredPromise;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.RejectionException;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 08/01/2017.
 */
class WrappingDeferredPromise<I, O> implements DeferredPromise<I, O>, Serializable {

  private final DeferredPromise<I, ?> mDeferred;

  private final Promise<O> mPromise;

  WrappingDeferredPromise(@NotNull final DeferredPromise<I, ?> deferred,
      @NotNull final Promise<O> promise) {
    mDeferred = ConstantConditions.notNull("deferred", deferred);
    mPromise = ConstantConditions.notNull("promise", promise);
  }

  @NotNull
  public <R> DeferredPromise<I, R> apply(@NotNull final Mapper<Promise<O>, Promise<R>> mapper) {
    return new WrappingDeferredPromise<I, R>(mDeferred, mPromise.apply(mapper));
  }

  @NotNull
  public DeferredPromise<I, O> catchAny(@NotNull final Mapper<Throwable, O> mapper) {
    return new WrappingDeferredPromise<I, O>(mDeferred, mPromise.catchAny(mapper));
  }

  @NotNull
  public <R> DeferredPromise<I, R> then(
      @Nullable final Handler<O, R, ? super Callback<R>> outputHandler,
      @Nullable final Handler<Throwable, R, ? super Callback<R>> errorHandler) {
    return new WrappingDeferredPromise<I, R>(mDeferred, mPromise.then(outputHandler, errorHandler));
  }

  @NotNull
  public <R> DeferredPromise<I, R> then(@NotNull final Mapper<O, R> mapper) {
    return new WrappingDeferredPromise<I, R>(mDeferred, mPromise.then(mapper));
  }

  @NotNull
  public <R> DeferredPromise<I, R> then(@NotNull final Processor<O, R> processor) {
    return new WrappingDeferredPromise<I, R>(mDeferred, mPromise.then(processor));
  }

  @NotNull
  public DeferredPromise<I, O> whenFulfilled(@NotNull final Observer<O> observer) {
    return new WrappingDeferredPromise<I, O>(mDeferred, mPromise.whenFulfilled(observer));
  }

  @NotNull
  public DeferredPromise<I, O> whenRejected(@NotNull final Observer<Throwable> observer) {
    return new WrappingDeferredPromise<I, O>(mDeferred, mPromise.whenRejected(observer));
  }

  @NotNull
  public DeferredPromise<I, O> whenResolved(@NotNull final Action action) {
    return new WrappingDeferredPromise<I, O>(mDeferred, mPromise.whenResolved(action));
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
    mDeferred.reject(reason);
  }

  public void resolve(final I output) {
    mDeferred.resolve(output);
  }
}
