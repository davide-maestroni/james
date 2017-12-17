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

import dm.james.executor.ScheduledExecutor;
import dm.james.promise.Action;
import dm.james.promise.Chainable;
import dm.james.promise.DeferredPromise;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Promise;
import dm.james.promise.PromiseInspection;
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
  public DeferredPromise<I, O> catchAllFlat(
      @NotNull final Iterable<Class<? extends Throwable>> errors,
      @NotNull final Mapper<Throwable, Chainable<? extends O>> mapper) {
    return newInstance(mPromise.catchAllFlat(errors, mapper));
  }

  @NotNull
  public DeferredPromise<I, O> catchAllFlat(
      @NotNull final Mapper<Throwable, Chainable<? extends O>> mapper) {
    return newInstance(mPromise.catchAllFlat(mapper));
  }

  @NotNull
  public DeferredPromise<I, PromiseInspection<O>> inspect() {
    return newInstance(mPromise.inspect());
  }

  @NotNull
  public DeferredPromise<I, O> renew() {
    return newInstance(mPromise.renew());
  }

  @NotNull
  public DeferredPromise<I, O> scheduleAll(@NotNull final ScheduledExecutor executor) {
    return newInstance(mPromise.scheduleAll(executor));
  }

  @NotNull
  public <R> DeferredPromise<I, R> then(@NotNull final Handler<O, ? super Callback<R>> handler) {
    return newInstance(mPromise.then(handler));
  }

  @NotNull
  public <R> DeferredPromise<I, R> then(@NotNull final Mapper<O, R> mapper) {
    return newInstance(mPromise.then(mapper));
  }

  @NotNull
  public <R> DeferredPromise<I, R> thenFlat(
      @NotNull final Mapper<O, Chainable<? extends R>> mapper) {
    return newInstance(mPromise.thenFlat(mapper));
  }

  @NotNull
  public <R> DeferredPromise<I, R> thenTry(@NotNull final Handler<O, ? super Callback<R>> handler) {
    return newInstance(mPromise.thenTry(handler));
  }

  @NotNull
  public <R> DeferredPromise<I, R> thenTry(@NotNull final Mapper<O, R> mapper) {
    return newInstance(mPromise.thenTry(mapper));
  }

  @NotNull
  public <R> DeferredPromise<I, R> thenTryFlat(
      @NotNull final Mapper<O, Chainable<? extends R>> mapper) {
    return newInstance(mPromise.thenTryFlat(mapper));
  }

  @NotNull
  public DeferredPromise<I, O> onFulfill(@NotNull final Observer<O> observer) {
    return newInstance(mPromise.onFulfill(observer));
  }

  @NotNull
  public DeferredPromise<I, O> onReject(@NotNull final Observer<Throwable> observer) {
    return newInstance(mPromise.onReject(observer));
  }

  @NotNull
  public DeferredPromise<I, O> onResolve(@NotNull final Action action) {
    return newInstance(mPromise.onResolve(action));
  }

  public void defer(@NotNull final Chainable<I> chainable) {
    mDeferred.defer(chainable);
  }

  public void reject(final Throwable reason) {
    mDeferred.reject(reason);
  }

  public void resolve(final I input) {
    mDeferred.resolve(input);
  }

  public boolean cancel() {
    return mDeferred.cancel();
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

  public void waitResolved() {
    mPromise.waitResolved();
  }

  public boolean waitResolved(final long timeout, @NotNull final TimeUnit timeUnit) {
    return mPromise.waitResolved(timeout, timeUnit);
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

  public Throwable reason() {
    return mPromise.reason();
  }

  public O value() {
    return mPromise.value();
  }

  @NotNull
  private <R> WrappingDeferredPromise<I, R> newInstance(@NotNull final Promise<R> promise) {
    return new WrappingDeferredPromise<I, R>(mDeferred, promise);
  }
}
