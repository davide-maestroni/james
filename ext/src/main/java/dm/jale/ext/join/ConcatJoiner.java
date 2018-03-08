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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.Loop;
import dm.jale.eventual.LoopJoiner;
import dm.jale.eventual.SimpleState;
import dm.jale.ext.config.BuildConfig;
import dm.jale.ext.join.ConcatJoiner.JoinerStack;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class ConcatJoiner<V> implements LoopJoiner<JoinerStack<V>, V, V>, Serializable {

  private static final ConcatJoiner<?> sInstance = new ConcatJoiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private ConcatJoiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> ConcatJoiner<V> instance() {
    return (ConcatJoiner<V>) sInstance;
  }

  public JoinerStack<V> done(final JoinerStack<V> stack,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> contexts,
      final int index) {
    if (stack.index == index) {
      final int nextIndex = stack.index++;
      if (nextIndex < stack.states.length) {
        final ArrayList<SimpleState<V>> states = stack.states[nextIndex];
        for (final SimpleState<V> state : states) {
          state.addTo(evaluation);
        }

        states.clear();
      }
    }

    return stack;
  }

  public JoinerStack<V> failure(final JoinerStack<V> stack, final Throwable failure,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> contexts,
      final int index) {
    if (stack.index == index) {
      evaluation.addFailure(failure);

    } else {
      stack.states[index - 1].add(SimpleState.<V>ofFailure(failure));
    }

    return stack;
  }

  public JoinerStack<V> init(@NotNull final List<Loop<V>> contexts) {
    return new JoinerStack<V>(contexts.size() - 1);
  }

  public void settle(final JoinerStack<V> stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<V>> contexts) {
    evaluation.set();
  }

  @SuppressWarnings("unchecked")
  public JoinerStack<V> value(final JoinerStack<V> stack, final V value,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> contexts,
      final int index) {
    if (stack.index == index) {
      evaluation.addValue(value);

    } else {
      stack.states[index - 1].add(SimpleState.ofValue(value));
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }

  static class JoinerStack<V> {

    private final ArrayList<SimpleState<V>>[] states;

    private int index;

    @SuppressWarnings("unchecked")
    private JoinerStack(final int size) {
      states = new ArrayList[size];
      for (int i = 0; i < size; ++i) {
        states[i] = new ArrayList<SimpleState<V>>();
      }
    }
  }
}
