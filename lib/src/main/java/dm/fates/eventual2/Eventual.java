package dm.fates.eventual2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dm.fates.eventual.Mapper;

/**
 * Created by davide-maestroni on 03/22/2018.
 */
public interface Eventual<V> {

  @NotNull
  <R> Eventual<R> lift(@NotNull Mapper<? super Flow<R>, ? extends Flow<V>> flowMapper);

  interface Consumer<V> {

    void complete() throws Exception;

    @NotNull
    Consumer<V> failure(@NotNull Throwable failure) throws Exception;

    @NotNull
    Consumer<V> failures(@Nullable Iterable<? extends Throwable> failures) throws Exception;

    @NotNull
    Consumer<V> value(V value) throws Exception;

    @NotNull
    Consumer<V> values(@Nullable Iterable<? extends V> values) throws Exception;
  }

  interface Flow<V> extends Producer, Consumer<V> {

    @NotNull
    Flow<V> failure(@NotNull Throwable failure) throws Exception;

    @NotNull
    Flow<V> failures(@Nullable Iterable<? extends Throwable> failures) throws Exception;

    @NotNull
    Flow<V> value(V value) throws Exception;

    @NotNull
    Flow<V> values(@Nullable Iterable<? extends V> values) throws Exception;
  }

  interface Producer {

    void cancel() throws Exception;

    void consume() throws Exception;
  }
}
