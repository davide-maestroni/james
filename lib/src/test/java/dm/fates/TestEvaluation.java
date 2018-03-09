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
import dm.fates.eventual.EvaluationState;
import dm.fates.eventual.SimpleState;

/**
 * Created by davide-maestroni on 01/30/2018.
 */
class TestEvaluation<V> implements Evaluation<V> {

  private EvaluationState<V> mState;

  public void fail(@NotNull final Throwable failure) {
    mState = SimpleState.ofFailure(failure);
  }

  public void set(final V value) {
    mState = SimpleState.ofValue(value);
  }

  public EvaluationState<V> getState() {
    return mState;
  }
}
