package dm.fates.eventual2;

import org.jetbrains.annotations.NotNull;

/**
 * Created by davide-maestroni on 04/03/2018.
 */
public interface EventualState<V> {

  @NotNull
  Throwable failure();

  boolean isFailure();

  boolean isValue();

  V value();
}
