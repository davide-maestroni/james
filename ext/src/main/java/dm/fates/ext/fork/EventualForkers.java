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

package dm.fates.ext.fork;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import dm.fates.eventual.Loop.Yielder;
import dm.fates.eventual.LoopForker;
import dm.fates.eventual.Statement;
import dm.fates.eventual.StatementForker;
import dm.fates.ext.backpressure.PendingOutputs;
import dm.fates.ext.eventual.BiMapper;
import dm.fates.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/28/2018.
 */
public class EventualForkers {

  private EventualForkers() {
    ConstantConditions.avoid();
  }

  @NotNull
  public static <V> StatementForker<?, V> repeat() {
    return RepeatForker.newForker();
  }

  @NotNull
  public static <V> StatementForker<?, V> repeat(final int maxTimes) {
    return RepeatForker.newForker(maxTimes);
  }

  @NotNull
  public static <V> StatementForker<?, V> repeatAfter(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return RepeatAfterForker.newForker(timeout, timeUnit);
  }

  @NotNull
  public static <V> StatementForker<?, V> repeatAfter(final long timeout,
      @NotNull final TimeUnit timeUnit, final int maxTimes) {
    return RepeatAfterForker.newForker(timeout, timeUnit, maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> repeatAll() {
    return RepeatAllForker.newForker();
  }

  @NotNull
  public static <V> LoopForker<?, V> repeatAll(final int maxTimes) {
    return RepeatAllForker.newForker(maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> repeatAllAfter(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return RepeatAllAfterForker.newForker(timeout, timeUnit);
  }

  @NotNull
  public static <V> LoopForker<?, V> repeatAllAfter(final long timeout,
      @NotNull final TimeUnit timeUnit, final int maxTimes) {
    return RepeatAllAfterForker.newForker(timeout, timeUnit, maxTimes);
  }

  @NotNull
  public static <V> StatementForker<?, V> replay() {
    return ReplayForker.newForker();
  }

  @NotNull
  public static <V> StatementForker<?, V> replay(final int maxTimes) {
    return ReplayForker.newForker(maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayAll() {
    return ReplayAllForker.newForker();
  }

  @NotNull
  public static <V> LoopForker<?, V> replayAll(final int maxTimes) {
    return ReplayAllForker.newForker(maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayFirst(final int maxCount) {
    return ReplayFirstForker.newForker(maxCount);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayFirst(final int maxCount, final int maxTimes) {
    return ReplayFirstForker.newForker(maxCount, maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayLast(final int maxCount) {
    return ReplayLastForker.newForker(maxCount);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayLast(final int maxCount, final int maxTimes) {
    return ReplayLastForker.newForker(maxCount, maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> replaySince(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return ReplaySinceForker.newForker(timeout, timeUnit);
  }

  @NotNull
  public static <V> LoopForker<?, V> replaySince(final long timeout,
      @NotNull final TimeUnit timeUnit, final int maxTimes) {
    return ReplaySinceForker.newForker(timeout, timeUnit, maxTimes);
  }

  @NotNull
  public static <V> StatementForker<?, V> retry(final int maxCount) {
    return new RetryForker<V>(maxCount);
  }

  @NotNull
  public static <S, V> StatementForker<?, V> retryIf(
      @NotNull final BiMapper<S, ? super Throwable, ? extends Statement<S>> mapper) {
    return new RetryMapperForker<S, V>(mapper, null);
  }

  @NotNull
  public static <S, V> LoopForker<?, V> withBackPressure(
      @NotNull final Yielder<S, V, ? super PendingOutputs<V>> yielder,
      @NotNull final Executor executor) {
    return new BackPressureForker<S, V>(executor, yielder);
  }
}
