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

package dm.jail;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dm.jail.async.AsyncEvaluations;

/**
 * Created by davide-maestroni on 02/01/2018.
 */
class AsyncLoopHandler<V, R> {

  void addFailure(@NotNull final Throwable failure,
      @NotNull final AsyncEvaluations<R> evaluations) throws Exception {
    evaluations.addFailure(failure).set();
  }

  void addFailures(@Nullable final Iterable<? extends Throwable> failures,
      @NotNull final AsyncEvaluations<R> evaluations) throws Exception {
    evaluations.addFailures(failures).set();
  }

  @SuppressWarnings("unchecked")
  void addValue(final V value, @NotNull final AsyncEvaluations<R> evaluations) throws Exception {
    evaluations.addValue((R) value).set();
  }

  @SuppressWarnings("unchecked")
  void addValues(@Nullable final Iterable<? extends V> values,
      @NotNull final AsyncEvaluations<R> evaluations) throws Exception {
    evaluations.addValues((Iterable<R>) values).set();
  }

  @NotNull
  AsyncLoopHandler<V, R> renew() {
    return this;
  }

  void set(@NotNull final AsyncEvaluations<R> evaluations) throws Exception {
    evaluations.set();
  }
}
