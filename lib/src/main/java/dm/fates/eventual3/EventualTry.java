package dm.fates.eventual3;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public abstract class EventualTry<V> extends Eventual<V> {

  @NotNull
  public static <A> EventualTry<A> tryAwait(@NotNull AsyncNullaryFunction<A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1 extends Closeable, A> EventualTry<A> tryAwait(
      @NotNull Eventual<V1> firstEventual,
      @NotNull AsyncUnaryFunction<? super V1, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1 extends Closeable, V2 extends Closeable, A> EventualTry<A> tryAwait(
      @NotNull Eventual<V1> firstEventual, @NotNull Eventual<V2> secondEventual,
      @NotNull AsyncBinaryFunction<? super V1, ? super V2, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1 extends Closeable, V2 extends Closeable, V3 extends Closeable, A>
  EventualTry<A> tryAwait(
      @NotNull Eventual<V1> firstEventual, @NotNull Eventual<V2> secondEventual,
      @NotNull Eventual<V3> thirdEventual,
      @NotNull AsyncTernaryFunction<? super V1, ? super V2, ? super V3, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1 extends Closeable, V2 extends Closeable, V3 extends Closeable, V4 extends
      Closeable, A> EventualTry<A> tryAwait(
      @NotNull Eventual<V1> firstEventual, @NotNull Eventual<V2> secondEventual,
      @NotNull Eventual<V3> thirdEventual, @NotNull Eventual<V4> fourthEventual,
      @NotNull AsyncQuaternaryFunction<? super V1, ? super V2, ? super V3, ? super V4, A>
          asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1 extends Closeable, V2 extends Closeable, V3 extends Closeable, V4 extends
      Closeable, V5 extends Closeable, A> Eventual<A> tryAwait(
      @NotNull Eventual<V1> firstEventual, @NotNull Eventual<V2> secondEventual,
      @NotNull Eventual<V3> thirdEventual, @NotNull Eventual<V4> fourthEventual,
      @NotNull Eventual<V5> fifthEventual,
      @NotNull AsyncQuinaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super V5,
          A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1 extends Closeable, V2 extends Closeable, V3 extends Closeable, V4 extends
      Closeable, V5 extends Closeable, V6 extends Closeable, A> Eventual<A> tryAwait(
      @NotNull Eventual<V1> firstEventual, @NotNull Eventual<V2> secondEventual,
      @NotNull Eventual<V3> thirdEventual, @NotNull Eventual<V4> fourthEventual,
      @NotNull Eventual<V5> fifthEventual, @NotNull Eventual<V6> sixthEventual,
      @NotNull AsyncSenaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super V5, ?
          super V6, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1 extends Closeable, V2 extends Closeable, V3 extends Closeable, V4 extends
      Closeable, V5 extends Closeable, V6 extends Closeable, V7 extends Closeable, A> Eventual<A>
  tryAwait(
      @NotNull Eventual<V1> firstEventual, @NotNull Eventual<V2> secondEventual,
      @NotNull Eventual<V3> thirdEventual, @NotNull Eventual<V4> fourthEventual,
      @NotNull Eventual<V5> fifthEventual, @NotNull Eventual<V6> sixthEventual,
      @NotNull Eventual<V7> seventhEventual,
      @NotNull AsyncSeptenaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super V5,
          ? super V6, ? super V7, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1 extends Closeable, V2 extends Closeable, V3 extends Closeable, V4 extends
      Closeable, V5 extends Closeable, V6 extends Closeable, V7 extends Closeable, V8 extends
      Closeable, A> Eventual<A> tryAwait(
      @NotNull Eventual<V1> firstEventual, @NotNull Eventual<V2> secondEventual,
      @NotNull Eventual<V3> thirdEventual, @NotNull Eventual<V4> fourthEventual,
      @NotNull Eventual<V5> fifthEventual, @NotNull Eventual<V6> sixthEventual,
      @NotNull Eventual<V7> seventhEventual, @NotNull Eventual<V8> eighthEventual,
      @NotNull AsyncOctonaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super V5,
          ? super V6, ? super V7, ? super V8, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1 extends Closeable, V2 extends Closeable, V3 extends Closeable, V4 extends
      Closeable, V5 extends Closeable, V6 extends Closeable, V7 extends Closeable, V8 extends
      Closeable, V9 extends Closeable, A> Eventual<A> tryAwait(
      @NotNull Eventual<V1> firstEventual, @NotNull Eventual<V2> secondEventual,
      @NotNull Eventual<V3> thirdEventual, @NotNull Eventual<V4> fourthEventual,
      @NotNull Eventual<V5> fifthEventual, @NotNull Eventual<V6> sixthEventual,
      @NotNull Eventual<V7> seventhEventual, @NotNull Eventual<V8> eighthEventual,
      @NotNull Eventual<V9> ninthEventual,
      @NotNull AsyncNovenaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super V5,
          ? super V6, ? super V7, ? super V8, ? super V9, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1 extends Closeable, V2 extends Closeable, V3 extends Closeable, V4 extends
      Closeable, V5 extends Closeable, V6 extends Closeable, V7 extends Closeable, V8 extends
      Closeable, V9 extends Closeable, V10 extends Closeable, A> Eventual<A> tryAwait(
      @NotNull Eventual<V1> firstEventual, @NotNull Eventual<V2> secondEventual,
      @NotNull Eventual<V3> thirdEventual, @NotNull Eventual<V4> fourthEventual,
      @NotNull Eventual<V5> fifthEventual, @NotNull Eventual<V6> sixthEventual,
      @NotNull Eventual<V7> seventhEventual, @NotNull Eventual<V8> eighthEventual,
      @NotNull Eventual<V9> ninthEventual, @NotNull Eventual<V10> tenthEventual,
      @NotNull AsyncDenaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super V5, ?
          super V6, ? super V7, ? super V8, ? super V9, ? super V10, A> asyncFunction) {
    return null;
  }

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
