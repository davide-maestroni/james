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
import java.util.List;
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

  @NotNull
  Promise<O> catchAllFlat(@NotNull Iterable<Class<? extends Throwable>> errors,
      @NotNull Mapper<Throwable, Chainable<? extends O>> mapper);

  @NotNull
  Promise<O> catchAllFlat(@NotNull Mapper<Throwable, Chainable<? extends O>> mapper);

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
  Promise<O> onFulfill(@NotNull Observer<O> observer);

  @NotNull
  Promise<O> onReject(@NotNull Observer<Throwable> observer);

  @NotNull
  Promise<O> onResolve(@NotNull Action action);

  @NotNull
  Promise<O> renew();

  @NotNull
  Promise<O> scheduleAll(@NotNull ScheduledExecutor executor);

  @NotNull
  <R> Promise<R> then(@NotNull Handler<O, ? super Callback<R>> handler);

  @NotNull
  <R> Promise<R> then(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> Promise<R> thenFlat(@NotNull Mapper<O, Chainable<? extends R>> mapper);

  @NotNull
  <R> Promise<R> thenTry(@NotNull Handler<O, ? super Callback<R>> handler);

  @NotNull
  <R> Promise<R> thenTry(@NotNull Mapper<O, R> mapper);

  @NotNull
  <R> Promise<R> thenTryFlat(@NotNull Mapper<O, Chainable<? extends R>> mapper);

  void waitResolved();

  boolean waitResolved(long timeout, @NotNull TimeUnit timeUnit);
//
//  @NotNull
//  <R, S> Promise<R> whenChained(@NotNull ChainHandler<O, R, S> handler);
//
//  interface ChainHandler<I, O, S> {
//
//    S chain(S state, @NotNull Callback<O> callback, @NotNull List<Callback<O>> callbacks);
//
//    S create();
//
//    S handle(S state, @NotNull PromiseInspection<I> inspection,
//        @NotNull List<Callback<O>> callbacks);
//  }
}
