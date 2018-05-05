package dm.fates.eventual3;

import org.jetbrains.annotations.Nullable;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public abstract class EventualIterable<V> extends Eventual<Iterable<V>> {

  abstract void eventuallyEach(@Nullable Tester<? super V> valueTester,
      @Nullable Tester<? super Throwable> failureTester, @Nullable Action completeAction);
}
