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
import dm.jail.async.DefaultLoopForker.ForkerStack;

/**
 * Created by davide-maestroni on 02/12/2018.
 */
public class DefaultLoopForker<V>
    implements Forker<ForkerStack<V>, AsyncLoop<V>, V, AsyncEvaluations<V>> {

  public ForkerStack<V> done(final ForkerStack<V> stack, @NotNull final AsyncLoop<V> loop) throws
      Exception {
    return stack.settle().setEvaluations();
  }

  public ForkerStack<V> evaluation(final ForkerStack<V> stack,
      @NotNull final AsyncEvaluations<V> evaluations, @NotNull final AsyncLoop<V> loop) throws
      Exception {
    return stack.addEvaluations(evaluations).addToEvaluations(evaluations);
  }

  public ForkerStack<V> failure(final ForkerStack<V> stack, @NotNull final Throwable failure,
      @NotNull final AsyncLoop<V> loop) throws Exception {
    return stack.addFailure(failure).addFailureToEvaluations(failure);
  }

  public ForkerStack<V> init(@NotNull final AsyncLoop<V> statement) throws Exception {
    return new ForkerStack<V>();
  }

  public ForkerStack<V> value(final ForkerStack<V> stack, final V value,
      @NotNull final AsyncLoop<V> loop) throws Exception {
    return stack.addValue(value).addValueToEvaluations(value);
  }

  public static class ForkerStack<V> {

    private final ArrayList<AsyncEvaluations<V>> mEvaluations =
        new ArrayList<AsyncEvaluations<V>>();

    private ArrayList<SimpleState<V>> mStates = new ArrayList<SimpleState<V>>();

    @NotNull
    public ForkerStack<V> addEvaluations(@NotNull final AsyncEvaluations<V> evaluations) {
      mEvaluations.add(evaluations);
      return this;
    }

    @NotNull
    public ForkerStack<V> addFailure(@NotNull final Throwable failure) {
      mStates.add(SimpleState.<V>ofFailure(failure));
      return this;
    }

    @NotNull
    public ForkerStack<V> addFailureToEvaluations(@NotNull final Throwable failure) {
      for (final AsyncEvaluations<V> evaluation : mEvaluations) {
        evaluation.addFailure(failure);
      }

      return this;
    }

    @NotNull
    public ForkerStack<V> addToEvaluations(@NotNull final AsyncEvaluations<V> evaluations) {
      final ArrayList<SimpleState<V>> states = mStates;
      if (!states.isEmpty()) {
        for (final SimpleState<V> state : states) {
          state.addTo(evaluations);
        }
      }

      return this;
    }

    @NotNull
    public ForkerStack<V> addValue(final V value) {
      mStates.add(SimpleState.ofValue(value));
      return this;
    }

    @NotNull
    public ForkerStack<V> addValueToEvaluations(final V value) {
      for (final AsyncEvaluations<V> evaluation : mEvaluations) {
        evaluation.addValue(value);
      }

      return this;
    }

    @NotNull
    public ForkerStack<V> clearEvaluations() {
      mEvaluations.clear();
      return this;
    }

    @NotNull
    public ForkerStack<V> resetState() {
      mStates.clear();
      return this;
    }

    @NotNull
    public ForkerStack<V> setEvaluations() {
      for (final AsyncEvaluations<V> evaluation : mEvaluations) {
        evaluation.set();
      }

      return this;
    }

    @NotNull
    public ForkerStack<V> settle() {
      mStates.add(SimpleState.<V>settled());
      return this;
    }

    public int sizeEvaluations() {
      return mEvaluations.size();
    }
  }
}
