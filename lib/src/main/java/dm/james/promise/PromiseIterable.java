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

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public interface PromiseIterable<O> extends Promise<Iterable<O>> {

  @NotNull
  <R, S> PromiseIterable<R> then(@NotNull StateFulProcessor<O, R, S> processor);

  @NotNull
  <R> PromiseIterable<R> thenAll(@NotNull StatelessProcessor<Iterable<O>, Iterable<R>> processor);

  @NotNull
  <R> PromiseIterable<R> thenAll(
      @Nullable Handler<Iterable<O>, R, CallbackIterable<R>> outputHandler,
      @Nullable Handler<Throwable, R, CallbackIterable<R>> errorHandler,
      @Nullable Observer<CallbackIterable<R>> emptyHandler);

  @NotNull
  PromiseIterable<O> thenCatch(@NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  PromiseIterable<O> thenFill(@NotNull Provider<Iterable<O>> provider);

  @NotNull
  PromiseIterable<O> thenCatchEach(@NotNull Mapper<Throwable, O> mapper);

  @NotNull
  <R> PromiseIterable<R> thenEach(@Nullable Handler<O, R, CallbackIterable<R>> outputHandler,
      @Nullable Handler<Throwable, R, CallbackIterable<R>> errorHandler,
      @Nullable Observer<CallbackIterable<R>> emptyHandler);

  @NotNull
  <R> PromiseIterable<R> thenEach(@NotNull StatelessProcessor<O, R> processor);

  @NotNull
  PromiseIterable<O> thenFillEach(@NotNull Provider<O> provider);

  @NotNull
  <R> PromiseIterable<R> thenMapAll(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> thenMapEach(@NotNull Mapper<O, R> mapper);

  interface CallbackIterable<O> extends Callback<O> {

    void add(O output);

    void addAll(@Nullable Iterable<O> outputs);

    void addAllDeferred(@NotNull Promise<? extends Iterable<O>> promise);

    void addDeferred(@NotNull Promise<O> promise);
  }

  interface StateFulProcessor<I, O, S> {

    S add(S state, I input, @NotNull CallbackIterable<O> callback) throws Exception;

    S create(@NotNull CallbackIterable<O> callback) throws Exception;

    void reject(S state, Throwable reason, @NotNull CallbackIterable<O> callback) throws Exception;

    void resolve(S state, @NotNull CallbackIterable<O> callback) throws Exception;
  }
}
