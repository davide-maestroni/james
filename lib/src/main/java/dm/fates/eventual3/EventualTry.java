package dm.fates.eventual3;

import org.jetbrains.annotations.NotNull;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public abstract class EventualTry<V> extends Eventual<V> {

  @NotNull
  public abstract <E extends Throwable> EventualTry<V> catchAwait(
      @NotNull Class<? extends E> firstClass,
      @NotNull AsyncUnaryFunction<? super E, ? extends V> asyncFunction);

  @NotNull
  public abstract <E extends Throwable> EventualTry<V> catchAwait(
      @NotNull Class<? extends E> firstClass, @NotNull Class<? extends E> secondClass,
      @NotNull AsyncUnaryFunction<? super E, ? extends V> asyncFunction);

  @NotNull
  public abstract <E extends Throwable> EventualTry<V> catchAwait(
      @NotNull Class<? extends E> firstClass, @NotNull Class<? extends E> secondClass,
      @NotNull Class<? extends E> thirdClass,
      @NotNull AsyncUnaryFunction<? super E, ? extends V> asyncFunction);

  @NotNull
  public abstract <E extends Throwable> EventualTry<V> catchAwait(
      @NotNull Class<? extends E> firstClass, @NotNull Class<? extends E> secondClass,
      @NotNull Class<? extends E> thirdClass, @NotNull Class<? extends E> fourthClass,
      @NotNull AsyncUnaryFunction<? super E, ? extends V> asyncFunction);

  @NotNull
  public abstract <E extends Throwable> EventualTry<V> catchAwait(
      @NotNull Class<? extends E> firstClass, @NotNull Class<? extends E> secondClass,
      @NotNull Class<? extends E> thirdClass, @NotNull Class<? extends E> fourthClass,
      @NotNull Class<? extends E> fifthClass,
      @NotNull AsyncUnaryFunction<? super E, ? extends V> asyncFunction);

  @NotNull
  public abstract <E extends Throwable> EventualTry<V> catchAwait(
      @NotNull Class<? extends E> firstClass, @NotNull Class<? extends E> secondClass,
      @NotNull Class<? extends E> thirdClass, @NotNull Class<? extends E> fourthClass,
      @NotNull Class<? extends E> fifthClass, @NotNull Class<? extends E> sixthClass,
      @NotNull AsyncUnaryFunction<? super E, ? extends V> asyncFunction);

  @NotNull
  public abstract <E extends Throwable> EventualTry<V> catchAwait(
      @NotNull Class<? extends E> firstClass, @NotNull Class<? extends E> secondClass,
      @NotNull Class<? extends E> thirdClass, @NotNull Class<? extends E> fourthClass,
      @NotNull Class<? extends E> fifthClass, @NotNull Class<? extends E> sixthClass,
      @NotNull Class<? extends E> seventhClass,
      @NotNull AsyncUnaryFunction<? super E, ? extends V> asyncFunction);

  @NotNull
  public abstract <E extends Throwable> EventualTry<V> catchAwait(
      @NotNull Class<? extends E> firstClass, @NotNull Class<? extends E> secondClass,
      @NotNull Class<? extends E> thirdClass, @NotNull Class<? extends E> fourthClass,
      @NotNull Class<? extends E> fifthClass, @NotNull Class<? extends E> sixthClass,
      @NotNull Class<? extends E> seventhClass, @NotNull Class<? extends E> eighthClass,
      @NotNull AsyncUnaryFunction<? super E, ? extends V> asyncFunction);

  @NotNull
  public abstract <E extends Throwable> EventualTry<V> catchAwait(
      @NotNull Class<? extends E> firstClass, @NotNull Class<? extends E> secondClass,
      @NotNull Class<? extends E> thirdClass, @NotNull Class<? extends E> fourthClass,
      @NotNull Class<? extends E> fifthClass, @NotNull Class<? extends E> sixthClass,
      @NotNull Class<? extends E> seventhClass, @NotNull Class<? extends E> eighthClass,
      @NotNull Class<? extends E> ninthClass,
      @NotNull AsyncUnaryFunction<? super E, ? extends V> asyncFunction);

  @NotNull
  public abstract <E extends Throwable> EventualTry<V> catchAwait(
      @NotNull Class<? extends E> firstClass, @NotNull Class<? extends E> secondClass,
      @NotNull Class<? extends E> thirdClass, @NotNull Class<? extends E> fourthClass,
      @NotNull Class<? extends E> fifthClass, @NotNull Class<? extends E> sixthClass,
      @NotNull Class<? extends E> seventhClass, @NotNull Class<? extends E> eighthClass,
      @NotNull Class<? extends E> ninthClass, @NotNull Class<? extends E> tenthClass,
      @NotNull AsyncUnaryFunction<? super E, ? extends V> asyncFunction);

  @NotNull
  public abstract Eventual<V> finallyAwait(
      @NotNull AsyncNullaryFunction<? extends V> asyncFunction);
}
