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

package dm.james.promise;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;

import dm.james.executor.ScheduledExecutor;
import dm.james.util.Backoff;

/**
 * Created by davide-maestroni on 08/01/2017.
 */
public interface DeferredPromiseIterable<I, O>
    extends PromiseIterable<O>, DeferredPromise<Iterable<I>, Iterable<O>> {

  void add(I input);

  void addAll(@Nullable Iterable<I> inputs);

  void addAllDeferred(@Nullable Iterable<? extends Chainable<?>> chainables);

  void addAllDeferred(@NotNull Chainable<? extends Iterable<I>> chainable);

  void addDeferred(@NotNull Chainable<I> chainable);

  void addRejection(Throwable reason);

  @NotNull
  <R> DeferredPromiseIterable<I, R> applyAll(
      @NotNull Mapper<PromiseIterable<O>, PromiseIterable<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> applyEach(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> applyEachSorted(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> backoffEach(@NotNull ScheduledExecutor executor,
      @NotNull Backoff<ScheduledData<O>> backoff);

  @NotNull
  DeferredPromiseIterable<I, O> catchAll(@NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchAll(@NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchAllFlat(
      @NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Chainable<? extends Iterable<O>>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchAllFlat(
      @NotNull Mapper<Throwable, Chainable<? extends Iterable<O>>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> renew();

  @NotNull
  DeferredPromiseIterable<I, O> scheduleAll(@NotNull ScheduledExecutor executor);

  @NotNull
  DeferredPromiseIterable<I, O> onFulfill(@NotNull Observer<Iterable<O>> observer);

  @NotNull
  DeferredPromiseIterable<I, O> onReject(@NotNull Observer<Throwable> observer);

  @NotNull
  DeferredPromiseIterable<I, O> onResolve(@NotNull Action action);

  @NotNull
  DeferredPromiseIterable<I, O> catchEach(@NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, O> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchEach(@NotNull Mapper<Throwable, O> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchEachSpread(
      @NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchEachSpread(@NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchEachSpreadTrusted(
      @NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Chainable<? extends Iterable<O>>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchEachSpreadTrusted(
      @NotNull Mapper<Throwable, Chainable<? extends Iterable<O>>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchEachTrusted(
      @NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Chainable<? extends O>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchEachTrusted(
      @NotNull Mapper<Throwable, Chainable<? extends O>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachSorted(
      @NotNull Handler<O, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachSpread(@NotNull Mapper<O, Iterable<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachSpreadTrusted(
      @NotNull Mapper<O, Chainable<? extends Iterable<R>>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachTrusted(
      @NotNull Mapper<O, Chainable<? extends R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachTry(
      @NotNull Handler<O, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachTry(int minBatchSize, @NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachTry(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachTry(@NotNull Mapper<O, R> mapper, int maxBatchSize);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachTrySorted(
      @NotNull Handler<O, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachTrySpread(@NotNull Mapper<O, Iterable<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachTrySpreadTrusted(
      @NotNull Mapper<O, Chainable<? extends Iterable<R>>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachTryTrusted(
      @NotNull Mapper<O, Chainable<? extends R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachValue(
      @NotNull Handler<O, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachValue(int minBatchSize, @NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachValue(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> forEachValue(@NotNull Mapper<O, R> mapper, int maxBatchSize);

  @NotNull
  DeferredPromiseIterable<I, PromiseInspection<O>> inspectAll();

  @NotNull
  DeferredPromiseIterable<I, PromiseInspection<O>> inspectEach();

  @NotNull
  DeferredPromiseIterable<I, O> scheduleEach(@NotNull ScheduledExecutor executor);

  @NotNull
  DeferredPromiseIterable<I, O> scheduleEachSorted(@NotNull ScheduledExecutor executor);

  @NotNull
  <R, S> DeferredPromiseIterable<I, R> then(@NotNull StatefulHandler<O, R, S> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> thenAll(
      @NotNull Handler<Iterable<O>, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> thenAll(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> thenAllSorted(
      @NotNull Handler<Iterable<O>, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> PromiseIterable<R> thenAllTrusted(
      @NotNull Mapper<Iterable<O>, Chainable<? extends Iterable<R>>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> thenAllTry(
      @NotNull Handler<Iterable<O>, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> thenAllTry(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> thenAllTrySorted(
      @NotNull Handler<Iterable<O>, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> thenAllTryTrusted(
      @NotNull Mapper<Iterable<O>, Chainable<? extends Iterable<R>>> mapper);

  @NotNull
  <R, S> DeferredPromiseIterable<I, R> thenSorted(@NotNull StatefulHandler<O, R, S> handler);

  @NotNull
  <R, S extends Closeable> DeferredPromiseIterable<I, R> thenTryState(
      @NotNull StatefulHandler<O, R, S> handler);

  @NotNull
  <R, S extends Closeable> DeferredPromiseIterable<I, R> thenTryStateSorted(
      @NotNull StatefulHandler<O, R, S> handler);

  @NotNull
  DeferredPromiseIterable<I, O> whenFulfilledEach(@NotNull Observer<O> observer);

  @NotNull
  DeferredPromiseIterable<I, O> whenRejectedEach(@NotNull Observer<Throwable> observer);

  void resolve();

  @NotNull
  <R> DeferredPromise<Iterable<I>, R> then(
      @NotNull Handler<Iterable<O>, ? super Callback<R>> handler);

  @NotNull
  <R> DeferredPromise<Iterable<I>, R> then(@NotNull Mapper<Iterable<O>, R> mapper);

  @NotNull
  <R> DeferredPromise<Iterable<I>, R> thenTry(
      @NotNull Handler<Iterable<O>, ? super Callback<R>> handler);

  @NotNull
  <R> DeferredPromise<Iterable<I>, R> thenTry(@NotNull Mapper<Iterable<O>, R> mapper);
}
