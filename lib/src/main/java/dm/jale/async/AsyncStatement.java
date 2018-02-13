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

package dm.jale.async;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Serializable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

  // TODO: 12/02/2018 tryFork?
  // TODO: 13/02/2018 forkBuffered

  @NotNull
  <S> AsyncStatement<V> fork(
      @NotNull Forker<S, ? super V, ? super AsyncEvaluation<V>, ? super AsyncStatement<V>> forker);

  @NotNull
  <S> AsyncStatement<V> fork(@Nullable Mapper<? super AsyncStatement<V>, S> init,
      @Nullable Updater<S, ? super V, ? super AsyncStatement<V>> value,
      @Nullable Updater<S, ? super Throwable, ? super AsyncStatement<V>> failure,
      @Nullable Completer<S, ? super AsyncStatement<V>> done,
      @Nullable Updater<S, ? super AsyncEvaluation<V>, ? super AsyncStatement<V>> statement);

  @NotNull
  AsyncStatement<V> forkOn(@NotNull Executor executor);

  @Nullable
  FailureException getFailure();

  @Nullable
  FailureException getFailure(long timeout, @NotNull TimeUnit timeUnit);

  V getValue();

  V getValue(long timeout, @NotNull TimeUnit timeUnit);

  boolean isFinal();

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

  interface Forker<S, V, R, A> {

    S done(S stack, @NotNull A async) throws Exception;

    S evaluation(S stack, @NotNull R evaluation, @NotNull A async) throws Exception;

    S failure(S stack, @NotNull Throwable failure, @NotNull A async) throws Exception;

    S init(@NotNull A async) throws Exception;

    S value(S stack, V value, @NotNull A async) throws Exception;
  }
}
