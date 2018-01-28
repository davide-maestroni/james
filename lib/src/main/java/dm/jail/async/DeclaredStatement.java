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

import dm.jail.executor.ScheduledExecutor;

/**
 * Created by davide-maestroni on 01/08/2018.
 */
public interface DeclaredStatement<V> extends AsyncStatement<V>, Serializable {

  @NotNull
  DeclaredStatement<V> autoEvaluate();

  @NotNull
  DeclaredStatement<V> elseCatch(@NotNull Mapper<? super Throwable, ? extends V> mapper,
      @Nullable Class<?>[] exceptionTypes);

  @NotNull
  DeclaredStatement<V> elseDo(@NotNull Observer<? super Throwable> observer,
      @Nullable Class<?>[] exceptionTypes);

  @NotNull
  DeclaredStatement<V> elseIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable Class<?>[] exceptionTypes);

  @NotNull
  <S> DeclaredStatement<V> fork(
      @NotNull Forker<S, ? super AsyncStatement<V>, ? super V, ? super AsyncResult<V>> forker);

  @NotNull
  <S> DeclaredStatement<V> fork(@Nullable Mapper<? super AsyncStatement<V>, S> init,
      @Nullable ForkUpdater<S, ? super AsyncStatement<V>, ? super V> value,
      @Nullable ForkUpdater<S, ? super AsyncStatement<V>, ? super Throwable> failure,
      @Nullable ForkCompleter<S, ? super AsyncStatement<V>> done,
      @Nullable ForkUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<V>> statement);

  @NotNull
  DeclaredStatement<V> on(@NotNull ScheduledExecutor executor);

  @NotNull
  <R> DeclaredStatement<R> then(@NotNull Mapper<? super V, R> mapper);

  @NotNull
  DeclaredStatement<V> thenDo(@NotNull Observer<? super V> observer);

  @NotNull
  <R> DeclaredStatement<R> thenIf(@NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> DeclaredStatement<R> thenTry(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, R> mapper);

  @NotNull
  DeclaredStatement<V> thenTryDo(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Observer<? super V> observer);

  @NotNull
  <R> DeclaredStatement<R> thenTryIf(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  DeclaredStatement<V> whenDone(@NotNull Action action);
}
