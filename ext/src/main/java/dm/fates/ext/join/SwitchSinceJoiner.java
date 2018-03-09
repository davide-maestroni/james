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

package dm.fates.ext.join;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dm.fates.eventual.EvaluationCollection;
import dm.fates.eventual.FailureException;
import dm.fates.eventual.Loop;
import dm.fates.eventual.LoopJoiner;
import dm.fates.ext.config.BuildConfig;
import dm.fates.ext.eventual.TimedState;
import dm.fates.ext.join.SwitchSinceJoiner.JoinerStack;
import dm.fates.util.ConstantConditions;
import dm.fates.util.DoubleQueue;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class SwitchSinceJoiner<V> implements LoopJoiner<JoinerStack<V>, Object, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final TimeUnit mTimeUnit;

  private final long mTimeout;

  SwitchSinceJoiner(final long timeout, @NotNull final TimeUnit timeUnit) {
    mTimeUnit = ConstantConditions.notNull("timeUnit", timeUnit);
    mTimeout = timeout;
  }

  public JoinerStack<V> done(final JoinerStack<V> stack,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<Object>> contexts,
      final int index) {
    return stack;
  }

  public JoinerStack<V> failure(final JoinerStack<V> stack, final Throwable failure,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<Object>> contexts,
      final int index) {
    if (index == 0) {
      throw FailureException.wrap(failure);

    } else {
      final Integer stackIndex = stack.index;
      if ((stackIndex != null) && (stackIndex == index)) {
        final DoubleQueue<TimedState<V>> states = stack.states[index - 1];
        for (final TimedState<V> state : states) {
          state.addTo(evaluation);
        }

        states.clear();
        evaluation.addFailure(failure);

      } else {
        stack.states[index - 1].add(TimedState.<V>ofFailure(failure));
        purgeStates(stack);
      }
    }

    return stack;
  }

  public JoinerStack<V> init(@NotNull final List<Loop<Object>> contexts) {
    return new JoinerStack<V>(contexts.size() - 1);
  }

  public void settle(final JoinerStack<V> stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<Object>> contexts) {
    evaluation.set();
  }

  @SuppressWarnings("unchecked")
  public JoinerStack<V> value(final JoinerStack<V> stack, final Object value,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<Object>> contexts,
      final int index) {
    if (index == 0) {
      stack.index = (Integer) value + 1;

    } else {
      final Integer stackIndex = stack.index;
      if ((stackIndex != null) && (stackIndex == index)) {
        final DoubleQueue<TimedState<V>> states = stack.states[index - 1];
        for (final TimedState<V> state : states) {
          state.addTo(evaluation);
        }

        states.clear();
        evaluation.addValue((V) value);

      } else {
        stack.states[index - 1].add(TimedState.ofValue((V) value));
        purgeStates(stack);
      }
    }

    return stack;
  }

  private void purgeStates(@NotNull final JoinerStack<V> stack) {
    final long timeout = mTimeout;
    if (timeout < 0) {
      return;
    }

    final DoubleQueue<TimedState<V>>[] states = stack.states;
    if (timeout == 0) {
      for (final DoubleQueue<TimedState<V>> state : states) {
        state.clear();
      }
    }

    final long expireTime = System.currentTimeMillis() - mTimeUnit.toMillis(timeout);
    for (final DoubleQueue<TimedState<V>> state : states) {
      while (state.peekFirst().timestamp() <= expireTime) {
        state.removeFirst();
      }
    }
  }

  static class JoinerStack<V> {

    private final DoubleQueue<TimedState<V>>[] states;

    private Integer index;

    @SuppressWarnings("unchecked")
    private JoinerStack(final int size) {
      states = new DoubleQueue[size];
      for (int i = 0; i < size; ++i) {
        states[i] = new DoubleQueue<TimedState<V>>();
      }
    }
  }
}
