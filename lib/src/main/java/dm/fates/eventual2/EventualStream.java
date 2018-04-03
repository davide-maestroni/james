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

  // TODO: 03/04/2018 fork(), on(), join(), is*()

  @NotNull
  EventualStream<V> buffer(int count);

  @NotNull
  EventualStream<V> buffer(
      @NotNull Mapper<? super List<EventualState<V>>, ? extends List<EventualState<V>>> dataMapper);

  @NotNull
  EventualStream<V> buffer(@NotNull QueueFactory<V> factory);

  boolean cancel(boolean mayInterruptIfRunning);

  @NotNull
  EventualStream<V> catchFlatMap(
      @NotNull Mapper<? super Throwable, ? extends EventualStream<V>> failureMapper,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  EventualStream<V> catchMap(@NotNull Mapper<? super Throwable, V> failureMapper,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  EventualStream<V> catchPeek(@NotNull Observer<? super Throwable> failureObserver,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  <R> EventualValue<R> collect(@NotNull CollectorFactory<R> factory,
      @NotNull CollectorAccumulator<? super V, R> accumulator);

  @NotNull
  EventualStream<V> consume(int count);

  @NotNull
  <R> R convert(@NotNull Converter<? super V, R> converter);

  @NotNull
  <R> EventualStream<R> eventually(@NotNull OperationFactory<? super V, R> factory);

  @NotNull
  EventualStream<V> filter(@NotNull Tester<? super V> valueTester);

  @NotNull
  <R> EventualStream<R> flatMap(
      @NotNull Mapper<? super V, ? extends EventualStream<R>> valueMapper);

  @NotNull
  <R> EventualValue<R> flatReduce(
      @NotNull Mapper<? super List<V>, ? extends EventualValue<R>> valuesMapper);

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
  <R> EventualStream<R> forEachState(@NotNull LooperFactory<? super V, R> looperFactory);

  @NotNull
  <R> EventualStream<R> forEachStateOrdered(@NotNull LooperFactory<? super V, R> looperFactory);

  @NotNull
  List<V> get();

  @NotNull
  List<V> get(int count);

  @NotNull
  List<V> get(int count, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<V> get(long timeout, @NotNull TimeUnit timeUnit);

  boolean isCancelled();

  boolean isComplete();

  @NotNull
  <R> EventualStream<R> map(@NotNull Mapper<? super V, R> valueMapper);

  @NotNull
  EventualStream<V> peek(@NotNull Observer<? super V> valueObserver);

  @NotNull
  EventualStream<V> prefetch(int count);

  void pull();

  void pull(int maxCount);

  @NotNull
  EventualValue<V> reduce(@NotNull Accumulator<V> accumulator);

  @NotNull
  <R> EventualValue<R> reduce(@NotNull Mapper<? super List<V>, R> valuesMapper);

  @NotNull
  EventualValue<V> reduce(@NotNull ValueFactory<V> factory, @NotNull Accumulator<V> accumulator);

  interface Accumulator<V> {

    V accumulate(V accumulated, V value);
  }

  interface CollectorAccumulator<V, R> {

    void accumulate(R collector, V value);
  }

  interface CollectorFactory<V> {

    @NotNull
    V create();
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

  interface LooperFactory<V, R> {

    @NotNull
    Looper<V, R> create();
  }

  interface QueueFactory<V> {

    @NotNull
    Queue<EventualState<V>> create();
  }

  interface ValueFactory<V> {

    @NotNull
    V create();
  }

  interface ValueLooper<V, R> {

    boolean handle(long count, V value, Yielder<R> yielder);
  }

  interface Yielder<V> {

    @NotNull
    Yielder<V> yieldFailure(@NotNull Throwable failure);

    @NotNull
    Yielder<V> yieldFailures(@Nullable Iterable<? extends Throwable> failures);

    @NotNull
    Yielder<V> yieldStream(@NotNull EventualStream<? extends V> stream);

    @NotNull
    Yielder<V> yieldValue(V value);

    @NotNull
    Yielder<V> yieldValues(@Nullable Iterable<? extends V> values);
  }
}
