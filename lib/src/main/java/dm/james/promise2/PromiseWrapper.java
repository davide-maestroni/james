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

package dm.james.promise2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dm.james.executor.ScheduledExecutor;
import dm.james.promise.Action;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.PromiseInspection;
import dm.james.promise.RejectionException;
import dm.james.promise.ScheduledData;
import dm.james.util.Backoff;
import dm.james.util.ConstantConditions;

/**
 * Created by davide-maestroni on 12/11/2017.
 */
abstract class PromiseWrapper<V> implements Promise<V> {

  private final Promise<V> mPromise;

  PromiseWrapper(@NotNull final Promise<V> promise) {
    mPromise = ConstantConditions.notNull("promise", promise);
  }

  @NotNull
  public <E> Iterable<E> asIterable() {
    return mPromise.asIterable();
  }

  public boolean cancel() {
    return mPromise.cancel();
  }

  @NotNull
  public <E, R, S> Promise<R> collect(
      @NotNull final ReductionHandler<E, R, S, ? super Callback<R>> handler) {
    return newInstance(mPromise.collect(handler));
  }

  @NotNull
  public <E, R, S> Promise<R> collect(@Nullable final Mapper<? super Callback<R>, S> create,
      @Nullable final ReductionFulfill<E, R, S, ? super Callback<R>> fulfill,
      @Nullable final ReductionReject<R, S, ? super Callback<R>> reject,
      @Nullable final ReductionResolve<R, S, ? super Callback<R>> resolve) {
    return newInstance(mPromise.collect(create, fulfill, reject, resolve));
  }

  @NotNull
  public <E, R, S> Promise<R> collectTrying(
      @NotNull final ReductionHandler<E, R, S, ? super Callback<R>> handler) {
    return newInstance(mPromise.collectTrying(handler));
  }

  @NotNull
  public <E, R, S> Promise<R> collectTrying(@Nullable final Mapper<? super Callback<R>, S> create,
      @Nullable final ReductionFulfill<E, R, S, ? super Callback<R>> fulfill,
      @Nullable final ReductionReject<R, S, ? super Callback<R>> reject,
      @Nullable final ReductionResolve<R, S, ? super Callback<R>> resolve) {
    return newInstance(mPromise.collectTrying(create, fulfill, reject, resolve));
  }

  @NotNull
  public <E, R> Promise<Iterable<R>> forEach(
      @Nullable final CallbackHandler<E, R, ? super IterableCallback<R>> fulfill,
      @Nullable final CallbackHandler<Throwable, R, ? super IterableCallback<R>> reject) {
    return newInstance(mPromise.forEach(fulfill, reject));
  }

  @NotNull
  public <R> Promise<Iterable<R>> forEachCatch(@Nullable final Mapper<Throwable, R> mapper) {
    return newInstance(mPromise.forEachCatch(mapper));
  }

  @NotNull
  public <E, R> Promise<Iterable<R>> forEachMap(@Nullable final Mapper<E, R> mapper) {
    return newInstance(mPromise.forEachMap(mapper));
  }

  @NotNull
  public <E, R> Promise<Iterable<R>> forEachMap(@Nullable final Mapper<E, R> mapper,
      final int minBatchSize, final int maxBatchSize) {
    return newInstance(mPromise.forEachMap(mapper, minBatchSize, maxBatchSize));
  }

  @NotNull
  public <E> Promise<Iterable<E>> forEachSchedule(@NotNull final ScheduledExecutor executor,
      @NotNull final Backoff<ScheduledData<E>> backoff) {
    return newInstance(mPromise.forEachSchedule(executor, backoff));
  }

  @NotNull
  public <E> Promise<Iterable<E>> forEachSchedule(@NotNull final ScheduledExecutor executor) {
    return newInstance(mPromise.<E>forEachSchedule(executor));
  }

  @Nullable
  public RejectionException getReason() {
    return mPromise.getReason();
  }

  @Nullable
  public RejectionException getReason(final long timeout, @NotNull final TimeUnit unit) {
    return mPromise.getReason(timeout, unit);
  }

  public RejectionException getReasonOr(final RejectionException other, final long timeout,
      @NotNull final TimeUnit unit) {
    return mPromise.getReasonOr(other, timeout, unit);
  }

  public V getValue() {
    return mPromise.getValue();
  }

  public V getValue(final long timeout, @NotNull final TimeUnit unit) {
    return mPromise.getValue(timeout, unit);
  }

  public V getValueOr(final V other, final long timeout, @NotNull final TimeUnit unit) {
    return mPromise.getValueOr(other, timeout, unit);
  }

  @NotNull
  public Promise<PromiseInspection<V>> inspect() {
    return newInstance(mPromise.inspect());
  }

  @NotNull
  public <E> BlockingIterator<PromiseInspection<E>> inspectIterator() {
    return mPromise.inspectIterator();
  }

  @NotNull
  public <E> BlockingIterator<PromiseInspection<E>> inspectIterator(final long timeout,
      @NotNull final TimeUnit unit) {
    return mPromise.inspectIterator(timeout, unit);
  }

  public boolean isChained() {
    return mPromise.isChained();
  }

  @NotNull
  public <E> BlockingIterator<E> iterator() {
    return mPromise.iterator();
  }

  @NotNull
  public <E> BlockingIterator<E> iterator(final long timeout, @NotNull final TimeUnit unit) {
    return mPromise.iterator(timeout, unit);
  }

  @NotNull
  public <E> Promise<Iterable<E>> onEachFulfill(@NotNull final Observer<E> observer) {
    return newInstance(mPromise.onEachFulfill(observer));
  }

  @NotNull
  public <E> Promise<Iterable<E>> onEachReject(@NotNull final Observer<Throwable> observer) {
    return newInstance(mPromise.<E>onEachReject(observer));
  }

  @NotNull
  public Promise<V> onFulfill(@NotNull final Observer<? super V> observer) {
    return newInstance(mPromise.onFulfill(observer));
  }

  @NotNull
  public Promise<V> onReject(@NotNull final Observer<? super Throwable> observer) {
    return newInstance(mPromise.onReject(observer));
  }

  @NotNull
  public Promise<V> onResolve(@NotNull final Action action) {
    return newInstance(mPromise.onResolve(action));
  }

  @NotNull
  public Promise<V> ordered() {
    return newInstance(mPromise.ordered());
  }

  @NotNull
  public <E, R, S> Promise<Iterable<R>> reduce(
      @NotNull final ReductionHandler<E, R, S, ? super IterableCallback<R>> handler) {
    return newInstance(mPromise.reduce(handler));
  }

  @NotNull
  public <E, R, S> Promise<Iterable<R>> reduce(
      @Nullable final Mapper<? super IterableCallback<R>, S> create,
      @Nullable final ReductionFulfill<E, R, S, ? super IterableCallback<R>> fulfill,
      @Nullable final ReductionReject<R, S, ? super IterableCallback<R>> reject,
      @Nullable final ReductionResolve<R, S, ? super IterableCallback<R>> resolve) {
    return newInstance(mPromise.reduce(create, fulfill, reject, resolve));
  }

  @NotNull
  public <E, R, S> Promise<Iterable<R>> reduceTrying(
      @NotNull final ReductionHandler<E, R, S, ? super IterableCallback<R>> handler) {
    return newInstance(mPromise.reduceTrying(handler));
  }

  @NotNull
  public <E, R, S> Promise<Iterable<R>> reduceTrying(
      @Nullable final Mapper<? super IterableCallback<R>, S> create,
      @Nullable final ReductionFulfill<E, R, S, ? super IterableCallback<R>> fulfill,
      @Nullable final ReductionReject<R, S, ? super IterableCallback<R>> reject,
      @Nullable final ReductionResolve<R, S, ? super IterableCallback<R>> resolve) {
    return newInstance(mPromise.reduceTrying(create, fulfill, reject, resolve));
  }

  @NotNull
  public Promise<V> renew() {
    return newInstance(mPromise.renew());
  }

  @NotNull
  public <R> Promise<R> then(@Nullable final CallbackHandler<V, R, ? super Callback<R>> fulfill,
      @Nullable final CallbackHandler<Throwable, R, ? super Callback<R>> reject) {
    return newInstance(mPromise.then(fulfill, reject));
  }

  @NotNull
  public Promise<V> thenCatch(@NotNull final Mapper<Throwable, V> mapper) {
    return newInstance(mPromise.thenCatch(mapper));
  }

  @NotNull
  public <R> Promise<R> thenMap(@NotNull final Mapper<V, R> mapper) {
    return newInstance(mPromise.thenMap(mapper));
  }

  @NotNull
  public Promise<V> thenSchedule(@NotNull final ScheduledExecutor executor) {
    return newInstance(mPromise.thenSchedule(executor));
  }

  @NotNull
  public <R> Promise<Iterable<R>> thenSpread(
      @Nullable final CallbackHandler<V, R, ? super IterableCallback<R>> fulfill,
      @Nullable final CallbackHandler<Throwable, R, ? super IterableCallback<R>> reject) {
    return newInstance(mPromise.thenSpread(fulfill, reject));
  }

  @NotNull
  public Promise<V> trying() {
    return newInstance(mPromise.trying());
  }

  public void waitDone() {
    mPromise.waitDone();
  }

  public boolean waitDone(final long timeout, @NotNull final TimeUnit unit) {
    return mPromise.waitDone(timeout, unit);
  }

  @NotNull
  public <R, S> Promise<R> whenChained(
      @NotNull final ChainHandler<V, R, S, ? super Callback<R>> handler) {
    return newInstance(mPromise.whenChained(handler));
  }

  @NotNull
  public <R, S> Promise<R> whenChained(@Nullable final Mapper<? super Promise<V>, S> create,
      @Nullable final ChainHandle<V, S> handle,
      @Nullable final ChainThen<V, R, S, ? super Callback<R>> then) {
    return newInstance(mPromise.whenChained(create, handle, then));
  }

  @NotNull
  public <R, S> Promise<Iterable<R>> whenEachChained(
      @NotNull final ChainHandler<V, R, S, ? super IterableCallback<R>> handler) {
    return newInstance(mPromise.whenEachChained(handler));
  }

  @NotNull
  public <R, S> Promise<Iterable<R>> whenEachChained(
      @Nullable final Mapper<? super Promise<V>, S> create,
      @Nullable final ChainHandle<V, S> handle,
      @Nullable final ChainThen<V, R, S, ? super IterableCallback<R>> then) {
    return newInstance(mPromise.whenEachChained(create, handle, then));
  }

  @NotNull
  public <R> Promise<R> wrap(
      @NotNull final Mapper<? super Promise<?>, ? extends Promise<?>> mapper) {
    return newInstance(mPromise.<R>wrap(mapper));
  }

  @NotNull
  public <R> Promise<R> wrapOnce(
      @NotNull final Mapper<? super Promise<V>, ? extends Promise<R>> mapper) {
    return newInstance(mPromise.wrapOnce(mapper));
  }

  public boolean cancel(final boolean b) {
    return mPromise.cancel();
  }

  public boolean isCancelled() {
    return mPromise.isCancelled();
  }

  public boolean isDone() {
    return mPromise.isDone();
  }

  public V get() throws InterruptedException, ExecutionException {
    return mPromise.get();
  }

  public V get(final long timeout, @NotNull final TimeUnit unit) throws InterruptedException,
      ExecutionException, TimeoutException {
    return mPromise.get(timeout, unit);
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

  public V value() {
    return mPromise.value();
  }

  @NotNull
  protected abstract <R> Promise<R> newInstance(@NotNull Promise<R> promise);

  @NotNull
  protected Promise<V> wrapped() {
    return mPromise;
  }
}
