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

package dm.fates.ext.fork;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;

import dm.fates.Eventual;
import dm.fates.eventual.EvaluationCollection;
import dm.fates.eventual.Loop;
import dm.fates.eventual.LoopForker;
import dm.fates.eventual.SimpleState;
import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.fork.ReplayLastForker.ForkerStack;
import dm.fates.util.ConstantConditions;
import dm.fates.util.DoubleQueue;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class ReplayLastForker<V> implements LoopForker<ForkerStack<V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxCount;

  private final int mMaxTimes;

  private ReplayLastForker(final int maxCount) {
    mMaxCount = ConstantConditions.notNegative("maxCount", maxCount);
    mMaxTimes = -1;
  }

  private ReplayLastForker(final int maxCount, final int maxTimes) {
    mMaxCount = ConstantConditions.notNegative("maxCount", maxCount);
    mMaxTimes = ConstantConditions.positive("maxTimes", maxTimes);
  }

  @NotNull
  static <V> LoopForker<?, V> newForker(final int maxCount) {
    return Eventual.safeLoopForker(new ReplayLastForker<V>(maxCount));
  }

  @NotNull
  static <V> LoopForker<?, V> newForker(final int maxCount, final int maxTimes) {
    return Eventual.safeLoopForker(new ReplayLastForker<V>(maxCount, maxTimes));
  }

  public ForkerStack<V> done(final ForkerStack<V> stack, @NotNull final Loop<V> context) {
    final ArrayList<EvaluationCollection<V>> evaluations = stack.evaluations;
    for (final EvaluationCollection<V> evaluation : evaluations) {
      evaluation.set();
    }

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
    final DoubleQueue<SimpleState<V>> states = stack.states;
    for (final SimpleState<V> state : states) {
      state.addTo(evaluation);
    }

    final ArrayList<EvaluationCollection<V>> evaluations = stack.evaluations;
    if (evaluations != null) {
      evaluations.add(evaluation);

    } else {
      evaluation.set();
    }

    return stack;
  }

  public ForkerStack<V> failure(final ForkerStack<V> stack, @NotNull final Throwable failure,
      @NotNull final Loop<V> context) {
    final ArrayList<EvaluationCollection<V>> evaluations = stack.evaluations;
    for (final EvaluationCollection<V> evaluation : evaluations) {
      evaluation.addFailure(failure);
    }

    final DoubleQueue<SimpleState<V>> states = stack.states;
    if (states != null) {
      states.add(SimpleState.<V>ofFailure(failure));
      @SuppressWarnings("UnnecessaryLocalVariable") final int maxCount = mMaxCount;
      while (states.size() > maxCount) {
        states.removeFirst();
      }
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

    final DoubleQueue<SimpleState<V>> states = stack.states;
    if (states != null) {
      states.add(SimpleState.ofValue(value));
      @SuppressWarnings("UnnecessaryLocalVariable") final int maxCount = mMaxCount;
      while (states.size() > maxCount) {
        states.removeFirst();
      }
    }

    return stack;
  }

  static class ForkerStack<V> {

    private int count;

    private ArrayList<EvaluationCollection<V>> evaluations =
        new ArrayList<EvaluationCollection<V>>();

    private DoubleQueue<SimpleState<V>> states = new DoubleQueue<SimpleState<V>>();
  }
}
