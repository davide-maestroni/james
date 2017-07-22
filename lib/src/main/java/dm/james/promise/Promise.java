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

/**
 * Created by davide-maestroni on 07/17/2017.
 */
public interface Promise<O> extends Serializable {

  // TODO: 18/07/2017 float timeout??
  // TODO: 22/07/2017 thenAccept(Observer<O>), thenFinally(Observer<Throwable>), thenDo(Action)

  @NotNull
  <R> Promise<R> apply(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  O get();

  O get(long timeout, @NotNull TimeUnit timeUnit);

  @Nullable
  RejectionException getError();

  @Nullable
  RejectionException getError(long timeout, @NotNull TimeUnit timeUnit);

  RejectionException getErrorOr(RejectionException other, long timeout, @NotNull TimeUnit timeUnit);

  O getOr(O other, long timeout, @NotNull TimeUnit timeUnit);

  boolean isBound();

  boolean isFulfilled();

  boolean isPending();

  boolean isRejected();

  boolean isResolved();

  @NotNull
  <R> Promise<R> then(@NotNull StatelessProcessor<O, R> processor);

  @NotNull
  <R> Promise<R> then(@Nullable Handler<O, R, Callback<R>> outputHandler,
      @Nullable Handler<Throwable, R, Callback<R>> errorHandler,
      @Nullable Observer<Callback<R>> emptyHandler);

  @NotNull
  Promise<O> thenAccept(@NotNull Observer<O> observer);

  @NotNull
  Promise<O> thenCatch(@NotNull Mapper<Throwable, O> mapper);

  @NotNull
  Promise<O> thenDo(@NotNull Action action);

  @NotNull
  Promise<O> thenFill(@NotNull Provider<O> provider);

  @NotNull
  Promise<O> thenFinally(@NotNull Observer<Throwable> observer);

  @NotNull
  <R> Promise<R> thenMap(@NotNull Mapper<O, R> mapper);

  boolean waitFulfilled(long timeout, @NotNull TimeUnit timeUnit);

  boolean waitPending(long timeout, @NotNull TimeUnit timeUnit);

  boolean waitRejected(long timeout, @NotNull TimeUnit timeUnit);

  boolean waitResolved(long timeout, @NotNull TimeUnit timeUnit);

  interface Callback<O> extends Resolvable<O> {

    void defer(@NotNull Promise<O> promise);
  }

  interface Handler<I, O, C extends Callback<O>> {

    void accept(I input, @NotNull C callback) throws Exception;
  }

  interface StatelessProcessor<I, O> {

    void reject(Throwable reason, @NotNull Callback<O> callback) throws Exception;

    void resolve(@NotNull Callback<O> callback) throws Exception;

    void resolve(I input, @NotNull Callback<O> callback) throws Exception;
  }
}
