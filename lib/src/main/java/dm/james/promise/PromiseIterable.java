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

import java.io.Closeable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dm.james.executor.ScheduledExecutor;
import dm.james.util.Backoff;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public interface PromiseIterable<O>
    extends Promise<Iterable<O>>, ChainableIterable<O>, Iterable<O> {

  @NotNull
  <R> PromiseIterable<R> applyAll(@NotNull Mapper<PromiseIterable<O>, PromiseIterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> applyEach(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> applyEachSorted(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  PromiseIterable<O> backoffEach(@NotNull ScheduledExecutor executor,
      @NotNull Backoff<ScheduledData<O>> backoff);

  @NotNull
  PromiseIterable<O> catchAll(@NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  PromiseIterable<O> catchAll(@NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  PromiseIterable<O> catchAllFlat(@NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Chainable<? extends Iterable<O>>> mapper);

  @NotNull
  PromiseIterable<O> catchAllFlat(
      @NotNull Mapper<Throwable, Chainable<? extends Iterable<O>>> mapper);

  @NotNull
  Promise<PromiseInspection<Iterable<O>>> inspect();

  @NotNull
  PromiseIterable<O> renew();

  @NotNull
  PromiseIterable<O> scheduleAll(@NotNull ScheduledExecutor executor);

  @NotNull
  PromiseIterable<O> onFulfill(@NotNull Observer<Iterable<O>> observer);

  @NotNull
  PromiseIterable<O> onReject(@NotNull Observer<Throwable> observer);

  @NotNull
  PromiseIterable<O> onResolve(@NotNull Action action);

  @NotNull
  PromiseIterable<O> catchEach(@NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, O> mapper);

  @NotNull
  PromiseIterable<O> catchEach(@NotNull Mapper<Throwable, O> mapper);

  @NotNull
  PromiseIterable<O> catchEachSpread(@NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  PromiseIterable<O> catchEachSpread(@NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  PromiseIterable<O> catchEachSpreadTrusted(@NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Chainable<? extends Iterable<O>>> mapper);

  @NotNull
  PromiseIterable<O> catchEachSpreadTrusted(
      @NotNull Mapper<Throwable, Chainable<? extends Iterable<O>>> mapper);

  @NotNull
  PromiseIterable<O> catchEachTrusted(@NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Chainable<? extends O>> mapper);

  @NotNull
  PromiseIterable<O> catchEachTrusted(@NotNull Mapper<Throwable, Chainable<? extends O>> mapper);

  @NotNull
  <R> PromiseIterable<R> forEachSorted(@NotNull Handler<O, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> PromiseIterable<R> forEachSpread(@NotNull Mapper<O, Iterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> forEachSpreadTrusted(
      @NotNull Mapper<O, Chainable<? extends Iterable<R>>> mapper);

  @NotNull
  <R> PromiseIterable<R> forEachTrusted(@NotNull Mapper<O, Chainable<? extends R>> mapper);

  @NotNull
  <R> PromiseIterable<R> forEachTry(@NotNull Handler<O, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> PromiseIterable<R> forEachTry(int minBatchSize, @NotNull Mapper<O, R> mapper);

  @NotNull
  <R> PromiseIterable<R> forEachTry(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> PromiseIterable<R> forEachTry(@NotNull Mapper<O, R> mapper, int maxBatchSize);

  @NotNull
  <R> PromiseIterable<R> forEachTrySorted(@NotNull Handler<O, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> PromiseIterable<R> forEachTrySpread(@NotNull Mapper<O, Iterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> forEachTrySpreadTrusted(
      @NotNull Mapper<O, Chainable<? extends Iterable<R>>> mapper);

  @NotNull
  <R> PromiseIterable<R> forEachTryTrusted(@NotNull Mapper<O, Chainable<? extends R>> mapper);

  @NotNull
  <R> PromiseIterable<R> forEachValue(@NotNull Handler<O, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> PromiseIterable<R> forEachValue(int minBatchSize, @NotNull Mapper<O, R> mapper);

  @NotNull
  <R> PromiseIterable<R> forEachValue(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> PromiseIterable<R> forEachValue(@NotNull Mapper<O, R> mapper, int maxBatchSize);

  @NotNull
  List<O> get(int maxSize);

  @NotNull
  List<O> get(int maxSize, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<O> getAll();

  @NotNull
  List<O> getAll(long timeout, @NotNull TimeUnit timeUnit);

  O getAny();

  O getAny(long timeout, @NotNull TimeUnit timeUnit);

  O getAnyOr(O other, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  PromiseIterable<PromiseInspection<O>> inspectAll();

  @NotNull
  PromiseIterable<PromiseInspection<O>> inspectEach();

  boolean isSettled();

  @NotNull
  Iterator<O> iterator(long timeout, @NotNull TimeUnit timeUnit);

  O remove();

  @NotNull
  List<O> remove(int maxSize);

  @NotNull
  List<O> remove(int maxSize, long timeout, @NotNull TimeUnit timeUnit);

  O remove(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<O> removeAll();

  @NotNull
  List<O> removeAll(long timeout, @NotNull TimeUnit timeUnit);

  O removeOr(O other, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  PromiseIterable<O> scheduleEach(@NotNull ScheduledExecutor executor);

  @NotNull
  PromiseIterable<O> scheduleEachSorted(@NotNull ScheduledExecutor executor);

  @NotNull
  <R, S> PromiseIterable<R> then(@NotNull StatefulHandler<O, R, S> handler);

  @NotNull
  <R> PromiseIterable<R> thenAll(
      @NotNull Handler<Iterable<O>, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> PromiseIterable<R> thenAll(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> thenAllSorted(
      @NotNull Handler<Iterable<O>, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> PromiseIterable<R> thenAllTrusted(
      @NotNull Mapper<Iterable<O>, Chainable<? extends Iterable<R>>> mapper);

  @NotNull
  <R> PromiseIterable<R> thenAllTry(
      @NotNull Handler<Iterable<O>, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> PromiseIterable<R> thenAllTry(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> thenAllTrySorted(
      @NotNull Handler<Iterable<O>, ? super CallbackIterable<R>> handler);

  @NotNull
  <R> PromiseIterable<R> thenAllTryTrusted(
      @NotNull Mapper<Iterable<O>, Chainable<? extends Iterable<R>>> mapper);

  @NotNull
  <R, S> PromiseIterable<R> thenSorted(@NotNull StatefulHandler<O, R, S> handler);

  @NotNull
  <R, S extends Closeable> PromiseIterable<R> thenTryState(
      @NotNull StatefulHandler<O, R, S> handler);

  @NotNull
  <R, S extends Closeable> PromiseIterable<R> thenTryStateSorted(
      @NotNull StatefulHandler<O, R, S> handler);

  void waitSettled();

  boolean waitSettled(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  PromiseIterable<O> whenFulfilledEach(@NotNull Observer<O> observer);

  @NotNull
  PromiseIterable<O> whenRejectedEach(@NotNull Observer<Throwable> observer);
}
