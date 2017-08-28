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
 * Created by davide-maestroni on 08/21/2017.
 */
public interface ChainableIterable<O> extends Chainable<Iterable<O>> {

  @NotNull
  <R, S> ChainableIterable<R> then(@NotNull StatefulHandler<O, R, S> handler);

  interface CallbackIterable<O> extends Callback<O> {

    void add(O output);

    void addAll(@Nullable Iterable<O> outputs);

    void addAllDeferred(@Nullable Iterable<? extends Chainable<?>> chainables);

    void addAllDeferred(@NotNull Chainable<? extends Iterable<O>> chainable);

    void addDeferred(@NotNull Chainable<O> chainable);

    void addRejection(Throwable reason);

    void resolve();
  }

  interface StatefulHandler<I, O, S> {

    S create(@NotNull CallbackIterable<O> callback) throws Exception;

    S fulfill(S state, I input, @NotNull CallbackIterable<O> callback) throws Exception;

    S reject(S state, Throwable reason, @NotNull CallbackIterable<O> callback) throws Exception;

    void resolve(S state, @NotNull CallbackIterable<O> callback) throws Exception;
  }
}
