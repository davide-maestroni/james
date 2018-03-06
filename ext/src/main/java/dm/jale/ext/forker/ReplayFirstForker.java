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

package dm.jale.ext.forker;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;

import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.Loop;
import dm.jale.eventual.LoopForker;
import dm.jale.eventual.SimpleState;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.forker.ReplayFirstForker.ForkerStack;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class ReplayFirstForker<V> implements LoopForker<ForkerStack<V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxCount;

  private final int mMaxTimes;

  ReplayFirstForker(final int maxCount) {
    mMaxCount = ConstantConditions.notNegative("maxCount", maxCount);
    mMaxTimes = -1;
  }

  ReplayFirstForker(final int maxCount, final int maxTimes) {
    mMaxCount = ConstantConditions.notNegative("maxCount", maxCount);
    mMaxTimes = ConstantConditions.positive("maxTimes", maxTimes);
  }

  public ForkerStack<V> done(final ForkerStack<V> stack, @NotNull final Loop<V> context) {
    stack.evaluations = null;
    return stack;
  }

  public ForkerStack<V> evaluation(final ForkerStack<V> stack,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final Loop<V> context) {
    final int maxTimes = mMaxTimes;
    if ((maxTimes > 0) && (stack.count >= maxTimes)) {
      stack.states = null;
      evaluation.addFailure(new IllegalStateException("the loop evaluation cannot be propagated"))
          .set();
      return stack;
    }

    ++stack.count;
    final ArrayList<SimpleState<V>> states = stack.states;
    for (final SimpleState<V> state : states) {
      state.addTo(evaluation);
    }

    final ArrayList<EvaluationCollection<V>> evaluations = stack.evaluations;
    if (evaluations != null) {
      stack.evaluations.add(evaluation);
    }

    return stack;
  }

  public ForkerStack<V> failure(final ForkerStack<V> stack, @NotNull final Throwable failure,
      @NotNull final Loop<V> context) {
    final ArrayList<EvaluationCollection<V>> evaluations = stack.evaluations;
    for (final EvaluationCollection<V> evaluation : evaluations) {
      evaluation.addFailure(failure);
    }

    final ArrayList<SimpleState<V>> states = stack.states;
    if ((states != null) && (states.size() < mMaxCount)) {
      states.add(SimpleState.<V>ofFailure(failure));
    }

    return stack;
  }

  public ForkerStack<V> init(@NotNull final Loop<V> context) {
    return new ForkerStack<V>();
  }

  public ForkerStack<V> value(final ForkerStack<V> stack, final V value,
      @NotNull final Loop<V> context) {
    final ArrayList<EvaluationCollection<V>> evaluations = stack.evaluations;
    for (final EvaluationCollection<V> evaluation : evaluations) {
      evaluation.addValue(value);
    }

    final ArrayList<SimpleState<V>> states = stack.states;
    if ((states != null) && (states.size() < mMaxCount)) {
      states.add(SimpleState.ofValue(value));
    }

    return stack;
  }

  static class ForkerStack<V> {

    private int count;

    private ArrayList<EvaluationCollection<V>> evaluations =
        new ArrayList<EvaluationCollection<V>>();

    private ArrayList<SimpleState<V>> states = new ArrayList<SimpleState<V>>();
  }
}
