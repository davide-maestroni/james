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

package dm.jale.ext.join;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.FailureException;
import dm.jale.eventual.Loop;
import dm.jale.eventual.LoopJoiner;
import dm.jale.eventual.SimpleState;
import dm.jale.ext.join.SwitchFirstJoiner.JoinerStack;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.ConstantConditions;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class SwitchFirstJoiner<V> implements LoopJoiner<JoinerStack<V>, Object, V>, Serializable {

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private final int mMaxCount;

  SwitchFirstJoiner(final int maxCount) {
    mMaxCount = ConstantConditions.positive("maxCount", maxCount);
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
        final ArrayList<SimpleState<V>> states = stack.states[index - 1];
        for (final SimpleState<V> state : states) {
          state.addTo(evaluation);
        }

        states.clear();
        evaluation.addFailure(failure);

      } else {
        final ArrayList<SimpleState<V>> stateList = stack.states[index - 1];
        if (stateList.size() < mMaxCount) {
          stateList.add(SimpleState.<V>ofFailure(failure));
        }
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
        final ArrayList<SimpleState<V>> states = stack.states[index - 1];
        for (final SimpleState<V> state : states) {
          state.addTo(evaluation);
        }

        states.clear();
        evaluation.addValue((V) value);

      } else {
        final ArrayList<SimpleState<V>> stateList = stack.states[index - 1];
        if (stateList.size() < mMaxCount) {
          stateList.add(SimpleState.ofValue((V) value));
        }
      }
    }

    return stack;
  }

  static class JoinerStack<V> {

    private final ArrayList<SimpleState<V>>[] states;

    private Integer index;

    @SuppressWarnings("unchecked")
    private JoinerStack(final int size) {
      states = new ArrayList[size];
      for (int i = 0; i < size; ++i) {
        states[i] = new ArrayList<SimpleState<V>>();
      }
    }
  }
}
