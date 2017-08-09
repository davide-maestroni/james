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

package dm.james.reflect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

import dm.james.promise.Promise;
import dm.james.promise.PromiseIterable;

/**
 * Created by davide-maestroni on 08/09/2017.
 */
public interface PromisifiedObject {

  @NotNull
  <O> PromiseIterable<O> iterable(@NotNull Method method, @Nullable Object... params);

  @NotNull
  <O> PromiseIterable<O> iterable(@NotNull String name, @Nullable Object... params);

  @NotNull
  <O> Promise<O> promise(@NotNull Method method, @Nullable Object... params);

  @NotNull
  <O> Promise<O> promise(@NotNull String name, @Nullable Object... params);
}
