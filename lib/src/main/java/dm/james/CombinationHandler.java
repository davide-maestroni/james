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

package dm.james;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import dm.james.promise.Promise;
import dm.james.promise.PromiseIterable.CallbackIterable;

/**
 * Created by davide-maestroni on 08/19/2017.
 */
public interface CombinationHandler<O, S> {

  S create(@NotNull List<? extends Promise<?>> promises,
      @NotNull CallbackIterable<O> callback) throws Exception;

  S fulfill(S state, O output, @NotNull List<? extends Promise<?>> promises, int index,
      @NotNull CallbackIterable<O> callback) throws Exception;

  S reject(S state, Throwable reason, @NotNull List<? extends Promise<?>> promises, int index,
      @NotNull CallbackIterable<O> callback) throws Exception;

  S resolve(S state, @NotNull List<? extends Promise<?>> promises, int index,
      @NotNull CallbackIterable<O> callback) throws Exception;

  void resolve(S state, @NotNull List<? extends Promise<?>> promises,
      @NotNull CallbackIterable<O> callback) throws Exception;
}
