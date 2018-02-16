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

package dm.jale.ext;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.List;

import dm.jale.async.AsyncEvaluation;
import dm.jale.async.AsyncState;
import dm.jale.async.AsyncStatement;
import dm.jale.async.SimpleState;
import dm.jale.async.StatementCombiner;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class AnyOfCombiner<V> implements StatementCombiner<AsyncState<V>, V, V>, Serializable {

  private static final AnyOfCombiner<?> sInstance = new AnyOfCombiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private AnyOfCombiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> AnyOfCombiner<V> instance() {
    return (AnyOfCombiner<V>) sInstance;
  }

  public AsyncState<V> done(final AsyncState<V> stack, @NotNull final AsyncEvaluation<V> evaluation,
      @NotNull final List<AsyncStatement<V>> asyncs, final int index) {
    return stack;
  }

  public AsyncState<V> failure(final AsyncState<V> stack, final Throwable failure,
      @NotNull final AsyncEvaluation<V> evaluation, @NotNull final List<AsyncStatement<V>> asyncs,
      final int index) {
    if (stack == null) {
      return SimpleState.ofFailure(failure);
    }

    return stack;
  }

  public AsyncState<V> init(@NotNull final List<AsyncStatement<V>> asyncs) {
    return null;
  }

  public void settle(final AsyncState<V> stack, @NotNull final AsyncEvaluation<V> evaluation,
      @NotNull final List<AsyncStatement<V>> asyncs) throws Exception {
    if (stack.isFailed()) {
      stack.to(evaluation);
    }
  }

  public AsyncState<V> value(final AsyncState<V> stack, final V value,
      @NotNull final AsyncEvaluation<V> evaluation, @NotNull final List<AsyncStatement<V>> asyncs,
      final int index) {
    if ((stack == null) || stack.isFailed()) {
      evaluation.set(value);
      return SimpleState.ofValue(value);
    }

    return stack;
  }
}
