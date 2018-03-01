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
import dm.jale.eventual.Loop.YieldOutputs;
import dm.jale.eventual.LoopForker;
import dm.jale.eventual.LoopYielder;
import dm.jale.eventual.Mapper;
import dm.jale.eventual.SimpleState;
import dm.jale.eventual.Statement;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.forker.RepeatAllAfterForker.ForkerStack;
import dm.jale.util.ConstantConditions;
import dm.jale.util.Iterables;

/**
 * Created by davide-maestroni on 02/19/2018.
 */
class RepeatAllAfterForker<V> implements LoopForker<ForkerStack<V>, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxTimes;

  private final TimeUnit mTimeUnit;

  private final long mTimeout;

  RepeatAllAfterForker(final long timeout, @NotNull final TimeUnit timeUnit) {
    mTimeUnit = ConstantConditions.notNull("timeUnit", timeUnit);
    mTimeout = timeout;
    mMaxTimes = -1;
  }

  RepeatAllAfterForker(final long timeout, @NotNull final TimeUnit timeUnit, final int maxTimes) {
    mTimeUnit = ConstantConditions.notNull("timeUnit", timeUnit);
    mMaxTimes = ConstantConditions.positive("maxTimes", maxTimes);
    mTimeout = timeout;
  }

  public ForkerStack<V> done(final ForkerStack<V> stack, @NotNull final Loop<V> context) {
    for (final EvaluationCollection<V> evaluation : stack.evaluations) {
      evaluation.set();
    }

    stack.evaluations = null;
    stack.timestamp = System.currentTimeMillis();
    return stack;
  }

  public ForkerStack<V> evaluation(final ForkerStack<V> stack,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final Loop<V> context) {
    final int maxTimes = mMaxTimes;
    if ((maxTimes > 0) && (stack.count >= maxTimes)) {
      evaluation.addFailure(new IllegalStateException("the loop evaluation cannot be propagated"))
          .set();
      return stack;
    }

    ++stack.count;
    final Statement<TimedStates<V>> valueStatement = stack.valueStatement;
    if (valueStatement != null) {
      if (valueStatement.isDone()) {
        final TimedStates<V> timedStates = valueStatement.value();
        stack.states = timedStates.states;
        stack.timestamp = timedStates.timestamp;

      } else {
        stack.loop.to(evaluation);
        return stack;
      }
    }

    final Long timestamp = stack.timestamp;
    if (timestamp != null) {
      final long timeout = mTimeout;
      if ((timeout < 0) || (System.currentTimeMillis() <= (timestamp + mTimeUnit.toMillis(
          timeout)))) {
        for (final SimpleState<V> state : stack.states) {
          state.addTo(evaluation);
        }

        evaluation.set();

      } else {
        final Loop<V> loop = context.evaluate().forkLoop(new ReplayAllForker<V>());
        stack.loop = loop;
        stack.valueStatement =
            loop.yield(new ToSimpleStateYielder<V>()).eventually(new ToTimedStatesMapper<V>());
        loop.to(evaluation);
      }

    } else {
      for (final SimpleState<V> state : stack.states) {
        state.addTo(evaluation);
      }

      stack.evaluations.add(evaluation);
    }

    return stack;
  }

  public ForkerStack<V> failure(final ForkerStack<V> stack, @NotNull final Throwable failure,
      @NotNull final Loop<V> context) {
    final SimpleState<V> state = SimpleState.ofFailure(failure);
    final ArrayList<EvaluationCollection<V>> evaluations = stack.evaluations;
    for (final EvaluationCollection<V> evaluation : evaluations) {
      state.addTo(evaluation);
    }

    stack.states.add(state);
    return stack;
  }

  public ForkerStack<V> init(@NotNull final Loop<V> context) {
    return new ForkerStack<V>();
  }

  public ForkerStack<V> value(final ForkerStack<V> stack, final V value,
      @NotNull final Loop<V> context) {
    final SimpleState<V> state = SimpleState.ofValue(value);
    final ArrayList<EvaluationCollection<V>> evaluations = stack.evaluations;
    for (final EvaluationCollection<V> evaluation : evaluations) {
      state.addTo(evaluation);
    }

    stack.states.add(state);
    return stack;
  }

  static class ForkerStack<V> {

    private int count;

    private ArrayList<EvaluationCollection<V>> evaluations =
        new ArrayList<EvaluationCollection<V>>();

    private Loop<V> loop;

    private ArrayList<SimpleState<V>> states = new ArrayList<SimpleState<V>>();

    private Long timestamp;

    private Statement<TimedStates<V>> valueStatement;
  }

  static class TimedStates<V> {

    private ArrayList<SimpleState<V>> states = new ArrayList<SimpleState<V>>();

    private long timestamp;
  }

  private static class ToSimpleStateYielder<V> implements LoopYielder<Void, V, SimpleState<V>> {

    public void done(final Void stack, @NotNull final YieldOutputs<SimpleState<V>> outputs) {
    }

    public Void failure(final Void stack, @NotNull final Throwable failure,
        @NotNull final YieldOutputs<SimpleState<V>> outputs) {
      outputs.yieldValue(SimpleState.<V>ofFailure(failure));
      return null;
    }

    public Void init() {
      return null;
    }

    public boolean loop(final Void stack) {
      return true;
    }

    public Void value(final Void stack, final V value,
        @NotNull final YieldOutputs<SimpleState<V>> outputs) {
      outputs.yieldValue(SimpleState.ofValue(value));
      return null;
    }
  }

  private static class ToTimedStatesMapper<V>
      implements Mapper<Iterable<SimpleState<V>>, TimedStates<V>> {

    public TimedStates<V> apply(final Iterable<SimpleState<V>> values) {
      final TimedStates<V> timedStates = new TimedStates<V>();
      timedStates.timestamp = System.currentTimeMillis();
      Iterables.addAll(values, timedStates.states);
      return timedStates;
    }
  }
}
