/*
 * Copyright 2018 Davide Maestroni
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

package dm.jail.async;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dm.jail.executor.ScheduledExecutor;

/**
 * Created by davide-maestroni on 01/08/2018.
 */
public interface AsyncLoop<V> extends AsyncStatement<Iterable<V>>, Serializable {

  @NotNull
  <R> AsyncLoop<R> forEach(@NotNull Mapper<V, R> mapper);

  @NotNull
  AsyncLoop<V> forEachCatch(@NotNull Mapper<Throwable, V> mapper);

  @NotNull
  AsyncLoop<V> forEachDo(@NotNull Observer<V> observer);

  @NotNull
  AsyncLoop<V> forEachElseDo(@NotNull Observer<Throwable> observer);

  @NotNull
  AsyncLoop<V> forEachElseIf(@NotNull Mapper<Throwable, AsyncStatement<V>> mapper);

  @NotNull
  AsyncLoop<V> forEachElseLoop(@NotNull Mapper<Throwable, Iterable<V>> mapper);

  @NotNull
  AsyncLoop<V> forEachElseLoopIf(@NotNull Mapper<Throwable, AsyncLoop<V>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachIf(@NotNull Mapper<V, AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachLoop(@NotNull Mapper<V, Iterable<R>> mapper);
  // TODO: 16/01/2018 ordered?

  @NotNull
  <R> AsyncLoop<R> forEachLoopIf(@NotNull Mapper<V, AsyncLoop<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachTry(@NotNull Mapper<V, Closeable> closeable,
      @NotNull Mapper<V, R> mapper);
  // TODO: 16/01/2018 ordered?

  @NotNull
  AsyncLoop<V> forEachTryDo(@NotNull Mapper<V, Closeable> closeable, @NotNull Observer<V> observer);
  // TODO: 16/01/2018 ordered?

  @NotNull
  <R> AsyncLoop<R> forEachTryIf(@NotNull Mapper<V, Closeable> closeable,
      @NotNull Mapper<V, AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachTryLoop(@NotNull Mapper<V, Closeable> closeable,
      @NotNull Mapper<V, Iterable<R>> mapper);
  // TODO: 16/01/2018 ordered?

  @NotNull
  <R> AsyncLoop<R> forEachTryLoopIf(@NotNull Mapper<V, Closeable> closeable,
      @NotNull Mapper<V, AsyncLoop<R>> mapper);

  @NotNull
  <R, S> AsyncLoop<R> forEachTryYield(@NotNull Mapper<V, Closeable> closeable,
      @NotNull Looper<S, V, R> looper);

  @NotNull
  <R, S> AsyncLoop<R> forEachTryYield(@NotNull Mapper<V, Closeable> closeable,
      @Nullable Provider<S> init, @Nullable Mapper<S, Boolean> loop,
      @Nullable LoopUpdater<S, V, R> value, @Nullable LoopUpdater<S, Throwable, R> failure,
      @Nullable LoopCompleter<S, R> complete);
  // TODO: 16/01/2018 ordered?

  @NotNull
  <R, S> AsyncLoop<R> forEachYield(@NotNull Looper<S, V, R> looper);

  @NotNull
  <R, S> AsyncLoop<R> forEachYield(@Nullable Provider<S> init, @Nullable Mapper<S, Boolean> loop,
      @Nullable LoopUpdater<S, V, R> value, @Nullable LoopUpdater<S, Throwable, R> failure,
      @Nullable LoopCompleter<S, R> complete);
  // TODO: 16/01/2018 ordered?

  @NotNull
  <S> AsyncLoop<V> fork(
      @NotNull Forker<S, ? super AsyncStatement<Iterable<V>>, ? super Iterable<V>, ? extends
          Iterable<V>> forker);
  // TODO: 16/01/2018 ordered?

  @NotNull
  <S> AsyncLoop<V> fork(@Nullable Mapper<? super AsyncStatement<Iterable<V>>, S> init,
      @Nullable ForkUpdater<S, ? super AsyncStatement<Iterable<V>>, ? super Iterable<V>> value,
      @Nullable ForkUpdater<S, ? super AsyncStatement<Iterable<V>>, ? super Throwable> failure,
      @Nullable ForkUpdater<S, ? super AsyncStatement<Iterable<V>>, ? super AsyncResult<?
                extends Iterable<V>>> statement,
      @Nullable ForkUpdater<S, ? super AsyncStatement<Iterable<V>>, ? super
                AsyncResultCollection<? extends Iterable<V>>> loop);
  // TODO: 16/01/2018 ordered?

  @NotNull
  AsyncLoop<V> on(@NotNull ScheduledExecutor executor);
  // TODO: 16/01/2018 ordered?

  @NotNull
  AsyncLoop<V> renew();
  // TODO: 16/01/2018 ordered?

  @NotNull
  AsyncLoop<V> parallelOn(@NotNull ScheduledExecutor executor);

  @NotNull
  AsyncLoop<V> parallelOn(@NotNull ScheduledExecutor executor, int minBatch, int maxBatch);

  @NotNull
  Iterator<AsyncState<V>> stateIterator();

  @NotNull
  Iterator<AsyncState<V>> stateIterator(long timeout, @NotNull TimeUnit timeUnit);

  AsyncState<V> takeState();

  AsyncState<V> takeState(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<AsyncState<V>> takeStates(int maxSize);

  @NotNull
  List<AsyncState<V>> takeStates(int maxSize, long timeout, @NotNull TimeUnit timeUnit);

  V takeValue();

  V takeValue(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<V> takeValues(int maxSize);

  @NotNull
  List<V> takeValues(int maxSize, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  Iterator<V> valueIterator();

  @NotNull
  Iterator<V> valueIterator(long timeout, @NotNull TimeUnit timeUnit);

  interface Generator<V> {

    @NotNull
    Generator<V> yieldFailure(Throwable failure);

    @NotNull
    Generator<V> yieldFailures(@Nullable Iterable<Throwable> failures);

    @NotNull
    Generator<V> yieldIf(@NotNull AsyncStatement<V> statement);

    @NotNull
    Generator<V> yieldLoop(@NotNull AsyncLoop<V> loop);

    @NotNull
    Generator<V> yieldValue(V value);

    @NotNull
    Generator<V> yieldValues(@Nullable Iterable<V> value);
  }

  interface LoopCompleter<S, R> {

    void complete(S stack, Generator<R> generator);
  }

  interface LoopUpdater<S, V, R> {

    S update(S stack, V value, Generator<R> generator);
  }

  interface Looper<S, V, R> {

    void complete(S stack, Generator<R> generator);

    S failure(S stack, Throwable failure, Generator<R> generator);

    S init();

    boolean loop(S stack);

    S value(S stack, V value, Generator<R> generator);
  }
}
