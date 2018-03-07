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

package dm.jale.ext.join;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

import dm.jale.eventual.LoopJoiner;
import dm.jale.eventual.StatementJoiner;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 03/07/2018.
 */
public class EventualJoiners {

  private EventualJoiners() {
    ConstantConditions.avoid();
  }

  @NotNull
  public static <V> StatementJoiner<?, V, List<V>> allOf() {
    return AllOfJoiner.instance();
  }

  @NotNull
  public static <V> StatementJoiner<?, V, V> anyOf() {
    return AnyOfJoiner.instance();
  }

  @NotNull
  public static <V> LoopJoiner<?, V, V> concat() {
    return ConcatJoiner.instance();
  }

  @NotNull
  public static <V> LoopJoiner<?, V, V> first() {
    return FirstJoiner.instance();
  }

  @NotNull
  public static <V> LoopJoiner<?, V, V> firstOnly(final boolean mayInterruptIfRunning) {
    return new FirstOnlyJoiner<V>(mayInterruptIfRunning);
  }

  @NotNull
  public static <V> LoopJoiner<?, V, V> firstOrEmpty() {
    return FirstOrEmptyJoiner.instance();
  }

  @NotNull
  public static <V> LoopJoiner<?, V, V> firstOrEmptyOnly(final boolean mayInterruptIfRunning) {
    return new FirstOrEmptyOnlyJoiner<V>(mayInterruptIfRunning);
  }

  @NotNull
  public static <V> LoopJoiner<?, V, V> merge() {
    return MergeJoiner.instance();
  }

  @NotNull
  public static <V> LoopJoiner<?, Object, V> switchAll() {
    return SwitchAllJoiner.instance();
  }

  @NotNull
  public static <V> LoopJoiner<?, V, V> switchBetween() {
    return SwitchJoiner.instance();
  }

  @NotNull
  public static <V> LoopJoiner<?, Object, V> switchFirst(final int maxCount) {
    return new SwitchFirstJoiner<V>(maxCount);
  }

  @NotNull
  public static <V> LoopJoiner<?, Object, V> switchLast(final int maxCount) {
    return new SwitchLastJoiner<V>(maxCount);
  }

  @NotNull
  public static <V> LoopJoiner<?, V, V> switchOnly(final boolean mayInterruptIfRunning) {
    return new SwitchOnlyJoiner<V>(mayInterruptIfRunning);
  }

  @NotNull
  public static <V> LoopJoiner<?, Object, V> switchSince(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return new SwitchSinceJoiner<V>(timeout, timeUnit);
  }

  @NotNull
  public static <V> LoopJoiner<?, Object, V> switchWhen() {
    return SwitchWhenJoiner.instance();
  }

  @NotNull
  public static <V> LoopJoiner<?, V, List<V>> zip() {
    return ZipJoiner.instance();
  }

  @NotNull
  public static <V> LoopJoiner<?, V, List<V>> zip(@NotNull final List<V> fillers) {
    return new ZipFillJoiner<V>(fillers);
  }

  @NotNull
  public static <V> LoopJoiner<?, V, List<V>> zipStrict() {
    return ZipStrictJoiner.instance();
  }
}
