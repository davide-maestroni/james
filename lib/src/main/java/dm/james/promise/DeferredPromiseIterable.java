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

/**
 * Created by davide-maestroni on 08/01/2017.
 */
public interface DeferredPromiseIterable<I, O>
    extends PromiseIterable<O>, ResolvableIterable<I>, DeferredPromise<Iterable<I>, Iterable<O>> {

  @NotNull
  DeferredPromiseIterable<I, O> added(I input);

  @NotNull
  DeferredPromiseIterable<I, O> addedAll(Iterable<I> input);

  @NotNull
  DeferredPromiseIterable<I, O> addedRejection(Throwable reason);

  @NotNull
  <R> DeferredPromiseIterable<I, R> all(@NotNull Handler<Iterable<O>, Iterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> all(
      @Nullable HandlerObserver<Iterable<O>, ? super CallbackIterable<R>> resolve,
      @Nullable HandlerObserver<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> DeferredPromiseIterable<I, R> all(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> all(@NotNull StatelessHandler<Iterable<O>, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allSorted(
      @Nullable HandlerObserver<Iterable<O>, ? super CallbackIterable<R>> resolve,
      @Nullable HandlerObserver<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allSorted(@NotNull StatelessHandler<Iterable<O>, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allTry(@NotNull Handler<Iterable<O>, Iterable<R>> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allTry(
      @Nullable HandlerObserver<Iterable<O>, ? super CallbackIterable<R>> resolve,
      @Nullable HandlerObserver<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allTry(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allTry(@NotNull StatelessHandler<Iterable<O>, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allTrySorted(
      @Nullable HandlerObserver<Iterable<O>, ? super CallbackIterable<R>> resolve,
      @Nullable HandlerObserver<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> DeferredPromiseIterable<I, R> allTrySorted(@NotNull StatelessHandler<Iterable<O>, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> any(@NotNull Handler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> any(
      @Nullable HandlerObserver<O, ? super CallbackIterable<R>> resolve,
      @Nullable HandlerObserver<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> DeferredPromiseIterable<I, R> any(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> any(@NotNull StatelessHandler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> anySorted(
      @Nullable HandlerObserver<O, ? super CallbackIterable<R>> resolve,
      @Nullable HandlerObserver<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> DeferredPromiseIterable<I, R> anySorted(@NotNull StatelessHandler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> anyTry(@NotNull Handler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> anyTry(
      @Nullable HandlerObserver<O, ? super CallbackIterable<R>> resolve,
      @Nullable HandlerObserver<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> DeferredPromiseIterable<I, R> anyTry(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> anyTry(@NotNull StatelessHandler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> anyTrySorted(
      @Nullable HandlerObserver<O, ? super CallbackIterable<R>> resolve,
      @Nullable HandlerObserver<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> DeferredPromiseIterable<I, R> anyTrySorted(@NotNull StatelessHandler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> applyAll(
      @NotNull Mapper<PromiseIterable<O>, PromiseIterable<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> applyAny(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> applyEach(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> applyEachSorted(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchAny(@NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> whenFulfilled(@NotNull Observer<Iterable<O>> observer);

  @NotNull
  DeferredPromiseIterable<I, O> whenRejected(@NotNull Observer<Throwable> observer);

  @NotNull
  DeferredPromiseIterable<I, O> whenResolved(@NotNull Action action);

  @NotNull
  DeferredPromiseIterable<I, O> catchEach(int minBatchSize, @NotNull Mapper<Throwable, O> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchEach(@NotNull Mapper<Throwable, O> mapper);

  @NotNull
  DeferredPromiseIterable<I, O> catchEach(@NotNull Mapper<Throwable, O> mapper, int maxBatchSize);

  @NotNull
  <R> DeferredPromiseIterable<I, R> each(@NotNull Handler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> each(
      @Nullable HandlerObserver<O, ? super CallbackIterable<R>> resolve,
      @Nullable HandlerObserver<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> DeferredPromiseIterable<I, R> each(int minBatchSize, @NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> each(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> each(@NotNull Mapper<O, R> mapper, int maxBatchSize);

  @NotNull
  <R> DeferredPromiseIterable<I, R> each(@NotNull StatelessHandler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachSorted(@NotNull Handler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachSorted(
      @Nullable HandlerObserver<O, ? super CallbackIterable<R>> resolve,
      @Nullable HandlerObserver<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachSorted(@NotNull StatelessHandler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTry(@NotNull Handler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTry(
      @Nullable HandlerObserver<O, ? super CallbackIterable<R>> resolve,
      @Nullable HandlerObserver<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTry(int minBatchSize, @NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTry(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTry(@NotNull Mapper<O, R> mapper, int maxBatchSize);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTry(@NotNull StatelessHandler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTrySorted(@NotNull Handler<O, R> handler);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTrySorted(
      @Nullable HandlerObserver<O, ? super CallbackIterable<R>> resolve,
      @Nullable HandlerObserver<Throwable, ? super CallbackIterable<R>> reject);

  @NotNull
  <R> DeferredPromiseIterable<I, R> eachTrySorted(@NotNull StatelessHandler<O, R> handler);

  @NotNull
  <R, S> DeferredPromiseIterable<I, R> then(@NotNull StatefulHandler<O, R, S> handler);

  @NotNull
  <R, S> DeferredPromiseIterable<I, R> thenSorted(@NotNull StatefulHandler<O, R, S> handler);

  @NotNull
  DeferredPromiseIterable<I, O> whenFulfilledAny(@NotNull Observer<O> observer);

  @NotNull
  DeferredPromiseIterable<I, O> whenFulfilledEach(@NotNull Observer<O> observer);

  @NotNull
  DeferredPromiseIterable<I, O> whenRejectedEach(@NotNull Observer<Throwable> observer);

  @NotNull
  DeferredPromiseIterable<I, O> rejected(Throwable reason);

  @NotNull
  DeferredPromiseIterable<I, O> resolved(Iterable<I> inputs);

  @NotNull
  <R> DeferredPromise<Iterable<I>, R> then(@NotNull Handler<Iterable<O>, R> handler);

  @NotNull
  <R> DeferredPromise<Iterable<I>, R> then(@NotNull Mapper<Iterable<O>, R> mapper);
}
