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
public interface DeferredStatement<V> extends AsyncStatement<V>, Serializable {

  @NotNull
  <S> DeferredStatement<V> buffer(
      @NotNull Bufferer<S, ? super AsyncStatement<V>, ? super V, ? extends V> bufferer);

  @NotNull
  <S> DeferredStatement<V> buffer(@Nullable Mapper<? super AsyncStatement<V>, S> init,
      @Nullable BufferUpdater<S, ? super AsyncStatement<V>, ? super V> value,
      @Nullable BufferUpdater<S, ? super AsyncStatement<V>, ? super Throwable> failure,
      @Nullable BufferUpdater<S, ? super AsyncStatement<V>, ? super AsyncResult<? extends V>>
          statement,
      @Nullable BufferUpdater<S, ? super AsyncStatement<V>, ? super AsyncResultCollection<?
          extends V>> loop);

  @NotNull
  DeferredStatement<V> elseCatch(@NotNull Mapper<? super Throwable, ? extends V> mapper,
      @Nullable Class<?>[] exceptionTypes);

  @NotNull
  DeferredStatement<V> elseDo(@NotNull Observer<? super Throwable> observer,
      @Nullable Class<?>[] exceptionTypes);

  @NotNull
  DeferredStatement<V> elseIf(
      @NotNull Mapper<? super Throwable, ? extends AsyncStatement<? extends V>> mapper,
      @Nullable Class<?>[] exceptionTypes);

  @NotNull
  DeferredStatement<V> on(@NotNull ScheduledExecutor executor);

  @NotNull
  AsyncStatement<V> renew();

  @NotNull
  <R> DeferredStatement<R> then(@NotNull Mapper<? super V, R> mapper);

  @NotNull
  DeferredStatement<V> thenDo(@NotNull Observer<? super V> observer);

  @NotNull
  <R> DeferredStatement<R> thenIf(@NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  <R> DeferredStatement<R> thenTry(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, R> mapper);

  @NotNull
  DeferredStatement<V> thenTryDo(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Observer<? super V> observer);

  @NotNull
  <R> DeferredStatement<R> thenTryIf(@NotNull Mapper<? super V, ? extends Closeable> closeable,
      @NotNull Mapper<? super V, ? extends AsyncStatement<R>> mapper);

  @NotNull
  DeferredStatement<V> whenDone(@NotNull Action action);

  @NotNull
  AsyncStatement<V> evaluate();
}
