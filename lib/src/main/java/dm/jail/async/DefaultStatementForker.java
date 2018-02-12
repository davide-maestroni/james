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

package dm.jail.async;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import dm.jail.async.AsyncStatement.Forker;
import dm.jail.async.DefaultStatementForker.ForkerStack;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
public class DefaultStatementForker<V>
    implements Forker<ForkerStack<V>, AsyncStatement<V>, V, AsyncEvaluation<V>> {

  public ForkerStack<V> done(final ForkerStack<V> stack,
      @NotNull final AsyncStatement<V> statement) throws Exception {
    return stack;
  }

  public ForkerStack<V> evaluation(final ForkerStack<V> stack,
      @NotNull final AsyncEvaluation<V> evaluation,
      @NotNull final AsyncStatement<V> statement) throws Exception {
    return stack.addEvaluation(evaluation).flushEvaluations();
  }

  public ForkerStack<V> failure(final ForkerStack<V> stack, @NotNull final Throwable failure,
      @NotNull final AsyncStatement<V> statement) throws Exception {
    return stack.setFailure(failure).flushEvaluations();
  }

  public ForkerStack<V> init(@NotNull final AsyncStatement<V> statement) throws Exception {
    return new ForkerStack<V>();
  }

  public ForkerStack<V> value(final ForkerStack<V> stack, final V value,
      @NotNull final AsyncStatement<V> statement) throws Exception {
    return stack.setValue(value).flushEvaluations();
  }

  public static class ForkerStack<V> {

    private final ArrayList<AsyncEvaluation<V>> mEvaluations = new ArrayList<AsyncEvaluation<V>>();

    private SimpleState<V> mState;

    @NotNull
    public ForkerStack<V> addEvaluation(@NotNull final AsyncEvaluation<V> evaluation) {
      mEvaluations.add(evaluation);
      return this;
    }

    @NotNull
    public ForkerStack<V> clearEvaluations() {
      mEvaluations.clear();
      return this;
    }

    @NotNull
    public ForkerStack<V> flushEvaluations() {
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
    public ForkerStack<V> resetState() {
      mState = null;
      return this;
    }

    @NotNull
    public ForkerStack<V> setFailure(@NotNull final Throwable failure) {
      mState = SimpleState.ofFailure(failure);
      return this;
    }

    @NotNull
    public ForkerStack<V> setValue(final V value) {
      mState = SimpleState.ofValue(value);
      return this;
    }

    public int sizeEvaluations() {
      return mEvaluations.size();
    }
  }
}
