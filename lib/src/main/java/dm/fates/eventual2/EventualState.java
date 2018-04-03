package dm.fates.eventual2;

/**
 * Created by davide-maestroni on 04/03/2018.
 */
public interface EventualState<V> {

  Throwable failure();

  boolean isFailure();

  boolean isValue();

  V value();
}
