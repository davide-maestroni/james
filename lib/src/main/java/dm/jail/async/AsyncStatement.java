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

package dm.jail.async;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Serializable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dm.jail.executor.ScheduledExecutor;

/**
 * Created by davide-maestroni on 01/08/2018.
 */
public interface AsyncStatement<V> extends AsyncState<V>, Future<V>, Serializable {

  void consume();

  @NotNull
  AsyncStatement<V> elseCatch(@NotNull Mapper<? super Throwable, ? extends V> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncStatement<V> elseDo(@NotNull Observer<? super Throwable> observer,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncStatement<V> elseIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  AsyncStatement<V> evaluate();

  @NotNull
  AsyncStatement<V> evaluated();

  @NotNull
  <S> AsyncStatement<V> fork(
      @NotNull Forker<S, ? super AsyncStatement<V>, ? super V, ? super AsyncResult<V>> forker);

  @NotNull
  <S> AsyncStatement<V> fork(@Nullable Mapper<? super AsyncStatement<V>, S> init,
      @Nullable ForkUpdater<S, ? super AsyncStatement<V>, ? super V> value,
      @Nullable ForkUpdater<S, ? super AsyncStatement<V>, ? super Throwable> failure,
      @Nullable ForkCompleter<S, ? super AsyncStatement<V>> done,
      @Nullable ForkUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<V>> statement);

  @Nullable
  FailureException getFailure();

  @Nullable
  FailureException getFailure(long timeout, @NotNull TimeUnit timeUnit);

  V getValue();

  V getValue(long timeout, @NotNull TimeUnit timeUnit);

  boolean isFinal();

  @NotNull
  AsyncStatement<V> on(@NotNull ScheduledExecutor executor);

  @NotNull
  <R> AsyncStatement<R> then(@NotNull Mapper<? super V, R> mapper);

  @NotNull
  AsyncStatement<V> thenDo(@NotNull Observer<? super V> observer);

  @NotNull
  <R> AsyncStatement<R> thenIf(@NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> AsyncStatement<R> thenTry(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, R> mapper);

  @NotNull
  AsyncStatement<V> thenTryDo(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Observer<? super V> observer);

  @NotNull
  <R> AsyncStatement<R> thenTryIf(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  void waitDone();

  boolean waitDone(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  AsyncStatement<V> whenDone(@NotNull Action action);

  interface ForkCompleter<S, A> {

    S complete(@NotNull A statement, S stack) throws Exception;
  }

  interface ForkUpdater<S, A, T> {

    S update(@NotNull A statement, S stack, T input) throws Exception;
  }

  interface Forker<S, A, V, R> {

    S done(@NotNull A statement, S stack) throws Exception;

    S failure(@NotNull A statement, S stack, @NotNull Throwable failure) throws Exception;

    S init(@NotNull A statement) throws Exception;

    S statement(@NotNull A statement, S stack, @NotNull R result) throws Exception;

    S value(@NotNull A statement, S stack, V value) throws Exception;
  }
}
