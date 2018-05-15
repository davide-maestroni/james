package dm.fates.eventual3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public abstract class EventualIterable<V> extends Eventual<Iterable<V>> {

  @NotNull
  public static <V> EventualIterable<V> allAwait(@NotNull Eventual<V>... eventuals) {
    return null;
  }

  @NotNull
  public static <V> EventualIterable<V> allAwait(
      @NotNull Iterable<? extends Eventual<V>> eventuals) {
    return null;
  }

  @NotNull
  public static <V> EventualIterable<V> andAwait(@NotNull Eventual<V>... eventuals) {
    return null;
  }

  @NotNull
  public static <V> EventualIterable<V> andAwait(
      @NotNull Iterable<? extends Eventual<V>> eventuals) {
    return null;
  }

  @NotNull
  public static <V> EventualIterable<V> array(@NotNull V... values) {
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
  public static <V, A> EventualIterable<A> eachAwait(@NotNull EventualIterable<V> eventualIterable,
      @NotNull AsyncUnaryFunction<? super V, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <V> EventualIterable<V> empty() {
    return null;
  }

  @NotNull
  public static <V> EventualIterable<V> eventualIterable(
      @NotNull Eventual<? extends Iterable<V>> eventual) {
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
  public static <P1, P2, P3, P4, V, A> Eventual<A> forAwait(@NotNull Provider<P1> firstProvider,
      @NotNull Provider<P2> secondProvider, @NotNull Provider<P3> thirdProvider,
      @NotNull Provider<P4> fourthProvider, @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumQuaternaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super V,
          A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, V, A> Eventual<A> forAwait(@NotNull Provider<P1> firstProvider,
      @NotNull Provider<P2> secondProvider, @NotNull Provider<P3> thirdProvider,
      @NotNull Provider<P4> fourthProvider, @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumQuaternaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super V,
          A> asyncFunction,
      @NotNull EnumQuaternaryProvider<? super P1, ? super P2, ? super P3, ? super P4, A>
          asyncProvider) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, P5, V, A> Eventual<A> forAwait(@NotNull Provider<P1> firstProvider,
      @NotNull Provider<P2> secondProvider, @NotNull Provider<P3> thirdProvider,
      @NotNull Provider<P4> fourthProvider, @NotNull Provider<P5> fifthProvider,
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumQuinaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super V, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, P5, V, A> Eventual<A> forAwait(@NotNull Provider<P1> firstProvider,
      @NotNull Provider<P2> secondProvider, @NotNull Provider<P3> thirdProvider,
      @NotNull Provider<P4> fourthProvider, @NotNull Provider<P5> fifthProvider,
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumQuinaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super V, A> asyncFunction,
      @NotNull EnumQuinaryProvider<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, A>
          asyncProvider) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, P5, P6, V, A> Eventual<A> forAwait(
      @NotNull Provider<P1> firstProvider, @NotNull Provider<P2> secondProvider,
      @NotNull Provider<P3> thirdProvider, @NotNull Provider<P4> fourthProvider,
      @NotNull Provider<P5> fifthProvider, @NotNull Provider<P6> sixthProvider,
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumSenaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super P6, ? super V, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, P5, P6, V, A> Eventual<A> forAwait(
      @NotNull Provider<P1> firstProvider, @NotNull Provider<P2> secondProvider,
      @NotNull Provider<P3> thirdProvider, @NotNull Provider<P4> fourthProvider,
      @NotNull Provider<P5> fifthProvider, @NotNull Provider<P6> sixthProvider,
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumSenaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super P6, ? super V, A> asyncFunction,
      @NotNull EnumSenaryProvider<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super P6, A> asyncProvider) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, P5, P6, P7, V, A> Eventual<A> forAwait(
      @NotNull Provider<P1> firstProvider, @NotNull Provider<P2> secondProvider,
      @NotNull Provider<P3> thirdProvider, @NotNull Provider<P4> fourthProvider,
      @NotNull Provider<P5> fifthProvider, @NotNull Provider<P6> sixthProvider,
      @NotNull Provider<P7> seventhProvider, @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumSeptenaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super P5,
          ? super P6, ? super P7, ? super V, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, P5, P6, P7, V, A> Eventual<A> forAwait(
      @NotNull Provider<P1> firstProvider, @NotNull Provider<P2> secondProvider,
      @NotNull Provider<P3> thirdProvider, @NotNull Provider<P4> fourthProvider,
      @NotNull Provider<P5> fifthProvider, @NotNull Provider<P6> sixthProvider,
      @NotNull Provider<P7> seventhProvider, @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumSeptenaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super P5,
          ? super P6, ? super P7, ? super V, A> asyncFunction,
      @NotNull EnumSeptenaryProvider<? super P1, ? super P2, ? super P3, ? super P4, ? super P5,
          ? super P6, ? super P7, A> asyncProvider) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, P5, P6, P7, P8, V, A> Eventual<A> forAwait(
      @NotNull Provider<P1> firstProvider, @NotNull Provider<P2> secondProvider,
      @NotNull Provider<P3> thirdProvider, @NotNull Provider<P4> fourthProvider,
      @NotNull Provider<P5> fifthProvider, @NotNull Provider<P6> sixthProvider,
      @NotNull Provider<P7> seventhProvider, @NotNull Provider<P8> eighthProvider,
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumOctonaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super P6, ? super P7, ? super P8, ? super V, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, P5, P6, P7, P8, V, A> Eventual<A> forAwait(
      @NotNull Provider<P1> firstProvider, @NotNull Provider<P2> secondProvider,
      @NotNull Provider<P3> thirdProvider, @NotNull Provider<P4> fourthProvider,
      @NotNull Provider<P5> fifthProvider, @NotNull Provider<P6> sixthProvider,
      @NotNull Provider<P7> seventhProvider, @NotNull Provider<P8> eighthProvider,
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumOctonaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super P6, ? super P7, ? super P8, ? super V, A> asyncFunction,
      @NotNull EnumOctonaryProvider<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super P6, ? super P7, ? super P8, A> asyncProvider) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, P5, P6, P7, P8, P9, V, A> Eventual<A> forAwait(
      @NotNull Provider<P1> firstProvider, @NotNull Provider<P2> secondProvider,
      @NotNull Provider<P3> thirdProvider, @NotNull Provider<P4> fourthProvider,
      @NotNull Provider<P5> fifthProvider, @NotNull Provider<P6> sixthProvider,
      @NotNull Provider<P7> seventhProvider, @NotNull Provider<P8> eighthProvider,
      @NotNull Provider<P9> ninthProvider, @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumNovenaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super P6, ? super P7, ? super P8, ? super P9, ? super V, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, P5, P6, P7, P8, P9, V, A> Eventual<A> forAwait(
      @NotNull Provider<P1> firstProvider, @NotNull Provider<P2> secondProvider,
      @NotNull Provider<P3> thirdProvider, @NotNull Provider<P4> fourthProvider,
      @NotNull Provider<P5> fifthProvider, @NotNull Provider<P6> sixthProvider,
      @NotNull Provider<P7> seventhProvider, @NotNull Provider<P8> eighthProvider,
      @NotNull Provider<P9> ninthProvider, @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumNovenaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super P6, ? super P7, ? super P8, ? super P9, ? super V, A> asyncFunction,
      @NotNull EnumNovenaryProvider<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super P6, ? super P7, ? super P8, ? super P9, A> asyncProvider) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, V, A> Eventual<A> forAwait(
      @NotNull Provider<P1> firstProvider, @NotNull Provider<P2> secondProvider,
      @NotNull Provider<P3> thirdProvider, @NotNull Provider<P4> fourthProvider,
      @NotNull Provider<P5> fifthProvider, @NotNull Provider<P6> sixthProvider,
      @NotNull Provider<P7> seventhProvider, @NotNull Provider<P8> eighthProvider,
      @NotNull Provider<P9> ninthProvider, @NotNull Provider<P10> tenthProvider,
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumDenaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super P6, ? super P7, ? super P8, ? super P9, ? super P10, ? super V, A> asyncFunction) {
    return null;
  }

  @NotNull
  public static <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, V, A> Eventual<A> forAwait(
      @NotNull Provider<P1> firstProvider, @NotNull Provider<P2> secondProvider,
      @NotNull Provider<P3> thirdProvider, @NotNull Provider<P4> fourthProvider,
      @NotNull Provider<P5> fifthProvider, @NotNull Provider<P6> sixthProvider,
      @NotNull Provider<P7> seventhProvider, @NotNull Provider<P8> eighthProvider,
      @NotNull Provider<P9> ninthProvider, @NotNull Provider<P10> tenthProvider,
      @NotNull EventualIterable<V> eventualIterable,
      @NotNull EnumDenaryFunction<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super P6, ? super P7, ? super P8, ? super P9, ? super P10, ? super V, A> asyncFunction,
      @NotNull EnumDenaryProvider<? super P1, ? super P2, ? super P3, ? super P4, ? super P5, ?
          super P6, ? super P7, ? super P8, ? super P9, ? super P10, A> asyncProvider) {
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

  public abstract void evaluateEach(@Nullable Tester<? super V> valueTester,
      @Nullable Tester<? super Throwable> errorTester, @Nullable Action completeAction);

  @NotNull
  public abstract EventualIterable<V> logWithName(@Nullable String loggerName);

  @NotNull
  public abstract EventualIterable<V> on(@Nullable Executor executor);

  public interface Yielder<V> {

    @NotNull
    Yielder<V> yield(V value);
  }
}
