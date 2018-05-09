package dm.fates.eventual3;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.Queue;

/**
 * Created by davide-maestroni on 05/07/2018.
 */
public abstract class EventualChannel<V> extends EventualIterable<V> implements Closeable {

  @NotNull
  public static <V> EventualChannel<V> channel() {
    return null;
  }

  @NotNull
  public static <V> EventualChannel<V> channel(@NotNull Queue<V> queue) {
    return null;
  }

  public abstract void close();

  @NotNull
  public abstract EventualChannel<V> push(@NotNull Eventual<? extends V> value);

  @NotNull
  public abstract EventualChannel<V> push(@NotNull EventualIterable<? extends V> values);
}
