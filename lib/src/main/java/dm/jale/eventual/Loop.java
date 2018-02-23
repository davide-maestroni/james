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

package dm.jale.eventual;

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
public interface Loop<V> extends Statement<Iterable<V>>, Serializable {

  @NotNull
  Loop<V> elseCatch(
      @NotNull dm.jale.eventual.Mapper<? super Throwable, ? extends Iterable<V>> mapper,
      @Nullable Class<?>[] exceptionTypes);

  @NotNull
  Loop<V> elseDo(@NotNull Observer<? super Throwable> observer,
      @Nullable Class<?>[] exceptionTypes);

  @NotNull
  Loop<V> elseIf(
      @NotNull dm.jale.eventual.Mapper<? super Throwable, ? extends Statement<? extends
          Iterable<V>>> mapper,
      @Nullable Class<?>[] exceptionTypes);

  @NotNull
  Loop<V> evaluate();

  @NotNull
  Loop<V> evaluated();

  @NotNull
  <S> Loop<V> fork(
      @NotNull Forker<S, ? super Iterable<V>, ? super Evaluation<Iterable<V>>, ? super
          Statement<Iterable<V>>> forker);

  @NotNull
  <S> Loop<V> fork(@Nullable dm.jale.eventual.Mapper<? super Statement<Iterable<V>>, S> init,
      @Nullable Updater<S, ? super Iterable<V>, ? super Statement<Iterable<V>>> value,
      @Nullable Updater<S, ? super Throwable, ? super Statement<Iterable<V>>> failure,
      @Nullable dm.jale.eventual.Completer<S, ? super Statement<Iterable<V>>> done,
      @Nullable Updater<S, ? super Evaluation<Iterable<V>>, ? super Statement<Iterable<V>>>
          evaluation);

  @NotNull
  Loop<V> forkOn(@NotNull Executor executor);

  @NotNull
  Loop<V> elseForEach(@NotNull dm.jale.eventual.Mapper<? super Throwable, V> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseForEachDo(@NotNull Observer<? super Throwable> observer,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseForEachIf(
      @NotNull dm.jale.eventual.Mapper<? super Throwable, ? extends Statement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseForEachLoop(
      @NotNull dm.jale.eventual.Mapper<? super Throwable, ? extends Iterable<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseForEachLoopIf(
      @NotNull dm.jale.eventual.Mapper<? super Throwable, ? extends Loop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseForEachOrderedIf(
      @NotNull dm.jale.eventual.Mapper<? super Throwable, ? extends Statement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseForEachOrderedLoopIf(
      @NotNull dm.jale.eventual.Mapper<? super Throwable, ? extends Loop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  <R> Loop<R> forEach(@NotNull dm.jale.eventual.Mapper<? super V, R> mapper);

  @NotNull
  Loop<V> forEachDo(@NotNull Observer<? super V> observer);

  @NotNull
  Loop<V> forEachDone(@NotNull Action action);

  @NotNull
  <R> Loop<R> forEachIf(@NotNull dm.jale.eventual.Mapper<? super V, ? extends Statement<R>> mapper);

  @NotNull
  <R> Loop<R> forEachLoop(
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Iterable<R>> mapper);

  @NotNull
  <R> Loop<R> forEachLoopIf(@NotNull dm.jale.eventual.Mapper<? super V, ? extends Loop<R>> mapper);

  @NotNull
  <R> Loop<R> forEachOrderedIf(
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Statement<R>> mapper);

  @NotNull
  <R> Loop<R> forEachOrderedLoopIf(
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Loop<R>> mapper);

  @NotNull
  <R> Loop<R> forEachOrderedTryIf(
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Closeable> closeable,
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Statement<R>> mapper);

  @NotNull
  <R> Loop<R> forEachOrderedTryLoopIf(
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Closeable> closeable,
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Loop<R>> mapper);

  @NotNull
  <R> Loop<R> forEachTry(@NotNull dm.jale.eventual.Mapper<? super V, ? extends Closeable> closeable,
      @NotNull dm.jale.eventual.Mapper<? super V, R> mapper);

  @NotNull
  Loop<V> forEachTryDo(@NotNull dm.jale.eventual.Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Observer<? super V> observer);

  @NotNull
  <R> Loop<R> forEachTryIf(
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Closeable> closeable,
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Statement<R>> mapper);

  @NotNull
  <R> Loop<R> forEachTryLoop(
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Closeable> closeable,
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Iterable<R>> mapper);

  @NotNull
  <R> Loop<R> forEachTryLoopIf(
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Closeable> closeable,
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Loop<R>> mapper);

  @NotNull
  <S> Loop<V> forkLoop(
      @NotNull Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>> forker);

  @NotNull
  <S> Loop<V> forkLoop(@Nullable dm.jale.eventual.Mapper<? super Loop<V>, S> init,
      @Nullable Updater<S, ? super V, ? super Loop<V>> value,
      @Nullable Updater<S, ? super Throwable, ? super Loop<V>> failure,
      @Nullable dm.jale.eventual.Completer<S, ? super Loop<V>> done,
      @Nullable Updater<S, ? super EvaluationCollection<V>, ? super Loop<V>> evaluation);

  @NotNull
  Loop<V> forkOn(@NotNull Executor executor, int maxValues, final int maxFailures);

  @NotNull
  Loop<V> forkOnParallel(@NotNull Executor executor, int maxValues, final int maxFailures);

  @NotNull
  Loop<V> forkOnParallel(@NotNull Executor executor);

  @NotNull
  Generator<dm.jale.eventual.EvaluationState<V>> generateStates();

  @NotNull
  Generator<dm.jale.eventual.EvaluationState<V>> generateStates(long timeout,
      @NotNull TimeUnit timeUnit);

  @NotNull
  Generator<V> generateValues();

  @NotNull
  Generator<V> generateValues(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<dm.jale.eventual.EvaluationState<V>> getStates(int maxCount);

  @NotNull
  List<dm.jale.eventual.EvaluationState<V>> getStates(int maxCount, long timeout,
      @NotNull TimeUnit timeUnit);

  @NotNull
  List<V> getValues(int maxCount);

  @NotNull
  List<V> getValues(int maxCount, long timeout, @NotNull TimeUnit timeUnit);

  void to(@NotNull EvaluationCollection<? super V> evaluation);

  @NotNull
  <S, R> Loop<R> yield(@NotNull Yielder<S, ? super V, R> yielder);

  @NotNull
  <S, R> Loop<R> yield(@Nullable Provider<S> init,
      @Nullable dm.jale.eventual.Mapper<S, ? extends Boolean> loop,
      @Nullable Updater<S, ? super V, ? super YieldOutputs<R>> value,
      @Nullable Updater<S, ? super Throwable, ? super YieldOutputs<R>> failure,
      @Nullable Settler<S, ? super YieldOutputs<R>> done);

  @NotNull
  <S, R> Loop<R> yieldOrdered(@NotNull Yielder<S, ? super V, R> yielder);

  @NotNull
  <S, R> Loop<R> yieldOrdered(@Nullable Provider<S> init,
      @Nullable dm.jale.eventual.Mapper<S, ? extends Boolean> loop,
      @Nullable Updater<S, ? super V, ? super YieldOutputs<R>> value,
      @Nullable Updater<S, ? super Throwable, ? super YieldOutputs<R>> failure,
      @Nullable Settler<S, ? super YieldOutputs<R>> done);

  interface Generator<V> extends Iterable<V> {

    @NotNull
    GeneratorIterator<V> iterator();
  }

  interface GeneratorIterator<V> extends Iterator<V> {

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
    YieldOutputs<V> yieldIf(@NotNull Statement<? extends V> statement);

    @NotNull
    YieldOutputs<V> yieldLoop(@NotNull Statement<? extends Iterable<V>> loop);

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
