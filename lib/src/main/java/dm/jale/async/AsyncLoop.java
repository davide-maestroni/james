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

package dm.jale.async;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Created by davide-maestroni on 01/08/2018.
 */
public interface AsyncLoop<V> extends AsyncStatement<Iterable<V>>, Serializable {

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
      @NotNull Forker<S, ? super Iterable<V>, ? super AsyncEvaluation<Iterable<V>>, ? super
          AsyncStatement<Iterable<V>>> forker);

  @NotNull
  <S> AsyncLoop<V> fork(@Nullable Mapper<? super AsyncStatement<Iterable<V>>, S> init,
      @Nullable Updater<S, ? super Iterable<V>, ? super AsyncStatement<Iterable<V>>> value,
      @Nullable Updater<S, ? super Throwable, ? super AsyncStatement<Iterable<V>>> failure,
      @Nullable Completer<S, ? super AsyncStatement<Iterable<V>>> done,
      @Nullable Updater<S, ? super AsyncEvaluation<Iterable<V>>, ? super
          AsyncStatement<Iterable<V>>> evaluation);

  @NotNull
  AsyncLoop<V> forkOn(@NotNull Executor executor);

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
      @NotNull Forker<S, ? super V, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>> forker);

  @NotNull
  <S> AsyncLoop<V> forkLoop(@Nullable Mapper<? super AsyncLoop<V>, S> init,
      @Nullable Updater<S, ? super V, ? super AsyncLoop<V>> value,
      @Nullable Updater<S, ? super Throwable, ? super AsyncLoop<V>> failure,
      @Nullable Completer<S, ? super AsyncLoop<V>> done,
      @Nullable Updater<S, ? super AsyncEvaluations<V>, ? super AsyncLoop<V>> evaluation);

  @NotNull
  AsyncLoop<V> forkOn(@NotNull Executor executor, int maxValues, final int maxFailures);

  @NotNull
  AsyncLoop<V> forkOnParallel(@NotNull Executor executor, int maxValues, final int maxFailures);

  @NotNull
  AsyncLoop<V> forkOnParallel(@NotNull Executor executor);

  @NotNull
  List<AsyncState<V>> getStates(int maxCount);

  @NotNull
  List<AsyncState<V>> getStates(int maxCount, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<V> getValues(int maxCount);

  @NotNull
  List<V> getValues(int maxCount, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  AsyncGenerator<AsyncState<V>> stateGenerator();

  @NotNull
  AsyncGenerator<AsyncState<V>> stateGenerator(long timeout, @NotNull TimeUnit timeUnit);

  void to(@NotNull AsyncEvaluations<? super V> evaluations);

  @NotNull
  AsyncGenerator<V> valueGenerator();

  @NotNull
  AsyncGenerator<V> valueGenerator(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  <S, R> AsyncLoop<R> yield(@NotNull Yielder<S, ? super V, R> yielder);

  @NotNull
  <S, R> AsyncLoop<R> yield(@Nullable Provider<S> init, @Nullable Mapper<S, ? extends Boolean> loop,
      @Nullable Updater<S, ? super V, ? super YieldOutputs<R>> value,
      @Nullable Updater<S, ? super Throwable, ? super YieldOutputs<R>> failure,
      @Nullable Settler<S, ? super YieldOutputs<R>> done);

  @NotNull
  <S, R> AsyncLoop<R> yieldOrdered(@NotNull Yielder<S, ? super V, R> yielder);

  @NotNull
  <S, R> AsyncLoop<R> yieldOrdered(@Nullable Provider<S> init,
      @Nullable Mapper<S, ? extends Boolean> loop,
      @Nullable Updater<S, ? super V, ? super YieldOutputs<R>> value,
      @Nullable Updater<S, ? super Throwable, ? super YieldOutputs<R>> failure,
      @Nullable Settler<S, ? super YieldOutputs<R>> done);

  interface AsyncGenerator<V> extends Iterable<V> {

    @NotNull
    AsyncIterator<V> iterator();
  }

  interface AsyncIterator<V> extends Iterator<V> {

    @NotNull
    List<V> next(int maxCount);

    @NotNull
    List<V> next(int maxCount, long timeout, @NotNull TimeUnit timeUnit);

    int readNext(@NotNull Collection<? super V> collection, int maxCount);

    int readNext(@NotNull Collection<? super V> collection, int maxCount, long timeout,
        @NotNull TimeUnit timeUnit);
  }

  interface YieldOutputs<V> {

    @NotNull
    YieldOutputs<V> yieldFailure(@NotNull Throwable failure);

    @NotNull
    YieldOutputs<V> yieldFailures(@Nullable Iterable<Throwable> failures);

    @NotNull
    YieldOutputs<V> yieldIf(@NotNull AsyncStatement<? extends V> statement);

    @NotNull
    YieldOutputs<V> yieldLoop(@NotNull AsyncStatement<? extends Iterable<V>> loop);

    @NotNull
    YieldOutputs<V> yieldValue(V value);

    @NotNull
    YieldOutputs<V> yieldValues(@Nullable Iterable<V> value);
  }

  interface Yielder<S, V, R> {

    void done(S stack, @NotNull YieldOutputs<R> outputs) throws Exception;

    S failure(S stack, @NotNull Throwable failure, @NotNull YieldOutputs<R> outputs) throws
        Exception;

    S init() throws Exception;

    boolean loop(S stack) throws Exception;

    S value(S stack, V value, @NotNull YieldOutputs<R> outputs) throws Exception;
  }
}
