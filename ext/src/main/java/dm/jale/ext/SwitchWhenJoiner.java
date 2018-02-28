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
import java.util.List;

import dm.jale.eventual.EvaluationCollection;
import dm.jale.eventual.FailureException;
import dm.jale.eventual.Loop;
import dm.jale.eventual.LoopJoiner;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class SwitchWhenJoiner<V> implements LoopJoiner<Integer, Object, V>, Serializable {

  private static final SwitchWhenJoiner<?> sInstance = new SwitchWhenJoiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private SwitchWhenJoiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> SwitchWhenJoiner<V> instance() {
    return (SwitchWhenJoiner<V>) sInstance;
  }

  public Integer done(final Integer stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<Object>> contexts, final int index) {
    return stack;
  }

  public Integer failure(final Integer stack, final Throwable failure,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<Object>> contexts,
      final int index) {
    if (index == 0) {
      throw FailureException.wrap(failure);

    } else if ((stack != null) && (stack == index)) {
      evaluation.addFailure(failure);
    }

    return stack;
  }

  public Integer init(@NotNull final List<Loop<Object>> contexts) {
    return null;
  }

  public void settle(final Integer stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<Object>> contexts) {
    evaluation.set();
  }

  @SuppressWarnings("unchecked")
  public Integer value(final Integer stack, final Object value,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<Object>> contexts,
      final int index) {
    if (index == 0) {
      return (Integer) value + 1;

    } else if ((stack != null) && (stack == index)) {
      evaluation.addValue((V) value);
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}
