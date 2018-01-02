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

import java.io.Serializable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dm.james.promise.Action;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.PromiseInspection;
import dm.james.promise.RejectionException;

/**
 * Created by davide-maestroni on 11/16/2017.
 */
public interface Promise<V> extends Thenable<V>, PromiseInspection<V>, Future<V>, Serializable {

  @NotNull
  <E> Iterable<E> asIterable();

  boolean cancel();

  @NotNull
  <E, R> Promise<Iterable<R>> forEach(@Nullable ThenHandler<E, R, ? super Callback<R>> handler);

  @NotNull
  <E, R> Promise<Iterable<R>> forEach(@Nullable CallbackHandler<E, R, ? super Callback<R>> fulfill,
      @Nullable CallbackHandler<Throwable, R, ? super Callback<R>> reject);

  @NotNull
  <E, R, S> Promise<Iterable<R>> forEach(
      @NotNull LoopHandler<E, R, S, ? super IterableCallback<R>> handler);

  @NotNull
  <E, R, S> Promise<Iterable<R>> forEach(@Nullable Mapper<? super IterableCallback<R>, S> create,
      @Nullable LoopFulfill<E, R, S, ? super IterableCallback<R>> fulfill,
      @Nullable LoopReject<R, S, ? super IterableCallback<R>> reject,
      @Nullable LoopResolve<R, S, ? super IterableCallback<R>> resolve,
      @Nullable Mapper<S, Boolean> test);

  @NotNull
  <R> Promise<Iterable<R>> forEachCatch(@Nullable Mapper<Throwable, R> mapper);

  @NotNull
  <R> Promise<Iterable<R>> forEachCatchOrdered(@Nullable Mapper<Throwable, R> mapper);

  @NotNull
  <E, R> Promise<Iterable<R>> forEachMap(@Nullable Mapper<E, R> mapper);

  @NotNull
  <E, R> Promise<Iterable<R>> forEachMap(@Nullable Mapper<E, R> mapper, int minBatchSize,
      int maxBatchSize);

  @NotNull
  <E, R> Promise<Iterable<R>> forEachMapOrdered(@Nullable Mapper<E, R> mapper);

  @NotNull
  <E, R> Promise<Iterable<R>> forEachMapOrdered(@Nullable Mapper<E, R> mapper, int minBatchSize,
      int maxBatchSize);

  @NotNull
  <E, R> Promise<Iterable<R>> forEachOrdered(
      @Nullable ThenHandler<E, R, ? super Callback<R>> handler);

  @NotNull
  <E, R> Promise<Iterable<R>> forEachOrdered(
      @Nullable CallbackHandler<E, R, ? super Callback<R>> fulfill,
      @Nullable CallbackHandler<Throwable, R, ? super Callback<R>> reject);

  @NotNull
  <E, R, S> Promise<Iterable<R>> forEachOrdered(
      @NotNull LoopHandler<E, R, S, ? super IterableCallback<R>> handler);

  @NotNull
  <E, R, S> Promise<Iterable<R>> forEachOrdered(
      @Nullable Mapper<? super IterableCallback<R>, S> create,
      @Nullable LoopFulfill<E, R, S, ? super IterableCallback<R>> fulfill,
      @Nullable LoopReject<R, S, ? super IterableCallback<R>> reject,
      @Nullable LoopResolve<R, S, ? super IterableCallback<R>> resolve,
      @Nullable Mapper<S, Boolean> test);

  @Nullable
  RejectionException getReason();

  @Nullable
  RejectionException getReason(long timeout, @NotNull TimeUnit unit);

  RejectionException getReasonOr(RejectionException other, long timeout, @NotNull TimeUnit unit);

  V getValue();

  V getValue(long timeout, @NotNull TimeUnit unit);

  V getValueOr(V other, long timeout, @NotNull TimeUnit unit);

  @NotNull
  Promise<PromiseInspection<V>> inspect();

  @NotNull
  <E> BlockingIterator<PromiseInspection<E>> inspectIterator();

  @NotNull
  <E> BlockingIterator<PromiseInspection<E>> inspectIterator(long timeout, @NotNull TimeUnit unit);

  boolean isChained();

  @NotNull
  <E> BlockingIterator<E> iterator();

  @NotNull
  <E> BlockingIterator<E> iterator(long timeout, @NotNull TimeUnit unit);

  @NotNull
  <E> Promise<Iterable<E>> onEachFulfill(@NotNull Observer<E> observer);

  @NotNull
  <E> Promise<Iterable<E>> onEachReject(@NotNull Observer<? super Throwable> observer);

  @NotNull
  Promise<V> onFulfill(@NotNull Observer<? super V> observer);

  @NotNull
  Promise<V> onReject(@NotNull Observer<? super Throwable> observer);

  @NotNull
  Promise<V> onResolve(@NotNull Action action);

  @NotNull
  Promise<V> renew();

  @NotNull
  <R> Promise<R> then(@Nullable ThenHandler<V, R, ? super Callback<R>> handler);

  @NotNull
  <R> Promise<R> then(@Nullable CallbackHandler<V, R, ? super Callback<R>> fulfill,
      @Nullable CallbackHandler<Throwable, R, ? super Callback<R>> reject);

  @NotNull
  Promise<V> thenCatch(@NotNull Mapper<Throwable, V> mapper);

  @NotNull
  <R> Promise<R> thenMap(@NotNull Mapper<V, R> mapper);

  @NotNull
  Promise<V> tryingState();

  @NotNull
  Promise<V> tryingStateAndValues();

  @NotNull
  Promise<V> tryingValues();

  void waitDone();

  boolean waitDone(long timeout, @NotNull TimeUnit unit);

  @NotNull
  <R, S> Promise<R> whenChained(@NotNull ChainHandler<V, R, S> handler);

  @NotNull
  <R, S> Promise<R> whenChained(@Nullable Mapper<? super Promise<V>, S> create,
      @Nullable ChainThen<V, R, S, ? super IterableCallback<R>> forEach,
      @Nullable ChainThen<V, R, S, ? super Callback<R>> then, @Nullable ChainValue<V, S> value);

  @NotNull
  <R> Promise<R> wrap(@NotNull Mapper<? super Promise<?>, ? extends Promise<?>> mapper);

  @NotNull
  <R> Promise<R> wrapOnce(@NotNull Mapper<? super Promise<V>, ? extends Promise<R>> mapper);

  interface ChainHandler<V, R, S> {

    S create(@NotNull Promise<V> promise) throws Exception;

    S forEach(S state, @NotNull Promise<V> promise, @NotNull IterableCallback<R> callback);

    S then(S state, @NotNull Promise<V> promise, @NotNull Callback<R> callback);

    S value(S state, @NotNull Promise<V> promise);
  }

  interface ChainThen<V, R, S, C extends Callback<R>> {

    S then(S state, @NotNull Promise<V> promise, @NotNull C callback);
  }

  interface ChainValue<V, S> {

    S handle(S state, @NotNull Promise<V> promise);
  }

  interface IterableCallback<V> extends Callback<V> {

    IterableCallback<V> fulfillAllAndContinue(Iterable<? extends V> values);

    IterableCallback<V> fulfillAndContinue(V value);

    IterableCallback<V> rejectAndContinue(Throwable reason);

    void resolve();

    IterableCallback<V> resolveAllAndContinue(@NotNull Iterable<? extends Thenable<?>> thenables);

    IterableCallback<V> resolveAllAndContinue(@NotNull Thenable<? extends Iterable<V>> thenable);

    IterableCallback<V> resolveAndContinue(@NotNull Thenable<? extends V> thenable);
  }

  interface LoopFulfill<V, R, S, C extends Callback<R>> {

    S fulfill(S state, V value, @NotNull C callback) throws Exception;
  }

  interface LoopHandler<V, R, S, C extends Callback<R>> {

    S create(@NotNull C callback) throws Exception;

    S fulfill(S state, V value, @NotNull C callback) throws Exception;

    S reject(S state, @NotNull Throwable reason, @NotNull C callback) throws Exception;

    void resolve(S state, @NotNull C callback) throws Exception;

    boolean test(S state) throws Exception;
  }

  interface LoopReject<R, S, C extends Callback<R>> {

    S reject(S state, @NotNull Throwable reason, @NotNull C callback) throws Exception;
  }

  interface LoopResolve<R, S, C extends Callback<R>> {

    void resolve(S state, @NotNull C callback) throws Exception;
  }

  interface ThenHandler<V, R, C extends Callback<R>> {

    void fulfill(V value, @NotNull C callback) throws Exception;

    void reject(Throwable reason, @NotNull C callback) throws Exception;
  }
}
