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
  AsyncLoop<V> evaluate();

  @NotNull
  AsyncLoop<V> evaluated();

  @NotNull
  AsyncLoop<V> on(@NotNull ScheduledExecutor executor);

  @NotNull
  <R> AsyncLoop<R> forEach(@NotNull Mapper<? super V, R> mapper);

  @NotNull
  AsyncLoop<V> forEachCatch(@NotNull Mapper<? super Throwable, V> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncLoop<V> forEachDo(@NotNull Observer<? super V> observer);

  @NotNull
  AsyncLoop<V> forEachDone(@NotNull Action action);

  @NotNull
  AsyncLoop<V> forEachElseDo(@NotNull Observer<? super Throwable> observer,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncLoop<V> forEachElseIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncLoop<V> forEachElseLoop(
      @NotNull Mapper<? super Throwable, ? extends Iterable<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncLoop<V> forEachElseLoopIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  <R> AsyncLoop<R> forEachIf(@NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachLoop(@NotNull Mapper<? super V, ? extends Iterable<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> forEachLoopIf(@NotNull Mapper<? super V, ? extends AsyncLoop<R>> mapper);

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
  AsyncLoop<V> orderedForEachElseIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncLoop<V> orderedForEachElseLoopIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncLoop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  <R> AsyncLoop<R> orderedForEachIf(@NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> orderedForEachLoopIf(@NotNull Mapper<? super V, ? extends AsyncLoop<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> orderedForEachTryIf(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncLoop<R> orderedForEachTryLoopIf(
      @NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends AsyncLoop<R>> mapper);

  @NotNull
  AsyncLoop<V> orderedOn(@NotNull ScheduledExecutor executor); // TODO: 04/02/2018 onOrdered?

  @NotNull
  <R, S> AsyncLoop<R> orderedTryYield(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Yielder<S, ? super V, R> yielder);

  @NotNull
  <R, S> AsyncLoop<R> orderedTryYield(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @Nullable Provider<S> init, @Nullable Mapper<S, ? extends Boolean> loop,
      @Nullable YieldUpdater<S, ? super V, ? super YieldResults<R>> value,
      @Nullable YieldUpdater<S, ? super Throwable, ? super YieldResults<R>> failure,
      @Nullable YieldCompleter<S, ? super YieldResults<R>> done);

  @NotNull
  <R, S> AsyncLoop<R> orderedYield(@NotNull Yielder<S, ? super V, R> yielder);

  @NotNull
  <R, S> AsyncLoop<R> orderedYield(@Nullable Provider<S> init,
      @Nullable Mapper<S, ? extends Boolean> loop,
      @Nullable YieldUpdater<S, ? super V, ? super YieldResults<R>> value,
      @Nullable YieldUpdater<S, ? super Throwable, ? super YieldResults<R>> failure,
      @Nullable YieldCompleter<S, ? super YieldResults<R>> done);

  @NotNull
  AsyncLoop<V> parallelOn(@NotNull ScheduledExecutor executor); // TODO: 04/02/2018 onParallel?

  @NotNull
  Generator<AsyncState<V>> stateGenerator();

  @NotNull
  Generator<AsyncState<V>> stateGenerator(long timeout, @NotNull TimeUnit timeUnit);

  void to(@NotNull AsyncResultCollection<? super V> results);

  @NotNull
  <R, S> AsyncLoop<R> tryYield(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Yielder<S, ? super V, R> yielder); // TODO: 04/02/2018 remove?

  @NotNull
  <R, S> AsyncLoop<R> tryYield(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @Nullable Provider<S> init, @Nullable Mapper<S, ? extends Boolean> loop,
      @Nullable YieldUpdater<S, ? super V, ? super YieldResults<R>> value,
      @Nullable YieldUpdater<S, ? super Throwable, ? super YieldResults<R>> failure,
      @Nullable YieldCompleter<S, ? super YieldResults<R>> done);

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

  interface Backoffer<S, V> {

    void done(S stack, @NotNull PendingState state);

    S failure(S stack, @NotNull Throwable failure, @NotNull PendingState state);

    S init();

    S value(S stack, V value, @NotNull PendingState state);
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

    void complete(S stack, @NotNull R state);
  }

  interface YieldResults<V> {

    @NotNull
    YieldResults<V> yieldFailure(@NotNull Throwable failure);

    @NotNull
    YieldResults<V> yieldFailures(@Nullable Iterable<Throwable> failures);

    @NotNull
    YieldResults<V> yieldIf(@NotNull AsyncStatement<V> statement);

    @NotNull
    YieldResults<V> yieldLoop(@NotNull AsyncLoop<V> loop);

    @NotNull
    YieldResults<V> yieldValue(V value);

    @NotNull
    YieldResults<V> yieldValues(@Nullable Iterable<V> value);
  }

  interface YieldUpdater<S, V, R> {

    S update(S stack, V value, @NotNull R state);
  }

  interface Yielder<S, V, R> {

    void done(S stack, @NotNull YieldResults<R> results);

    S failure(S stack, @NotNull Throwable failure, @NotNull YieldResults<R> results);

    S init();

    boolean loop(S stack);

    S value(S stack, V value, @NotNull YieldResults<R> results);
  }
}
