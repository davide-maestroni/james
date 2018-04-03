package dm.fates.eventual2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by davide-maestroni on 03/22/2018.
 */
public interface Eventual<V> {

  @NotNull
  <R> Eventual<R> eventually(@NotNull OperationFactory<? super V, R> factory);

  interface Consumer<V> {

    void complete();

    @NotNull
    Consumer<V> failure(@NotNull Throwable failure);

    @NotNull
    Consumer<V> failures(@Nullable Iterable<? extends Throwable> failures);

    @NotNull
    Consumer<V> value(V value);

    @NotNull
    Consumer<V> values(@Nullable Iterable<? extends V> values);
  }

  interface Controller {

    void cancel(boolean mayInterruptIfRunning);

    void consume(int maxCount);
  }

  interface Operation<V, R> {

    void cancel(boolean mayInterruptIfRunning, @NotNull Consumer<R> consumer) throws Exception;

    void complete() throws Exception;

    void consume(int maxCount, @NotNull Consumer<R> consumer) throws Exception;

    void failure(@NotNull Throwable failure) throws Exception;

    void value(V value) throws Exception;
  }

  interface OperationFactory<V, R> {

    @NotNull
    Operation<V, R> create(@NotNull Controller controller);
  }
}
