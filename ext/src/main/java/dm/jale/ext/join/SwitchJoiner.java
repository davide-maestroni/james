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
import java.util.List;

import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.Loop;
import dm.jale.eventual.LoopJoiner;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class SwitchJoiner<V> implements LoopJoiner<Boolean[], V, V>, Serializable {

  private static final SwitchJoiner<?> sInstance = new SwitchJoiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private SwitchJoiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> SwitchJoiner<V> instance() {
    return (SwitchJoiner<V>) sInstance;
  }

  public Boolean[] done(final Boolean[] stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<V>> contexts, final int index) {
    return stack;
  }

  public Boolean[] failure(final Boolean[] stack, final Throwable failure,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> contexts,
      final int index) {
    changeOwnership(stack, index);
    if (stack[index]) {
      evaluation.addFailure(failure);
    }

    return stack;
  }

  public Boolean[] init(@NotNull final List<Loop<V>> contexts) {
    return new Boolean[contexts.size()];
  }

  public void settle(final Boolean[] stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<V>> contexts) {
    evaluation.set();
  }

  public Boolean[] value(final Boolean[] stack, final V value,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> contexts,
      final int index) {
    changeOwnership(stack, index);
    if (stack[index]) {
      evaluation.addValue(value);
    }

    return stack;
  }

  private void changeOwnership(final Boolean[] stack, final int index) {
    if (stack[index] == null) {
      for (int i = 0; i < stack.length; i++) {
        if (Boolean.TRUE.equals(stack[i])) {
          stack[i] = Boolean.FALSE;
          break;
        }
      }

      stack[index] = Boolean.TRUE;
    }
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}
