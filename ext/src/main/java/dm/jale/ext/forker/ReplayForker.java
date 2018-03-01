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

import dm.jale.eventual.Evaluation;
import dm.jale.eventual.SimpleState;
import dm.jale.eventual.Statement;
import dm.jale.eventual.StatementForker;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.forker.ReplayForker.ForkerStack;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class ReplayForker<V> implements StatementForker<ForkerStack<V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxTimes;

  ReplayForker() {
    mMaxTimes = -1;
  }

  ReplayForker(final int maxTimes) {
    mMaxTimes = ConstantConditions.positive("maxTimes", maxTimes);
  }

  public ForkerStack<V> done(final ForkerStack<V> stack, @NotNull final Statement<V> context) {
    stack.evaluations = null;
    return stack;
  }

  public ForkerStack<V> evaluation(final ForkerStack<V> stack,
      @NotNull final Evaluation<V> evaluation, @NotNull final Statement<V> context) {
    final int maxTimes = mMaxTimes;
    if ((maxTimes > 0) && (stack.count >= maxTimes)) {
      evaluation.fail(new IllegalStateException("the statement evaluation cannot be propagated"));
      return stack;
    }

    ++stack.count;
    final SimpleState<V> state = stack.state;
    if (state != null) {
      state.to(evaluation);

    } else {
      stack.evaluations.add(evaluation);
    }

    return stack;
  }

  public ForkerStack<V> failure(final ForkerStack<V> stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) {
    final SimpleState<V> state = (stack.state = SimpleState.ofFailure(failure));
    final ArrayList<Evaluation<V>> evaluations = stack.evaluations;
    for (final Evaluation<V> evaluation : evaluations) {
      state.to(evaluation);
    }

    return stack;
  }

  public ForkerStack<V> init(@NotNull final Statement<V> context) {
    return new ForkerStack<V>();
  }

  public ForkerStack<V> value(final ForkerStack<V> stack, final V value,
      @NotNull final Statement<V> context) {
    final SimpleState<V> state = (stack.state = SimpleState.ofValue(value));
    final ArrayList<Evaluation<V>> evaluations = stack.evaluations;
    for (final Evaluation<V> evaluation : evaluations) {
      state.to(evaluation);
    }

    return stack;
  }

  static class ForkerStack<V> {

    private int count;

    private ArrayList<Evaluation<V>> evaluations = new ArrayList<Evaluation<V>>();

    private SimpleState<V> state;
  }
}
