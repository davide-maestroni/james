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
import java.util.ArrayList;
import java.util.List;

import dm.jale.eventual.Evaluation;
import dm.jale.eventual.EvaluationState;
import dm.jale.eventual.SimpleState;
import dm.jale.eventual.Statement;
import dm.jale.eventual.StatementJoiner;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class StatesOfJoiner<V>
    implements StatementJoiner<List<EvaluationState<V>>, V, List<EvaluationState<V>>>,
    Serializable {

  private static final StatesOfJoiner<?> sInstance = new StatesOfJoiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private StatesOfJoiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> StatesOfJoiner<V> instance() {
    return (StatesOfJoiner<V>) sInstance;
  }

  public List<EvaluationState<V>> done(final List<EvaluationState<V>> stack,
      @NotNull final Evaluation<List<EvaluationState<V>>> evaluation,
      @NotNull final List<Statement<V>> contexts, final int index) {
    return stack;
  }

  public List<EvaluationState<V>> failure(final List<EvaluationState<V>> stack,
      final Throwable failure, @NotNull final Evaluation<List<EvaluationState<V>>> evaluation,
      @NotNull final List<Statement<V>> contexts, final int index) {
    stack.add(SimpleState.<V>ofFailure(failure));
    return stack;
  }

  public List<EvaluationState<V>> init(@NotNull final List<Statement<V>> contexts) {
    return new ArrayList<EvaluationState<V>>();
  }

  public void settle(final List<EvaluationState<V>> stack,
      @NotNull final Evaluation<List<EvaluationState<V>>> evaluation,
      @NotNull final List<Statement<V>> contexts) {
    evaluation.set(stack);
  }

  public List<EvaluationState<V>> value(final List<EvaluationState<V>> stack, final V value,
      @NotNull final Evaluation<List<EvaluationState<V>>> evaluation,
      @NotNull final List<Statement<V>> contexts, final int index) {
    stack.add(SimpleState.ofValue(value));
    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}
