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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.List;

import dm.jale.async.Evaluation;
import dm.jale.async.EvaluationState;
import dm.jale.async.SimpleState;
import dm.jale.async.Statement;
import dm.jale.async.StatementCombiner;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class AnyOfCombiner<V> implements StatementCombiner<EvaluationState<V>, V, V>, Serializable {

  private static final AnyOfCombiner<?> sInstance = new AnyOfCombiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private AnyOfCombiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> AnyOfCombiner<V> instance() {
    return (AnyOfCombiner<V>) sInstance;
  }

  public EvaluationState<V> done(final EvaluationState<V> stack,
      @NotNull final Evaluation<V> evaluation, @NotNull final List<Statement<V>> asyncs,
      final int index) {
    return stack;
  }

  public EvaluationState<V> failure(final EvaluationState<V> stack, final Throwable failure,
      @NotNull final Evaluation<V> evaluation, @NotNull final List<Statement<V>> asyncs,
      final int index) {
    if (stack == null) {
      return SimpleState.ofFailure(failure);
    }

    return stack;
  }

  public EvaluationState<V> init(@NotNull final List<Statement<V>> asyncs) {
    return null;
  }

  public void settle(final EvaluationState<V> stack, @NotNull final Evaluation<V> evaluation,
      @NotNull final List<Statement<V>> asyncs) throws Exception {
    if (stack.isFailed()) {
      stack.to(evaluation);
    }
  }

  public EvaluationState<V> value(final EvaluationState<V> stack, final V value,
      @NotNull final Evaluation<V> evaluation, @NotNull final List<Statement<V>> asyncs,
      final int index) {
    if ((stack == null) || stack.isFailed()) {
      evaluation.set(value);
      return SimpleState.ofValue(value);
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}
