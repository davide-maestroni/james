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

package dm.fates.ext.yield;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

import dm.fates.eventual.EvaluationState;
import dm.fates.eventual.Loop;
import dm.fates.eventual.Loop.YieldOutputs;
import dm.fates.eventual.LoopYielder;
import dm.fates.eventual.Mapper;
import dm.fates.eventual.Observer;
import dm.fates.eventual.Provider;
import dm.fates.ext.eventual.BiMapper;
import dm.fates.ext.eventual.BiObserver;
import dm.fates.ext.eventual.KeyedValue;
import dm.fates.ext.eventual.Tester;
import dm.fates.ext.eventual.TimedState;
import dm.fates.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/28/2018.
 */
public class EventualYielders {

  private EventualYielders() {
    ConstantConditions.avoid();
  }

  @NotNull
  public static <V> LoopYielder<?, V, Boolean> allMatch(@NotNull final Tester<V> tester) {
    return new AllMatchYielder<V>(tester);
  }

  @NotNull
  public static <V> LoopYielder<?, V, Boolean> anyMatch(@NotNull final Tester<V> tester) {
    return new AnyMatchYielder<V>(tester);
  }

  @NotNull
  public static LoopYielder<?, Number, Double> averageDouble() {
    return AverageDoubleYielder.instance();
  }

  @NotNull
  public static LoopYielder<?, Number, Float> averageFloat() {
    return AverageFloatYielder.instance();
  }

  @NotNull
  public static LoopYielder<?, Number, Integer> averageInteger() {
    return AverageIntegerYielder.instance();
  }

  @NotNull
  public static LoopYielder<?, Number, Long> averageLong() {
    return AverageLongYielder.instance();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> batch(final int maxValues, final int maxFailures) {
    return new BatchYielder<V>(maxValues, maxFailures);
  }

  @NotNull
  public static <V, R> LoopYielder<?, V, R> collect(@NotNull final Provider<R> init,
      @NotNull final BiObserver<? super R, ? super V> accumulate) {
    return new CollectYielder<V, R>(init, accumulate);
  }

  @NotNull
  public static <V> LoopYielder<?, V, Integer> count() {
    return CountYielder.instance();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> delayedFailures() {
    return DelayedFailuresYielder.instance();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> distinct() {
    return DistinctYielder.instance();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> distinctBy(
      @NotNull final Comparator<? super V> comparator) {
    return new DistinctByYielder<V>(comparator);
  }

  @NotNull
  public static <V> LoopYielder<?, V, EvaluationState<V>> evaluationStates() {
    return StatesYielder.instance();
  }

  @NotNull
  public static <V> LoopYielder<?, V, TimedState<V>> evaluationStatesTimed() {
    return TimedStatesYielder.instance();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> filter(@NotNull final Tester<V> tester) {
    return new FilterYielder<V>(tester);
  }

  @NotNull
  public static <K, V> LoopYielder<?, V, KeyedValue<K, Loop<V>>> groupBy(
      @NotNull final Mapper<? super V, K> mapper) {
    return new GroupByYielder<K, V>(mapper);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> ifEmpty(
      @NotNull final Observer<? super YieldOutputs<V>> observer) {
    return new IfEmptyYielder<V>(observer);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> ifNoFailure(
      @NotNull final Observer<? super YieldOutputs<V>> observer) {
    return new IfNoFailureYielder<V>(observer);
  }

  @NotNull
  public static <V extends Comparable<? super V>> LoopYielder<?, V, V> max() {
    return MaxYielder.instance();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> maxBy(@NotNull final Comparator<? super V> comparator) {
    return new MaxByYielder<V>(comparator);
  }

  @NotNull
  public static <V extends Comparable<? super V>> LoopYielder<?, V, V> min() {
    return MinYielder.instance();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> minBy(@NotNull final Comparator<? super V> comparator) {
    return new MinByYielder<V>(comparator);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> onFailure(
      @NotNull final BiMapper<? super Throwable, ? super YieldOutputs<V>, Boolean> mapper) {
    return new OnFailureYielder<V>(mapper);
  }

  @NotNull
  public static <V, R> LoopYielder<?, V, R> onValue(
      @NotNull final BiMapper<? super V, ? super YieldOutputs<R>, Boolean> mapper) {
    return new OnValueYielder<V, R>(mapper);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> reduce(
      @NotNull final BiMapper<? super V, ? super V, ? extends V> accumulate) {
    return new ReduceYielder<V, V>(accumulate);
  }

  @NotNull
  public static <V, R> LoopYielder<?, V, R> reduce(@NotNull final Provider<R> init,
      @NotNull final BiMapper<? super R, ? super V, ? extends R> accumulate) {
    return new ReduceYielder<V, R>(init, accumulate);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> resize(final long size,
      @NotNull final EvaluationState<V> padding) {
    return new ResizeYielder<V>(size, padding);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> resizeFailures(final long size,
      @NotNull final Throwable failure) {
    return new ResizeFailuresYielder<V>(size, failure);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> resizeValues(final long size, final V padding) {
    return new ResizeValuesYielder<V>(size, padding);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipFirst(final int maxCount) {
    return new SkipFirstYielder<V>(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipFirstFailures(final int maxCount) {
    return new SkipFirstFailuresYielder<V>(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipFirstValues(final int maxCount) {
    return new SkipFirstValuesYielder<V>(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipLast(final int maxCount) {
    return new SkipLastYielder<V>(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipLastFailures(final int maxCount) {
    return new SkipLastFailuresYielder<V>(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipLastValues(final int maxCount) {
    return new SkipLastValuesYielder<V>(maxCount);
  }

  @NotNull
  public static <V extends Comparable<? super V>> LoopYielder<?, V, V> sort() {
    return SortYielder.instance();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> sortBy(@NotNull final Comparator<? super V> comparator) {
    return new SortByYielder<V>(comparator);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> stopErrorBackPropagation() {
    return StopErrorBackPropagationYielder.instance();
  }

  @NotNull
  public static LoopYielder<?, Number, Double> sumDouble() {
    return SumDoubleYielder.instance();
  }

  @NotNull
  public static LoopYielder<?, Number, Float> sumFloat() {
    return SumFloatYielder.instance();
  }

  @NotNull
  public static LoopYielder<?, Number, Integer> sumInteger() {
    return SumIntegerYielder.instance();
  }

  @NotNull
  public static LoopYielder<?, Number, Long> sumLong() {
    return SumLongYielder.instance();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> takeFirst(final int maxCount) {
    return new TakeFirstYielder<V>(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> takeFirstFailures(final int maxCount) {
    return new TakeFirstFailuresYielder<V>(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> takeFirstValues(final int maxCount) {
    return new TakeFirstValuesYielder<V>(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> takeLast(final int maxCount) {
    return new TakeLastYielder<V>(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> takeLastFailures(final int maxCount) {
    return new TakeLastFailuresYielder<V>(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> takeLastValues(final int maxCount) {
    return new TakeLastValuesYielder<V>(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> unique() {
    return UniqueYielder.instance();
  }
}
