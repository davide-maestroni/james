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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
public class StatementStack<V> {

  private final ArrayList<AsyncEvaluation<V>> mEvaluations = new ArrayList<AsyncEvaluation<V>>();

  private SimpleState<V> mState;

  @NotNull
  public StatementStack<V> addEvaluation(@NotNull final AsyncEvaluation<V> evaluation) {
    mEvaluations.add(evaluation);
    return this;
  }

  @NotNull
  public List<AsyncEvaluation<V>> getEvaluations() {
    return mEvaluations;
  }

  @Nullable
  public SimpleState<V> getState() {
    return mState;
  }

  @NotNull
  public StatementStack<V> resetState() {
    mState = null;
    return this;
  }

  @NotNull
  public StatementStack<V> setEvaluations() {
    final SimpleState<V> state = mState;
    if (state != null) {
      final ArrayList<AsyncEvaluation<V>> evaluations = mEvaluations;
      for (final AsyncEvaluation<V> evaluation : evaluations) {
        state.to(evaluation);
      }

      evaluations.clear();
    }

    return this;
  }

  @NotNull
  public StatementStack<V> setFailure(@NotNull final Throwable failure) {
    mState = SimpleState.ofFailure(failure);
    return this;
  }

  @NotNull
  public StatementStack<V> setValue(final V value) {
    mState = SimpleState.ofValue(value);
    return this;
  }
}
