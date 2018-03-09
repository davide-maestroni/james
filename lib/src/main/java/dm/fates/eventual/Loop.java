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

package dm.fates.eventual;

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
  Loop<V> elseCatch(@NotNull Mapper<? super Throwable, ? extends Iterable<V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseDo(@NotNull Observer<? super Throwable> observer,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseEval(
      @NotNull Mapper<? super Throwable, ? extends Statement<? extends Iterable<V>>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> evaluate();

  @NotNull
  Loop<V> evaluated();

  @NotNull
  <S> Loop<V> fork(
      @NotNull Forker<S, ? super Iterable<V>, ? super Evaluation<Iterable<V>>, ? super
          Statement<Iterable<V>>> forker);

  @NotNull
  <S> Loop<V> fork(@Nullable Mapper<? super Statement<Iterable<V>>, S> init,
      @Nullable Updater<S, ? super Iterable<V>, ? super Statement<Iterable<V>>> value,
      @Nullable Updater<S, ? super Throwable, ? super Statement<Iterable<V>>> failure,
      @Nullable Completer<S, ? super Statement<Iterable<V>>> done,
      @Nullable Updater<S, ? super Evaluation<Iterable<V>>, ? super Statement<Iterable<V>>>
          evaluation);

  @NotNull
  Loop<V> forkOn(@NotNull Executor executor);

  @NotNull
  Loop<V> elseForEach(@NotNull Mapper<? super Throwable, V> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseForEachDo(@NotNull Observer<? super Throwable> observer,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseForEachEval(
      @NotNull Mapper<? super Throwable, ? extends Statement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseForEachEvalLoop(
      @NotNull Mapper<? super Throwable, ? extends Loop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseForEachEvalLoopOrdered(
      @NotNull Mapper<? super Throwable, ? extends Loop<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseForEachEvalOrdered(
      @NotNull Mapper<? super Throwable, ? extends Statement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Loop<V> elseForEachLoop(
      @NotNull Mapper<? super Throwable, ? extends Iterable<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  <R> Loop<R> eventuallyEvalLoop(@NotNull Mapper<? super Iterable<V>, ? extends Loop<R>> mapper);

  @NotNull
  <R> Loop<R> eventuallyLoop(@NotNull Mapper<? super Iterable<V>, ? extends Iterable<R>> mapper);

  @NotNull
  <R> Loop<R> forEach(@NotNull Mapper<? super V, R> mapper);

  @NotNull
  Loop<V> forEachDo(@NotNull Observer<? super V> observer);

  @NotNull
  Loop<V> forEachDone(@NotNull Action action);

  @NotNull
  <R> Loop<R> forEachEval(@NotNull Mapper<? super V, ? extends Statement<R>> mapper);

  @NotNull
  <R> Loop<R> forEachEvalLoop(@NotNull Mapper<? super V, ? extends Loop<R>> mapper);

  @NotNull
  <R> Loop<R> forEachEvalLoopOrdered(@NotNull Mapper<? super V, ? extends Loop<R>> mapper);

  @NotNull
  <R> Loop<R> forEachEvalOrdered(@NotNull Mapper<? super V, ? extends Statement<R>> mapper);

  @NotNull
  <R> Loop<R> forEachLoop(@NotNull Mapper<? super V, ? extends Iterable<R>> mapper);

  @NotNull
  <R> Loop<R> forEachTry(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, R> mapper);

  @NotNull
  Loop<V> forEachTryDo(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Observer<? super V> observer);

  @NotNull
  <R> Loop<R> forEachTryEval(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends Statement<R>> mapper);

  @NotNull
  <R> Loop<R> forEachTryEvalLoop(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends Loop<R>> mapper);

  @NotNull
  <R> Loop<R> forEachTryEvalLoopOrdered(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends Loop<R>> mapper);

  @NotNull
  <R> Loop<R> forEachTryEvalOrdered(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends Statement<R>> mapper);

  @NotNull
  <R> Loop<R> forEachTryLoop(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends Iterable<R>> mapper);

  @NotNull
  <S> Loop<V> forkLoop(
      @NotNull Forker<S, ? super V, ? super EvaluationCollection<V>, ? super Loop<V>> forker);

  @NotNull
  <S> Loop<V> forkLoop(@Nullable Mapper<? super Loop<V>, S> init,
      @Nullable Updater<S, ? super V, ? super Loop<V>> value,
      @Nullable Updater<S, ? super Throwable, ? super Loop<V>> failure,
      @Nullable Completer<S, ? super Loop<V>> done,
      @Nullable Updater<S, ? super EvaluationCollection<V>, ? super Loop<V>> evaluation);

  @NotNull
  Loop<V> forkOn(@NotNull Executor executor, int maxValues, final int maxFailures);

  @NotNull
  Loop<V> forkOnParallel(@NotNull Executor executor, int maxValues, final int maxFailures);

  @NotNull
  Loop<V> forkOnParallel(@NotNull Executor executor);

  @NotNull
  Generator<EvaluationState<V>> generateStates();

  @NotNull
  Generator<EvaluationState<V>> generateStates(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  Generator<V> generateValues();

  @NotNull
  Generator<V> generateValues(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<EvaluationState<V>> getStates(int maxCount);

  @NotNull
  List<EvaluationState<V>> getStates(int maxCount, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<V> getValues(int maxCount);

  @NotNull
  List<V> getValues(int maxCount, long timeout, @NotNull TimeUnit timeUnit);

  void to(@NotNull EvaluationCollection<? super V> evaluation);

  @NotNull
  <S, R> Loop<R> yield(@NotNull Yielder<S, ? super V, ? super YieldOutputs<R>> yielder);

  @NotNull
  <S, R> Loop<R> yield(@Nullable Provider<S> init, @Nullable Mapper<S, ? extends Boolean> loop,
      @Nullable Updater<S, ? super V, ? super YieldOutputs<R>> value,
      @Nullable Updater<S, ? super Throwable, ? super YieldOutputs<R>> failure,
      @Nullable Settler<S, ? super YieldOutputs<R>> done);

  @NotNull
  <S, R> Loop<R> yieldOrdered(@NotNull Yielder<S, ? super V, ? super YieldOutputs<R>> yielder);

  @NotNull
  <S, R> Loop<R> yieldOrdered(@Nullable Provider<S> init,
      @Nullable Mapper<S, ? extends Boolean> loop,
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
    YieldOutputs<V> yieldLoop(@NotNull Statement<? extends Iterable<? extends V>> loop);

    @NotNull
    YieldOutputs<V> yieldStatement(@NotNull Statement<? extends V> statement);

    @NotNull
    YieldOutputs<V> yieldValue(V value);

    @NotNull
    YieldOutputs<V> yieldValues(@Nullable Iterable<V> value);
  }

  interface Yielder<S, V, O> {

    void done(S stack, @NotNull O outputs) throws Exception;

    S failure(S stack, @NotNull Throwable failure, @NotNull O outputs) throws Exception;

    S init() throws Exception;

    boolean loop(S stack) throws Exception;

    S value(S stack, V value, @NotNull O outputs) throws Exception;
  }
}
