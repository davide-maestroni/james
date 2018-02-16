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
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.concurrent.Executor;

import dm.jale.Async;
import dm.jale.async.AsyncEvaluation;
import dm.jale.async.AsyncEvaluations;
import dm.jale.async.AsyncLoop;
import dm.jale.async.AsyncState;
import dm.jale.async.AsyncStatement;
import dm.jale.async.AsyncStatement.Forker;
import dm.jale.async.CombinationCompleter;
import dm.jale.async.CombinationSettler;
import dm.jale.async.CombinationUpdater;
import dm.jale.async.Combiner;
import dm.jale.async.Mapper;
import dm.jale.async.Observer;
import dm.jale.async.Provider;
import dm.jale.async.Settler;
import dm.jale.async.Updater;
import dm.jale.ext.backoff.Backoffer;
import dm.jale.ext.backoff.PendingEvaluations;
import dm.jale.ext.io.AllocationType;
import dm.jale.ext.io.Chunk;

/**
 * Created by davide-maestroni on 02/15/2018.
 */
public class AsyncExt extends Async {

  // TODO: 16/02/2018 Combiners: concat(), switch(), first()
  // TODO: 16/02/2018 Yielders: delayFailures(), sum(), sumLong(), average(), averageLong(), min(),
  // TODO: 16/02/2018 - max(), distinct()
  // TODO: 16/02/2018 Forkers: retry(), repeat(), repeatAll(), repeatLast(), repeatFirst(),
  // TODO: 16/02/2018 - repeatNewerThan()

  private final Async mAsync;

  public AsyncExt() {
    mAsync = new Async();
  }

  private AsyncExt(@NotNull final Async async) {
    mAsync = async;
  }

  @NotNull
  public static <S, V> Forker<?, V, AsyncEvaluations<V>, AsyncLoop<V>> onBackoffed(
      @NotNull final Executor executor, @NotNull final Backoffer<S, V> backoffer) {
    return Async.buffered(new BackoffForker<S, V>(executor, backoffer));
  }

  @NotNull
  public static <S, V> Forker<?, V, AsyncEvaluations<V>, AsyncLoop<V>> onBackoffed(
      @NotNull final Executor executor, @Nullable final Provider<S> init,
      @Nullable final Updater<S, ? super V, ? super PendingEvaluations<V>> value,
      @Nullable final Updater<S, ? super Throwable, ? super PendingEvaluations<V>> failure,
      @Nullable final Settler<S, ? super PendingEvaluations<V>> done) {
    return onBackoffed(executor, new ComposedBackoffer<S, V>(init, value, failure, done));
  }

  @NotNull
  public <V> AsyncStatement<List<V>> allOf(
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return statementOf(AllOfCombiner.<V>instance(), statements);
  }

  @NotNull
  public <V> AsyncStatement<V> anyOf(
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return statementOf(AnyOfCombiner.<V>instance(), statements);
  }

  @NotNull
  @Override
  public AsyncExt evaluateOn(@Nullable final Executor executor) {
    return new AsyncExt(mAsync.evaluateOn(executor));
  }

  @NotNull
  @Override
  public <V> AsyncStatement<V> failure(@NotNull final Throwable failure) {
    return mAsync.failure(failure);
  }

  @NotNull
  @Override
  public <V> AsyncLoop<V> failures(@NotNull final Iterable<? extends Throwable> failures) {
    return mAsync.failures(failures);
  }

  @NotNull
  @Override
  public AsyncExt loggerName(@Nullable final String loggerName) {
    return new AsyncExt(mAsync.loggerName(loggerName));
  }

  @NotNull
  @Override
  public <V> AsyncLoop<V> loop(@NotNull final AsyncStatement<? extends Iterable<V>> statement) {
    return mAsync.loop(statement);
  }

  @NotNull
  @Override
  public <V> AsyncLoop<V> loop(@NotNull final Observer<AsyncEvaluations<V>> observer) {
    return mAsync.loop(observer);
  }

  @NotNull
  @Override
  public <S, V, R> AsyncLoop<R> loopOf(
      @NotNull final Combiner<S, ? super V, ? super AsyncEvaluations<R>, AsyncLoop<V>> combiner,
      @NotNull final Iterable<? extends AsyncLoop<? extends V>> asyncLoops) {
    return mAsync.loopOf(combiner, asyncLoops);
  }

  @NotNull
  @Override
  public <S, V, R> AsyncLoop<R> loopOf(@Nullable final Mapper<? super List<AsyncLoop<V>>, S> init,
      @Nullable final CombinationUpdater<S, ? super V, ? super AsyncEvaluations<? extends R>,
          AsyncLoop<V>> value,
      @Nullable final CombinationUpdater<S, ? super Throwable, ? super AsyncEvaluations<? extends
          R>, AsyncLoop<V>> failure,
      @Nullable final CombinationCompleter<S, ? super AsyncEvaluations<? extends R>,
          AsyncLoop<V>> done,
      @Nullable final CombinationSettler<S, ? super AsyncEvaluations<? extends R>, AsyncLoop<V>>
          settle,
      @NotNull final Iterable<? extends AsyncLoop<? extends V>> asyncLoops) {
    return mAsync.loopOf(init, value, failure, done, settle, asyncLoops);
  }

  @NotNull
  @Override
  public <V> AsyncLoop<V> loopOnce(@NotNull final AsyncStatement<? extends V> statement) {
    return mAsync.loopOnce(statement);
  }

  @NotNull
  @Override
  public <V> AsyncStatement<V> statement(@NotNull final Observer<AsyncEvaluation<V>> observer) {
    return mAsync.statement(observer);
  }

  @NotNull
  @Override
  public <S, V, R> AsyncStatement<R> statementOf(
      @NotNull final Combiner<S, ? super V, ? super AsyncEvaluation<R>, AsyncStatement<V>> combiner,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return mAsync.statementOf(combiner, statements);
  }

  @NotNull
  @Override
  public <S, V, R> AsyncStatement<R> statementOf(
      @Nullable final Mapper<? super List<AsyncStatement<V>>, S> init,
      @Nullable final CombinationUpdater<S, ? super V, ? super AsyncEvaluation<? extends R>,
          AsyncStatement<V>> value,
      @Nullable final CombinationUpdater<S, ? super Throwable, ? super AsyncEvaluation<? extends
          R>, AsyncStatement<V>> failure,
      @Nullable final CombinationCompleter<S, ? super AsyncEvaluation<? extends R>,
          AsyncStatement<V>> done,
      @Nullable final CombinationSettler<S, ? super AsyncEvaluation<? extends R>,
          AsyncStatement<V>> settle,
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return mAsync.statementOf(init, value, failure, done, settle, statements);
  }

  @NotNull
  @Override
  public AsyncExt unevaluated() {
    return new AsyncExt(mAsync.unevaluated());
  }

  @NotNull
  @Override
  public <V> AsyncStatement<V> value(final V value) {
    return mAsync.value(value);
  }

  @NotNull
  @Override
  public <V> AsyncLoop<V> values(@NotNull final Iterable<? extends V> values) {
    return mAsync.values(values);
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final InputStream inputStream) {
    return inChunks(inputStream, null);
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final InputStream inputStream,
      @Nullable final AllocationType allocationType) {
    return loop(new InputStreamChunkObserver(inputStream, allocationType, null, null, null));
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final InputStream inputStream,
      @Nullable final AllocationType allocationType, final int coreSize) {
    dm.jale.util.ConstantConditions.positive("coreSize", coreSize);
    return loop(new InputStreamChunkObserver(inputStream, allocationType, coreSize, null, null));
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final InputStream inputStream,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return loop(
        new InputStreamChunkObserver(inputStream, allocationType, null, bufferSize, poolSize));
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final ReadableByteChannel channel) {
    return inChunks(channel, null);
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final ReadableByteChannel channel,
      @Nullable final AllocationType allocationType) {
    return loop(new ChannelChunkObserver(channel, allocationType, null, null, null));
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final ReadableByteChannel channel,
      @Nullable final AllocationType allocationType, final int coreSize) {
    return loop(new ChannelChunkObserver(channel, allocationType, coreSize, null, null));
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final ReadableByteChannel channel,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return loop(new ChannelChunkObserver(channel, allocationType, null, bufferSize, poolSize));
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final ByteBuffer buffer) {
    return inChunks(buffer, null);
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final ByteBuffer buffer,
      @Nullable final AllocationType allocationType) {
    return loop(new ByteBufferChunkObserver(buffer, allocationType, null, null, null));
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final ByteBuffer buffer,
      @Nullable final AllocationType allocationType, final int coreSize) {
    return loop(new ByteBufferChunkObserver(buffer, allocationType, coreSize, null, null));
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final ByteBuffer buffer,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return loop(new ByteBufferChunkObserver(buffer, allocationType, null, bufferSize, poolSize));
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final byte[] buffer) {
    return inChunks(buffer, null);
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final byte[] buffer,
      @Nullable final AllocationType allocationType) {
    return loop(new ByteArrayChunkObserver(buffer, allocationType, null, null, null));
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final byte[] buffer,
      @Nullable final AllocationType allocationType, final int coreSize) {
    return loop(new ByteArrayChunkObserver(buffer, allocationType, coreSize, null, null));
  }

  @NotNull
  public AsyncLoop<Chunk> inChunks(@NotNull final byte[] buffer,
      @Nullable final AllocationType allocationType, final int bufferSize, final int poolSize) {
    return loop(new ByteArrayChunkObserver(buffer, allocationType, null, bufferSize, poolSize));
  }

  @NotNull
  public <V extends Comparable<V>> AsyncLoop<V> inRange(@NotNull final V start,
      @NotNull final V end, @NotNull final Mapper<? super V, ? extends V> increment) {
    return loop(new InRangeComparableObserver<V>(start, end, increment, false));
  }

  @NotNull
  public AsyncLoop<Integer> inRange(final int start, final int end) {
    return inRange(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public AsyncLoop<Integer> inRange(final int start, final int end, final int increment) {
    return loop(new InRangeIntegerObserver(start, end, increment, false));
  }

  @NotNull
  public AsyncLoop<Long> inRange(final long start, final long end) {
    return inRange(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public AsyncLoop<Long> inRange(final long start, final long end, final long increment) {
    return loop(new InRangeLongObserver(start, end, increment, false));
  }

  @NotNull
  public AsyncLoop<Float> inRange(final float start, final float end) {
    return inRange(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public AsyncLoop<Float> inRange(final float start, final float end, final float increment) {
    return loop(new InRangeFloatObserver(start, end, increment, false));
  }

  @NotNull
  public AsyncLoop<Double> inRange(final double start, final double end) {
    return inRange(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public AsyncLoop<Double> inRange(final double start, final double end, final double increment) {
    return loop(new InRangeDoubleObserver(start, end, increment, false));
  }

  @NotNull
  public <V extends Comparable<V>> AsyncLoop<V> inRangeInclusive(@NotNull final V start,
      @NotNull final V end, @NotNull final Mapper<? super V, ? extends V> increment) {
    return loop(new InRangeComparableObserver<V>(start, end, increment, true));
  }

  @NotNull
  public AsyncLoop<Integer> inRangeInclusive(final int start, final int end) {
    return inRangeInclusive(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public AsyncLoop<Integer> inRangeInclusive(final int start, final int end, final int increment) {
    return loop(new InRangeIntegerObserver(start, end, increment, true));
  }

  @NotNull
  public AsyncLoop<Long> inRangeInclusive(final long start, final long end) {
    return inRangeInclusive(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public AsyncLoop<Long> inRangeInclusive(final long start, final long end, final long increment) {
    return loop(new InRangeLongObserver(start, end, increment, true));
  }

  @NotNull
  public AsyncLoop<Float> inRangeInclusive(final float start, final float end) {
    return inRangeInclusive(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public AsyncLoop<Float> inRangeInclusive(final float start, final float end,
      final float increment) {
    return loop(new InRangeFloatObserver(start, end, increment, true));
  }

  @NotNull
  public AsyncLoop<Double> inRangeInclusive(final double start, final double end) {
    return inRangeInclusive(start, end, (start <= end) ? 1 : -1);
  }

  @NotNull
  public AsyncLoop<Double> inRangeInclusive(final double start, final double end,
      final double increment) {
    return loop(new InRangeDoubleObserver(start, end, increment, true));
  }

  @NotNull
  public <V> AsyncLoop<V> inSequence(@NotNull final V start, final long count,
      @NotNull final Mapper<? super V, ? extends V> increment) {
    return loop(new InSequenceObserver<V>(start, count, increment));
  }

  @NotNull
  public <V> AsyncStatement<List<AsyncState<V>>> statesOf(
      @NotNull final Iterable<? extends AsyncStatement<? extends V>> statements) {
    return statementOf(StatesOfCombiner.<V>instance(), statements);
  }
}
