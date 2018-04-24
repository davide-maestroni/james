package dm.fates.eventual2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dm.fates.eventual2.Consumable.Producer;

/**
 * Created by davide-maestroni on 04/17/2018.
 */
public interface StreamJoiner<V> extends Producer {

  void complete(int index);

  void done();

  void failure(int index, @NotNull Throwable failure);

  void failures(int index, @Nullable Iterable<? extends Throwable> failures);

  void value(int index, V value);

  void values(int index, @Nullable Iterable<? extends V> values);
}
