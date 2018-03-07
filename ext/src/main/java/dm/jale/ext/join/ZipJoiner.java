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
import dm.jale.ext.join.ZipJoiner.JoinerStack;
import dm.jale.ext.config.BuildConfig;
import dm.jale.util.DoubleQueue;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class ZipJoiner<V> implements LoopJoiner<JoinerStack<V>, V, List<V>>, Serializable {

  private static final ZipJoiner<?> sInstance = new ZipJoiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private ZipJoiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> ZipJoiner<V> instance() {
    return (ZipJoiner<V>) sInstance;
  }

  public JoinerStack<V> done(final JoinerStack<V> stack,
      @NotNull final EvaluationCollection<List<V>> evaluation,
      @NotNull final List<Loop<V>> contexts, final int index) {
    if ((stack.failure == null) && !stack.isSet) {
      stack.isSet = true;
      evaluation.set();
    }

    return stack;
  }

  public JoinerStack<V> failure(final JoinerStack<V> stack, final Throwable failure,
      @NotNull final EvaluationCollection<List<V>> evaluation,
      @NotNull final List<Loop<V>> contexts, final int index) {
    if (stack.failure == null) {
      stack.failure = failure;
      evaluation.addFailure(failure).set();
    }

    return stack;
  }

  public JoinerStack<V> init(@NotNull final List<Loop<V>> contexts) {
    return new JoinerStack<V>(contexts.size());
  }

  public void settle(final JoinerStack<V> stack,
      @NotNull final EvaluationCollection<List<V>> evaluation,
      @NotNull final List<Loop<V>> contexts) {
  }

  @SuppressWarnings("unchecked")
  public JoinerStack<V> value(final JoinerStack<V> stack, final V value,
      @NotNull final EvaluationCollection<List<V>> evaluation,
      @NotNull final List<Loop<V>> contexts, final int index) {
    if (stack.failure == null) {
      final DoubleQueue<V>[] states = stack.states;
      final int length = states.length;
      boolean canAdd = true;
      for (int i = 0; i < length; ++i) {
        if ((i != index) && states[i].isEmpty()) {
          canAdd = false;
          break;
        }
      }

      if (canAdd) {
        final ArrayList<V> values = new ArrayList<V>();
        for (int i = 0; i < length; ++i) {
          values.add((i != index) ? states[i].removeFirst() : value);
        }

        evaluation.addValue(values);

      } else {
        states[index].add(value);
      }
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }

  static class JoinerStack<V> {

    private final DoubleQueue<V>[] states;

    private Throwable failure;

    private boolean isSet;

    @SuppressWarnings("unchecked")
    private JoinerStack(final int size) {
      states = new DoubleQueue[size];
      for (int i = 0; i < size; ++i) {
        states[i] = new DoubleQueue<V>();
      }
    }
  }
}
