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

package dm.jale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.EvaluationState;
import dm.jale.eventual.SimpleState;

/**
 * Created by davide-maestroni on 01/30/2018.
 */
class TestEvaluations<V> implements EvaluationCollection<V> {

  private ArrayList<EvaluationState<V>> mStates = new ArrayList<EvaluationState<V>>();

  @NotNull
  public EvaluationCollection<V> addFailure(@NotNull final Throwable failure) {
    mStates.add(SimpleState.<V>ofFailure(failure));
    return this;
  }

  @NotNull
  public EvaluationCollection<V> addFailures(
      @Nullable final Iterable<? extends Throwable> failures) {
    if (failures != null) {
      for (final Throwable failure : failures) {
        mStates.add(SimpleState.<V>ofFailure(failure));
      }
    }

    return this;
  }

  @NotNull
  public EvaluationCollection<V> addValue(final V value) {
    mStates.add(SimpleState.ofValue(value));
    return this;
  }

  @NotNull
  public EvaluationCollection<V> addValues(@Nullable final Iterable<? extends V> values) {
    if (values != null) {
      for (final V value : values) {
        mStates.add(SimpleState.ofValue(value));
      }
    }

    return this;
  }

  public void set() {
  }

  ArrayList<EvaluationState<V>> getStates() {
    return mStates;
  }
}
