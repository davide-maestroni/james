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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by davide-maestroni on 07/21/2017.
 */
public interface PromiseIterable<O> extends Promise<Iterable<O>>, Iterable<O> {

  @NotNull
  <R> PromiseIterable<R> applyAll(@NotNull Mapper<PromiseIterable<O>, PromiseIterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> applyEach(@NotNull Mapper<Promise<O>, Promise<R>> mapper);

  @NotNull
  List<O> getAll();

  @NotNull
  List<O> getAll(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  Iterator<O> iterator(long timeout, @NotNull TimeUnit timeUnit);

  O remove();

  O remove(long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<O> remove(int maxSize);

  @NotNull
  List<O> remove(int maxSize, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  List<O> removeAll();

  @NotNull
  List<O> removeAll(long timeout, @NotNull TimeUnit timeUnit);

  O removeOr(O other, long timeout, @NotNull TimeUnit timeUnit);

  @NotNull
  <R, S> PromiseIterable<R> then(@NotNull StatefulProcessor<O, R, S> processor);

  @NotNull
  PromiseIterable<O> thenAccept(@NotNull Observer<Iterable<O>> observer);

  @NotNull
  PromiseIterable<O> thenCatch(@NotNull Mapper<Throwable, Iterable<O>> mapper);

  @NotNull
  PromiseIterable<O> thenDo(@NotNull Action action);

  @NotNull
  PromiseIterable<O> thenFill(@NotNull Provider<Iterable<O>> provider);

  @NotNull
  PromiseIterable<O> thenFinally(@NotNull Observer<Throwable> observer);

  @NotNull
  PromiseIterable<O> thenAcceptEach(@NotNull Observer<O> observer);

  @NotNull
  <R> PromiseIterable<R> thenAll(@NotNull StatelessProcessor<Iterable<O>, Iterable<R>> processor);

  @NotNull
  <R> PromiseIterable<R> thenAll(
      @Nullable Handler<Iterable<O>, R, CallbackIterable<R>> outputHandler,
      @Nullable Handler<Throwable, R, CallbackIterable<R>> errorHandler,
      @Nullable Observer<CallbackIterable<R>> emptyHandler);

  @NotNull
  PromiseIterable<O> thenCatchEach(@NotNull Mapper<Throwable, O> mapper);

  @NotNull
  PromiseIterable<O> thenDoEach(@NotNull Action action);

  @NotNull
  <R> PromiseIterable<R> thenEach(@Nullable Handler<O, R, CallbackIterable<R>> outputHandler,
      @Nullable Handler<Throwable, R, CallbackIterable<R>> errorHandler,
      @Nullable Observer<CallbackIterable<R>> emptyHandler);

  @NotNull
  <R> PromiseIterable<R> thenEach(@NotNull StatelessProcessor<O, R> processor);

  @NotNull
  PromiseIterable<O> thenFillEach(@NotNull Provider<O> provider);

  @NotNull
  PromiseIterable<O> thenFinallyEach(@NotNull Observer<Throwable> observer);

  @NotNull
  <R> PromiseIterable<R> thenMapAll(@NotNull Mapper<Iterable<O>, Iterable<R>> mapper);

  @NotNull
  <R> PromiseIterable<R> thenMapEach(@NotNull Mapper<O, R> mapper);

  interface CallbackIterable<O> extends Callback<O>, ResolvableIterable<O> {

    void addAllDeferred(@NotNull Promise<? extends Iterable<O>> promise);

    void addDeferred(@NotNull Promise<O> promise);
  }

  interface StatefulProcessor<I, O, S> {

    S add(S state, I input, @NotNull CallbackIterable<O> callback) throws Exception;

    S create(@NotNull CallbackIterable<O> callback) throws Exception;

    void reject(S state, Throwable reason, @NotNull CallbackIterable<O> callback) throws Exception;

    void resolve(S state, @NotNull CallbackIterable<O> callback) throws Exception;
  }
}
