package dm.fates.eventual3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public abstract class Eventual<V> {

  public static final Eventual<?> VOID = new Eventual<Object>() {

    @Override
    public boolean cancel() {
      return false;
    }

    @Override
    public void evaluate() {
    }

    @Override
    public void evaluate(@Nullable final Observer<? super Object> valueObserver,
        @Nullable final Observer<? super Throwable> errorObserver,
        @Nullable final Action voidAction) {
      if (voidAction == null) {
        return;
      }

      try {
        voidAction.perform();

      } catch (final Exception ignored) {
        // just ignore it
      }
    }
  };

  @NotNull
  public static <V> Eventual<V> anyAwait(Eventual<V>... eventuals) {
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
  public static <V> Eventual<V> error(@NotNull Throwable error) {
    return null;
  }

  @NotNull
  public static <V> Eventual<V> orAwait(Eventual<V>... eventuals) {
    return null;
  }

  @NotNull
  public static <V> Eventual<V> value(V value) {
    return null;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <V> Eventual<V> voidValue() {
    return (Eventual<V>) VOID;
  }

  public abstract boolean cancel();

  public abstract void evaluate();

  public abstract void evaluate(@Nullable Observer<? super V> valueObserver,
      @Nullable Observer<? super Throwable> errorObserver, @Nullable Action voidAction);
}
