package dm.fates.eventual2;

import org.jetbrains.annotations.NotNull;

/**
 * Created by davide-maestroni on 04/16/2018.
 */
public interface StreamForker<S extends ConsumableStream> {

  @NotNull
  S fork();
}
