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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import dm.jale.async.EvaluationCollection;
import dm.jale.async.Loop;
import dm.jale.async.LoopJoiner;
import dm.jale.async.SimpleState;
import dm.jale.ext.SwitchAllJoiner.CombinerStack;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class SwitchAllJoiner<V> implements LoopJoiner<CombinerStack<V>, Object, V>, Serializable {

  private static final SwitchAllJoiner<?> sInstance = new SwitchAllJoiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private SwitchAllJoiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> SwitchAllJoiner<V> instance() {
    return (SwitchAllJoiner<V>) sInstance;
  }

  public CombinerStack<V> done(final CombinerStack<V> stack,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<Object>> contexts,
      final int index) {
    return stack;
  }

  public CombinerStack<V> failure(final CombinerStack<V> stack, final Throwable failure,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<Object>> contexts,
      final int index) {
    if (index == 0) {
      return null;

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
        stack.states[index - 1].add(SimpleState.<V>ofFailure(failure));
      }
    }

    return stack;
  }

  public CombinerStack<V> init(@NotNull final List<Loop<Object>> contexts) {
    return new CombinerStack<V>(contexts.size() - 1);
  }

  public void settle(final CombinerStack<V> stack,
      @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<Object>> contexts) {
    evaluation.set();
  }

  @SuppressWarnings("unchecked")
  public CombinerStack<V> value(final CombinerStack<V> stack, final Object value,
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
        stack.states[index - 1].add(SimpleState.ofValue((V) value));
      }
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }

  static class CombinerStack<V> {

    private final ArrayList<SimpleState<V>>[] states;

    private Integer index;

    @SuppressWarnings("unchecked")
    private CombinerStack(final int size) {
      states = new ArrayList[size];
      for (int i = 0; i < size; ++i) {
        states[i] = new ArrayList<SimpleState<V>>();
      }
    }
  }
}
