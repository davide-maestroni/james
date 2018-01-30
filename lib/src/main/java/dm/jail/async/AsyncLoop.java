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
  <S> AsyncLoop<V> backoffOn(@NotNull ScheduledExecutor executor,
      @NotNull Backoffer<S, V> backoffer);

  @NotNull
  <S> AsyncLoop<V> backoffOn(@NotNull ScheduledExecutor executor, @Nullable Provider<S> init,
      @Nullable LoopUpdater<S, ? super V, ? super PendingState> value,
      @Nullable LoopUpdater<S, ? super Throwable, ? super PendingState> failure,
      @Nullable LoopCompleter<S, ? super PendingState> done);

  @NotNull
  AsyncLoop<V> evaluate();

  @NotNull
  AsyncLoop<V> on(@NotNull ScheduledExecutor executor);

  @NotNull
  <R> AsyncLoop<R> forEach(@NotNull Mapper<? super V, R> mapper);

  @NotNull
  AsyncLoop<V> forEachCatch(@NotNull Mapper<? super Throwable, V> mapper);

  @NotNull
  AsyncLoop<V> forEachDo(@NotNull Observer<? super V> observer);

  @NotNull
  AsyncLoop<V> forEachElseDo(@NotNull Observer<? super Throwable> observer);

  @NotNull
  AsyncLoop<V> forEachElseIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper);

  @NotNull
  AsyncLoop<V> forEachElseIfOrdered(
      @NotNull Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper);

  @NotNull
  AsyncLoop<V> forEachElseLoop(
      @NotNull Mapper<? super Throwable, ? extends Iterable<? extends V>> mapper);

  @NotNull
  AsyncLoop<V> forEachElseLoopIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mapper);

  @NotNull
  AsyncLoop<V> forEachElseLoopIfOrdered(
      @NotNull Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachIf(@NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachIfOrdered(@NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachLoop(@NotNull Mapper<? super V, ? extends Iterable<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachLoopIf(@NotNull Mapper<? super V, ? extends AsyncLoop<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachLoopIfOrdered(@NotNull Mapper<? super V, ? extends AsyncLoop<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachTry(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, R> mapper);

  @NotNull
  AsyncLoop<V> forEachTryDo(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Observer<? super V> observer);

  @NotNull
  <R> AsyncLoop<R> forEachTryIf(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachTryIfOrdered(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachTryLoop(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends Iterable<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachTryLoopIf(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends AsyncLoop<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachTryLoopIfOrdered(
      @NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends AsyncLoop<R>> mapper);

  @NotNull
  <S> AsyncLoop<V> forkLoop(
      @NotNull Forker<S, ? super AsyncLoop<V>, ? super Iterable<V>, ? super
          AsyncResultCollection<V>> forker);

  @NotNull
  <S> AsyncLoop<V> forkLoop(@Nullable Mapper<? super AsyncLoop<V>, S> init,
      @Nullable ForkUpdater<S, ? super AsyncLoop<V>, ? super V> value,
      @Nullable ForkUpdater<S, ? super AsyncLoop<V>, ? super Throwable> failure,
      @Nullable ForkCompleter<S, ? super AsyncLoop<V>> done,
      @Nullable ForkUpdater<S, ? super AsyncLoop<V>, ? super AsyncResultCollection<V>> statement);

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

  void to(@NotNull AsyncResultCollection<? super V> results);

  @NotNull
  <R, S> AsyncLoop<R> tryYield(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Looper<S, ? super V, R> looper);

  @NotNull
  <R, S> AsyncLoop<R> tryYield(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @Nullable Provider<S> init, @Nullable Mapper<S, ? extends Boolean> loop,
      @Nullable LoopUpdater<S, ? super V, ? super Generator<R>> value,
      @Nullable LoopUpdater<S, ? super Throwable, ? super Generator<R>> failure,
      @Nullable LoopCompleter<S, ? super Generator<R>> done);

  @NotNull
  <R, S> AsyncLoop<R> tryYieldOrdered(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Looper<S, ? super V, R> looper);

  @NotNull
  <R, S> AsyncLoop<R> tryYieldOrdered(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @Nullable Provider<S> init, @Nullable Mapper<S, ? extends Boolean> loop,
      @Nullable LoopUpdater<S, ? super V, ? super Generator<R>> value,
      @Nullable LoopUpdater<S, ? super Throwable, ? super Generator<R>> failure,
      @Nullable LoopCompleter<S, ? super Generator<R>> done);

  @NotNull
  Iterator<V> valueIterator();

  @NotNull
  Iterator<V> valueIterator(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  <R, S> AsyncLoop<R> yield(@NotNull Looper<S, ? super V, R> looper);

  @NotNull
  <R, S> AsyncLoop<R> yield(@Nullable Provider<S> init, @Nullable Mapper<S, ? extends Boolean> loop,
      @Nullable LoopUpdater<S, ? super V, ? super Generator<R>> value,
      @Nullable LoopUpdater<S, ? super Throwable, ? super Generator<R>> failure,
      @Nullable LoopCompleter<S, ? super Generator<R>> done);

  @NotNull
  <R, S> AsyncLoop<R> yieldOrdered(@NotNull Looper<S, ? super V, R> looper);

  @NotNull
  <R, S> AsyncLoop<R> yieldOrdered(@Nullable Provider<S> init,
      @Nullable Mapper<S, ? extends Boolean> loop,
      @Nullable LoopUpdater<S, ? super V, ? super Generator<R>> value,
      @Nullable LoopUpdater<S, ? super Throwable, ? super Generator<R>> failure,
      @Nullable LoopCompleter<S, ? super Generator<R>> done);

  interface Backoffer<S, V> {

    void done(S stack, @NotNull PendingState state);

    S failure(S stack, @NotNull Throwable failure, @NotNull PendingState state);

    S init();

    S value(S stack, V value, @NotNull PendingState state);
  }

  interface Generator<V> {

    @NotNull
    Generator<V> yieldFailure(@NotNull Throwable failure);

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

    void complete(S stack, @NotNull R state);
  }

  interface LoopUpdater<S, V, R> {

    S update(S stack, V value, @NotNull R state);
  }

  interface Looper<S, V, R> {

    void done(S stack, @NotNull Generator<R> generator);

    S failure(S stack, @NotNull Throwable failure, @NotNull Generator<R> generator);

    S init();

    boolean loop(S stack);

    S value(S stack, V value, @NotNull Generator<R> generator);
  }

  interface PendingState {

    int pendingOps();

    int pendingTasks();

    void wait(long time, @NotNull TimeUnit timeUnit);

    boolean waitOps(int pendingOps, long time, @NotNull TimeUnit timeUnit);

    boolean waitTasks(int pendingTasks, long time, @NotNull TimeUnit timeUnit);
  }
}
