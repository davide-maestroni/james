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
public interface Chainable<O> {

  @NotNull
  <R> Chainable<R> then(@Nullable Handler<O, ? super Callback<R>> fulfill,
      @Nullable Handler<Throwable, ? super Callback<R>> reject);

  interface Callback<O> {

    void defer(@NotNull Chainable<O> chainable);

    void reject(Throwable reason);

    void resolve(O output);
  }

  interface Handler<I, C> {

    void accept(I input, C callback) throws Exception;
  }
}
