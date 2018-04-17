package dm.fates.eventual2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dm.fates.eventual2.Eventual.Producer;

/**
 * Created by davide-maestroni on 04/17/2018.
 */
public interface StreamJoiner<V> extends Producer {

  void handleComplete(int index);

  void handleDone();

  void handleFailure(int index, @NotNull Throwable failure);

  void handleFailures(int index, @Nullable Iterable<? extends Throwable> failures);

  void handleValue(int index, V value);

  void handleValues(int index, @Nullable Iterable<? extends V> values);
}
