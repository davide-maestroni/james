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

  // TODO: 04/02/2018 forEachElse* => forEachCatch* (??)
  // TODO: 04/02/2018 forEachCatch => elseForEach
  // TODO: 04/02/2018 orderedForEach* => forEachOrdered

  @NotNull
  <S> AsyncLoop<V> backoffOn(@NotNull ScheduledExecutor executor,
      @NotNull Backoffer<S, V> backoffer);

  @NotNull
  <S> AsyncLoop<V> backoffOn(@NotNull ScheduledExecutor executor, @Nullable Provider<S> init,
      @Nullable YieldUpdater<S, ? super V, ? super PendingState> value,
      @Nullable YieldUpdater<S, ? super Throwable, ? super PendingState> failure,
      @Nullable YieldCompleter<S, ? super PendingState> done);

  @NotNull
  AsyncLoop<V> elseCatch(@NotNull Mapper<? super Throwable, ? extends Iterable<V>> mapper,
      @Nullable Class<?>[] exceptionTypes);

  @NotNull
  AsyncLoop<V> elseDo(@NotNull Observer<? super Throwable> observer,
      @Nullable Class<?>[] exceptionTypes);

  @NotNull
  AsyncLoop<V> elseIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncStatement<? extends Iterable<V>>> mapper,
      @Nullable Class<?>[] exceptionTypes);

  @NotNull
  AsyncLoop<V> evaluate();

  @NotNull
  AsyncLoop<V> evaluated();

  @NotNull
  <S> AsyncLoop<V> fork(
      @NotNull Forker<S, ? super AsyncStatement<Iterable<V>>, ? super Iterable<V>, ? super
          AsyncResult<Iterable<V>>> forker);

  @NotNull
  <S> AsyncLoop<V> fork(@Nullable Mapper<? super AsyncStatement<Iterable<V>>, S> init,
      @Nullable ForkUpdater<S, ? super AsyncStatement<Iterable<V>>, ? super Iterable<V>> value,
      @Nullable ForkUpdater<S, ? super AsyncStatement<Iterable<V>>, ? super Throwable> failure,
      @Nullable ForkCompleter<S, ? super AsyncStatement<Iterable<V>>> done,
      @Nullable ForkUpdater<S, ? super AsyncStatement<Iterable<V>>, ? super
          AsyncResult<Iterable<V>>> statement);

  @NotNull
  AsyncLoop<V> on(@NotNull ScheduledExecutor executor);

  @NotNull
  AsyncLoop<V> elseForEach(@NotNull Mapper<? super Throwable, V> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncLoop<V> elseForEachDo(@NotNull Observer<? super Throwable> observer,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncLoop<V> elseForEachIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncLoop<V> elseForEachLoop(
      @NotNull Mapper<? super Throwable, ? extends Iterable<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncLoop<V> elseForEachLoopIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncLoop<V> elseForEachOrderedIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncLoop<V> elseForEachOrderedLoopIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  <R> AsyncLoop<R> forEach(@NotNull Mapper<? super V, R> mapper);

  @NotNull
  AsyncLoop<V> forEachDo(@NotNull Observer<? super V> observer);

  @NotNull
  AsyncLoop<V> forEachDone(@NotNull Action action);

  @NotNull
  <R> AsyncLoop<R> forEachIf(@NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachLoop(@NotNull Mapper<? super V, ? extends Iterable<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachLoopIf(@NotNull Mapper<? super V, ? extends AsyncLoop<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachOrderedIf(@NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachOrderedLoopIf(@NotNull Mapper<? super V, ? extends AsyncLoop<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachOrderedTryIf(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachOrderedTryLoopIf(
      @NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends AsyncLoop<R>> mapper);

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
  <R> AsyncLoop<R> forEachTryLoop(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends Iterable<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachTryLoopIf(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends AsyncLoop<R>> mapper);

  @NotNull
  <S> AsyncLoop<V> forkLoop(
      @NotNull Forker<S, ? super AsyncLoop<V>, ? super V, ? super AsyncResultCollection<V>> forker);

  @NotNull
  <S> AsyncLoop<V> forkLoop(@Nullable Mapper<? super AsyncLoop<V>, S> init,
      @Nullable ForkUpdater<S, ? super AsyncLoop<V>, ? super V> value,
      @Nullable ForkUpdater<S, ? super AsyncLoop<V>, ? super Throwable> failure,
      @Nullable ForkCompleter<S, ? super AsyncLoop<V>> done,
      @Nullable ForkUpdater<S, ? super AsyncLoop<V>, ? super AsyncResultCollection<V>> statement);

  @NotNull
  AsyncLoop<V> onParallel(@NotNull ScheduledExecutor executor);

  @NotNull
  AsyncLoop<V> onParallelOrdered(@NotNull ScheduledExecutor executor);

  @NotNull
  Generator<AsyncState<V>> stateGenerator();

  @NotNull
  Generator<AsyncState<V>> stateGenerator(long timeout, @NotNull TimeUnit timeUnit);

  void to(@NotNull AsyncResultCollection<? super V> results);

  @NotNull
  Generator<V> valueGenerator();

  @NotNull
  Generator<V> valueGenerator(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  <R, S> AsyncLoop<R> yield(@NotNull Yielder<S, ? super V, R> yielder);

  @NotNull
  <R, S> AsyncLoop<R> yield(@Nullable Provider<S> init, @Nullable Mapper<S, ? extends Boolean> loop,
      @Nullable YieldUpdater<S, ? super V, ? super YieldResults<R>> value,
      @Nullable YieldUpdater<S, ? super Throwable, ? super YieldResults<R>> failure,
      @Nullable YieldCompleter<S, ? super YieldResults<R>> done);

  @NotNull
  <R, S> AsyncLoop<R> yieldOrdered(@NotNull Yielder<S, ? super V, R> yielder);

  @NotNull
  <R, S> AsyncLoop<R> yieldOrdered(@Nullable Provider<S> init,
      @Nullable Mapper<S, ? extends Boolean> loop,
      @Nullable YieldUpdater<S, ? super V, ? super YieldResults<R>> value,
      @Nullable YieldUpdater<S, ? super Throwable, ? super YieldResults<R>> failure,
      @Nullable YieldCompleter<S, ? super YieldResults<R>> done);

  interface Backoffer<S, V> {

    void done(S stack, @NotNull PendingState state) throws Exception;

    S failure(S stack, @NotNull Throwable failure, @NotNull PendingState state) throws Exception;

    S init() throws Exception;

    S value(S stack, V value, @NotNull PendingState state) throws Exception;
  }

  interface Generator<V> extends Iterable<V> {

    @NotNull
    GeneratorIterator<V> iterator();
  }

  interface GeneratorIterator<V> extends Iterator<V> {

    @NotNull
    List<V> next(int maxCount);
  }

  interface PendingState {

    int pendingTasks();

    int pendingValues();

    void wait(long timeout, @NotNull TimeUnit timeUnit);

    boolean waitOps(int maxCount, long timeout, @NotNull TimeUnit timeUnit);

    boolean waitValues(int maxCount, long timeout, @NotNull TimeUnit timeUnit);
  }

  interface YieldCompleter<S, R> {

    void complete(S stack, @NotNull R state) throws Exception;
  }

  interface YieldResults<V> {

    @NotNull
    YieldResults<V> yieldFailure(@NotNull Throwable failure);

    @NotNull
    YieldResults<V> yieldFailures(@Nullable Iterable<Throwable> failures);

    @NotNull
    YieldResults<V> yieldIf(@NotNull AsyncStatement<? extends V> statement);

    @NotNull
    YieldResults<V> yieldLoop(@NotNull AsyncStatement<? extends Iterable<V>> loop);

    @NotNull
    YieldResults<V> yieldValue(V value);

    @NotNull
    YieldResults<V> yieldValues(@Nullable Iterable<V> value);
  }

  interface YieldUpdater<S, V, R> {

    S update(S stack, V value, @NotNull R state) throws Exception;
  }

  interface Yielder<S, V, R> {

    void done(S stack, @NotNull YieldResults<R> results) throws Exception;

    S failure(S stack, @NotNull Throwable failure, @NotNull YieldResults<R> results) throws
        Exception;

    S init() throws Exception;

    boolean loop(S stack) throws Exception;

    S value(S stack, V value, @NotNull YieldResults<R> results) throws Exception;
  }
}
