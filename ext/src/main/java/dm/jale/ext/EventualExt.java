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
import dm.jale.eventual.Statement;
import dm.jale.eventual.Statement.Forker;
import dm.jale.eventual.Updater;
import dm.jale.executor.ScheduledExecutor;
import dm.jale.ext.backoff.BackoffUpdater;
import dm.jale.ext.backoff.Backoffer;
import dm.jale.ext.backoff.PendingEvaluation;
import dm.jale.ext.eventual.Tester;
import dm.jale.ext.eventual.TimedState;
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

  // TODO: 22/02/2018 Yielders: sort, sortBy?
  // TODO: 21/02/2018 BatchYielder
  // TODO: 21/02/2018 Joiners: zip(BiMapper), zip(TriMapper), zip(TetraMapper),
  // TODO: 16/02/2018 Forkers: repeat(), repeatAll(), repeatLast(), repeatFirst(), repeatSince(),
  // TODO: 16/02/2018 - refreshAfter(), refreshAllAfter()
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
    return statementOf(AllOfJoiner.<V>instance(), statements);
  }

  @NotNull
  public <V> Statement<V> anyOf(
      @NotNull final Iterable<? extends Statement<? extends V>> statements) {
    return statementOf(AnyOfJoiner.<V>instance(), statements);
  }

  @NotNull
  public <V> Loop<V> concat(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return loopOf(ConcatJoiner.<V>instance(), loops);
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
  public <S, V, R> Loop<R> loopOf(
      @NotNull final Joiner<S, ? super V, ? super EvaluationCollection<R>, Loop<V>> joiner,
      @NotNull final Iterable<? extends Loop<? extends V>> asyncLoops) {
    return mEventual.loopOf(joiner, asyncLoops);
  }

  @NotNull
  @Override
  public <S, V, R> Loop<R> loopOf(@Nullable final Mapper<? super List<Loop<V>>, S> init,
      @Nullable final JoinUpdater<S, ? super V, ? super EvaluationCollection<? extends R>,
          Loop<V>> value,
      @Nullable final JoinUpdater<S, ? super Throwable, ? super EvaluationCollection<? extends
          R>, Loop<V>> failure,
      @Nullable final JoinCompleter<S, ? super EvaluationCollection<? extends R>, Loop<V>> done,
      @Nullable final JoinSettler<S, ? super EvaluationCollection<? extends R>, Loop<V>> settle,
      @NotNull final Iterable<? extends Loop<? extends V>> asyncLoops) {
    return mEventual.loopOf(init, value, failure, done, settle, asyncLoops);
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
  public <S, V, R> Statement<R> statementOf(
      @NotNull final Joiner<S, ? super V, ? super Evaluation<R>, Statement<V>> joiner,
      @NotNull final Iterable<? extends Statement<? extends V>> statements) {
    return mEventual.statementOf(joiner, statements);
  }

  @NotNull
  @Override
  public <S, V, R> Statement<R> statementOf(
      @Nullable final Mapper<? super List<Statement<V>>, S> init,
      @Nullable final JoinUpdater<S, ? super V, ? super Evaluation<? extends R>, Statement<V>>
          value,
      @Nullable final JoinUpdater<S, ? super Throwable, ? super Evaluation<? extends R>,
          Statement<V>> failure,
      @Nullable final JoinCompleter<S, ? super Evaluation<? extends R>, Statement<V>> done,
      @Nullable final JoinSettler<S, ? super Evaluation<? extends R>, Statement<V>> settle,
      @NotNull final Iterable<? extends Statement<? extends V>> statements) {
    return mEventual.statementOf(init, value, failure, done, settle, statements);
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
    return loopOf(FirstJoiner.<V>instance(), loops);
  }

  @NotNull
  public <V> Loop<V> firstOnly(final boolean mayInterruptIfRunning,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return loopOf(new FirstOnlyJoiner<V>(mayInterruptIfRunning), loops);
  }

  @NotNull
  public <V> Loop<V> firstOrEmpty(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return loopOf(FirstOrEmptyJoiner.<V>instance(), loops);
  }

  @NotNull
  public <V> Loop<V> firstOrEmptyOnly(final boolean mayInterruptIfRunning,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return loopOf(new FirstOrEmptyOnlyJoiner<V>(mayInterruptIfRunning), loops);
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
  public <V extends Comparable<V>> Loop<V> inRange(@NotNull final V start, @NotNull final V end,
      @NotNull final Mapper<? super V, ? extends V> increment) {
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
  public <V extends Comparable<V>> Loop<V> inRangeInclusive(@NotNull final V start,
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
  public <V extends Comparable<V>> Yielder<?, V, V> max() {
    return MaxYielder.instance();
  }

  @NotNull
  public <V> Yielder<?, V, V> maxBy(@NotNull final Comparator<? super V> comparator) {
    return new MaxByYielder<V>(comparator);
  }

  @NotNull
  public <V> Loop<V> merge(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return loopOf(MergeJoiner.<V>instance(), loops);
  }

  @NotNull
  public <V extends Comparable<V>> Yielder<?, V, V> min() {
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
  public <V> Statement<List<EvaluationState<V>>> statesOf(
      @NotNull final Iterable<? extends Statement<? extends V>> statements) {
    return statementOf(StatesOfJoiner.<V>instance(), statements);
  }

  @NotNull
  public <V> Loop<V> switchAll(@NotNull final Loop<? extends Integer> indexes,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    final ArrayList<Loop<?>> allLoops = new ArrayList<Loop<?>>();
    allLoops.add(indexes);
    Iterables.addAll((Iterable<? extends Loop<?>>) loops, allLoops);
    return loopOf(SwitchAllJoiner.<V>instance(), allLoops);
  }

  @NotNull
  public <V> Loop<V> switchBetween(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return loopOf(SwitchJoiner.<V>instance(), loops);
  }

  @NotNull
  public <V> Loop<V> switchFirst(final int maxCount, @NotNull final Loop<? extends Integer> indexes,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    final ArrayList<Loop<?>> allLoops = new ArrayList<Loop<?>>();
    allLoops.add(indexes);
    Iterables.addAll((Iterable<? extends Loop<?>>) loops, allLoops);
    return loopOf(new SwitchFirstJoiner<V>(maxCount), allLoops);
  }

  @NotNull
  public <V> Loop<V> switchLast(final int maxCount, @NotNull final Loop<? extends Integer> indexes,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    final ArrayList<Loop<?>> allLoops = new ArrayList<Loop<?>>();
    allLoops.add(indexes);
    Iterables.addAll((Iterable<? extends Loop<?>>) loops, allLoops);
    return loopOf(new SwitchLastJoiner<V>(maxCount), allLoops);
  }

  @NotNull
  public <V> Loop<V> switchOnly(final boolean mayInterruptIfRunning,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return loopOf(new SwitchOnlyJoiner<V>(mayInterruptIfRunning), loops);
  }

  @NotNull
  public <V> Loop<V> switchSince(final long timeout, @NotNull final TimeUnit timeUnit,
      @NotNull final Loop<? extends Integer> indexes,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    final ArrayList<Loop<?>> allLoops = new ArrayList<Loop<?>>();
    allLoops.add(indexes);
    Iterables.addAll((Iterable<? extends Loop<?>>) loops, allLoops);
    return loopOf(new SwitchSinceJoiner<V>(timeout, timeUnit), allLoops);
  }

  @NotNull
  public <V> Loop<V> switchWhen(@NotNull final Loop<? extends Integer> indexes,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    final ArrayList<Loop<?>> allLoops = new ArrayList<Loop<?>>();
    allLoops.add(indexes);
    Iterables.addAll((Iterable<? extends Loop<?>>) loops, allLoops);
    return loopOf(SwitchWhenJoiner.<V>instance(), allLoops);
  }

  @NotNull
  public <V> Yielder<?, V, V> unique() {
    return UniqueYielder.instance();
  }

  @NotNull
  public <V> Loop<List<V>> zip(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return loopOf(ZipJoiner.<V>instance(), loops);
  }

  @NotNull
  public <V> Loop<List<V>> zip(final V filler,
      @NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return loopOf(new ZipFillJoiner<V>(filler), loops);
  }

  @NotNull
  public <V> Loop<List<V>> zipStrict(@NotNull final Iterable<? extends Loop<? extends V>> loops) {
    return loopOf(ZipStrictJoiner.<V>instance(), loops);
  }
}
