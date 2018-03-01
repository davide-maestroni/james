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

package dm.jale.ext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import dm.jale.Eventual;
import dm.jale.eventual.Evaluation;
import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.EvaluationState;
import dm.jale.eventual.JoinCompleter;
import dm.jale.eventual.JoinSettler;
import dm.jale.eventual.JoinUpdater;
import dm.jale.eventual.Joiner;
import dm.jale.eventual.Loop;
import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.Loop.Yielder;
import dm.jale.eventual.LoopForker;
import dm.jale.eventual.LoopYielder;
import dm.jale.eventual.Mapper;
import dm.jale.eventual.Observer;
import dm.jale.eventual.Provider;
import dm.jale.eventual.Settler;
import dm.jale.eventual.SimpleState;
import dm.jale.eventual.Statement;
import dm.jale.eventual.StatementForker;
import dm.jale.eventual.Updater;
import dm.jale.ext.backpressure.PendingOutputs;
import dm.jale.ext.eventual.BiMapper;
import dm.jale.ext.eventual.QuadriMapper;
import dm.jale.ext.eventual.Tester;
import dm.jale.ext.eventual.TimedState;
import dm.jale.ext.eventual.TriMapper;
import dm.jale.ext.forker.EventualForkers;
import dm.jale.ext.io.AllocationType;
import dm.jale.ext.io.Chunk;
import dm.jale.ext.yielder.EventualYielders;
import dm.jale.util.Iterables;

import static dm.jale.executor.ExecutorPool.immediateExecutor;
import static dm.jale.executor.ExecutorPool.withNoDelay;
import static dm.jale.executor.ExecutorPool.withThrottling;

/**
 * Created by davide-maestroni on 02/15/2018.
 */
public class EventualExt extends Eventual {

  // TODO: 26/02/2018 Mappers: fallbackStatement()?
  // TODO: 21/02/2018 Yielders: throttle(), throttleValues(), debounce(), groupBy()?
  // TODO: 21/02/2018 Joiners:
  // TODO: 16/02/2018 Forkers:
  // TODO: 20/02/2018 (BackPressure): dropFirst, dropLast, wait(backoff?)

  private final Eventual mEventual;

  public EventualExt() {
    mEventual = new Eventual();
  }

  private EventualExt(@NotNull final Eventual eventual) {
    mEventual = eventual;
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> accumulate(
      @NotNull final BiMapper<? super V, ? super V, ? extends V> accumulator) {
    return EventualYielders.accumulate(accumulator);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> accumulate(final V initialValue,
      @NotNull final BiMapper<? super V, ? super V, ? extends V> accumulator) {
    return EventualYielders.accumulate(initialValue, accumulator);
  }

  @NotNull
  public static LoopYielder<?, Number, Float> average() {
    return averageFloat();
  }

  @NotNull
  public static LoopYielder<?, Number, Double> averageDouble() {
    return EventualYielders.averageDouble();
  }

  @NotNull
  public static LoopYielder<?, Number, Float> averageFloat() {
    return EventualYielders.averageFloat();
  }

  @NotNull
  public static LoopYielder<?, Number, Integer> averageInteger() {
    return EventualYielders.averageInteger();
  }

  @NotNull
  public static LoopYielder<?, Number, Long> averageLong() {
    return EventualYielders.averageLong();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> batch(final int maxValues, final int maxFailures) {
    return EventualYielders.batch(maxValues, maxFailures);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> delayedFailures() {
    return EventualYielders.delayedFailures();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> distinct() {
    return EventualYielders.distinct();
  }

  @NotNull
  public static <V> LoopYielder<?, V, EvaluationState<V>> evaluationStates() {
    return EventualYielders.evaluationStates();
  }

  @NotNull
  public static <V> LoopYielder<?, V, TimedState<V>> evaluationStatesTimed() {
    return EventualYielders.evaluationStatesTimed();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> filter(@NotNull final Tester<V> tester) {
    return EventualYielders.filter(tester);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> ifEmpty(
      @NotNull final Observer<? super YieldOutputs<V>> observer) {
    return EventualYielders.ifEmpty(observer);
  }

  @NotNull
  public static <V extends Comparable<? super V>> LoopYielder<?, V, V> max() {
    return EventualYielders.max();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> maxBy(@NotNull final Comparator<? super V> comparator) {
    return EventualYielders.maxBy(comparator);
  }

  @NotNull
  public static <V extends Comparable<? super V>> LoopYielder<?, V, V> min() {
    return EventualYielders.min();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> minBy(@NotNull final Comparator<? super V> comparator) {
    return EventualYielders.minBy(comparator);
  }

  @NotNull
  public static <V> StatementForker<?, V> repeat() {
    return EventualForkers.repeat();
  }

  @NotNull
  public static <V> StatementForker<?, V> repeat(final int maxTimes) {
    return EventualForkers.repeat(maxTimes);
  }

  @NotNull
  public static <V> StatementForker<?, V> repeatAfter(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return EventualForkers.repeatAfter(timeout, timeUnit);
  }

  @NotNull
  public static <V> StatementForker<?, V> repeatAfter(final long timeout,
      @NotNull final TimeUnit timeUnit, final int maxTimes) {
    return EventualForkers.repeatAfter(timeout, timeUnit, maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> repeatAll() {
    return EventualForkers.repeatAll();
  }

  @NotNull
  public static <V> LoopForker<?, V> repeatAll(final int maxTimes) {
    return EventualForkers.repeatAll(maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> repeatAllAfter(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return EventualForkers.repeatAllAfter(timeout, timeUnit);
  }

  @NotNull
  public static <V> LoopForker<?, V> repeatAllAfter(final long timeout,
      @NotNull final TimeUnit timeUnit, final int maxTimes) {
    return EventualForkers.repeatAllAfter(timeout, timeUnit, maxTimes);
  }

  @NotNull
  public static <V> StatementForker<?, V> replay() {
    return EventualForkers.replay();
  }

  @NotNull
  public static <V> StatementForker<?, V> replay(final int maxTimes) {
    return EventualForkers.replay(maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayAll() {
    return EventualForkers.replayAll();
  }

  @NotNull
  public static <V> LoopForker<?, V> replayAll(final int maxTimes) {
    return EventualForkers.replayAll(maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayFirst(final int maxCount) {
    return EventualForkers.replayFirst(maxCount);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayFirst(final int maxCount, final int maxTimes) {
    return EventualForkers.replayFirst(maxCount, maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayLast(final int maxCount) {
    return EventualForkers.replayLast(maxCount);
  }

  @NotNull
  public static <V> LoopForker<?, V> replayLast(final int maxCount, final int maxTimes) {
    return EventualForkers.replayLast(maxCount, maxTimes);
  }

  @NotNull
  public static <V> LoopForker<?, V> replaySince(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return EventualForkers.replaySince(timeout, timeUnit);
  }

  @NotNull
  public static <V> LoopForker<?, V> replaySince(final long timeout,
      @NotNull final TimeUnit timeUnit, final int maxTimes) {
    return EventualForkers.replaySince(timeout, timeUnit, maxTimes);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> resize(final long size) {
    return resize(size, SimpleState.<V>ofValue(null));
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> resize(final long size,
      @NotNull final EvaluationState<V> padding) {
    return EventualYielders.resize(size, padding);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> resizeFailures(final long size,
      @NotNull final Throwable failure) {
    return EventualYielders.resizeFailures(size, failure);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> resizeValues(final long size) {
    return resizeValues(size, null);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> resizeValues(final long size, final V padding) {
    return EventualYielders.resizeValues(size, padding);
  }

  @NotNull
  public static <V> StatementForker<?, V> retry(final int maxCount) {
    return EventualForkers.retry(maxCount);
  }

  @NotNull
  public static <S, V> StatementForker<?, V> retryIf(
      @NotNull final BiMapper<S, ? super Throwable, ? extends Statement<S>> mapper) {
    return EventualForkers.retryIf(mapper);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skip(final int maxCount) {
    return skipFirst(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipFailures(final int maxCount) {
    return skipFirstFailures(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipFirst(final int maxCount) {
    return EventualYielders.skipFirst(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipFirstFailures(final int maxCount) {
    return EventualYielders.skipFirstFailures(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipFirstValues(final int maxCount) {
    return EventualYielders.skipFirstValues(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipLast(final int maxCount) {
    return EventualYielders.skipLast(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipLastFailures(final int maxCount) {
    return EventualYielders.skipLastFailures(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipLastValues(final int maxCount) {
    return EventualYielders.skipLastValues(maxCount);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> skipValues(final int maxCount) {
    return skipFirstValues(maxCount);
  }

  @NotNull
  public static <V extends Comparable<? super V>> LoopYielder<?, V, V> sort() {
    return EventualYielders.sort();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> sortBy(@NotNull final Comparator<? super V> comparator) {
    return EventualYielders.sortBy(comparator);
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> stopErrorBackPropagation() {
    return EventualYielders.stopErrorBackPropagation();
  }

  @NotNull
  public static LoopYielder<?, Number, Integer> sum() {
    return sumInteger();
  }

  @NotNull
  public static LoopYielder<?, Number, Double> sumDouble() {
    return EventualYielders.sumDouble();
  }

  @NotNull
  public static LoopYielder<?, Number, Float> sumFloat() {
    return EventualYielders.sumFloat();
  }

  @NotNull
  public static LoopYielder<?, Number, Integer> sumInteger() {
    return EventualYielders.sumInteger();
  }

  @NotNull
  public static LoopYielder<?, Number, Long> sumLong() {
    return EventualYielders.sumLong();
  }

  @NotNull
  public static <V> LoopYielder<?, V, V> unique() {
    return EventualYielders.unique();
  }

  @NotNull
  public static <S, V> LoopForker<?, V> withBackPressure(@NotNull final Executor executor,
      @NotNull final Yielder<S, V, ? super PendingOutputs<V>> yielder) {
    return EventualForkers.withBackPressure(executor, yielder);
  }

  @NotNull
  public static <S, V> LoopForker<?, V> withBackPressure(@NotNull final Executor executor,
      @Nullable final Provider<S> init, @Nullable final Mapper<S, ? extends Boolean> loop,
      @Nullable final Updater<S, ? super V, ? super PendingOutputs<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super PendingOutputs<V>> failure,
      @Nullable final Settler<S, ? super PendingOutputs<V>> done) {
    return EventualForkers.withBackPressure(executor,
        Eventual.yielder(init, loop, value, failure, done));
  }

  @NotNull
  public <V> Statement<List<V>> allOf(
      @NotNull final Iterable<? extends Statement<? extends V>> statements) {
    return joinStatements(AllOfJoiner.<V>instance(), statements);
  }

  @NotNull
  public <V> Statement<V> anyOf(
      @NotNull final Iterable<? extends Statement<? extends V>> statements) {
    return joinStatements(AnyOfJoiner.<V>instance(), statements);
  }

  @NotNull
  public <V> Loop<V> concat(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return joinLoops(ConcatJoiner.<V>instance(), loops);
  }

  @NotNull
  @Override
  public EventualExt evaluateOn(@Nullable final Executor executor) {
    return new EventualExt(mEventual.evaluateOn(executor));
  }

  @NotNull
  @Override
  public EventualExt evaluated(final boolean isEvaluated) {
    return new EventualExt(mEventual.evaluated(isEvaluated));
  }

  @NotNull
  @Override
  public <V> Statement<V> failure(@NotNull final Throwable failure) {
    return mEventual.failure(failure);
  }

  @NotNull
  @Override
  public <V> Loop<V> failures(@NotNull final Iterable<? extends Throwable> failures) {
    return mEventual.failures(failures);
  }

  @NotNull
  @Override
  public <S, V, R> Loop<R> joinLoops(
      @NotNull final Joiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> joiner,
      @NotNull final Iterable<? extends Loop<? extends V>> asyncLoops) {
    return mEventual.joinLoops(joiner, asyncLoops);
  }

  @NotNull
  @Override
  public <S, V, R> Loop<R> joinLoops(@Nullable final Mapper<? super List<Loop<V>>, S> init,
      @Nullable final JoinUpdater<S, ? super V, ? super EvaluationCollection<? extends R>,
          Loop<V>> value,
      @Nullable final JoinUpdater<S, ? super Throwable, ? super EvaluationCollection<? extends
          R>, Loop<V>> failure,
      @Nullable final JoinCompleter<S, ? super EvaluationCollection<? extends R>, Loop<V>> done,
      @Nullable final JoinSettler<S, ? super EvaluationCollection<? extends R>, Loop<V>> settle,
      @NotNull final Iterable<? extends Loop<? extends V>> asyncLoops) {
    return mEventual.joinLoops(init, value, failure, done, settle, asyncLoops);
  }

  @NotNull
  @Override
  public <S, V, R> Statement<R> joinStatements(
      @NotNull final Joiner<S, ? super V, ? super Evaluation<R>, Statement<V>> joiner,
      @NotNull final Iterable<? extends Statement<? extends V>> statements) {
    return mEventual.joinStatements(joiner, statements);
  }

  @NotNull
  @Override
  public <S, V, R> Statement<R> joinStatements(
      @Nullable final Mapper<? super List<Statement<V>>, S> init,
      @Nullable final JoinUpdater<S, ? super V, ? super Evaluation<? extends R>, Statement<V>>
          value,
      @Nullable final JoinUpdater<S, ? super Throwable, ? super Evaluation<? extends R>,
          Statement<V>> failure,
      @Nullable final JoinCompleter<S, ? super Evaluation<? extends R>, Statement<V>> done,
      @Nullable final JoinSettler<S, ? super Evaluation<? extends R>, Statement<V>> settle,
      @NotNull final Iterable<? extends Statement<? extends V>> statements) {
    return mEventual.joinStatements(init, value, failure, done, settle, statements);
  }

  @NotNull
  @Override
  public EventualExt loggerName(@Nullable final String loggerName) {
    return new EventualExt(mEventual.loggerName(loggerName));
  }

  @NotNull
  @Override
  public <V> Loop<V> loop(@NotNull final Statement<? extends Iterable<V>> statement) {
    return mEventual.loop(statement);
  }

  @NotNull
  @Override
  public <V> Loop<V> loop(@NotNull final Observer<EvaluationCollection<V>> observer) {
    return mEventual.loop(observer);
  }

  @NotNull
  @Override
  public <V> Loop<V> loopOnce(@NotNull final Statement<? extends V> statement) {
    return mEventual.loopOnce(statement);
  }

  @NotNull
  @Override
  public <V> Statement<V> statement(@NotNull final Observer<Evaluation<V>> observer) {
    return mEventual.statement(observer);
  }

  @NotNull
  @Override
  public <V> Statement<V> value(final V value) {
    return mEventual.value(value);
  }

  @NotNull
  @Override
  public <V> Loop<V> values(@NotNull final Iterable<? extends V> values) {
    return mEventual.values(values);
  }

  @NotNull
  public <V> Loop<V> first(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return joinLoops(FirstJoiner.<V>instance(), loops);
  }

  @NotNull
  public <V> Loop<V> firstOnly(final boolean mayInterruptIfRunning,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return joinLoops(new FirstOnlyJoiner<V>(mayInterruptIfRunning), loops);
  }

  @NotNull
  public <V> Loop<V> firstOrEmpty(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return joinLoops(FirstOrEmptyJoiner.<V>instance(), loops);
  }

  @NotNull
  public <V> Loop<V> firstOrEmptyOnly(final boolean mayInterruptIfRunning,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return joinLoops(new FirstOrEmptyOnlyJoiner<V>(mayInterruptIfRunning), loops);
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final InputStream inputStream) {
    return loopChunks(inputStream, null);
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final InputStream inputStream,
      @Nullable final AllocationType allocationType) {
    return loop(new InputStreamChunkObserver(inputStream, allocationType, null, null, null));
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final InputStream inputStream,
      @Nullable final AllocationType allocationType, final int coreSize) {
    return loop(new InputStreamChunkObserver(inputStream, allocationType, coreSize, null, null));
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final InputStream inputStream,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return loop(
        new InputStreamChunkObserver(inputStream, allocationType, null, bufferSize, poolSize));
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final ReadableByteChannel channel) {
    return loopChunks(channel, null);
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final ReadableByteChannel channel,
      @Nullable final AllocationType allocationType) {
    return loop(new ChannelChunkObserver(channel, allocationType, null, null, null));
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final ReadableByteChannel channel,
      @Nullable final AllocationType allocationType, final int coreSize) {
    return loop(new ChannelChunkObserver(channel, allocationType, coreSize, null, null));
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final ReadableByteChannel channel,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return loop(new ChannelChunkObserver(channel, allocationType, null, bufferSize, poolSize));
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final ByteBuffer buffer) {
    return loopChunks(buffer, null);
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final ByteBuffer buffer,
      @Nullable final AllocationType allocationType) {
    return loop(new ByteBufferChunkObserver(buffer, allocationType, null, null, null));
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final ByteBuffer buffer,
      @Nullable final AllocationType allocationType, final int coreSize) {
    return loop(new ByteBufferChunkObserver(buffer, allocationType, coreSize, null, null));
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final ByteBuffer buffer,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return loop(new ByteBufferChunkObserver(buffer, allocationType, null, bufferSize, poolSize));
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final byte[] buffer) {
    return loopChunks(buffer, null);
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final byte[] buffer,
      @Nullable final AllocationType allocationType) {
    return loop(new ByteArrayChunkObserver(buffer, allocationType, null, null, null));
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final byte[] buffer,
      @Nullable final AllocationType allocationType, final int coreSize) {
    return loop(new ByteArrayChunkObserver(buffer, allocationType, coreSize, null, null));
  }

  @NotNull
  public Loop<Chunk> loopChunks(@NotNull final byte[] buffer,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return loop(new ByteArrayChunkObserver(buffer, allocationType, null, bufferSize, poolSize));
  }

  @NotNull
  public <V extends Comparable<? super V>> Loop<V> loopRange(@NotNull final V startInclusive,
      @NotNull final V endExclusive, @NotNull final Mapper<? super V, ? extends V> increment) {
    return loop(new InRangeComparableObserver<V>(startInclusive, endExclusive, increment, false));
  }

  @NotNull
  public Loop<Integer> loopRange(final int startInclusive, final int endExclusive) {
    return loopRange(startInclusive, endExclusive, (startInclusive <= endExclusive) ? 1 : -1);
  }

  @NotNull
  public Loop<Integer> loopRange(final int startInclusive, final int endExclusive,
      final int increment) {
    return loop(new InRangeIntegerObserver(startInclusive, endExclusive, increment, false));
  }

  @NotNull
  public Loop<Long> loopRange(final long startInclusive, final long endExclusive) {
    return loopRange(startInclusive, endExclusive, (startInclusive <= endExclusive) ? 1 : -1);
  }

  @NotNull
  public Loop<Long> loopRange(final long startInclusive, final long endExclusive,
      final long increment) {
    return loop(new InRangeLongObserver(startInclusive, endExclusive, increment, false));
  }

  @NotNull
  public Loop<Float> loopRange(final float startInclusive, final float endExclusive) {
    return loopRange(startInclusive, endExclusive, (startInclusive <= endExclusive) ? 1 : -1);
  }

  @NotNull
  public Loop<Float> loopRange(final float startInclusive, final float endExclusive,
      final float increment) {
    return loop(new InRangeFloatObserver(startInclusive, endExclusive, increment, false));
  }

  @NotNull
  public Loop<Double> loopRange(final double startInclusive, final double endExclusive) {
    return loopRange(startInclusive, endExclusive, (startInclusive <= endExclusive) ? 1 : -1);
  }

  @NotNull
  public Loop<Double> loopRange(final double startInclusive, final double endExclusive,
      final double increment) {
    return loop(new InRangeDoubleObserver(startInclusive, endExclusive, increment, false));
  }

  @NotNull
  public <V extends Comparable<? super V>> Loop<V> loopRangeInclusive(
      @NotNull final V startInclusive, @NotNull final V endInclusive,
      @NotNull final Mapper<? super V, ? extends V> increment) {
    return loop(new InRangeComparableObserver<V>(startInclusive, endInclusive, increment, true));
  }

  @NotNull
  public Loop<Integer> loopRangeInclusive(final int startInclusive, final int endInclusive) {
    return loopRangeInclusive(startInclusive, endInclusive,
        (startInclusive <= endInclusive) ? 1 : -1);
  }

  @NotNull
  public Loop<Integer> loopRangeInclusive(final int startInclusive, final int endInclusive,
      final int increment) {
    return loop(new InRangeIntegerObserver(startInclusive, endInclusive, increment, true));
  }

  @NotNull
  public Loop<Long> loopRangeInclusive(final long startInclusive, final long endInclusive) {
    return loopRangeInclusive(startInclusive, endInclusive,
        (startInclusive <= endInclusive) ? 1 : -1);
  }

  @NotNull
  public Loop<Long> loopRangeInclusive(final long startInclusive, final long endInclusive,
      final long increment) {
    return loop(new InRangeLongObserver(startInclusive, endInclusive, increment, true));
  }

  @NotNull
  public Loop<Float> loopRangeInclusive(final float startInclusive, final float endInclusive) {
    return loopRangeInclusive(startInclusive, endInclusive,
        (startInclusive <= endInclusive) ? 1 : -1);
  }

  @NotNull
  public Loop<Float> loopRangeInclusive(final float startInclusive, final float endInclusive,
      final float increment) {
    return loop(new InRangeFloatObserver(startInclusive, endInclusive, increment, true));
  }

  @NotNull
  public Loop<Double> loopRangeInclusive(final double startInclusive, final double endInclusive) {
    return loopRangeInclusive(startInclusive, endInclusive,
        (startInclusive <= endInclusive) ? 1 : -1);
  }

  @NotNull
  public Loop<Double> loopRangeInclusive(final double startInclusive, final double endInclusive,
      final double increment) {
    return loop(new InRangeDoubleObserver(startInclusive, endInclusive, increment, true));
  }

  @NotNull
  public <V> Loop<V> loopSequence(@NotNull final V start, final long count,
      @NotNull final Mapper<? super V, ? extends V> increment) {
    return loop(new InSequenceObserver<V>(start, count, increment));
  }

  @NotNull
  public <V> Loop<V> merge(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return joinLoops(MergeJoiner.<V>instance(), loops);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <T> T proxy(@NotNull final Object object, @NotNull final Class<? extends T> type) {
    final Executor executor = getExecutor();
    return (T) Proxy.newProxyInstance(EventualExt.class.getClassLoader(), new Class<?>[]{type},
        new EventualInvocationHandler(this.evaluateOn(
            withThrottling(1, (executor != null) ? withNoDelay(executor) : immediateExecutor())),
            object));
  }

  @NotNull
  public <V> Statement<List<EvaluationState<V>>> statesOf(
      @NotNull final Iterable<? extends Statement<? extends V>> statements) {
    return joinStatements(StatesOfJoiner.<V>instance(), statements);
  }

  @NotNull
  public <V> Loop<V> switchAll(@NotNull final Loop<? extends Integer> indexes,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    final ArrayList<Loop<?>> allLoops = new ArrayList<Loop<?>>();
    allLoops.add(indexes);
    Iterables.addAll((Iterable<? extends Loop<?>>) loops, allLoops);
    return joinLoops(SwitchAllJoiner.<V>instance(), allLoops);
  }

  @NotNull
  public <V> Loop<V> switchBetween(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return joinLoops(SwitchJoiner.<V>instance(), loops);
  }

  @NotNull
  public <V> Loop<V> switchFirst(final int maxCount, @NotNull final Loop<? extends Integer> indexes,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    final ArrayList<Loop<?>> allLoops = new ArrayList<Loop<?>>();
    allLoops.add(indexes);
    Iterables.addAll((Iterable<? extends Loop<?>>) loops, allLoops);
    return joinLoops(new SwitchFirstJoiner<V>(maxCount), allLoops);
  }

  @NotNull
  public <V> Loop<V> switchLast(final int maxCount, @NotNull final Loop<? extends Integer> indexes,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    final ArrayList<Loop<?>> allLoops = new ArrayList<Loop<?>>();
    allLoops.add(indexes);
    Iterables.addAll((Iterable<? extends Loop<?>>) loops, allLoops);
    return joinLoops(new SwitchLastJoiner<V>(maxCount), allLoops);
  }

  @NotNull
  public <V> Loop<V> switchOnly(final boolean mayInterruptIfRunning,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return joinLoops(new SwitchOnlyJoiner<V>(mayInterruptIfRunning), loops);
  }

  @NotNull
  public <V> Loop<V> switchSince(final long timeout, @NotNull final TimeUnit timeUnit,
      @NotNull final Loop<? extends Integer> indexes,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    final ArrayList<Loop<?>> allLoops = new ArrayList<Loop<?>>();
    allLoops.add(indexes);
    Iterables.addAll((Iterable<? extends Loop<?>>) loops, allLoops);
    return joinLoops(new SwitchSinceJoiner<V>(timeout, timeUnit), allLoops);
  }

  @NotNull
  public <V> Loop<V> switchWhen(@NotNull final Loop<? extends Integer> indexes,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    final ArrayList<Loop<?>> allLoops = new ArrayList<Loop<?>>();
    allLoops.add(indexes);
    Iterables.addAll((Iterable<? extends Loop<?>>) loops, allLoops);
    return joinLoops(SwitchWhenJoiner.<V>instance(), allLoops);
  }

  @NotNull
  public <V> Loop<List<V>> zip(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return joinLoops(ZipJoiner.<V>instance(), loops);
  }

  @NotNull
  public <V> Loop<List<V>> zip(final V filler,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    final int size = Iterables.size(loops);
    final ArrayList<V> fillers = new ArrayList<V>(size);
    for (int i = 0; i < size; ++i) {
      fillers.add(filler);
    }

    return joinLoops(new ZipFillJoiner<V>(fillers), loops);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <V1, V2, R> Loop<R> zip(final V1 filler1, final V2 filler2,
      @NotNull final Loop<? extends V1> loop1, @NotNull final Loop<? extends V2> loop2,
      @NotNull final BiMapper<V1, V2, R> mapper) {
    return joinLoops(new ZipFillJoiner<Object>(Arrays.asList(filler1, filler2)),
        Arrays.asList(loop1, loop2)).forEach(new BiZipMapper<V1, V2, R>(mapper));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <V1, V2, R> Loop<R> zip(@NotNull final Loop<? extends V1> loop1,
      @NotNull final Loop<? extends V2> loop2, @NotNull final BiMapper<V1, V2, R> mapper) {
    return joinLoops(ZipJoiner.instance(), Arrays.asList(loop1, loop2)).forEach(
        new BiZipMapper<V1, V2, R>(mapper));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <V1, V2, V3, R> Loop<R> zip(final V1 filler1, final V2 filler2, final V3 filler3,
      @NotNull final Loop<? extends V1> loop1, @NotNull final Loop<? extends V2> loop2,
      @NotNull final Loop<? extends V3> loop3, @NotNull final TriMapper<V1, V2, V3, R> mapper) {
    return joinLoops(new ZipFillJoiner<Object>(Arrays.asList(filler1, filler2, filler3)),
        Arrays.asList(loop1, loop2, loop3)).forEach(new TriZipMapper<V1, V2, V3, R>(mapper));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <V1, V2, V3, R> Loop<R> zip(@NotNull final Loop<? extends V1> loop1,
      @NotNull final Loop<? extends V2> loop2, @NotNull final Loop<? extends V3> loop3,
      @NotNull final TriMapper<V1, V2, V3, R> mapper) {
    return joinLoops(ZipJoiner.instance(), Arrays.asList(loop1, loop2, loop3)).forEach(
        new TriZipMapper<V1, V2, V3, R>(mapper));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <V1, V2, V3, V4, R> Loop<R> zip(final V1 filler1, final V2 filler2, final V3 filler3,
      final V4 filler4, @NotNull final Loop<? extends V1> loop1,
      @NotNull final Loop<? extends V2> loop2, @NotNull final Loop<? extends V3> loop3,
      @NotNull final Loop<? extends V4> loop4,
      @NotNull final QuadriMapper<V1, V2, V3, V4, R> mapper) {
    return joinLoops(new ZipFillJoiner<Object>(Arrays.asList(filler1, filler2, filler3, filler4)),
        Arrays.asList(loop1, loop2, loop3, loop4)).forEach(
        new QuadriZipMapper<V1, V2, V3, V4, R>(mapper));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <V1, V2, V3, V4, R> Loop<R> zip(@NotNull final Loop<? extends V1> loop1,
      @NotNull final Loop<? extends V2> loop2, @NotNull final Loop<? extends V3> loop3,
      @NotNull final Loop<? extends V4> loop4,
      @NotNull final QuadriMapper<V1, V2, V3, V4, R> mapper) {
    return joinLoops(ZipJoiner.instance(), Arrays.asList(loop1, loop2, loop3, loop4)).forEach(
        new QuadriZipMapper<V1, V2, V3, V4, R>(mapper));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <V1, V2, R> Loop<R> zipStrict(@NotNull final Loop<? extends V1> loop1,
      @NotNull final Loop<? extends V2> loop2, @NotNull final BiMapper<V1, V2, R> mapper) {
    return joinLoops(ZipStrictJoiner.instance(), Arrays.asList(loop1, loop2)).forEach(
        new BiZipMapper<V1, V2, R>(mapper));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <V1, V2, V3, R> Loop<R> zipStrict(@NotNull final Loop<? extends V1> loop1,
      @NotNull final Loop<? extends V2> loop2, @NotNull final Loop<? extends V3> loop3,
      @NotNull final TriMapper<V1, V2, V3, R> mapper) {
    return joinLoops(ZipStrictJoiner.instance(), Arrays.asList(loop1, loop2, loop3)).forEach(
        new TriZipMapper<V1, V2, V3, R>(mapper));
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <V1, V2, V3, V4, R> Loop<R> zipStrict(@NotNull final Loop<? extends V1> loop1,
      @NotNull final Loop<? extends V2> loop2, @NotNull final Loop<? extends V3> loop3,
      @NotNull final Loop<? extends V4> loop4,
      @NotNull final QuadriMapper<V1, V2, V3, V4, R> mapper) {
    return joinLoops(ZipStrictJoiner.instance(), Arrays.asList(loop1, loop2, loop3, loop4)).forEach(
        new QuadriZipMapper<V1, V2, V3, V4, R>(mapper));
  }

  @NotNull
  public <V> Loop<List<V>> zipStrict(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return joinLoops(ZipStrictJoiner.<V>instance(), loops);
  }
}
