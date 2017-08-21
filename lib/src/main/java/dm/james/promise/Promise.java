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

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import dm.james.executor.ScheduledExecutor;

/**
 * Created by davide-maestroni on 07/17/2017.
 */
public interface Promise<O> extends Chainable<O>, PromiseInspection<O>, Serializable {

  @NotNull
  <R> Promise<R> apply(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  boolean cancel();

  @NotNull
  Promise<O> catchAll(@NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, O> mapper);

  @NotNull
  Promise<O> catchAll(@NotNull Mapper<Throwable, O> mapper);

  O get();

  O get(long timeout, @NotNull TimeUnit timeUnit);

  O getOr(O other, long timeout, @NotNull TimeUnit timeUnit);

  @Nullable
  RejectionException getReason();

  @Nullable
  RejectionException getReason(long timeout, @NotNull TimeUnit timeUnit);

  RejectionException getReasonOr(RejectionException other, long timeout,
      @NotNull TimeUnit timeUnit);

  @NotNull
  Promise<PromiseInspection<O>> inspect();

  boolean isChained();

  @NotNull
  Promise<O> scheduleAll(@Nullable ScheduledExecutor fulfillExecutor,
      @Nullable ScheduledExecutor rejectExecutor);

  @NotNull
  <R> Promise<R> then(@Nullable Handler<O, ? super Callback<R>> fulfill,
      @Nullable Handler<Throwable, ? super Callback<R>> reject);

  @NotNull
  <R> Promise<R> then(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> Promise<R> thenTry(@Nullable Handler<O, ? super Callback<R>> fulfill,
      @Nullable Handler<Throwable, ? super Callback<R>> reject);

  @NotNull
  <R> Promise<R> thenTry(@NotNull Mapper<O, R> mapper);

  void waitResolved();

  boolean waitResolved(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  Promise<O> whenFulfilled(@NotNull Observer<O> observer);

  @NotNull
  Promise<O> whenRejected(@NotNull Observer<Throwable> observer);

  @NotNull
  Promise<O> whenResolved(@NotNull Action action);
}
