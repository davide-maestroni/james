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

package dm.jale.eventual;

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
  Statement<V> elseCatch(@NotNull dm.jale.eventual.Mapper<? super Throwable, ? extends V> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Statement<V> elseDo(@NotNull Observer<? super Throwable> observer,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Statement<V> elseIf(
      @NotNull dm.jale.eventual.Mapper<? super Throwable, ? extends Statement<? extends V>> mapper,
      @Nullable Class<?>... exceptionTypes);

  @NotNull
  Statement<V> evaluate();

  @NotNull
  Statement<V> evaluated();

  @NotNull
  <R> Statement<R> eventually(@NotNull dm.jale.eventual.Mapper<? super V, R> mapper);

  @NotNull
  Statement<V> eventuallyDo(@NotNull Observer<? super V> observer);

  @NotNull
  <R> Statement<R> eventuallyIf(
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Statement<R>> mapper);

  @NotNull
  <R> Statement<R> eventuallyTry(
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Closeable> closeable,
      @NotNull dm.jale.eventual.Mapper<? super V, R> mapper);

  @NotNull
  Statement<V> eventuallyTryDo(
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Observer<? super V> observer);

  @NotNull
  <R> Statement<R> eventuallyTryIf(
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Closeable> closeable,
      @NotNull dm.jale.eventual.Mapper<? super V, ? extends Statement<R>> mapper);

  @NotNull
  <S> Statement<V> fork(
      @NotNull Forker<S, ? super V, ? super Evaluation<V>, ? super Statement<V>> forker);

  @NotNull
  <S> Statement<V> fork(@Nullable dm.jale.eventual.Mapper<? super Statement<V>, S> init,
      @Nullable dm.jale.eventual.Updater<S, ? super V, ? super Statement<V>> value,
      @Nullable dm.jale.eventual.Updater<S, ? super Throwable, ? super Statement<V>> failure,
      @Nullable dm.jale.eventual.Completer<S, ? super Statement<V>> done,
      @Nullable dm.jale.eventual.Updater<S, ? super Evaluation<V>, ? super Statement<V>>
          evaluation);

  @NotNull
  Statement<V> forkOn(@NotNull Executor executor);

  boolean getDone();

  boolean getDone(long timeout, @NotNull TimeUnit timeUnit);

  @Nullable
  dm.jale.eventual.FailureException getFailure();

  @Nullable
  dm.jale.eventual.FailureException getFailure(long timeout, @NotNull TimeUnit timeUnit);

  V getValue();

  V getValue(long timeout, @NotNull TimeUnit timeUnit);

  boolean isFinal();

  @NotNull
  Statement<V> whenDone(@NotNull dm.jale.eventual.Action action);

  interface Forker<S, V, R, C> {

    S done(S stack, @NotNull C context) throws Exception;

    S evaluation(S stack, @NotNull R evaluation, @NotNull C context) throws Exception;

    S failure(S stack, @NotNull Throwable failure, @NotNull C context) throws Exception;

    S init(@NotNull C context) throws Exception;

    S value(S stack, V value, @NotNull C context) throws Exception;
  }
}
