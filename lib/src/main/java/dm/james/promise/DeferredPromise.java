/*
 * Copyright 2017 Davide Maestroni
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

package dm.james.promise;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dm.james.executor.ScheduledExecutor;

/**
 * Created by davide-maestroni on 07/19/2017.
 */
public interface DeferredPromise<I, O> extends Promise<O> {

  @NotNull
  <R> DeferredPromise<I, R> apply(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  DeferredPromise<I, O> catchAll(@NotNull Mapper<Throwable, O> mapper);

  @NotNull
  DeferredPromise<I, O> scheduleAll(@Nullable ScheduledExecutor fulfillExecutor,
      @Nullable ScheduledExecutor rejectExecutor);

  @NotNull
  <R> DeferredPromise<I, R> then(@Nullable Handler<O, ? super Callback<R>> fulfill,
      @Nullable Handler<Throwable, ? super Callback<R>> reject);

  @NotNull
  <R> DeferredPromise<I, R> then(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> DeferredPromise<I, R> thenTry(@Nullable Handler<O, ? super Callback<R>> fulfill,
      @Nullable Handler<Throwable, ? super Callback<R>> reject);

  @NotNull
  <R> DeferredPromise<I, R> thenTry(@NotNull Mapper<O, R> mapper);

  @NotNull
  DeferredPromise<I, O> whenFulfilled(@NotNull Observer<O> observer);

  @NotNull
  DeferredPromise<I, O> whenRejected(@NotNull Observer<Throwable> observer);

  @NotNull
  DeferredPromise<I, O> whenResolved(@NotNull Action action);

  void defer(@NotNull Promise<I> promise);

  void reject(Throwable reason);

  void resolve(I input);
}
