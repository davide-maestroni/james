package dm.fates.eventual2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;

import dm.fates.eventual.Mapper;
import dm.fates.eventual.Observer;
import dm.fates.eventual.Tester;

/**
 * Created by davide-maestroni on 04/18/2018.
 */
public interface ConsumableEither<R, L> extends ConsumableStream<R> {

  @NotNull
  ConsumableEither<R, L> buffer(@NotNull Factory<? extends Queue<EventualState<R>>> queueFactory);

  @NotNull
  <C extends Collection<EventualState<R>>> ConsumableEither<R, L> buffer(
      @NotNull Factory<C> listFactory, @NotNull Mapper<? super C, ? extends C> dataMapper);

  @NotNull
  ConsumableEither<R, L> buffer(int maxCount);

  @NotNull
  StreamForker<? extends ConsumableEither<R, L>> buffer(int maxConsumers, int maxBatchSize,
      @NotNull Factory<? extends Queue<EventualState<R>>> queueFactory);

  @NotNull
  <C extends Collection<EventualState<R>>> StreamForker<? extends ConsumableEither<R, L>> buffer(
      int maxConsumers, int maxBatchSize, @NotNull Factory<C> listFactory,
      @NotNull Mapper<? super C, ? extends C> dataMapper);

  @NotNull
  StreamForker<? extends ConsumableEither<R, L>> buffer(int maxConsumers, int maxBatchSize,
      int maxCount);

  @NotNull
  StreamForker<? extends ConsumableEither<R, L>> cache(
      @NotNull Factory<? extends Queue<EventualState<R>>> queueFactory);

  @NotNull
  <C extends Collection<EventualState<R>>> StreamForker<? extends ConsumableEither<R, L>> cache(
      @NotNull Factory<C> listFactory, @NotNull Mapper<? super C, ? extends C> dataMapper);

  @NotNull
  StreamForker<? extends ConsumableEither<R, L>> cache(int maxCount);

  @NotNull
  StreamForker<? extends ConsumableEither<R, L>> cache(int maxConsumers, int maxBatchSize,
      @NotNull Factory<? extends Queue<EventualState<R>>> queueFactory);

  @NotNull
  <C extends Collection<EventualState<R>>> StreamForker<? extends ConsumableEither<R, L>> cache(
      int maxConsumers, int maxBatchSize, @NotNull Factory<C> listFactory,
      @NotNull Mapper<? super C, ? extends C> dataMapper);

  @NotNull
  StreamForker<? extends ConsumableEither<R, L>> cache(int maxConsumers, int maxBatchSize,
      int maxCount);

  @NotNull
  ConsumableEither<R, L> catchFlatMap(
      @NotNull Mapper<? super Throwable, ? extends Consumable<R>> failureMapper,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  ConsumableEither<R, L> catchMap(@NotNull Mapper<? super Throwable, R> failureMapper,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  ConsumableEither<R, L> catchPeek(@NotNull Observer<? super Throwable> failureObserver,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  <M> ConsumableEither<M, L> chain(
      @NotNull Mapper<? super Consumer<M>, ? extends Consumer<R>> consumerMapper);

  @NotNull
  ConsumableEither<R, L> clone();

  @NotNull
  <M> ConsumableEither<M, L> collect(@NotNull Factory<M> collectorFactory,
      @NotNull CollectorAccumulator<? super R, ? super M> collectorAccumulator);

  @NotNull
  ConsumableEither<R, L> filter(@NotNull Tester<? super R> valueTester);

  @NotNull
  <M> ConsumableEither<M, L> flatMap(
      @NotNull Mapper<? super R, ? extends Consumable<M>> valueMapper);

  @NotNull
  <M> ConsumableEither<M, L> flatMapOrdered(
      @NotNull Mapper<? super R, ? extends Consumable<M>> valueMapper);

  @NotNull
  <M> ConsumableEither<M, L> flatReduce(
      @NotNull Mapper<? super List<R>, ? extends Consumable<M>> valuesMapper);

  @NotNull
  <M> ConsumableEither<M, L> forEach(@NotNull LoopHandler<? super R, M> valueHandler);

  @NotNull
  <M> ConsumableEither<M, L> forEach(@NotNull LoopHandler<? super R, M> valueHandler,
      @NotNull LoopHandler<? super Throwable, M> failureHandler);

  @NotNull
  <M> ConsumableEither<M, L> forEach(@NotNull LoopHandler<? super R, M> valueHandler,
      @NotNull LoopHandler<? super Throwable, M> failureHandler,
      @NotNull LoopCompleter<M> completer);

  @NotNull
  <M> ConsumableEither<M, L> forEachOrdered(@NotNull LoopHandler<? super R, M> valueHandler);

  @NotNull
  <M> ConsumableEither<M, L> forEachOrdered(@NotNull LoopHandler<? super R, M> valueHandler,
      @NotNull LoopHandler<? super Throwable, M> failureHandler);

  @NotNull
  <M> ConsumableEither<M, L> forEachOrdered(@NotNull LoopHandler<? super R, M> valueHandler,
      @NotNull LoopHandler<? super Throwable, M> failureHandler,
      @NotNull LoopCompleter<M> completer);

  @NotNull
  <M> ConsumableEither<M, L> forLoop(
      @NotNull Mapper<? super Yielder<M>, ? extends Looper<? super R>> looperMapper);

  @NotNull
  <M> ConsumableEither<M, L> forLoopOrdered(
      @NotNull Mapper<? super Yielder<M>, ? extends Looper<? super R>> looperMapper);

  @NotNull
  <M> ConsumableEither<M, L> join(
      @NotNull BiMapper<? super List<? extends ConsumableStream<? super R>>, ? super Consumer<M>,
          ? extends StreamJoiner<? super R>> joinerMapper,
      @NotNull ConsumableStream<?>... streams);

  @NotNull
  <M> ConsumableEither<M, L> join(
      @NotNull BiMapper<? super List<? extends ConsumableStream<? super R>>, ? super Consumer<M>,
          ? extends StreamJoiner<? super R>> joinerMapper,
      @NotNull List<? extends ConsumableStream<? super R>> eventualStreams);

  @NotNull
  <M> ConsumableEither<M, L> lift(
      @NotNull BiMapper<? super Producer, ? super Consumer<M>, ? extends Flow<R>> flowMapper);

  @NotNull
  <M> ConsumableEither<M, L> map(@NotNull Mapper<? super R, M> valueMapper);

  @NotNull
  ConsumableEither<R, L> parallel();

  @NotNull
  ConsumableEither<R, L> parallel(@NotNull Executor executor);

  @NotNull
  ConsumableEither<R, L> peek(@NotNull Observer<? super R> valueObserver);

  @NotNull
  ConsumableEither<R, L> reduce(@NotNull Accumulator<R> accumulator);

  @NotNull
  <M> ConsumableEither<M, L> reduce(@NotNull Mapper<? super List<R>, M> valuesMapper);

  @NotNull
  ConsumableEither<R, L> reduce(@NotNull Factory<? extends R> valueFactory,
      @NotNull Accumulator<R> valueAccumulator);

  @NotNull
  ConsumableEither<R, L> sequential();

  @NotNull
  ConsumableEither<R, L> sequential(@NotNull Executor executor);

  @NotNull
  <M> M convertEither(@NotNull Mapper<? super ConsumableEither<R, L>, M> eitherMapper);

  @NotNull
  <M> ConsumableStream<M> fold(@NotNull Mapper<? super R, M> rightMapper,
      @NotNull Mapper<? super L, M> leftMapper);

  void resolve(@NotNull Observer<? super R> rightObserver,
      @NotNull Observer<? super L> leftObserver);

  @NotNull
  ConsumableEither<L, R> swap();
}
