package dm.fates.eventual3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public abstract class Eventual<V> {

  private static final Eventual<?> VOID = new Eventual<Object>() {

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

    @NotNull
    public Eventual<Object> logWithName(@Nullable final String loggerName) {
      return this;
    }

    @NotNull
    public Eventual<Object> on(@Nullable final Executor executor) {
      return null;
    }
  };

  @NotNull
  public static <V> Eventual<V> anyAwait(@Nullable final Eventual<V>... eventuals) {
    return null;
  }

  @NotNull
  public static <V> Eventual<V> anyAwait(
      @Nullable final Iterable<? extends Eventual<V>> eventuals) {
    return null;
  }

  @NotNull
  public static <A> Eventual<A> await(@NotNull final AsyncNullaryFunction<A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, A> Eventual<A> await(@NotNull final Eventual<V1> firstEventual,
      @NotNull final AsyncUnaryFunction<? super V1, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, A> Eventual<A> await(@NotNull final Eventual<V1> firstEventual,
      @NotNull final Eventual<V2> secondEventual,
      @NotNull final AsyncBinaryFunction<? super V1, ? super V2, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, A> Eventual<A> await(@NotNull final Eventual<V1> firstEventual,
      @NotNull final Eventual<V2> secondEventual, @NotNull final Eventual<V3> thirdEventual,
      @NotNull final AsyncTernaryFunction<? super V1, ? super V2, ? super V3, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, A> Eventual<A> await(@NotNull final Eventual<V1> firstEventual,
      @NotNull final Eventual<V2> secondEventual, @NotNull final Eventual<V3> thirdEventual,
      @NotNull final Eventual<V4> fourthEventual,
      @NotNull final AsyncQuaternaryFunction<? super V1, ? super V2, ? super V3, ? super V4, A>
          asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, V5, A> Eventual<A> await(@NotNull final Eventual<V1> firstEventual,
      @NotNull final Eventual<V2> secondEventual, @NotNull final Eventual<V3> thirdEventual,
      @NotNull final Eventual<V4> fourthEventual, @NotNull final Eventual<V5> fifthEventual,
      @NotNull final AsyncQuinaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super
          V5, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, V5, V6, A> Eventual<A> await(
      @NotNull final Eventual<V1> firstEventual, @NotNull final Eventual<V2> secondEventual,
      @NotNull final Eventual<V3> thirdEventual, @NotNull final Eventual<V4> fourthEventual,
      @NotNull final Eventual<V5> fifthEventual, @NotNull final Eventual<V6> sixthEventual,
      @NotNull final AsyncSenaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super
          V5, ? super V6, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, V5, V6, V7, A> Eventual<A> await(
      @NotNull final Eventual<V1> firstEventual, @NotNull final Eventual<V2> secondEventual,
      @NotNull final Eventual<V3> thirdEventual, @NotNull final Eventual<V4> fourthEventual,
      @NotNull final Eventual<V5> fifthEventual, @NotNull final Eventual<V6> sixthEventual,
      @NotNull final Eventual<V7> seventhEventual,
      @NotNull final AsyncSeptenaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ?
          super V5, ? super V6, ? super V7, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, V5, V6, V7, V8, A> Eventual<A> await(
      @NotNull final Eventual<V1> firstEventual, @NotNull final Eventual<V2> secondEventual,
      @NotNull final Eventual<V3> thirdEventual, @NotNull final Eventual<V4> fourthEventual,
      @NotNull final Eventual<V5> fifthEventual, @NotNull final Eventual<V6> sixthEventual,
      @NotNull final Eventual<V7> seventhEventual, @NotNull final Eventual<V8> eighthEventual,
      @NotNull final AsyncOctonaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ?
          super V5, ? super V6, ? super V7, ? super V8, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, V5, V6, V7, V8, V9, A> Eventual<A> await(
      @NotNull final Eventual<V1> firstEventual, @NotNull final Eventual<V2> secondEventual,
      @NotNull final Eventual<V3> thirdEventual, @NotNull final Eventual<V4> fourthEventual,
      @NotNull final Eventual<V5> fifthEventual, @NotNull final Eventual<V6> sixthEventual,
      @NotNull final Eventual<V7> seventhEventual, @NotNull final Eventual<V8> eighthEventual,
      @NotNull final Eventual<V9> ninthEventual,
      @NotNull final AsyncNovenaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ?
          super V5, ? super V6, ? super V7, ? super V8, ? super V9, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, A> Eventual<A> await(
      @NotNull final Eventual<V1> firstEventual, @NotNull final Eventual<V2> secondEventual,
      @NotNull final Eventual<V3> thirdEventual, @NotNull final Eventual<V4> fourthEventual,
      @NotNull final Eventual<V5> fifthEventual, @NotNull final Eventual<V6> sixthEventual,
      @NotNull final Eventual<V7> seventhEventual, @NotNull final Eventual<V8> eighthEventual,
      @NotNull final Eventual<V9> ninthEventual, @NotNull final Eventual<V10> tenthEventual,
      @NotNull final AsyncDenaryFunction<? super V1, ? super V2, ? super V3, ? super V4, ? super
          V5, ? super V6, ? super V7, ? super V8, ? super V9, ? super V10, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V> V blockingAwait(@NotNull final Eventual<V> eventual) {
    return null;
  }

  @NotNull
  public static <V> V blockingAwait(@NotNull final Eventual<V> eventual, long timeout,
      @NotNull TimeUnit timeUnit) {
    return null;
  }

  @NotNull
  public static <V> Eventual<V> error(@NotNull final Throwable error) {
    return null;
  }

  @NotNull
  public static <V> Eventual<V> orAwait(@Nullable final Eventual<V>... eventuals) {
    return null;
  }

  @NotNull
  public static <V> Eventual<V> orAwait(@Nullable final Iterable<? extends Eventual<V>> eventuals) {
    return null;
  }

  @NotNull
  public static <V> Eventual<V> value(final V value) {
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

  @NotNull
  public abstract Eventual<V> logWithName(@Nullable String loggerName);

  @NotNull
  public abstract Eventual<V> on(@Nullable Executor executor);
}
