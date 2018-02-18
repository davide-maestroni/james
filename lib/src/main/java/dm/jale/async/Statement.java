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
public interface Statement<V> extends EvaluationState<V>, Future<V>, Serializable {

  void consume();

  @NotNull
  Statement<V> elseCatch(@NotNull Mapper<? super Throwable, ? extends V> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Statement<V> elseDo(@NotNull Observer<? super Throwable> observer,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Statement<V> elseIf(@NotNull Mapper<? super Throwable, ? extends Statement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Statement<V> evaluate();

  @NotNull
  Statement<V> evaluated();

  @NotNull
  <S> Statement<V> fork(
      @NotNull Forker<S, ? super V, ? super Evaluation<V>, ? super Statement<V>> forker);

  @NotNull
  <S> Statement<V> fork(@Nullable Mapper<? super Statement<V>, S> init,
      @Nullable Updater<S, ? super V, ? super Statement<V>> value,
      @Nullable Updater<S, ? super Throwable, ? super Statement<V>> failure,
      @Nullable Completer<S, ? super Statement<V>> done,
      @Nullable Updater<S, ? super Evaluation<V>, ? super Statement<V>> evaluation);

  @NotNull
  Statement<V> forkOn(@NotNull Executor executor);

  boolean getDone();

  boolean getDone(long timeout, @NotNull TimeUnit timeUnit);

  @Nullable
  FailureException getFailure();

  @Nullable
  FailureException getFailure(long timeout, @NotNull TimeUnit timeUnit);

  V getValue();

  V getValue(long timeout, @NotNull TimeUnit timeUnit);

  boolean isFinal();

  @NotNull
  <R> Statement<R> then(@NotNull Mapper<? super V, R> mapper);

  @NotNull
  Statement<V> thenDo(@NotNull Observer<? super V> observer);

  @NotNull
  <R> Statement<R> thenIf(@NotNull Mapper<? super V, ? extends Statement<R>> mapper);

  @NotNull
  <R> Statement<R> thenTry(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, R> mapper);

  @NotNull
  Statement<V> thenTryDo(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Observer<? super V> observer);

  @NotNull
  <R> Statement<R> thenTryIf(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends Statement<R>> mapper);

  @NotNull
  Statement<V> whenDone(@NotNull Action action);

  interface Forker<S, V, R, C> {

    S done(S stack, @NotNull C context) throws Exception;

    S evaluation(S stack, @NotNull R evaluation, @NotNull C context) throws Exception;

    S failure(S stack, @NotNull Throwable failure, @NotNull C context) throws Exception;

    S init(@NotNull C context) throws Exception;

    S value(S stack, V value, @NotNull C context) throws Exception;
  }
}
