package dm.fates.eventual2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public interface EventualStream<V> extends Eventual<V> {

  // TODO: 03/04/2018 join(), is*(), using/try

  @NotNull
  EventualStream<V> buffer(@NotNull Factory<? extends Queue<EventualState<V>>> queueFactory);

  @NotNull
  <L extends List<EventualState<V>>> EventualStream<V> buffer(@NotNull Factory<L> listFactory,
      @NotNull Mapper<? super L, ? extends L> dataMapper);

  @NotNull
  EventualStream<V> buffer(int maxCount);

  @NotNull
  StreamForker<V> buffer(int maxConsumers, int maxBatchSize,
      @NotNull Factory<? extends Queue<EventualState<V>>> queueFactory);

  @NotNull
  <L extends List<EventualState<V>>> StreamForker<V> buffer(int maxConsumers, int maxBatchSize,
      @NotNull Factory<L> listFactory, @NotNull Mapper<? super L, ? extends L> dataMapper);

  @NotNull
  StreamForker<V> buffer(int maxConsumers, int maxBatchSize, int maxCount);

  @NotNull
  StreamForker<V> cache(@NotNull Factory<? extends Queue<EventualState<V>>> queueFactory);

  @NotNull
  <L extends List<EventualState<V>>> StreamForker<V> cache(@NotNull Factory<L> listFactory,
      @NotNull Mapper<? super L, ? extends L> dataMapper);

  @NotNull
  StreamForker<V> cache(int maxCount);

  @NotNull
  StreamForker<V> cache(int maxConsumers, int maxBatchSize,
      @NotNull Factory<? extends Queue<EventualState<V>>> queueFactory);

  @NotNull
  <L extends List<EventualState<V>>> StreamForker<V> cache(int maxConsumers, int maxBatchSize,
      @NotNull Factory<L> listFactory, @NotNull Mapper<? super L, ? extends L> dataMapper);

  @NotNull
  StreamForker<V> cache(int maxConsumers, int maxBatchSize, int maxCount);

  boolean cancel();

  @NotNull
  EventualStream<V> catchFlatMap(
      @NotNull Mapper<? super Throwable, ? extends Eventual<V>> failureMapper,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  EventualStream<V> catchMap(@NotNull Mapper<? super Throwable, V> failureMapper,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  EventualStream<V> catchPeek(@NotNull Observer<? super Throwable> failureObserver,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  <R> EventualStream<R> chain(
      @NotNull Mapper<? super Consumer<R>, ? extends Consumer<V>> consumerMapper);

  @NotNull
  EventualStream<V> clone();

  @NotNull
  <R> EventualStream<R> collect(@NotNull Factory<R> collectorFactory,
      @NotNull CollectorAccumulator<? super V, ? super R> collectorAccumulator);

  void consume();

  void consume(int minCount);

  @NotNull
  <R> R convert(@NotNull Converter<? super V, R> converter);

  @NotNull
  EventualStream<V> filter(@NotNull Tester<? super V> valueTester);

  @NotNull
  <R> EventualStream<R> flatMap(@NotNull Mapper<? super V, ? extends Eventual<R>> valueMapper);

  @NotNull
  <R> EventualStream<R> flatMapOrdered(
      @NotNull Mapper<? super V, ? extends Eventual<R>> valueMapper);

  @NotNull
  <R> EventualStream<R> flatReduce(
      @NotNull Mapper<? super List<V>, ? extends Eventual<R>> valuesMapper);

  @NotNull
  <R> EventualStream<R> forEach(@NotNull LoopHandler<? super V, R> valueHandler);

  @NotNull
  <R> EventualStream<R> forEach(@NotNull LoopHandler<? super V, R> valueHandler,
      @NotNull LoopHandler<? super Throwable, R> failureHandler);

  @NotNull
  <R> EventualStream<R> forEach(@NotNull LoopHandler<? super V, R> valueHandler,
      @NotNull LoopHandler<? super Throwable, R> failureHandler,
      @NotNull LoopCompleter<R> completer);

  @NotNull
  <R> EventualStream<R> forEachOrdered(@NotNull LoopHandler<? super V, R> valueHandler);

  @NotNull
  <R> EventualStream<R> forEachOrdered(@NotNull LoopHandler<? super V, R> valueHandler,
      @NotNull LoopHandler<? super Throwable, R> failureHandler);

  @NotNull
  <R> EventualStream<R> forEachOrdered(@NotNull LoopHandler<? super V, R> valueHandler,
      @NotNull LoopHandler<? super Throwable, R> failureHandler,
      @NotNull LoopCompleter<R> completer);

  @NotNull
  <R> EventualStream<R> forLoop(
      @NotNull Mapper<? super Yielder<R>, ? extends Looper<? super V, R>> looperMapper);

  @NotNull
  <R> EventualStream<R> forLoopOrdered(
      @NotNull Mapper<? super Yielder<R>, ? extends Looper<? super V, R>> looperMapper);

  boolean isCancelled();

  boolean isComplete();

  @NotNull
  <R> EventualStream<R> join(
      @NotNull BiMapper<? super List<? extends EventualStream<? super V>>, ? super Consumer<R>, ?
          extends StreamJoiner<? super V>> joinerMapper,
      @NotNull EventualStream<?>... streams);

  @NotNull
  <R> EventualStream<R> join(
      @NotNull BiMapper<? super List<? extends EventualStream<? super V>>, ? super Consumer<R>, ?
          extends StreamJoiner<? super V>> joinerMapper,
      @NotNull List<? extends EventualStream<? super V>> streams);

  @NotNull
  <R> EventualStream<R> lift(@NotNull Mapper<? super Flow<R>, ? extends Flow<V>> flowMapper);

  @NotNull
  <R> EventualStream<R> map(@NotNull Mapper<? super V, R> valueMapper);

  @NotNull
  EventualStream<V> parallel();

  @NotNull
  EventualStream<V> parallel(@NotNull Executor executor);

  @NotNull
  EventualStream<V> peek(@NotNull Observer<? super V> valueObserver);

  void pull();

  void pull(int minCount);

  void pull(int minCount, long timeout, @NotNull TimeUnit timeUnit);

  void pull(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  EventualStream<V> reduce(@NotNull Accumulator<V> accumulator);

  @NotNull
  <R> EventualStream<R> reduce(@NotNull Mapper<? super List<V>, R> valuesMapper);

  @NotNull
  EventualStream<V> reduce(@NotNull Factory<? extends V> valueFactory,
      @NotNull Accumulator<V> valueAccumulator);

  @NotNull
  EventualStream<V> sequential();

  @NotNull
  EventualStream<V> sequential(@NotNull Executor executor);

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
  V takeLatest();

  @NotNull
  V takeLatest(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  V takeLatestOr(V defaultValue);

  @NotNull
  V takeLatestOr(V defaultValue, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  EventualStream<EventualState<V>> toState();

  interface Accumulator<V> {

    V accumulate(V accumulated, V value) throws Exception;
  }

  interface CollectorAccumulator<V, R> {

    void accumulate(R collector, V value) throws Exception;
  }

  interface Converter<V, R> {

    R convert(@NotNull EventualStream<V> stream) throws Exception;
  }

  interface LoopCompleter<R> {

    void complete(long count, @NotNull Yielder<R> yielder) throws Exception;
  }

  interface LoopHandler<V, R> {

    boolean handle(long count, V value, @NotNull Yielder<R> yielder) throws Exception;
  }

  interface Looper<V, R> {

    void handleComplete(long count) throws Exception;

    boolean handleFailure(long count, @NotNull Throwable failure) throws Exception;

    boolean handleValue(long count, V value) throws Exception;
  }

  interface Yielder<V> {

    @NotNull
    Yielder<V> yield(@NotNull Eventual<? extends V> eventual);

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
