package dm.fates.eventual2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

import dm.fates.eventual.Mapper;
import dm.fates.eventual.Observer;

/**
 * Created by davide-maestroni on 04/03/2018.
 */
public interface EventualValue<V> extends Eventual<V> {

  @NotNull
  EventualValue<V> buffer();

  boolean cancel(boolean mayInterruptIfRunning);

  @NotNull
  EventualValue<V> catchFlatMap(
      @NotNull Mapper<? super Throwable, ? extends EventualValue<V>> failureMapper,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  EventualValue<V> catchMap(@NotNull Mapper<? super Throwable, V> failureMapper,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  EventualValue<V> catchPeek(@NotNull Observer<? super Throwable> failureObserver,
      @Nullable Class<?>... exceptionClasses);

  @NotNull
  EventualValue<V> consume();

  @NotNull
  <R> R convert(@NotNull Converter<? super V, R> converter);

  @NotNull
  <R> EventualValue<R> eventually(@NotNull OperationFactory<? super V, R> factory);

  @NotNull
  <R> EventualValue<R> flatMap(@NotNull Mapper<? super V, ? extends EventualValue<R>> valueMapper);

  @NotNull
  V get();

  @NotNull
  V get(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  V getOrDefault(V defaultValue);

  @NotNull
  V getOrDefault(V defaultValue, long timeout, @NotNull TimeUnit timeUnit);

  boolean isCancelled();

  boolean isComplete();

  @NotNull
  <R> EventualValue<R> map(@NotNull Mapper<? super V, R> valueMapper);

  @NotNull
  EventualValue<V> peek(@NotNull Observer<? super V> valueObserver);

  @NotNull
  EventualValue<V> prefetch();

  void pull();

  interface Converter<V, R> {

    R convert(@NotNull EventualValue<V> stream);
  }
}
