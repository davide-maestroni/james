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
  <R> DeferredPromiseIterable<I, R> all(
      @NotNull Handler<Iterable<O>, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> all(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allSorted(
      @NotNull Handler<Iterable<O>, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> PromiseIterable<R> allTrusted(
      @NotNull Mapper<Iterable<O>, Chainable<? extends Iterable<R>>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allTry(
      @NotNull Handler<Iterable<O>, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allTry(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allTrySorted(
      @NotNull Handler<Iterable<O>, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allTryTrusted(
      @NotNull Mapper<Iterable<O>, Chainable<? extends Iterable<R>>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> applyAll(
      @NotNull Mapper<PromiseIterable<O>, PromiseIterable<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> applyEach(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> applyEachSorted(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> backoffOn(@NotNull ScheduledExecutor executor,
      @NotNull Backoff<ScheduledData<O>> backoff);

  @NotNull
  DeferredPromiseIterable<I, O> catchAll(@NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchAll(@NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchAllTrusted(
      @NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Chainable<? extends Iterable<O>>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchAllTrusted(
      @NotNull Mapper<Throwable, Chainable<? extends Iterable<O>>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> scheduleAll(@NotNull ScheduledExecutor executor);

  @NotNull
  DeferredPromiseIterable<I, O> whenFulfilled(@NotNull Observer<Iterable<O>> observer);

  @NotNull
  DeferredPromiseIterable<I, O> whenRejected(@NotNull Observer<Throwable> observer);

  @NotNull
  DeferredPromiseIterable<I, O> whenResolved(@NotNull Action action);

  @NotNull
  DeferredPromiseIterable<I, O> catchEach(@NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, O> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchEach(@NotNull Mapper<Throwable, O> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchEachTrusted(
      @NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Chainable<? extends O>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchEachTrusted(
      @NotNull Mapper<Throwable, Chainable<? extends O>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> each(@NotNull Handler<O, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> each(int minBatchSize, @NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> each(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> each(@NotNull Mapper<O, R> mapper, int maxBatchSize);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachSorted(
      @NotNull Handler<O, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTrusted(@NotNull Mapper<O, Chainable<? extends R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTry(
      @NotNull Handler<O, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTry(int minBatchSize, @NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTry(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTry(@NotNull Mapper<O, R> mapper, int maxBatchSize);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTrySorted(
      @NotNull Handler<O, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTryTrusted(
      @NotNull Mapper<O, Chainable<? extends R>> mapper);

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
