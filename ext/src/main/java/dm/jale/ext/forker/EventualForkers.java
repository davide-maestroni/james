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

package dm.jale.ext.forker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import dm.jale.eventual.Evaluation;
import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.Loop;
import dm.jale.eventual.LoopForker;
import dm.jale.eventual.Provider;
import dm.jale.eventual.Settler;
import dm.jale.eventual.Statement;
import dm.jale.eventual.Statement.Forker;
import dm.jale.eventual.StatementForker;
import dm.jale.eventual.Updater;
import dm.jale.ext.backoff.Backoffer;
import dm.jale.ext.backoff.PendingEvaluation;
import dm.jale.ext.eventual.BiMapper;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/28/2018.
 */
public class EventualForkers {

  private EventualForkers() {
    ConstantConditions.avoid();
  }

  @NotNull
  public static <V> StatementForker<?, V> repeat() {
    return new RepeatForker<V>();
  }

  @NotNull
  public static <V> StatementForker<?, V> repeat(final int maxTimes) {
    return new RepeatForker<V>(maxTimes);
  }

  @NotNull
  public static <V> StatementForker<?, V> repeatAfter(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return new RepeatAfterForker<V>(timeout, timeUnit);
  }

  @NotNull
  public static <V> StatementForker<?, V> repeatAfter(final long timeout,
      @NotNull final TimeUnit timeUnit, final int maxTimes) {
    return new RepeatAfterForker<V>(timeout, timeUnit, maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> repeatAll() {
    return new RepeatAllForker<V>();
  }

  @NotNull
  public static <V> LoopForker<?, V> repeatAll(final int maxTimes) {
    return new RepeatAllForker<V>(maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> repeatAllAfter(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return new RepeatAllAfterForker<V>(timeout, timeUnit);
  }

  @NotNull
  public static <V> LoopForker<?, V> repeatAllAfter(final long timeout,
      @NotNull final TimeUnit timeUnit, final int maxTimes) {
    return new RepeatAllAfterForker<V>(timeout, timeUnit, maxTimes);
  }

  @NotNull
  public static <V> StatementForker<?, V> replay() {
    return new ReplayForker<V>();
  }

  @NotNull
  public static <V> StatementForker<?, V> replay(final int maxTimes) {
    return new ReplayForker<V>(maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayAll() {
    return new ReplayAllForker<V>();
  }

  @NotNull
  public static <V> LoopForker<?, V> replayAll(final int maxTimes) {
    return new ReplayAllForker<V>(maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayFirst(final int maxCount) {
    return new ReplayFirstForker<V>(maxCount);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayFirst(final int maxCount, final int maxTimes) {
    return new ReplayFirstForker<V>(maxCount, maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayLast(final int maxCount) {
    return new ReplayLastForker<V>(maxCount);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayLast(final int maxCount, final int maxTimes) {
    return new ReplayLastForker<V>(maxCount, maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> replaySince(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return new ReplaySinceForker<V>(timeout, timeUnit);
  }

  @NotNull
  public static <V> LoopForker<?, V> replaySince(final long timeout,
      @NotNull final TimeUnit timeUnit, final int maxTimes) {
    return new ReplaySinceForker<V>(timeout, timeUnit, maxTimes);
  }

  @NotNull
  public static <V> Forker<?, V, Evaluation<V>, Statement<V>> retry(final int maxCount) {
    return RetryForker.newForker(maxCount);
  }

  @NotNull
  public static <S, V> Forker<?, V, Evaluation<V>, Statement<V>> retry(
      @NotNull final BiMapper<S, ? super Throwable, ? extends Statement<S>> mapper) {
    return RetryMapperForker.newForker(mapper);
  }

  @NotNull
  public static <S, V> Forker<?, V, EvaluationCollection<V>, Loop<V>> withBackoff(
      @NotNull final Backoffer<S, V> backoffer, @NotNull final Executor executor) {
    return BackoffForker.newForker(backoffer, executor);
  }

  @NotNull
  public static <S, V> Forker<?, V, EvaluationCollection<V>, Loop<V>> withBackoff(
      @Nullable final Provider<S> init,
      @Nullable final Updater<S, ? super V, ? super PendingEvaluation<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super PendingEvaluation<V>> failure,
      @Nullable final Settler<S, ? super PendingEvaluation<V>> done,
      @NotNull final Executor executor) {
    return withBackoff(new ComposedBackoffer<S, V>(init, value, failure, done), executor);
  }
}
