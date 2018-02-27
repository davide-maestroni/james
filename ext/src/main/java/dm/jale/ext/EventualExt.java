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
import dm.jale.eventual.Loop.Yielder;
import dm.jale.eventual.Mapper;
import dm.jale.eventual.Observer;
import dm.jale.eventual.Provider;
import dm.jale.eventual.Settler;
import dm.jale.eventual.SimpleState;
import dm.jale.eventual.Statement;
import dm.jale.eventual.Statement.Forker;
import dm.jale.eventual.StatementForker;
import dm.jale.eventual.Updater;
import dm.jale.executor.ScheduledExecutor;
import dm.jale.ext.backoff.BackoffUpdater;
import dm.jale.ext.backoff.Backoffer;
import dm.jale.ext.backoff.PendingEvaluation;
import dm.jale.ext.eventual.BiMapper;
import dm.jale.ext.eventual.QuadriMapper;
import dm.jale.ext.eventual.Tester;
import dm.jale.ext.eventual.TimedState;
import dm.jale.ext.eventual.TriMapper;
import dm.jale.ext.io.AllocationType;
import dm.jale.ext.io.Chunk;
import dm.jale.util.Iterables;

import static dm.jale.executor.ExecutorPool.immediateExecutor;
import static dm.jale.executor.ExecutorPool.withNoDelay;
import static dm.jale.executor.ExecutorPool.withThrottling;

/**
 * Created by davide-maestroni on 02/15/2018.
 */
public class EventualExt extends Eventual {

  // TODO: 26/02/2018 Mappers: fallbackStatement()?
  // TODO: 21/02/2018 Yielders: groupBy()
  // TODO: 21/02/2018 Joiners:
  // TODO: 16/02/2018 Forkers: repeat(), replayAll(), replayLast(), replayFirst(), replaySince(),
  // TODO: 16/02/2018 - repeatAll(), repeatAllAfter()?
  // TODO: 20/02/2018 Backoff.apply(int count, long lastDelay) => BackoffUpdater
  // TODO: 20/02/2018 (Backoffers): dropFirst, dropLast, wait(backoff?)

  private final Eventual mEventual;

  public EventualExt() {
    mEventual = new Eventual();
  }

  private EventualExt(@NotNull final Eventual eventual) {
    mEventual = eventual;
  }

  @NotNull
  public static <V> Yielder<?, V, V> accumulate(
      @NotNull final BiMapper<? super V, ? super V, ? extends V> accumulator) {
    return new AccumulateYielder<V>(accumulator);
  }

  @NotNull
  public static <V> Yielder<?, V, V> accumulate(final V initialValue,
      @NotNull final BiMapper<? super V, ? super V, ? extends V> accumulator) {
    return new AccumulateYielder<V>(initialValue, accumulator);
  }

  @NotNull
  public static Yielder<?, Number, Float> average() {
    return averageFloat();
  }

  @NotNull
  public static Yielder<?, Number, Double> averageDouble() {
    return AverageDoubleYielder.instance();
  }

  @NotNull
  public static Yielder<?, Number, Float> averageFloat() {
    return AverageFloatYielder.instance();
  }

  @NotNull
  public static Yielder<?, Number, Integer> averageInteger() {
    return AverageIntegerYielder.instance();
  }

  @NotNull
  public static Yielder<?, Number, Long> averageLong() {
    return AverageLongYielder.instance();
  }

  @NotNull
  public static <V> Yielder<?, V, V> ifEmptyFallback(
      @NotNull final Loop<? extends V> fallbackLoop) {
    return new IfEmptyFallback<V>(fallbackLoop);
  }

  @NotNull
  public static <V> StatementForker<?, V> repeat() {
    return RepeatForker.instance();
  }

  @NotNull
  public static <V> StatementForker<?, V> repeatAfter(final long timeout,
      @NotNull final TimeUnit timeUnit) {
    return new RepeatAfterForker<V>(timeout, timeUnit);
  }

  @NotNull
  public static <V> StatementForker<?, V> replay() {
    return ReplayForker.instance();
  }

  @NotNull
  public static <V> Forker<?, V, Evaluation<V>, Statement<V>> retry(final int maxCount) {
    return RetryForker.newForker(maxCount);
  }

  @NotNull
  public static <S, V> Forker<?, V, Evaluation<V>, Statement<V>> retryOn(
      @NotNull final ScheduledExecutor executor, @NotNull final BackoffUpdater<S> backoff) {
    return RetryBackoffForker.newForker(executor, backoff);
  }

  @NotNull
  public static Yielder<?, Number, Integer> sum() {
    return sumInteger();
  }

  @NotNull
  public static Yielder<?, Number, Double> sumDouble() {
    return SumDoubleYielder.instance();
  }

  @NotNull
  public static Yielder<?, Number, Float> sumFloat() {
    return SumFloatYielder.instance();
  }

  @NotNull
  public static Yielder<?, Number, Integer> sumInteger() {
    return SumIntegerYielder.instance();
  }

  @NotNull
  public static Yielder<?, Number, Long> sumLong() {
    return SumLongYielder.instance();
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
  public <V> Yielder<?, V, V> batch(final int maxValues, final int maxFailures) {
    return new BatchYielder<V>(maxValues, maxFailures);
  }

  @NotNull
  public <V> Loop<V> concat(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return joinLoops(ConcatJoiner.<V>instance(), loops);
  }

  @NotNull
  public <V> Yielder<?, V, V> delayedFailures() {
    return DelayedFailuresYielder.instance();
  }

  @NotNull
  public <V> Yielder<?, V, V> distinct() {
    return DistinctYielder.instance();
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
  public <V> Yielder<?, V, EvaluationState<V>> evaluationStates() {
    return StatesYielder.instance();
  }

  @NotNull
  public <V> Yielder<?, V, TimedState<V>> evaluationStatesTimed() {
    return TimedStatesYielder.instance();
  }

  @NotNull
  public <V> Yielder<?, V, V> filter(@NotNull final Tester<V> tester) {
    return new FilterYielder<V>(tester);
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
  public Loop<Chunk> inChunks(@NotNull final InputStream inputStream) {
    return inChunks(inputStream, null);
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final InputStream inputStream,
      @Nullable final AllocationType allocationType) {
    return loop(new InputStreamChunkObserver(inputStream, allocationType, null, null, null));
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final InputStream inputStream,
      @Nullable final AllocationType allocationType, final int coreSize) {
    dm.jale.util.ConstantConditions.positive("coreSize", coreSize);
    return loop(new InputStreamChunkObserver(inputStream, allocationType, coreSize, null, null));
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final InputStream inputStream,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return loop(
        new InputStreamChunkObserver(inputStream, allocationType, null, bufferSize, poolSize));
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final ReadableByteChannel channel) {
    return inChunks(channel, null);
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final ReadableByteChannel channel,
      @Nullable final AllocationType allocationType) {
    return loop(new ChannelChunkObserver(channel, allocationType, null, null, null));
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final ReadableByteChannel channel,
      @Nullable final AllocationType allocationType, final int coreSize) {
    return loop(new ChannelChunkObserver(channel, allocationType, coreSize, null, null));
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final ReadableByteChannel channel,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return loop(new ChannelChunkObserver(channel, allocationType, null, bufferSize, poolSize));
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final ByteBuffer buffer) {
    return inChunks(buffer, null);
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final ByteBuffer buffer,
      @Nullable final AllocationType allocationType) {
    return loop(new ByteBufferChunkObserver(buffer, allocationType, null, null, null));
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final ByteBuffer buffer,
      @Nullable final AllocationType allocationType, final int coreSize) {
    return loop(new ByteBufferChunkObserver(buffer, allocationType, coreSize, null, null));
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final ByteBuffer buffer,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return loop(new ByteBufferChunkObserver(buffer, allocationType, null, bufferSize, poolSize));
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final byte[] buffer) {
    return inChunks(buffer, null);
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final byte[] buffer,
      @Nullable final AllocationType allocationType) {
    return loop(new ByteArrayChunkObserver(buffer, allocationType, null, null, null));
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final byte[] buffer,
      @Nullable final AllocationType allocationType, final int coreSize) {
    return loop(new ByteArrayChunkObserver(buffer, allocationType, coreSize, null, null));
  }

  @NotNull
  public Loop<Chunk> inChunks(@NotNull final byte[] buffer,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return loop(new ByteArrayChunkObserver(buffer, allocationType, null, bufferSize, poolSize));
  }

  @NotNull
  public <V extends Comparable<? super V>> Loop<V> inRange(@NotNull final V start,
      @NotNull final V end, @NotNull final Mapper<? super V, ? extends V> increment) {
    return loop(new InRangeComparableObserver<V>(start, end, increment, false));
  }

  @NotNull
  public Loop<Integer> inRange(final int start, final int end) {
    return inRange(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public Loop<Integer> inRange(final int start, final int end, final int increment) {
    return loop(new InRangeIntegerObserver(start, end, increment, false));
  }

  @NotNull
  public Loop<Long> inRange(final long start, final long end) {
    return inRange(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public Loop<Long> inRange(final long start, final long end, final long increment) {
    return loop(new InRangeLongObserver(start, end, increment, false));
  }

  @NotNull
  public Loop<Float> inRange(final float start, final float end) {
    return inRange(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public Loop<Float> inRange(final float start, final float end, final float increment) {
    return loop(new InRangeFloatObserver(start, end, increment, false));
  }

  @NotNull
  public Loop<Double> inRange(final double start, final double end) {
    return inRange(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public Loop<Double> inRange(final double start, final double end, final double increment) {
    return loop(new InRangeDoubleObserver(start, end, increment, false));
  }

  @NotNull
  public <V extends Comparable<? super V>> Loop<V> inRangeInclusive(@NotNull final V start,
      @NotNull final V end, @NotNull final Mapper<? super V, ? extends V> increment) {
    return loop(new InRangeComparableObserver<V>(start, end, increment, true));
  }

  @NotNull
  public Loop<Integer> inRangeInclusive(final int start, final int end) {
    return inRangeInclusive(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public Loop<Integer> inRangeInclusive(final int start, final int end, final int increment) {
    return loop(new InRangeIntegerObserver(start, end, increment, true));
  }

  @NotNull
  public Loop<Long> inRangeInclusive(final long start, final long end) {
    return inRangeInclusive(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public Loop<Long> inRangeInclusive(final long start, final long end, final long increment) {
    return loop(new InRangeLongObserver(start, end, increment, true));
  }

  @NotNull
  public Loop<Float> inRangeInclusive(final float start, final float end) {
    return inRangeInclusive(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public Loop<Float> inRangeInclusive(final float start, final float end, final float increment) {
    return loop(new InRangeFloatObserver(start, end, increment, true));
  }

  @NotNull
  public Loop<Double> inRangeInclusive(final double start, final double end) {
    return inRangeInclusive(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public Loop<Double> inRangeInclusive(final double start, final double end,
      final double increment) {
    return loop(new InRangeDoubleObserver(start, end, increment, true));
  }

  @NotNull
  public <V> Loop<V> inSequence(@NotNull final V start, final long count,
      @NotNull final Mapper<? super V, ? extends V> increment) {
    return loop(new InSequenceObserver<V>(start, count, increment));
  }

  @NotNull
  public <V extends Comparable<? super V>> Yielder<?, V, V> max() {
    return MaxYielder.instance();
  }

  @NotNull
  public <V> Yielder<?, V, V> maxBy(@NotNull final Comparator<? super V> comparator) {
    return new MaxByYielder<V>(comparator);
  }

  @NotNull
  public <V> Loop<V> merge(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return joinLoops(MergeJoiner.<V>instance(), loops);
  }

  @NotNull
  public <V extends Comparable<? super V>> Yielder<?, V, V> min() {
    return MinYielder.instance();
  }

  @NotNull
  public <V> Yielder<?, V, V> minBy(@NotNull final Comparator<? super V> comparator) {
    return new MinByYielder<V>(comparator);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <T> T proxy(@NotNull final Object object, @NotNull final Class<? extends T> type) {
    final Executor executor = getExecutor();
    return (T) Proxy.newProxyInstance(EventualExt.class.getClassLoader(), new Class<?>[]{type},
        new AsyncInvocationHandler(this.evaluateOn(
            withThrottling(1, (executor != null) ? withNoDelay(executor) : immediateExecutor())),
            object));
  }

  @NotNull
  public <V> Yielder<?, V, V> resize(final long size) {
    return resize(size, SimpleState.<V>ofValue(null));
  }

  @NotNull
  public <V> Yielder<?, V, V> resize(final long size, @NotNull final EvaluationState<V> padding) {
    return new ResizeYielder<V>(size, padding);
  }

  @NotNull
  public <V> Yielder<?, V, V> resizeFailures(final long size, @NotNull final Throwable failure) {
    return new ResizeFailuresYielder<V>(size, failure);
  }

  @NotNull
  public <V> Yielder<?, V, V> resizeValues(final long size) {
    return resizeValues(size, null);
  }

  @NotNull
  public <V> Yielder<?, V, V> resizeValues(final long size, final V padding) {
    return new ResizeValuesYielder<V>(size, padding);
  }

  @NotNull
  public <V> Yielder<?, V, V> skip(final int count) {
    return skipFirst(count);
  }

  @NotNull
  public <V> Yielder<?, V, V> skipFailures(final int count) {
    return skipFirstFailures(count);
  }

  @NotNull
  public <V> Yielder<?, V, V> skipFirst(final int count) {
    return new SkipFirstYielder<V>(count);
  }

  @NotNull
  public <V> Yielder<?, V, V> skipFirstFailures(final int count) {
    return new SkipFirstFailuresYielder<V>(count);
  }

  @NotNull
  public <V> Yielder<?, V, V> skipFirstValues(final int count) {
    return new SkipFirstValuesYielder<V>(count);
  }

  @NotNull
  public <V> Yielder<?, V, V> skipLast(final int count) {
    return new SkipLastYielder<V>(count);
  }

  @NotNull
  public <V> Yielder<?, V, V> skipLastFailures(final int count) {
    return new SkipLastFailuresYielder<V>(count);
  }

  @NotNull
  public <V> Yielder<?, V, V> skipLastValues(final int count) {
    return new SkipLastValuesYielder<V>(count);
  }

  @NotNull
  public <V> Yielder<?, V, V> skipValues(final int count) {
    return skipFirstValues(count);
  }

  @NotNull
  public <V extends Comparable<? super V>> Yielder<?, V, V> sort() {
    return SortYielder.instance();
  }

  @NotNull
  public <V> Yielder<?, V, V> sortBy(@NotNull final Comparator<? super V> comparator) {
    return new SortByYielder<V>(comparator);
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
  public <V> Yielder<?, V, V> unique() {
    return UniqueYielder.instance();
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
  public <V1, V2, R> Loop<R> zip(final V1 filler1, final V2 filler2,
      @NotNull final Loop<? extends V1> loop1, @NotNull final Loop<? extends V2> loop2,
      @NotNull final BiMapper<V1, V2, R> mapper) {
    return joinLoops(new ZipFillJoiner<Object>(Arrays.asList(filler1, filler2)),
        Arrays.asList(loop1, loop2)).forEach(new BiZipMapper<V1, V2, R>(mapper));
  }

  @NotNull
  public <V1, V2, R> Loop<R> zip(@NotNull final Loop<? extends V1> loop1,
      @NotNull final Loop<? extends V2> loop2, @NotNull final BiMapper<V1, V2, R> mapper) {
    return joinLoops(ZipJoiner.instance(), Arrays.asList(loop1, loop2)).forEach(
        new BiZipMapper<V1, V2, R>(mapper));
  }

  @NotNull
  public <V1, V2, V3, R> Loop<R> zip(final V1 filler1, final V2 filler2, final V3 filler3,
      @NotNull final Loop<? extends V1> loop1, @NotNull final Loop<? extends V2> loop2,
      @NotNull final Loop<? extends V3> loop3, @NotNull final TriMapper<V1, V2, V3, R> mapper) {
    return joinLoops(new ZipFillJoiner<Object>(Arrays.asList(filler1, filler2, filler3)),
        Arrays.asList(loop1, loop2, loop3)).forEach(new TriZipMapper<V1, V2, V3, R>(mapper));
  }

  @NotNull
  public <V1, V2, V3, R> Loop<R> zip(@NotNull final Loop<? extends V1> loop1,
      @NotNull final Loop<? extends V2> loop2, @NotNull final Loop<? extends V3> loop3,
      @NotNull final TriMapper<V1, V2, V3, R> mapper) {
    return joinLoops(ZipJoiner.instance(), Arrays.asList(loop1, loop2, loop3)).forEach(
        new TriZipMapper<V1, V2, V3, R>(mapper));
  }

  @NotNull
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
  public <V1, V2, V3, V4, R> Loop<R> zip(@NotNull final Loop<? extends V1> loop1,
      @NotNull final Loop<? extends V2> loop2, @NotNull final Loop<? extends V3> loop3,
      @NotNull final Loop<? extends V4> loop4,
      @NotNull final QuadriMapper<V1, V2, V3, V4, R> mapper) {
    return joinLoops(ZipJoiner.instance(), Arrays.asList(loop1, loop2, loop3, loop4)).forEach(
        new QuadriZipMapper<V1, V2, V3, V4, R>(mapper));
  }

  @NotNull
  public <V1, V2, R> Loop<R> zipStrict(@NotNull final Loop<? extends V1> loop1,
      @NotNull final Loop<? extends V2> loop2, @NotNull final BiMapper<V1, V2, R> mapper) {
    return joinLoops(ZipStrictJoiner.instance(), Arrays.asList(loop1, loop2)).forEach(
        new BiZipMapper<V1, V2, R>(mapper));
  }

  @NotNull
  public <V1, V2, V3, R> Loop<R> zipStrict(@NotNull final Loop<? extends V1> loop1,
      @NotNull final Loop<? extends V2> loop2, @NotNull final Loop<? extends V3> loop3,
      @NotNull final TriMapper<V1, V2, V3, R> mapper) {
    return joinLoops(ZipStrictJoiner.instance(), Arrays.asList(loop1, loop2, loop3)).forEach(
        new TriZipMapper<V1, V2, V3, R>(mapper));
  }

  @NotNull
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
