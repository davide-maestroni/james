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

import dm.jale.async.EvaluationCollection;
import dm.jale.async.Loop;
import dm.jale.async.LoopCombiner;
import dm.jale.ext.config.BuildConfig;

/**
 * Created by davide-maestroni on 02/16/2018.
 */
class SwitchWhenCombiner<V> implements LoopCombiner<Integer, Object, V>, Serializable {

  private static final SwitchWhenCombiner<?> sInstance = new SwitchWhenCombiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private SwitchWhenCombiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> SwitchWhenCombiner<V> instance() {
    return (SwitchWhenCombiner<V>) sInstance;
  }

  public Integer done(final Integer stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<Object>> contexts, final int index) {
    return stack;
  }

  public Integer failure(final Integer stack, final Throwable failure,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<Object>> contexts,
      final int index) {
    if (index == 0) {
      return null;

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
