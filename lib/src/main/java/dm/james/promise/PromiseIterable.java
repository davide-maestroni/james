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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public interface PromiseIterable<O> extends Promise<Iterable<O>>, Iterable<O> {

  @NotNull
  <R> PromiseIterable<R> all(
      @Nullable Handler<Iterable<O>, R, ? super CallbackIterable<R>> outputHandler,
      @Nullable Handler<Throwable, R, ? super CallbackIterable<R>> errorHandler);

  @NotNull
  <R> PromiseIterable<R> all(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> all(@NotNull Processor<Iterable<O>, Iterable<R>> processor);

  @NotNull
  <R> PromiseIterable<R> all(@NotNull StatelessProcessor<Iterable<O>, R> processor);

  @NotNull
  <R> PromiseIterable<R> allSorted(
      @Nullable Handler<Iterable<O>, R, ? super CallbackIterable<R>> outputHandler,
      @Nullable Handler<Throwable, R, ? super CallbackIterable<R>> errorHandler);

  @NotNull
  <R> PromiseIterable<R> allSorted(@NotNull StatelessProcessor<Iterable<O>, R> processor);

  @NotNull
  <R> PromiseIterable<R> any(@Nullable Handler<O, R, ? super CallbackIterable<R>> outputHandler,
      @Nullable Handler<Throwable, R, ? super CallbackIterable<R>> errorHandler);

  @NotNull
  <R> PromiseIterable<R> any(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> PromiseIterable<R> any(@NotNull Processor<O, R> processor);

  @NotNull
  <R> PromiseIterable<R> any(@NotNull StatelessProcessor<O, R> processor);

  @NotNull
  <R> PromiseIterable<R> anySorted(
      @Nullable Handler<O, R, ? super CallbackIterable<R>> outputHandler,
      @Nullable Handler<Throwable, R, ? super CallbackIterable<R>> errorHandler);

  @NotNull
  <R> PromiseIterable<R> anySorted(@NotNull StatelessProcessor<O, R> processor);

  @NotNull
  <R> PromiseIterable<R> applyAll(@NotNull Mapper<PromiseIterable<O>, PromiseIterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> applyAny(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> applyEach(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> applyEachSorted(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  PromiseIterable<O> catchAny(@NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  PromiseIterable<O> whenFulfilled(@NotNull Observer<Iterable<O>> observer);

  @NotNull
  PromiseIterable<O> whenRejected(@NotNull Observer<Throwable> observer);

  @NotNull
  PromiseIterable<O> whenResolved(@NotNull Action action);

  @NotNull
  PromiseIterable<O> catchEach(@NotNull Mapper<Throwable, O> mapper);

  @NotNull
  PromiseIterable<O> catchEach(@NotNull Mapper<Throwable, O> mapper, int maxBatchSize);

  @NotNull
  <R> PromiseIterable<R> each(@Nullable Handler<O, R, ? super CallbackIterable<R>> outputHandler,
      @Nullable Handler<Throwable, R, ? super CallbackIterable<R>> errorHandler);

  @NotNull
  <R> PromiseIterable<R> each(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> PromiseIterable<R> each(@NotNull Mapper<O, R> mapper, int maxBatchSize);

  @NotNull
  <R> PromiseIterable<R> each(@NotNull Processor<O, R> processor);

  @NotNull
  <R> PromiseIterable<R> each(@NotNull StatelessProcessor<O, R> processor);

  @NotNull
  <R> PromiseIterable<R> eachSorted(
      @Nullable Handler<O, R, ? super CallbackIterable<R>> outputHandler,
      @Nullable Handler<Throwable, R, ? super CallbackIterable<R>> errorHandler);

  @NotNull
  <R> PromiseIterable<R> eachSorted(@NotNull StatelessProcessor<O, R> processor);

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

  O remove(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<O> remove(int maxSize);

  @NotNull
  List<O> remove(int maxSize, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<O> removeAll();

  @NotNull
  List<O> removeAll(long timeout, @NotNull TimeUnit timeUnit);

  O removeOr(O other, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  <R, S> PromiseIterable<R> then(@NotNull StatefulProcessor<O, R, S> processor);

  @NotNull
  <R, S> PromiseIterable<R> thenSorted(@NotNull StatefulProcessor<O, R, S> processor);

  void waitCompleted();

  boolean waitCompleted(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  PromiseIterable<O> whenFulfilledAny(@NotNull Observer<O> observer);

  @NotNull
  PromiseIterable<O> whenFulfilledEach(@NotNull Observer<O> observer);

  @NotNull
  PromiseIterable<O> whenRejectedEach(@NotNull Observer<Throwable> observer);

  interface CallbackIterable<O> extends Callback<O>, ResolvableIterable<O> {

    void addAllDeferred(@NotNull Promise<? extends Iterable<O>> promise);

    void addDeferred(@NotNull Promise<O> promise);
  }

  interface StatefulProcessor<I, O, S> {

    S create(@NotNull CallbackIterable<O> callback) throws Exception;

    S fulfill(S state, I input, @NotNull CallbackIterable<O> callback) throws Exception;

    S reject(S state, Throwable reason, @NotNull CallbackIterable<O> callback) throws Exception;

    void resolve(S state, @NotNull CallbackIterable<O> callback) throws Exception;
  }

  interface StatelessProcessor<I, O> {

    void reject(Throwable reason, @NotNull CallbackIterable<O> callback) throws Exception;

    void resolve(I input, @NotNull CallbackIterable<O> callback) throws Exception;
  }
}
