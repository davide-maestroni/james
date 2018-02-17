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
class FirstOrEmptyCombiner<V> implements LoopCombiner<Integer, V, V>, Serializable {

  private static final FirstOrEmptyCombiner<?> sInstance = new FirstOrEmptyCombiner<Object>();

  private static final long serialVersionUID = BuildConfig.VERSION_HASH_CODE;

  private FirstOrEmptyCombiner() {
  }

  @NotNull
  @SuppressWarnings("unchecked")
  static <V> FirstOrEmptyCombiner<V> instance() {
    return (FirstOrEmptyCombiner<V>) sInstance;
  }

  public Integer done(final Integer stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<V>> asyncs, final int index) {
    if ((stack == null) || (stack == index)) {
      evaluation.set();
      return index;
    }

    return stack;
  }

  public Integer failure(final Integer stack, final Throwable failure,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> asyncs,
      final int index) {
    if ((stack == null) || (stack == index)) {
      evaluation.addFailure(failure);
      return index;
    }

    return stack;
  }

  public Integer init(@NotNull final List<Loop<V>> asyncs) {
    return null;
  }

  public void settle(final Integer stack, @NotNull final EvaluationCollection<V> evaluation,
      @NotNull final List<Loop<V>> asyncs) {
    if (stack == null) {
      evaluation.set();
    }
  }

  public Integer value(final Integer stack, final V value,
      @NotNull final EvaluationCollection<V> evaluation, @NotNull final List<Loop<V>> asyncs,
      final int index) {
    if ((stack == null) || (stack == index)) {
      evaluation.addValue(value);
      return index;
    }

    return stack;
  }

  @NotNull
  private Object readResolve() throws ObjectStreamException {
    return sInstance;
  }
}
