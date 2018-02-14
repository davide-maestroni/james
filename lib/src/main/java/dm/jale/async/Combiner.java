/*
 * Copyright 2018 Davide Maestroni
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

package dm.jale.async;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by davide-maestroni on 02/14/2018.
 */
public interface Combiner<S, V, R, A> {

  S done(S stack, @NotNull R evaluation, @NotNull List<A> asyncs, int index) throws Exception;

  S failure(S stack, Throwable failure, @NotNull R evaluation, @NotNull List<A> asyncs, int
      index) throws Exception;

  S init(@NotNull List<A> asyncs) throws Exception;

  void settle(S stack, @NotNull R evaluation, @NotNull List<A> asyncs) throws Exception;

  S value(S stack, V value, @NotNull R evaluation, @NotNull List<A> asyncs, int index) throws
      Exception;
}
