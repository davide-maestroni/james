package dm.fates.eventual2;

import org.jetbrains.annotations.NotNull;

/**
 * Created by davide-maestroni on 04/04/2018.
 */
public interface Factory<V> {

  @NotNull
  V create() throws Exception;
}
