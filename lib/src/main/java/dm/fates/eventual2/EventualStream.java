package dm.fates.eventual2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import dm.fates.eventual.Mapper;
import dm.fates.eventual.Observer;
import dm.fates.eventual.Tester;

/**
 * Created by davide-maestroni on 04/03/2018.
 */
public interface EventualStream<V> extends Eventual<V> {

  // TODO: 03/04/2018 fork/cache, on(), join(), is*(), using/try

  @NotNull
  EventualStream<V> buffer(@NotNull Factory<? extends Queue<EventualState<V>>> queueFactory);

  @NotNull
  <L extends List<EventualState<V>>> EventualStream<V> buffer(@NotNull Factory<L> listFactory,
      @NotNull Mapper<? super L, ? extends L> dataMapper);

  @NotNull
  EventualStream<V> buffer(int maxCount);

  @NotNull
  EventualStream<V> buffer(int maxConsumers,
      @NotNull Factory<? extends Queue<EventualState<V>>> queueFactory);

  @NotNull
  <L extends List<EventualState<V>>> EventualStream<V> buffer(int maxConsumers,
      @NotNull Factory<L> listFactory, @NotNull Mapper<? super L, ? extends L> dataMapper);

  @NotNull
  EventualStream<V> buffer(int maxConsumers, int maxCount);

  boolean cancel(boolean mayInterruptIfRunning);

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
  EventualStream<V> clone();

  @NotNull
  <R> EventualStream<R> collect(@NotNull Factory<R> collectorFactory,
      @NotNull CollectorAccumulator<? super V, ? super R> collectorAccumulator);

  @NotNull
  EventualStream<V> consume(int maxCount);

  @NotNull
  <R> R convert(@NotNull Converter<? super V, R> converter);

  @NotNull
  <R> EventualStream<R> eventually(@NotNull OperationFactory<? super V, R> operationFactory);

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
  <R> EventualStream<R> forEach(@NotNull ValueLooper<? super V, R> valueLooper,
      @NotNull FailureLooper<R> failureLooper);

  @NotNull
  <R> EventualStream<R> forEach(@NotNull ValueLooper<? super V, R> valueLooper);

  @NotNull
  <R> EventualStream<R> forEachOrdered(@NotNull ValueLooper<? super V, R> valueLooper,
      @NotNull FailureLooper<R> failureLooper);

  @NotNull
  <R> EventualStream<R> forEachOrdered(@NotNull ValueLooper<? super V, R> valueLooper);

  @NotNull
  <R> EventualStream<R> forEachState(
      @NotNull Factory<? extends Looper<? super V, R>> looperFactory);

  @NotNull
  <R> EventualStream<R> forEachStateOrdered(
      @NotNull Factory<? extends Looper<? super V, R>> looperFactory);

  @NotNull
  List<V> get();

  @NotNull
  List<V> get(int maxCount);

  @NotNull
  List<V> get(int maxCount, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<V> get(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  V getLatest();

  @NotNull
  V getLatest(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  V getLatestOrDefault(V defaultValue);

  @NotNull
  V getLatestOrDefault(V defaultValue, long timeout, @NotNull TimeUnit timeUnit);

  boolean isCancelled();

  boolean isComplete();

  @NotNull
  <R> EventualStream<R> map(@NotNull Mapper<? super V, R> valueMapper);

  @NotNull
  EventualStream<V> peek(@NotNull Observer<? super V> valueObserver);

  @NotNull
  EventualStream<V> prefetch(int maxCount);

  void pull();

  void pull(int maxCount);

  void pull(int maxCount, long timeout, @NotNull TimeUnit timeUnit);

  void pull(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  EventualStream<V> reduce(@NotNull Accumulator<V> accumulator);

  @NotNull
  <R> EventualStream<R> reduce(@NotNull Mapper<? super List<V>, R> valuesMapper);

  @NotNull
  EventualStream<V> reduce(@NotNull Factory<? extends V> valueFactory,
      @NotNull Accumulator<V> valueAccumulator);

  interface Accumulator<V> {

    V accumulate(V accumulated, V value);
  }

  interface CollectorAccumulator<V, R> {

    void accumulate(R collector, V value);
  }

  interface Converter<V, R> {

    R convert(@NotNull EventualStream<V> stream);
  }

  interface FailureLooper<R> {

    boolean handle(long count, Throwable failure, Yielder<R> yielder);
  }

  interface Looper<V, R> {

    boolean handleFailure(long count, Throwable failure, Yielder<R> yielder);

    boolean handleValue(long count, V value, Yielder<R> yielder);
  }

  interface ValueLooper<V, R> {

    boolean handle(long count, V value, Yielder<R> yielder);
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
