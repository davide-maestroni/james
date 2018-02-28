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
import java.util.concurrent.TimeUnit;

import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.Loop;
import dm.jale.eventual.LoopForker;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.eventual.TimedState;
import dm.jale.ext.forker.ReplaySinceForker.ForkerStack;
import dm.jale.util.ConstantConditions;
import dm.jale.util.DoubleQueue;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class ReplaySinceForker<V> implements LoopForker<ForkerStack<V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxTimes;

  private final TimeUnit mTimeUnit;

  private final long mTimeout;

  ReplaySinceForker(final long timeout, @NotNull final TimeUnit timeUnit) {
    mTimeUnit = ConstantConditions.notNull("timeUnit", timeUnit);
    mMaxTimes = -1;
    mTimeout = timeout;
  }

  ReplaySinceForker(final long timeout, @NotNull final TimeUnit timeUnit, final int maxTimes) {
    mTimeUnit = ConstantConditions.notNull("timeUnit", timeUnit);
    mMaxTimes = ConstantConditions.positive("maxTimes", maxTimes);
    mTimeout = timeout;
  }

  public ForkerStack<V> done(final ForkerStack<V> stack, @NotNull final Loop<V> context) {
    stack.evaluations = null;
    return stack;
  }

  public ForkerStack<V> evaluation(final ForkerStack<V> stack,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final Loop<V> context) {
    final int maxTimes = mMaxTimes;
    if ((maxTimes > 0) && (stack.count >= maxTimes)) {
      evaluation.addFailure(new IllegalStateException()).set();
      return stack;
    }

    ++stack.count;
    final DoubleQueue<TimedState<V>> states = purgeStates(stack).states;
    for (final TimedState<V> state : states) {
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

    stack.states.add(TimedState.<V>ofFailure(failure));
    return purgeStates(stack);
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

    stack.states.add(TimedState.ofValue(value));
    return purgeStates(stack);
  }

  @NotNull
  private ForkerStack<V> purgeStates(@NotNull final ForkerStack<V> stack) {
    final long timeout = mTimeout;
    if (timeout < 0) {
      return stack;
    }

    final DoubleQueue<TimedState<V>> states = stack.states;
    if (timeout == 0) {
      states.clear();
    }

    final long expireTime = System.currentTimeMillis() - mTimeUnit.toMillis(timeout);
    while (states.peekFirst().timestamp() <= expireTime) {
      states.removeFirst();
    }

    return stack;
  }

  static class ForkerStack<V> {

    private final DoubleQueue<TimedState<V>> states = new DoubleQueue<TimedState<V>>();

    private int count;

    private ArrayList<EvaluationCollection<V>> evaluations =
        new ArrayList<EvaluationCollection<V>>();
  }
}
