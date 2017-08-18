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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dm.james.executor.ScheduledExecutor;
import dm.james.util.Backoff;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public interface PromiseIterable<O> extends Promise<Iterable<O>>, Iterable<O> {

  @NotNull
  PromiseIterable<O> all();

  @NotNull
  <R> PromiseIterable<R> all(@Nullable Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> PromiseIterable<R> all(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> allSorted(
      @Nullable Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> PromiseIterable<R> allTry(@Nullable Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> PromiseIterable<R> allTry(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> allTrySorted(
      @Nullable Handler<Iterable<O>, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  PromiseIterable<O> any();

  @NotNull
  <R> PromiseIterable<R> any(@Nullable Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> PromiseIterable<R> any(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> PromiseIterable<R> anySorted(@Nullable Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> PromiseIterable<R> anyTry(@Nullable Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> PromiseIterable<R> anyTry(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> PromiseIterable<R> anyTrySorted(@Nullable Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> PromiseIterable<R> applyAll(@NotNull Mapper<PromiseIterable<O>, PromiseIterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> applyAny(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> applyEach(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> applyEachSorted(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  PromiseIterable<O> catchAll(@NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  PromiseIterable<O> scheduleAll(@Nullable ScheduledExecutor fulfillExecutor,
      @Nullable ScheduledExecutor rejectExecutor);

  @NotNull
  PromiseIterable<O> whenFulfilled(@NotNull Observer<Iterable<O>> observer);

  @NotNull
  PromiseIterable<O> whenRejected(@NotNull Observer<Throwable> observer);

  @NotNull
  PromiseIterable<O> whenResolved(@NotNull Action action);

  @NotNull
  PromiseIterable<O> catchAny(@NotNull Mapper<Throwable, O> mapper);

  @NotNull
  PromiseIterable<O> catchEach(int minBatchSize, @NotNull Mapper<Throwable, O> mapper);

  @NotNull
  PromiseIterable<O> catchEach(@NotNull Mapper<Throwable, O> mapper);

  @NotNull
  PromiseIterable<O> catchEach(@NotNull Mapper<Throwable, O> mapper, int maxBatchSize);

  @NotNull
  <R> PromiseIterable<R> each(@Nullable Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> PromiseIterable<R> each(int minBatchSize, @NotNull Mapper<O, R> mapper);

  @NotNull
  <R> PromiseIterable<R> each(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> PromiseIterable<R> each(@NotNull Mapper<O, R> mapper, int maxBatchSize);

  @NotNull
  <R> PromiseIterable<R> eachSorted(@Nullable Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> PromiseIterable<R> eachTry(@Nullable Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> PromiseIterable<R> eachTry(int minBatchSize, @NotNull Mapper<O, R> mapper);

  @NotNull
  <R> PromiseIterable<R> eachTry(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> PromiseIterable<R> eachTry(@NotNull Mapper<O, R> mapper, int maxBatchSize);

  @NotNull
  <R> PromiseIterable<R> eachTrySorted(@Nullable Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject);

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
  PromiseIterable<O> scheduleAny(@Nullable ScheduledExecutor fulfillExecutor,
      @Nullable ScheduledExecutor rejectExecutor);

  @NotNull
  PromiseIterable<O> scheduleEach(@Nullable ScheduledExecutor fulfillExecutor,
      @Nullable ScheduledExecutor rejectExecutor);

  @NotNull
  PromiseIterable<O> scheduleEachSorted(@Nullable ScheduledExecutor fulfillExecutor,
      @Nullable ScheduledExecutor rejectExecutor);

  @NotNull
  PromiseIterable<O> scheduleOn(@NotNull ScheduledExecutor executor,
      @NotNull Backoff<ScheduledOutputs<O>> backoff);

  @NotNull
  PromiseIterable<O> scheduleOnSorted(@NotNull ScheduledExecutor executor,
      @NotNull Backoff<ScheduledOutputs<O>> backoff);

  @NotNull
  <R> PromiseIterable<R> then(@Nullable Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject,
      @Nullable Observer<? super CallbackIterable<R>> resolve);

  @NotNull
  <R, S> PromiseIterable<R> then(@NotNull StatefulHandler<O, R, S> handler);

  @NotNull
  <R> PromiseIterable<R> thenSorted(@Nullable Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject,
      @Nullable Observer<? super CallbackIterable<R>> resolve);

  @NotNull
  <R, S> PromiseIterable<R> thenSorted(@NotNull StatefulHandler<O, R, S> handler);

  @NotNull
  <R> PromiseIterable<R> thenTry(@Nullable Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject,
      @Nullable Observer<? super CallbackIterable<R>> resolve);

  @NotNull
  <R> PromiseIterable<R> thenTrySorted(@Nullable Handler<O, ? super CallbackIterable<R>> fulfill,
      @Nullable Handler<Throwable, ? super CallbackIterable<R>> reject,
      @Nullable Observer<? super CallbackIterable<R>> resolve);

  @NotNull
  <R, S extends Closeable> PromiseIterable<R> thenTryState(
      @NotNull StatefulHandler<O, R, S> handler);

  @NotNull
  <R, S extends Closeable> PromiseIterable<R> thenTryStateSorted(
      @NotNull StatefulHandler<O, R, S> handler);

  void waitComplete();

  boolean waitComplete(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  PromiseIterable<O> whenFulfilledAny(@NotNull Observer<O> observer);

  @NotNull
  PromiseIterable<O> whenFulfilledEach(@NotNull Observer<O> observer);

  @NotNull
  PromiseIterable<O> whenRejectedAny(@NotNull Observer<Throwable> observer);

  @NotNull
  PromiseIterable<O> whenRejectedEach(@NotNull Observer<Throwable> observer);

  interface CallbackIterable<O> extends Callback<O> {

    void add(O output);

    void addAll(@Nullable Iterable<O> outputs);

    void addAllDeferred(@Nullable Iterable<? extends Promise<?>> promises);

    void addAllDeferred(@NotNull Promise<? extends Iterable<O>> promise);

    void addDeferred(@NotNull Promise<O> promise);

    void addRejection(Throwable reason);

    void resolve();
  }

  interface StatefulHandler<I, O, S> {

    S create(@NotNull CallbackIterable<O> callback) throws Exception;

    S fulfill(S state, I input, @NotNull CallbackIterable<O> callback) throws Exception;

    S reject(S state, Throwable reason, @NotNull CallbackIterable<O> callback) throws Exception;

    void resolve(S state, @NotNull CallbackIterable<O> callback) throws Exception;
  }
}
