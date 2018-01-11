/*
 * Copyright 2018 Davide Maestroni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dm.james.async;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Serializable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dm.james.executor.ScheduledExecutor;
import dm.james.promise.Mapper;
import dm.james.promise.Observer;
import dm.james.promise.Provider;

/**
 * Created by davide-maestroni on 01/08/2018.
 */
public interface AsyncStatement<V> extends AsyncState<V>, Future<V>, Serializable {

  @NotNull
  <O, R> AsyncStatement<R> andIf(@NotNull AsyncStatement<O> statement,
      @NotNull Merger<V, O, R> merger);

  @NotNull
  <S> AsyncStatement<V> buffer(@NotNull BufferHandler<S, V> handler);

  @NotNull
  <S> AsyncStatement<V> buffer(@Nullable Provider<S> init, @Nullable BufferUpdater<S, V> value,
      @Nullable BufferUpdater<S, Throwable> failure,
      @Nullable BufferUpdater<S, AsyncResult<V>> statement,
      @Nullable BufferUpdater<S, AsyncResultCollection<V>> loop);

  @NotNull
  AsyncStatement<V> elseCatch(@NotNull Mapper<Throwable, V> mapper,
      @Nullable Class<? extends Throwable>... exceptionTypes);

  @NotNull
  AsyncStatement<V> elseDo(@NotNull Observer<Throwable> observer,
      @Nullable Class<? extends Throwable>... exceptionTypes);

  @NotNull
  AsyncStatement<V> elseIf(@NotNull Mapper<Throwable, AsyncStatement<V>> mapper,
      @Nullable Class<? extends Throwable>... exceptionTypes);

  @Nullable
  FailureException getFailure();

  @Nullable
  FailureException getFailure(long timeout, @NotNull TimeUnit unit);

  V getValue();

  V getValue(long timeout, @NotNull TimeUnit unit);

  @NotNull
  AsyncStatement<V> on(@NotNull ScheduledExecutor executor);

  @NotNull
  <O, R> AsyncStatement<R> orIf(@NotNull AsyncStatement<O> statement);

  @NotNull
  AsyncStatement<V> renew();

  @NotNull
  <R> AsyncStatement<R> then(@NotNull Mapper<V, R> mapper);

  @NotNull
  AsyncStatement<V> thenDo(@NotNull Observer<V> observer);

  @NotNull
  <R> AsyncStatement<R> thenIf(@NotNull Mapper<V, AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncStatement<R> thenTry(@NotNull Mapper<V, Closeable> closeable,
      @NotNull Mapper<V, R> mapper);

  @NotNull
  AsyncStatement<V> thenTryDo(@NotNull Mapper<V, Closeable> closeable,
      @NotNull Observer<V> observer);

  @NotNull
  <R> AsyncStatement<R> thenTryIf(@NotNull Mapper<V, Closeable> closeable,
      @NotNull Mapper<V, AsyncStatement<R>> mapper);

  void waitDone();

  boolean waitDone(long timeout, @NotNull TimeUnit unit);

  interface BufferHandler<S, V> {

    S failure(S stack, Throwable failure);

    S init();

    S loop(S stack, AsyncResultCollection<V> results);

    S statement(S stack, AsyncResult<V> result);

    S value(S stack, V value);
  }

  interface BufferUpdater<S, V> {

    S apply(S stack, V value);
  }
}
