package dm.fates.eventual3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public abstract class Eventual<V> {

  @NotNull
  public static <V> EventualIterable<V> allAwait(Eventual<V>... eventuals) {
    return null;
  }

  @NotNull
  public static <V> EventualIterable<V> andAwait(Eventual<V>... eventuals) {
    return null;
  }

  @NotNull
  public static <V> Eventual<V> anyAwait(Eventual<V>... eventuals) {
    return null;
  }

  @NotNull
  public static <V> EventualIterable<V> array(@NotNull V... values) {
    return null;
  }

  @NotNull
  public static <A> Eventual<A> await(@NotNull AsyncNullaryFunction<A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, A> Eventual<A> await(@NotNull Eventual<V1> firstEventual,
      @NotNull AsyncUnaryFunction<? super V1, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, A> Eventual<A> await(@NotNull Eventual<V1> firstEventual,
      @NotNull Eventual<V2> secondEventual,
      @NotNull AsyncBinaryFunction<? super V1, ? super V2, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, A> Eventual<A> await(@NotNull Eventual<V1> firstEventual,
      @NotNull Eventual<V2> secondEventual, @NotNull Eventual<V3> thirdEventual,
      @NotNull AsyncTernaryFunction<? super V1, ? super V2, ? super V3, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, A> Eventual<A> await(@NotNull Eventual<V1> firstEventual,
      @NotNull Eventual<V2> secondEventual, @NotNull Eventual<V3> thirdEventual,
      @NotNull Eventual<V4> fourthEventual,
      @NotNull AsyncQuaternaryFunction<? super V1, ? super V2, ? super V3, ? super V4, A>
          asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, V5, A> Eventual<A> await(@NotNull Eventual<V1> firstEventual,
      @NotNull Eventual<V2> secondEventual, @NotNull Eventual<V3> thirdEventual,
      @NotNull Eventual<V4> fourthEventual, @NotNull Eventual<V5> fifthEventual,
      @NotNull AsyncQuinaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super V5,
          A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, V5, V6, A> Eventual<A> await(@NotNull Eventual<V1> firstEventual,
      @NotNull Eventual<V2> secondEventual, @NotNull Eventual<V3> thirdEventual,
      @NotNull Eventual<V4> fourthEventual, @NotNull Eventual<V5> fifthEventual,
      @NotNull Eventual<V6> sixthEventual,
      @NotNull AsyncSenaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super V5, ?
          super V6, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, V5, V6, V7, A> Eventual<A> await(
      @NotNull Eventual<V1> firstEventual, @NotNull Eventual<V2> secondEventual,
      @NotNull Eventual<V3> thirdEventual, @NotNull Eventual<V4> fourthEventual,
      @NotNull Eventual<V5> fifthEventual, @NotNull Eventual<V6> sixthEventual,
      @NotNull Eventual<V7> seventhEventual,
      @NotNull AsyncSeptenaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super V5,
          ? super V6, ? super V7, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, V5, V6, V7, V8, A> Eventual<A> await(
      @NotNull Eventual<V1> firstEventual, @NotNull Eventual<V2> secondEventual,
      @NotNull Eventual<V3> thirdEventual, @NotNull Eventual<V4> fourthEventual,
      @NotNull Eventual<V5> fifthEventual, @NotNull Eventual<V6> sixthEventual,
      @NotNull Eventual<V7> seventhEventual, @NotNull Eventual<V8> eighthEventual,
      @NotNull AsyncOctonaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super V5,
          ? super V6, ? super V7, ? super V8, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, V5, V6, V7, V8, V9, A> Eventual<A> await(
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
  public static <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, A> Eventual<A> await(
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
  public static <V> V blockingAwait(@NotNull Eventual<V> eventual) {
    return null;
  }

  @NotNull
  public static <V> V blockingAwait(@NotNull Eventual<V> eventual, long timeout,
      @NotNull TimeUnit timeUnit) {
    return null;
  }

  @NotNull
  public static <V> Iterable<V> blockingIterable(@NotNull EventualIterable<V> eventual) {
    return null;
  }

  @NotNull
  public static <V> Iterable<V> blockingIterable(@NotNull EventualIterable<V> eventual,
      long timeout, @NotNull TimeUnit timeUnit) {
    return null;
  }

  @NotNull
  public static EventualIterable<ByteBuffer> buffers(@NotNull InputStream stream) {
    return null;
  }

  @NotNull
  public static <V> EventualIterable<V> eventualIterable(
      @NotNull Iterable<? extends Eventual<V>> eventuals) {
    return null;
  }

  @NotNull
  public static <V> Eventual<V> failure(@NotNull Throwable failure) {
    return null;
  }

  @NotNull
  public static <V, A> Eventual<A> forAwait(@NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumNullaryFunction<? super V, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V, A> Eventual<A> forAwait(@NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumNullaryFunction<? super V, A> asyncFunction,
      @NotNull EnumNullaryProvider<A> asyncProvider) {
    return null;
  }

  @NotNull
  public static <P1, V, A> Eventual<A> forAwait(@NotNull Provider<P1> firstProvider,
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumUnaryFunction<? super P1, ? super V, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <P1, V, A> Eventual<A> forAwait(@NotNull Provider<P1> firstProvider,
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumUnaryFunction<? super P1, ? super V, A> asyncFunction,
      @NotNull EnumUnaryProvider<? super P1, A> asyncProvider) {
    return null;
  }

  @NotNull
  public static <P1, P2, V, A> Eventual<A> forAwait(@NotNull Provider<P1> firstProvider,
      @NotNull Provider<P2> secondProvider, @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumBinaryFunction<? super P1, ? super P2, ? super V, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <P1, P2, V, A> Eventual<A> forAwait(@NotNull Provider<P1> firstProvider,
      @NotNull Provider<P2> secondProvider, @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumBinaryFunction<? super P1, ? super P2, ? super V, A> asyncFunction,
      @NotNull EnumBinaryProvider<? super P1, ? super P2, A> asyncProvider) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, V, A> Eventual<A> forAwait(@NotNull Provider<P1> firstProvider,
      @NotNull Provider<P2> secondProvider, @NotNull Provider<P3> thirdProvider,
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumTernaryFunction<? super P1, ? super P2, ? super P3, ? super V, A>
          asyncFunction) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, V, A> Eventual<A> forAwait(@NotNull Provider<P1> firstProvider,
      @NotNull Provider<P2> secondProvider, @NotNull Provider<P3> thirdProvider,
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumTernaryFunction<? super P1, ? super P2, ? super P3, ? super V, A> asyncFunction,
      @NotNull EnumTernaryProvider<? super P1, ? super P2, ? super P3, A> asyncProvider) {
    return null;
  }

  @NotNull
  public static <V, A> EventualIterable<A> forEachAwait(
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull AsyncUnaryFunction<? super V, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V> EventualIterable<V> generator(
      @NotNull Observer<? super Yielder<? super V>> yielderObserver) {
    return null;
  }

  @NotNull
  public static <V> EventualIterable<V> iterable(@NotNull Iterable<V> values) {
    return null;
  }

  @NotNull
  public static <V> Eventual<V> orAwait(Eventual<V>... eventuals) {
    return null;
  }

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
  public static <V> Eventual<V> value(V value) {
    return null;
  }

  @NotNull
  public static <V> Eventual<V> voidValue() {
    return null;
  }

  public abstract boolean cancel();

  public abstract void evaluate();

  public abstract void eventually(@Nullable Observer<? super V> valueObserver,
      @Nullable Observer<? super Throwable> failureObserver, @Nullable Action voidAction);

  public interface Yielder<V> {

    @NotNull
    Yielder<V> yield(V value);
  }
}
