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
import org.jetbrains.annotations.Nullable;

import dm.james.promise.Promise.Callback;
import dm.james.promise.PromiseIterable.CallbackIterable;

/**
 * Created by davide-maestroni on 08/07/2017.
 */
public @interface Promisify {

  Class<? extends CallbackMapper> mapper() default NoMapper.class;

  String name() default "";

  Class<?>[] params() default {};

  interface CallbackMapper<O, T> {

    @Nullable
    T map(@NotNull Class<?> paramType, int position, @NotNull Callback<O> callback);

    @Nullable
    T mapIterable(@NotNull Class<?> paramType, int position, @NotNull CallbackIterable<O> callback);
  }

  class NoMapper implements CallbackMapper<Object, Object> {

    @Nullable
    public Object map(@NotNull final Class<?> paramType, final int position,
        @NotNull final Callback<Object> callback) {
      return null;
    }

    @Nullable
    public Object mapIterable(@NotNull final Class<?> paramType, final int position,
        @NotNull final CallbackIterable<Object> callback) {
      return null;
    }
  }
}
