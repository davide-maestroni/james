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

package dm.jale.ext;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import dm.jale.eventual.Evaluation;
import dm.jale.eventual.Statement;
import dm.jale.eventual.StatementForker;
import dm.jale.ext.RepeatSinceForker.ForkerStack;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.eventual.TimedState;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class RepeatSinceForker<V> implements StatementForker<ForkerStack<V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final TimeUnit mTimeUnit;

  private final long mTimeout;

  RepeatSinceForker(final long timeout, @NotNull final TimeUnit timeUnit) {
    mTimeUnit = ConstantConditions.notNull("timeUnit", timeUnit);
    mTimeout = timeout;
  }

  public ForkerStack<V> done(final ForkerStack<V> stack, @NotNull final Statement<V> context) {
    return stack;
  }

  public ForkerStack<V> evaluation(final ForkerStack<V> stack,
      @NotNull final Evaluation<V> evaluation, @NotNull final Statement<V> context) {
    final TimedState<V> state = stack.state;
    final Statement<V> statement = stack.statement;
    if (statement != null) {
      statement.to(evaluation);

    } else if (state != null) {
      final long timeout = mTimeout;
      if ((timeout < 0) || (System.currentTimeMillis() <= (state.timestamp() + mTimeUnit.toMillis(
          timeout)))) {
        state.to(evaluation);

      } else {
        stack.statement = context.evaluate().fork(this);
        stack.statement.to(evaluation);
      }

    } else {
      stack.evaluations.add(evaluation);
    }

    return stack;
  }

  public ForkerStack<V> failure(final ForkerStack<V> stack, @NotNull final Throwable failure,
      @NotNull final Statement<V> context) {
    final TimedState<V> state = (stack.state = TimedState.ofFailure(failure));
    final ArrayList<Evaluation<V>> evaluations = stack.evaluations;
    try {
      for (final Evaluation<V> evaluation : evaluations) {
        state.to(evaluation);
      }

    } finally {
      evaluations.clear();
    }

    return stack;
  }

  public ForkerStack<V> init(@NotNull final Statement<V> context) {
    return new ForkerStack<V>();
  }

  public ForkerStack<V> value(final ForkerStack<V> stack, final V value,
      @NotNull final Statement<V> context) {
    final TimedState<V> state = (stack.state = TimedState.ofValue(value));
    final ArrayList<Evaluation<V>> evaluations = stack.evaluations;
    try {
      for (final Evaluation<V> evaluation : evaluations) {
        state.to(evaluation);
      }

    } finally {
      evaluations.clear();
    }

    return stack;
  }

  static class ForkerStack<V> {

    private final ArrayList<Evaluation<V>> evaluations = new ArrayList<Evaluation<V>>();

    private TimedState<V> state;

    private Statement<V> statement;
  }
}
