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

package dm.fates;

import org.jetbrains.annotations.NotNull;

import dm.fates.eventual.Evaluation;

/**
 * Created by davide-maestroni on 01/14/2018.
 */
class StatementExpression<V, R> {

  void failure(@NotNull final Throwable failure, @NotNull final Evaluation<R> evaluation) throws
      Exception {
    evaluation.fail(failure);
  }

  @NotNull
  StatementExpression<V, R> renew() {
    return this;
  }

  @SuppressWarnings("unchecked")
  void value(final V value, @NotNull final Evaluation<R> evaluation) throws Exception {
    evaluation.set((R) value);
  }
}
