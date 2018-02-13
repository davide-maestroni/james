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

import java.util.ArrayList;
import java.util.List;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
public class LoopStack<V> {

  private final ArrayList<AsyncEvaluations<V>> mEvaluations = new ArrayList<AsyncEvaluations<V>>();

  private final ArrayList<SimpleState<V>> mStates = new ArrayList<SimpleState<V>>();

  @NotNull
  public LoopStack<V> addEvaluations(@NotNull final AsyncEvaluations<V> evaluations) {
    mEvaluations.add(evaluations);
    return this;
  }

  @NotNull
  public LoopStack<V> addFailure(@NotNull final Throwable failure) {
    mStates.add(SimpleState.<V>ofFailure(failure));
    return this;
  }

  @NotNull
  public LoopStack<V> addFailureToEvaluations(@NotNull final Throwable failure) {
    for (final AsyncEvaluations<V> evaluations : mEvaluations) {
      evaluations.addFailure(failure);
    }

    return this;
  }

  @NotNull
  public LoopStack<V> addToEvaluations(@NotNull final AsyncEvaluations<V> evaluations) {
    for (final SimpleState<V> state : mStates) {
      state.addTo(evaluations);
    }

    return this;
  }

  @NotNull
  public LoopStack<V> addValue(final V value) {
    mStates.add(SimpleState.ofValue(value));
    return this;
  }

  @NotNull
  public LoopStack<V> addValueToEvaluations(final V value) {
    for (final AsyncEvaluations<V> evaluations : mEvaluations) {
      evaluations.addValue(value);
    }

    return this;
  }

  @NotNull
  public List<AsyncEvaluations<V>> getEvaluations() {
    return mEvaluations;
  }

  @NotNull
  public List<SimpleState<V>> getStates() {
    return mStates;
  }

  @NotNull
  public LoopStack<V> setEvaluations() {
    for (final AsyncEvaluations<V> evaluations : mEvaluations) {
      evaluations.set();
    }

    return this;
  }
}
