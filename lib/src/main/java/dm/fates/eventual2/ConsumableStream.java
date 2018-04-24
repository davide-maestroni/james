package dm.fates.eventual2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import dm.fates.eventual.Mapper;
import dm.fates.eventual.Observer;
import dm.fates.eventual.Tester;

/**
 * Created by davide-maestroni on 04/03/2018.
 */
public interface ConsumableStream<V> extends Consumable<V> {

  // TODO: 03/04/2018 join(), is*(), using/try, either, option, promise(?)

  @NotNull
  ConsumableStream<V> buffer(@NotNull Factory<? extends Queue<EventualState<V>>> queueFactory);

  @NotNull
  <C extends Collection<EventualState<V>>> ConsumableStream<V> buffer(
      @NotNull Factory<C> listFactory, @NotNull Mapper<? super C, ? extends C> dataMapper);

  @NotNull
  ConsumableStream<V> buffer(int maxCount);

  @NotNull
  StreamForker<? extends ConsumableStream<V>> buffer(int maxConsumers, int maxBatchSize,
      @NotNull Factory<? extends Queue<EventualState<V>>> queueFactory);

  @NotNull
  <C extends Collection<EventualState<V>>> StreamForker<? extends ConsumableStream<V>> buffer(
      int maxConsumers, int maxBatchSize, @NotNull Factory<C> listFactory,
      @NotNull Mapper<? super C, ? extends C> dataMapper);

  @NotNull
  StreamForker<? extends ConsumableStream<V>> buffer(int maxConsumers, int maxBatchSize,
      int maxCount);

  @NotNull
  StreamForker<? extends ConsumableStream<V>> cache(
      @NotNull Factory<? extends Queue<EventualState<V>>> queueFactory);

  @NotNull
  <C extends Collection<EventualState<V>>> StreamForker<? extends ConsumableStream<V>> cache(
      @NotNull Factory<C> listFactory, @NotNull Mapper<? super C, ? extends C> dataMapper);

  @NotNull
  StreamForker<? extends ConsumableStream<V>> cache(int maxCount);

  @NotNull
  StreamForker<? extends ConsumableStream<V>> cache(int maxConsumers, int maxBatchSize,
      @NotNull Factory<? extends Queue<EventualState<V>>> queueFactory);

  @NotNull
  <C extends Collection<EventualState<V>>> StreamForker<? extends ConsumableStream<V>> cache(
      int maxConsumers, int maxBatchSize, @NotNull Factory<C> listFactory,
      @NotNull Mapper<? super C, ? extends C> dataMapper);

  @NotNull
  StreamForker<? extends ConsumableStream<V>> cache(int maxConsumers, int maxBatchSize,
      int maxCount);

  @NotNull
  ConsumableStream<V> catchFlatMap(
      @NotNull Mapper<? super Throwable, ? extends Consumable<V>> failureMapper,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  ConsumableStream<V> catchMap(@NotNull Mapper<? super Throwable, V> failureMapper,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  ConsumableStream<V> catchPeek(@NotNull Observer<? super Throwable> failureObserver,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  <M> ConsumableStream<M> chain(
      @NotNull Mapper<? super Consumer<M>, ? extends Consumer<V>> consumerMapper);

  @NotNull
  ConsumableStream<V> clone();

  @NotNull
  <M> ConsumableStream<M> collect(@NotNull Factory<M> collectorFactory,
      @NotNull CollectorAccumulator<? super V, ? super M> collectorAccumulator);

  @NotNull
  <M> M convert(@NotNull Mapper<? super ConsumableStream<V>, M> streamMapper);

  @NotNull
  ConsumableStream<V> filter(@NotNull Tester<? super V> valueTester);

  @NotNull
  <M> ConsumableStream<M> flatMap(@NotNull Mapper<? super V, ? extends Consumable<M>> valueMapper);

  @NotNull
  <M> ConsumableStream<M> flatMapOrdered(
      @NotNull Mapper<? super V, ? extends Consumable<M>> valueMapper);

  @NotNull
  <M> ConsumableStream<M> flatReduce(
      @NotNull Mapper<? super List<V>, ? extends Consumable<M>> valuesMapper);

  @NotNull
  <M> ConsumableStream<M> forEach(@NotNull LoopHandler<? super V, M> valueHandler);

  @NotNull
  <M> ConsumableStream<M> forEach(@NotNull LoopHandler<? super V, M> valueHandler,
      @NotNull LoopHandler<? super Throwable, M> failureHandler);

  @NotNull
  <M> ConsumableStream<M> forEach(@NotNull LoopHandler<? super V, M> valueHandler,
      @NotNull LoopHandler<? super Throwable, M> failureHandler,
      @NotNull LoopCompleter<M> completer);

  @NotNull
  <M> ConsumableStream<M> forEachOrdered(@NotNull LoopHandler<? super V, M> valueHandler);

  @NotNull
  <M> ConsumableStream<M> forEachOrdered(@NotNull LoopHandler<? super V, M> valueHandler,
      @NotNull LoopHandler<? super Throwable, M> failureHandler);

  @NotNull
  <M> ConsumableStream<M> forEachOrdered(@NotNull LoopHandler<? super V, M> valueHandler,
      @NotNull LoopHandler<? super Throwable, M> failureHandler,
      @NotNull LoopCompleter<M> completer);

  @NotNull
  <M> ConsumableStream<M> forLoop(
      @NotNull Mapper<? super Yielder<M>, ? extends Looper<? super V>> looperMapper);

  @NotNull
  <M> ConsumableStream<M> forLoopOrdered(
      @NotNull Mapper<? super Yielder<M>, ? extends Looper<? super V>> looperMapper);

  boolean isCancelled();

  boolean isComplete();

  @NotNull
  <M> ConsumableStream<M> join(
      @NotNull BiMapper<? super List<? extends ConsumableStream<? super V>>, ? super Consumer<M>,
          ? extends StreamJoiner<? super V>> joinerMapper,
      @NotNull ConsumableStream<?>... streams);

  @NotNull
  <M> ConsumableStream<M> join(
      @NotNull BiMapper<? super List<? extends ConsumableStream<? super V>>, ? super Consumer<M>,
          ? extends StreamJoiner<? super V>> joinerMapper,
      @NotNull List<? extends ConsumableStream<? super V>> streams);

  @NotNull
  <M> ConsumableStream<M> lift(
      @NotNull BiMapper<? super Producer, ? super Consumer<M>, ? extends Flow<V>> flowMapper);

  @NotNull
  <M> ConsumableStream<M> map(@NotNull Mapper<? super V, M> valueMapper);

  @NotNull
  ConsumableStream<V> parallel();

  @NotNull
  ConsumableStream<V> parallel(@NotNull Executor executor);

  @NotNull
  ConsumableStream<V> peek(@NotNull Observer<? super V> valueObserver);

  void pull();

  void pull(int minCount);

  void pull(int minCount, long timeout, @NotNull TimeUnit timeUnit);

  void pull(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  ConsumableStream<V> reduce(@NotNull Accumulator<V> accumulator);

  @NotNull
  <M> ConsumableStream<M> reduce(@NotNull Mapper<? super List<V>, M> valuesMapper);

  @NotNull
  ConsumableStream<V> reduce(@NotNull Factory<? extends V> valueFactory,
      @NotNull Accumulator<V> valueAccumulator);

  @NotNull
  ConsumableStream<V> sequential();

  @NotNull
  ConsumableStream<V> sequential(@NotNull Executor executor);

  @NotNull
  List<V> take();

  @NotNull
  List<V> take(int maxCount);

  @NotNull
  List<V> take(int maxCount, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<V> take(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  V takeFirst();

  @NotNull
  V takeFirst(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  V takeFirstOr(V defaultValue);

  @NotNull
  V takeFirstOr(V defaultValue, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  V takeLast();

  @NotNull
  V takeLast(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  V takeLastOr(V defaultValue);

  @NotNull
  V takeLastOr(V defaultValue, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  ConsumableStream<EventualState<V>> toStateStream();

  interface Accumulator<V> {

    V accumulate(V accumulated, V value) throws Exception;
  }

  interface CollectorAccumulator<V, R> {

    void accumulate(R collector, V value) throws Exception;
  }

  interface LoopCompleter<R> {

    void complete(long count, @NotNull Yielder<R> yielder) throws Exception;
  }

  interface LoopHandler<V, R> {

    boolean handle(long count, V value, @NotNull Yielder<R> yielder) throws Exception;
  }

  interface Looper<V> {

    void handleComplete(long count) throws Exception;

    boolean handleFailure(long count, @NotNull Throwable failure) throws Exception;

    boolean handleValue(long count, V value) throws Exception;
  }

  interface Yielder<V> {

    @NotNull
    Yielder<V> yield(@NotNull Consumable<? extends V> consumable);

    @NotNull
    Yielder<V> yieldFailure(@NotNull Throwable failure);

    @NotNull
    Yielder<V> yieldFailures(@Nullable Iterable<? extends Throwable> failures);

    @NotNull
    Yielder<V> yieldValue(V value);

    @NotNull
    Yielder<V> yieldValues(@Nullable Iterable<? extends V> values);
  }
}
